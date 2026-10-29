CREATE UNIQUE INDEX IF NOT EXISTS uq_saga_choreography_event
  ON saga_choreography_log (saga_id, event_type, step);
