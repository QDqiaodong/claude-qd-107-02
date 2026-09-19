package com.archive.center;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.archive.center.dto.BizException;
import com.archive.center.entity.Archive;
import com.archive.center.entity.Retrieval;
import com.archive.center.entity.Room;
import com.archive.center.entity.RoomCheck;
import com.archive.center.repository.AppraisalRepository;
import com.archive.center.repository.ArchiveRepository;
import com.archive.center.repository.RetrievalRepository;
import com.archive.center.repository.RoomCheckRepository;
import com.archive.center.repository.RoomRepository;
import com.archive.center.service.CheckService;
import com.archive.center.service.RetrievalService;
import com.archive.center.service.RoomService;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** 封库口径：连续两班越限才封、一班只预警、回温才解封、历史班次改数解不了。 */
@SpringBootTest
class SealFlowTests {

    @Autowired CheckService checkService;
    @Autowired RetrievalService retrievalService;
    @Autowired RoomService roomService;
    @Autowired RoomCheckRepository checks;
    @Autowired RetrievalRepository retrievals;
    @Autowired ArchiveRepository archives;
    @Autowired RoomRepository rooms;
    @Autowired AppraisalRepository appraisals;

    Long roomId;
    Long aInStock;  // 在库，可借
    Long aOnLoan;   // 已借出，调阅中
    Long loanId;    // aOnLoan 的未归还调阅单

    @BeforeEach
    void seed() {
        appraisals.deleteAll();
        checks.deleteAll();
        retrievals.deleteAll();
        archives.deleteAll();
        rooms.deleteAll();

        Room r = new Room();
        r.code = "R-F1";
        r.name = "封测库房";
        r.capacity = 10;
        r.status = "在用";
        r.tempMin = 14.0;
        r.tempMax = 24.0;
        r.humidityMin = 45.0;
        r.humidityMax = 60.0;
        r.sealed = false;
        roomId = rooms.save(r).id;

        aInStock = saveArchive("F-2020-1", "在库");
        aOnLoan = saveArchive("F-2020-2", "已借出");

        Retrieval loan = new Retrieval();
        loan.archiveId = aOnLoan;
        loan.visitor = "借阅人";
        loan.dept = "外单位";
        loan.retrieveDate = LocalDate.now().minusDays(2);
        loan.dueDate = LocalDate.now().plusDays(5);
        loan.status = "调阅中";
        loanId = retrievals.save(loan).id;
    }

    private Long saveArchive(String code, String status) {
        Archive a = new Archive();
        a.code = code + "-" + System.nanoTime();
        a.title = code;
        a.archiveYear = 2020;
        a.keepYears = 30;
        a.roomId = roomId;
        a.status = status;
        return archives.save(a).id;
    }

    private RoomCheck check(LocalDate date, double t, double h) {
        RoomCheck c = new RoomCheck();
        c.roomId = roomId;
        c.checkDate = date;
        c.temperature = t;
        c.humidity = h;
        c.issueDesc = "越限说明";
        c.checker = "测试员";
        return checkService.create(c);
    }

    private Room room() {
        return rooms.findById(roomId).orElseThrow();
    }

    private Retrieval borrow(Long archiveId) {
        Retrieval b = new Retrieval();
        b.archiveId = archiveId;
        b.visitor = "调阅人";
        b.dept = "某单位";
        b.retrieveDate = LocalDate.now();
        b.dueDate = LocalDate.now().plusDays(3);
        return retrievalService.create(b);
    }

    private long presentInRoom() {
        return archives.findAllByOrderByIdAsc().stream()
                .filter(a -> a.roomId.equals(roomId) && "在库".equals(a.status))
                .count();
    }

    @Test
    void 只越一班只预警不封库_新调阅照常() {
        check(LocalDate.of(2026, 9, 17), 26.0, 50);   // 温度越上限，只此一班
        assertThat(room().sealed).isFalse();
        Retrieval ok = borrow(aInStock);              // 只预警不封：新调阅开得成
        assertThat(ok.status).isEqualTo("调阅中");
    }

    @Test
    void 连续两班越限封库_新调阅开不成_归还照常_现放当天加回() {
        LocalDate d1 = LocalDate.of(2026, 9, 17);
        LocalDate d2 = LocalDate.of(2026, 9, 18);
        check(d1, 26.0, 50);
        assertThat(room().sealed).isFalse();          // 第一班只预警
        check(d2, 27.0, 61);                          // 连续第二班还越：封
        assertThat(room().sealed).isTrue();
        assertThat(room().sealedDate).isEqualTo(d2);

        long storedBefore = roomService.stored(roomId);
        long presentBefore = presentInRoom();
        // 封库期间硬开新单：失败说明在，单子落不成调阅中，卷不动、现放不减
        assertThatThrownBy(() -> borrow(aInStock))
                .isInstanceOf(BizException.class).hasMessageContaining("封库");
        assertThat(archives.findById(aInStock).orElseThrow().status).isEqualTo("在库");
        assertThat(retrievals.findByArchiveIdAndStatus(aInStock, "调阅中")).isEmpty();
        assertThat(presentInRoom()).isEqualTo(presentBefore);
        assertThat(roomService.stored(roomId)).isEqualTo(storedBefore);

        // 封库期间归还照常收下：卷当天回在库，现放卷数当天加回
        retrievalService.giveBack(loanId, LocalDate.now());
        assertThat(archives.findById(aOnLoan).orElseThrow().status).isEqualTo("在库");
        assertThat(presentInRoom()).isEqualTo(presentBefore + 1);
        assertThat(room().sealed).isTrue();           // 归还不影响封库
    }

