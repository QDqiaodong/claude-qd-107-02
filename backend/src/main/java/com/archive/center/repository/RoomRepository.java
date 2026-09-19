package com.archive.center.repository;

import com.archive.center.entity.Room;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomRepository extends JpaRepository<Room, Long> {

    boolean existsByCode(String code);

    List<Room> findAllByOrderByIdAsc();
}
