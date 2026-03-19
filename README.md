# PassTheo Content Service

The core learning engine for PassTheo — practice sessions, mock exams, spaced repetition, progress tracking, streaks, achievements, and study plans.

## Service Details

| Property | Value |
|----------|-------|
| Port | 8087 |
| Package | `com.passtheo.content` |
| Database | `content_db` (PostgreSQL) |
| Redis DB | 3 |

## Content Hierarchy

```
Country (NL) → ProductType (CBR) → Product (Auto B) → Domain → Topic → Question
```

## Endpoints

### Content Browsing
- `GET /api/content/countries` — List countries
- `GET /api/content/{countryCode}/product-types` — List product types
- `GET /api/content/{countryCode}/{productTypeCode}/products` — List products
- `GET /api/content/{countryCode}/{productTypeCode}/{productCode}/domains` — List domains with progress
- `GET /api/content/{countryCode}/{productTypeCode}/{productCode}/domains/{domainCode}/topics` — List topics
- `GET /api/content/{countryCode}/{productTypeCode}/{productCode}/road-signs` — Road sign reference
- `GET /api/content/{countryCode}/{productTypeCode}/{productCode}/lessons/{topicCode}` — Theory lessons

### Practice Sessions
- `POST /api/practice/sessions` — Start practice session
- `POST /api/practice/sessions/{id}/answer` — Submit answer
- `GET /api/practice/sessions/{id}` — Resume session
- `POST /api/practice/sessions/{id}/complete` — Complete session

### Mock Exams
- `POST /api/exams/mock/start` — Start mock exam (50q, 30min)
- `POST /api/exams/mock/{id}/submit` — Submit all answers
- `GET /api/exams/history` — Exam history

### Progress
- `GET /api/progress/readiness` — Readiness score
- `GET /api/progress/readiness/trend` — Daily trend chart
- `GET /api/progress/domains` — Domain progress
- `GET /api/progress/mastery` — Mastery distribution

### Streaks & Achievements
- `GET /api/streaks/me` — Current streak
- `GET /api/achievements/me` — Achievement gallery

### Study Plan
- `POST /api/study-plan` — Generate study plan
- `GET /api/study-plan` — Active plan
- `GET /api/study-plan/today` — Today's tasks

### Internal
- `DELETE /internal/content/user/{id}/delete` — GDPR delete
- `POST /internal/cache/flush` — Flush Strapi cache

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_PORT` | 8087 | Server port |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/content_db` | Database URL |
| `SPRING_DATASOURCE_USERNAME` | `content_service_app` | Database username |
| `SPRING_DATASOURCE_PASSWORD` | `password` | Database password |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka bootstrap servers |
| `REDIS_HOST` | `localhost` | Redis host |
| `REDIS_PORT` | `6379` | Redis port |
| `REDIS_DATABASE` | `3` | Redis database number |
| `STRAPI_BASE_URL` | `http://localhost:1337` | Strapi CMS URL |
| `STRAPI_API_TOKEN` | `strapi-api-token` | Strapi API token |
| `SUBSCRIPTION_SERVICE_URL` | `http://localhost:8083` | Subscription service URL |
| `CONTENT_CACHE_TTL_SECONDS` | `3600` | Strapi content cache TTL |
| `OUTBOX_POLL_INTERVAL_MS` | `5000` | Outbox poller interval |

## Local Development

```bash
# Build
./gradlew clean build

# Run
./gradlew bootRun --args='--spring.profiles.active=dev'

# Run tests
./gradlew test

# Karate acceptance tests (requires running service)
./gradlew test --tests "com.passtheo.content.KarateRunner"
```

## Architecture

- **6 Engines**: Content Gateway, Practice Session, Spaced Repetition, Mock Exam, Progress/Readiness, Streak/Achievement
- **Strapi CMS**: Read-only content via REST API + Redis-aside cache (1h TTL)
- **Spaced Repetition**: Modified SM-2 with 4 mastery levels, 14-day max interval
- **Entitlement**: Reads shared Redis access cache (written by subscription-service)
- **Kafka**: Outbox pattern for event publishing, manual acknowledgement for consuming
