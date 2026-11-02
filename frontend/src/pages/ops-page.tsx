import { useCallback, useEffect, useState } from "react";
import { PageCard } from "@/components/common/page-card";
import { opsService, type DlqRow, type OutboxSummary } from "@/shared/services/ops.service";

export function OpsPage() {
  const [summary, setSummary] = useState<OutboxSummary | null>(null);
  const [processing, setProcessing] = useState<Record<string, unknown> | null>(null);
  const [sagaPending, setSagaPending] = useState<DlqRow[]>([]);
  const [transactionPending, setTransactionPending] = useState<DlqRow[]>([]);
  const [sagaDlq, setSagaDlq] = useState<DlqRow[]>([]);
  const [transactionDlq, setTransactionDlq] = useState<DlqRow[]>([]);
  const [gatewayOutbox, setGatewayOutbox] = useState<DlqRow[]>([]);
  const [gatewayDlq, setGatewayDlq] = useState<DlqRow[]>([]);
  const [consumerInbox, setConsumerInbox] = useState<DlqRow[]>([]);
  const [gaps, setGaps] = useState<DlqRow[]>([]);
  const [stale, setStale] = useState<DlqRow[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);

  const refresh = useCallback(() => {
    setError(null);
    Promise.all([
      opsService.processingSummary(),
      opsService.outboxSummary(),
      opsService.sagaPending(25),
      opsService.transactionPending(25),
      opsService.sagaDlq(25),
      opsService.transactionDlq(25),
      opsService.gatewayPartnerOutbox(25),
      opsService.gatewayPartnerDlq(25),
      opsService.consumerSagaInbox(25),
      opsService.choreographyGaps(25),
      opsService.staleSagas(5),
    ])
      .then(([proc, s, sagaP, txnP, saga, txn, gOut, gDlq, inbox, g, st]) => {
        setProcessing(proc);
        setSummary(s);
        setSagaPending(sagaP);
        setTransactionPending(txnP);
        setSagaDlq(saga);
        setTransactionDlq(txn);
        setGatewayOutbox(gOut);
        setGatewayDlq(gDlq);
        setConsumerInbox(inbox);
        setGaps(g);
        setStale(st);
      })
      .catch((e: Error) => setError(e.message));
  }, []);

  useEffect(() => {
    refresh();
    const t = window.setInterval(refresh, 10_000);
    return () => window.clearInterval(t);
  }, [refresh]);

  const replaySaga = async (id: string) => {
    setBusyId(id);
    try {
      await opsService.replaySagaDlq(id);
      refresh();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Replay failed");
    } finally {
      setBusyId(null);
    }
  };

  const replayTransaction = async (id: string) => {
    setBusyId(id);
    try {
      await opsService.replayTransactionDlq(id);
      refresh();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Replay failed");
    } finally {
      setBusyId(null);
    }
  };

  const replayGateway = async (id: string) => {
    setBusyId(id);
    try {
      await opsService.replayGatewayPartnerDlq(id);
      refresh();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Replay failed");
    } finally {
      setBusyId(null);
    }
  };

  const gatewaySummary = processing?.gateway as Record<string, unknown> | undefined;

  return (
    <PageCard
      title="Operations — inbox, outbox & gateway"
      description="Live fixture outbox, gateway partner calls, consumer inbox, and DLQ replay. Refresh every 10 seconds."
    >
      {error && <p className="error">{error}</p>}

      <div className="grid-metrics">
        <Metric
          label="Gateway inbox open"
          value={String(gatewaySummary?.inboxOpenCount ?? "—")}
        />
        <Metric
          label="Partner pending"
          value={String(gatewaySummary?.partnerOutboxPendingCount ?? "—")}
        />
        <Metric label="Saga pending" value={String(summary?.sagaPendingCount ?? 0)} />
        <Metric label="Saga DLQ" value={String(summary?.sagaDlqCount ?? 0)} />
      </div>

      <OpsSection title="Fixture saga outbox (pending)" empty="No pending saga outbox rows." rows={sagaPending} />
      <OpsSection
        title="Fixture transaction outbox (pending)"
        empty="No pending transaction outbox rows."
        rows={transactionPending}
        keyField="partition_key"
      />
      <DlqSection title="Saga outbox DLQ" empty="No saga DLQ rows." rows={sagaDlq} busyId={busyId} onReplay={replaySaga} />
      <DlqSection
        title="Transaction outbox DLQ"
        empty="No transaction DLQ rows."
        rows={transactionDlq}
        busyId={busyId}
        onReplay={replayTransaction}
        keyField="partition_key"
      />

      <h3>Gateway partner outbox (pending / in-flight)</h3>
      {gatewayOutbox.length === 0 ? (
        <p className="empty">No gateway partner outbox rows (or gateway disabled).</p>
      ) : (
        <table className="data-table">
          <thead>
            <tr>
              <th>Id</th>
              <th>Call</th>
              <th>State</th>
            </tr>
          </thead>
          <tbody>
            {gatewayOutbox.map((row) => (
              <tr key={String(row.id)}>
                <td>
                  <code>{String(row.id).slice(0, 8)}</code>
                </td>
                <td>
                  {String(row.http_method ?? "")} {String(row.path ?? "")}
                </td>
                <td>{String(row.state ?? "")}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      <DlqSection
        title="Gateway partner outbox DLQ"
        empty="No gateway partner DLQ rows."
        rows={gatewayDlq}
        busyId={busyId}
        onReplay={replayGateway}
        keyField="path"
      />

      <h3>Backend saga consumer inbox (recent)</h3>
      {consumerInbox.length === 0 ? (
        <p className="empty">No consumed saga events yet.</p>
      ) : (
        <ul className="list">
          {consumerInbox.map((row) => (
            <li key={String(row.id ?? row.idempotency_key)}>
              <code>{String(row.idempotency_key ?? row.event_type ?? "")}</code>
            </li>
          ))}
        </ul>
      )}

      <h3>Choreography gaps (backend)</h3>
      {gaps.length === 0 ? (
        <p className="empty">No incomplete choreography traces.</p>
      ) : (
        <ul className="list">
          {gaps.map((row) => (
            <li key={String(row.idempotency_key)}>
              <code>{String(row.idempotency_key)}</code> · events {String(row.event_count ?? "")}
            </li>
          ))}
        </ul>
      )}

      <h3>Stale active sagas (fixture)</h3>
      {stale.length === 0 ? (
        <p className="empty">No stale sagas.</p>
      ) : (
        <ul className="list">
          {stale.map((row) => (
            <li key={String(row.id)}>
              <code>{String(row.id)}</code> · {String(row.state)} · {String(row.idempotency_key ?? "")}
            </li>
          ))}
        </ul>
      )}

      <p className="muted" style={{ marginTop: "1.25rem" }}>
        Grafana localhost:3000 (admin / flowguard) · Prometheus localhost:9090
      </p>
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

function OpsSection({
  title,
  empty,
  rows,
  keyField = "idempotency_key",
}: {
  title: string;
  empty: string;
  rows: DlqRow[];
  keyField?: string;
}) {
  return (
    <>
      <h3>{title}</h3>
      {rows.length === 0 ? (
        <p className="empty">{empty}</p>
      ) : (
        <ul className="list">
          {rows.map((row) => (
            <li key={String(row.id)}>
              <code>{String(row.id).slice(0, 8)}</code> · {String(row[keyField] ?? "")} · attempts{" "}
              {String(row.publish_attempts ?? "0")}
            </li>
          ))}
        </ul>
      )}
    </>
  );
}

function DlqSection({
  title,
  empty,
  rows,
  busyId,
  onReplay,
  keyField = "idempotency_key",
}: {
  title: string;
  empty: string;
  rows: DlqRow[];
  busyId: string | null;
  onReplay: (id: string) => void;
  keyField?: string;
}) {
  return (
    <>
      <h3>{title}</h3>
      {rows.length === 0 ? (
        <p className="empty">{empty}</p>
      ) : (
        <table className="data-table">
          <thead>
            <tr>
              <th>Id</th>
              <th>Key</th>
              <th>Attempts</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => {
              const id = String(row.id);
              return (
                <tr key={id}>
                  <td>
                    <code>{id.slice(0, 8)}</code>
                  </td>
                  <td>{String(row[keyField] ?? "")}</td>
                  <td>{String(row.publish_attempts ?? "")}</td>
                  <td>
                    <button type="button" className="secondary" disabled={busyId === id} onClick={() => onReplay(id)}>
                      Replay
                    </button>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      )}
    </>
  );
}
