package com.passtheo.content.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.passtheo.shared.outbox.entity.OutboxEvent;
import com.passtheo.content.domain.entity.QuestionProgress;
import com.passtheo.content.domain.entity.SessionAnswer;
import com.passtheo.content.domain.entity.StudySession;
import com.passtheo.content.domain.enums.MasteryLevel;
import com.passtheo.content.domain.enums.PlanDayStatus;
import com.passtheo.content.domain.enums.PlanStatus;
import com.passtheo.shared.outbox.entity.OutboxStatus;
import com.passtheo.content.domain.enums.SessionStatus;
import com.passtheo.content.domain.enums.SessionType;
import com.passtheo.content.domain.valueobject.StreakResult;
import com.passtheo.content.dto.request.StartSessionRequest;
import com.passtheo.content.dto.request.SubmitAnswerRequest;
import com.passtheo.content.dto.response.ActiveSessionDto;
import com.passtheo.content.dto.response.AnswerResultDto;
import com.passtheo.content.domain.valueobject.AccessGrant;
import com.passtheo.content.dto.response.AnsweredQuestionSummaryDto;
import com.passtheo.content.dto.response.DomainSummaryDto;
import com.passtheo.content.dto.response.EarnedAchievementDto;
import com.passtheo.content.dto.response.QuestionDto;
import com.passtheo.content.dto.response.SessionDto;
import com.passtheo.content.dto.response.SessionSummaryDto;
import com.passtheo.content.integration.strapi.dto.StrapiDomainDto;
import com.passtheo.content.integration.strapi.StrapiContentCache;
import com.passtheo.content.integration.strapi.dto.StrapiQuestionDto;
import com.passtheo.shared.outbox.repository.OutboxEventRepository;
import com.passtheo.content.repository.QuestionProgressRepository;
import com.passtheo.content.repository.SessionAnswerRepository;
import com.passtheo.content.repository.StudyPlanDayRepository;
import com.passtheo.content.repository.StudyPlanRepository;
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

