# PassTheo — passtheo-content-service Specification

> **Service:** passtheo-content-service
> **Port:** 8087
> **Package:** `com.passtheo.content`
> **Database:** `content_db`
> **Priority:** Product — this is what PassTheo sells

---

## 1. Single Responsibility

The passtheo-content-service is the **learning engine**.
It answers: "What should this student study next, and how ready are they?"

Everything in PassTheo exists to support this service.

---

## 2. The 6 Engines

| Engine | What It Does | Priority |
|--------|-------------|----------|
| Content Gateway | Reads questions from Strapi, caches in Redis | Core |
| Practice Session | Creates sessions, serves questions, records answers | Core |
| Spaced Repetition | Selects optimal questions using modified SM-2 | Core |
| Mock Exam | Simulates full CBR exam (50q, 30min, pass 44) | Core |
| Progress + Readiness | Calculates readiness score (0-100) per domain | Core |
| Streak + Achievement | Daily streaks, freeze slots, badge triggers | Core |
| Study Plan Generator | Creates daily study schedule from weak domains | v1 |

---

## 3. Architecture Overview

```
┌───────────────┐     ┌────────────────────────────┐
│  Flutter       │────▶│  passtheo-content-service   │
│  (practice,    │     │                            │
│   exams,       │     │  ┌──────────────────────┐  │
│   progress)    │     │  │ Practice Engine       │  │
└───────────────┘     │  │ Spaced Repetition     │  │
                      │  │ Mock Exam Engine       │  │
                      │  │ Progress Calculator    │  │
                      │  │ Streak Engine          │  │
                      │  │ Achievement Checker    │  │
                      │  │ Study Plan Generator   │  │
                      │  └──────────────────────┘  │
                      │                            │
┌───────────────┐     │  ┌──────────────┐          │
│  Strapi CMS   │◀────│  │ Content      │          │
│  (questions,   │     │  │ Gateway      │          │
│   lessons,     │     │  │ (REST +      │          │
│   config)      │     │  │  Redis)      │          │
└───────────────┘     │  └──────────────┘          │
                      │                            │
┌───────────────┐     │  ┌──────────────┐          │
│  Redis         │◀──▶│  │ Caches:      │          │
│  (shared)      │     │  │ - Content    │          │
│                │     │  │ - Access     │          │
│                │     │  │   grants     │          │
└───────────────┘     │  └──────────────┘          │
                      │                            │
                      │  ┌──────────────┐   ┌─────────────┐
                      │  │ PostgreSQL   │   │  Kafka      │
                      │  │ (all user    │──▶│  (events)   │
                      │  │  learning    │   └─────────────┘
                      │  │  state)      │
                      │  └──────────────┘
                      └────────────────────────────┘
```

### Data Ownership Split

| Data | Where It Lives | Why |
|------|---------------|-----|
| Question text, answers, explanations | Strapi → Redis cache | Static content, edited by content team |
| Domain, topic, lesson content | Strapi → Redis cache | Static content |
| Exam config (50q, 30min, pass 44) | Strapi → Redis cache | Config, rarely changes |
| Question mastery per user | PostgreSQL (content_db) | Changes on every answer |
| Session records, answers | PostgreSQL (content_db) | Transactional, per-user |
| Streaks, achievements | PostgreSQL (content_db) | Transactional, per-user |
| Study plans | PostgreSQL (content_db) | Generated per user |
| Progress aggregations | PostgreSQL (content_db) | Calculated from answers |
| Readiness score snapshots | PostgreSQL (content_db) | Daily snapshots for trends |
| Access grants (is user paid?) | Redis (shared with gateway) | Read-only, written by subscription-service |

---

## 4. Database Schema

### 4.1 Study Sessions

```sql
CREATE TABLE study_sessions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID         NOT NULL,
    keycloak_user_id    UUID         NOT NULL,
    product_code        VARCHAR(50)  NOT NULL,
    domain_code         VARCHAR(50),            -- NULL = mixed/all domains
    topic_code          VARCHAR(50),            -- NULL = all topics in domain
    session_type        VARCHAR(20)  NOT NULL,  -- PRACTICE, QUICK_QUIZ, WEAK_REVIEW
    status              VARCHAR(20)  NOT NULL DEFAULT 'IN_PROGRESS',
                                                -- IN_PROGRESS, COMPLETED, ABANDONED
    total_questions     INTEGER      NOT NULL,
    answered_count      INTEGER      NOT NULL DEFAULT 0,
    correct_count       INTEGER      NOT NULL DEFAULT 0,
    accuracy_percent    DECIMAL(5,2),
    started_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    completed_at        TIMESTAMP,
    last_activity_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    time_spent_seconds  INTEGER      NOT NULL DEFAULT 0,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_session_user ON study_sessions(tenant_id, keycloak_user_id, status);
CREATE INDEX idx_session_abandoned ON study_sessions(status, last_activity_at)
    WHERE status = 'IN_PROGRESS';
```

### 4.2 Session Answers

