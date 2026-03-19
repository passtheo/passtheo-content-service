package com.passtheo.content.controller;

import com.passtheo.content.integration.strapi.StrapiContentCache;
import com.passtheo.content.repository.EarnedAchievementRepository;
import com.passtheo.content.repository.ExamAttemptRepository;
import com.passtheo.content.repository.QuestionProgressRepository;
import com.passtheo.content.repository.ReadinessSnapshotRepository;
import com.passtheo.content.repository.SessionAnswerRepository;
import com.passtheo.content.repository.StreakRepository;
import com.passtheo.content.repository.StudyPlanRepository;
import com.passtheo.content.repository.StudySessionRepository;
import com.passtheo.content.repository.TopicProgressRepository;
import com.passtheo.content.repository.DomainProgressRepository;
import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Internal service-to-service endpoints: GDPR delete, cache flush.
 */
@RestController
@RequestMapping("/internal")
public class InternalContentController {

    private static final Logger LOG = LoggerFactory.getLogger(InternalContentController.class);

    private final QuestionProgressRepository progressRepository;
    private final TopicProgressRepository topicProgressRepository;
    private final DomainProgressRepository domainProgressRepository;
    private final StreakRepository streakRepository;
    private final EarnedAchievementRepository achievementRepository;
    private final ExamAttemptRepository examAttemptRepository;
    private final ReadinessSnapshotRepository snapshotRepository;
    private final StudyPlanRepository planRepository;
    private final StrapiContentCache strapiContentCache;

    /**
     * Constructs the internal controller.
     *
     * @param progressRepository       question progress repository
     * @param topicProgressRepository  topic progress repository
     * @param domainProgressRepository domain progress repository
     * @param streakRepository         streak repository
     * @param achievementRepository    earned achievement repository
     * @param examAttemptRepository    exam attempt repository
     * @param snapshotRepository       readiness snapshot repository
     * @param planRepository           study plan repository
     * @param strapiContentCache       Strapi content cache
     */
    public InternalContentController(QuestionProgressRepository progressRepository,
                                     TopicProgressRepository topicProgressRepository,
                                     DomainProgressRepository domainProgressRepository,
                                     StreakRepository streakRepository,
                                     EarnedAchievementRepository achievementRepository,
                                     ExamAttemptRepository examAttemptRepository,
                                     ReadinessSnapshotRepository snapshotRepository,
                                     StudyPlanRepository planRepository,
                                     StrapiContentCache strapiContentCache) {
        this.progressRepository = progressRepository;
        this.topicProgressRepository = topicProgressRepository;
        this.domainProgressRepository = domainProgressRepository;
        this.streakRepository = streakRepository;
        this.achievementRepository = achievementRepository;
        this.examAttemptRepository = examAttemptRepository;
        this.snapshotRepository = snapshotRepository;
        this.planRepository = planRepository;
        this.strapiContentCache = strapiContentCache;
    }

    /**
     * GDPR delete — removes all learning data for a user.
     *
     * @param keycloakUserId the user's Keycloak ID
     * @return 204 No Content
     */
    @DeleteMapping("/content/user/{keycloakUserId}/delete")
    @Transactional
    public ResponseEntity<Void> deleteUserData(
            @PathVariable @Nonnull UUID keycloakUserId,
            @RequestHeader("X-Tenant-ID") UUID tenantId) {

        LOG.info("GDPR delete: removing all learning data for user={}", keycloakUserId);

        progressRepository.deleteByKeycloakUserId(keycloakUserId);
        topicProgressRepository.deleteByKeycloakUserId(keycloakUserId);
        domainProgressRepository.deleteByKeycloakUserId(keycloakUserId);
        streakRepository.deleteByKeycloakUserId(keycloakUserId);
        achievementRepository.deleteByKeycloakUserId(keycloakUserId);
        examAttemptRepository.deleteByKeycloakUserId(keycloakUserId);
        snapshotRepository.deleteByKeycloakUserId(keycloakUserId);
        planRepository.deleteByKeycloakUserId(keycloakUserId);

        LOG.info("GDPR delete complete: user={}", keycloakUserId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Flushes the Strapi content cache.
     *
     * @return 200 OK
     */
    @PostMapping("/cache/flush")
    public ResponseEntity<String> flushContentCache() {
        LOG.info("Flushing Strapi content cache");
        strapiContentCache.flushAll();
        return ResponseEntity.ok("Cache flushed");
    }
}
