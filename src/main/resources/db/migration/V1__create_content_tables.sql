-- ============================================================
-- PassTheo Content Service — Core Tables
-- ============================================================

-- 1. Study Sessions
CREATE TABLE study_sessions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID         NOT NULL,
    keycloak_user_id    UUID         NOT NULL,
    product_code        VARCHAR(50)  NOT NULL,
    domain_code         VARCHAR(50),
    topic_code          VARCHAR(50),
    session_type        VARCHAR(20)  NOT NULL,
    status              VARCHAR(20)  NOT NULL DEFAULT 'IN_PROGRESS',
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

-- 2. Session Answers
CREATE TABLE session_answers (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID         NOT NULL,
    session_id          UUID         NOT NULL REFERENCES study_sessions(id),
    keycloak_user_id    UUID         NOT NULL,
    strapi_question_id  VARCHAR(100) NOT NULL,
    question_version    INTEGER      NOT NULL DEFAULT 1,
    interaction_type    VARCHAR(30)  NOT NULL,
    is_correct          BOOLEAN      NOT NULL,
    user_answer         JSONB        NOT NULL,
    correct_answer      JSONB        NOT NULL,
    time_taken_ms       INTEGER      NOT NULL,
    question_order      INTEGER      NOT NULL,
    answered_at         TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- 3. Question Progress (Spaced Repetition State)
CREATE TABLE question_progress (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID         NOT NULL,
    keycloak_user_id        UUID         NOT NULL,
    strapi_question_id      VARCHAR(100) NOT NULL,
    product_code            VARCHAR(50)  NOT NULL,
    domain_code             VARCHAR(50)  NOT NULL,
    topic_code              VARCHAR(50)  NOT NULL,
    mastery_level           VARCHAR(20)  NOT NULL DEFAULT 'NEW',
    ease_factor             DECIMAL(4,2) NOT NULL DEFAULT 2.50,
    consecutive_correct     INTEGER      NOT NULL DEFAULT 0,
    total_attempts          INTEGER      NOT NULL DEFAULT 0,
    total_correct           INTEGER      NOT NULL DEFAULT 0,
    last_answered_at        TIMESTAMP,
    next_review_at          TIMESTAMP,
    interval_days           INTEGER      NOT NULL DEFAULT 0,
    created_at              TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_progress_user_question
        UNIQUE (tenant_id, keycloak_user_id, strapi_question_id)
);

-- 4. Topic Progress (Aggregated)
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

-- 5. Domain Progress (Aggregated)
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
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_domain_progress
        UNIQUE (tenant_id, keycloak_user_id, product_code, domain_code)
);

-- 6. Streaks
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

-- 7. Earned Achievements
CREATE TABLE earned_achievements (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID         NOT NULL,
    keycloak_user_id    UUID         NOT NULL,
    achievement_code    VARCHAR(50)  NOT NULL,
    earned_at           TIMESTAMP    NOT NULL DEFAULT NOW(),
    trigger_value       INTEGER,
    notified            BOOLEAN      NOT NULL DEFAULT FALSE,

    CONSTRAINT uq_achievement_user
        UNIQUE (tenant_id, keycloak_user_id, achievement_code)
);

-- 8. Exam Attempts
CREATE TABLE exam_attempts (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID         NOT NULL,
    keycloak_user_id    UUID         NOT NULL,
    product_code        VARCHAR(50)  NOT NULL,
    exam_type           VARCHAR(20)  NOT NULL,
    total_questions     INTEGER      NOT NULL,
    correct_count       INTEGER      NOT NULL,
    pass_score          INTEGER      NOT NULL,
    passed              BOOLEAN      NOT NULL,
    score_percent       DECIMAL(5,2) NOT NULL,
    time_taken_seconds  INTEGER      NOT NULL,
    time_limit_seconds  INTEGER      NOT NULL,
    domain_breakdown    JSONB        NOT NULL,
    started_at          TIMESTAMP    NOT NULL,
    completed_at        TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT check_exam_score CHECK (correct_count >= 0 AND correct_count <= total_questions)
);

-- 9. Exam Answers
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

-- 10. Readiness Snapshots
CREATE TABLE readiness_snapshots (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID         NOT NULL,
    keycloak_user_id    UUID         NOT NULL,
    product_code        VARCHAR(50)  NOT NULL,
    snapshot_date       DATE         NOT NULL,
    readiness_score     DECIMAL(5,2) NOT NULL,
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

-- 11. Question Difficulty (Crowd-Sourced Calibration)
CREATE TABLE question_difficulty (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID         NOT NULL,
    strapi_question_id  VARCHAR(100) NOT NULL,
    product_code        VARCHAR(50)  NOT NULL,
    times_answered      INTEGER      NOT NULL DEFAULT 0,
    times_correct       INTEGER      NOT NULL DEFAULT 0,
    difficulty_score    DECIMAL(5,2),
    calibrated_at       TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_difficulty
        UNIQUE (tenant_id, strapi_question_id)
);

-- 12. Study Plans
CREATE TABLE study_plans (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID         NOT NULL,
    keycloak_user_id        UUID         NOT NULL,
    product_code            VARCHAR(50)  NOT NULL,
    exam_date               DATE,
    total_days              INTEGER      NOT NULL,
    status                  VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    daily_question_target   INTEGER      NOT NULL DEFAULT 20,
    focus_domains           JSONB,
    created_at              TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_active_plan
        UNIQUE (tenant_id, keycloak_user_id, product_code)
);

-- 13. Study Plan Days
CREATE TABLE study_plan_days (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID         NOT NULL,
    plan_id             UUID         NOT NULL REFERENCES study_plans(id),
    day_number          INTEGER      NOT NULL,
    plan_date           DATE         NOT NULL,
    domain_code         VARCHAR(50)  NOT NULL,
    question_target     INTEGER      NOT NULL DEFAULT 20,
    questions_completed INTEGER      NOT NULL DEFAULT 0,
    include_exam        BOOLEAN      NOT NULL DEFAULT FALSE,
    status              VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    completed_at        TIMESTAMP,

    CONSTRAINT uq_plan_day UNIQUE (plan_id, day_number)
);
