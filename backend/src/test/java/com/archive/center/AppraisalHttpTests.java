package com.archive.center;

import static org.assertj.core.api.Assertions.assertThat;

import com.archive.center.entity.Archive;
import com.archive.center.entity.Room;
import com.archive.center.repository.AppraisalRepository;
import com.archive.center.repository.ArchiveRepository;
import com.archive.center.repository.RetrievalRepository;
import com.archive.center.repository.RoomRepository;
import java.time.LocalDate;
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

/** 走真实 HTTP，验证接口路径、参数绑定和 JSON 字段。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AppraisalHttpTests {

    @LocalServerPort int port;
    @Autowired TestRestTemplate http;
    @Autowired AppraisalRepository appraisals;
    @Autowired ArchiveRepository archives;
    @Autowired RetrievalRepository retrievals;
    @Autowired RoomRepository rooms;

    Long inStock;
    Long untouched;

    @BeforeEach
    void seed() {
        appraisals.deleteAll();
        retrievals.deleteAll();
        archives.deleteAll();
        rooms.deleteAll();
        Room r = new Room();
        r.code = "R-H1-" + System.nanoTime();
        r.name = "一号库房";
        r.capacity = 100;
        r.status = "在用";
        Long roomId = rooms.save(r).id;

        Archive a = new Archive();
        a.code = "H-2016-1-" + System.nanoTime();
        a.title = "HTTP 到期卷";
        a.archiveYear = 2016;
        a.keepYears = 10;
        a.roomId = roomId;
        a.status = "在库";
        inStock = archives.save(a).id;

        Archive b = new Archive();
        b.code = "H-2015-2-" + System.nanoTime();
        b.title = "HTTP 另一卷";
        b.archiveYear = 2015;
        b.keepYears = 10;
        b.roomId = roomId;
        b.status = "在库";
        untouched = archives.save(b).id;
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    void 全链路_开单_会签销毁_名单消失_库位腾出() {
        ResponseEntity<List> pending0 = http.getForEntity(url("/api/appraisals/pending"), List.class);
        assertThat(pending0.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> rows = pending0.getBody();
        assertThat(rows).hasSize(2);
        Map<String, Object> row = rows.stream()
                .filter(x -> inStock.intValue() == ((Number) x.get("archiveId")).intValue())
                .findFirst().orElseThrow();
        assertThat(row.get("archiveId")).isEqualTo(inStock.intValue());
        assertThat(row.get("roomName")).isEqualTo("一号库房");
        assertThat(row.get("outOnLoan")).isEqualTo(false);

        ResponseEntity<Map> opened = http.postForEntity(
                url("/api/appraisals?archiveId=" + inStock), null, Map.class);
        assertThat(opened.getStatusCode()).isEqualTo(HttpStatus.OK);
        Number sheetId = (Number) opened.getBody().get("id");
        assertThat(opened.getBody().get("status")).isEqualTo("未结案");

        // 重复开单：后到的失败
        ResponseEntity<Map> again = http.postForEntity(
                url("/api/appraisals?archiveId=" + inStock), null, Map.class);
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(String.valueOf(again.getBody().get("message"))).contains("已经在鉴定中");

        // 两人同名：失败
        Map<String, Object> sameName = Map.of(
                "opinion", "销毁", "appraiser", "甲", "leader", "甲");
        ResponseEntity<Map> bad = http.postForEntity(
                url("/api/appraisals/" + sheetId + "/submit"), sameName, Map.class);
        assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(String.valueOf(bad.getBody().get("message"))).contains("同一个人");

        // 会签齐，销毁
        Map<String, Object> ok = Map.of(
                "opinion", "销毁", "appraiser", "鉴定人赵", "leader", "钱馆长");
        ResponseEntity<Map> done = http.postForEntity(
                url("/api/appraisals/" + sheetId + "/submit"), ok, Map.class);
        assertThat(done.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(done.getBody().get("status")).isEqualTo("已销毁");
        assertThat(done.getBody().get("closedDate")).isEqualTo(LocalDate.now().toString());

        // 刷新：名单里只剩另一卷，卷宗已销毁
        ResponseEntity<List> pending1 = http.getForEntity(url("/api/appraisals/pending"), List.class);
        assertThat(pending1.getBody()).hasSize(1);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> archiveList =
                http.getForEntity(url("/api/archives"), List.class).getBody();
        Map<String, Object> a = archiveList.stream()
                .filter(x -> inStock.intValue() == ((Number) x.get("id")).intValue())
                .findFirst().orElseThrow();
        assertThat(a.get("status")).isEqualTo("已销毁");

        // 另一卷在库：直接在目录改成已销毁同样被封
        Map<String, Object> patch = Map.of("status", "已销毁");
        org.springframework.http.HttpEntity<Map<String, Object>> entity =
                new org.springframework.http.HttpEntity<>(patch);
        ResponseEntity<Map> directDestroy = http.exchange(
                url("/api/archives/" + untouched),
                org.springframework.http.HttpMethod.PUT, entity, Map.class);
        assertThat(directDestroy.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(String.valueOf(directDestroy.getBody().get("message"))).contains("鉴定台");
    }
}
