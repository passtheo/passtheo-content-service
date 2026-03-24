package com.passtheo.content.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.passtheo.content.domain.entity.ExamAnswer;
import com.passtheo.content.domain.entity.ExamAttempt;
import com.passtheo.content.domain.entity.OutboxEvent;
import com.passtheo.content.domain.enums.ExamType;
import com.passtheo.content.domain.enums.OutboxStatus;
import com.passtheo.content.dto.request.StartExamRequest;
import com.passtheo.content.dto.request.SubmitExamRequest;
import com.passtheo.content.dto.response.AnswerResultDto;
import com.passtheo.content.dto.response.EarnedAchievementDto;
import com.passtheo.content.dto.response.ExamDto;
import com.passtheo.content.dto.response.ExamHistorySummaryDto;
import com.passtheo.content.dto.response.ExamResultDto;
import com.passtheo.content.dto.response.QuestionDto;
import com.passtheo.content.integration.strapi.StrapiContentCache;
import com.passtheo.content.integration.strapi.dto.StrapiExamConfigDto;
import com.passtheo.content.integration.strapi.dto.StrapiQuestionDto;
import com.passtheo.content.repository.ExamAnswerRepository;
import com.passtheo.content.repository.ExamAttemptRepository;
import com.passtheo.content.repository.OutboxEventRepository;
import com.passtheo.shared.core.context.TenantContext;
import com.passtheo.shared.core.exception.AppException;
import com.passtheo.shared.core.exception.ErrorCode;
import com.passtheo.shared.events.config.KafkaTopic;
import com.passtheo.shared.events.content.ExamCompletedEvent;
import org.springframework.http.HttpStatus;
import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Manages mock exam generation, grading, and history.
 * Loads ExamConfig from Strapi, selects questions across domains, grades on submit.
 */
@Service
public class MockExamService {

    private static final Logger LOG = LoggerFactory.getLogger(MockExamService.class);
    private static final Logger AUDIT = LoggerFactory.getLogger("AUDIT");

