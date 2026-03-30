# Memory Index — passtheo-content-service

> Auto-maintained by Claude Code. See CLAUDE.md for static rules.

## Architectural Decisions

<!-- 2026-03-30 | Seed -->
- Outbox pattern: never call KafkaTemplate directly in service layer
- Strapi content is always fetched via StrapiApiClient with circuit breaker — never call Strapi URL directly
- Free tier enforcement is a subscription-service concern — content-service checks entitlements via SubscriptionServiceClient
- @Transactional on service layer only — controllers have none

## Known Gotchas

<!-- 2026-03-30 | Seed -->
- Redis DB 3 is for Strapi content cache only — do not store user data here
- Circuit breaker for Strapi: on open, serve stale cache or return 503 — never fail silently
- Streak logic uses UTC calendar day — practicing at 23:59 and 00:01 UTC counts as two streak days
- Readiness snapshot is computed by scheduler, not in real-time per request
- spring.flyway.clean-disabled=true — never run flyway:clean
- 8 migrations — most entities in the platform

## Test Patterns

<!-- 2026-03-30 | Seed -->
- Unit tests: @ExtendWith(MockitoExtension.class) — no @SpringBootTest
- Test naming: methodName_scenario_expectedOutcome
- RLS isolation tests mandatory
- Mock StrapiApiClient in unit tests — it calls an external service
- WireMock for Strapi API integration tests
