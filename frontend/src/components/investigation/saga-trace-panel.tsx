import { useEffect, useState } from "react";
import { sagaService, type SagaTraceRow, type SagaVerifyResult } from "@/shared/services/saga.service";

type Props = {
  idempotencyKey?: string;
};

export function SagaTracePanel({ idempotencyKey }: Props) {
  const [trace, setTrace] = useState<SagaTraceRow[]>([]);
  const [verify, setVerify] = useState<SagaVerifyResult | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!idempotencyKey) {
      setTrace([]);
      setVerify(null);
      return;
    }
    let cancelled = false;
    setError(null);
    sagaService
      .verifyByIdempotency(idempotencyKey)
      .then((v) => {
        if (!cancelled) setVerify(v);
      })
      .catch((e: Error) => {
        if (!cancelled) setError(e.message);
      });
    sagaService
      .traceByIdempotency(idempotencyKey)
      .then((rows) => {
        if (!cancelled) setTrace(rows);
      })
      .catch(() => {
        /* verify error already surfaced */
      });
    return () => {
      cancelled = true;
    };
  }, [idempotencyKey]);

  if (!idempotencyKey) {
    return null;
  }

  return (
    <section>
      <h3>Saga choreography</h3>
      <p className="muted">
        Idempotency key: <code>{idempotencyKey}</code>
        {verify && (
          <>
            {" "}
            · Verify: <strong className={verify.passed ? "ok" : "error"}>{verify.passed ? "passed" : "failed"}</strong>
            {verify.reason ? ` (${verify.reason})` : ""}
          </>
        )}
      </p>
      {error && <p className="error">{error}</p>}
      {trace.length === 0 ? (
        <p className="empty">No choreography events yet.</p>
      ) : (
        <ol className="list">
          {trace.map((row, i) => (
            <li key={`${row.event_type}-${i}`}>
              <code>{row.event_type}</code>
              {row.saga_state ? ` · ${row.saga_state}` : ""}
              {row.step ? ` · ${row.step}` : ""}
            </li>
          ))}
        </ol>
      )}
    </section>
  );
}
