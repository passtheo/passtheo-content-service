-- ============================================================
-- V7: Add UNIQUE(session_id, strapi_question_id) to session_answers
-- Prevents duplicate answer submissions for the same question in a session.
-- ============================================================

ALTER TABLE session_answers
    ADD CONSTRAINT uq_session_answer_question
        UNIQUE (session_id, strapi_question_id);
