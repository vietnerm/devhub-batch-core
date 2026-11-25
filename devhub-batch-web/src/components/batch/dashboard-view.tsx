"use client";

import { AlertTriangle, Activity, CheckCircle2, ListChecks, Server, TrendingUp } from "lucide-react";
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { api } from "@/lib/batch-api";
import { useBatchPolling } from "@/hooks/use-batch-polling";
import { fmtDuration, fmtRelative, fmtShortTime } from "@/lib/batch-format";
import type { LogDto, StatsSummary, TimelinePoint } from "@/lib/batch-types";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { Progress } from "@/components/ui/progress";
import { StatusBadge } from "./status-badge";

function KpiCard({
  icon,
  label,
  value,
  hint,
  tone,
}: {
  icon: React.ReactNode;
  label: string;
  value: React.ReactNode;
  hint?: string;
  tone: "teal" | "emerald" | "amber" | "red" | "stone";
}) {
  const toneClasses: Record<string, string> = {
    teal: "bg-teal-50 text-teal-700",
    emerald: "bg-emerald-50 text-emerald-700",
    amber: "bg-amber-50 text-amber-700",
    red: "bg-red-50 text-red-600",
    stone: "bg-stone-100 text-stone-600",
  };
  return (
    <Card className="rounded-2xl border-stone-200/70 shadow-none">
      <CardContent className="p-5">
        <div className="flex items-center gap-2">
          <div className={`flex h-8 w-8 items-center justify-center rounded-xl ${toneClasses[tone]}`}>
            {icon}
          </div>
          <span className="text-sm text-muted-foreground">{label}</span>
        </div>
        <div className="mt-3 text-2xl font-semibold tracking-tight">{value}</div>
        {hint && <div className="mt-1 text-xs text-muted-foreground">{hint}</div>}
      </CardContent>
    </Card>
  );
}

