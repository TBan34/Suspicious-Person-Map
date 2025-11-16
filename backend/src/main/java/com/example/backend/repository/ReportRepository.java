package com.example.backend.repository;

import com.example.backend.entity.ReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 不審者情報Repository
 */
@Repository
public interface ReportRepository extends JpaRepository<ReportEntity, Long> {
}


