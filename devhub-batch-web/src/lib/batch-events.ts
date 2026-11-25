import { wsUrl } from "./batch-api";
import type { BatchWsEvent } from "./batch-types";

/**
 * Singleton WebSocket bridge to /ws/events (native WS through the sandbox
 * gateway). Data views keep their own polling as the safety net — WS events
 * only trigger faster refreshes.
 */
type Handler = (event: BatchWsEvent) => void;
type StatusListener = (status: SocketStatus) => void;

export type SocketStatus = "connecting" | "connected" | "offline";

let socket: WebSocket | null = null;
let currentToken: string | null = null;
let reconnectTimer: ReturnType<typeof setTimeout> | null = null;
let status: SocketStatus = "offline";
const handlers = new Set<Handler>();
const statusListeners = new Set<StatusListener>();

function setStatus(next: SocketStatus) {
  if (status === next) return;
  status = next;
  statusListeners.forEach((listener) => listener(status));
}

export function getSocketStatus(): SocketStatus {
  return status;
}

export function onBatchEvent(handler: Handler): () => void {
  handlers.add(handler);
  return () => handlers.delete(handler);
}

export function onSocketStatus(listener: StatusListener): () => void {
  statusListeners.add(listener);
  listener(status);
  return () => statusListeners.delete(listener);
}

export function connectBatchEvents(token: string): void {
  if (typeof window === "undefined") return;
  if (socket && currentToken === token && socket.readyState <= WebSocket.OPEN) return;
  disconnectBatchEvents();
  currentToken = token;
  open();
}

function open() {
  if (!currentToken) return;
  setStatus("connecting");
  try {
    socket = new WebSocket(wsUrl(currentToken));
  } catch {
    scheduleReconnect();
    return;
  }
  socket.onopen = () => setStatus("connected");
  socket.onmessage = (message) => {
    try {
      const event = JSON.parse(message.data as string) as BatchWsEvent;
      handlers.forEach((handler) => handler(event));
    } catch {
      /* ignore malformed frames */
    }
  };
  socket.onclose = () => {
    socket = null;
    scheduleReconnect();
  };
  socket.onerror = () => {
    try {
      socket?.close();
    } catch {
      /* noop */
    }
  };
}

function scheduleReconnect() {
  setStatus("offline");
  if (!currentToken) return;
  if (reconnectTimer) clearTimeout(reconnectTimer);
  reconnectTimer = setTimeout(() => {
    reconnectTimer = null;
    open();
  }, 4000);
}

export function disconnectBatchEvents(): void {
  if (reconnectTimer) {
    clearTimeout(reconnectTimer);
    reconnectTimer = null;
  }
  currentToken = null;
  if (socket) {
    try {
      socket.close();
    } catch {
      /* noop */
    }
    socket = null;
  }
  setStatus("offline");
}
