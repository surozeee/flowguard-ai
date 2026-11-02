import { apiFetch } from "@/lib/api-fetch";

export type SagaTraceRow = {
  event_type: string;
  saga_state?: string;
  step?: string;
  recorded_at?: string;
};

export type SagaVerifyResult = {
  passed: boolean;
  idempotencyKey: string;
  eventCount?: number;
  eventTypes?: string[];
  reason?: string;
};

export const sagaService = {
  traceByIdempotency(idempotencyKey: string) {
    return apiFetch<SagaTraceRow[]>(`/sagas/trace/idempotency/${encodeURIComponent(idempotencyKey)}`);
  },
  verifyByIdempotency(idempotencyKey: string) {
    return apiFetch<SagaVerifyResult>(`/sagas/verify/idempotency/${encodeURIComponent(idempotencyKey)}`);
  },
  unifiedTrace(idempotencyKey: string) {
    return apiFetch<{
      idempotencyKey: string;
      orchestrator: SagaTraceRow[];
      choreography: SagaTraceRow[];
      verify: SagaVerifyResult;
    }>(`/sagas/unified/idempotency/${encodeURIComponent(idempotencyKey)}`);
  },
};
