# Content Service — Claude Code Guide

> Practice sessions, mock exams, progress tracking, streaks, achievements, study plans, Strapi integration.
> See root CLAUDE.md for architecture rules, Git workflow, and platform conventions.

## Identity

| Property | Value |
|----------|-------|
| Port | 8087 |
| Package | `com.passtheo.content` |
| Database | `content_db` (public schema) |
| Redis DB | 3 (Strapi content cache) |

## Commands

| Task | Command |
|------|---------|
| Build | `./gradlew clean build` |
| Test | `./gradlew test` |
| Run | `./gradlew bootRun` |

## Source Layout

```
src/main/java/com/passtheo/content/
├── controller/      # PracticeController, ExamController, ProgressController, StreakController, AchievementController, StudyPlanController, ContentBrowsingController
├── service/         # PracticeSessionService, MockExamService, ProgressService, ReadinessService, StudyPlanService, StreakService, AchievementService, QuestionDifficultyService
├── repository/      # StudySessionRepository, ExamAttemptRepository, StreakRepository, EarnedAchievementRepository, QuestionProgressRepository, DomainProgressRepository, TopicProgressRepository, ReadinessSnapshotRepository
├── entity/          # StudySession, SessionAnswer, ExamAttempt, ExamAnswer, StudyPlan, Streak, EarnedAchievement, QuestionProgress, DomainProgress, TopicProgress, ReadinessSnapshot
├── client/          # StrapiApiClient, StrapiApiClientConfig, SubscriptionServiceClient
├── kafka/           # ContentEventProducer, OutboxPoller, UserEventConsumer, TenantEventConsumer, SubscriptionEventConsumer
├── scheduler/       # StreakExpiryScheduler, ReadinessSnapshotScheduler, StudyPlanResetScheduler
├── mapper/
├── dto/
└── config/
```

## Strapi Integration

- `StrapiApiClient` fetches questions, lessons, exam configs, achievement definitions
- All responses cached in Redis DB 3 — TTL: 1h for questions, 24h for static content
- Circuit breaker wraps all Strapi calls — on open, serve from Redis cache or return 503
- **Never cache user-specific data** in the Strapi cache layer

## Domain Rules

- **Readiness score:** rolling 7-day weighted average of correct answer rates per domain
- **Streak:** incremented once per UTC calendar day when any study activity occurs
- **Free tier limits:** checked against subscription-service — call `SubscriptionServiceClient.getEntitlements()`
- **Question difficulty:** Elo-like algorithm in `QuestionDifficultyService` — updates after each answer

## Kafka

**Produces** (via outbox → `passtheo.content.events`):
- `ExamCompletedEvent`, `AchievementEarnedEvent`, `StreakUpdatedEvent`, `ReadinessChangedEvent`

**Consumes:**
- `passtheo.user.events` — `UserCreatedEvent` → initialise user progress records
- `passtheo.tenant.events` — `TenantCreatedEvent` → initialise tenant content config
- `passtheo.subscription.events` — `SubscriptionActivatedEvent/ExpiredEvent` → update access tier

## Database

8 migrations in `src/main/resources/db/migration/`.
Every `V{n}__*.sql` has a companion `U{n}__*.sql` undo script.
