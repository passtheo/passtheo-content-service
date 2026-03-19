-- ============================================================
-- V5: Add missing BaseEntity columns (created_at, updated_at, deleted_at)
-- BaseEntity requires: id, tenant_id, created_at, updated_at, deleted_at
-- ============================================================

-- domain_progress: missing created_at, deleted_at
ALTER TABLE domain_progress ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT NOW();
ALTER TABLE domain_progress ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;

-- topic_progress: missing created_at, deleted_at
ALTER TABLE topic_progress ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT NOW();
ALTER TABLE topic_progress ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;

-- streaks: missing created_at, deleted_at
ALTER TABLE streaks ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT NOW();
ALTER TABLE streaks ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;

-- earned_achievements: missing created_at, updated_at, deleted_at
ALTER TABLE earned_achievements ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT NOW();
ALTER TABLE earned_achievements ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT NOW();
ALTER TABLE earned_achievements ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;

-- exam_attempts: missing created_at, updated_at, deleted_at
ALTER TABLE exam_attempts ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT NOW();
ALTER TABLE exam_attempts ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT NOW();
ALTER TABLE exam_attempts ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;

-- exam_answers: missing created_at, updated_at, deleted_at
ALTER TABLE exam_answers ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT NOW();
ALTER TABLE exam_answers ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT NOW();
ALTER TABLE exam_answers ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;

-- session_answers: missing created_at, updated_at, deleted_at
ALTER TABLE session_answers ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT NOW();
ALTER TABLE session_answers ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT NOW();
ALTER TABLE session_answers ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;

-- study_sessions: missing updated_at, deleted_at
ALTER TABLE study_sessions ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT NOW();
ALTER TABLE study_sessions ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;

-- question_progress: missing deleted_at
ALTER TABLE question_progress ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;

-- question_difficulty: missing created_at, deleted_at
ALTER TABLE question_difficulty ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT NOW();
ALTER TABLE question_difficulty ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;

-- readiness_snapshots: missing updated_at, deleted_at
ALTER TABLE readiness_snapshots ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT NOW();
ALTER TABLE readiness_snapshots ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;

-- study_plans: missing deleted_at
ALTER TABLE study_plans ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;

-- study_plan_days: missing created_at, updated_at, deleted_at
ALTER TABLE study_plan_days ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT NOW();
ALTER TABLE study_plan_days ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT NOW();
ALTER TABLE study_plan_days ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;
