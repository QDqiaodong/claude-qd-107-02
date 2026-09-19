package com.archive.center;

import static org.assertj.core.api.Assertions.assertThat;

import com.archive.center.entity.Archive;
import com.archive.center.entity.Room;
import com.archive.center.repository.ArchiveRepository;
import com.archive.center.repository.RoomRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** 三号库容量从 40 改到 10 的完整容量口径走真实 HTTP。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CapacityHttpTests {

    @LocalServerPort int port;
    @Autowired TestRestTemplate http;
    @Autowired RoomRepository rooms;
    @Autowired ArchiveRepository archives;

    Long r3;
    Long r1;

    @BeforeEach
    void seed() {
        archives.deleteAll();
        rooms.deleteAll();
        r3 = saveRoom("R-03", "三号库房", 40, "整理");
        r1 = saveRoom("R-01", "一号库房", 100, "在用");
    }

    private Long saveRoom(String code, String name, int cap, String status) {
        Room r = new Room();
        r.code = code + "-" + System.nanoTime();
        r.name = name;
        r.capacity = cap;
        r.status = status;
        r.tempMin = 14.0;
        r.tempMax = 24.0;
        r.humidityMin = 45.0;
        r.humidityMax = 60.0;
        r.sealed = false;
        return rooms.save(r).id;
    }

    private Long saveArchive(String code, Long roomId, String status) {
        Archive a = new Archive();
        a.code = code + "-" + System.nanoTime();
        a.title = code;
        a.archiveYear = 2026;
        a.keepYears = 10;
        a.roomId = roomId;
        a.status = status;
        return archives.save(a).id;
    }

    private String url(String p) {
        return "http://localhost:" + port + p;
    }

    private ResponseEntity<Map> put(String path, Object body) {
        return http.exchange(url(path), HttpMethod.PUT, new HttpEntity<>(body), Map.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> roomJson(Long id) {
        List<Map<String, Object>> list = http.getForEntity(url("/api/rooms"), List.class).getBody();
        return list.stream()
                .filter(x -> id.intValue() == ((Number) x.get("id")).intValue())
                .findFirst().orElseThrow();
    }

    @Test
    void 三号库容量改小打回_超容不收新卷不改挂_刷新三件事对得上() {
        // 三号库先从整理改成在用（此刻空，允许）
        Map<String, Object> open = new HashMap<>();
        open.put("status", "在用");
        assertThat(put("/api/rooms/" + r3, open).getStatusCode()).isEqualTo(HttpStatus.OK);

        // 往里塞 15 卷
        for (int i = 0; i < 15; i++) {
            Map<String, Object> body = new HashMap<>();
            body.put("code", "D-2026-" + System.nanoTime() + i);
            body.put("title", "卷" + i);
            body.put("archiveYear", 2026);
            body.put("keepYears", 10);
            body.put("roomId", r3);
            assertThat(http.postForEntity(url("/api/archives"), body, Map.class).getStatusCode())
                    .isEqualTo(HttpStatus.OK);
        }

        // 容量 40 -> 10：400 + 失败说明，容量仍是 40
        Map<String, Object> cut = new HashMap<>();
        cut.put("capacity", 10);
        ResponseEntity<Map> denied = put("/api/rooms/" + r3, cut);
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(String.valueOf(denied.getBody().get("message")))
                .contains("在册 15").contains("挪走");
        Map<String, Object> r3json = roomJson(r3);
        assertThat(r3json.get("capacity")).isEqualTo(40);
        assertThat(r3json.get("registered")).isEqualTo(15);
        assertThat(r3json.get("availableSlots")).isEqualTo(25);
        assertThat(r3json.get("overCapacity")).isEqualTo(false);

        // 模拟“只改数字糊过去”：库里直接把容量改成 10（在册 15，超容 5）
        Room entity = rooms.findById(r3).orElseThrow();
        entity.capacity = 10;
        rooms.save(entity);

        // 刷新后：在册 15、还能放 0（不是负数）、超容说明齐
        r3json = roomJson(r3);
        assertThat(r3json.get("registered")).isEqualTo(15);
        assertThat(r3json.get("availableSlots")).isEqualTo(0);
        assertThat(r3json.get("overCapacity")).isEqualTo(true);
        assertThat(String.valueOf(r3json.get("capacityNote")))
                .contains("在册 15").contains("10").contains("多出 5");

        // 已经超容：收新卷被拒
        Map<String, Object> newbie = new HashMap<>();
        newbie.put("code", "D-2026-" + System.nanoTime());
        newbie.put("title", "新卷");
        newbie.put("archiveYear", 2026);
        newbie.put("keepYears", 10);
        newbie.put("roomId", r3);
        ResponseEntity<Map> addDenied =
                http.postForEntity(url("/api/archives"), newbie, Map.class);
        assertThat(addDenied.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(String.valueOf(addDenied.getBody().get("message"))).contains("超容");

        // 一号库的卷改挂进三号库被拒，卷还在一号库
        Long a1 = saveArchive("D-2026-X", r1, "在库");
        Map<String, Object> move = new HashMap<>();
        move.put("roomId", r3);
        ResponseEntity<Map> moveDenied = put("/api/archives/" + a1, move);
        assertThat(moveDenied.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(String.valueOf(moveDenied.getBody().get("message"))).contains("超容");
        assertThat(archives.findById(a1).orElseThrow().roomId).isEqualTo(r1);

        // 挪走 5 卷后三号库回到容量内：还能放恢复为 0、超容标记和说明消失
        archives.findAllByOrderByIdAsc().stream()
                .filter(a -> a.roomId.equals(r3))
                .limit(5)
                .forEach(a -> archives.deleteById(a.id));
        r3json = roomJson(r3);
        assertThat(r3json.get("registered")).isEqualTo(10);
        assertThat(r3json.get("availableSlots")).isEqualTo(0);
        assertThat(r3json.get("overCapacity")).isEqualTo(false);
        assertThat(r3json.get("capacityNote")).isNull();
    }
}
