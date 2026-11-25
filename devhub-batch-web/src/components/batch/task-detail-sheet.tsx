"use client";

import { useState } from "react";
import { Loader2, Pause, Play, Trash2, Zap } from "lucide-react";
import { api } from "@/lib/batch-api";
import { useBatchPolling } from "@/hooks/use-batch-polling";
import { fmtDateTime, fmtDuration, fmtRelative } from "@/lib/batch-format";
import type { TaskDetail } from "@/lib/batch-types";
import { Button } from "@/components/ui/button";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { Separator } from "@/components/ui/separator";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { toast } from "sonner";
import { StatusBadge } from "./status-badge";
import { cn } from "@/lib/utils";

function InfoRow({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex flex-col gap-0.5 py-1.5 sm:flex-row sm:items-baseline sm:gap-3">
      <span className="w-40 shrink-0 text-xs text-muted-foreground">{label}</span>
      <span className="min-w-0 break-words text-sm">{children}</span>
    </div>
  );
}

export function TaskDetailSheet({
  taskId,
  open,
  isAdmin,
  onOpenChange,
  onChanged,
}: {
  taskId: number | null;
  open: boolean;
  isAdmin: boolean;
  onOpenChange: (open: boolean) => void;
  onChanged: () => void;
}) {
  const [expandedLog, setExpandedLog] = useState<{ taskId: number; logId: number } | null>(null);
  const [pendingDelete, setPendingDelete] = useState(false);
  const [busyAction, setBusyAction] = useState<string | null>(null);

  const detail = useBatchPolling<TaskDetail | null>(
    () => (taskId !== null && open ? api<TaskDetail>(`/api/tasks/${taskId}`) : Promise.resolve(null)),
    taskId !== null && open ? 4000 : 3600_000,
  );

  const task = detail.data?.task;
  const logs = detail.data?.recentLogs ?? [];

  async function mutate(action: "pause" | "resume" | "trigger", message: string) {
    if (taskId === null) return;
    setBusyAction(action);
    try {
      await api(`/api/tasks/${taskId}/${action}`, { method: "POST" });
      toast.success(message);
      void detail.refresh();
      onChanged();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "Thao tác thất bại");
    } finally {
      setBusyAction(null);
    }
  }

  async function removeTask() {
    if (taskId === null) return;
    try {
      await api(`/api/tasks/${taskId}`, { method: "DELETE" });
      toast.success(`Đã xóa task #${taskId}`);
      setPendingDelete(false);
      onOpenChange(false);
      onChanged();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "Xóa thất bại");
    }
  }

  return (
    <>
      <Sheet open={open} onOpenChange={onOpenChange}>
        <SheetContent
          side="right"
          className="w-full gap-0 overflow-y-auto p-0 sm:max-w-xl rounded-none sm:rounded-l-2xl"
        >
          <SheetHeader className="border-b border-stone-100 px-5 py-4 text-left">
            <div className="flex flex-wrap items-center gap-3">
              <SheetTitle className="text-lg">{task ? task.taskName : "Chi tiết task"}</SheetTitle>
              {task && <StatusBadge status={task.status} />}
            </div>
            {task && (
              <SheetDescription className="mt-1">
                {task.functionName ?? task.beanClass}
                {task.functionCode ? ` · ${task.functionCode}` : ""}
              </SheetDescription>
            )}
          </SheetHeader>
          {detail.loading && !task ? (
            <div className="flex h-full items-center justify-center">
              <Loader2 className="h-6 w-6 animate-spin text-teal-600" />
            </div>
          ) : task ? (
            <div className="flex min-h-full flex-col">
              <div className="flex-1 space-y-5 px-5 py-5">
                <section className="rounded-2xl border border-stone-200/70 p-4">
                  <h3 className="mb-1 text-sm font-semibold">Lịch chạy (Schedule)</h3>
                  <Separator className="mb-2" />
                  <InfoRow label="Loại lịch (Type)">
                    <span className="font-mono text-xs">{task.scheduleType}</span>
                  </InfoRow>
                  {task.cronExpression && (
                    <InfoRow label="Cron (6 fields)">
                      <span className="font-mono text-xs">{task.cronExpression}</span>
                    </InfoRow>
                  )}
                  {task.scheduledTime && (
                    <InfoRow label="Chạy một lần lúc">
                      <span className="font-mono text-xs">{fmtDateTime(task.scheduledTime)}</span>
                    </InfoRow>
                  )}
                  <InfoRow label="Timezone">
                    <span className="font-mono text-xs">{task.timezone}</span>
                  </InfoRow>
                  <InfoRow label="Misfire policy">
                    <span className="font-mono text-xs">{task.misfirePolicy}</span>
                  </InfoRow>
                  <InfoRow label="Chạy kế tiếp (Next run)">
                    {task.nextRunAt ? (
                      <>
                        {fmtRelative(task.nextRunAt)}
                        <span className="ml-1 text-xs text-muted-foreground">
                          ({fmtDateTime(task.nextRunAt)})
                        </span>
                      </>
                    ) : (
                      "—"
                    )}
                  </InfoRow>
                  <InfoRow label="Lần chạy cuối (Last run)">
                    {task.lastRunAt ? (
                      <>
                        {fmtRelative(task.lastRunAt)}
                        <span className="ml-1 text-xs text-muted-foreground">
                          ({fmtDateTime(task.lastRunAt)})
                        </span>
                      </>
                    ) : (
                      "Chưa chạy lần nào"
                    )}
                  </InfoRow>
                </section>

                <section className="rounded-2xl border border-stone-200/70 p-4">
                  <h3 className="mb-1 text-sm font-semibold">Cấu hình</h3>
                  <Separator className="mb-2" />
                  <InfoRow label="BEAN_CLASS">
                    <span className="break-all font-mono text-xs">{task.beanClass}</span>
                  </InfoRow>
                  <InfoRow label="Timeout">{task.timeoutSeconds}s</InfoRow>
                  <InfoRow label="Requeue khi lỗi">
                    {task.requeueOnFailure ? "Có" : "Không"}
                  </InfoRow>
                  <InfoRow label="Priority">{task.priority}</InfoRow>
                  <InfoRow label="Mô tả">{task.description ?? "—"}</InfoRow>
                  <InfoRow label="Tạo / cập nhật">
                    {fmtDateTime(task.createdAt)}
                    <span className="text-xs text-muted-foreground"> · {fmtDateTime(task.updatedAt)}</span>
                  </InfoRow>
                </section>

                {isAdmin && (
                  <section className="flex flex-wrap gap-2">
                    <Button
                      size="sm"
                      className="rounded-xl"
                      disabled={task.status !== "SCHEDULED" || busyAction !== null}
                      onClick={() => mutate("trigger", "Đã gửi lệnh chạy ngay (NEXT_RUN_AT = now)")}
                    >
                      {busyAction === "trigger" ? (
                        <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                      ) : (
                        <Zap className="mr-2 h-4 w-4" />
                      )}
                      Chạy ngay
                    </Button>
                    <Button
                      size="sm"
                      variant="outline"
                      className="rounded-xl"
                      disabled={task.status !== "SCHEDULED" || busyAction !== null}
                      onClick={() => mutate("pause", "Đã tạm dừng (PAUSED)")}
                    >
                      <Pause className="mr-2 h-4 w-4" />
                      Tạm dừng
                    </Button>
                    <Button
                      size="sm"
                      variant="outline"
                      className="rounded-xl"
                      disabled={task.status !== "PAUSED" || busyAction !== null}
                      onClick={() => mutate("resume", "Đã tiếp tục (SCHEDULED)")}
                    >
                      <Play className="mr-2 h-4 w-4" />
                      Tiếp tục
                    </Button>
                    <Button
                      size="sm"
                      variant="outline"
                      className="ml-auto rounded-xl border-red-200 text-red-600 hover:bg-red-50"
                      disabled={task.status === "RUNNING"}
                      onClick={() => setPendingDelete(true)}
                    >
                      <Trash2 className="mr-2 h-4 w-4" />
                      Xóa
                    </Button>
                  </section>
                )}

                <section>
                  <h3 className="mb-2 text-sm font-semibold">
                    Lịch sử thực thi ({logs.length} lần gần nhất)
                  </h3>
                  <div className="space-y-2">
                    {logs.length === 0 && (
                      <p className="text-sm text-muted-foreground">Chưa có lần chạy nào.</p>
                    )}
                    {logs.map((log) => (
                      <button
                        key={log.id}
                        onClick={() => {
                          if (taskId === null) return;
                          setExpandedLog((current) =>
                            current?.taskId === taskId && current.logId === log.id
                              ? null
                              : { taskId, logId: log.id },
                          );
                        }}
                        className="w-full rounded-xl border border-stone-200/70 bg-white px-3 py-2.5 text-left transition hover:border-teal-300"
                      >
                        <div className="flex flex-wrap items-center gap-2">
                          <StatusBadge status={log.status} className="text-[11px] px-2 py-0" />
                          <span className="text-xs text-muted-foreground">
                            {fmtRelative(log.executedAt)}
                          </span>
                          <span className="ml-auto text-xs tabular-nums text-muted-foreground">
                            {fmtDuration(log.durationMs)}
                          </span>
                        </div>
                        <div className="mt-1 flex flex-wrap items-center gap-x-3 text-[11px] text-muted-foreground">
                          <span>{fmtDateTime(log.executedAt)}</span>
                          <span className="font-mono">worker: {log.workerId ?? "—"}</span>
                        </div>
                        {log.errorMessage && (
                          <p className="mt-1.5 line-clamp-1 text-xs text-red-600">{log.errorMessage}</p>
                        )}
                        {expandedLog?.taskId === taskId && expandedLog.logId === log.id && (
                          <pre
                            className={cn(
                              "mt-2 max-h-64 overflow-y-auto whitespace-pre-wrap rounded-lg bg-stone-950 p-3",
                              "font-mono text-[11px] leading-relaxed text-stone-100",
                            )}
                          >
                            {log.errorMessage ? `ERROR: ${log.errorMessage}\n\n` : ""}
                            {log.result ?? "(no output)"}
                          </pre>
                        )}
                      </button>
                    ))}
                  </div>
                </section>
              </div>
            </div>
          ) : (
            <div className="flex h-full items-center justify-center text-sm text-muted-foreground">
              Chưa chọn task nào
            </div>
          )}
        </SheetContent>
      </Sheet>

      <AlertDialog open={pendingDelete} onOpenChange={setPendingDelete}>
        <AlertDialogContent className="rounded-2xl">
          <AlertDialogHeader>
            <AlertDialogTitle>Xóa task #{taskId}?</AlertDialogTitle>
            <AlertDialogDescription>
              Toàn bộ execution log sẽ bị xóa theo (ON DELETE CASCADE). Hành động không thể hoàn tác.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel className="rounded-lg">Hủy</AlertDialogCancel>
            <AlertDialogAction
              className="rounded-lg bg-red-600 text-white hover:bg-red-700"
              onClick={removeTask}
            >
              Xóa vĩnh viễn
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
