import { apiFetch } from "@/lib/api-fetch";

export const experimentService = {
  getEvidence: (experimentId: string) =>
    apiFetch<Record<string, unknown>>(`/experiments/${experimentId}/evidence`),
};