```sql
CREATE TABLE session_answers (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID         NOT NULL,
    session_id          UUID         NOT NULL REFERENCES study_sessions(id),
    keycloak_user_id    UUID         NOT NULL,
    strapi_question_id  VARCHAR(100) NOT NULL,  -- Strapi's question ID/UID
    question_version    INTEGER      NOT NULL DEFAULT 1,
    interaction_type    VARCHAR(30)  NOT NULL,
    is_correct          BOOLEAN      NOT NULL,
    user_answer         JSONB        NOT NULL,   -- flexible: {"selectedOption": 2} or {"tappedRegion": "A"} etc
    correct_answer      JSONB        NOT NULL,   -- stored for audit even if Strapi changes
    time_taken_ms       INTEGER      NOT NULL,
    question_order      INTEGER      NOT NULL,   -- position in session
    answered_at         TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_answer_session ON session_answers(session_id);
CREATE INDEX idx_answer_user_question ON session_answers(tenant_id, keycloak_user_id, strapi_question_id);
```

### 4.3 Question Progress (Spaced Repetition State)

```sql
CREATE TABLE question_progress (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID         NOT NULL,
    keycloak_user_id        UUID         NOT NULL,
    strapi_question_id      VARCHAR(100) NOT NULL,
    product_code            VARCHAR(50)  NOT NULL,
    domain_code             VARCHAR(50)  NOT NULL,
    topic_code              VARCHAR(50)  NOT NULL,
    mastery_level           VARCHAR(20)  NOT NULL DEFAULT 'NEW',
                                                    -- NEW, LEARNING, FAMILIAR, MASTERED
    ease_factor             DECIMAL(4,2) NOT NULL DEFAULT 2.50,
    consecutive_correct     INTEGER      NOT NULL DEFAULT 0,
    total_attempts          INTEGER      NOT NULL DEFAULT 0,
    total_correct           INTEGER      NOT NULL DEFAULT 0,
    last_answered_at        TIMESTAMP,
    next_review_at          TIMESTAMP,              -- NULL = not yet seen (NEW)
    interval_days           INTEGER      NOT NULL DEFAULT 0,
    created_at              TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_progress_user_question
        UNIQUE (tenant_id, keycloak_user_id, strapi_question_id)
);

CREATE INDEX idx_progress_review ON question_progress(
    tenant_id, keycloak_user_id, product_code, next_review_at
) WHERE mastery_level != 'NEW';

CREATE INDEX idx_progress_new ON question_progress(
    tenant_id, keycloak_user_id, product_code, domain_code
) WHERE mastery_level = 'NEW';

CREATE INDEX idx_progress_mastery ON question_progress(
    tenant_id, keycloak_user_id, product_code, mastery_level
);
```

### 4.4 Topic Progress (Aggregated)

```sql
CREATE TABLE topic_progress (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID         NOT NULL,
    keycloak_user_id    UUID         NOT NULL,
    product_code        VARCHAR(50)  NOT NULL,
    domain_code         VARCHAR(50)  NOT NULL,
    topic_code          VARCHAR(50)  NOT NULL,
    total_questions     INTEGER      NOT NULL DEFAULT 0,
    attempted_count     INTEGER      NOT NULL DEFAULT 0,
    correct_count       INTEGER      NOT NULL DEFAULT 0,
    mastered_count      INTEGER      NOT NULL DEFAULT 0,
    accuracy_percent    DECIMAL(5,2) NOT NULL DEFAULT 0,
    coverage_percent    DECIMAL(5,2) NOT NULL DEFAULT 0,
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_topic_progress
        UNIQUE (tenant_id, keycloak_user_id, product_code, topic_code)
);
```

### 4.5 Domain Progress (Aggregated)

```sql
CREATE TABLE domain_progress (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID         NOT NULL,
    keycloak_user_id    UUID         NOT NULL,
    product_code        VARCHAR(50)  NOT NULL,
    domain_code         VARCHAR(50)  NOT NULL,
    total_questions     INTEGER      NOT NULL DEFAULT 0,
    attempted_count     INTEGER      NOT NULL DEFAULT 0,
    correct_count       INTEGER      NOT NULL DEFAULT 0,
    mastered_count      INTEGER      NOT NULL DEFAULT 0,
    accuracy_percent    DECIMAL(5,2) NOT NULL DEFAULT 0,
    coverage_percent    DECIMAL(5,2) NOT NULL DEFAULT 0,
    strength            VARCHAR(10)  NOT NULL DEFAULT 'UNKNOWN',
                                                -- WEAK, MODERATE, STRONG, MASTERED, UNKNOWN
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_domain_progress
        UNIQUE (tenant_id, keycloak_user_id, product_code, domain_code)
);
```

### 4.6 Streaks

```sql
CREATE TABLE streaks (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID     NOT NULL,
    keycloak_user_id        UUID     NOT NULL,
    product_code            VARCHAR(50) NOT NULL,
    current_streak          INTEGER  NOT NULL DEFAULT 0,
    longest_streak          INTEGER  NOT NULL DEFAULT 0,
    last_study_date         DATE,
    freeze_slots_available  INTEGER  NOT NULL DEFAULT 0,
    freeze_slots_used       INTEGER  NOT NULL DEFAULT 0,
    total_study_days        INTEGER  NOT NULL DEFAULT 0,
    updated_at              TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_streak_user_product
        UNIQUE (tenant_id, keycloak_user_id, product_code)
);
```

### 4.7 Earned Achievements

