import { apiFetch } from "@/lib/api-fetch";
import type { SagaTraceRow, SagaVerifyResult } from "@/shared/services/saga.service";

export const DEMO_ACCOUNTS = {
  treasury: "00000000-0000-4000-8000-000000000001",
  customer: "00000000-0000-4000-8000-000000000002",
};

export type CachedTransactionEvent = {
  eventType: string | null;
  accountId: string | null;
  idempotencyKey: string | null;
  riskTier: string | null;
};

export type CustomerRiskSnapshot = {
  accountId: string;
  riskTier: string;
  settledTxnCount: number;
};

export type OrchestratorSagaRow = {
  id: string | null;
  idempotencyKey: string | null;
  sagaType: string | null;
  state: string | null;
  currentStep: string | null;
  accountId: string | null;
  paymentId: string | null;
  failureReason: string | null;
  createdAt: string | null;
  updatedAt: string | null;
};

export type FixturePaymentAck = {
  paymentId: string | null;
};

export type IntegrationSnapshot = {
  idempotencyKey: string;
  accountId: string;
  idempotencyRecord: CachedTransactionEvent | null;
  lastTransaction: CachedTransactionEvent | null;
  risk: CustomerRiskSnapshot;
  orchestrator: OrchestratorSagaRow[];
  orchestratorError: string | null;
  choreography: SagaTraceRow[];
  verify: SagaVerifyResult;
};

export type TransactionResult = {
  kind: "CREDIT" | "TRANSFER";
  idempotencyKey: string;
  sourceAccountId: string | null;
  targetAccountId: string | null;
  amountMinor: number;
  fixture: FixturePaymentAck;
  integration: IntegrationSnapshot;
};

export const transactionService = {
  createCredit(body: {
    clientReference?: string;
    idempotencyKey: string;
    targetAccountId: string;
    amountMinor: number;
  }) {
    return apiFetch<TransactionResult>("/transactions/credits", {
      method: "POST",
      body: JSON.stringify(body),
    });
  },
  createTransfer(body: {
    sourceAccountId: string;
    targetAccountId: string;
    amountMinor: number;
    idempotencyKey: string;
    prefund?: boolean;
    fundMinor?: number;
  }) {
    return apiFetch<TransactionResult>("/transactions/transfers", {
      method: "POST",
      body: JSON.stringify(body),
    });
  },
  integration(idempotencyKey: string, accountId?: string) {
    const q = accountId ? `?accountId=${encodeURIComponent(accountId)}` : "";
    return apiFetch<IntegrationSnapshot>(
      `/transactions/integration/${encodeURIComponent(idempotencyKey)}${q}`,
    );
  },
};
