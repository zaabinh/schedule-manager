import { defineConfig, devices } from "@playwright/test";

const apiBaseUrl = process.env.E2E_API_BASE_URL ?? "http://localhost:8080/api/v1";
const webBaseUrl = process.env.E2E_WEB_BASE_URL ?? "http://localhost:3000";
const webPort = new URL(webBaseUrl).port || "3000";

export default defineConfig({
  testDir: "./tests/e2e",
  fullyParallel: true,
  retries: process.env.CI ? 2 : 0,
  reporter: process.env.CI ? "github" : "list",
  use: { baseURL: webBaseUrl, trace: "on-first-retry" },
  projects: [
    { name: "chromium", use: { ...devices["Desktop Chrome"] } },
    { name: "mobile-chromium", use: { ...devices["Pixel 7"] } },
  ],
  webServer: process.env.E2E_EXTERNAL_WEB === "true" ? undefined : {
    command: `npm run dev -- --port ${webPort}`,
    url: `${webBaseUrl}/login`,
    reuseExistingServer: !process.env.CI && process.env.E2E_FULL_STACK !== "true",
    timeout: 120_000,
    env: { NEXT_PUBLIC_API_BASE_URL: apiBaseUrl },
  },
});