```sql
CREATE TABLE earned_achievements (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID         NOT NULL,
    keycloak_user_id    UUID         NOT NULL,
    achievement_code    VARCHAR(50)  NOT NULL,
    earned_at           TIMESTAMP    NOT NULL DEFAULT NOW(),
    trigger_value       INTEGER,                -- the actual value that triggered it
    notified            BOOLEAN      NOT NULL DEFAULT FALSE,

    CONSTRAINT uq_achievement_user
        UNIQUE (tenant_id, keycloak_user_id, achievement_code)
);
```

### 4.8 Exam Attempts

```sql
CREATE TABLE exam_attempts (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID         NOT NULL,
    keycloak_user_id    UUID         NOT NULL,
    product_code        VARCHAR(50)  NOT NULL,
    exam_type           VARCHAR(20)  NOT NULL,  -- MOCK_EXAM, PRACTICE_EXAM
    total_questions     INTEGER      NOT NULL,
    correct_count       INTEGER      NOT NULL,
    pass_score          INTEGER      NOT NULL,  -- 44 for CBR
    passed              BOOLEAN      NOT NULL,
    score_percent       DECIMAL(5,2) NOT NULL,
    time_taken_seconds  INTEGER      NOT NULL,
    time_limit_seconds  INTEGER      NOT NULL,
    domain_breakdown    JSONB        NOT NULL,  -- per-domain correct/total
    started_at          TIMESTAMP    NOT NULL,
    completed_at        TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT check_exam_score CHECK (correct_count >= 0 AND correct_count <= total_questions)
);

CREATE INDEX idx_exam_user ON exam_attempts(tenant_id, keycloak_user_id, product_code);
CREATE INDEX idx_exam_passed ON exam_attempts(tenant_id, keycloak_user_id, passed);

-- domain_breakdown example:
-- {
--   "verkeersborden": {"correct": 8, "total": 9},
--   "voorrang": {"correct": 7, "total": 8},
--   "verkeersregels": {"correct": 9, "total": 10},
--   ...
-- }
```

### 4.9 Exam Answers

```sql
CREATE TABLE exam_answers (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID         NOT NULL,
    exam_attempt_id     UUID         NOT NULL REFERENCES exam_attempts(id),
    strapi_question_id  VARCHAR(100) NOT NULL,
    question_version    INTEGER      NOT NULL DEFAULT 1,
    domain_code         VARCHAR(50)  NOT NULL,
    is_correct          BOOLEAN      NOT NULL,
    user_answer         JSONB        NOT NULL,
    correct_answer      JSONB        NOT NULL,
    time_taken_ms       INTEGER,
    question_order      INTEGER      NOT NULL,
    answered_at         TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_exam_answer_attempt ON exam_answers(exam_attempt_id);
```

### 4.10 Readiness Snapshots

```sql
CREATE TABLE readiness_snapshots (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID         NOT NULL,
    keycloak_user_id    UUID         NOT NULL,
    product_code        VARCHAR(50)  NOT NULL,
    snapshot_date       DATE         NOT NULL,
    readiness_score     DECIMAL(5,2) NOT NULL,  -- 0-100
    coverage_score      DECIMAL(5,2) NOT NULL,
    accuracy_score      DECIMAL(5,2) NOT NULL,
    exam_score          DECIMAL(5,2) NOT NULL,
    questions_attempted INTEGER      NOT NULL,
    total_questions     INTEGER      NOT NULL,
    best_exam_score     INTEGER,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_readiness_daily
        UNIQUE (tenant_id, keycloak_user_id, product_code, snapshot_date)
);
```

### 4.11 Question Difficulty (Crowd-Sourced Calibration)

```sql
CREATE TABLE question_difficulty (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID         NOT NULL,
    strapi_question_id  VARCHAR(100) NOT NULL,
    product_code        VARCHAR(50)  NOT NULL,
    times_answered      INTEGER      NOT NULL DEFAULT 0,
    times_correct       INTEGER      NOT NULL DEFAULT 0,
    difficulty_score    DECIMAL(5,2),            -- 0=very easy, 100=very hard
    calibrated_at       TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_difficulty
        UNIQUE (tenant_id, strapi_question_id)
);
```

### 4.12 Study Plans

```sql
CREATE TABLE study_plans (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID         NOT NULL,
    keycloak_user_id    UUID         NOT NULL,
    product_code        VARCHAR(50)  NOT NULL,
    exam_date           DATE,                    -- target CBR exam date (optional)
    total_days          INTEGER      NOT NULL,
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
                                                -- ACTIVE, COMPLETED, ABANDONED
    daily_question_target INTEGER    NOT NULL DEFAULT 20,
    focus_domains       JSONB,                   -- ["voorrang", "gevaarherkenning"] or null = all
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_active_plan
        UNIQUE (tenant_id, keycloak_user_id, product_code)
);

CREATE TABLE study_plan_days (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID         NOT NULL,
    plan_id             UUID         NOT NULL REFERENCES study_plans(id),
    day_number          INTEGER      NOT NULL,  -- 1, 2, 3...
    plan_date           DATE         NOT NULL,
    domain_code         VARCHAR(50)  NOT NULL,   -- focus domain for this day
    question_target     INTEGER      NOT NULL DEFAULT 20,
    questions_completed INTEGER      NOT NULL DEFAULT 0,
    include_exam        BOOLEAN      NOT NULL DEFAULT FALSE, -- do a mock exam today?
    status              VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
                                                -- PENDING, IN_PROGRESS, COMPLETED, SKIPPED
    completed_at        TIMESTAMP,

    CONSTRAINT uq_plan_day UNIQUE (plan_id, day_number)
);
```