export function DashboardView({ onOpenTask }: { onOpenTask: (id: number) => void }) {
  const summary = useBatchPolling<StatsSummary>(() => api("/api/stats/summary"), 5000);
  const timeline = useBatchPolling<TimelinePoint[]>(() => api("/api/stats/timeline?hours=24"), 10000);
  const latestFailure = useBatchPolling<LogDto | null>(() => api("/api/stats/latest-failure"), 8000);

  if (summary.loading && !summary.data) {
    return (
      <div className="grid gap-4 md:grid-cols-5">
        {Array.from({ length: 5 }).map((_, i) => (
          <Skeleton key={i} className="h-32 rounded-2xl" />
        ))}
      </div>
    );
  }

  if (summary.error || !summary.data) {
    return (
      <div className="rounded-2xl border border-red-200 bg-red-50 p-5 text-sm text-red-700">
        Không tải được số liệu: {summary.error?.message ?? "không xác định"} — đang thử lại tự động
        (polling 5s)...
      </div>
    );
  }

  const s = summary.data;
  const maxFailureCount = Math.max(1, ...s.topFailures.map((f) => f.count));

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Dashboard</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          Tổng quan vận hành batch — dữ liệu làm mới tự động qua WebSocket + polling
        </p>
      </div>

      <div className="grid gap-4 grid-cols-2 md:grid-cols-3 xl:grid-cols-5">
        <KpiCard
          icon={<Activity className="h-4 w-4" />}
          label="Đang chạy (Running)"
          value={s.runningNow}
          tone="amber"
          hint={`Tổng ${Object.values(s.tasksByStatus).reduce((a, b) => a + b, 0)} task`}
        />
        <KpiCard
          icon={<ListChecks className="h-4 w-4" />}
          label="Lượt chạy 24h"
          value={s.executions24h}
          tone="teal"
          hint={`Trung bình ${fmtDuration(s.avgDurationMs)} / lần`}
        />
        <KpiCard
          icon={<CheckCircle2 className="h-4 w-4" />}
          label="Tỷ lệ thành công"
          value={`${s.successRatePct}%`}
          tone="emerald"
          hint={`${s.success24h} thành công`}
        />
        <KpiCard
          icon={<AlertTriangle className="h-4 w-4" />}
          label="Lỗi / Quá hạn 24h"
          value={`${s.failed24h} / ${s.timeout24h}`}
          tone="red"
        />
        <KpiCard
          icon={<Server className="h-4 w-4" />}
          label="Worker online"
          value={s.workersOnline}
          tone="stone"
          hint={`STALE ${s.workersByStatus["STALE"] ?? 0} · OFFLINE ${s.workersByStatus["OFFLINE"] ?? 0}`}
        />
      </div>

      <Card className="rounded-2xl border-stone-200/70 shadow-none">
        <CardHeader className="pb-2">
          <CardTitle className="flex items-center gap-2 text-base">
            <TrendingUp className="h-4 w-4 text-teal-600" />
            Số lần chạy theo giờ — 24 giờ qua
          </CardTitle>
          <CardDescription>Thành công / Thất bại / Quá hạn (execution history)</CardDescription>
        </CardHeader>
        <CardContent className="h-72 px-2 pb-4">
          {timeline.loading && !timeline.data ? (
            <Skeleton className="h-full w-full rounded-xl" />
          ) : (
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={timeline.data ?? []} margin={{ top: 8, right: 16, left: 0, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#e7e5e4" />
                <XAxis dataKey="hour" tick={{ fontSize: 11 }} interval={1} tickLine={false} axisLine={false} />
                <YAxis tick={{ fontSize: 11 }} tickLine={false} axisLine={false} allowDecimals={false} />
                <Tooltip
                  contentStyle={{ borderRadius: 12, border: "1px solid #e7e5e4", fontSize: 12 }}
                  formatter={(value: number, name: string) => [value, name]}
                />
                <Legend wrapperStyle={{ fontSize: 12 }} />
                <Bar dataKey="success" name="Thành công" stackId="runs" fill="#10b981" radius={[0, 0, 0, 0]} />
                <Bar dataKey="failed" name="Thất bại" stackId="runs" fill="#ef4444" />
                <Bar dataKey="timeout" name="Quá hạn" stackId="runs" fill="#f59e0b" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          )}
        </CardContent>
      </Card>

      <div className="grid gap-4 lg:grid-cols-2">
        <Card className="rounded-2xl border-stone-200/70 shadow-none">
          <CardHeader className="pb-3">
            <CardTitle className="text-base">Top task lỗi (24 giờ)</CardTitle>
            <CardDescription>Tổng hợp FAILED + TIMEOUT theo task</CardDescription>
          </CardHeader>
          <CardContent className="space-y-3">
            {s.topFailures.length === 0 && (
              <p className="text-sm text-muted-foreground">Không có lỗi nào trong 24 giờ qua.</p>
            )}
            {s.topFailures.map((failure) => (
              <div key={failure.taskName}>
                <div className="flex items-center justify-between text-sm">
                  <span className="font-mono text-[13px]">{failure.taskName}</span>
                  <span className="font-semibold text-red-600">{failure.count}</span>
                </div>
                <Progress
                  value={(failure.count / maxFailureCount) * 100}
                  className="mt-1.5 h-1.5 [&>div]:bg-red-400"
                />
              </div>
            ))}
          </CardContent>
        </Card>

        <Card className="rounded-2xl border-stone-200/70 shadow-none">
          <CardHeader className="pb-3">
            <CardTitle className="text-base">Lỗi mới nhất (Latest failure)</CardTitle>
            <CardDescription>Lần chạy FAILED/TIMEOUT gần nhất trên toàn hệ thống</CardDescription>
          </CardHeader>
          <CardContent>
            {!latestFailure.data ? (
              <p className="text-sm text-muted-foreground">Không có lỗi nào gần đây.</p>
            ) : (
              <button
                onClick={() => onOpenTask(latestFailure.data!.taskId)}
                className="w-full rounded-xl border border-stone-200 bg-stone-50/60 p-3 text-left transition hover:border-teal-300 hover:bg-teal-50/40"
              >
                <div className="flex items-center justify-between gap-2">
                  <span className="font-mono text-[13px] font-medium">
                    {latestFailure.data.taskName ?? `Task #${latestFailure.data.taskId}`}
                  </span>
                  <StatusBadge status={latestFailure.data.status} />
                </div>
                <p className="mt-2 line-clamp-2 text-xs text-red-700">
                  {latestFailure.data.errorMessage ?? latestFailure.data.result ?? "—"}
                </p>
                <p className="mt-2 text-xs text-muted-foreground">
                  {fmtRelative(latestFailure.data.executedAt)} · {fmtShortTime(latestFailure.data.executedAt)} ·{" "}
                  worker {latestFailure.data.workerId ?? "—"}
                </p>
              </button>
            )}
          </CardContent>
        </Card>
      </div>

      <Card className="rounded-2xl border-stone-200/70 shadow-none">
        <CardHeader className="pb-3">
          <CardTitle className="text-base">Trạng thái task</CardTitle>
          <CardDescription>Phân bố trạng thái hiện tại của toàn bộ task</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-wrap gap-2">
          {Object.entries(s.tasksByStatus).map(([status, count]) => (
            <div
              key={status}
              className="flex items-center gap-2 rounded-xl border border-stone-200 bg-white px-3 py-1.5"
            >
              <StatusBadge status={status} />
              <span className="text-sm font-semibold">{count}</span>
            </div>
          ))}
        </CardContent>
      </Card>
    </div>
  );
}