    private final ExamAttemptRepository examAttemptRepository;
    private final ExamAnswerRepository examAnswerRepository;
    private final StrapiContentCache strapiContentCache;
    private final AnswerProcessingService answerProcessingService;
    private final AchievementService achievementService;
    private final ReadinessService readinessService;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    /**
     * Constructs the mock exam service.
     *
     * @param examAttemptRepository  exam attempt repository
     * @param examAnswerRepository   exam answer repository
     * @param strapiContentCache     Strapi content cache
     * @param answerProcessingService answer grading
     * @param achievementService     achievement checking
     * @param readinessService       readiness score calculator
     * @param outboxEventRepository  outbox event repository
     * @param objectMapper           JSON serializer
     */
    public MockExamService(ExamAttemptRepository examAttemptRepository,
                           ExamAnswerRepository examAnswerRepository,
                           StrapiContentCache strapiContentCache,
                           AnswerProcessingService answerProcessingService,
                           AchievementService achievementService,
                           ReadinessService readinessService,
                           OutboxEventRepository outboxEventRepository,
                           ObjectMapper objectMapper) {
        this.examAttemptRepository = examAttemptRepository;
        this.examAnswerRepository = examAnswerRepository;
        this.strapiContentCache = strapiContentCache;
        this.answerProcessingService = answerProcessingService;
        this.achievementService = achievementService;
        this.readinessService = readinessService;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Starts a mock exam — generates questions across all domains, shuffled.
     *
     * @param userId  the user's Keycloak ID
     * @param request the start exam request
     * @return the exam with all questions
     */
    @Transactional
    public ExamDto startExam(@Nonnull UUID userId, @Nonnull StartExamRequest request) {
        String locale = request.locale() != null ? request.locale() : "nl";

        StrapiExamConfigDto examConfig = strapiContentCache.getExamConfig(request.productCode());
        if (examConfig == null) {
            LOG.error("Exam config not found: product={}", request.productCode());
            throw new AppException(HttpStatus.NOT_FOUND, ErrorCode.VALIDATION_ERROR, "Exam config not found for product: " + request.productCode());
        }
        LOG.debug("Exam config loaded: product={}, totalQ={}, timeLimitMin={}, passScore={}",
                request.productCode(), examConfig.totalQuestions(),
                examConfig.timeLimitMinutes(), examConfig.passScore());

        // Get all questions for the product
        List<StrapiQuestionDto> allQuestions = strapiContentCache
                .getQuestionsByProduct(request.productCode(), locale);

        if (allQuestions.size() < examConfig.totalQuestions()) {
            LOG.warn("Not enough questions: have={}, need={}", allQuestions.size(), examConfig.totalQuestions());
        }

        // Select questions using domain weights + difficulty distribution
        List<StrapiQuestionDto> examQuestions = selectQuestions(allQuestions, examConfig);

        // Create exam attempt (placeholder — completed on submit)
        UUID examId = UUID.randomUUID();
        Instant startedAt = Instant.now();
        int timeLimitSeconds = examConfig.timeLimitMinutes() * 60;
        Instant expiresAt = startedAt.plus(examConfig.timeLimitMinutes(), ChronoUnit.MINUTES);

        // Convert to QuestionDto (without correct answers)
        List<QuestionDto> questionDtos = new ArrayList<>();
        for (int i = 0; i < examQuestions.size(); i++) {
            StrapiQuestionDto q = examQuestions.get(i);
            questionDtos.add(new QuestionDto(
                    q.documentId(), q.questionText(), q.interactionType(),
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
                    i + 1, q.domain() != null ? q.domain().code() : null
            ));
        }

        // Save placeholder attempt
        ExamAttempt attempt = new ExamAttempt();
        attempt.setTenantId(TenantContext.get());
        attempt.setKeycloakUserId(userId);
        attempt.setProductCode(request.productCode());
        attempt.setExamType(ExamType.MOCK_EXAM);
        attempt.setTotalQuestions(examQuestions.size());
        attempt.setCorrectCount(0);
        attempt.setPassScore(examConfig.passScore());
        attempt.setPassed(false);
        attempt.setScorePercent(BigDecimal.ZERO);
        attempt.setTimeTakenSeconds(0);
        attempt.setTimeLimitSeconds(timeLimitSeconds);
        attempt.setDomainBreakdown("{}");
        attempt.setStartedAt(startedAt);
        attempt.setCompletedAt(startedAt); // placeholder
        attempt = examAttemptRepository.save(attempt);

        LOG.info("Mock exam started: id={}, user={}, product={}, questions={}",
                attempt.getId(), userId, request.productCode(), examQuestions.size());

        return new ExamDto(
                attempt.getId(),
                examQuestions.size(),
                timeLimitSeconds,
                examConfig.passScore(),
                startedAt,
                expiresAt,
                questionDtos
        );
    }

    /**
     * Submits all exam answers, grades them, calculates results.
     *
     * @param userId  the user's Keycloak ID
     * @param examId  the exam attempt ID
     * @param request the submit exam request
     * @param locale  the content locale
     * @return the exam result
     */
    @Transactional
    public ExamResultDto submitExam(@Nonnull UUID userId, @Nonnull UUID examId,
                                    @Nonnull SubmitExamRequest request, @Nonnull String locale) {
        ExamAttempt attempt = examAttemptRepository.findByIdAndKeycloakUserId(examId, userId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, ErrorCode.VALIDATION_ERROR, "Exam attempt not found: " + examId));

        // Grade all answers
        int correctCount = 0;
        Map<String, int[]> domainStats = new LinkedHashMap<>(); // domainCode -> [correct, total]
        List<ExamResultDto.WrongAnswerDto> wrongAnswers = new ArrayList<>();

        for (int i = 0; i < request.answers().size(); i++) {
            SubmitExamRequest.ExamAnswerItem item = request.answers().get(i);
            StrapiQuestionDto question = strapiContentCache.getQuestion(item.strapiQuestionId(), locale);
            if (question == null) {
                continue;
            }

            boolean isCorrect = answerProcessingService.gradeAnswer(question, item.answer());
            if (isCorrect) {
                correctCount++;
            }

            String domainCode = question.domain() != null ? question.domain().code() : "unknown";
            domainStats.computeIfAbsent(domainCode, k -> new int[]{0, 0});
            domainStats.get(domainCode)[1]++;
            if (isCorrect) {
                domainStats.get(domainCode)[0]++;
            }

            // Save exam answer
            ExamAnswer examAnswer = new ExamAnswer();
            examAnswer.setTenantId(TenantContext.get());
            examAnswer.setExamAttemptId(examId);
            examAnswer.setStrapiQuestionId(item.strapiQuestionId());
            examAnswer.setQuestionVersion(question.version());
            examAnswer.setDomainCode(domainCode);
            examAnswer.setCorrect(isCorrect);
            examAnswer.setUserAnswer(serializeJson(item.answer()));
            examAnswer.setCorrectAnswer(serializeJson(answerProcessingService.buildCorrectAnswer(question)));
            examAnswer.setTimeTakenMs(item.timeTakenMs());
            examAnswer.setQuestionOrder(i + 1);
            examAnswer.setAnsweredAt(Instant.now());
            examAnswerRepository.save(examAnswer);

            // Collect wrong answers for review
            if (!isCorrect) {
                AnswerResultDto.ExplanationDto explanation = question.explanation() != null
                        ? new AnswerResultDto.ExplanationDto(
                        question.explanation().text(), question.explanation().tip(),
                        question.explanation().image(), question.explanation().legalReference())
                        : null;
                wrongAnswers.add(new ExamResultDto.WrongAnswerDto(
                        item.strapiQuestionId(), question.questionText(),
                        item.answer(), answerProcessingService.buildCorrectAnswer(question),
                        explanation));
            }
        }

        // Calculate results
        int totalQuestions = request.answers().size();
        boolean passed = correctCount >= attempt.getPassScore();
        double scorePercent = totalQuestions > 0
                ? (double) correctCount / totalQuestions * 100.0 : 0.0;
        int timeTakenSeconds = (int) ChronoUnit.SECONDS.between(attempt.getStartedAt(), Instant.now());

        // Update attempt
        attempt.setCorrectCount(correctCount);
        attempt.setPassed(passed);
        attempt.setScorePercent(BigDecimal.valueOf(scorePercent).setScale(2, RoundingMode.HALF_UP));
        attempt.setTimeTakenSeconds(timeTakenSeconds);
        attempt.setCompletedAt(Instant.now());
        attempt.setDomainBreakdown(serializeJson(domainStats));
        examAttemptRepository.save(attempt);

        // Build domain breakdown DTOs
        List<ExamResultDto.DomainBreakdownDto> domainBreakdown = domainStats.entrySet().stream()
                .map(e -> new ExamResultDto.DomainBreakdownDto(
                        e.getKey(), e.getKey(),
                        e.getValue()[0], e.getValue()[1],
                        e.getValue()[1] > 0 ? (double) e.getValue()[0] / e.getValue()[1] * 100.0 : 0.0))
                .toList();

        // Calculate readiness update
        var readiness = readinessService.calculate(userId, attempt.getProductCode(), locale);
        ExamResultDto.ReadinessUpdateDto readinessUpdate = new ExamResultDto.ReadinessUpdateDto(
                0.0, readiness.readinessScore(), readiness.readinessLabel().name());

        List<EarnedAchievementDto> achievements = achievementService
                .checkAchievements(userId, attempt.getProductCode());

        // Publish exam.completed outbox event
        List<String> weakDomains = domainStats.entrySet().stream()
                .filter(e -> e.getValue()[1] > 0 && (double) e.getValue()[0] / e.getValue()[1] < 0.6)
                .map(Map.Entry::getKey)
                .toList();
        publishExamCompletedEvent(TenantContext.get(), userId, examId,
                attempt.getProductCode(), passed, correctCount, totalQuestions, scorePercent, weakDomains);

        LOG.info("Mock exam submitted: id={}, user={}, correct={}/{}, passed={}",
                examId, userId, correctCount, totalQuestions, passed);

        return new ExamResultDto(
                examId, passed, correctCount, totalQuestions,
                attempt.getPassScore(), scorePercent, timeTakenSeconds,
                domainBreakdown, wrongAnswers, readinessUpdate, achievements
        );
    }

