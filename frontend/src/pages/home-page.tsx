import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { PageCard } from "@/components/common/page-card";
import { healthService, type HealthPayload } from "@/shared/services/health.service";

export function HomePage() {
  const [health, setHealth] = useState<HealthPayload | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    healthService
      .check()
      .then((payload) => {
        setHealth(payload);
        setError(null);
      })
      .catch(() => {
        setHealth(null);
        setError("backend unreachable");
      });
  }, []);

  const deps = health?.dependencies ?? {};

  return (
    <div className="stack">
      <PageCard
        title="FlowGuard AI — LedgerGuard"
        description="Independent investigation for payment ledgers. Meta may suggest the next probe. Java and SQL decide whether the books still balance. This is a synthetic demo — no real funds."
      >
        <div className="grid-metrics">
          <div className="metric-card">
            <div className="metric-label">API status</div>
            <div className="metric-value">{error ? "Down" : health?.status ?? "Checking"}</div>
          </div>
          <div className="metric-card">
            <div className="metric-label">Orchestrator</div>
            <div className="metric-value" style={{ fontSize: "1.05rem" }}>
              {health?.service ?? "flowguard-backend"}
            </div>
          </div>
        </div>
        <p className="muted">Dependency health</p>
        <div className="status-row">
          {error ? (
            <span className="pill down">
              <span className="dot" />
              {error}
            </span>
          ) : Object.keys(deps).length === 0 ? (
            <span className="pill">
              <span className="dot" />
              checking
            </span>
          ) : (
            Object.entries(deps).map(([name, status]) => {
              const up = String(status).toUpperCase() === "UP";
              return (
                <span key={name} className={up ? "pill ok" : "pill down"}>
                  <span className="dot" />
                  {name} · {String(status)}
                </span>
              );
            })
          )}
        </div>
        <p className="lab-note">
          Lab note: a repeated credit key should return the same payment. Transfers still post straight to the ledger.
        </p>
        <button type="button" onClick={() => window.location.reload()}>
          Reload dependency check
        </button>        <div className="action-grid">
          <Link className="action-card" to="/transactions/new">
            <strong>Create credit</strong>
            <span>Post an idempotent payment and replay the same key.</span>
          </Link>
          <Link className="action-card" to="/transactions/saga">
            <strong>Create transfer</strong>
            <span>Run orchestrator saga plus Kafka choreography end to end.</span>
          </Link>

          <Link className="action-card" to="/investigate">
            <strong>Start investigation</strong>
            <span>Hypothesize duplicate-credit risk and probe the fixture.</span>
          </Link>
          <Link className="action-card" to="/dashboard">
            <strong>Processing dashboard</strong>
            <span>Watch gateway inbox, partner outbox, and saga consumers.</span>
          </Link>
          <Link className="action-card" to="/ops">
            <strong>Operations console</strong>
            <span>Inspect pending outbox rows and replay dead-letter items.</span>
          </Link>
        </div>
      </PageCard>
    </div>
  );
}
