import { expect, test } from "@playwright/test";

async function signInAsOps(page: import("@playwright/test").Page, request: import("@playwright/test").APIRequestContext) {
  const gateway = process.env.VITE_API_GATEWAY_URL ?? "http://localhost:8088";
  const login = await request.post(`${gateway}/api/v1/auth/login`, {
    data: { username: "ops", password: "ops123" },
  });
  expect(login.ok()).toBeTruthy();
  const body = (await login.json()) as { access_token: string };
  expect(body.access_token).toBeTruthy();
  await page.addInitScript((token: string) => {
    sessionStorage.setItem("flowguard.access_token", token);
  }, body.access_token);
}

test.describe("public pages", () => {
  test("/ home", async ({ page }) => {
    await page.goto("/");
    await expect(page.getByRole("heading", { level: 1, name: /FlowGuard/i })).toBeVisible();
    await expect(page.getByRole("heading", { level: 2, name: /FlowGuard AI — LedgerGuard/i })).toBeVisible();
  });

  test("/present", async ({ page }) => {
    await page.goto("/present");
    await expect(page.getByRole("heading", { name: /FlowGuard AI — LedgerGuard/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /Next/i })).toBeVisible();
    await page.getByRole("button", { name: /Next/i }).click();
    await expect(page.getByRole("heading", { name: /What this system is for/i })).toBeVisible();
  });

  test("/investigate", async ({ page }) => {
    await page.goto("/investigate");
    await expect(page.getByRole("heading", { name: /New investigation/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /Start investigation/i })).toBeVisible();
  });

  test("/login", async ({ page }) => {
    await page.goto("/login");
    await expect(page.getByRole("heading", { name: /Sign in/i })).toBeVisible();
    await expect(page.getByLabel(/Username/i)).toBeVisible();
  });

  test("/evaluations", async ({ page }) => {
    await page.goto("/evaluations");
    await expect(page.getByRole("heading", { name: /Evaluations/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /Run evaluation/i })).toBeVisible();
  });

  test("/investigations/:id", async ({ page }) => {
    await page.goto("/investigations/00000000-0000-0000-0000-000000000000");
    await expect(page.getByRole("heading", { name: /Investigation 00000000/i })).toBeVisible();
  });
});

test.describe("auth guards", () => {
  for (const path of ["/ops", "/dashboard", "/transactions/new", "/transactions/saga"]) {
    test(`${path} redirects anonymous users to sign in`, async ({ page }) => {
      await page.goto(path);
      await expect(page).toHaveURL(/\/login/);
      await expect(page.getByRole("heading", { name: /Sign in/i })).toBeVisible();
    });
  }
});

test.describe("authenticated pages", () => {
  test("ops, dashboard, credit create, and transfer after gateway login", async ({ page, request }) => {
    await signInAsOps(page, request);

    await page.goto("/ops");
    await expect(
      page.getByRole("heading", { name: /Operations — inbox, outbox & gateway/i }),
    ).toBeVisible({ timeout: 60_000 });

    await page.goto("/dashboard");
    await expect(page.getByRole("heading", { name: /Enterprise processing dashboard/i })).toBeVisible({
      timeout: 60_000,
    });

    await page.goto("/transactions/new");
    await expect(page.getByRole("heading", { name: /Create credit/i })).toBeVisible();
    await page.getByRole("button", { name: /^Create credit$/ }).click();
    await expect(page.getByText(/paymentId/i)).toBeVisible({ timeout: 60_000 });
    await page.getByRole("button", { name: /Replay same key/i }).click();
    await expect(page.getByText(/identical payment id \(idempotent replay\)/i)).toBeVisible({ timeout: 60_000 });

    await page.goto("/transactions/saga");
    await expect(page.getByRole("heading", { name: /Create transfer/i })).toBeVisible();
    await page.getByRole("button", { name: /^Create transfer$/ }).click();
    await expect(page.getByText(/Transfer accepted|payment/i)).toBeVisible({ timeout: 90_000 });
  });

  test("investigate starts and shows duplicate-credit guard timeline", async ({ page, request }) => {
    await signInAsOps(page, request);
    await page.goto("/investigate");
    await page.getByRole("button", { name: /Start investigation/i }).click();
    await expect(page).toHaveURL(/\/investigations\/[0-9a-f-]{8,}/, { timeout: 60_000 });
    await expect(page.getByRole("heading", { name: /Investigation /i })).toBeVisible();
    await expect(page.getByRole("heading", { name: /Live timeline/i })).toBeVisible();
    await expect(page.getByRole("heading", { name: /What Meta said|Live timeline/i })).toBeVisible();
  });
});