### 4.13 Row-Level Security

```sql
ALTER TABLE study_sessions       ENABLE ROW LEVEL SECURITY;
ALTER TABLE session_answers      ENABLE ROW LEVEL SECURITY;
ALTER TABLE question_progress    ENABLE ROW LEVEL SECURITY;
ALTER TABLE topic_progress       ENABLE ROW LEVEL SECURITY;
ALTER TABLE domain_progress      ENABLE ROW LEVEL SECURITY;
ALTER TABLE streaks              ENABLE ROW LEVEL SECURITY;
ALTER TABLE earned_achievements  ENABLE ROW LEVEL SECURITY;
ALTER TABLE exam_attempts        ENABLE ROW LEVEL SECURITY;
ALTER TABLE exam_answers         ENABLE ROW LEVEL SECURITY;
ALTER TABLE readiness_snapshots  ENABLE ROW LEVEL SECURITY;
ALTER TABLE question_difficulty  ENABLE ROW LEVEL SECURITY;
ALTER TABLE study_plans          ENABLE ROW LEVEL SECURITY;
ALTER TABLE study_plan_days      ENABLE ROW LEVEL SECURITY;

-- Same pattern for all:
CREATE POLICY tenant_isolation_sessions ON study_sessions
    USING (tenant_id = current_setting('app.tenant_id')::UUID);
-- ... repeat for all tables
```

---

## 5. Spaced Repetition Algorithm (Modified SM-2)

### Why Modified, Not Pure SM-2

Standard SM-2 is designed for lifetime learning (language vocabulary over years).
CBR students study for 4-6 weeks. Key differences:

1. **Shorter intervals**: max interval is 14 days, not months
2. **Coverage guarantee**: students MUST see all questions at least once
3. **Binary grading**: correct/incorrect, not 0-5 quality scale
4. **4 mastery levels**: simpler than continuous ease factor

### Mastery Levels

| Level | Meaning | Consecutive Correct Required | Review Interval |
|-------|---------|------------------------------|-----------------|
| `NEW` | Never seen | — | Immediately available |
| `LEARNING` | Seen but not retained | 0-1 correct in a row | 1 day |
| `FAMILIAR` | Recalled with effort | 2+ correct in a row | 3 days |
| `MASTERED` | Recalled easily | 4+ correct in a row | 7-14 days |

### State Transitions

```
NEW ──(first answer correct)──▶ LEARNING (interval: 1 day)
NEW ──(first answer wrong)────▶ LEARNING (interval: 0, review same session)

LEARNING ──(2 correct in a row)──▶ FAMILIAR (interval: 3 days)
LEARNING ──(wrong answer)────────▶ LEARNING (reset consecutive, interval: 1 day)

FAMILIAR ──(2 more correct, total 4)──▶ MASTERED (interval: 7 days)
FAMILIAR ──(wrong answer)─────────────▶ LEARNING (drop level, interval: 1 day)

MASTERED ──(correct, with randomized interval)──▶ MASTERED (interval: 7-14 days)
MASTERED ──(wrong answer)──────────────────────▶ FAMILIAR (drop level, interval: 3 days)
```

### Ease Factor Adjustment

```java
// After each answer
if (isCorrect) {
    easeFactor = Math.max(1.3, easeFactor + 0.1);
} else {
    easeFactor = Math.max(1.3, easeFactor - 0.2);
}

// Interval calculation
nextInterval = (int) Math.ceil(currentInterval * easeFactor);
nextInterval = Math.min(nextInterval, 14); // cap at 14 days for exam prep
nextReviewAt = now + nextInterval days;
```

### Question Selection Algorithm (per session)

```java
/**
 * Select N questions for a practice session.
 * Priority order ensures due reviews and weak spots are always addressed first.
 */
public List<String> selectQuestions(UUID userId, String productCode,
                                     String domainCode, int count) {
    List<String> selected = new ArrayList<>();

    // Priority 1: Due reviews (nextReviewAt < now)
    // These are questions the student has seen before but are about to be forgotten
    List<QuestionProgress> dueReviews = progressRepo
        .findDueReviews(userId, productCode, domainCode, Instant.now());
    // Sort by most overdue first
    dueReviews.sort(Comparator.comparing(QuestionProgress::getNextReviewAt));
    for (QuestionProgress qp : dueReviews) {
        if (selected.size() >= count) break;
        selected.add(qp.getStrapiQuestionId());
    }

    // Priority 2: Weak questions (LEARNING with consecutiveCorrect < 2)
    if (selected.size() < count) {
        List<QuestionProgress> weak = progressRepo
            .findWeak(userId, productCode, domainCode, 2);
        for (QuestionProgress qp : weak) {
            if (selected.size() >= count) break;
            if (!selected.contains(qp.getStrapiQuestionId())) {
                selected.add(qp.getStrapiQuestionId());
            }
        }
    }

    // Priority 3: New (unseen) questions — shuffled randomly
    // Critical: students must see ALL questions before the exam
    if (selected.size() < count) {
        List<String> allQuestionIds = strapiContentCache
            .getQuestionIds(productCode, domainCode);
        Set<String> seenIds = progressRepo
            .findSeenQuestionIds(userId, productCode, domainCode);
        List<String> newIds = allQuestionIds.stream()
            .filter(id -> !seenIds.contains(id) && !selected.contains(id))
            .collect(Collectors.toList());
        Collections.shuffle(newIds);
        for (String id : newIds) {
            if (selected.size() >= count) break;
            selected.add(id);
        }
    }

    // Priority 4: Fill with FAMILIAR closest to review date
    if (selected.size() < count) {
        List<QuestionProgress> familiar = progressRepo
            .findFamiliarSorted(userId, productCode, domainCode);
        for (QuestionProgress qp : familiar) {
            if (selected.size() >= count) break;
            if (!selected.contains(qp.getStrapiQuestionId())) {
                selected.add(qp.getStrapiQuestionId());
            }
        }
    }

    return selected;
}
```

