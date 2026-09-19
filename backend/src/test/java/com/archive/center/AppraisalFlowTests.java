package com.archive.center;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.archive.center.dto.AppraisalSubmit;
import com.archive.center.dto.BizException;
import com.archive.center.entity.Appraisal;
import com.archive.center.entity.Archive;
import com.archive.center.entity.Retrieval;
import com.archive.center.entity.Room;
import com.archive.center.dto.AppraisalPendingRow;
import com.archive.center.repository.AppraisalRepository;
import com.archive.center.repository.ArchiveRepository;
import com.archive.center.repository.RetrievalRepository;
import com.archive.center.repository.RoomRepository;
import com.archive.center.service.AppraisalService;
import com.archive.center.service.ArchiveService;
import com.archive.center.service.RetrievalService;
import com.archive.center.service.RoomService;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AppraisalFlowTests {

    @Autowired AppraisalService appraisalService;
    @Autowired ArchiveService archiveService;
    @Autowired RetrievalService retrievalService;
    @Autowired RoomService roomService;
    @Autowired AppraisalRepository appraisals;
    @Autowired ArchiveRepository archives;
    @Autowired RetrievalRepository retrievals;
    @Autowired RoomRepository rooms;

    Long aInStock;   // 2016 + 10 = 2026 到期，在库
    Long aInStock2;  // 2015 + 10 = 2025 到期，在库
    Long aFuture;    // 还没到期
    Long aDestroyed; // 已销毁
    Long aOnLoan;    // 到期但调阅中
    Long roomId;

    @BeforeEach
    void seed() {
        appraisals.deleteAll();
        retrievals.deleteAll();
        archives.deleteAll();
        rooms.deleteAll();

        Room r = new Room();
        r.code = "R-T1";
        r.name = "一号库房";
        r.capacity = 100;
        r.status = "在用";
        roomId = rooms.save(r).id;

        aInStock = saveArchive("T-2016-1", 2016, 10, "在库");
        aInStock2 = saveArchive("T-2015-1", 2015, 10, "在库");
        aFuture = saveArchive("T-2050-1", 2050, 10, "在库");
        aDestroyed = saveArchive("T-2014-X", 2014, 10, "已销毁");
        aOnLoan = saveArchive("T-2017-9", 2017, 9, "已借出");

        Retrieval r1 = new Retrieval();
        r1.archiveId = aOnLoan;
        r1.visitor = "借阅人甲";
        r1.dept = "外单位";
        r1.retrieveDate = LocalDate.now().minusDays(3);
        r1.dueDate = LocalDate.now().plusDays(7);
        r1.status = "调阅中";
        retrievals.save(r1);
    }

    private Long saveArchive(String code, int year, int keep, String status) {
        Archive a = new Archive();
        a.code = code + "-" + System.nanoTime();
        a.title = code;
        a.archiveYear = year;
        a.keepYears = keep;
        a.roomId = roomId;
        a.status = status;
        return archives.save(a).id;
    }

    private AppraisalSubmit submit(String opinion, String appraiser, String leader, Integer years) {
        AppraisalSubmit s = new AppraisalSubmit();
        s.opinion = opinion;
        s.appraiser = appraiser;
        s.leader = leader;
        s.extendYears = years;
        return s;
    }

    private Appraisal openOnly(Long archiveId) {
        return appraisals.findAllByOrderByIdDesc().stream()
                .filter(a -> a.archiveId.equals(archiveId) && "未结案".equals(a.status))
                .findFirst().orElseThrow();
    }

    @Test
    void 到期台只收当年及以前的在库卷_借出也进_已销毁不进() {
        List<AppraisalPendingRow> rows = appraisalService.pending();
        List<Long> ids = rows.stream().map(AppraisalPendingRow::archiveId).toList();
        assertThat(ids).contains(aInStock, aInStock2, aOnLoan);
        assertThat(ids).doesNotContain(aFuture, aDestroyed);

        AppraisalPendingRow loanRow = rows.stream().filter(r -> r.archiveId().equals(aOnLoan))
                .findFirst().orElseThrow();
        assertThat(loanRow.outOnLoan()).isTrue();
        assertThat(loanRow.archiveStatus()).isEqualTo("已借出");
    }

    @Test
    void 没到期和已销毁的卷不能开鉴定单() {
        assertThatThrownBy(() -> appraisalService.open(aFuture))
                .isInstanceOf(BizException.class).hasMessageContaining("到期");
        assertThatThrownBy(() -> appraisalService.open(aDestroyed))
                .isInstanceOf(BizException.class).hasMessageContaining("销毁");
        assertThat(appraisals.count()).isZero();
    }

    @Test
    void 同一卷只能有一条未结案鉴定_连开第二次失败() {
        appraisalService.open(aInStock);
        assertThatThrownBy(() -> appraisalService.open(aInStock))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已经在鉴定中");
        assertThat(appraisals.count()).isEqualTo(1);
    }

    @Test
    void 两人几乎同时开同一卷_后到的失败() throws Exception {
        int n = 8;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(n);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        for (int i = 0; i < n; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    appraisalService.open(aInStock);
                    ok.incrementAndGet();
                } catch (BizException e) {
                    if (e.getMessage().contains("已经在鉴定中")) {
                        rejected.incrementAndGet();
                    }
                } catch (Exception ignored) {
                }
            });
        }
        start.countDown();
        pool.shutdown();
        pool.awaitTermination(15, java.util.concurrent.TimeUnit.SECONDS);
        assertThat(ok.get()).isEqualTo(1);
        assertThat(rejected.get()).isEqualTo(n - 1);
        assertThat(appraisals.count()).isEqualTo(1);
    }

    @Test
    void 调阅中的卷能开单但销毁结论提交不了() {
        Appraisal p = appraisalService.open(aOnLoan);
        assertThatThrownBy(() -> appraisalService.submit(p.id,
                submit("销毁", "鉴定人", "分管领导", null)))
                .isInstanceOf(BizException.class).hasMessageContaining("归还");
        Appraisal still = appraisals.findById(p.id).orElseThrow();
        assertThat(still.status).isEqualTo("未结案");
        assertThat(archives.findById(aOnLoan).orElseThrow().status).isEqualTo("已借出");
    }

    @Test
    void 会签校验_意见限定_两人不同名_续存要写年数() {
        Appraisal p = appraisalService.open(aInStock);
        assertThatThrownBy(() -> appraisalService.submit(p.id,
                submit("转卖", "甲", "乙", null)))
                .isInstanceOf(BizException.class).hasMessageContaining("续存");
        assertThatThrownBy(() -> appraisalService.submit(p.id,
                submit("销毁", "甲", "甲", null)))
                .isInstanceOf(BizException.class).hasMessageContaining("同一个人");
        assertThatThrownBy(() -> appraisalService.submit(p.id,
                submit("续存", "甲", "乙", null)))
                .isInstanceOf(BizException.class).hasMessageContaining("续存年数");
        assertThatThrownBy(() -> appraisalService.submit(p.id,
                submit("销毁", "  ", "乙", null)))
                .isInstanceOf(BizException.class).hasMessageContaining("鉴定人");
        assertThat(appraisals.findById(p.id).orElseThrow().status).isEqualTo("未结案");
    }

    @Test
    void 会签齐销毁_卷已销毁_库房当天腾出_不能再调阅() {
        long storedBefore = roomService.stored(roomId);
        Appraisal p = appraisalService.open(aInStock);
        Appraisal done = appraisalService.submit(p.id,
                submit("销毁", "鉴定人赵", "钱馆长", null));

        assertThat(done.status).isEqualTo("已销毁");
        assertThat(done.closedDate).isEqualTo(LocalDate.now());
        assertThat(archives.findById(aInStock).orElseThrow().status).isEqualTo("已销毁");
        assertThat(roomService.stored(roomId)).isEqualTo(storedBefore - 1);

        List<Long> ids = appraisalService.pending().stream()
                .map(AppraisalPendingRow::archiveId).toList();
        assertThat(ids).doesNotContain(aInStock);

        Retrieval borrow = new Retrieval();
        borrow.archiveId = aInStock;
        borrow.visitor = "谁";
        borrow.dept = "哪";
        borrow.retrieveDate = LocalDate.now();
        borrow.dueDate = LocalDate.now().plusDays(1);
        assertThatThrownBy(() -> retrievalService.create(borrow))
                .isInstanceOf(BizException.class).hasMessageContaining("销毁");
    }

    @Test
    void 会签齐续存_期限加上去_保持在库_从到期台消失() {
        Appraisal p = appraisalService.open(aInStock);
        Appraisal done = appraisalService.submit(p.id,
                submit("续存", "鉴定人赵", "钱馆长", 5));

        assertThat(done.status).isEqualTo("已续存");
        assertThat(done.extendYears).isEqualTo(5);
        Archive a = archives.findById(aInStock).orElseThrow();
        assertThat(a.keepYears).isEqualTo(15);
        assertThat(a.archiveYear + a.keepYears).isEqualTo(2031);
        assertThat(a.status).isEqualTo("在库");

        List<Long> ids = appraisalService.pending().stream()
                .map(AppraisalPendingRow::archiveId).toList();
        assertThat(ids).doesNotContain(aInStock);
    }

    @Test
    void 会签期间目录改保管期限_目录侧直接被拒() {
        Appraisal p = appraisalService.open(aInStock);
        Archive patch = new Archive();
        patch.keepYears = 20;
        assertThatThrownBy(() -> archiveService.update(aInStock, patch))
                .isInstanceOf(BizException.class).hasMessageContaining("会签");
        assertThat(archives.findById(aInStock).orElseThrow().keepYears).isEqualTo(10);
        assertThat(appraisals.findById(p.id).orElseThrow().status).isEqualTo("未结案");
    }

    @Test
    void 会签期间目录状态被改_提交失败单子不结案并说明目录动过() {
        Appraisal p = appraisalService.open(aInStock);
        Archive a = archives.findById(aInStock).orElseThrow();
        a.status = "已销毁";
        archives.save(a);
        assertThatThrownBy(() -> appraisalService.submit(p.id,
                submit("销毁", "鉴定人赵", "钱馆长", null)))
                .isInstanceOf(BizException.class).hasMessageContaining("目录");
        assertThat(appraisals.findById(p.id).orElseThrow().status).isEqualTo("未结案");

        Archive b = archives.findById(aInStock).orElseThrow();
        b.status = "在库";
        b.keepYears = 30;
        archives.save(b);
        assertThatThrownBy(() -> appraisalService.submit(openOnly(aInStock).id,
                submit("销毁", "鉴定人赵", "钱馆长", null)))
                .isInstanceOf(BizException.class).hasMessageContaining("保管期限");
        assertThat(openOnly(aInStock).status).isEqualTo("未结案");
    }

    @Test
    void 目录侧直接改成已销毁一律拒绝() {
        Archive patch = new Archive();
        patch.status = "已销毁";
        assertThatThrownBy(() -> archiveService.update(aInStock2, patch))
                .isInstanceOf(BizException.class).hasMessageContaining("鉴定台");
        assertThat(archives.findById(aInStock2).orElseThrow().status).isEqualTo("在库");
    }

    @Test
    void 续存提交时卷刚被借走_续存失败期限不变() {
        Appraisal p = appraisalService.open(aInStock2);
        Retrieval borrow = new Retrieval();
        borrow.archiveId = aInStock2;
        borrow.visitor = "临时借阅";
        borrow.dept = "某单位";
        borrow.retrieveDate = LocalDate.now();
        borrow.dueDate = LocalDate.now().plusDays(3);
        retrievalService.create(borrow);

        assertThatThrownBy(() -> appraisalService.submit(p.id,
                submit("续存", "鉴定人赵", "钱馆长", 3)))
                .isInstanceOf(BizException.class).hasMessageContaining("借走");
        assertThat(archives.findById(aInStock2).orElseThrow().keepYears).isEqualTo(10);
        assertThat(appraisals.findById(p.id).orElseThrow().status).isEqualTo("未结案");
    }

    @Test
    void 调阅中归还后销毁能走通() {
        Appraisal p = appraisalService.open(aOnLoan);
        Retrieval out = retrievals.findByArchiveIdAndStatus(aOnLoan, "调阅中").get(0);
        retrievalService.giveBack(out.id, LocalDate.now());

        Appraisal done = appraisalService.submit(p.id,
                submit("销毁", "鉴定人赵", "钱馆长", null));
        assertThat(done.status).isEqualTo("已销毁");
        assertThat(archives.findById(aOnLoan).orElseThrow().status).isEqualTo("已销毁");
    }

    @Test
    void 刷新后各状态数量一致() {
        Appraisal p1 = appraisalService.open(aInStock);
        appraisalService.open(aInStock2);
        appraisalService.submit(p1.id, submit("销毁", "鉴定人赵", "钱馆长", null));

        assertThat(appraisals.findAllByOrderByIdDesc().stream()
                .filter(a -> "未结案".equals(a.status)).count()).isEqualTo(1);
        assertThat(appraisals.findAllByOrderByIdDesc().stream()
                .filter(a -> "已销毁".equals(a.status)).count()).isEqualTo(1);
        assertThat(appraisals.findAllByOrderByIdDesc().stream()
                .filter(a -> "已续存".equals(a.status)).count()).isZero();
        assertThat(roomService.stored(roomId))
                .isEqualTo(archives.findByRoomIdAndStatusNot(roomId, "已销毁").size());
    }
}
