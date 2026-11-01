import { setAccessToken } from "@/lib/auth-session";

export type LoginResponse = {
  access_token: string;
  token_type: string;
  expires_in: number;
  scope: string;
};

export async function login(username: string, password: string): Promise<LoginResponse> {
  const response = await fetch("/api/v1/auth/login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username, password }),
  });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || "Login failed");
  }
  const body = (await response.json()) as LoginResponse;
  setAccessToken(body.access_token);
  return body;
}