    @Test
    void 封库中抄表口不堵_下一班回区间即回温_新调阅恢复() {
        check(LocalDate.of(2026, 9, 17), 26.0, 50);
        check(LocalDate.of(2026, 9, 18), 27.0, 61);
        assertThat(room().sealed).isTrue();

        // 封库不堵抄表口：本班抄表照常登记，回到区间内即回温
        RoomCheck back = check(LocalDate.of(2026, 9, 19), 22.0, 50);
        assertThat(back.result).isEqualTo("正常");
        assertThat(room().sealed).isFalse();
        assertThat(room().sealedDate).isNull();

        // 回温后新调阅能再开；已借出未还的卷继续算外借，不当成已回来
        assertThat(archives.findById(aOnLoan).orElseThrow().status).isEqualTo("已借出");
        assertThat(retrievals.findById(loanId).orElseThrow().status).isEqualTo("调阅中");
        Retrieval ok = borrow(aInStock);
        assertThat(ok.status).isEqualTo("调阅中");
    }

    @Test
    void 回温前补录历史班次到区间内_封库不解除_新调阅仍开不成() {
        check(LocalDate.of(2026, 9, 17), 26.0, 50);
        check(LocalDate.of(2026, 9, 18), 27.0, 61);
        assertThat(room().sealed).isTrue();

        // 把历史班次补成区间内的数（补录更早日期的正常抄表）：封库不动
        check(LocalDate.of(2026, 9, 10), 21.0, 50);
        check(LocalDate.of(2026, 9, 11), 20.5, 48);
        assertThat(room().sealed).isTrue();
        assertThatThrownBy(() -> borrow(aInStock))
                .isInstanceOf(BizException.class).hasMessageContaining("封库");

        // 只有最新一班抄表回到区间内才回温
        check(LocalDate.of(2026, 9, 19), 22.0, 50);
        assertThat(room().sealed).isFalse();
    }

    @Test
    void 越下限和湿度越限同样算越限() {
        // 温度连续两班越下限：封
        check(LocalDate.of(2026, 9, 17), 10.0, 50);
        check(LocalDate.of(2026, 9, 18), 11.0, 52);
        assertThat(room().sealed).isTrue();
        check(LocalDate.of(2026, 9, 19), 20.0, 50);   // 回温
        assertThat(room().sealed).isFalse();
        // 湿度越上限：一班预警，两班才封
        check(LocalDate.of(2026, 9, 20), 22.0, 66);
        assertThat(room().sealed).isFalse();
        check(LocalDate.of(2026, 9, 21), 23.0, 68);
        assertThat(room().sealed).isTrue();
    }

    @Test
    void 封库期间越限班继续_仍封且封库起始日不动() {
        check(LocalDate.of(2026, 9, 17), 26.0, 50);
        check(LocalDate.of(2026, 9, 18), 27.0, 61);
        assertThat(room().sealed).isTrue();
        check(LocalDate.of(2026, 9, 19), 28.0, 62);   // 第三班还越：继续封
        assertThat(room().sealed).isTrue();
        assertThat(room().sealedDate).isEqualTo(LocalDate.of(2026, 9, 18));
    }

    @Test
    void 库房接口改状态改区间都解不了封_只有抄表回温才解() {
        check(LocalDate.of(2026, 9, 17), 26.0, 50);
        check(LocalDate.of(2026, 9, 18), 27.0, 61);
        assertThat(room().sealed).isTrue();

        // 试图经库房接口抹掉封库：接口根本不看 sealed 字段
        Room patch = new Room();
        patch.sealed = false;
        patch.name = "封测库房改名";
        roomService.update(roomId, patch);
        assertThat(room().sealed).isTrue();
        assertThat(room().name).isEqualTo("封测库房改名");

        // 把上下限放宽到历史班次都落进区间：封库照样不解
        Room widen = new Room();
        widen.tempMin = 0.0;
        widen.tempMax = 40.0;
        widen.humidityMin = 0.0;
        widen.humidityMax = 100.0;
        roomService.update(roomId, widen);
        assertThat(room().sealed).isTrue();
        assertThatThrownBy(() -> borrow(aInStock))
                .isInstanceOf(BizException.class).hasMessageContaining("封库");

        // 只有下一班抄表回到（新）区间内才回温
        check(LocalDate.of(2026, 9, 19), 25.0, 55);
        assertThat(room().sealed).isFalse();
    }
}
