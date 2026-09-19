package com.archive.center.service;

import com.archive.center.dto.BizException;
import com.archive.center.entity.Archive;
import com.archive.center.entity.Room;
import com.archive.center.repository.AppraisalRepository;
import com.archive.center.repository.ArchiveRepository;
import com.archive.center.repository.RoomRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ArchiveService {

    private final ArchiveRepository archives;
    private final RoomRepository rooms;
    private final AppraisalRepository appraisals;

    public ArchiveService(ArchiveRepository archives, RoomRepository rooms,
                          AppraisalRepository appraisals) {
        this.archives = archives;
        this.rooms = rooms;
        this.appraisals = appraisals;
    }

    public List<Archive> list(Long roomId, String status, Integer year, String keyword) {
        return archives.findAllByOrderByIdAsc().stream()
                .filter(a -> roomId == null || roomId.equals(a.roomId))
                .filter(a -> status == null || status.isEmpty() || status.equals(a.status))
                .filter(a -> year == null || year.equals(a.archiveYear))
                .filter(a -> keyword == null || keyword.isEmpty()
                        || a.title.contains(keyword) || a.code.contains(keyword))
                .toList();
    }

    @Transactional
    public Archive create(Archive input) {
        if (input.code == null || input.code.isBlank()) {
            throw new BizException("卷宗号不能为空");
        }
        if (archives.existsByCode(input.code)) {
            throw new BizException("卷宗号 " + input.code + " 已经用过了");
        }
        if (input.roomId == null) {
            throw new BizException("请选择放进哪间库房");
        }
        if (input.archiveYear == null) {
            throw new BizException("要填所属年度");
        }
        if (input.keepYears == null || input.keepYears <= 0) {
            throw new BizException("保管期限要大于 0 年");
        }
        Room room = rooms.findById(input.roomId)
                .orElseThrow(() -> new BizException("库房不存在"));
        if (!"在用".equals(room.status)) {
            throw new BizException("库房 " + room.name + " 现在是「" + room.status + "」，不能往里放卷");
        }
        ensureRoomCanHold(room, 1, "放卷");
        Archive saved = new Archive();
        saved.code = input.code.trim();
        saved.title = input.title;
        saved.archiveYear = input.archiveYear;
        saved.keepYears = input.keepYears;
        saved.roomId = room.id;
        saved.status = "在库";
        return archives.save(saved);
    }

    @Transactional
    public Archive update(Long id, Archive input) {
        Archive a = archives.findById(id).orElseThrow(() -> new BizException("卷宗不存在"));
        if ("已销毁".equals(a.status)) {
            throw new BizException("这卷已经销毁了，改不了");
        }
        if (input.title != null) {
            a.title = input.title;
        }
        if (input.keepYears != null && !input.keepYears.equals(a.keepYears)) {
            if (appraisals.existsByArchiveIdAndStatus(a.id, "未结案")) {
                throw new BizException("这卷正在鉴定会签中，会签没齐之前不许改保管期限");
            }
            if (input.keepYears <= 0) {
                throw new BizException("保管期限要大于 0 年");
            }
            a.keepYears = input.keepYears;
        }
        if (input.roomId != null && !input.roomId.equals(a.roomId)) {
            if ("已借出".equals(a.status)) {
                throw new BizException("这卷还在外面调阅，还回来才能挪库房");
            }
            Room room = rooms.findById(input.roomId)
                    .orElseThrow(() -> new BizException("库房不存在"));
            if (!"在用".equals(room.status)) {
                throw new BizException("库房 " + room.name + " 现在是「" + room.status
                        + "」，不能往里挪卷");
            }
            ensureRoomCanHold(room, 1, "改挂卷");
            a.roomId = room.id;
        }
        if (input.status != null && !input.status.isBlank() && !input.status.equals(a.status)) {
            if ("已销毁".equals(input.status)) {
                // 目录侧不能再直接把状态改成已销毁：到期卷必须先过鉴定会签，由鉴定台执行销毁
                throw new BizException("不能在案卷目录里直接改成已销毁，到期卷要先到「到期鉴定台」过会签");
            }
            a.status = input.status;
        }
        return archives.save(a);
    }

    /**
     * 这间库房还能不能再收 add 卷。
     * 在册口径与库房页一致：在库 + 已借出（不含已销毁）。
     * 已经超容、或加上这卷就超容量，一律拦下——不允许往超容库里再收新卷或从别的库改挂进来。
     *
     * @param action 放卷 / 改挂卷，用于拼失败说明
     */
    private void ensureRoomCanHold(Room room, int add, String action) {
        int registered = archives.findByRoomIdAndStatusNot(room.id, "已销毁").size();
        if (registered + add > room.capacity) {
            if (registered >= room.capacity) {
                String state = registered > room.capacity
                        ? "现在在册 " + registered + " 卷，已经超容 " + (registered - room.capacity)
                                + " 卷，先把多出来的卷挪走才能再收"
                        : "容量 " + room.capacity + " 卷已经放满，先腾出位子才能再收";
                throw new BizException("库房 " + room.name + " " + state + "，这次" + action
                        + "办不了");
            }
            throw new BizException("库房 " + room.name + " 只能放 " + room.capacity
                    + " 卷，现在在册 " + registered + " 卷，放不下了");
        }
    }

    /** 这卷按年度 + 保管期限算，到期年份是哪一年。 */
    public int expireYear(Archive a) {
        return a.archiveYear + a.keepYears;
    }

    public boolean expired(Archive a) {
        return expireYear(a) <= LocalDate.now().getYear();
    }
}
