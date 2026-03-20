# Windsurf Prompt — Implement PassTheo passtheo-content-service

You are implementing the `passtheo-content-service` for PassTheo, a multi-tenant SaaS platform for exam preparation. This is the **core product service** — everything else exists to support it. Spring Boot 4 + Java 21.

## Context

Already built and running:
- `passtheo-shared-lib` (shared-core, shared-security, shared-events, shared-testing)
- `tenant-service` (port 8081)
- `user-service` (port 8082)
- `subscription-service` (port 8083) — manages plans, entitlements, writes Redis access cache

Now implement `passtheo-content-service` (port 8087).

**Read all specification documents completely before writing any code:**

1. **`passtheo-content-service-spec.md`** — Full architecture: 6 engines, 13 database tables, spaced repetition algorithm, readiness score formula, streak logic, achievement triggers, study plan generator, Strapi integration, package structure, build order.

2. **`passtheo-content-service-openapi.yaml`** — OpenAPI 3.1 contract: ~25 endpoints covering content hierarchy, practice sessions, mock exams, progress, streaks, achievements, and study plans.

3. **`strapi-schema-v2.md`** — Strapi CMS content schema: 16 content types with the corrected hierarchy Country → ProductType → Product → Domain → Topic → Question.

4. **`karate-tests/`** — Karate acceptance tests (RED phase). Your implementation must make them GREEN.

## Content Hierarchy — CRITICAL

```
Country (NL) → ProductType (CBR) → Product (Auto B) → Domain → Topic → Question
```

- Language is Strapi i18n, NOT a hierarchy level
- Content API routes are hierarchical: `/api/content/{countryCode}/{productTypeCode}/{productCode}/domains`
- Practice/exam/progress APIs use `productCode` as a body/query parameter
- ALL database tables scope to `product_code` (not product_type_code)
- `examLocale` and `uiLocale` can differ (Turkish questions, English UI)

## Implementation Rules

### Shared Library
- Every JPA entity extends `BaseEntity` from shared-core
- Every table has `tenant_id` + RLS via `TenantAwareDataSource`
- Controllers read `X-Tenant-ID` and `X-Keycloak-User-ID` from gateway headers via `SecurityUtils`
- All responses wrapped in `ApiResponse<T>`, errors in RFC 7807 Problem Detail

### Strapi Integration
- `StrapiClient` uses Spring WebClient to call Strapi REST API
- `StrapiContentCache` wraps all Strapi calls with Redis-aside caching (1 hour TTL)
- Cache key pattern: `strapi:{countryCode}:{productTypeCode}:{productCode}:{entity}:{code}:{locale}`
- Questions include: answerOptions, explanation, imageRegions, dragTargets, image, video (Strapi populate)
- Never write to Strapi. Read-only.
- If Redis cache misses, fetch from Strapi, cache, return. If Strapi is down and cache exists, serve stale cache.

### Entitlement (Access Control)
- Read from shared Redis cache: key `access:{tenantId}:{keycloakUserId}`
- This key is written by subscription-service. This service only reads it.
- Free users: can only access domains with `isFreePreview=true`, limited daily questions and weekly exams
- Paid users: all domains, unlimited
- Before starting a practice session: check domain access + call subscription-service internal API to increment usage
- Before starting a mock exam: check paid status + call subscription-service to check weekly exam limit

### Spaced Repetition (Modified SM-2)
This is the most important algorithm. Implement exactly as specified:
- 4 mastery levels: NEW → LEARNING → FAMILIAR → MASTERED
- Binary grading (correct/incorrect), not 0-5 scale
- Max interval: 14 days (not months — this is exam prep, not lifetime learning)
- Coverage guarantee: students must see ALL questions at least once
- Question selection priority: (1) due reviews, (2) weak questions, (3) new/unseen, (4) familiar reinforcement
- `QuestionSelectionService` — unit test this class with at least 15 test cases

### 6 Question Interaction Types
The service must handle answer grading for all 6 types:
- `multiple_choice`: compare selectedOptionId against correct option
- `yes_no`: compare boolean answer
- `fill_in_number`: compare number within tolerance range (±correctNumberTolerance)
- `tap_on_image`: check if tapped coordinates fall within correct ImageRegion
- `drag_checkmark`: check if all and only correct DragTargets are selected
- `drag_numbers`: check if numbers are placed in correct order on DragTargets

### Mock Exam Engine
- Load `ExamConfig` from Strapi cache (50 questions, 30 min, pass 44 for CBR Auto)
- Select questions across all domains weighted by `questionDistribution`
- Shuffle questions and answer options
- Send ALL questions at once (Flutter handles the timer)
- No feedback during exam — grade all answers on submit
- Store `ExamAttempt` + `ExamAnswer` records
- Calculate per-domain breakdown
- Return wrong answers with explanations for review