---

## 6. Readiness Score Calculation

```java
public record ReadinessScore(
    double readinessScore,      // 0-100 composite
    double coverageScore,       // 0-100
    double accuracyScore,       // 0-100
    double examScore,           // 0-100
    String readinessLabel,      // NOT_READY, GETTING_THERE, ALMOST_READY, READY
    List<DomainStrength> domainStrengths
) {}

public ReadinessScore calculate(UUID userId, String productCode) {
    int totalQuestions = strapiContentCache.getQuestionCount(productCode);
    int attempted = progressRepo.countAttempted(userId, productCode);
    int correct = progressRepo.countCorrect(userId, productCode);
    Integer bestExamScore = examRepo.findBestScore(userId, productCode);
    int passScore = strapiContentCache.getExamConfig(productCode).passScore();

    // Coverage: what % of all questions has the student attempted?
    double coverage = totalQuestions > 0
        ? (double) attempted / totalQuestions * 100 : 0;

    // Accuracy: what % of answered questions were correct?
    double accuracy = attempted > 0
        ? (double) correct / attempted * 100 : 0;

    // Exam: best mock exam score relative to pass threshold
    double exam = 0;
    if (bestExamScore != null && passScore > 0) {
        exam = Math.min(100, (double) bestExamScore / passScore * 100);
    }

    // Weighted composite
    double readiness = (0.40 * coverage) + (0.35 * accuracy) + (0.25 * exam);

    String label;
    if (readiness < 30) label = "NOT_READY";
    else if (readiness < 60) label = "GETTING_THERE";
    else if (readiness < 80) label = "ALMOST_READY";
    else label = "READY";

    // Per-domain breakdown
    List<DomainStrength> domains = domainProgressRepo
        .findAll(userId, productCode).stream()
        .map(dp -> new DomainStrength(
            dp.getDomainCode(),
            dp.getAccuracyPercent(),
            dp.getCoveragePercent(),
            dp.getStrength()  // WEAK, MODERATE, STRONG, MASTERED
        ))
        .toList();

    return new ReadinessScore(readiness, coverage, accuracy, exam, label, domains);
}
```

### Domain Strength Thresholds

| Accuracy | Coverage | Strength |
|----------|----------|----------|
| < 50% | any | WEAK |
| 50-70% | < 50% | MODERATE |
| 50-70% | ≥ 50% | MODERATE |
| 70-85% | ≥ 60% | STRONG |
| ≥ 85% | ≥ 80% | MASTERED |

---

## 7. Streak Engine

### Rules
- Study at least 1 question in a day to maintain streak
- Streak resets at 00:00 UTC if no activity yesterday
- Freeze slot: skip 1 day without breaking streak
- Freeze slots earned: 1 at 7-day streak, 2 more at 14-day, 3 more at 30-day

### Streak Check (runs on every answer submission)

```java
public StreakResult updateStreak(UUID userId, String productCode) {
    Streak streak = streakRepo.findByUser(userId, productCode)
        .orElseGet(() -> createNewStreak(userId, productCode));

    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    LocalDate lastStudy = streak.getLastStudyDate();

    if (lastStudy == null || lastStudy.isBefore(today)) {
        // New study day
        if (lastStudy != null && lastStudy.equals(today.minusDays(1))) {
            // Consecutive day — extend streak
            streak.setCurrentStreak(streak.getCurrentStreak() + 1);
        } else if (lastStudy != null && lastStudy.equals(today.minusDays(2))
                   && streak.getFreezeSlots() > 0) {
            // Missed 1 day but has freeze slot
            streak.setFreezeSlots(streak.getFreezeSlots() - 1);
            streak.setFreezeSlotsUsed(streak.getFreezeSlotsUsed() + 1);
            streak.setCurrentStreak(streak.getCurrentStreak() + 1);
        } else if (lastStudy != null && !lastStudy.equals(today)) {
            // Streak broken
            streak.setCurrentStreak(1);
        }

        streak.setLastStudyDate(today);
        streak.setTotalStudyDays(streak.getTotalStudyDays() + 1);

        if (streak.getCurrentStreak() > streak.getLongestStreak()) {
            streak.setLongestStreak(streak.getCurrentStreak());
        }

        // Award freeze slots at milestones
        awardFreezeSlots(streak);
    }
    // else: already studied today, no change

    return streakRepo.save(streak);
}
```

---

## 8. Study Plan Generator

### Algorithm