    /**
     * Gets exam history for a user/product.
     *
     * @param userId      the user's Keycloak ID
     * @param productCode the product code
     * @param page        page number
     * @param size        page size
     * @return list of exam history summaries
     */
    @Transactional(readOnly = true)
    public List<ExamHistorySummaryDto> getExamHistory(@Nonnull UUID userId, @Nonnull String productCode,
                                                      int page, int size) {
        Page<ExamAttempt> attempts = examAttemptRepository
                .findByKeycloakUserIdAndProductCodeOrderByCompletedAtDesc(
                        userId, productCode, PageRequest.of(page, size));
        return attempts.getContent().stream()
                .map(a -> new ExamHistorySummaryDto(
                        a.getId(), a.isPassed(), a.getCorrectCount(),
                        a.getTotalQuestions(), a.getScorePercent().doubleValue(),
                        a.getCompletedAt()))
                .toList();
    }

    private void publishExamCompletedEvent(java.util.UUID tenantId, java.util.UUID userId,
                                            java.util.UUID examAttemptId, String productCode,
                                            boolean passed, int correctCount, int totalQuestions,
                                            double scorePercent, List<String> weakDomains) {
        try {
            ExamCompletedEvent event = ExamCompletedEvent.create(
                    tenantId, userId, examAttemptId, productCode,
                    passed, correctCount, totalQuestions, scorePercent, weakDomains);
            OutboxEvent outbox = new OutboxEvent();
            outbox.setTenantId(tenantId);
            outbox.setEventType(event.eventType());
            outbox.setTopic(KafkaTopic.CONTENT_EVENTS);
            outbox.setPayload(objectMapper.writeValueAsString(event));
            outbox.setStatus(OutboxStatus.PENDING);
            outbox.setPartitionKey(userId.toString());
            outboxEventRepository.save(outbox);
        } catch (JsonProcessingException e) {
            LOG.error("Failed to serialize ExamCompletedEvent for user={}", userId, e);
        }
    }

