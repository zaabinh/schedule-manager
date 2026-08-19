import { expect, request, test, type APIRequestContext, type APIResponse } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";

type ApiEnvelope<T> = { data: T };
type MailpitMessage = { Subject: string; To: Array<{ Address: string }> };

async function apiData<T>(response: APIResponse): Promise<T> {
  if (!response.ok()) throw new Error(`${response.url()} returned ${response.status()}: ${await response.text()}`);
  return (await response.json() as ApiEnvelope<T>).data;
}

async function loginApi(context: APIRequestContext, email: string, password: string) {
  const response = await context.post("auth/login", { data: { email, password } });
  await apiData(response);
  const csrf = response.headers()["x-csrf-token"];
  if (!csrf) throw new Error("Login response did not include X-CSRF-Token.");
  return csrf;
}

async function hasMail(context: APIRequestContext, recipient: string, subject: string) {
  const response = await context.get("messages");
  if (!response.ok()) throw new Error(`Mailpit messages returned ${response.status()}: ${await response.text()}`);
  const payload = await response.json() as { messages: MailpitMessage[] };
  return payload.messages.some(message => message.Subject.includes(subject)
    && message.To.some(address => address.Address.toLowerCase() === recipient.toLowerCase()));
}

test.describe("authenticated production-critical smoke", () => {
  test.skip(process.env.E2E_FULL_STACK !== "true", "Requires the isolated PostgreSQL and backend E2E stack.");

  test("bootstrap admin can authenticate and open protected administration pages", async ({ page }) => {
    await page.goto("/login");
    await page.getByLabel("Email").fill("admin.e2e@example.edu.vn");
    await page.getByLabel("Mật khẩu", { exact: true }).fill("AdminE2E@2026");
    await page.getByRole("button", { name: "Đăng nhập", exact: true }).click();

    await expect(page).toHaveURL(/\/admin\/dashboard$/, { timeout: 15_000 });
    await expect(page.getByRole("heading", { name: "Tổng quan quản trị" })).toBeVisible();

    await page.goto("/admin/users");
    await expect(page).toHaveURL(/\/admin\/users$/);
    await expect(page.getByRole("heading", { name: "Người dùng" })).toBeVisible();
    await expect(page.getByText("admin.e2e@example.edu.vn")).toBeVisible();

    const result = await new AxeBuilder({ page }).analyze();
    expect(result.violations.filter(item => item.impact === "critical" || item.impact === "serious")).toEqual([]);
  });

  test("teacher registration requires approval before login and exposes the approved identity", async ({ page }, testInfo) => {
    test.setTimeout(60_000);
    const suffix = testInfo.project.name.replace(/[^a-z0-9]/gi, "-").toLowerCase();
    const email = `teacher.${suffix}@example.edu.vn`;
    const displayName = `Giáo viên E2E ${suffix}`;
    const password = "TeacherE2E@2026";

    await page.goto("/register");
    await page.getByLabel("Họ và tên *").fill(displayName);
    await page.getByLabel("Email *").fill(email);
    await page.getByLabel("Mật khẩu *").fill(password);
    await page.getByRole("button", { name: "Gửi đăng ký" }).click();
    await expect(page.getByRole("heading", { name: "Đăng ký thành công" })).toBeVisible();

    await page.goto("/login");
    await page.getByLabel("Email").fill(email);
    await page.getByLabel("Mật khẩu", { exact: true }).fill(password);
    await page.getByRole("button", { name: "Đăng nhập", exact: true }).click();
    await expect(page.getByRole("alert")).toBeVisible();
    await expect(page).toHaveURL(/\/login$/);

    await page.getByLabel("Email").fill("admin.e2e@example.edu.vn");
    await page.getByLabel("Mật khẩu", { exact: true }).fill("AdminE2E@2026");
    await page.getByRole("button", { name: "Đăng nhập", exact: true }).click();
    await expect(page).toHaveURL(/\/admin\/dashboard$/, { timeout: 15_000 });

    await page.goto("/admin/users");
    const row = page.getByRole("row").filter({ hasText: email });
    await expect(row).toBeVisible();
    await row.getByRole("button", { name: "Xem & duyệt" }).click();
    const dialog = page.getByRole("dialog", { name: "Xem và phê duyệt tài khoản" });
    await dialog.getByLabel("Phòng ban *").selectOption({ label: "Văn phòng E2E" });
    await dialog.getByLabel("Giáo viên", { exact: true }).check();
    await dialog.getByRole("button", { name: "Phê duyệt", exact: true }).click();
    await expect(dialog).toBeHidden();
    await expect(row).toContainText("Đang hoạt động");

    await page.getByRole("button", { name: "Mở menu tài khoản" }).click();
    await page.getByRole("menuitem", { name: "Đăng xuất" }).click();
    await expect(page).toHaveURL(/\/login$/);

    await page.getByLabel("Email").fill(email);
    await page.getByLabel("Mật khẩu", { exact: true }).fill(password);
    await page.getByRole("button", { name: "Đăng nhập", exact: true }).click();
    await expect(page).toHaveURL(/\/dashboard$/, { timeout: 15_000 });

    await page.goto("/profile");
    await expect(page.getByRole("heading", { name: "Hồ sơ của tôi" })).toBeVisible();
    await expect(page.getByRole("heading", { name: displayName, exact: true })).toBeVisible();
    const profileDetails = page.locator("[data-profile-details]");
    await expect(profileDetails.getByText(email, { exact: true })).toBeVisible();
    await expect(profileDetails.getByText("Văn phòng E2E", { exact: true })).toBeVisible();
    await expect(profileDetails.getByText("Giáo viên", { exact: true })).toBeVisible();

    const result = await new AxeBuilder({ page }).analyze();
    expect(result.violations.filter(item => item.impact === "critical" || item.impact === "serious")).toEqual([]);
  });

  test("published plan flows through assignment, reminder, conversation and Excel export", async ({ page }, testInfo) => {
    test.skip(testInfo.project.name !== "chromium", "The full business chain runs once; mobile is covered by the focused authenticated tests.");
    test.setTimeout(90_000);

    const apiBaseUrl = process.env.E2E_API_BASE_URL ?? "http://localhost:18080/api/v1";
    const api = await request.newContext({
      baseURL: `${apiBaseUrl.replace(/\/$/, "")}/`,
      extraHTTPHeaders: { Origin: process.env.E2E_WEB_BASE_URL ?? "http://localhost:3100" },
    });
    const mailpitBaseUrl = process.env.E2E_MAILPIT_BASE_URL ?? "http://localhost:18025/api/v1";
    const mailpit = await request.newContext({ baseURL: `${mailpitBaseUrl.replace(/\/$/, "")}/` });
    const teacherEmail = "business-chain.teacher@example.edu.vn";
    const teacherPassword = "TeacherE2E@2026";
    const teacherName = "Giáo viên chuỗi nghiệp vụ";
    const eventTitle = "Họp kiểm thử production readiness";
    const taskTitle = "Hoàn tất biên bản kiểm thử E2E";
    const conversationSubject = "Xác nhận kết quả kiểm thử E2E";

    try {
      await apiData(await api.post("auth/register", { data: { email: teacherEmail, password: teacherPassword, displayName: teacherName } }));
      const adminCsrf = await loginApi(api, "admin.e2e@example.edu.vn", "AdminE2E@2026");
      const mutation = { headers: { "X-CSRF-Token": adminCsrf } };

      const users = await apiData<Array<{ id: string; email: string; version: number }>>(await api.get("users?status=PENDING"));
      const teacher = users.find(user => user.email === teacherEmail);
      if (!teacher) throw new Error("The registered E2E teacher was not returned by the pending-user API.");
      const approval = await apiData<{ departments: Array<{ id: string; name: string }>; businessRoles: Array<{ id: string; name: string }> }>(await api.get("users/approval-options"));
      const department = approval.departments.find(item => item.name.includes("E2E")) ?? approval.departments[0];
      const role = approval.businessRoles.find(item => item.name === "Giáo viên") ?? approval.businessRoles[0];
      if (!department || !role) throw new Error("Bootstrap organization data is incomplete.");
      await apiData(await api.patch(`users/${teacher.id}/approval`, {
        ...mutation, data: { departmentId: department.id, businessRoleIds: [role.id], version: teacher.version },
      }));

      const year = await apiData<{ id: string }>(await api.post("academic-years", {
        ...mutation, data: { name: "2031-2032", startDate: "2031-08-18", isActive: true, generateWeeks: true },
      }));
      const weeks = await apiData<Array<{ id: string; startDate: string }>>(await api.get(`academic-years/${year.id}/weeks`));
      const week = weeks[2];
      if (!week) throw new Error("The generated academic year did not contain the first study week.");
      const plan = await apiData<{ id: string }>(await api.post(`weeks/${week.id}/plan`, mutation));
      const event = await apiData<{ id: string }>(await api.post(`weekly-plans/${plan.id}/events`, {
        ...mutation,
        data: { content: eventTitle, startDate: week.startDate, session: "MORNING", startTime: "09:00", endTime: "10:00", location: "Phòng họp E2E" },
      }));
      const currentDraft = await apiData<{ version: number }>(await api.get(`weeks/${week.id}/plan`));
      await apiData(await api.post(`weekly-plans/${plan.id}/publish`, {
        headers: { ...mutation.headers, "Idempotency-Key": "e2e-business-chain-publish" },
        data: { version: currentDraft.version, publishWithWarnings: true },
      }));
      await apiData(await api.post(`weekly-plans/${plan.id}/events`, {
        ...mutation,
        data: { content: "Cập nhật có gửi email E2E", startDate: week.startDate, session: "AFTERNOON",
          startTime: "14:00", endTime: "15:00", notifyWebsite: true, notifyEmail: true },
      }));
      await apiData(await api.post("tasks", {
        ...mutation,
        data: { weeklyPlanId: plan.id, assigneeUserId: teacher.id, title: taskTitle, dueAt: "2031-09-01T10:00:00Z" },
      }));
      const teacherApi = await request.newContext({
        baseURL: `${apiBaseUrl.replace(/\/$/, "")}/`,
        extraHTTPHeaders: { Origin: process.env.E2E_WEB_BASE_URL ?? "http://localhost:3100" },
      });
      try {
        const teacherCsrf = await loginApi(teacherApi, teacherEmail, teacherPassword);
        await apiData(await teacherApi.post(`events/${event.id}/reminders`, {
          headers: { "X-CSRF-Token": teacherCsrf },
          data: { preset: "CUSTOM", remindAt: new Date(Date.now() + 3_000).toISOString() },
        }));
      } finally {
        await teacherApi.dispose();
      }

      await page.goto("/login");
      await page.getByLabel("Email").fill("admin.e2e@example.edu.vn");
      await page.getByLabel("Mật khẩu", { exact: true }).fill("AdminE2E@2026");
      await page.getByRole("button", { name: "Đăng nhập", exact: true }).click();
      await expect(page).toHaveURL(/\/admin\/dashboard$/);
      await page.goto("/admin/weekly-plans");
      const planCard = page.locator("article").filter({ hasText: "Tuần 1" }).first();
      await expect(planCard).toContainText("Đã công bố");
      const downloadPromise = page.waitForEvent("download");
      await planCard.getByRole("button", { name: "Excel" }).click();
      const download = await downloadPromise;
      expect(download.suggestedFilename()).toMatch(/\.xlsx$/);
      const stream = await download.createReadStream();
      const firstChunk = await new Promise<Buffer>((resolve, reject) => {
        stream.once("data", chunk => resolve(Buffer.from(chunk)));
        stream.once("error", reject);
      });
      expect(firstChunk.subarray(0, 2).toString("ascii")).toBe("PK");

      await page.getByRole("button", { name: "Mở menu tài khoản" }).click();
      await page.getByRole("menuitem", { name: "Đăng xuất" }).click();
      await page.getByLabel("Email").fill(teacherEmail);
      await page.getByLabel("Mật khẩu", { exact: true }).fill(teacherPassword);
      await page.getByRole("button", { name: "Đăng nhập", exact: true }).click();
      await expect(page).toHaveURL(/\/dashboard$/);

      await page.goto("/weekly-plan");
      const eventButton = page.getByRole("button", { name: new RegExp(eventTitle) });
      await expect(eventButton).toBeVisible();
      await eventButton.click();
      await page.getByRole("button", { name: "Nhắc tôi qua email" }).click();
      await page.getByRole("button", { name: "Tạo nhắc lịch" }).click();
      await page.goto("/reminders");
      await expect(page.getByRole("heading", { name: eventTitle }).first()).toBeVisible();

      await page.goto("/assignments");
      const taskCard = page.locator("article").filter({ hasText: taskTitle });
      await expect(taskCard).toBeVisible();
      await taskCard.getByRole("button", { name: "Đánh dấu hoàn thành" }).click();
      await expect(taskCard).toContainText("Đã hoàn thành");

      await page.goto("/conversations");
      await page.getByRole("button", { name: "+ Tạo trao đổi mới" }).click();
      const dialog = page.getByRole("dialog", { name: "Tạo trao đổi mới" });
      await dialog.getByLabel("Chủ đề *").fill(conversationSubject);
      await dialog.getByLabel("Nội dung *").fill("Chuỗi nghiệp vụ đã hoàn tất trên môi trường E2E.");
      await dialog.getByRole("button", { name: "Gửi trao đổi" }).click();
      await expect(page.getByText(conversationSubject, { exact: true }).first()).toBeVisible();

      await expect.poll(() => hasMail(mailpit, teacherEmail, "Kế hoạch tuần đã cập nhật"), {
        message: "Expected the published-plan update email in Mailpit.", timeout: 15_000,
      }).toBe(true);
      await expect.poll(() => hasMail(mailpit, teacherEmail, `Nhắc lịch: ${eventTitle}`), {
        message: "Expected the due reminder email in Mailpit.", timeout: 15_000,
      }).toBe(true);

      const accessibility = await new AxeBuilder({ page }).analyze();
      expect(accessibility.violations.filter(item => item.impact === "critical" || item.impact === "serious")).toEqual([]);
      expect(event.id).toBeTruthy();
    } finally {
      await mailpit.dispose();
      await api.dispose();
    }
  });
});
