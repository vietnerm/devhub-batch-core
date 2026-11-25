import type { BatchAuth } from "./batch-types";

/**
 * API client for devhub-batch-api.
 *
 * Sandbox mode (default): every request goes through the local gateway with
 * ?XTransformPort=8080 so only one port is exposed.
 * Standalone mode: set NEXT_PUBLIC_BATCH_API_BASE (e.g. http://api-host:8080)
 * and the client talks to the API origin directly.
 */
const API_PORT = process.env.NEXT_PUBLIC_BATCH_API_PORT ?? "8080";
const API_BASE = process.env.NEXT_PUBLIC_BATCH_API_BASE ?? "";
const USE_GATEWAY = API_BASE === "";

const AUTH_KEY = "devhub-batch.auth";

export class ApiError extends Error {
  status: number;
  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

export function apiUrl(path: string): string {
  if (!USE_GATEWAY) return `${API_BASE}${path}`;
  const separator = path.includes("?") ? "&" : "?";
  return `${path}${separator}XTransformPort=${API_PORT}`;
}

export function getAuth(): BatchAuth | null {
  if (typeof window === "undefined") return null;
  try {
    const raw = window.localStorage.getItem(AUTH_KEY);
    if (!raw) return null;
    const auth = JSON.parse(raw) as BatchAuth;
    if (!auth?.token) return null;
    if (auth.expiresAt && new Date(auth.expiresAt).getTime() < Date.now()) {
      window.localStorage.removeItem(AUTH_KEY);
      return null;
    }
    return auth;
  } catch {
    return null;
  }
}

/* --- React-friendly auth store (useSyncExternalStore compatible) --- */

const authListeners = new Set<() => void>();
let authCache: BatchAuth | null | undefined;

export function authSnapshot(): BatchAuth | null {
  if (authCache === undefined) authCache = getAuth();
  return authCache;
}

function notifyAuthChanged(): void {
  authCache = undefined;
  authListeners.forEach((listener) => listener());
}

export function subscribeAuth(listener: () => void): () => void {
  authListeners.add(listener);
  if (typeof window !== "undefined") {
    const onStorage = (event: StorageEvent) => {
      if (event.key === AUTH_KEY || event.key === null) notifyAuthChanged();
    };
    window.addEventListener("storage", onStorage);
    return () => {
      authListeners.delete(listener);
      window.removeEventListener("storage", onStorage);
    };
  }
  return () => authListeners.delete(listener);
}

export function setAuth(auth: BatchAuth): void {
  window.localStorage.setItem(AUTH_KEY, JSON.stringify(auth));
  notifyAuthChanged();
}

export function clearAuth(): void {
  window.localStorage.removeItem(AUTH_KEY);
  notifyAuthChanged();
}

export async function api<T>(path: string, init?: RequestInit): Promise<T> {
  const auth = getAuth();
  const headers = new Headers(init?.headers);
  if (!headers.has("Content-Type") && init?.body) headers.set("Content-Type", "application/json");
  if (auth) headers.set("Authorization", `Bearer ${auth.token}`);

  const res = await fetch(apiUrl(path), { ...init, headers, cache: "no-store" });
  if (res.status === 401) {
    clearAuth();
    throw new ApiError(401, "Phiên đăng nhập hết hạn (token expired) — vui lòng đăng nhập lại");
  }
  if (!res.ok) {
    let message = `HTTP ${res.status}`;
    try {
      const body = (await res.json()) as { message?: string };
      if (body?.message) message = body.message;
    } catch {
      /* keep default message */
    }
    throw new ApiError(res.status, message);
  }
  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}

export async function login(username: string, password: string): Promise<BatchAuth> {
  const res = await fetch(apiUrl("/api/auth/login"), {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username, password }),
    cache: "no-store",
  });
  if (!res.ok) {
    let message = `HTTP ${res.status}`;
    try {
      const body = (await res.json()) as { message?: string };
      if (body?.message) message = body.message;
    } catch {
      /* keep default */
    }
    throw new ApiError(res.status, message);
  }
  return (await res.json()) as BatchAuth;
}

export function wsUrl(token: string): string {
  const protocol = location.protocol === "https:" ? "wss:" : "ws:";
  if (!USE_GATEWAY) {
    const host = API_BASE.replace(/^https?:\/\//, "").replace(/\/$/, "");
    return `${protocol}//${host}/ws/events?token=${encodeURIComponent(token)}`;
  }
  return `${protocol}//${location.host}/?XTransformPort=${API_PORT}&token=${encodeURIComponent(token)}`;
}
