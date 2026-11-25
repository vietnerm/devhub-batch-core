import { format, formatDistanceToNowStrict, parseISO } from "date-fns";
import { vi } from "date-fns/locale";

/**
 * Server timestamps are Asia/Ho_Chi_Minh wall clock (LocalDateTime, no zone
 * suffix). Parsing appends +07:00 so relative times stay correct in any
 * browser timezone; display uses the viewer's local time.
 */
export function parseServerTime(iso: string | null | undefined): Date | null {
  if (!iso) return null;
  try {
    if (iso.includes("+") || iso.endsWith("Z")) return parseISO(iso);
    return parseISO(`${iso}+07:00`);
  } catch {
    return null;
  }
}

export function fmtDateTime(iso: string | null | undefined): string {
  const d = parseServerTime(iso);
  return d ? format(d, "dd/MM/yyyy HH:mm:ss") : "—";
}

export function fmtShortTime(iso: string | null | undefined): string {
  const d = parseServerTime(iso);
  return d ? format(d, "dd/MM HH:mm:ss") : "—";
}

export function fmtRelative(iso: string | null | undefined): string {
  const d = parseServerTime(iso);
  if (!d) return "—";
  return formatDistanceToNowStrict(d, { addSuffix: true, locale: vi });
}

export function fmtDuration(ms: number | null | undefined): string {
  if (ms == null) return "—";
  if (ms < 1000) return `${ms}ms`;
  if (ms < 60_000) return `${(ms / 1000).toFixed(1)}s`;
  const minutes = Math.floor(ms / 60_000);
  const seconds = Math.round((ms % 60_000) / 1000);
  return `${minutes}m ${String(seconds).padStart(2, "0")}s`;
}

export const TASK_STATUS_VI: Record<string, string> = {
  SCHEDULED: "Đã lên lịch",
  RUNNING: "Đang chạy",
  PAUSED: "Tạm dừng",
  STOPPED: "Dừng hẳn",
  ERROR: "Lỗi cấu hình",
  TIMEOUT: "Quá hạn",
  COMPLETED: "Hoàn thành",
};

export const LOG_STATUS_VI: Record<string, string> = {
  SUCCESS: "Thành công",
  FAILED: "Thất bại",
  TIMEOUT: "Quá hạn",
  COMPLETED: "Hoàn thành",
};

export const WORKER_STATUS_VI: Record<string, string> = {
  ONLINE: "Online",
  STALE: "Mất heartbeat",
  OFFLINE: "Offline",
};

export function statusVi(status: string | null | undefined): string {
  if (!status) return "—";
  return TASK_STATUS_VI[status] ?? LOG_STATUS_VI[status] ?? WORKER_STATUS_VI[status] ?? status;
}
