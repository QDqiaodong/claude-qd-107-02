package com.archive.center.service;

import com.archive.center.dto.BizException;
import com.archive.center.entity.Archive;
import com.archive.center.entity.Retrieval;
import com.archive.center.entity.Room;
import com.archive.center.repository.ArchiveRepository;
import com.archive.center.repository.RetrievalRepository;
import com.archive.center.repository.RoomRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RetrievalService {

    private final RetrievalRepository retrievals;
    private final ArchiveRepository archives;
    private final RoomRepository rooms;

    public RetrievalService(RetrievalRepository retrievals, ArchiveRepository archives,
                            RoomRepository rooms) {
        this.retrievals = retrievals;
        this.archives = archives;
        this.rooms = rooms;
    }

    public List<Retrieval> list(Long archiveId, String status, String visitor) {
        return retrievals.findAllByOrderByIdDesc().stream()
                .filter(r -> archiveId == null || archiveId.equals(r.archiveId))
                .filter(r -> status == null || status.isEmpty() || status.equals(r.status))
                .filter(r -> visitor == null || visitor.isEmpty() || r.visitor.contains(visitor))
                .toList();
    }

    @Transactional
    public Retrieval create(Retrieval input) {
        if (input.archiveId == null) {
            throw new BizException("请选择要调阅的卷宗");
        }
        if (input.visitor == null || input.visitor.isBlank()) {
            throw new BizException("要填调阅人");
        }
        if (input.dept == null || input.dept.isBlank()) {
            throw new BizException("要填调阅人所在单位");
        }
        if (input.retrieveDate == null || input.dueDate == null) {
            throw new BizException("调阅日期和应还日期都要填");
        }
        if (!input.dueDate.isAfter(input.retrieveDate)) {
            throw new BizException("应还日期要晚于调阅日期");
        }
        Archive archive = archives.findById(input.archiveId)
                .orElseThrow(() -> new BizException("卷宗不存在"));
        if ("已销毁".equals(archive.status)) {
            throw new BizException("这卷已经销毁了，调阅不了");
        }
        if (!"在库".equals(archive.status)) {
            throw new BizException("这卷现在是「" + archive.status + "」，不在库里，借不了");
        }
        if (!retrievals.findByArchiveIdAndStatus(archive.id, "调阅中").isEmpty()) {
            throw new BizException("这卷已经有人借走了，还回来才能再借");
        }
        // 封库口径：所在库房连续两班抄表越限未回温时，该库新调阅一律开不成；
        // 单子落不成、卷宗状态不动、现放卷数不减。已借出的卷归还照常（见 giveBack）。
        Room room = rooms.findById(archive.roomId)
                .orElseThrow(() -> new BizException("卷宗所在库房不存在"));
        if (Boolean.TRUE.equals(room.sealed)) {
            throw new BizException("库房「" + room.name + "」连续两班抄表越限，封库中："
                    + "新调阅开不成，等下一班抄表回到本库区间回温后再办；已借出的卷归还照常");
        }
        Retrieval saved = new Retrieval();
        saved.archiveId = archive.id;
        saved.visitor = input.visitor.trim();
        saved.dept = input.dept.trim();
        saved.retrieveDate = input.retrieveDate;
        saved.dueDate = input.dueDate;
        saved.status = "调阅中";

        archive.status = "已借出";
        archives.save(archive);
        return retrievals.save(saved);
    }

    @Transactional
    public Retrieval giveBack(Long id, LocalDate returnDate) {
        Retrieval r = retrievals.findById(id).orElseThrow(() -> new BizException("调阅记录不存在"));
        if (!"调阅中".equals(r.status)) {
            throw new BizException("这条调阅已经还过了");
        }
        Archive archive = archives.findById(r.archiveId)
                .orElseThrow(() -> new BizException("卷宗不存在"));
        r.returnDate = returnDate == null ? LocalDate.now() : returnDate;
        r.status = "已归还";
        archive.status = "在库";
        archives.save(archive);
        return retrievals.save(r);
    }

    /** 已经过了应还日期还没还的，超了几天。 */
    public long overdueDays(Retrieval r) {
        if (!"调阅中".equals(r.status)) {
            return 0;
        }
        long days = java.time.temporal.ChronoUnit.DAYS.between(r.dueDate, LocalDate.now());
        return Math.max(days, 0);
    }
}
