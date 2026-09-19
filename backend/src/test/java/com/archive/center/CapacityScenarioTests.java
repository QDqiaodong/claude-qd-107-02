package com.archive.center;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.archive.center.dto.BizException;
import com.archive.center.entity.Archive;
import com.archive.center.entity.Room;
import com.archive.center.repository.ArchiveRepository;
import com.archive.center.repository.RoomRepository;
import com.archive.center.service.ArchiveService;
import com.archive.center.service.RoomService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 容量口径（在服务层）：
 * 1. 容量改小到在册数以下必须打回并留说明，容量不动；
 * 2. 已经超容的库不能再收新卷；
 * 3. 已经超容的库不能从别的库改挂卷进来；
 * 4. 服务端回填的在册数 / 还能放几卷 / 超容说明彼此对得上。
 */
@SpringBootTest
class CapacityScenarioTests {

    @Autowired RoomService roomService;
    @Autowired ArchiveService archiveService;
    @Autowired RoomRepository rooms;
    @Autowired ArchiveRepository archives;

    Long room3;
    Long room1;

    @BeforeEach
    void seed() {
        archives.deleteAll();
        rooms.deleteAll();

        room3 = newRoom("R-03", "三号库房", 40);
        room1 = newRoom("R-01", "一号库房", 100);

        for (int i = 1; i <= 15; i++) {
            newArchive("D-2026-" + String.format("%03d", i), room3, "在库");
        }
        newArchive("D-2026-100", room1, "在库");
    }

    private Long newRoom(String code, String name, int capacity) {
        Room r = new Room();
        r.code = code + "-" + System.nanoTime();
        r.name = name;
        r.capacity = capacity;
        r.status = "在用";
        r.tempMin = 14.0;
        r.tempMax = 24.0;
        r.humidityMin = 45.0;
        r.humidityMax = 60.0;
        r.sealed = false;
        return rooms.save(r).id;
    }

    private Long newArchive(String code, Long roomId, String status) {
        Archive a = new Archive();
        a.code = code + "-" + System.nanoTime();
        a.title = code;
        a.archiveYear = 2026;
        a.keepYears = 10;
        a.roomId = roomId;
        a.status = status;
        return archives.save(a).id;
    }

    @Test
    void 容量改小到在册数以下必须打回并留说明_容量不动() {
        assertThat(roomService.stored(room3)).isEqualTo(15);

        Room patch = new Room();
        patch.capacity = 10;
        assertThatThrownBy(() -> roomService.update(room3, patch))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("在册 15")
                .hasMessageContaining("挪走");

        assertThat(rooms.findById(room3).orElseThrow().capacity).isEqualTo(40);
    }

    @Test
    void 容量改到与在册数相等是允许的_还能放零卷() {
        Room patch = new Room();
        patch.capacity = 15;
        Room saved = roomService.update(room3, patch);
        assertThat(saved.capacity).isEqualTo(15);
        assertThat(saved.registered).isEqualTo(15);
        assertThat(saved.availableSlots).isEqualTo(0);
        assertThat(saved.overCapacity).isFalse();
    }

    @Test
    void 已经超容的库不能再收新卷() {
        Room entity = rooms.findById(room3).orElseThrow();
        entity.capacity = 10;
        rooms.save(entity);

        Archive input = new Archive();
        input.code = "D-2026-9999-" + System.nanoTime();
        input.title = "新来的卷";
        input.archiveYear = 2026;
        input.keepYears = 10;
        input.roomId = room3;
        assertThatThrownBy(() -> archiveService.create(input))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("超容");
        assertThat(roomService.stored(room3)).isEqualTo(15);
    }

    @Test
    void 超容库不能从别的库改挂卷进来_卷还留在原库() {
        Room entity = rooms.findById(room3).orElseThrow();
        entity.capacity = 10;
        rooms.save(entity);

        Long onRoom1 = archives.findAllByOrderByIdAsc().stream()
                .filter(a -> a.roomId.equals(room1)).findFirst().orElseThrow().id;
        Archive patch = new Archive();
        patch.roomId = room3;
        assertThatThrownBy(() -> archiveService.update(onRoom1, patch))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("超容");
        assertThat(archives.findById(onRoom1).orElseThrow().roomId).isEqualTo(room1);
    }

    @Test
    void 刷新后在册数_还能放几卷_超容说明对得上() {
        // 正常库
        Room ok = roomService.fillCapacityStats(rooms.findById(room1).orElseThrow());
        assertThat(ok.registered).isEqualTo(1);
        assertThat(ok.availableSlots).isEqualTo(99);
        assertThat(ok.overCapacity).isFalse();
        assertThat(ok.capacityNote).isNull();

        // 借出的卷仍占在册位置：在库 + 已借出都算
        newArchive("D-2026-200", room1, "已借出");
        ok = roomService.fillCapacityStats(rooms.findById(room1).orElseThrow());
        assertThat(ok.registered).isEqualTo(2);
        assertThat(ok.availableSlots).isEqualTo(98);

        // 已销毁的卷不占位置
        Long dead = newArchive("D-2026-300", room3, "已销毁");
        Room r3 = roomService.fillCapacityStats(rooms.findById(room3).orElseThrow());
        assertThat(r3.registered).isEqualTo(15);

        // 制造超容后说明齐全，还能放给 0 而不是负数
        Room force = rooms.findById(room3).orElseThrow();
        force.capacity = 10;
        rooms.save(force);
        archives.deleteById(dead);
        r3 = roomService.list(null, null).stream().filter(x -> x.id.equals(room3))
                .findFirst().orElseThrow();
        assertThat(r3.registered).isEqualTo(15);
        assertThat(r3.availableSlots).isEqualTo(0);
        assertThat(r3.overCapacity).isTrue();
        assertThat(r3.capacityNote).contains("在册 15").contains("10").contains("多出 5");
    }
}
