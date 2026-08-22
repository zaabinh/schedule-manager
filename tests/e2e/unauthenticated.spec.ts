import { expect, test } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";

const apiBaseUrl = process.env.E2E_API_BASE_URL ?? "http://localhost:8080/api/v1";

test("khách truy cập dashboard được chuyển về đăng nhập", async ({ page }) => {
  await page.route(`${apiBaseUrl}/auth/me`, route => route.fulfill({
    status: 401, contentType: "application/json",
    body: JSON.stringify({ success: false, error: { code: "UNAUTHENTICATED", message: "Unauthenticated" }, meta: { correlationId: "e2e" } }),
  }));
  await page.goto("/dashboard");
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByRole("heading", { name: "Đăng nhập" })).toBeVisible();
});

test("form đăng nhập dùng được bằng label và bàn phím", async ({ page }) => {
  await page.goto("/login");
  await expect(page.getByRole("img", { name: "Logo Trường THPT số 2 Phan Bội Châu Gia Lai" }).first()).toBeVisible();
  expect(await page.getByText("TRƯỜNG THPT SỐ 2 PHAN BỘI CHÂU GIA LAI", { exact: true }).count()).toBeGreaterThanOrEqual(2);
  await expect(page.locator('link[rel="icon"]')).toHaveAttribute("href", /school-logo\.png/);
  const email = page.getByLabel("Email");
  const password = page.getByLabel("Mật khẩu", { exact: true });
  await email.fill("teacher@example.edu.vn");
  await password.fill("Teacher@2026");
  await expect(password).toHaveAttribute("type", "password");
  await page.getByRole("button", { name: "Hiện mật khẩu" }).click();
  await expect(password).toHaveAttribute("type", "text");
  await expect(page.getByRole("button", { name: "Đăng nhập", exact: true })).toBeEnabled();
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth);
  expect(overflow).toBeFalsy();
});

test("đăng nhập không có accessibility violation nghiêm trọng", async ({ page }) => {
  await page.goto("/login");
  const result = await new AxeBuilder({ page }).analyze();
  expect(result.violations.filter(item => item.impact === "critical" || item.impact === "serious")).toEqual([]);
});
