CREATE TABLE IF NOT EXISTS projects (
  id UUID PRIMARY KEY,
  slug VARCHAR(80) NOT NULL UNIQUE,
  fixture_version VARCHAR(40) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS investigations (
  id UUID PRIMARY KEY,
  project_id UUID NOT NULL REFERENCES projects(id),
  state VARCHAR(40) NOT NULL,
  model_id VARCHAR(80) NOT NULL,
  budget_limit INT NOT NULL,
  budget_consumed INT NOT NULL DEFAULT 0,
  objective TEXT,
  started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  completed_at TIMESTAMPTZ,
  error_code VARCHAR(80)
);

CREATE TABLE IF NOT EXISTS hypotheses (
  id UUID PRIMARY KEY,
  investigation_id UUID NOT NULL REFERENCES investigations(id),
  invariant_id VARCHAR(60) NOT NULL,
  suspected_mechanism VARCHAR(80),
  predicted_observation VARCHAR(120),
  hypothesis_text TEXT,
  raw_model_response_hash VARCHAR(128),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS experiments (
  id UUID PRIMARY KEY,
  investigation_id UUID NOT NULL REFERENCES investigations(id),
  hypothesis_id UUID REFERENCES hypotheses(id),
  experiment_type VARCHAR(60) NOT NULL,
  params_json JSONB NOT NULL,
  seed BIGINT,
  status VARCHAR(40) NOT NULL,
  oracle_verdict VARCHAR(60),
  started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  completed_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS run_evidence (
  id UUID PRIMARY KEY,
  experiment_id UUID NOT NULL REFERENCES experiments(id),
  oracle_version VARCHAR(20) NOT NULL,
  verdict VARCHAR(60) NOT NULL,
  before_json JSONB,
  after_json JSONB,
  trace_json JSONB,
  record_hash VARCHAR(128),
  prev_hash VARCHAR(128),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO projects (id, slug, fixture_version)
VALUES ('11111111-1111-4111-8111-111111111111', 'sample-wallet', '0.1.0')
ON CONFLICT (slug) DO NOTHING;
