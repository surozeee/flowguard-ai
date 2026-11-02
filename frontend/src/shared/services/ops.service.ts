import { apiFetch } from "@/lib/api-fetch";

export type DlqRow = Record<string, unknown>;

export type OutboxSummary = {
  sagaPendingCount?: number;
  transactionPendingCount?: number;
  sagaDlqCount: number;
  transactionDlqCount: number;
};

export const opsService = {
  processingSummary() {
    return apiFetch<{
      gateway: Record<string, unknown>;
      fixtureOutbox: OutboxSummary;
      consumerInbox: Record<string, unknown>;
    }>("/ops/processing/summary");
  },
  outboxSummary() {
    return apiFetch<OutboxSummary>("/ops/outbox/summary");
  },
  gatewayInbox(limit = 25) {
    return apiFetch<DlqRow[]>(`/ops/gateway/inbox?limit=${limit}`);
  },
  consumerSagaInbox(limit = 25) {
    return apiFetch<DlqRow[]>(`/ops/inbox/saga-events?limit=${limit}`);
  },
  sagaDlq(limit = 50) {
    return apiFetch<DlqRow[]>(`/ops/outbox/saga-dlq?limit=${limit}`);
  },
  transactionDlq(limit = 50) {
    return apiFetch<DlqRow[]>(`/ops/outbox/transaction-dlq?limit=${limit}`);
  },
  replaySagaDlq(id: string) {
    return apiFetch<{ replayed: boolean; outboxId: string }>(`/ops/outbox/saga-dlq/${id}/replay`, {
      method: "POST",
    });
  },
  replayTransactionDlq(id: string) {
    return apiFetch<{ replayed: boolean; outboxId: string }>(`/ops/outbox/transaction-dlq/${id}/replay`, {
      method: "POST",
    });
  },
  staleSagas(olderThanMinutes = 5) {
    return apiFetch<DlqRow[]>(`/ops/sagas/stale?olderThanMinutes=${olderThanMinutes}`);
  },
  choreographyGaps(limit = 50) {
    return apiFetch<DlqRow[]>(`/ops/sagas/choreography-gaps?limit=${limit}`);
  },
  sagaPending(limit = 25) {
    return apiFetch<DlqRow[]>(`/ops/outbox/saga-pending?limit=${limit}`);
  },
  transactionPending(limit = 25) {
    return apiFetch<DlqRow[]>(`/ops/outbox/transaction-pending?limit=${limit}`);
  },
  gatewayPartnerOutbox(limit = 25) {
    return apiFetch<DlqRow[]>(`/ops/gateway/outbox?limit=${limit}`);
  },
  gatewayPartnerDlq(limit = 25) {
    return apiFetch<DlqRow[]>(`/ops/gateway/outbox/dlq?limit=${limit}`);
  },
  replayGatewayPartnerDlq(id: string) {
    return apiFetch<{ replayed: boolean }>(`/ops/gateway/outbox/dlq/${id}/replay`, { method: "POST" });
  },
};
