package com.archive.center.repository;

import com.archive.center.entity.Retrieval;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RetrievalRepository extends JpaRepository<Retrieval, Long> {

    List<Retrieval> findByArchiveIdAndStatus(Long archiveId, String status);

    List<Retrieval> findAllByOrderByIdDesc();
}
