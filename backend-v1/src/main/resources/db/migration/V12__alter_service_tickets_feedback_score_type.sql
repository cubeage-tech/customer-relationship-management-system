-- FR-6.5: feedback_score was declared SMALLINT in V11, but the entity maps it as a
-- Java Integer, which Hibernate expects as SQL INTEGER — schema-validation failed on
-- startup with a type mismatch (int2 vs int4). Widening SMALLINT -> INTEGER preserves
-- the existing "BETWEEN 1 AND 5" check constraint automatically.

ALTER TABLE service_tickets ALTER COLUMN feedback_score TYPE INTEGER;
