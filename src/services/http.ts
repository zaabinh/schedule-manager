let csrfToken: string | undefined;

const baseUrl = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api/v1";

interface Envelope<T> {
  success: boolean;
  data: T;
  error?: { code: string; message: string; details?: { field: string; message: string }[] };
  meta: { correlationId: string; page?: number; size?: number; totalElements?: number; totalPages?: number };
}

export class ApiClientError extends Error {
  constructor(public readonly status: number, public readonly code: string, message: string) { super(message); }
}

export async function apiRequest<T>(path: string, init: RequestInit = {}): Promise<T> {
  const method = (init.method ?? "GET").toUpperCase();
  const headers = new Headers(init.headers);
  if (init.body && !(init.body instanceof FormData)) headers.set("Content-Type", "application/json");
  if (!["GET", "HEAD", "OPTIONS"].includes(method) && csrfToken) headers.set("X-CSRF-Token", csrfToken);
  const response = await fetch(`${baseUrl}${path}`, { ...init, headers, credentials: "include" });
  csrfToken = response.headers.get("X-CSRF-Token") ?? csrfToken;
  if (response.status === 204) return undefined as T;
  const envelope = await response.json() as Envelope<T>;
  if (!response.ok || !envelope.success) {
    throw new ApiClientError(response.status, envelope.error?.code ?? "REQUEST_FAILED",
      envelope.error?.message ?? "Không thể xử lý yêu cầu.");
  }
  return envelope.data;
}

export async function apiDownload(path: string): Promise<Blob> {
  const response = await fetch(`${baseUrl}${path}`, { credentials: "include" });
  if (!response.ok) throw new ApiClientError(response.status, "DOWNLOAD_FAILED", "Không thể tải tệp.");
  return response.blob();
}

export function clearCsrfToken() { csrfToken = undefined; }
