package com.passtheo.content.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.passtheo.content.domain.entity.OutboxEvent;
import com.passtheo.content.domain.entity.QuestionProgress;
import com.passtheo.content.domain.entity.SessionAnswer;
import com.passtheo.content.domain.entity.StudySession;
import com.passtheo.content.domain.enums.MasteryLevel;
import com.passtheo.content.domain.enums.OutboxStatus;
import com.passtheo.content.domain.enums.SessionStatus;
import com.passtheo.content.domain.enums.SessionType;
import com.passtheo.content.domain.valueobject.StreakResult;
import com.passtheo.content.dto.request.StartSessionRequest;
import com.passtheo.content.dto.request.SubmitAnswerRequest;
import com.passtheo.content.dto.response.ActiveSessionDto;
import com.passtheo.content.dto.response.AnswerResultDto;
import com.passtheo.content.dto.response.AnsweredQuestionSummaryDto;
import com.passtheo.content.dto.response.EarnedAchievementDto;
import com.passtheo.content.dto.response.QuestionDto;
import com.passtheo.content.dto.response.SessionDto;
import com.passtheo.content.dto.response.SessionSummaryDto;
import com.passtheo.content.integration.strapi.dto.StrapiDomainDto;
import com.passtheo.content.integration.strapi.StrapiContentCache;
import com.passtheo.content.integration.strapi.dto.StrapiQuestionDto;
import com.passtheo.content.repository.OutboxEventRepository;
import com.passtheo.content.repository.QuestionProgressRepository;
import com.passtheo.content.repository.SessionAnswerRepository;
import com.passtheo.content.repository.StudySessionRepository;
import com.passtheo.shared.core.context.TenantContext;
import com.passtheo.shared.core.exception.AppException;
import com.passtheo.shared.core.exception.ErrorCode;
import com.passtheo.shared.events.config.KafkaTopic;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages practice session lifecycle: create, answer, resume, complete.
 */
@Service
public class PracticeSessionService {

    private static final Logger LOG = LoggerFactory.getLogger(PracticeSessionService.class);
    private static final Logger AUDIT = LoggerFactory.getLogger("AUDIT");

    private final StudySessionRepository sessionRepository;
    private final SessionAnswerRepository answerRepository;
    private final QuestionProgressRepository progressRepository;
    private final QuestionSelectionService questionSelectionService;
    private final AnswerProcessingService answerProcessingService;
    private final StreakService streakService;
    private final AchievementService achievementService;
    private final StrapiContentCache strapiContentCache;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    /**
     * Constructs the practice session service.
     *
     * @param sessionRepository        study session repository
     * @param answerRepository         session answer repository
     * @param progressRepository       question progress repository
     * @param questionSelectionService question selection (spaced repetition)
     * @param answerProcessingService  answer grading + mastery update
     * @param streakService            streak management
     * @param achievementService       achievement checking
     * @param strapiContentCache       Strapi content cache
     * @param outboxEventRepository    outbox event repository
     * @param objectMapper             JSON serializer
     */
    public PracticeSessionService(StudySessionRepository sessionRepository,
                                  SessionAnswerRepository answerRepository,
                                  QuestionProgressRepository progressRepository,
                                  QuestionSelectionService questionSelectionService,
                                  AnswerProcessingService answerProcessingService,
                                  StreakService streakService,
                                  AchievementService achievementService,
                                  StrapiContentCache strapiContentCache,
                                  OutboxEventRepository outboxEventRepository,
                                  ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.answerRepository = answerRepository;
        this.progressRepository = progressRepository;
        this.questionSelectionService = questionSelectionService;
        this.answerProcessingService = answerProcessingService;
        this.streakService = streakService;
        this.achievementService = achievementService;
        this.strapiContentCache = strapiContentCache;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Starts a new practice session, selects questions, returns first question.
     *
     * @param userId  the user's Keycloak ID
     * @param request the start session request
     * @return the session with current question
     */
    @Transactional
    public SessionDto startSession(@Nonnull UUID userId, @Nonnull StartSessionRequest request) {
        SessionType sessionType = SessionType.valueOf(request.sessionType());
        String locale = request.locale() != null ? request.locale() : "nl";

        // Select questions using spaced repetition
        List<String> questionIds = questionSelectionService.selectQuestions(
                userId, request.productCode(), request.domainCode(),
                request.questionCount(), locale);

        if (questionIds.isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, "No questions available for the selected criteria");
        }

        // Create session entity
        StudySession session = new StudySession(
                userId, request.productCode(), request.domainCode(),
                request.topicCode(), sessionType, questionIds.size());
        session.setTenantId(TenantContext.get());
        session = sessionRepository.save(session);

        // Store selected question IDs in a transient way (we'll use order-based retrieval)
        // The question list is determined at session start; answers track order

        // Get first question from Strapi cache
        QuestionDto firstQuestion = loadQuestion(questionIds.getFirst(), locale, 1);

        LOG.info("Session started: id={}, user={}, product={}, domain={}, type={}, questions={}",
                session.getId(), userId, request.productCode(), request.domainCode(),
                sessionType, questionIds.size());
        AUDIT.info("SESSION_STARTED sessionId={} userId={} productCode={} domainCode={} sessionType={} questionCount={}",
                session.getId(), userId, request.productCode(), request.domainCode(),
                sessionType, questionIds.size());

        return new SessionDto(
                session.getId(),
                session.getStatus().name(),
                session.getTotalQuestions(),
                session.getAnsweredCount(),
                session.getCorrectCount(),
                firstQuestion,
                List.of()
        );
    }

