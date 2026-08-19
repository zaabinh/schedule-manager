import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { spawnSync } from "node:child_process";

const argumentsList = process.argv.slice(2);
const skipCompose = argumentsList.includes("--skip-compose");
const envArgumentIndex = argumentsList.indexOf("--env-file");

if (envArgumentIndex >= 0 && !argumentsList[envArgumentIndex + 1]) {
  throw new Error("--env-file requires a path");
}
const environmentFile = resolve(envArgumentIndex >= 0 ? argumentsList[envArgumentIndex + 1] : ".env.production");

const values = new Map();
for (const rawLine of readFileSync(environmentFile, "utf8").split(/\r?\n/u)) {
  const line = rawLine.trim();
  if (!line || line.startsWith("#")) continue;
  const separator = line.indexOf("=");
  if (separator < 1) throw new Error(`Invalid environment entry (expected KEY=VALUE): ${line}`);
  const key = line.slice(0, separator).trim();
  const value = line.slice(separator + 1).trim();
  if (values.has(key)) throw new Error(`Duplicate environment key: ${key}`);
  values.set(key, value);
}

const errors = [];
const required = [
  "POSTGRES_DB", "POSTGRES_USER", "POSTGRES_PASSWORD", "PUBLIC_WEB_ORIGIN",
  "PUBLIC_API_ORIGIN", "SESSION_COOKIE_NAME", "SESSION_PEPPER", "EMAIL_FROM",
  "SMTP_HOST", "SMTP_PORT", "SMTP_USERNAME", "SMTP_PASSWORD",
];

for (const key of required) {
  if (!values.get(key)?.trim()) errors.push(`Missing required value: ${key}`);
}

const placeholderPattern = /(replace-with|example\.edu\.vn|changeme|change-me|local_password|schedule_local|provider-secret)/iu;
for (const [key, value] of values) {
  if (placeholderPattern.test(value)) errors.push(`${key} still contains an example or placeholder value`);
}

for (const key of ["POSTGRES_PASSWORD", "SESSION_PEPPER", "SMTP_PASSWORD"]) {
  if (values.has(key) && values.get(key).length < 32) errors.push(`${key} must contain at least 32 characters`);
}

for (const key of ["PUBLIC_WEB_ORIGIN", "PUBLIC_API_ORIGIN"]) {
  if (!values.has(key)) continue;
  try {
    const uri = new URL(values.get(key));
    if (uri.protocol !== "https:" || uri.origin !== values.get(key)) {
      errors.push(`${key} must be an absolute HTTPS origin without a path, query, fragment, or trailing slash`);
    }
  } catch {
    errors.push(`${key} must be an absolute HTTPS origin`);
  }
}

if (values.get("PUBLIC_WEB_ORIGIN") === values.get("PUBLIC_API_ORIGIN")) {
  errors.push("PUBLIC_WEB_ORIGIN and PUBLIC_API_ORIGIN must be distinct origins");
}
if (values.has("SESSION_COOKIE_NAME") && !/^__Host-.+/u.test(values.get("SESSION_COOKIE_NAME"))) {
  errors.push("SESSION_COOKIE_NAME must use the __Host- prefix");
}
if (values.has("EMAIL_FROM") && !/^[^@\s]+@[^@\s]+\.[^@\s]+$/u.test(values.get("EMAIL_FROM"))) {
  errors.push("EMAIL_FROM must be a valid email address");
}
if (values.has("SMTP_PORT")) {
  const port = Number(values.get("SMTP_PORT"));
  if (!Number.isInteger(port) || port < 1 || port > 65_535) errors.push("SMTP_PORT must be between 1 and 65535");
}

if (errors.length > 0) {
  console.error("Production configuration preflight FAILED:");
  for (const message of errors) console.error(` - ${message}`);
  process.exit(1);
}

if (!skipCompose) {
  const compose = spawnSync(
    "docker",
    ["compose", "--env-file", environmentFile, "--file", resolve("compose.production.yaml"), "config", "--quiet"],
    { stdio: "inherit", shell: false },
  );
  if (compose.error) throw compose.error;
  if (compose.status !== 0) throw new Error(`Docker Compose configuration validation failed (${compose.status}).`);
}

console.log("Production configuration preflight PASS.");
console.log(`Validated required values, placeholders, secret lengths, __Host- cookie, HTTPS origins, email and SMTP port${skipCompose ? "." : " plus Compose interpolation."}`);
