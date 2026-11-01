import type { CachedTransactionEvent, IntegrationSnapshot } from "@/shared/services/transaction.service";

function hasEvent(event: CachedTransactionEvent | null | undefined) {
  return Boolean(event && (event.eventType || event.idempotencyKey || event.accountId));
}

export function IntegrationBoard({ snapshot }: { snapshot: IntegrationSnapshot | null }) {
  if (!snapshot) {
    return <p className="empty">Submit a transaction to load idempotency, saga orchestration, and choreography.</p>;
  }

  const verify = snapshot.verify;
  return (
    <div className="stack">
      <div className="grid-metrics">
        <div className="metric-card">
          <div className="metric-label">Idempotency key</div>
          <div className="metric-value" style={{ fontSize: "0.95rem" }}>
            <code>{snapshot.idempotencyKey}</code>
          </div>
        </div>
        <div className="metric-card">
          <div className="metric-label">Choreography verify</div>
          <div className={`metric-value ${verify?.passed ? "ok" : "error"}`} style={{ fontSize: "1.15rem" }}>
            {verify?.passed ? "Passed" : verify?.reason ?? "Pending"}
          </div>
        </div>
        <div className="metric-card">
          <div className="metric-label">Risk tier</div>
          <div className="metric-value" style={{ fontSize: "1.15rem" }}>
            {snapshot.risk?.riskTier ?? "—"}
          </div>
        </div>
      </div>

      <h3>Idempotency record (Redis)</h3>
      {!hasEvent(snapshot.idempotencyRecord) ? (
        <p className="empty">No Redis idempotency hit yet. Kafka consumers write this after settlement.</p>
      ) : (
        <pre>{JSON.stringify(snapshot.idempotencyRecord, null, 2)}</pre>
      )}

      <h3>Last transaction cache</h3>
      {!hasEvent(snapshot.lastTransaction) ? (
        <p className="empty">No last-transaction cache yet.</p>
      ) : (
        <pre>{JSON.stringify(snapshot.lastTransaction, null, 2)}</pre>
      )}

      <div className="flow-lanes">
        <div className="lane">
          <h3>Orchestrator saga (fixture)</h3>
          <p className="muted">Local payment saga state machine + outbox.</p>
          {snapshot.orchestratorError ? <p className="error">{snapshot.orchestratorError}</p> : null}
          {(snapshot.orchestrator ?? []).length === 0 ? (
            <p className="empty">No orchestrator rows yet.</p>
          ) : (
            <ol className="timeline">
              {snapshot.orchestrator.map((row, i) => (
                <li key={row.id ?? String(i)}>
                  <strong>{row.state ?? "saga"}</strong>
                  <span className="muted">
                    {" "}
                    {row.id} {row.currentStep} {String(row.updatedAt ?? "")}
                  </span>
                </li>
              ))}
            </ol>
          )}
        </div>
        <div className="lane">
          <h3>Choreography (backend consumer)</h3>
          <p className="muted">Kafka saga events appended to metadata.saga_choreography_log.</p>
          {(snapshot.choreography ?? []).length === 0 ? (
            <p className="empty">Waiting for choreography events…</p>
          ) : (
            <ol className="timeline">
              {snapshot.choreography.map((row, i) => (
                <li key={`${row.event_type}-${i}`}>
                  <code>{row.event_type}</code>
                  {row.saga_state ? ` · ${row.saga_state}` : ""}
                  {row.step ? ` · ${row.step}` : ""}
                </li>
              ))}
            </ol>
          )}
        </div>
      </div>
    </div>
  );
}
