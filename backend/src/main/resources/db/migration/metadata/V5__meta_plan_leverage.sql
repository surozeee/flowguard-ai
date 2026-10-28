ALTER TABLE hypotheses
  ALTER COLUMN invariant_id TYPE VARCHAR(120),
  ALTER COLUMN suspected_mechanism TYPE TEXT,
  ALTER COLUMN predicted_observation TYPE TEXT;

ALTER TABLE model_calls
  ADD COLUMN IF NOT EXISTS source VARCHAR(20),
  ADD COLUMN IF NOT EXISTS http_status INT;
