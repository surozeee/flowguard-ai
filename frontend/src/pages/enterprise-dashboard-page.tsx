import { useCallback, useEffect, useState } from "react";
import { PageCard } from "@/components/common/page-card";
import { opsService } from "@/shared/services/ops.service";

type ProcessingSummary = {
  gateway: Record<string, unknown>;
  fixtureOutbox: Record<string, unknown>;
  consumerInbox: Record<string, unknown>;
};

export function EnterpriseDashboardPage() {
  const [summary, setSummary] = useState<ProcessingSummary | null>(null);
  const [gatewayInbox, setGatewayInbox] = useState<Record<string, unknown>[]>([]);
  const [sagaInbox, setSagaInbox] = useState<Record<string, unknown>[]>([]);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(() => {
    setError(null);
    Promise.all([
      opsService.processingSummary(),
      opsService.gatewayInbox(10),
      opsService.consumerSagaInbox(10),
    ])
      .then(([s, inbox, consumer]) => {
        setSummary(s);
        setGatewayInbox(inbox);
        setSagaInbox(consumer);
      })
      .catch((e: Error) => setError(e.message));
  }, []);

  useEffect(() => {
    refresh();
    const t = window.setInterval(refresh, 10_000);
    return () => window.clearInterval(t);
  }, [refresh]);

  return (
    <PageCard
      title="Enterprise processing dashboard"
      description="Gateway ingress inbox, partner outbox, fixture Kafka outbox, and backend saga consumer inbox."
    >
      {error ? <p className="error">{error}</p> : null}
      {summary ? (
        <section className="grid-metrics">
          <Metric label="Gateway inbox open" value={String(summary.gateway.inboxOpenCount ?? "—")} />
          <Metric label="Partner outbox pending" value={String(summary.gateway.partnerOutboxPendingCount ?? "—")} />
          <Metric label="Fixture saga pending" value={String(summary.fixtureOutbox.sagaPendingCount ?? "—")} />
          <Metric label="Saga consumer events" value={String(summary.consumerInbox.sagaEventCount ?? "—")} />
        </section>
      ) : (
        <p className="empty">Loading processing metrics…</p>
      )}
      <h3>Recent gateway inbox</h3>
      {gatewayInbox.length === 0 ? (
        <p className="empty">No gateway inbox rows yet.</p>
      ) : (
        <ul className="list">{gatewayInbox.map((row) => renderRow(row, "correlation_id"))}</ul>
      )}
      <h3>Recent saga consumer inbox</h3>
      {sagaInbox.length === 0 ? (
        <p className="empty">No saga consumer events yet.</p>
      ) : (
        <ul className="list">{sagaInbox.map((row) => renderRow(row, "event_type"))}</ul>
      )}
    </PageCard>
  );
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div className="metric-card">
      <div className="metric-label">{label}</div>
      <div className="metric-value">{value}</div>
    </div>
  );
}

function renderRow(row: Record<string, unknown>, extraKey: string) {
  const id = String(row.id ?? "");
  return (
    <li key={id}>
      <code>{id.slice(0, 8)}</code> · {String(row.state ?? row[extraKey] ?? "")}
    </li>
  );
}
