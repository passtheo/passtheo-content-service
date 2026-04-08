package com.passtheo.content.repository;

import com.passtheo.content.domain.entity.QuestionReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository for QuestionReport entities.
 */
@Repository
public interface QuestionReportRepository extends JpaRepository<QuestionReport, UUID> {
}
