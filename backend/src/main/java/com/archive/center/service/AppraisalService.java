package com.archive.center.service;

import com.archive.center.dto.AppraisalPendingRow;
import com.archive.center.dto.AppraisalSubmit;
import com.archive.center.dto.BizException;
import com.archive.center.entity.Appraisal;
import com.archive.center.entity.Archive;
import com.archive.center.repository.AppraisalRepository;
import com.archive.center.repository.ArchiveRepository;
import com.archive.center.repository.RetrievalRepository;
import com.archive.center.repository.RoomRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppraisalService {

    public static final String OPEN = "未结案";
    public static final String DESTROYED = "已销毁";
    public static final String RENEWED = "已续存";

    private final AppraisalRepository appraisals;
    private final ArchiveRepository archives;
    private final RetrievalRepository retrievals;
    private final RoomRepository rooms;

    public AppraisalService(AppraisalRepository appraisals, ArchiveRepository archives,
                            RetrievalRepository retrievals, RoomRepository rooms) {
        this.appraisals = appraisals;
        this.archives = archives;
        this.retrievals = retrievals;
        this.rooms = rooms;
    }

    /**
     * 到期鉴定台名单：归档年度 + 保管期限年数 ≤ 今年、且没有销毁的在库卷（含借出在外）都进台。
     * 已销毁的卷不再进台。
     */
    public List<AppraisalPendingRow> pending() {
        int year = LocalDate.now().getYear();
        Map<Long, String> roomNames = rooms.findAllByOrderByIdAsc().stream()
                .collect(Collectors.toMap(r -> r.id, r -> r.name));
        Map<Long, Appraisal> openMap = appraisals.findAllByOrderByIdDesc().stream()
                .filter(a -> OPEN.equals(a.status))
                .collect(Collectors.toMap(a -> a.archiveId, Function.identity(), (x, y) -> x));
        return archives.findAllByOrderByIdAsc().stream()
                .filter(a -> !"已销毁".equals(a.status))
                .filter(a -> a.archiveYear + a.keepYears <= year)
                .map(a -> {
                    Appraisal open = openMap.get(a.id);
                    boolean outOnLoan =
                            !retrievals.findByArchiveIdAndStatus(a.id, "调阅中").isEmpty();
                    return new AppraisalPendingRow(
                            a.id, a.code, a.title, a.archiveYear, a.keepYears,
                            a.archiveYear + a.keepYears, a.roomId,
                            roomNames.getOrDefault(a.roomId, String.valueOf(a.roomId)),
                            a.status, outOnLoan,
                            open == null ? null : open.id,
                            open == null ? null : open.createdDate,
                            open == null ? null : open.appraiser,
                            open == null ? null : open.leader);
                })
                .toList();
    }

    public List<Appraisal> list(String status) {
        return appraisals.findAllByOrderByIdDesc().stream()
                .filter(a -> status == null || status.isEmpty() || status.equals(a.status))
                .toList();
    }

    /** 开鉴定单：两人几乎同时给同一卷开单，后到的拿不到行锁，看到已有未结案单后失败。 */
    @Transactional
    public Appraisal open(Long archiveId) {
        if (archiveId == null) {
            throw new BizException("请选择要鉴定的卷宗");
        }
        Archive a = archives.findForUpdate(archiveId)
                .orElseThrow(() -> new BizException("卷宗不存在"));
        if ("已销毁".equals(a.status)) {
            throw new BizException("这卷已经销毁了，不用再鉴定");
        }
        int year = LocalDate.now().getYear();
        if (a.archiveYear + a.keepYears > year) {
            throw new BizException("这卷要到 " + (a.archiveYear + a.keepYears)
                    + " 年才到期，现在还不能鉴定");
        }
        if (appraisals.existsByArchiveIdAndStatus(a.id, OPEN)) {
            throw new BizException("卷宗 " + a.code + " 已经在鉴定中，不能重复开鉴定单");
        }
        Appraisal saved = new Appraisal();
        saved.archiveId = a.id;
        saved.status = OPEN;
        saved.createdDate = LocalDate.now();
        saved.snapshotKeepYears = a.keepYears;
        saved.snapshotStatus = a.status;
        return appraisals.save(saved);
    }

    /** 会签提交：意见齐、两个人名不重复；销毁要卷已归还，续存要卷还在库。 */
    @Transactional
    public Appraisal submit(Long id, AppraisalSubmit input) {
        if (input == null || input.opinion == null
                || (!"销毁".equals(input.opinion) && !"续存".equals(input.opinion))) {
            throw new BizException("鉴定意见只许是「续存」或「销毁」");
        }
        if (input.appraiser == null || input.appraiser.isBlank()) {
            throw new BizException("要写鉴定人");
        }
        if (input.leader == null || input.leader.isBlank()) {
            throw new BizException("要写分管领导");
        }
        String appraiser = input.appraiser.trim();
        String leader = input.leader.trim();
        if (appraiser.equals(leader)) {
            throw new BizException("鉴定人和分管领导不能是同一个人，会签要两个人");
        }
        Appraisal p = appraisals.findByIdAndStatus(id, OPEN)
                .orElseThrow(() -> new BizException("鉴定单不存在或已经结案"));
        Archive a = archives.findForUpdate(p.archiveId)
                .orElseThrow(() -> new BizException("卷宗不存在"));

        // 会签进行到一半，目录侧若擅自动过：直接改已销毁 / 改保管期限，单子保持未结案。
        // 借出→归还（已借出变回在库）是正常调阅流程，不算目录侧动手脚。
        if (!a.keepYears.equals(p.snapshotKeepYears)) {
            throw new BizException("会签期间案卷目录已经改过保管期限，鉴定提交失败，单子保持未结案");
        }
        if ("已销毁".equals(a.status)
                || (!"在库".equals(a.status) && !"已借出".equals(a.status))) {
            throw new BizException("会签期间案卷目录已经把这卷改成「" + a.status
                    + "」，鉴定提交失败，单子保持未结案");
        }

        boolean outOnLoan = !retrievals.findByArchiveIdAndStatus(a.id, "调阅中").isEmpty();
        LocalDate today = LocalDate.now();
        if ("销毁".equals(input.opinion)) {
            if (outOnLoan) {
                throw new BizException("这卷还在调阅中，等归还以后才能提交销毁结论");
            }
            a.status = "已销毁";
            archives.save(a);
            p.opinion = "销毁";
            p.status = DESTROYED;
            p.closedDate = today;
        } else {
            if (outOnLoan) {
                throw new BizException("这卷刚被借走，续存提交失败，保管期限维持原值");
            }
            if (input.extendYears == null || input.extendYears <= 0) {
                throw new BizException("结论是续存，必须写续存年数");
            }
            a.keepYears = a.keepYears + input.extendYears;
            archives.save(a);
            p.opinion = "续存";
            p.extendYears = input.extendYears;
            p.status = RENEWED;
            p.closedDate = today;
        }
        p.appraiser = appraiser;
        p.leader = leader;
        return appraisals.save(p);
    }
}
