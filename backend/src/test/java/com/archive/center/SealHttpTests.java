package com.archive.center;

import static org.assertj.core.api.Assertions.assertThat;

import com.archive.center.entity.Archive;
import com.archive.center.entity.Retrieval;
import com.archive.center.entity.Room;
import com.archive.center.repository.AppraisalRepository;
import com.archive.center.repository.ArchiveRepository;
import com.archive.center.repository.RetrievalRepository;
import com.archive.center.repository.RoomCheckRepository;
import com.archive.center.repository.RoomRepository;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** 封库口径走真实 HTTP：两班越限封库 → 硬开被拦留下失败说明 → 归还照常 → 回温恢复。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SealHttpTests {

    @LocalServerPort int port;
    @Autowired TestRestTemplate http;
    @Autowired RoomCheckRepository checks;
    @Autowired AppraisalRepository appraisals;
    @Autowired RetrievalRepository retrievals;
    @Autowired ArchiveRepository archives;
    @Autowired RoomRepository rooms;

    Long roomId;
    Long inStock;
    Long onLoan;
    Long loanId;

    @BeforeEach
    void seed() {
        appraisals.deleteAll();
        checks.deleteAll();
        retrievals.deleteAll();
        archives.deleteAll();
        rooms.deleteAll();

        Room r = new Room();
        r.code = "R-S1-" + System.nanoTime();
        r.name = "二号库房";
        r.capacity = 60;
        r.status = "在用";
        r.tempMin = 14.0;
        r.tempMax = 24.0;
        r.humidityMin = 45.0;
        r.humidityMax = 60.0;
        r.sealed = false;
        roomId = rooms.save(r).id;

        Archive a = new Archive();
        a.code = "S-2023-1-" + System.nanoTime();
        a.title = "在库卷";
        a.archiveYear = 2023;
        a.keepYears = 30;
        a.roomId = roomId;
        a.status = "在库";
        inStock = archives.save(a).id;

        Archive b = new Archive();
        b.code = "S-2020-2-" + System.nanoTime();
        b.title = "借出卷";
        b.archiveYear = 2020;
        b.keepYears = 10;
        b.roomId = roomId;
        b.status = "已借出";
        onLoan = archives.save(b).id;

        Retrieval loan = new Retrieval();
        loan.archiveId = onLoan;
        loan.visitor = "刘敏";
        loan.dept = "市人社局";
        loan.retrieveDate = LocalDate.now().minusDays(3);
        loan.dueDate = LocalDate.now().plusDays(2);
        loan.status = "调阅中";
        loanId = retrievals.save(loan).id;
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private ResponseEntity<Map> postCheck(String date, double t, double h, String desc) {
        Map<String, Object> body = new HashMap<>();
        body.put("roomId", roomId);
        body.put("checkDate", date);
        body.put("temperature", t);
        body.put("humidity", h);
        body.put("issueDesc", desc);
        body.put("checker", "王保管");
        return http.postForEntity(url("/api/checks"), body, Map.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> roomJson() {
        List<Map<String, Object>> list = http.getForEntity(url("/api/rooms"), List.class).getBody();
        return list.stream()
                .filter(x -> roomId.intValue() == ((Number) x.get("id")).intValue())
                .findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> archiveJson(Long id) {
        List<Map<String, Object>> list = http.getForEntity(url("/api/archives"), List.class).getBody();
        return list.stream()
                .filter(x -> id.intValue() == ((Number) x.get("id")).intValue())
                .findFirst().orElseThrow();
    }

    @Test
    void 全链路_两班越限封库_硬开被拦留说明_归还照常_回温恢复() {
        // 第一班越限：只预警，不封
        ResponseEntity<Map> c1 = postCheck("2026-09-17", 26.5, 58, "空调故障");
        assertThat(c1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(c1.getBody().get("result")).isEqualTo("异常");
        assertThat(roomJson().get("sealed")).isEqualTo(false);

        // 第二班还越：封库
        ResponseEntity<Map> c2 = postCheck("2026-09-18", 27.2, 61, "温度湿度都越限");
        assertThat(c2.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> sealedRoom = roomJson();
        assertThat(sealedRoom.get("sealed")).isEqualTo(true);
        assertThat(sealedRoom.get("sealedDate")).isEqualTo("2026-09-18");

        // 封库期间硬开新单：400 + 失败说明；单子没落，卷还在库，现放卷数没减
        Map<String, Object> borrow = new HashMap<>();
        borrow.put("archiveId", inStock);
        borrow.put("visitor", "访客甲");
        borrow.put("dept", "利用科");
        borrow.put("retrieveDate", "2026-09-19");
        borrow.put("dueDate", "2026-09-22");
        ResponseEntity<Map> denied = http.postForEntity(url("/api/retrievals"), borrow, Map.class);
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(String.valueOf(denied.getBody().get("message"))).contains("封库");

        List<Map<String, Object>> loans = http.getForEntity(
                url("/api/retrievals?archiveId=" + inStock), List.class).getBody();
        assertThat(loans).isEmpty();
        assertThat(archiveJson(inStock).get("status")).isEqualTo("在库");

        // 封库期间归还照常：当天收回，卷回在库，现放卷数当天加回
        ResponseEntity<Map> back = http.postForEntity(
                url("/api/retrievals/" + loanId + "/giveback?returnDate=2026-09-19"), null, Map.class);
        assertThat(back.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(back.getBody().get("status")).isEqualTo("已归还");
        assertThat(archiveJson(onLoan).get("status")).isEqualTo("在库");

        // 回温前把历史班次补成区间内的数：封库不解除，新调阅仍开不成
        ResponseEntity<Map> history = postCheck("2026-09-10", 21.0, 50, null);
        assertThat(history.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(roomJson().get("sealed")).isEqualTo(true);
        ResponseEntity<Map> stillDenied = http.postForEntity(url("/api/retrievals"), borrow, Map.class);
        assertThat(stillDenied.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // 下一班抄表回到本库区间：回温，封库解除
        ResponseEntity<Map> c3 = postCheck("2026-09-19", 22.0, 50, null);
        assertThat(c3.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(c3.getBody().get("result")).isEqualTo("正常");
        assertThat(roomJson().get("sealed")).isEqualTo(false);

        // 回温后新调阅能再开
        ResponseEntity<Map> ok = http.postForEntity(url("/api/retrievals"), borrow, Map.class);
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ok.getBody().get("status")).isEqualTo("调阅中");
        assertThat(archiveJson(inStock).get("status")).isEqualTo("已借出");
    }

    @Test
    void 封库中的库房抄表口不堵_停用库房才不用查() {
        postCheck("2026-09-17", 26.5, 58, "空调故障");
        postCheck("2026-09-18", 27.2, 61, "温度湿度都越限");
        assertThat(roomJson().get("sealed")).isEqualTo(true);

        // 封库中本班抄表照常登记（越限要写说明，回区间不用）
        ResponseEntity<Map> stillOut = postCheck("2026-09-19", 28.0, 62, "还没修好");
        assertThat(stillOut.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(roomJson().get("sealed")).isEqualTo(true);

        ResponseEntity<Map> backIn = postCheck("2026-09-20", 22.0, 50, null);
        assertThat(backIn.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(roomJson().get("sealed")).isEqualTo(false);
    }
}