import jakarta.annotation.Nullable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
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

    /** JSON representation of a skipped answer stored in session_answers.user_answer. */
    private static final String SKIP_ANSWER_JSON = "null";

    private final StudySessionRepository sessionRepository;
    private final SessionAnswerRepository answerRepository;
    private final QuestionProgressRepository progressRepository;
    private final QuestionSelectionService questionSelectionService;
    private final AnswerProcessingService answerProcessingService;
    private final StreakService streakService;
    private final AchievementService achievementService;
    private final StrapiContentCache strapiContentCache;
    private final StudyPlanRepository planRepository;
    private final StudyPlanDayRepository planDayRepository;
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
     * @param planRepository           study plan repository for progress sync
     * @param planDayRepository        study plan day repository for progress sync
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
                                  StudyPlanRepository planRepository,
                                  StudyPlanDayRepository planDayRepository,
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
        this.planRepository = planRepository;
        this.planDayRepository = planDayRepository;
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

        // Check minimum question pool before selection — domains with < 5 questions
        // produce broken navigator grids in Flutter.
        // Note: checks domain-level count only. If topic-level filtering is added to
        // QuestionSelectionService in the future, this check needs updating.
        int availableCount = (request.domainCode() != null && !request.domainCode().isBlank())
                ? strapiContentCache.getQuestionCountByDomain(request.domainCode(), locale)
                : strapiContentCache.getQuestionCount(request.productCode(), locale);
        if (availableCount < 5) {
            throw new AppException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                    "Not enough questions available for this domain. At least 5 required.");
        }

        // Select questions using spaced repetition
        List<String> questionIds = questionSelectionService.selectQuestions(
                userId, request.productCode(), request.domainCode(),
                sessionType, request.questionCount(), locale);

        if (questionIds.isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, "No questions available for the selected criteria");
        }

        // Create session entity and persist the question list so it is fixed for the
        // entire session — re-running selectQuestions later may shuffle new questions
        // differently and cause duplicates. The locale is stored here and used for all
        // subsequent Strapi fetches; the session is the source of truth for locale.
        StudySession session = new StudySession(
                userId, request.productCode(), request.domainCode(),
                request.topicCode(), sessionType, questionIds.size(), locale);
        session.setTenantId(TenantContext.get());
        session.setQuestionIdList(questionIds);
        session = sessionRepository.save(session);

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
     * The content locale is read from the session record — not from the caller.
     *
     * @param userId    the user's Keycloak ID
     * @param sessionId the session ID
     * @param request   the answer request
     * @return the answer result with feedback
     */
    @Transactional
    public AnswerResultDto submitAnswer(@Nonnull UUID userId, @Nonnull UUID sessionId,
                                        @Nonnull SubmitAnswerRequest request) {
        try {
            return doSubmitAnswer(userId, sessionId, request);
        } catch (DataIntegrityViolationException ex) {
            LOG.warn("Duplicate answer detected for session={} question={}, returning existing result",
                    sessionId, request.strapiQuestionId());
            return buildExistingAnswerResult(userId, sessionId, request.strapiQuestionId());
        }
    }

    private AnswerResultDto doSubmitAnswer(UUID userId, UUID sessionId,
                                           SubmitAnswerRequest request) {
        StudySession session = sessionRepository.findByIdAndKeycloakUserId(sessionId, userId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, ErrorCode.VALIDATION_ERROR, "Session not found: " + sessionId));
        // The session is the source of truth for locale — never trust a request parameter.
        String locale = session.getLocale();

        if (session.getStatus() != SessionStatus.IN_PROGRESS) {
            throw new AppException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_STATUS_TRANSITION, "Session is not in progress: " + session.getStatus());
        }

        // Idempotency check: if this question was already answered (e.g. network retry),
        // return the existing result without attempting a duplicate insert.
        if (answerRepository.findBySessionIdAndStrapiQuestionId(sessionId, request.strapiQuestionId()).isPresent()) {
            LOG.warn("Duplicate answer detected (idempotency): session={} question={}, returning existing result",
                    sessionId, request.strapiQuestionId());
            return buildExistingAnswerResult(userId, sessionId, request.strapiQuestionId());
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
                    // Strapi does not set domain directly on questions — resolve from topic cache.
                    String resolvedDomain = question.domain() != null ? question.domain().code() : null;
                    if ((resolvedDomain == null || resolvedDomain.isBlank())
                            && question.topic() != null && question.topic().code() != null) {
                        resolvedDomain = strapiContentCache.getDomainCodeForTopic(
                                question.topic().code(), session.getProductCode(), locale);
                    }
                    QuestionProgress qp = new QuestionProgress(
                            userId, request.strapiQuestionId(),
                            session.getProductCode(),
                            resolvedDomain != null ? resolvedDomain : "",
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

        // Record the answer — use SKIP_ANSWER_JSON for skips (JSONB accepts JSON null)
        int questionOrder = session.getAnsweredCount() + 1;
        String userAnswerJson = isSkipped ? SKIP_ANSWER_JSON : serializeJson(request.answer());
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

        // Sync progress to active study plan day (if one exists for today)
        incrementStudyPlanProgress(userId, session.getProductCode(), session.getDomainCode());

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
            // Use the question list stored at session start. Fall back to re-selecting only
            // for legacy sessions that predate V9 (no stored IDs).
            List<String> allIds = session.getQuestionIdList();
            if (allIds.isEmpty()) {
                allIds = questionSelectionService.selectQuestions(
                        userId, session.getProductCode(), session.getDomainCode(),
                        SessionType.PRACTICE, session.getTotalQuestions(), locale);
            }
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

    /**
     * Increments the questionsCompleted counter on today's active study plan day,
     * if one exists and matches the session's domain (or is an "ALL" mixed-review day).
     */
    private void incrementStudyPlanProgress(UUID userId, String productCode, String domainCode) {
        planRepository.findByKeycloakUserIdAndProductCodeAndStatus(userId, productCode, PlanStatus.ACTIVE)
                .ifPresent(plan -> planDayRepository.findByPlanIdAndPlanDate(
                        plan.getId(), LocalDate.now(ZoneOffset.UTC))
                        .ifPresent(day -> {
                            if ("ALL".equals(day.getDomainCode())
                                    || day.getDomainCode().equals(domainCode)) {
                                day.setQuestionsCompleted(day.getQuestionsCompleted() + 1);
                                if (day.getStatus() == PlanDayStatus.PENDING) {
                                    day.setStatus(PlanDayStatus.IN_PROGRESS);
                                }
                                if (day.getQuestionsCompleted() >= day.getQuestionTarget()) {
                                    day.setStatus(PlanDayStatus.COMPLETED);
                                    day.setCompletedAt(Instant.now());
                                }
                                planDayRepository.save(day);
                            }
                        }));
    }

    private AnswerResultDto buildExistingAnswerResult(UUID userId, UUID sessionId,
                                                      String strapiQuestionId) {
        SessionAnswer existing = answerRepository.findBySessionIdAndStrapiQuestionId(sessionId, strapiQuestionId)
                .orElseThrow(() -> new AppException(HttpStatus.CONFLICT, ErrorCode.VALIDATION_ERROR,
                        "Duplicate answer but existing record not found"));
        StudySession session = sessionRepository.findByIdAndKeycloakUserId(sessionId, userId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, ErrorCode.VALIDATION_ERROR,
                        "Session not found: " + sessionId));
        String locale = session.getLocale();
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
     * Content locale is read from the session record — not from the caller.
     *
     * @param userId    the user's Keycloak ID
     * @param sessionId the session ID
     * @return the session with current question
     */
    @Transactional(readOnly = true)
    public SessionDto getSession(@Nonnull UUID userId, @Nonnull UUID sessionId) {
        StudySession session = sessionRepository.findByIdAndKeycloakUserId(sessionId, userId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, ErrorCode.VALIDATION_ERROR, "Session not found: " + sessionId));
        String locale = session.getLocale();

        QuestionDto currentQuestion = null;
        if (session.getStatus() == SessionStatus.IN_PROGRESS
                && session.getAnsweredCount() < session.getTotalQuestions()) {
            // Use stored IDs for deterministic ordering; fall back to re-selection for legacy sessions.
            List<String> questionIds = session.getQuestionIdList();
            if (questionIds.isEmpty()) {
                questionIds = questionSelectionService.selectQuestions(
                        userId, session.getProductCode(), session.getDomainCode(),
                        SessionType.PRACTICE, session.getTotalQuestions(), locale);
            }
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
                        SKIP_ANSWER_JSON.equals(a.getUserAnswer())))
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

    /**
     * Returns all active domains for a product with per-user mastery breakdown.
     * When {@code userId} is null (e.g. direct API call bypassing the gateway),
     * mastery stats are omitted (all zeros) and access defaults to free tier.
     *
     * @param userId      the user's Keycloak ID, or null if unauthenticated
     * @param productCode the product code (e.g. "auto-b")
     * @param locale      content locale
     * @param access      the user's access grant (free or paid)
     * @return sorted list of active domain summaries
     */
    @Transactional(readOnly = true)
    public List<DomainSummaryDto> listPracticeDomains(
            @Nullable UUID userId,
            @Nonnull String productCode,
            @Nonnull String locale,
            @Nonnull AccessGrant access) {

        List<StrapiDomainDto> domains = strapiContentCache.getDomains(productCode, locale);

        // Aggregate mastery stats from question_progress — domain_progress table is never populated
        Map<String, QuestionProgressRepository.DomainMasteryProjection> progressMap = userId != null
                ? progressRepository.aggregateByDomain(userId, productCode).stream()
                        .collect(java.util.stream.Collectors.toMap(
                                QuestionProgressRepository.DomainMasteryProjection::getDomainCode, p -> p))
                : Map.of();

        return domains.stream()
                .filter(StrapiDomainDto::isActive)
                .sorted(Comparator.comparingInt(StrapiDomainDto::sortOrder))
                .map(d -> {
                    int totalQuestions = strapiContentCache.getQuestionCountByDomain(d.code(), locale);
                    boolean isLocked = !access.isPaid() && !d.isFreePreview();
                    QuestionProgressRepository.DomainMasteryProjection agg = progressMap.get(d.code());

                    int masteredCount = 0;
                    int learningCount = 0;
                    int newCount = totalQuestions;
                    double masteryPercent = 0.0;

                    if (agg != null) {
                        masteredCount = (int) agg.getMasteredCount();
                        learningCount = (int) agg.getLearningCount();
                        newCount = Math.max(0, totalQuestions - masteredCount - learningCount);
                        masteryPercent = totalQuestions > 0
                                ? (double) masteredCount / totalQuestions * 100.0 : 0.0;
                    }

                    return new DomainSummaryDto(
                            d.code(), d.name(), d.icon(), d.color(),
                            totalQuestions, masteredCount, learningCount, newCount,
                            masteryPercent, isLocked);
                })
                .toList();
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