    /**
     * Selects exam questions using domain weights and difficulty distribution from ExamConfig.
     * Falls back to simple shuffle-and-limit if no domainWeights are configured.
     */
    private List<StrapiQuestionDto> selectQuestions(List<StrapiQuestionDto> allQuestions,
                                                     StrapiExamConfigDto examConfig) {
        List<StrapiExamConfigDto.DomainWeightDto> weights = examConfig.domainWeights();
        Map<String, Double> difficultyDist = examConfig.difficultyDistribution();
        int totalTarget = examConfig.totalQuestions();

        // Phase 1: domain-weighted selection
        List<StrapiQuestionDto> domainSelected;
        if (weights != null && !weights.isEmpty()) {
            Map<String, List<StrapiQuestionDto>> byDomain = allQuestions.stream()
                    .collect(Collectors.groupingBy(q -> q.domain() != null ? q.domain().code() : ""));
            List<StrapiQuestionDto> selected = new ArrayList<>();
            for (StrapiExamConfigDto.DomainWeightDto w : weights) {
                List<StrapiQuestionDto> pool = new ArrayList<>(
                        byDomain.getOrDefault(w.domainCode(), List.of()));
                Collections.shuffle(pool);
                int take = pool.size() >= w.targetQuestions() ? w.targetQuestions()
                        : Math.min(pool.size(), w.minQuestions());
                selected.addAll(pool.subList(0, take));
            }
            // If we ended up with fewer than target (small pools), top up from remaining questions
            if (selected.size() < totalTarget) {
                Set<String> selectedIds = selected.stream()
                        .map(StrapiQuestionDto::documentId).collect(Collectors.toSet());
                List<StrapiQuestionDto> remainder = allQuestions.stream()
                        .filter(q -> !selectedIds.contains(q.documentId()))
                        .collect(Collectors.toCollection(ArrayList::new));
                Collections.shuffle(remainder);
                int need = totalTarget - selected.size();
                selected.addAll(remainder.subList(0, Math.min(need, remainder.size())));
            }
            domainSelected = selected;
        } else {
            // No domain weights: simple shuffle
            List<StrapiQuestionDto> shuffled = new ArrayList<>(allQuestions);
            Collections.shuffle(shuffled);
            domainSelected = shuffled.stream().limit(totalTarget).collect(Collectors.toList());
        }

        // Phase 2: difficulty distribution within selected pool
        if (difficultyDist != null && !difficultyDist.isEmpty()) {
            Map<String, List<StrapiQuestionDto>> byDifficulty = domainSelected.stream()
                    .collect(Collectors.groupingBy(q -> q.difficulty() != null ? q.difficulty() : "medium"));
            List<StrapiQuestionDto> distributed = new ArrayList<>();
            for (Map.Entry<String, Double> entry : difficultyDist.entrySet()) {
                String difficulty = entry.getKey();
                int target = (int) Math.round(entry.getValue() * totalTarget);
                List<StrapiQuestionDto> pool = new ArrayList<>(
                        byDifficulty.getOrDefault(difficulty, List.of()));
                Collections.shuffle(pool);
                distributed.addAll(pool.subList(0, Math.min(target, pool.size())));
            }
            // Fill any gap from remaining questions if difficulty buckets ran short
            if (distributed.size() < totalTarget) {
                Set<String> usedIds = distributed.stream()
                        .map(StrapiQuestionDto::documentId).collect(Collectors.toSet());
                List<StrapiQuestionDto> remaining = domainSelected.stream()
                        .filter(q -> !usedIds.contains(q.documentId()))
                        .collect(Collectors.toCollection(ArrayList::new));
                Collections.shuffle(remaining);
                int need = totalTarget - distributed.size();
                distributed.addAll(remaining.subList(0, Math.min(need, remaining.size())));
            }
            // Truncate if over target (rounding may produce excess)
            List<StrapiQuestionDto> result = distributed.size() > totalTarget
                    ? distributed.subList(0, totalTarget) : distributed;
            Collections.shuffle(result);
            return List.copyOf(result);
        }

        // No difficulty distribution: just shuffle domain selection and limit
        Collections.shuffle(domainSelected);
        return domainSelected.size() > totalTarget
                ? List.copyOf(domainSelected.subList(0, totalTarget))
                : List.copyOf(domainSelected);
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
