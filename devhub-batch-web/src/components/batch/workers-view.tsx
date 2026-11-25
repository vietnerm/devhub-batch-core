"use client";

import { Database, Laptop, Server } from "lucide-react";
import { api } from "@/lib/batch-api";
import { useBatchPolling } from "@/hooks/use-batch-polling";
import { fmtDateTime, fmtRelative } from "@/lib/batch-format";
import { onBatchEvent } from "@/lib/batch-events";
import type { WorkerDto } from "@/lib/batch-types";
import { useEffect } from "react";
import { Card, CardContent } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { StatusBadge } from "./status-badge";

export function WorkersView() {
  const workers = useBatchPolling<WorkerDto[]>(() => api<WorkerDto[]>("/api/workers"), 5000);

  // WebSocket 'workers' events refresh instantly.
  useEffect(
    () =>
      onBatchEvent((event) => {
        if (event.type === "workers") void workers.refresh();
      }),
    [workers.refresh],
  );

  if (workers.loading && !workers.data) {
    return (
      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
        {Array.from({ length: 4 }).map((_, i) => (
          <Skeleton key={i} className="h-56 rounded-2xl" />
        ))}
      </div>
    );
  }

  if (workers.error || !workers.data) {
    return (
      <div className="rounded-2xl border border-red-200 bg-red-50 p-5 text-sm text-red-700">
        Không tải được danh sách worker: {workers.error?.message ?? "không xác định"}
      </div>
    );
  }

  return (
    <div className="space-y-5">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Workers</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          Trạng thái node chạy task — heartbeat mỗi 5s, STALE sau 30s không có tín hiệu
        </p>
      </div>

      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
        {workers.data.map((worker) => (
          <Card key={worker.workerId} className="rounded-2xl border-stone-200/70 shadow-none">
            <CardContent className="p-5">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <span className="font-mono text-sm font-semibold">{worker.workerId}</span>
                <StatusBadge status={worker.status} />
              </div>

              <dl className="mt-4 space-y-1.5 text-sm">
                <div className="flex items-center gap-2 text-muted-foreground">
                  <Laptop className="h-3.5 w-3.5 shrink-0" />
                  <span className="truncate">
                    {worker.machineName} · {worker.privateIp}
                  </span>
                </div>
                <div className="flex items-center gap-2 text-muted-foreground">
                  <Database className="h-3.5 w-3.5 shrink-0" />
                  <span className="truncate">
                    {worker.environment} · {worker.location}
                  </span>
                </div>
                <div className="flex items-center gap-2 text-muted-foreground">
                  <Server className="h-3.5 w-3.5 shrink-0" />
                  <span className="truncate">
                    {worker.osName}/{worker.osArch} · {worker.javaVersion}
                  </span>
                </div>
              </dl>

              <div className="mt-4 rounded-xl bg-stone-50 border border-stone-100 px-3 py-2">
                {worker.currentTaskName ? (
                  <>
                    <p className="text-xs text-muted-foreground">Đang chạy</p>
                    <p className="truncate font-mono text-[13px]">{worker.currentTaskName}</p>
                  </>
                ) : (
                  <p className="text-xs text-muted-foreground">Không chạy task nào (idle)</p>
                )}
              </div>

              <p className="mt-3 text-xs text-muted-foreground" title={fmtDateTime(worker.lastHeartbeatAt)}>
                Heartbeat: {fmtRelative(worker.lastHeartbeatAt)}
              </p>
              {worker.lastError && (
                <p className="mt-2 line-clamp-2 rounded-lg bg-amber-50 border border-amber-100 px-2.5 py-1.5 text-xs text-amber-800">
                  {worker.lastError}
                </p>
              )}
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  );
}
