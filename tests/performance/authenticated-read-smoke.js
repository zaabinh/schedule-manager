import http from "k6/http";
import { check, group } from "k6";

const baseUrl = __ENV.BASE_URL || "http://backend-e2e:8080/api/v1";
const cookieName = "schedule_e2e_session";

export const options = {
  setupTimeout: "2m",
  scenarios: {
    authenticated_reads: {
      executor: "constant-vus",
      vus: Number(__ENV.VUS || 100),
      duration: __ENV.DURATION || "30s",
      gracefulStop: "5s",
    },
  },
  thresholds: {
    "http_req_duration{request_type:read}": ["p(95)<500"],
    "http_req_failed{request_type:read}": ["rate<0.01"],
    checks: ["rate>0.99"],
  },
};

export function setup() {
  const requests = [];
  for (let index = 1; index <= 100; index += 1) {
    requests.push(["POST", `${baseUrl}/auth/login`, JSON.stringify({
      email: "admin.e2e@example.edu.vn", password: "AdminE2E@2026",
    }), { headers: { "Content-Type": "application/json", Origin: "http://localhost:3100",
      "X-Forwarded-For": `198.51.100.${index}` }, tags: { request_type: "setup" } }]);
  }
  const responses = http.batch(requests);
  const cookies = [];
  for (let index = 0; index < responses.length; index += 1) {
    const response = responses[index];
    const session = response.cookies[cookieName]?.[0]?.value;
    if (response.status !== 200 || !session) {
      throw new Error(`Unable to create performance session ${index + 1} (HTTP ${response.status}).`);
    }
    cookies.push(`${cookieName}=${session}`);
  }
  return { cookies };
}

export default function authenticatedReadSmoke(data) {
  const params = { headers: { Cookie: data.cookies[(__VU - 1) % data.cookies.length] }, tags: { request_type: "read" } };

  group("authenticated read APIs", () => {
    const me = http.get(`${baseUrl}/auth/me`, params);
    check(me, { "auth/me returns 200": (response) => response.status === 200 });
    if (me.status !== 200) console.warn(`auth/me failed: HTTP ${me.status} ${me.body}`);

    const dashboard = http.get(`${baseUrl}/dashboard/admin`, params);
    check(dashboard, { "admin dashboard returns 200": (response) => response.status === 200 });
    if (dashboard.status !== 200) console.warn(`dashboard failed: HTTP ${dashboard.status} ${dashboard.body}`);

    const users = http.get(`${baseUrl}/users`, params);
    check(users, { "users list returns 200": (response) => response.status === 200 });
    if (users.status !== 200) console.warn(`users failed: HTTP ${users.status} ${users.body}`);
  });
}
