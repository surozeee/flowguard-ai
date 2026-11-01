import type { GlobalResponse } from "@/shared/types/api.types";
import { resolveApiKeyFromEnv } from "@/lib/api-credentials";
import { clearAccessToken, getAccessToken } from "@/lib/auth-session";

const API_V1 = "/api/v1";

function authHeaders(): Record<string, string> {
  const headers: Record<string, string> = {};
  const token = getAccessToken();
  if (token) {
    headers.Authorization = `Bearer ${token}`;
    return headers;
  }
  const key = resolveApiKeyFromEnv();
  if (key) {
    headers["X-API-Key"] = key;
  }
  return headers;
}

export async function apiFetch<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_V1}${path}`, {
    headers: {
      "Content-Type": "application/json",
      ...authHeaders(),
      ...(init?.headers ?? {}),
    },
    ...init,
  });

  if (!response.ok) {
    if ((response.status === 401 || response.status === 403) && getAccessToken()) {
      clearAccessToken();
      const returnUrl = encodeURIComponent(window.location.pathname + window.location.search);
      window.location.assign(`/login?returnUrl=${returnUrl}`);
    }
    const text = await response.text();
    throw new Error(text || `Request failed (${response.status})`);
  }

  const envelope = (await response.json()) as GlobalResponse<T>;
  return envelope.data;
}

export { API_V1 };
