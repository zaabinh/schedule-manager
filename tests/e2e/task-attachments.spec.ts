import { expect, request, test, type APIRequestContext, type APIResponse } from "@playwright/test";

type Envelope<T> = { data: T };
const DOCX = Buffer.from("UEsDBBQAAAAIAA+DFl1fW9FMDQAAAAsAAAARAAAAd29yZC9kb2N1bWVudC54bWyzSclPLs1NzSvRtwMAUEsBAhQAFAAAAAgAD4MWXV9b0UwNAAAACwAAABEAAAAAAAAAAAAAAAAAAAAAAHdvcmQvZG9jdW1lbnQueG1sUEsFBgAAAAABAAEAPwAAADwAAAAAAA==", "base64");
const PDF = Buffer.from("%PDF-1.7\n1 0 obj\n<<>>\nendobj\n%%EOF", "utf8");

async function data<T>(response: APIResponse): Promise<T> {
  if (!response.ok()) throw new Error(`${response.url()} returned ${response.status()}: ${await response.text()}`);
  return (await response.json() as Envelope<T>).data;
}

async function login(context: APIRequestContext, email: string, password: string) {
  const response = await context.post("auth/login", { data: { email, password } });
  await data(response);
  const csrf = response.headers()["x-csrf-token"];
  if (!csrf) throw new Error("Missing CSRF token.");
  return csrf;
}

