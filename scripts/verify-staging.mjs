const args = process.argv.slice(2);

function option(name, environmentName) {
  const index = args.indexOf(name);
  return index >= 0 ? args[index + 1] : process.env[environmentName];
}

const webOrigin = option("--web-origin", "STAGING_WEB_ORIGIN");
const apiOrigin = option("--api-origin", "STAGING_API_ORIGIN");
const email = process.env.STAGING_ADMIN_EMAIL;
const password = process.env.STAGING_ADMIN_PASSWORD;

for (const [name, value] of Object.entries({ STAGING_WEB_ORIGIN: webOrigin, STAGING_API_ORIGIN: apiOrigin,
  STAGING_ADMIN_EMAIL: email, STAGING_ADMIN_PASSWORD: password })) {
  if (!value) throw new Error(`${name} is required`);
}

for (const [name, value] of [["STAGING_WEB_ORIGIN", webOrigin], ["STAGING_API_ORIGIN", apiOrigin]]) {
  const uri = new URL(value);
  if (uri.protocol !== "https:" || uri.origin !== value) {
    throw new Error(`${name} must be an HTTPS origin without path, query, fragment, or trailing slash`);
  }
}
if (webOrigin === apiOrigin) throw new Error("Staging web and API origins must be distinct");
const webParent = new URL(webOrigin).hostname.split(".").slice(1).join(".");
const apiParent = new URL(apiOrigin).hostname.split(".").slice(1).join(".");
if (!webParent || webParent !== apiParent) {
  throw new Error("Staging web and API must use sibling custom domains so SameSite session cookies work");
}

const failures = [];
function requireCheck(condition, message) {
  if (!condition) failures.push(message);
}

const readinessResponse = await fetch(`${apiOrigin}/actuator/health/readiness`, { redirect: "error" });
requireCheck(readinessResponse.ok, `API readiness returned HTTP ${readinessResponse.status}`);
const readiness = readinessResponse.ok ? await readinessResponse.json() : {};
requireCheck(readiness.status === "UP", "API readiness status is not UP");

const loginPage = await fetch(`${webOrigin}/login`, { redirect: "error" });
requireCheck(loginPage.ok, `Frontend login returned HTTP ${loginPage.status}`);
const headers = loginPage.headers;
const csp = headers.get("content-security-policy") ?? "";
requireCheck(/script-src[^;]*'nonce-[^']+'/u.test(csp), "Frontend CSP is missing a script nonce");
requireCheck(csp.includes("object-src 'none'"), "Frontend CSP is missing object-src 'none'");
requireCheck(csp.includes("frame-ancestors 'none'"), "Frontend CSP is missing frame-ancestors 'none'");
requireCheck(headers.get("x-frame-options")?.toUpperCase() === "DENY", "X-Frame-Options is not DENY");
requireCheck(headers.get("x-content-type-options")?.toLowerCase() === "nosniff", "X-Content-Type-Options is not nosniff");
requireCheck(headers.get("strict-transport-security")?.includes("max-age="), "Public HTTPS endpoint is missing HSTS");
requireCheck(!headers.has("x-powered-by"), "Frontend exposes X-Powered-By");

const loginResponse = await fetch(`${apiOrigin}/api/v1/auth/login`, {
  method: "POST",
  redirect: "error",
  headers: { "content-type": "application/json", origin: webOrigin },
  body: JSON.stringify({ email, password }),
});
requireCheck(loginResponse.ok, `Admin login returned HTTP ${loginResponse.status}`);
const setCookie = loginResponse.headers.get("set-cookie") ?? "";
let csrf = loginResponse.headers.get("x-csrf-token") ?? "";
requireCheck(setCookie.startsWith("__Host-"), "Session cookie does not use the __Host- prefix");
requireCheck(/;\s*Secure(?:;|$)/iu.test(setCookie), "Session cookie is missing Secure");
requireCheck(/;\s*HttpOnly(?:;|$)/iu.test(setCookie), "Session cookie is missing HttpOnly");
requireCheck(/;\s*SameSite=Lax(?:;|$)/iu.test(setCookie), "Session cookie is missing SameSite=Lax");
requireCheck(csrf.length >= 32, "Login did not return a strong CSRF token");

const sessionCookie = setCookie.split(";", 1)[0];
if (loginResponse.ok && sessionCookie) {
  const meResponse = await fetch(`${apiOrigin}/api/v1/auth/me`, {
    headers: { cookie: sessionCookie, origin: webOrigin },
    redirect: "error",
  });
  requireCheck(meResponse.ok, `/auth/me returned HTTP ${meResponse.status}`);
  if (meResponse.ok) {
    csrf = meResponse.headers.get("x-csrf-token") ?? csrf;
    const payload = await meResponse.json();
    requireCheck(payload?.data?.email?.toLowerCase() === email.toLowerCase(), "/auth/me identity differs from staging credential");
  }

  const logoutResponse = await fetch(`${apiOrigin}/api/v1/auth/logout`, {
    method: "POST",
    headers: { cookie: sessionCookie, origin: webOrigin, "x-csrf-token": csrf },
    redirect: "error",
  });
  requireCheck(logoutResponse.status === 204, `Session cleanup returned HTTP ${logoutResponse.status}`);
}

if (failures.length > 0) {
  console.error("Staging verification FAILED:");
  for (const failure of failures) console.error(` - ${failure}`);
  process.exit(1);
}

console.log(JSON.stringify({
  checkedAt: new Date().toISOString(),
  readiness: "UP",
  frontendStatus: loginPage.status,
  authenticatedSession: "PASS",
  csrf: "PASS",
  secureHostCookie: "PASS",
  sessionCleanup: "PASS",
  securityHeaders: "PASS",
}, null, 2));
