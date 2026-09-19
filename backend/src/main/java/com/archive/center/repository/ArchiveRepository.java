package com.archive.center.repository;

import com.archive.center.entity.Archive;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ArchiveRepository extends JpaRepository<Archive, Long> {

    boolean existsByCode(String code);

    List<Archive> findByRoomId(Long roomId);

    /** 这间库里还占着位置的那些卷（不含已销毁）。 */
    List<Archive> findByRoomIdAndStatusNot(Long roomId, String status);

    /** 开鉴定单时先把案卷行锁住，两人同时开单只有一个能过。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Archive a where a.id = :id")
    Optional<Archive> findForUpdate(@Param("id") Long id);

    List<Archive> findAllByOrderByIdAsc();
}