    /**
     * Submits an answer, grades it, updates mastery, returns feedback + next question.
     *
     * @param userId    the user's Keycloak ID
     * @param sessionId the session ID
     * @param request   the answer request
     * @param locale    the content locale
     * @return the answer result with feedback
     */
    @Transactional
    public AnswerResultDto submitAnswer(@Nonnull UUID userId, @Nonnull UUID sessionId,
                                        @Nonnull SubmitAnswerRequest request, @Nonnull String locale) {
        try {
            return doSubmitAnswer(userId, sessionId, request, locale);
        } catch (DataIntegrityViolationException ex) {
            LOG.warn("Duplicate answer detected for session={} question={}, returning existing result",
                    sessionId, request.strapiQuestionId());
            return buildExistingAnswerResult(userId, sessionId, request.strapiQuestionId(), locale);
        }
    }

    private AnswerResultDto doSubmitAnswer(UUID userId, UUID sessionId,
                                           SubmitAnswerRequest request, String locale) {
        StudySession session = sessionRepository.findByIdAndKeycloakUserId(sessionId, userId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, ErrorCode.VALIDATION_ERROR, "Session not found: " + sessionId));

        if (session.getStatus() != SessionStatus.IN_PROGRESS) {
            throw new AppException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_STATUS_TRANSITION, "Session is not in progress: " + session.getStatus());
        }

        // Load question from Strapi
        StrapiQuestionDto question = strapiContentCache.getQuestion(request.strapiQuestionId(), locale);
        if (question == null) {
            throw new AppException(HttpStatus.NOT_FOUND, ErrorCode.VALIDATION_ERROR, "Question not found: " + request.strapiQuestionId());
        }

        // Grade the answer — null answer means skip (not wrong, no mastery change)
        boolean isSkipped = request.answer() == null;
        boolean isCorrect = !isSkipped && answerProcessingService.gradeAnswer(question, request.answer());

        // Build correct answer for response
        Map<String, Object> correctAnswer = answerProcessingService.buildCorrectAnswer(question);

        // Get or create question progress and update mastery (skips don't affect mastery)
        QuestionProgress progress = progressRepository
                .findByKeycloakUserIdAndStrapiQuestionId(userId, request.strapiQuestionId())
                .orElseGet(() -> {
                    QuestionProgress qp = new QuestionProgress(
                            userId, request.strapiQuestionId(),
                            session.getProductCode(),
                            question.domain() != null ? question.domain().code() : "",
                            question.topic() != null ? question.topic().code() : "");
                    qp.setTenantId(TenantContext.get());
                    return qp;
                });

        MasteryLevel previousLevel;
        if (isSkipped) {
            previousLevel = progress.getMasteryLevel();
        } else {
            previousLevel = answerProcessingService.updateMastery(progress, isCorrect);
            progress = progressRepository.save(progress);
        }

        // Record the answer — use "null" JSON for skips (JSONB accepts JSON null)
        int questionOrder = session.getAnsweredCount() + 1;
        String userAnswerJson = isSkipped ? "null" : serializeJson(request.answer());
        SessionAnswer answer = new SessionAnswer(
                sessionId, userId, request.strapiQuestionId(),
                question.version(), question.interactionType(), isCorrect,
                userAnswerJson, serializeJson(correctAnswer),
                request.timeTakenMs(), questionOrder);
        answer.setTenantId(TenantContext.get());
        answerRepository.save(answer);

        // Publish question.answered outbox event
        publishQuestionAnsweredEvent(TenantContext.get(), userId, sessionId,
                request.strapiQuestionId(), isCorrect);

        // Update session counters
        session.setAnsweredCount(session.getAnsweredCount() + 1);
        if (isCorrect) {
            session.setCorrectCount(session.getCorrectCount() + 1);
        }
        session.setTimeSpentSeconds(session.getTimeSpentSeconds() + (request.timeTakenMs() / 1000));
        session.setLastActivityAt(Instant.now());
        if (session.getAnsweredCount() > 0) {
            double acc = (double) session.getCorrectCount() / session.getAnsweredCount() * 100.0;
            session.setAccuracyPercent(BigDecimal.valueOf(acc).setScale(2, RoundingMode.HALF_UP));
        }
        sessionRepository.save(session);

        LOG.debug("Answer graded: sessionId={}, questionId={}, correct={}, mastery={}→{}, timeTakenMs={}",
                sessionId, request.strapiQuestionId(), isCorrect,
                previousLevel, progress.getMasteryLevel(), request.timeTakenMs());
        AUDIT.info("ANSWER_SUBMITTED sessionId={} userId={} questionId={} correct={} masteryFrom={} masteryTo={}",
                sessionId, userId, request.strapiQuestionId(), isCorrect,
                previousLevel, progress.getMasteryLevel());

        // Update streak
        streakService.updateStreak(userId, session.getProductCode());

        // Check achievements async
        List<EarnedAchievementDto> newAchievements = achievementService
                .checkAchievements(userId, session.getProductCode());

        // Build explanation
        AnswerResultDto.ExplanationDto explanation = null;
        if (question.explanation() != null) {
            explanation = new AnswerResultDto.ExplanationDto(
                    question.explanation().text(),
                    question.explanation().tip(),
                    question.explanation().image(),
                    question.explanation().legalReference()
            );
        }

        // Load next question (null if session complete)
        QuestionDto nextQuestion = null;
        if (session.getAnsweredCount() < session.getTotalQuestions()) {
            // For simplicity, re-select or use pre-computed list
            // Here we use position-based: get the next question from the selection
            List<String> allIds = questionSelectionService.selectQuestions(
                    userId, session.getProductCode(), session.getDomainCode(),
                    session.getTotalQuestions(), locale);
            if (questionOrder < allIds.size()) {
                nextQuestion = loadQuestion(allIds.get(questionOrder), locale, questionOrder + 1);
            }
        }

        double accuracyPercent = session.getAnsweredCount() > 0
                ? (double) session.getCorrectCount() / session.getAnsweredCount() * 100.0 : 0.0;

        return new AnswerResultDto(
                isCorrect,
                correctAnswer,
                explanation,
                new AnswerResultDto.MasteryUpdateDto(
                        previousLevel.name(),
                        progress.getMasteryLevel().name(),
                        progress.getConsecutiveCorrect()
                ),
                new AnswerResultDto.SessionProgressDto(
                        session.getAnsweredCount(),
                        session.getCorrectCount(),
                        session.getTotalQuestions(),
                        accuracyPercent
                ),
                nextQuestion,
                newAchievements
        );
    }

    private AnswerResultDto buildExistingAnswerResult(UUID userId, UUID sessionId,
                                                      String strapiQuestionId, String locale) {
        SessionAnswer existing = answerRepository.findBySessionIdAndStrapiQuestionId(sessionId, strapiQuestionId)
                .orElseThrow(() -> new AppException(HttpStatus.CONFLICT, ErrorCode.VALIDATION_ERROR,
                        "Duplicate answer but existing record not found"));
        StudySession session = sessionRepository.findByIdAndKeycloakUserId(sessionId, userId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, ErrorCode.VALIDATION_ERROR,
                        "Session not found: " + sessionId));
        StrapiQuestionDto question = strapiContentCache.getQuestion(strapiQuestionId, locale);
        Map<String, Object> correctAnswer = question != null
                ? answerProcessingService.buildCorrectAnswer(question) : Map.of();
        QuestionProgress progress = progressRepository
                .findByKeycloakUserIdAndStrapiQuestionId(userId, strapiQuestionId).orElse(null);
        double accuracyPercent = session.getAnsweredCount() > 0
                ? (double) session.getCorrectCount() / session.getAnsweredCount() * 100.0 : 0.0;
        AnswerResultDto.ExplanationDto explanation = null;
        if (question != null && question.explanation() != null) {
            explanation = new AnswerResultDto.ExplanationDto(
                    question.explanation().text(), question.explanation().tip(),
                    question.explanation().image(), question.explanation().legalReference());
        }
        return new AnswerResultDto(
                existing.isCorrect(),
                correctAnswer,
                explanation,
                new AnswerResultDto.MasteryUpdateDto(
                        progress != null ? progress.getMasteryLevel().name() : MasteryLevel.NEW.name(),
                        progress != null ? progress.getMasteryLevel().name() : MasteryLevel.NEW.name(),
                        progress != null ? progress.getConsecutiveCorrect() : 0
                ),
                new AnswerResultDto.SessionProgressDto(
                        session.getAnsweredCount(),
                        session.getCorrectCount(),
                        session.getTotalQuestions(),
                        accuracyPercent
                ),
                null,
                List.of()
        );
    }

    private void publishQuestionAnsweredEvent(UUID tenantId, UUID userId, UUID sessionId,
                                              String strapiQuestionId, boolean correct) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "eventType", "QuestionAnswered",
                    "tenantId", tenantId.toString(),
                    "keycloakUserId", userId.toString(),
                    "sessionId", sessionId.toString(),
                    "strapiQuestionId", strapiQuestionId,
                    "correct", correct
            ));
            OutboxEvent outbox = new OutboxEvent();
            outbox.setTenantId(tenantId);
            outbox.setEventType("QuestionAnswered");
            outbox.setTopic(KafkaTopic.CONTENT_EVENTS);
            outbox.setPayload(payload);
            outbox.setStatus(OutboxStatus.PENDING);
            outbox.setPartitionKey(userId.toString());
            outboxEventRepository.save(outbox);
        } catch (JsonProcessingException e) {
            LOG.error("Failed to serialize QuestionAnswered outbox event for user={}", userId, e);
        }
    }

    /**
     * Gets the most recent in-progress session for the dashboard "Continue Practicing" card.
     *
     * @param userId      the user's Keycloak ID
     * @param productCode the product code
     * @param locale      the content locale for domain name resolution
     * @return the active session DTO, or null if no active session exists
     */
    @Transactional(readOnly = true)
    public ActiveSessionDto getActiveSession(@Nonnull UUID userId, @Nonnull String productCode,
                                             @Nonnull String locale) {
        return sessionRepository
                .findFirstByKeycloakUserIdAndProductCodeAndStatusOrderByLastActivityAtDesc(
                        userId, productCode, SessionStatus.IN_PROGRESS)
                .map(session -> {
                    String domainName = resolveDomainName(session.getDomainCode(), productCode, locale);
                    int progressPercent = session.getTotalQuestions() > 0
                            ? (session.getAnsweredCount() * 100) / session.getTotalQuestions()
                            : 0;
                    return new ActiveSessionDto(
                            session.getId(),
                            session.getDomainCode(),
                            domainName,
                            session.getSessionType().name(),
                            session.getTotalQuestions(),
                            session.getAnsweredCount(),
                            progressPercent,
                            session.getStartedAt()
                    );
                })
                .orElse(null);
    }

    private String resolveDomainName(String domainCode, String productCode, String locale) {
        if (domainCode == null) {
            return null;
        }
        return strapiContentCache.getDomains(productCode, locale).stream()
                .filter(d -> d.code().equals(domainCode))
                .findFirst()
                .map(StrapiDomainDto::name)
                .orElse(domainCode);
    }

    /**
     * Gets current session state for resuming.
     *
     * @param userId    the user's Keycloak ID
     * @param sessionId the session ID
     * @param locale    the content locale
     * @return the session with current question
     */
    @Transactional(readOnly = true)
    public SessionDto getSession(@Nonnull UUID userId, @Nonnull UUID sessionId, @Nonnull String locale) {
        StudySession session = sessionRepository.findByIdAndKeycloakUserId(sessionId, userId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, ErrorCode.VALIDATION_ERROR, "Session not found: " + sessionId));

        QuestionDto currentQuestion = null;
        if (session.getStatus() == SessionStatus.IN_PROGRESS
                && session.getAnsweredCount() < session.getTotalQuestions()) {
            List<String> questionIds = questionSelectionService.selectQuestions(
                    userId, session.getProductCode(), session.getDomainCode(),
                    session.getTotalQuestions(), locale);
            if (session.getAnsweredCount() < questionIds.size()) {
                currentQuestion = loadQuestion(
                        questionIds.get(session.getAnsweredCount()),
                        locale, session.getAnsweredCount() + 1);
            }
        }

        List<AnsweredQuestionSummaryDto> answeredQuestions = answerRepository
                .findBySessionIdOrderByQuestionOrderAsc(sessionId).stream()
                .map(a -> new AnsweredQuestionSummaryDto(
                        a.getQuestionOrder(),
                        a.getStrapiQuestionId(),
                        a.isCorrect(),
                        "null".equals(a.getUserAnswer())))
                .toList();

        return new SessionDto(
                session.getId(),
                session.getStatus().name(),
                session.getTotalQuestions(),
                session.getAnsweredCount(),
                session.getCorrectCount(),
                currentQuestion,
                answeredQuestions
        );
    }

    /**
     * Completes a session and returns the summary.
     *
     * @param userId    the user's Keycloak ID
     * @param sessionId the session ID
     * @return the session summary
     */
    @Transactional
    public SessionSummaryDto completeSession(@Nonnull UUID userId, @Nonnull UUID sessionId) {
        StudySession session = sessionRepository.findByIdAndKeycloakUserId(sessionId, userId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, ErrorCode.VALIDATION_ERROR, "Session not found: " + sessionId));

        session.setStatus(SessionStatus.COMPLETED);
        session.setCompletedAt(Instant.now());
        if (session.getAnsweredCount() > 0) {
            double acc = (double) session.getCorrectCount() / session.getAnsweredCount() * 100.0;
            session.setAccuracyPercent(BigDecimal.valueOf(acc).setScale(2, RoundingMode.HALF_UP));
        }
        sessionRepository.save(session);

        StreakResult streak = streakService.getStreak(userId, session.getProductCode());
        List<EarnedAchievementDto> achievements = achievementService
                .checkAchievements(userId, session.getProductCode());

        LOG.info("Session completed: id={}, user={}, correct={}/{}, accuracy={}%, timeSpentSec={}",
                sessionId, userId, session.getCorrectCount(), session.getTotalQuestions(),
                session.getAccuracyPercent(), session.getTimeSpentSeconds());
        AUDIT.info("SESSION_COMPLETED sessionId={} userId={} correct={} total={} accuracyPct={} timeSpentSec={}",
                sessionId, userId, session.getCorrectCount(), session.getTotalQuestions(),
                session.getAccuracyPercent(), session.getTimeSpentSeconds());

        return new SessionSummaryDto(
                session.getId(),
                session.getStatus().name(),
                session.getTotalQuestions(),
                session.getCorrectCount(),
                session.getAccuracyPercent() != null ? session.getAccuracyPercent().doubleValue() : 0.0,
                session.getTimeSpentSeconds(),
                new SessionSummaryDto.MasteryChangesDto(0, 0, 0),
                new SessionSummaryDto.StreakUpdateDto(streak.currentStreak(), streak.isNewDay()),
                achievements
        );
    }

    private QuestionDto loadQuestion(String questionId, String locale, int order) {
        StrapiQuestionDto q = strapiContentCache.getQuestion(questionId, locale);
        if (q == null) {
            return null;
        }
        QuestionDto.ExplanationDto explanation = null;
        if (q.explanation() != null) {
            explanation = new QuestionDto.ExplanationDto(
                    q.explanation().text(),
                    q.explanation().tip(),
                    q.explanation().legalReference(),
                    q.explanation().image()
            );
        }
        return new QuestionDto(
                q.documentId(), q.questionText(), q.interactionType(), q.difficulty(),
                q.image() != null ? q.image().url() : null, q.videoUrl(),
                q.answerOptions() != null ? q.answerOptions().stream()
                        .map(o -> new QuestionDto.AnswerOptionDto(String.valueOf(o.id()), o.text(), o.image()))
                        .toList() : null,
                q.imageRegions() != null ? q.imageRegions().stream()
                        .map(r -> new QuestionDto.ImageRegionDto(
                                String.valueOf(r.id()), r.xPercent(), r.yPercent(), r.widthPercent(), r.heightPercent()))
                        .toList() : null,
                q.dragTargets() != null ? q.dragTargets().stream()
                        .map(t -> new QuestionDto.DragTargetDto(String.valueOf(t.id()), t.label(), t.image()))
                        .toList() : null,
                order, q.domain() != null ? q.domain().code() : null, explanation
        );
    }

    private String serializeJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            LOG.error("Failed to serialize JSON: {}", e.getMessage());
            return "{}";
        }
    }
}