```java
public StudyPlan generate(UUID userId, String productCode, LocalDate examDate,
                           int dailyQuestionTarget) {
    ReadinessScore readiness = readinessService.calculate(userId, productCode);
    List<DomainStrength> domains = readiness.domainStrengths();

    LocalDate startDate = LocalDate.now(ZoneOffset.UTC);
    int totalDays = (int) ChronoUnit.DAYS.between(startDate, examDate);
    if (totalDays < 3) totalDays = 7; // minimum 7-day plan
    if (totalDays > 90) totalDays = 90; // cap at 90 days

    // Sort domains by weakness (WEAK first, MASTERED last)
    domains.sort(Comparator.comparing(d -> d.strength().ordinal()));

    // Allocate days: weak domains get 3x more days than strong
    Map<String, Integer> domainDayAllocation = allocateDays(domains, totalDays);

    // Generate daily tasks
    List<StudyPlanDay> days = new ArrayList<>();
    int dayNumber = 1;
    LocalDate planDate = startDate;

    for (Map.Entry<String, Integer> entry : domainDayAllocation.entrySet()) {
        String domainCode = entry.getKey();
        int allocatedDays = entry.getValue();
        for (int i = 0; i < allocatedDays; i++) {
            boolean includeExam = (dayNumber % 7 == 0); // mock exam every 7 days
            days.add(StudyPlanDay.builder()
                .dayNumber(dayNumber)
                .planDate(planDate)
                .domainCode(domainCode)
                .questionTarget(dailyQuestionTarget)
                .includeExam(includeExam)
                .build());
            dayNumber++;
            planDate = planDate.plusDays(1);
        }
    }

    // Last 3 days before exam: mixed review across all domains + daily mock exams
    for (int i = 0; i < 3 && dayNumber <= totalDays; i++) {
        days.add(StudyPlanDay.builder()
            .dayNumber(dayNumber)
            .planDate(planDate)
            .domainCode("ALL")
            .questionTarget(dailyQuestionTarget)
            .includeExam(true)
            .build());
        dayNumber++;
        planDate = planDate.plusDays(1);
    }

    return StudyPlan.builder()
        .userId(userId)
        .productCode(productCode)
        .examDate(examDate)
        .totalDays(totalDays)
        .dailyQuestionTarget(dailyQuestionTarget)
        .focusDomains(domains.stream()
            .filter(d -> d.strength() == Strength.WEAK || d.strength() == Strength.MODERATE)
            .map(DomainStrength::domainCode)
            .toList())
        .days(days)
        .build();
}

private Map<String, Integer> allocateDays(List<DomainStrength> domains, int totalDays) {
    // Reserve last 3 days for mixed review
    int available = totalDays - 3;
    Map<String, Integer> allocation = new LinkedHashMap<>();

    // Weight: WEAK=3, MODERATE=2, STRONG=1, MASTERED=0.5
    double totalWeight = domains.stream().mapToDouble(d -> switch (d.strength()) {
        case WEAK -> 3.0;
        case MODERATE -> 2.0;
        case STRONG -> 1.0;
        case MASTERED -> 0.5;
        default -> 1.0;
    }).sum();

    for (DomainStrength d : domains) {
        double weight = switch (d.strength()) {
            case WEAK -> 3.0;
            case MODERATE -> 2.0;
            case STRONG -> 1.0;
            case MASTERED -> 0.5;
            default -> 1.0;
        };
        int days = Math.max(1, (int) Math.round(available * weight / totalWeight));
        allocation.put(d.domainCode(), days);
    }

    return allocation;
}
```

---

## 9. Achievement Engine

### Achievement Triggers (checked async after answer submission)

| Code | Trigger | Value | Name |
|------|---------|-------|------|
| `first_question` | questions_answered | 1 | First steps |
| `questions_10` | questions_answered | 10 | Getting started |
| `questions_50` | questions_answered | 50 | Studious |
| `questions_100` | questions_answered | 100 | Dedicated |
| `questions_500` | questions_answered | 500 | Scholar |
| `questions_1000` | questions_answered | 1000 | Master |
| `streak_3` | study_days_streak | 3 | On a roll |
| `streak_7` | study_days_streak | 7 | Week warrior |
| `streak_14` | study_days_streak | 14 | Fortnight fighter |
| `streak_30` | study_days_streak | 30 | Monthly master |
| `correct_5` | correct_streak | 5 | Hot streak |
| `correct_10` | correct_streak | 10 | Unstoppable |
| `correct_25` | correct_streak | 25 | On fire |
| `first_exam` | exams_passed | 1 | Exam ready |
| `exams_5` | exams_passed | 5 | Exam veteran |
| `perfect_exam` | perfect_exam | 1 | Perfectionist |
| `domain_1` | domain_mastered | 1 | Domain expert |
| `domain_3` | domain_mastered | 3 | Multi-domain |
| `all_domains` | domain_mastered | 6 | Complete master |
| `readiness_50` | readiness_score | 50 | Halfway there |
| `readiness_75` | readiness_score | 75 | Almost ready |
| `readiness_90` | readiness_score | 90 | Exam confident |
| `speed_demon` | fast_correct | 1 | Speed demon |

