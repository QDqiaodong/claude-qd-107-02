package com.archive.center.service;

import com.archive.center.dto.BizException;
import com.archive.center.entity.Archive;
import com.archive.center.entity.Room;
import com.archive.center.repository.ArchiveRepository;
import com.archive.center.repository.RoomRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoomService {

    private final RoomRepository rooms;
    private final ArchiveRepository archives;

    public RoomService(RoomRepository rooms, ArchiveRepository archives) {
        this.rooms = rooms;
        this.archives = archives;
    }

    /** 这间库房现在实际放着多少卷。 */
    public int stored(Long roomId) {
        return archives.findByRoomIdAndStatusNot(roomId, "已销毁").size();
    }

    public List<Room> list(String status, String keyword) {
        return rooms.findAllByOrderByIdAsc().stream()
                .filter(r -> status == null || status.isEmpty() || status.equals(r.status))
                .filter(r -> keyword == null || keyword.isEmpty()
                        || r.name.contains(keyword) || r.code.contains(keyword))
                .peek(this::fillCapacityStats)
                .toList();
    }

    /**
     * 按 archive 表实时回填在册数、还能放几卷、超容标记与超容说明。
     * 全系统只此一处容量口径：在册 = 还占着位置的卷（不含已销毁，含已借出）。
     */
    public Room fillCapacityStats(Room r) {
        int registered = stored(r.id);
        r.registered = registered;
        r.availableSlots = Math.max(r.capacity - registered, 0);
        r.overCapacity = registered > r.capacity;
        r.capacityNote = Boolean.TRUE.equals(r.overCapacity)
                ? "已经超容：在册 " + registered + " 卷，容量只有 " + r.capacity
                        + " 卷，多出 " + (registered - r.capacity)
                        + " 卷。先把多出来的卷挪走，本库才能再收新卷或从别的库改挂卷进来。"
                : null;
        return r;
    }

    @Transactional
    public Room create(Room input) {
        if (input.code == null || input.code.isBlank()) {
            throw new BizException("库房编号不能为空");
        }
        if (rooms.existsByCode(input.code)) {
            throw new BizException("编号 " + input.code + " 已经被别的库房用掉了");
        }
        if (input.capacity == null || input.capacity <= 0) {
            throw new BizException("库房容量要大于 0 卷");
        }
        Room saved = new Room();
        saved.code = input.code.trim();
        saved.name = input.name;
        saved.capacity = input.capacity;
        saved.status = (input.status == null || input.status.isBlank()) ? "在用" : input.status;
        // 本库温湿度上下限：没配就按默认保管区间
        saved.tempMin = input.tempMin != null ? input.tempMin : CheckService.DEFAULT_MIN_TEMPERATURE;
        saved.tempMax = input.tempMax != null ? input.tempMax : CheckService.DEFAULT_MAX_TEMPERATURE;
        saved.humidityMin = input.humidityMin != null ? input.humidityMin : CheckService.DEFAULT_MIN_HUMIDITY;
        saved.humidityMax = input.humidityMax != null ? input.humidityMax : CheckService.DEFAULT_MAX_HUMIDITY;
        checkLimits(saved.tempMin, saved.tempMax, saved.humidityMin, saved.humidityMax);
        saved.sealed = false;
        return fillCapacityStats(rooms.save(saved));
    }

    @Transactional
    public Room update(Long id, Room input) {
        Room r = rooms.findById(id).orElseThrow(() -> new BizException("库房不存在"));
        int current = stored(r.id);
        if (input.name != null) {
            r.name = input.name;
        }
        if (input.capacity != null && !input.capacity.equals(r.capacity)) {
            if (input.capacity <= 0) {
                throw new BizException("库房容量要大于 0 卷");
            }
            if (input.capacity < current) {
                throw new BizException("容量改小被拒：这间库房现在在册 " + current + " 卷，容量不能改到 "
                        + input.capacity + " 卷。先把多出来的 " + (current - input.capacity)
                        + " 卷挪走，容量才能改到这么小");
            }
            r.capacity = input.capacity;
        }
        if (input.status != null && !input.status.isBlank() && !input.status.equals(r.status)) {
            if (!"在用".equals(input.status) && current > 0) {
                throw new BizException("这间库房还有 " + current + " 卷没挪走，先清空才能改成「"
                        + input.status + "」");
            }
            r.status = input.status;
        }
        // 本库温湿度上下限可以调；但封库状态只能由抄表驱动，这里绝不接 sealed/sealedDate
        Double tMin = input.tempMin != null ? input.tempMin : r.tempMin;
        Double tMax = input.tempMax != null ? input.tempMax : r.tempMax;
        Double hMin = input.humidityMin != null ? input.humidityMin : r.humidityMin;
        Double hMax = input.humidityMax != null ? input.humidityMax : r.humidityMax;
        checkLimits(tMin, tMax, hMin, hMax);
        r.tempMin = tMin;
        r.tempMax = tMax;
        r.humidityMin = hMin;
        r.humidityMax = hMax;
        return fillCapacityStats(rooms.save(r));
    }

    /** 上下限要成对、下限低于上限，湿度还得落在 0 到 100 里。 */
    private void checkLimits(Double tMin, Double tMax, Double hMin, Double hMax) {
        if (tMin != null && tMax != null && tMin >= tMax) {
            throw new BizException("温度下限要比上限低");
        }
        if (hMin != null && hMax != null) {
            if (hMin >= hMax) {
                throw new BizException("湿度下限要比上限低");
            }
            if (hMin < 0 || hMax > 100) {
                throw new BizException("湿度区间要在 0 到 100 之间");
            }
        }
    }
}
