package com.archive.center.repository;

import com.archive.center.entity.Appraisal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppraisalRepository extends JpaRepository<Appraisal, Long> {

    /** 这卷有没有没结案的鉴定单。 */
    boolean existsByArchiveIdAndStatus(Long archiveId, String status);

    Optional<Appraisal> findByIdAndStatus(Long id, String status);

    List<Appraisal> findAllByOrderByIdDesc();
}
