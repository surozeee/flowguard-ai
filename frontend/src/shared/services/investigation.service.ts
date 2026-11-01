import { apiFetch } from "@/lib/api-fetch";
import type {
  ExperimentSummary,
  InvestigationSummary,
  StartInvestigationRequest,
  StartInvestigationResponse,
} from "@/shared/types/investigation.types";

export const investigationService = {
  start: (body: StartInvestigationRequest) =>
    apiFetch<StartInvestigationResponse>("/investigations", {
      method: "POST",
      body: JSON.stringify(body),
    }),

  getById: (id: string) => apiFetch<InvestigationSummary>(`/investigations/${id}`),

  listExperiments: (id: string) =>
    apiFetch<ExperimentSummary[]>(`/investigations/${id}/experiments`),

  listEvents: (id: string) =>
    apiFetch<Array<{
      id: string;
      sequence: number;
      eventType: string;
      payload: Record<string, unknown>;
      createdAt: string;
    }>>(`/investigations/${id}/event-log`),

  proposePatch: (id: string) =>
    apiFetch<{ patchId: string; investigationId: string; status: string; source: string; diff: string }>(
      `/investigations/${id}/patches`,
      { method: "POST", body: JSON.stringify({ generate: true, approved: false }) },
    ),
};
