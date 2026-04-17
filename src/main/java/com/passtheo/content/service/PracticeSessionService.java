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
import com.passtheo.content.domain.valueobject.XpResult;
import com.passtheo.content.dto.request.StartSessionRequest;
import com.passtheo.content.dto.request.SubmitAnswerRequest;
import com.passtheo.content.dto.response.ActiveSessionDto;
import com.passtheo.content.dto.response.AnswerResultDto;
import com.passtheo.shared.core.dto.AccessGrant;
import com.passtheo.content.dto.response.AnsweredQuestionFullDto;
import com.passtheo.content.dto.response.AnsweredQuestionSummaryDto;
import com.passtheo.content.dto.response.DomainSummaryDto;
import com.passtheo.content.domain.entity.QuestionReport;
import com.passtheo.content.dto.request.QuestionReportRequest;
import com.passtheo.content.dto.response.BreakdownQuestionDto;
import com.passtheo.content.dto.response.EarnedAchievementDto;
import com.passtheo.content.dto.response.QuestionDto;
import com.passtheo.content.dto.response.SessionBreakdownDto;
import com.passtheo.content.dto.response.SessionDto;
import com.passtheo.content.dto.response.StreakUpdateDto;
import com.passtheo.content.dto.response.XpUpdateDto;
import com.passtheo.content.dto.response.SessionSummaryDto;
import com.passtheo.content.integration.strapi.dto.StrapiDomainDto;
import com.passtheo.content.integration.strapi.StrapiContentCache;
import com.passtheo.content.integration.strapi.dto.StrapiQuestionDto;
import com.passtheo.shared.outbox.repository.OutboxEventRepository;
import com.passtheo.content.repository.QuestionProgressRepository;
import com.passtheo.content.repository.QuestionReportRepository;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private final XpService xpService;
    private final StrapiContentCache strapiContentCache;
    private final StudyPlanRepository planRepository;
    private final StudyPlanDayRepository planDayRepository;
    private final QuestionReportRepository questionReportRepository;
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
     * @param xpService                XP grant service
     * @param strapiContentCache       Strapi content cache
     * @param planRepository           study plan repository for progress sync
     * @param planDayRepository        study plan day repository for progress sync
     * @param questionReportRepository question report repository
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
                                  XpService xpService,
                                  StrapiContentCache strapiContentCache,
                                  StudyPlanRepository planRepository,
                                  StudyPlanDayRepository planDayRepository,
                                  QuestionReportRepository questionReportRepository,
                                  OutboxEventRepository outboxEventRepository,
                                  ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.answerRepository = answerRepository;
        this.progressRepository = progressRepository;
        this.questionSelectionService = questionSelectionService;
        this.answerProcessingService = answerProcessingService;
        this.streakService = streakService;
        this.achievementService = achievementService;
        this.xpService = xpService;
        this.strapiContentCache = strapiContentCache;
        this.planRepository = planRepository;
        this.planDayRepository = planDayRepository;
        this.questionReportRepository = questionReportRepository;
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

        // Check minimum question pool before selection.
        String topicCode = request.topicCode();
        boolean hasTopicCode = topicCode != null && !topicCode.isBlank();
        int availableCount;
        if (hasTopicCode) {
            availableCount = strapiContentCache.getQuestionCountByTopic(topicCode, locale);
        } else if (request.domainCode() != null && !request.domainCode().isBlank()) {
            availableCount = strapiContentCache.getQuestionCountByDomain(request.domainCode(), locale);
        } else {
            availableCount = strapiContentCache.getQuestionCount(request.productCode(), locale);
        }
        if (availableCount < 5) {
            throw new AppException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                    "Not enough questions available. At least 5 required.");
        }

        // When practising a specific topic, use ALL available questions — the user
        // expects to go through every question in the topic, not a capped subset.
        int effectiveCount = hasTopicCode ? availableCount : request.questionCount();

        // Select questions using spaced repetition — filter by topic when provided.
        List<String> questionIds = questionSelectionService.selectQuestions(
                userId, request.productCode(), request.domainCode(),
                hasTopicCode ? topicCode : null,
                sessionType, effectiveCount, locale);

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

        // Get first valid question — skip any that were deleted/deactivated between selection and now
        QuestionDto firstQuestion = findNextValidQuestion(questionIds, 0, locale);
        if (firstQuestion == null) {
            throw new AppException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                    "No active questions available — all selected questions have been removed or deactivated.");
        }

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
                List.of(),
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

        // Store question snapshot for breakdown review (avoids re-fetching from Strapi)
        answer.setQuestionSnapshot(serializeJson(answerProcessingService.buildQuestionSnapshot(question)));
        answer.setPreviousMasteryLevel(previousLevel.name());
        answer.setNewMasteryLevel(progress.getMasteryLevel().name());
        String domainCode = resolveDomainCode(question, session.getProductCode(), session.getLocale());
        answer.setDomainCode(domainCode);
        String domainNameVal = question.domain() != null ? question.domain().name() : null;
        if (domainNameVal == null && domainCode != null) {
            domainNameVal = resolveDomainName(domainCode, session.getProductCode(), session.getLocale());
        }
        answer.setDomainName(domainNameVal);

        // Auto-unflag: when a flagged question is answered correctly, remove the flag.
        // The progress entity is already managed within this transaction — dirty checking
        // will flush the change at commit, no explicit save needed.
        if (isCorrect && progress.isFlagged()) {
            progress.setFlagged(false);
            LOG.debug("Auto-unflagged question: user={}, question={}", userId, request.strapiQuestionId());
        }

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

        // Check achievements
        List<EarnedAchievementDto> newAchievements = achievementService
                .checkAchievements(userId, session.getProductCode());

        // Grant per-answer XP
        int answerXp = isCorrect ? XpService.XP_CORRECT_ANSWER : XpService.XP_WRONG_ANSWER;
        int achievementXp = newAchievements.stream().mapToInt(EarnedAchievementDto::xpReward).sum();
        xpService.grantXp(userId, session.getProductCode(), answerXp + achievementXp);

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
                        session.getTopicCode(),
                        SessionType.PRACTICE, session.getTotalQuestions(), locale);
            }
            if (questionOrder < allIds.size()) {
                // Skip any deactivated/deleted questions to find the next valid one
                nextQuestion = findNextValidQuestion(allIds, questionOrder, locale);
            }
            // All remaining questions were deactivated — adjust totalQuestions so the
            // session naturally reaches the COMPLETED threshold on the next completeSession call.
            if (nextQuestion == null) {
                session.setTotalQuestions(session.getAnsweredCount());
                sessionRepository.save(session);
                LOG.info("Adjusted session totalQuestions: sessionId={}, newTotal={} (remaining questions deactivated)",
                        session.getId(), session.getAnsweredCount());
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
        int effectiveTotalQuestions = session.getTotalQuestions();
        if (session.getStatus() == SessionStatus.IN_PROGRESS
                && session.getAnsweredCount() < session.getTotalQuestions()) {
            // Use stored IDs for deterministic ordering; fall back to re-selection for legacy sessions.
            List<String> questionIds = session.getQuestionIdList();
            if (questionIds.isEmpty()) {
                questionIds = questionSelectionService.selectQuestions(
                        userId, session.getProductCode(), session.getDomainCode(),
                        session.getTopicCode(),
                        SessionType.PRACTICE, session.getTotalQuestions(), locale);
            }
            if (session.getAnsweredCount() < questionIds.size()) {
                // Skip any deactivated/deleted questions to find the next valid one
                currentQuestion = findNextValidQuestion(
                        questionIds, session.getAnsweredCount(), locale);
            }
            // All remaining questions were deactivated — report answered count as total
            // so the client shows the session as completable.
            if (currentQuestion == null) {
                effectiveTotalQuestions = session.getAnsweredCount();
            }
        }

        List<SessionAnswer> answers = answerRepository
                .findBySessionIdOrderByQuestionOrderAsc(sessionId);

        List<AnsweredQuestionSummaryDto> answeredQuestions = answers.stream()
                .map(a -> new AnsweredQuestionSummaryDto(
                        a.getQuestionOrder(),
                        a.getStrapiQuestionId(),
                        a.isCorrect(),
                        SKIP_ANSWER_JSON.equals(a.getUserAnswer())))
                .toList();

        List<AnsweredQuestionFullDto> answeredQuestionContents = answers.stream()
                .map(a -> buildAnsweredQuestionFull(a, locale))
                .filter(java.util.Objects::nonNull)
                .toList();

        return new SessionDto(
                session.getId(),
                session.getStatus().name(),
                effectiveTotalQuestions,
                session.getAnsweredCount(),
                session.getCorrectCount(),
                currentQuestion,
                answeredQuestions,
                answeredQuestionContents
        );
    }

    /**
     * Builds a full-content snapshot for a previously answered question. Returns
     * null when the underlying question has been deleted or deactivated in
     * Strapi (caller filters nulls). The explanation is translated from the
     * {@link QuestionDto.ExplanationDto} shape (used on the question payload)
     * to the {@link AnswerResultDto.ExplanationDto} shape (used on reveal).
     */
    @Nullable
    private AnsweredQuestionFullDto buildAnsweredQuestionFull(@Nonnull SessionAnswer answer,
                                                              @Nonnull String locale) {
        QuestionDto question = loadQuestion(
                answer.getStrapiQuestionId(), locale, answer.getQuestionOrder());
        if (question == null) {
            return null;
        }
        boolean skipped = SKIP_ANSWER_JSON.equals(answer.getUserAnswer());
        Map<String, Object> userAnswer = skipped ? null : deserializeJson(answer.getUserAnswer());
        Map<String, Object> correctAnswer = deserializeJson(answer.getCorrectAnswer());
        AnswerResultDto.ExplanationDto explanation = null;
        if (question.explanation() != null) {
            explanation = new AnswerResultDto.ExplanationDto(
                    question.explanation().text(),
                    question.explanation().tip(),
                    question.explanation().imageUrl(),
                    question.explanation().legalReference());
        }
        return new AnsweredQuestionFullDto(
                answer.getQuestionOrder(),
                question,
                userAnswer,
                correctAnswer,
                explanation,
                answer.isCorrect(),
                skipped,
                answer.getTimeTakenMs()
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

        // Auto-skip unanswered questions so they appear in the breakdown
        autoSkipUnansweredQuestions(userId, session);

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

        // Grant session completion XP bonus + any achievement XP
        int achievementXp = achievements.stream().mapToInt(EarnedAchievementDto::xpReward).sum();
        XpResult xpResult = xpService.grantXp(userId, session.getProductCode(),
                XpService.XP_PRACTICE_COMPLETE + achievementXp);

        // Compute actual mastery changes from session answers
        List<SessionAnswer> sessionAnswers = answerRepository.findBySessionIdOrderByQuestionOrderAsc(sessionId);
        SessionSummaryDto.MasteryChangesDto masteryChanges = computeMasteryChanges(sessionAnswers);

        LOG.info("Session completed: id={}, user={}, correct={}/{}, accuracy={}%, timeSpentSec={}, xp={}",
                sessionId, userId, session.getCorrectCount(), session.getTotalQuestions(),
                session.getAccuracyPercent(), session.getTimeSpentSeconds(),
                XpService.XP_PRACTICE_COMPLETE + achievementXp);
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
                masteryChanges,
                new StreakUpdateDto(streak.currentStreak(), streak.isNewDay()),
                achievements,
                new XpUpdateDto(XpService.XP_PRACTICE_COMPLETE + achievementXp, xpResult.totalXp(),
                        xpResult.currentLevel(), xpResult.xpForNextLevel(), xpResult.leveledUp())
        );
    }

    /**
     * Loads a single question from Strapi cache by ID.
     *
     * @param questionId the Strapi question document ID
     * @param locale     the content locale
     * @param order      the question order number in the session
     * @return the question DTO, or null if the question was deleted/deactivated in Strapi
     */
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
     * Finds the next valid (non-deleted, non-deactivated) question starting from the given
     * index in the question ID list. Skips questions that return null from Strapi.
     *
     * @param questionIds the ordered list of question IDs for the session
     * @param startIndex  the index to start searching from (inclusive)
     * @param locale      the content locale
     * @return the loaded question DTO, or null if no valid question remains
     */
    @Nullable
    private QuestionDto findNextValidQuestion(@Nonnull List<String> questionIds, int startIndex,
                                              @Nonnull String locale) {
        for (int i = startIndex; i < questionIds.size(); i++) {
            QuestionDto q = loadQuestion(questionIds.get(i), locale, i + 1);
            if (q != null) {
                return q;
            }
            LOG.warn("Skipping deactivated/deleted question: id={}, index={}", questionIds.get(i), i);
        }
        return null;
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
                        masteredCount = Math.min((int) agg.getMasteredCount(), totalQuestions);
                        learningCount = Math.min((int) agg.getLearningCount(), totalQuestions - masteredCount);
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

    /**
     * Returns a full session breakdown with per-question detail for review.
     *
     * @param userId    the user's Keycloak ID
     * @param sessionId the session ID
     * @return the breakdown with all questions, answers, snapshots, and flag status
     */
    @Transactional(readOnly = true)
    public SessionBreakdownDto getSessionBreakdown(@Nonnull UUID userId, @Nonnull UUID sessionId) {
        StudySession session = sessionRepository.findByIdAndKeycloakUserId(sessionId, userId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, ErrorCode.VALIDATION_ERROR,
                        "Session not found: " + sessionId));

        List<SessionAnswer> answers = answerRepository.findBySessionIdOrderByQuestionOrderAsc(sessionId);

        // Batch-load flag status for all questions in this session
        List<String> questionIds = answers.stream()
                .map(SessionAnswer::getStrapiQuestionId)
                .toList();
        Set<String> flaggedIds = questionIds.isEmpty() ? Set.of()
                : progressRepository.findFlaggedByQuestionIds(userId, questionIds)
                        .stream()
                        .map(QuestionProgress::getStrapiQuestionId)
                        .collect(Collectors.toSet());

        int skippedCount = 0;
        List<BreakdownQuestionDto> breakdownQuestions = new ArrayList<>();

        for (SessionAnswer sa : answers) {
            boolean isSkipped = SKIP_ANSWER_JSON.equals(sa.getUserAnswer());
            if (isSkipped) {
                skippedCount++;
            }

            Map<String, Object> userAnswer = isSkipped ? null : deserializeJson(sa.getUserAnswer());
            Map<String, Object> correctAnswer = deserializeJson(sa.getCorrectAnswer());
            Map<String, Object> snapshot = sa.getQuestionSnapshot() != null
                    ? deserializeJson(sa.getQuestionSnapshot()) : Map.of();

            breakdownQuestions.add(new BreakdownQuestionDto(
                    sa.getQuestionOrder(),
                    sa.getStrapiQuestionId(),
                    sa.getInteractionType(),
                    sa.isCorrect(),
                    isSkipped,
                    userAnswer,
                    correctAnswer,
                    snapshot,
                    sa.getDomainCode(),
                    sa.getDomainName(),
                    sa.getPreviousMasteryLevel(),
                    sa.getNewMasteryLevel(),
                    flaggedIds.contains(sa.getStrapiQuestionId())
            ));
        }

        SessionSummaryDto.MasteryChangesDto masteryChanges = computeMasteryChanges(answers);

        return new SessionBreakdownDto(
                session.getId(),
                session.getStatus().name(),
                session.getTotalQuestions(),
                session.getCorrectCount(),
                skippedCount,
                session.getAccuracyPercent() != null ? session.getAccuracyPercent().doubleValue() : 0.0,
                session.getTimeSpentSeconds(),
                masteryChanges,
                List.copyOf(breakdownQuestions)
        );
    }

    /**
     * Flags a question for extra practice.
     *
     * @param userId           the user's Keycloak ID
     * @param strapiQuestionId the Strapi question ID
     */
    @Transactional
    public void flagQuestion(@Nonnull UUID userId, @Nonnull String strapiQuestionId) {
        QuestionProgress progress = progressRepository
                .findByKeycloakUserIdAndStrapiQuestionId(userId, strapiQuestionId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, ErrorCode.VALIDATION_ERROR,
                        "No progress found for question: " + strapiQuestionId));
        progress.setFlagged(true);
        progressRepository.save(progress);
        LOG.debug("Question flagged: user={}, question={}", userId, strapiQuestionId);
    }

    /**
     * Unflags a question.
     *
     * @param userId           the user's Keycloak ID
     * @param strapiQuestionId the Strapi question ID
     */
    @Transactional
    public void unflagQuestion(@Nonnull UUID userId, @Nonnull String strapiQuestionId) {
        QuestionProgress progress = progressRepository
                .findByKeycloakUserIdAndStrapiQuestionId(userId, strapiQuestionId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, ErrorCode.VALIDATION_ERROR,
                        "No progress found for question: " + strapiQuestionId));
        progress.setFlagged(false);
        progressRepository.save(progress);
        LOG.debug("Question unflagged: user={}, question={}", userId, strapiQuestionId);
    }

    /**
     * Reports an error in a question.
     *
     * @param userId           the user's Keycloak ID
     * @param strapiQuestionId the Strapi question document ID
     * @param request          the report request
     */
    @Transactional
    public void reportQuestion(@Nonnull UUID userId, @Nonnull String strapiQuestionId,
                               @Nonnull QuestionReportRequest request) {
        QuestionReport report = new QuestionReport(
                TenantContext.get(), userId, strapiQuestionId, request.reportType(), request.comment());
        questionReportRepository.save(report);
        LOG.info("Question reported: user={}, question={}, type={}", userId, strapiQuestionId, request.reportType());
    }

    /**
     * Creates skip records for all unanswered questions in a session.
     * Called during completeSession() so the breakdown shows every question.
     * Does NOT affect mastery — skips are neutral for spaced repetition.
     */
    private void autoSkipUnansweredQuestions(@Nonnull UUID userId, @Nonnull StudySession session) {
        List<String> allQuestionIds = session.getQuestionIdList();
        if (allQuestionIds.isEmpty()) {
            return; // Legacy session without stored question IDs
        }

        Set<String> answeredIds = answerRepository.findBySessionIdOrderByQuestionOrderAsc(session.getId())
                .stream()
                .map(SessionAnswer::getStrapiQuestionId)
                .collect(Collectors.toSet());

        String locale = session.getLocale();
        List<SessionAnswer> skipAnswers = new ArrayList<>();

        for (int i = 0; i < allQuestionIds.size(); i++) {
            String questionId = allQuestionIds.get(i);
            if (answeredIds.contains(questionId)) {
                continue;
            }

            StrapiQuestionDto question = strapiContentCache.getQuestion(questionId, locale);
            if (question == null) {
                continue; // Deleted/deactivated in Strapi
            }

            int questionOrder = i + 1; // 1-indexed position in the full list

            // Look up current mastery level (unchanged since skip doesn't affect it)
            QuestionProgress progress = progressRepository
                    .findByKeycloakUserIdAndStrapiQuestionId(userId, questionId)
                    .orElse(null);
            String masteryLevel = progress != null ? progress.getMasteryLevel().name() : MasteryLevel.NEW.name();

            SessionAnswer skipAnswer = new SessionAnswer(
                    session.getId(), userId, questionId,
                    question.version(), question.interactionType(), false,
                    SKIP_ANSWER_JSON, serializeJson(answerProcessingService.buildCorrectAnswer(question)),
                    0, questionOrder);
            skipAnswer.setTenantId(TenantContext.get());
            skipAnswer.setQuestionSnapshot(serializeJson(answerProcessingService.buildQuestionSnapshot(question)));
            skipAnswer.setPreviousMasteryLevel(masteryLevel);
            skipAnswer.setNewMasteryLevel(masteryLevel);

            String domainCode = resolveDomainCode(question, session.getProductCode(), locale);
            skipAnswer.setDomainCode(domainCode);
            String domainName = question.domain() != null ? question.domain().name() : null;
            if (domainName == null && domainCode != null) {
                domainName = resolveDomainName(domainCode, session.getProductCode(), locale);
            }
            skipAnswer.setDomainName(domainName);

            skipAnswers.add(skipAnswer);
        }

        if (!skipAnswers.isEmpty()) {
            answerRepository.saveAll(skipAnswers);
            session.setAnsweredCount(session.getAnsweredCount() + skipAnswers.size());
            sessionRepository.save(session);
            LOG.info("Auto-skipped {} unanswered questions for session={}", skipAnswers.size(), session.getId());
        }
    }

    /**
     * Resolves the domain code for a question, falling back to the topic-to-domain mapping cache.
     */
    private String resolveDomainCode(StrapiQuestionDto question, String productCode, String locale) {
        String domainCode = question.domain() != null ? question.domain().code() : null;
        if ((domainCode == null || domainCode.isBlank())
                && question.topic() != null && question.topic().code() != null) {
            domainCode = strapiContentCache.getDomainCodeForTopic(
                    question.topic().code(), productCode, locale);
        }
        return domainCode;
    }

    /**
     * Computes mastery level changes from session answer records.
     *
     * @param answers the session answers (already loaded)
     * @return aggregated mastery changes
     */
    private SessionSummaryDto.MasteryChangesDto computeMasteryChanges(List<SessionAnswer> answers) {
        int upgraded = 0;
        int downgraded = 0;
        int unchanged = 0;

        for (SessionAnswer sa : answers) {
            String prev = sa.getPreviousMasteryLevel();
            String next = sa.getNewMasteryLevel();
            if (prev == null || next == null || prev.equals(next)) {
                unchanged++;
            } else {
                try {
                    MasteryLevel prevLevel = MasteryLevel.valueOf(prev);
                    MasteryLevel nextLevel = MasteryLevel.valueOf(next);
                    if (nextLevel.ordinal() > prevLevel.ordinal()) {
                        upgraded++;
                    } else if (nextLevel.ordinal() < prevLevel.ordinal()) {
                        downgraded++;
                    } else {
                        unchanged++;
                    }
                } catch (IllegalArgumentException e) {
                    unchanged++;
                }
            }
        }
        return new SessionSummaryDto.MasteryChangesDto(upgraded, downgraded, unchanged);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> deserializeJson(String json) {
        if (json == null || json.isBlank() || "null".equals(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            LOG.warn("Failed to deserialize JSON for breakdown: {}", e.getMessage());
            return Map.of();
        }
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