### Readiness Score
Formula: `(0.40 × coverage) + (0.35 × accuracy) + (0.25 × examScore)`
- coverageScore = questionsAttempted / totalQuestions × 100
- accuracyScore = correctAnswers / totalAnswers × 100
- examScore = min(100, bestMockExamScore / passScore × 100)
- Labels: <30 NOT_READY, 30-60 GETTING_THERE, 60-80 ALMOST_READY, ≥80 READY
- Per-domain strength: WEAK (<50% accuracy), MODERATE (50-70%), STRONG (70-85%), MASTERED (≥85% + ≥80% coverage)
- `ReadinessService` — unit test with at least 10 test cases

### Streaks
- 1 question/day to maintain streak, reset at 00:00 UTC
- Freeze slots: earned at 7-day (1 slot), 14-day (2 more), 30-day (3 more)
- Check and update on every answer submission
- `streakAtRisk` = true if last study date is today (streak breaks tomorrow if no study)

### Achievements
- 23 achievements with trigger types and values (see spec section 9)
- Check async after every answer using `@Async`
- Load definitions from Strapi cache
- Publish `achievement.earned` to Kafka
- Return newly earned achievements in answer response

### Study Plan Generator
- Analyze current domain strengths from progress data
- Allocate days weighted by weakness: WEAK=3x, MODERATE=2x, STRONG=1x, MASTERED=0.5x
- Last 3 days before exam: mixed review + daily mock exams
- Mock exam every 7 days
- Minimum 7-day plan, maximum 90-day plan
- One active plan per user per product

### Database
- Flyway migrations for all 13 tables + RLS + indexes
- Use PostgreSQL UPSERT for question_progress updates (atomic)
- `question_version` stored in session_answers and exam_answers for analytics integrity
- `user_answer` and `correct_answer` stored as JSONB (flexible per interaction type)
- `domain_breakdown` on exam_attempts stored as JSONB

### Kafka
- Publish: question.answered, session.completed, exam.completed, streak.updated, achievement.earned, readiness.changed
- Consume: subscription.activated, subscription.expired, user.deleted, tenant.terminated
- Use outbox pattern for publishing (same transaction as business operation)

### Redis
- Content cache from Strapi: 1 hour TTL
- Access cache (shared): read-only, written by subscription-service
- Cache invalidation: TTL-based for Strapi content, manual flush endpoint for admin

### Scheduled Jobs
- `ReadinessSnapshotJob`: daily at 01:00 UTC, snapshot readiness for all active users
- `DifficultyCalibrationJob`: nightly, recalculate crowd-sourced difficulty scores
- `AbandonedSessionCleanup`: mark IN_PROGRESS sessions with no activity for 24h as ABANDONED

## Build Order — implement step by step

1. Flyway migrations (13 tables + RLS + indexes)
2. Domain entities + repositories
3. Enums (MasteryLevel, InteractionType, SessionType, SessionStatus, DomainStrength, ReadinessLabel)
4. StrapiClient + StrapiContentCache (REST + Redis-aside)
5. EntitlementChecker (reads shared Redis access cache)
6. ContentController (browse hierarchy: countries → product types → products → domains → topics)
7. QuestionSelectionService (spaced repetition algorithm — UNIT TEST HEAVILY)
8. AnswerProcessingService (grade all 6 interaction types + update mastery)
9. PracticeSessionService + PracticeController (create session, answer, complete)
10. ProgressService (topic/domain aggregation)
11. ReadinessService + ProgressController (readiness score — UNIT TEST HEAVILY)
12. MockExamService + ExamController (exam generation, timing, scoring)
13. StreakService + StreakController
14. AchievementService + AchievementController
15. StudyPlanService + StudyPlanController
16. QuestionDifficultyService (crowd-sourced calibration)
17. Kafka consumers + producers (outbox pattern)
18. Scheduled jobs (readiness snapshots, difficulty calibration, abandoned cleanup)
19. Internal endpoints (GDPR delete, cache flush)
20. Integration tests with Testcontainers (PostgreSQL, Redis, Kafka)
21. Verify all Karate tests pass

## What NOT to Do

- Do NOT write to Strapi. Read-only via REST API.
- Do NOT call subscription-service synchronously for access checks. Read from shared Redis cache.
- Do NOT implement offline sync. That's Flutter's responsibility.
- Do NOT store question text in PostgreSQL. Questions live in Strapi → Redis cache only. PostgreSQL stores only the Strapi question ID and version.
- Do NOT use Temporal workflows in this service. No long-running processes. Use @Scheduled for periodic jobs.
- Do NOT hardcode exam configs (50 questions, 30 min, pass 44). Read from Strapi ExamConfig.
- Do NOT skip RLS policies. Every table must have tenant isolation.
- Do NOT implement B2B features. Schema supports it via tenant_id but no B2B API in v1.

## File References

Read before starting:
- `passtheo-content-service-spec.md` — sections 1-15
- `passtheo-content-service-openapi.yaml` — all endpoints and schemas
- `strapi-schema-v2.md` — all 16 Strapi content types
- `karate-tests/` — all acceptance test feature files

Start with step 1 (Flyway migrations). After each step, confirm what you built and ask if I want to proceed to the next step.
