package com.passtheo.content.repository;

import com.passtheo.content.domain.entity.ExamAnswer;
import jakarta.annotation.Nonnull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for ExamAnswer entities.
 */
@Repository
public interface ExamAnswerRepository extends JpaRepository<ExamAnswer, UUID> {

    /**
     * Finds all answers for an exam attempt.
     *
     * @param examAttemptId the exam attempt ID
     * @return list of exam answers ordered by question order
     */
    List<ExamAnswer> findByExamAttemptIdOrderByQuestionOrderAsc(@Nonnull UUID examAttemptId);
}
