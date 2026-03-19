package com.passtheo.content.scheduler;

import com.passtheo.content.domain.entity.StudySession;
import com.passtheo.content.domain.enums.SessionStatus;
import com.passtheo.content.repository.StudySessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Marks IN_PROGRESS sessions with no activity for 24h as ABANDONED.
 */
@Component
public class AbandonedSessionCleanup {

    private static final Logger LOG = LoggerFactory.getLogger(AbandonedSessionCleanup.class);
    private static final int ABANDONMENT_HOURS = 24;

    private final StudySessionRepository sessionRepository;

    /**
     * Constructs the abandoned session cleanup job.
     *
     * @param sessionRepository the study session repository
     */
    public AbandonedSessionCleanup(StudySessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    /**
     * Runs every hour to clean up abandoned sessions.
     */
    @Scheduled(cron = "0 0 * * * *", zone = "UTC")
    @Transactional
    public void cleanup() {
        Instant threshold = Instant.now().minus(ABANDONMENT_HOURS, ChronoUnit.HOURS);
        List<StudySession> abandoned = sessionRepository
                .findByStatusAndLastActivityAtBefore(SessionStatus.IN_PROGRESS, threshold);

        for (StudySession session : abandoned) {
            session.setStatus(SessionStatus.ABANDONED);
            session.setCompletedAt(Instant.now());
            sessionRepository.save(session);
        }

        if (!abandoned.isEmpty()) {
            LOG.info("Marked {} sessions as ABANDONED", abandoned.size());
        }
    }
}
