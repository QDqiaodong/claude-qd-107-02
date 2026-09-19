package com.archive.center.repository;

import com.archive.center.entity.RoomCheck;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomCheckRepository extends JpaRepository<RoomCheck, Long> {

    List<RoomCheck> findByRoomIdAndCheckDate(Long roomId, LocalDate checkDate);

    List<RoomCheck> findByRoomIdOrderByCheckDateDesc(Long roomId);

    /** 最新两班抄表（日期近的在前，同日期 id 大的在前），封库口径只看这两班。 */
    List<RoomCheck> findTop2ByRoomIdOrderByCheckDateDescIdDesc(Long roomId);

    List<RoomCheck> findAllByOrderByCheckDateDesc();
}
