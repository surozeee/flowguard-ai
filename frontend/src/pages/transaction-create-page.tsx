import { FormEvent, useState } from "react";
import { PageCard } from "@/components/common/page-card";
import { IntegrationBoard } from "@/components/transaction/integration-board";
import {
  DEMO_ACCOUNTS,
  transactionService,
  type IntegrationSnapshot,
  type TransactionResult,
} from "@/shared/services/transaction.service";

export function TransactionCreatePage() {
  const [idempotencyKey, setIdempotencyKey] = useState(() => `credit-${crypto.randomUUID()}`);
  const [amountMinor, setAmountMinor] = useState(10_000);
  const [targetAccountId, setTargetAccountId] = useState(DEMO_ACCOUNTS.customer);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [attempts, setAttempts] = useState<TransactionResult[]>([]);
  const [snapshot, setSnapshot] = useState<IntegrationSnapshot | null>(null);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const result = await transactionService.createCredit({
        clientReference: `ui-${idempotencyKey}`,
        idempotencyKey,
        targetAccountId,
        amountMinor,
      });
      setAttempts((prev) => [result, ...prev]);
      setSnapshot(result.integration);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Credit failed");
    } finally {
      setBusy(false);
    }
  }

  async function refresh() {
    if (!idempotencyKey) return;
    const next = await transactionService.integration(idempotencyKey, targetAccountId);
    setSnapshot(next);
  }

  const paymentIds = attempts
    .map((a) => a.fixture?.paymentId ?? "")
    .filter(Boolean);
  const sameId = paymentIds.length >= 2 && paymentIds.every((id) => id === paymentIds[0]);

  return (
    <div className="stack">
      <PageCard
        title="Create credit"
        description="Posts through the API gateway → backend → fixture wallet. Re-submit the same idempotency key to prove at-most-once processing."
      >
        <form className="form-wide" onSubmit={submit}>
          <label>
            Idempotency key
            <input value={idempotencyKey} onChange={(e) => setIdempotencyKey(e.target.value)} required />
          </label>
          <label>
            Target account
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
          <div className="toolbar">
            <button type="submit" disabled={busy}>
              {busy ? "Posting…" : attempts.length === 0 ? "Create credit" : "Replay same key"}
            </button>
            <button type="button" className="secondary" onClick={() => setIdempotencyKey(`credit-${crypto.randomUUID()}`)}>
              New key
            </button>
            <button type="button" className="secondary" disabled={!attempts.length} onClick={() => void refresh()}>
              Refresh integration
            </button>
          </div>
        </form>
        {error ? <p className="error">{error}</p> : null}
        {attempts.length > 0 ? (
          <p className={sameId ? "ok" : "muted"}>
            {attempts.length} POST{attempts.length === 1 ? "" : "s"} with this key
            {paymentIds[0] ? (
              <>
                {" "}
                · paymentId <code>{paymentIds[0]}</code>
              </>
            ) : null}
            {sameId ? " · identical payment id (idempotent replay)" : null}
          </p>
        ) : null}
      </PageCard>
      <PageCard title="Idempotency & choreography" description="Redis cache, fixture saga rows, and backend Kafka choreography log.">
        <IntegrationBoard snapshot={snapshot} />
      </PageCard>
    </div>
  );
}