```java
// Check after every answer — runs async via @Async
public List<EarnedAchievement> checkAchievements(UUID userId, String productCode) {
    List<EarnedAchievement> newlyEarned = new ArrayList<>();
    Set<String> alreadyEarned = achievementRepo.findEarnedCodes(userId);

    // Load achievement definitions from Strapi cache
    List<AchievementDef> defs = strapiContentCache.getAchievements();

    for (AchievementDef def : defs) {
        if (alreadyEarned.contains(def.code())) continue;

        int currentValue = switch (def.triggerType()) {
            case "questions_answered" -> progressRepo.countAttempted(userId, productCode);
            case "correct_streak" -> progressRepo.maxConsecutiveCorrect(userId, productCode);
            case "study_days_streak" -> streakRepo.getCurrentStreak(userId, productCode);
            case "exams_passed" -> examRepo.countPassed(userId, productCode);
            case "perfect_exam" -> examRepo.countPerfect(userId, productCode);
            case "domain_mastered" -> domainProgressRepo.countMastered(userId, productCode);
            case "readiness_score" -> (int) readinessService.calculate(userId, productCode).readinessScore();
            default -> 0;
        };

        if (currentValue >= def.triggerValue()) {
            EarnedAchievement earned = achievementRepo.save(new EarnedAchievement(
                userId, def.code(), Instant.now(), currentValue
            ));
            newlyEarned.add(earned);
        }
    }

    // Publish events for notification-service
    for (EarnedAchievement earned : newlyEarned) {
        kafkaProducer.publish(new AchievementEarnedEvent(
            userId, earned.getAchievementCode(), earned.getEarnedAt()
        ));
    }

    return newlyEarned;
}
```

---

## 10. Kafka Events

### Published

```
passtheo.question.answered     → analytics (future), difficulty calibration
passtheo.session.completed     → analytics (future)
passtheo.exam.completed        → notification-service (result email)
passtheo.streak.updated        → notification-service (milestone congrats)
passtheo.achievement.earned    → notification-service (badge popup)
passtheo.readiness.changed     → notification-service (threshold crossed)
```

### Consumed

```
passtheo.subscription.activated  → warm Redis cache with entitlements
passtheo.subscription.expired    → restrict access for user
passtheo.user.created            → no-op (user starts with 0 progress)
passtheo.user.deleted            → delete all learning data (GDPR)
passtheo.tenant.terminated       → delete all tenant learning data
```

---

## 11. Redis Cache Strategy

### Content Cache (from Strapi)

| Key Pattern | TTL | Content |
|-------------|-----|---------|
| `strapi:product:{code}` | 1 hour | Product with domains |
| `strapi:domains:{productCode}` | 1 hour | All domains for product |
| `strapi:topics:{domainCode}` | 1 hour | Topics in domain |
| `strapi:questions:{topicCode}` | 1 hour | Question IDs in topic |
| `strapi:question:{id}` | 1 hour | Full question with answers |
| `strapi:examconfig:{productCode}` | 1 hour | Exam rules |
| `strapi:achievements` | 1 hour | All achievement definitions |
| `strapi:roadsigns:{countryCode}` | 1 hour | Road sign reference |

### Access Cache (shared with gateway, written by subscription-service)

| Key Pattern | TTL | Read By |
|-------------|-----|---------|
| `access:{tenantId}:{userId}` | 5 min | content-service + gateway |

### Invalidation

- Strapi content: TTL-based only (1 hour). No webhook from Strapi.
- Manual cache bust: `DELETE /internal/cache/flush` endpoint for admin use.

---

## 12. Content Gateway (Strapi Integration)

### StrapiClient

```java
@Component
public class StrapiClient {
    private final WebClient webClient;
    private final RedisTemplate<String, String> redis;

    // Fetches with Redis-aside caching
    public List<QuestionDto> getQuestions(String topicCode, String locale) {
        String cacheKey = "strapi:questions:" + topicCode + ":" + locale;
        String cached = redis.opsForValue().get(cacheKey);
        if (cached != null) return deserialize(cached);

        // Cache miss — fetch from Strapi REST API
        String url = "/api/questions?filters[topic][code][$eq]=" + topicCode
            + "&locale=" + locale
            + "&populate=answerOptions,explanation,imageRegions,dragTargets,image,video";

        List<QuestionDto> questions = webClient.get()
            .uri(url)
            .retrieve()
            .bodyToMono(StrapiResponse.class)
            .map(StrapiResponse::getData)
            .block();

        redis.opsForValue().set(cacheKey, serialize(questions), Duration.ofHours(1));
        return questions;
    }
}
```

### Question DTO from Strapi

```java
public record StrapiQuestionDto(
    String id,
    String questionText,
    String interactionType,  // multiple_choice, yes_no, fill_in_number, etc
    String difficulty,
    String imageUrl,
    String videoUrl,
    Integer correctNumber,
    Integer correctNumberTolerance,
    int version,
    List<AnswerOptionDto> answerOptions,
    ExplanationDto explanation,
    List<ImageRegionDto> imageRegions,
    List<DragTargetDto> dragTargets,
    String domainCode,
    String topicCode
) {}
```

---

## 13. Entitlement Check (via shared Redis)

```java
@Component
public class EntitlementChecker {
    private final RedisTemplate<String, String> redis;
    private final ObjectMapper objectMapper;

    /**
     * Read the access grant from Redis.
     * This key is written by subscription-service and read by gateway + this service.
     */
    public AccessGrant getAccess(UUID tenantId, UUID userId) {
        String key = "access:" + tenantId + ":" + userId;
        String cached = redis.opsForValue().get(key);
        if (cached == null) {
            // No cache = assume FREE tier (defensive)
            return AccessGrant.free();
        }
        return objectMapper.readValue(cached, AccessGrant.class);
    }

    public boolean canStartSession(UUID tenantId, UUID userId, String domainCode) {
        AccessGrant grant = getAccess(tenantId, userId);
        if (grant.isPaid()) return true;

        // Free user: check if domain is in free preview
        // (first 2 domains have isFreePreview=true in Strapi)
        // and daily limit not exceeded (checked by subscription-service)
        return isDomainFreePreview(domainCode);
    }
}
```

