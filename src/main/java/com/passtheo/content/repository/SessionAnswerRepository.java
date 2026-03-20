package com.passtheo.content.repository;

import com.passtheo.content.domain.entity.SessionAnswer;
import jakarta.annotation.Nonnull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for SessionAnswer entities.
 */
@Repository
public interface SessionAnswerRepository extends JpaRepository<SessionAnswer, UUID> {

    /**
     * Finds all answers for a session.
     *
     * @param sessionId the session ID
     * @return list of answers ordered by question order
     */
    List<SessionAnswer> findBySessionIdOrderByQuestionOrderAsc(@Nonnull UUID sessionId);

    /**
     * Finds an existing answer for a specific question in a session (duplicate check).
     *
     * @param sessionId        the session ID
     * @param strapiQuestionId the Strapi question ID
     * @return the existing answer, if present
     */
    java.util.Optional<SessionAnswer> findBySessionIdAndStrapiQuestionId(
            @Nonnull UUID sessionId, @Nonnull String strapiQuestionId);

    /**
     * Counts answers in a session.
     *
     * @param sessionId the session ID
     * @return the answer count
     */
    long countBySessionId(@Nonnull UUID sessionId);
}
