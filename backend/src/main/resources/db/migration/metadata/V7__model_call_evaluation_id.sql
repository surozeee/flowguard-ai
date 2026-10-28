ALTER TABLE model_calls
  ADD COLUMN IF NOT EXISTS evaluation_id UUID REFERENCES evaluations(id);
