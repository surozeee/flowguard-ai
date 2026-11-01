import { apiFetch } from "@/lib/api-fetch";

export type StartEvaluationRequest = {
  projectSlug?: string;
  arm?: "META" | "RANDOM";
  probeBudget?: number;
  objective?: string;
};

export type EvaluationSummary = {
  id: string;
  projectSlug: string;
  arm: string;
  probeBudget: number;
  probesUsed: number;
  firstViolationProbe: number | null;
  status: string;
  result?: Record<string, unknown>;
};

export const evaluationService = {
  start: (body: StartEvaluationRequest) =>
    apiFetch<{ evaluationId: string; arm: string; probeBudget: number }>("/evaluations", {
      method: "POST",
      body: JSON.stringify(body),
    }),

  getById: (id: string) => apiFetch<EvaluationSummary>(`/evaluations/${id}`),
};
