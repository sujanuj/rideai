// Tiny fetch wrapper for the backend. Requests go to /api/*, which next.config.ts
// proxies to Spring Boot, so the browser never deals with CORS.

export class ApiError extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
    public fields?: Record<string, string>,
  ) {
    super(message);
  }
}

type Options = {
  method?: "GET" | "POST" | "PUT" | "DELETE";
  body?: unknown;
  token?: string | null;
};

export async function api<T>(path: string, { method = "GET", body, token }: Options = {}): Promise<T> {
  const headers: Record<string, string> = {};
  if (body !== undefined) headers["Content-Type"] = "application/json";
  if (token) headers["Authorization"] = `Bearer ${token}`;

  const res = await fetch(`/api${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
    cache: "no-store",
  });

  if (res.status === 204) return null as T;

  const data = await res.json().catch(() => null);
  if (!res.ok) {
    throw new ApiError(
      res.status,
      data?.code ?? (res.status === 401 ? "UNAUTHORIZED" : "ERROR"),
      data?.detail ?? `Request failed (${res.status})`,
      data?.fields,
    );
  }
  return data as T;
}

/** A readable message for any error, including field-level validation messages. */
export function errorMessage(err: unknown): string {
  if (err instanceof ApiError) {
    if (err.fields) {
      return Object.entries(err.fields)
        .map(([field, msg]) => `${field}: ${msg}`)
        .join(", ");
    }
    return err.message;
  }
  return "Can't reach the server. Is the backend running?";
}
