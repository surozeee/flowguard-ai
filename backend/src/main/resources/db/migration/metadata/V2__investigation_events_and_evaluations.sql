CREATE TABLE IF NOT EXISTS investigation_events (
  id UUID PRIMARY KEY,
  investigation_id UUID NOT NULL REFERENCES investigations(id),
  sequence_no BIGINT NOT NULL,
  event_type VARCHAR(80) NOT NULL,
  payload_json JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (investigation_id, sequence_no)
);

CREATE TABLE IF NOT EXISTS model_calls (
  id UUID PRIMARY KEY,
  investigation_id UUID REFERENCES investigations(id),
  model_id VARCHAR(80) NOT NULL,
  latency_ms INT,
  input_tokens INT,
  output_tokens INT,
  error_class VARCHAR(80),
  response_hash VARCHAR(128),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS patch_attempts (
  id UUID PRIMARY KEY,
  investigation_id UUID NOT NULL REFERENCES investigations(id),
  candidate_diff_hash VARCHAR(128) NOT NULL,
  approval_status VARCHAR(40) NOT NULL,
  build_status VARCHAR(40),
  immutable_test_hash VARCHAR(128),
  verdict VARCHAR(60),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE run_evidence ADD COLUMN IF NOT EXISTS manifest_hash VARCHAR(128);

CREATE TABLE IF NOT EXISTS evaluations (
  id UUID PRIMARY KEY,
  project_slug VARCHAR(80) NOT NULL,
  arm VARCHAR(40) NOT NULL,
  probe_budget INT NOT NULL,
  probes_used INT NOT NULL DEFAULT 0,
  first_violation_probe INT,
  status VARCHAR(40) NOT NULL,
  result_json JSONB,
  started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  completed_at TIMESTAMPTZ
);
