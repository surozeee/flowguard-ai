import { FormEvent, useEffect, useState } from "react";
import { PageCard } from "@/components/common/page-card";
import { IntegrationBoard } from "@/components/transaction/integration-board";
import {
  DEMO_ACCOUNTS,
  transactionService,
  type IntegrationSnapshot,
} from "@/shared/services/transaction.service";

export function TransactionSagaPage() {
  const [idempotencyKey, setIdempotencyKey] = useState(() => `xfer-${crypto.randomUUID()}`);
  const [amountMinor, setAmountMinor] = useState(5_000);
  const [sourceAccountId, setSourceAccountId] = useState(DEMO_ACCOUNTS.customer);
  const [targetAccountId, setTargetAccountId] = useState(DEMO_ACCOUNTS.treasury);
  const [prefund, setPrefund] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [paymentId, setPaymentId] = useState<string | null>(null);
  const [snapshot, setSnapshot] = useState<IntegrationSnapshot | null>(null);

  useEffect(() => {
    if (!paymentId) return;
    let cancelled = false;
    let ticks = 0;
    const timer = window.setInterval(() => {
      ticks += 1;
      transactionService
        .integration(idempotencyKey, sourceAccountId)
        .then((next) => {
          if (!cancelled) setSnapshot(next);
          if (next.verify?.passed || ticks >= 20) {
            window.clearInterval(timer);
          }
        })
        .catch(() => {
          /* keep polling */
        });
    }, 1500);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [paymentId, idempotencyKey, sourceAccountId]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const result = await transactionService.createTransfer({
        sourceAccountId,
        targetAccountId,
        amountMinor,
        idempotencyKey,
        prefund,
        fundMinor: 100_000,
      });
      setPaymentId(result.fixture?.paymentId ?? "ok");
      setSnapshot(result.integration);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Transfer failed");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="stack">
      <PageCard
        title="Create transfer"
        description="Debit transfer starts the fixture orchestrator saga; Kafka outbox events are consumed as choreography on the backend."
      >
        <form className="form-wide" onSubmit={submit}>
          <label>
            Idempotency key
            <input value={idempotencyKey} onChange={(e) => setIdempotencyKey(e.target.value)} required />
          </label>
          <label>
            Source (customer)
            <input value={sourceAccountId} onChange={(e) => setSourceAccountId(e.target.value)} required />
          </label>
          <label>
            Target (treasury)
            <input value={targetAccountId} onChange={(e) => setTargetAccountId(e.target.value)} required />
          </label>
          <label>
            Amount (minor units)
            <input
              type="number"
              min={1}
              value={amountMinor}
              onChange={(e) => setAmountMinor(Number(e.target.value))}
            />
          </label>
          <label className="check">
            <input type="checkbox" checked={prefund} onChange={(e) => setPrefund(e.target.checked)} />
            Prefund source with a unique credit first
          </label>
          <div className="toolbar">
            <button type="submit" disabled={busy}>
              {busy ? "Starting saga…" : "Create transfer"}
            </button>
            <button type="button" className="secondary" onClick={() => setIdempotencyKey(`xfer-${crypto.randomUUID()}`)}>
              New key
            </button>
          </div>
        </form>
        {error ? <p className="error">{error}</p> : null}
        {paymentId ? (
          <p className="ok">
            Transfer accepted · payment <code>{paymentId}</code>. Polling choreography until SAGA_COMPLETED.
          </p>
        ) : null}
      </PageCard>
      <PageCard
        title="Saga pattern — orchestrator + choreography"
        description="Left lane is fixture saga state. Right lane is independent backend consumers. Verify requires SAGA_STARTED and a terminal event."
      >
        <IntegrationBoard snapshot={snapshot} />
      </PageCard>
    </div>
  );
}
