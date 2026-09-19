package com.archive.center.service;

import com.archive.center.dto.BizException;
import com.archive.center.entity.Room;
import com.archive.center.entity.RoomCheck;
import com.archive.center.repository.RoomCheckRepository;
import com.archive.center.repository.RoomRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CheckService {

    /** 库房没配自己的上下限时，按这套默认保管区间判 */
    public static final double DEFAULT_MIN_TEMPERATURE = 14.0;
    public static final double DEFAULT_MAX_TEMPERATURE = 24.0;
    public static final double DEFAULT_MIN_HUMIDITY = 45.0;
    public static final double DEFAULT_MAX_HUMIDITY = 60.0;

    private final RoomCheckRepository checks;
    private final RoomRepository rooms;

    public CheckService(RoomCheckRepository checks, RoomRepository rooms) {
        this.checks = checks;
        this.rooms = rooms;
    }

    public List<RoomCheck> list(Long roomId, LocalDate checkDate, String result) {
        return checks.findAllByOrderByCheckDateDesc().stream()
                .filter(c -> roomId == null || roomId.equals(c.roomId))
                .filter(c -> checkDate == null || checkDate.equals(c.checkDate))
                .filter(c -> result == null || result.isEmpty() || result.equals(c.result))
                .toList();
    }

    /** 这一班抄表有没有越出本库自己的上下限（温度、湿度任一边越出都算）。 */
    public boolean outOfRange(Room room, double temperature, double humidity) {
        double tMin = room.tempMin == null ? DEFAULT_MIN_TEMPERATURE : room.tempMin;
        double tMax = room.tempMax == null ? DEFAULT_MAX_TEMPERATURE : room.tempMax;
        double hMin = room.humidityMin == null ? DEFAULT_MIN_HUMIDITY : room.humidityMin;
        double hMax = room.humidityMax == null ? DEFAULT_MAX_HUMIDITY : room.humidityMax;
        return temperature < tMin || temperature > tMax || humidity < hMin || humidity > hMax;
    }

    @Transactional
    public RoomCheck create(RoomCheck input) {
        if (input.roomId == null) {
            throw new BizException("请选择库房");
        }
        if (input.checkDate == null) {
            throw new BizException("请填检查日期");
        }
        if (input.temperature == null) {
            throw new BizException("请填温度");
        }
        if (input.humidity == null) {
            throw new BizException("请填湿度");
        }
        if (input.humidity < 0 || input.humidity > 100) {
            throw new BizException("湿度要在 0 到 100 之间");
        }
        Room room = rooms.findById(input.roomId)
                .orElseThrow(() -> new BizException("库房不存在"));
        if ("停用".equals(room.status)) {
            throw new BizException("库房 " + room.name + " 已经停用了，不用检查");
        }
        if (!checks.findByRoomIdAndCheckDate(room.id, input.checkDate).isEmpty()) {
            throw new BizException("库房 " + room.name + " " + input.checkDate
                    + " 已经检查过了，一天只记一次");
        }
        // 封库中的库房抄表口不堵：本班抄表照常登记，回了区间正好回温
        boolean bad = outOfRange(room, input.temperature, input.humidity);
        if (bad && (input.issueDesc == null || input.issueDesc.isBlank())) {
            throw new BizException("温湿度超出本库保管区间了，得写清是什么情况");
        }
        RoomCheck saved = new RoomCheck();
        saved.roomId = room.id;
        saved.checkDate = input.checkDate;
        saved.temperature = input.temperature;
        saved.humidity = input.humidity;
        saved.result = bad ? "异常" : "正常";
        saved.issueDesc = input.issueDesc;
        saved.checker = input.checker;
        saved = checks.save(saved);
        applySeal(room, saved);
        return saved;
    }

    /**
     * 封库口径：
     * 连续两班最新抄表都越本库上下限 → 封库；只越一班只预警，不封。
     * 回温 = 下一班（最新一班）抄表回到本库区间，封库随即解除。
     * 补录/改动历史班次不动封库状态：只有最新一班的抄表才说了算，
     * 所以回温前把历史班次改成区间内的数，封库也解除不了。
     */
    private void applySeal(Room room, RoomCheck saved) {
        List<RoomCheck> latest = checks.findTop2ByRoomIdOrderByCheckDateDescIdDesc(room.id);
        if (latest.isEmpty() || !latest.get(0).id.equals(saved.id)) {
            // 这次登记的是历史班次（日期比已有最新一班还早），不影响封库状态
            return;
        }
        boolean out = outOfRange(room, saved.temperature, saved.humidity);
        if (!out) {
            // 最新一班回到本库区间：回温，封库解除（没封库时也只是消掉预警）
            room.sealed = false;
            room.sealedDate = null;
        } else if (!Boolean.TRUE.equals(room.sealed)) {
            boolean prevOut = latest.size() > 1
                    && outOfRange(room, latest.get(1).temperature, latest.get(1).humidity);
            if (prevOut) {
                // 连续两班最新抄表都越限：封库
                room.sealed = true;
                room.sealedDate = saved.checkDate;
            }
            // 只越一班：预警，不封
        }
        // 已封库且最新一班仍越限：继续封，封库起始日不动
        rooms.save(room);
    }
}