---

## 14. Package Structure

```
com.passtheo.content
├── ContentServiceApplication.java
├── config/
│   ├── StrapiClientConfig.java
│   ├── RedisConfig.java
│   └── KafkaConfig.java
├── domain/
│   ├── entity/
│   │   ├── StudySession.java
│   │   ├── SessionAnswer.java
│   │   ├── QuestionProgress.java
│   │   ├── TopicProgress.java
│   │   ├── DomainProgress.java
│   │   ├── Streak.java
│   │   ├── EarnedAchievement.java
│   │   ├── ExamAttempt.java
│   │   ├── ExamAnswer.java
│   │   ├── ReadinessSnapshot.java
│   │   ├── QuestionDifficulty.java
│   │   ├── StudyPlan.java
│   │   └── StudyPlanDay.java
│   ├── enums/
│   │   ├── MasteryLevel.java
│   │   ├── SessionType.java
│   │   ├── SessionStatus.java
│   │   ├── DomainStrength.java
│   │   ├── ReadinessLabel.java
│   │   └── InteractionType.java
│   └── valueobject/
│       ├── ReadinessScore.java
│       ├── StreakResult.java
│       └── AccessGrant.java
├── repository/
│   ├── StudySessionRepository.java
│   ├── SessionAnswerRepository.java
│   ├── QuestionProgressRepository.java
│   ├── TopicProgressRepository.java
│   ├── DomainProgressRepository.java
│   ├── StreakRepository.java
│   ├── EarnedAchievementRepository.java
│   ├── ExamAttemptRepository.java
│   ├── ExamAnswerRepository.java
│   ├── ReadinessSnapshotRepository.java
│   ├── QuestionDifficultyRepository.java
│   ├── StudyPlanRepository.java
│   └── StudyPlanDayRepository.java
├── service/
│   ├── PracticeSessionService.java
│   ├── QuestionSelectionService.java      -- spaced repetition algorithm
│   ├── AnswerProcessingService.java       -- grade + update mastery
│   ├── MockExamService.java
│   ├── ProgressService.java               -- topic/domain aggregation
│   ├── ReadinessService.java              -- readiness score calculator
│   ├── StreakService.java
│   ├── AchievementService.java
│   ├── StudyPlanService.java
│   ├── QuestionDifficultyService.java     -- crowd-sourced calibration
│   └── EntitlementChecker.java            -- reads Redis access cache
├── integration/
│   ├── strapi/
│   │   ├── StrapiClient.java
│   │   ├── StrapiContentCache.java        -- Redis-backed cache
│   │   └── dto/ (Strapi response DTOs)
│   └── redis/
│       └── AccessCacheReader.java
├── kafka/
│   ├── consumer/
│   │   ├── SubscriptionEventConsumer.java
│   │   ├── UserEventConsumer.java
│   │   └── TenantEventConsumer.java
│   └── producer/
│       └── ContentEventProducer.java
├── controller/
│   ├── PracticeController.java
│   ├── ExamController.java
│   ├── ProgressController.java
│   ├── StreakController.java
│   ├── AchievementController.java
│   ├── StudyPlanController.java
│   ├── ContentController.java             -- domains, topics, lessons, road signs
│   └── InternalContentController.java
├── dto/ (request + response DTOs)
└── scheduler/
    ├── ReadinessSnapshotJob.java           -- daily at 01:00 UTC
    ├── DifficultyCalibrationJob.java       -- nightly recalculation
    └── AbandonedSessionCleanup.java        -- mark stale sessions as ABANDONED
```

---

## 15. Build Order Within Service

```
Step 1:  Flyway migrations (13 tables + RLS + indexes)
Step 2:  Domain entities + repositories
Step 3:  Enums (MasteryLevel, InteractionType, SessionType, etc.)
Step 4:  StrapiClient + StrapiContentCache (Redis-backed)
Step 5:  EntitlementChecker (reads shared Redis)
Step 6:  QuestionSelectionService (spaced repetition algorithm — unit test heavily)
Step 7:  AnswerProcessingService (grade answer + update mastery)
Step 8:  PracticeSessionService (create session, serve questions, submit answers)
Step 9:  ProgressService (topic/domain aggregation)
Step 10: ReadinessService (readiness score — unit test heavily)
Step 11: MockExamService (exam generation, timing, scoring)
Step 12: StreakService (streak tracking + freeze slots)
Step 13: AchievementService (trigger checking, async)
Step 14: StudyPlanService (plan generation from weak domains)
Step 15: QuestionDifficultyService (crowd-sourced calibration)
Step 16: Kafka consumers + producers
Step 17: All controllers
Step 18: Scheduled jobs (readiness snapshots, difficulty calibration, abandoned cleanup)
Step 19: Integration tests with Testcontainers
Step 20: Karate acceptance tests GREEN
```

---

*Document version: 1.0*
*Last updated: March 2026*
*Service: passtheo-content-service (port 8087)*