test.describe("task attachment critical flow", () => {
  test.skip(process.env.E2E_FULL_STACK !== "true", "Requires the isolated full stack.");

  test("admin uploads with retry; assignee downloads; other user is denied", async ({ page }, testInfo) => {
    test.skip(testInfo.project.name !== "chromium", "The attachment business chain runs once.");
    test.setTimeout(120_000);
    const apiBase = `${(process.env.E2E_API_BASE_URL ?? "http://localhost:18080/api/v1").replace(/\/$/, "")}/`;
    const origin = process.env.E2E_WEB_BASE_URL ?? "http://localhost:3100";
    const api = await request.newContext({ baseURL: apiBase, extraHTTPHeaders: { Origin: origin } });
    const suffix = `${Date.now()}`.slice(-7);
    const assigneeEmail = `attachment.${suffix}@example.edu.vn`;
    const otherEmail = `attachment.other.${suffix}@example.edu.vn`;
    const password = "TeacherE2E@2026";
    const taskTitle = `Báo cáo đính kèm ${suffix}`;

    try {
      await data(await api.post("auth/register", { data: { email: assigneeEmail, password, displayName: `Giáo viên tệp ${suffix}` } }));
      await data(await api.post("auth/register", { data: { email: otherEmail, password, displayName: `Giáo viên khác ${suffix}` } }));
      const adminCsrf = await login(api, "admin.e2e@example.edu.vn", "AdminE2E@2026");
      const headers = { "X-CSRF-Token": adminCsrf };
      const pending = await data<Array<{ id: string; email: string; version: number }>>(await api.get("users?status=PENDING"));
      const options = await data<{ departments: Array<{ id: string }>; businessRoles: Array<{ id: string }> }>(await api.get("users/approval-options"));
      const department = options.departments[0];
      const role = options.businessRoles[0];
      if (!department || !role) throw new Error("Approval options are empty.");
      for (const email of [assigneeEmail, otherEmail]) {
        const user = pending.find((item) => item.email === email);
        if (!user) throw new Error(`Pending user not found: ${email}`);
        await data(await api.patch(`users/${user.id}/approval`, { headers, data: { departmentId: department.id, businessRoleIds: [role.id], version: user.version } }));
      }
      const assignee = pending.find((item) => item.email === assigneeEmail)!;
      const year = await data<{ id: string }>(await api.post("academic-years", { headers, data: { name: "2040-2041", startDate: "2040-08-20", isActive: false, generateWeeks: true } }));
      const weeks = await data<Array<{ id: string }>>(await api.get(`academic-years/${year.id}/weeks`));
      const plan = await data<{ id: string }>(await api.post(`weeks/${weeks[2].id}/plan`, { headers }));

      await page.goto("/login");
      await page.getByLabel("Email").fill("admin.e2e@example.edu.vn");
      await page.getByLabel(/Mật khẩu|Máº­t kháº©u/, { exact: true }).fill("AdminE2E@2026");
      await page.getByRole("button", { name: /Đăng nhập|ÄÄƒng nháº­p/, exact: true }).click();
      await expect(page).toHaveURL(/\/admin\/dashboard$/);
      await page.goto("/admin/tasks");
      await page.getByRole("button", { name: "Giao nhiệm vụ" }).click();
      const dialog = page.getByRole("dialog", { name: "Giao nhiệm vụ" });
      await dialog.getByRole("button", { name: "Kế hoạch tuần" }).click();
      await page.locator(`[role="option"][data-value="${plan.id}"]`).click();
      await dialog.getByRole("button", { name: "Người nhận" }).click();
      await page.locator(`[role="option"][data-value="${assignee.id}"]`).click();
      await dialog.getByLabel("Tiêu đề *").fill(taskTitle);
      await dialog.getByLabel("Hạn hoàn thành *").fill("2040-09-01T17:00");
      await dialog.getByLabel("Chọn tệp đính kèm").setInputFiles([
        { name: "Mẫu báo cáo.docx", mimeType: "application/vnd.openxmlformats-officedocument.wordprocessingml.document", buffer: DOCX },
        { name: "Hướng dẫn.pdf", mimeType: "application/pdf", buffer: PDF },
      ]);

      let failedOnce = false;
      await page.route("**/api/v1/tasks/*/attachments", async (route) => {
        if (route.request().method() === "POST" && !failedOnce) {
          failedOnce = true;
          await route.fulfill({ status: 503, contentType: "application/json", headers: { "Access-Control-Allow-Origin": origin, "Access-Control-Allow-Credentials": "true" }, body: JSON.stringify({ success: false, error: { code: "FILE_STORAGE_UNAVAILABLE", message: "Kho tệp tạm thời không khả dụng." }, meta: { correlationId: "e2e" } }) });
        } else await route.continue();
      });
      await dialog.getByRole("button", { name: "Giao nhiệm vụ" }).click();
      await expect(dialog.getByText("Nhiệm vụ đã được tạo.")).toBeVisible();
      await expect(dialog.getByText(/1\/2 tệp tải lên thành công.*1 tệp thất bại/)).toBeVisible();
      await dialog.getByRole("button", { name: "Thử lại" }).click();
      await expect(dialog.getByText(/2\/2 tệp tải lên thành công/)).toBeVisible();
      await dialog.getByRole("button", { name: "Đóng" }).last().click();

      const tasks = await data<Array<{ id: string; title: string }>>(await api.get("tasks"));
      const task = tasks.find((item) => item.title === taskTitle);
      if (!task) throw new Error("Created attachment task was not found.");
      const attachments = await data<Array<{ id: string; originalName: string }>>(await api.get(`tasks/${task.id}/attachments`));
      expect(attachments).toHaveLength(2);

      await page.context().clearCookies();
      await page.goto("/login");
      await page.getByLabel("Email").fill(assigneeEmail);
      await page.getByLabel(/Mật khẩu|Máº­t kháº©u/, { exact: true }).fill(password);
      await page.getByRole("button", { name: /Đăng nhập|ÄÄƒng nháº­p/, exact: true }).click();
      await expect(page).toHaveURL(/\/dashboard$/);
      await page.goto("/assignments");
      const taskCard = page.locator("article").filter({ hasText: taskTitle });
      await expect(taskCard.getByText("Mẫu báo cáo.docx")).toBeVisible();
      await expect(taskCard.getByText("Hướng dẫn.pdf")).toBeVisible();
      await expect(taskCard.getByRole("button", { name: /Xóa tệp/ })).toHaveCount(0);
      const downloadEvent = page.waitForEvent("download");
      await taskCard.getByRole("button", { name: "Tải xuống" }).first().click();
      const downloaded = await downloadEvent;
      expect(downloaded.suggestedFilename()).toMatch(/\.(docx|pdf)$/);
      await taskCard.getByRole("button", { name: "Đánh dấu hoàn thành" }).click();
      await expect(taskCard).toContainText(/Đã hoàn thành|ÄÃ£ hoÃ n thÃ nh/);

      const otherApi = await request.newContext({ baseURL: apiBase, extraHTTPHeaders: { Origin: origin } });
      try {
        await login(otherApi, otherEmail, password);
        const denied = await otherApi.get(`task-attachments/${attachments[0].id}/download`);
        expect(denied.status()).toBe(403);
      } finally { await otherApi.dispose(); }

      await page.context().clearCookies();
      await page.goto("/login");
      await page.getByLabel("Email").fill("admin.e2e@example.edu.vn");
      await page.getByLabel(/Mật khẩu|Máº­t kháº©u/, { exact: true }).fill("AdminE2E@2026");
      await page.getByRole("button", { name: /Đăng nhập|ÄÄƒng nháº­p/, exact: true }).click();
      await expect(page).toHaveURL(/\/admin\/dashboard$/);
      await page.goto("/admin/tasks");
      const adminCard = page.locator("article").filter({ hasText: taskTitle });
      await expect(adminCard).toContainText(/Đã hoàn thành|ÄÃ£ hoÃ n thÃ nh/);
      await adminCard.getByRole("button", { name: /Quản lý tệp đính kèm/ }).click();
      await expect(adminCard.getByRole("button", { name: /Xóa tệp/ })).toHaveCount(2);
    } finally { await api.dispose(); }
  });
});
