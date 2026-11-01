import { apiFetch } from "@/lib/api-fetch";

export interface HealthPayload {
  status: string;
  service: string;
  dependencies: Record<string, string>;
}

export const healthService = {
  check: () => apiFetch<HealthPayload>("/health"),
};
