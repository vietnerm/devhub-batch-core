"use client";

import { useEffect, useState } from "react";
import {
  ChevronLeft,
  ChevronRight,
  Clock,
  MoreHorizontal,
  Pause,
  Play,
  Plus,
  Search,
  Trash2,
  Zap,
} from "lucide-react";
import { api, ApiError } from "@/lib/batch-api";
import { useBatchPolling } from "@/hooks/use-batch-polling";
import { fmtDateTime, fmtRelative } from "@/lib/batch-format";
import type { TaskPage } from "@/lib/batch-types";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Skeleton } from "@/components/ui/skeleton";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
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

const STATUS_OPTIONS = [
  "ALL",
  "SCHEDULED",
  "RUNNING",
  "PAUSED",
  "ERROR",
  "TIMEOUT",
  "COMPLETED",
];

export function TasksView({
  isAdmin,
  onSelectTask,
  onNewTask,
}: {
  isAdmin: boolean;
  onSelectTask: (id: number) => void;
  onNewTask: () => void;
}) {
  const [searchInput, setSearchInput] = useState("");
  const [query, setQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState("ALL");
  const [page, setPage] = useState(1);
  const [pendingDelete, setPendingDelete] = useState<number | null>(null);

  useEffect(() => {
    const timer = setTimeout(() => {
      setQuery(searchInput);
      setPage(1);
    }, 400);
    return () => clearTimeout(timer);
  }, [searchInput]);

  const list = useBatchPolling<TaskPage>(
    () =>
      api<TaskPage>(
        `/api/tasks?page=${page}&size=10&status=${encodeURIComponent(statusFilter)}&q=${encodeURIComponent(query)}`,
      ),
    5000,
  );

  async function mutate(id: number, action: "pause" | "resume" | "trigger", successMessage: string) {
    try {
      await api(`/api/tasks/${id}/${action}`, { method: "POST" });
      toast.success(successMessage);
      void list.refresh();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "Thao tác thất bại");
    }
  }

  async function removeTask(id: number) {
    try {
      await api(`/api/tasks/${id}`, { method: "DELETE" });
      toast.success(`Đã xóa task #${id}`);
      setPendingDelete(null);
      void list.refresh();
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : "Xóa task thất bại");
    }
  }

  const data = list.data;
  const totalPages = data ? Math.max(1, Math.ceil(data.total / data.size)) : 1;

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Tasks</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Danh sách task định kỳ — hiển thị theo NEXT_RUN_AT (nguồn sự thật duy nhất)
          </p>
        </div>
        {isAdmin && (
          <Button onClick={onNewTask} className="rounded-xl">
            <Plus className="mr-2 h-4 w-4" />
            Tạo task mới
          </Button>
        )}
      </div>

      <div className="flex flex-wrap items-center gap-2">
        <div className="relative w-full sm:w-72">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
            placeholder="Tìm theo tên task / bean class..."
            className="rounded-xl pl-9"
          />
        </div>
        <Select
          value={statusFilter}
          onValueChange={(value) => {
            setStatusFilter(value);
            setPage(1);
          }}
        >
          <SelectTrigger className="w-44 rounded-xl">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {STATUS_OPTIONS.map((option) => (
              <SelectItem key={option} value={option}>
                {option === "ALL" ? "Tất cả trạng thái" : option}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <span className="text-sm text-muted-foreground">
          {data ? `${data.total} task` : "Đang tải..."}
        </span>
      </div>

      {list.error && (
        <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
          {list.error.message} — tự động thử lại...
        </div>
      )}

      <div className="overflow-hidden rounded-2xl border border-stone-200/70 bg-white">
        <Table>
          <TableHeader>
            <TableRow className="bg-stone-50/80 hover:bg-stone-50/80">
              <TableHead className="w-[26%]">Task</TableHead>
              <TableHead className="w-[22%]">Cron / Timezone</TableHead>
              <TableHead>Trạng thái</TableHead>
              <TableHead className="w-[14%]">Lần chạy cuối</TableHead>
              <TableHead className="w-[14%]">Chạy kế tiếp</TableHead>
              <TableHead>Worker</TableHead>
              <TableHead className="w-12" />
            </TableRow>
          </TableHeader>
          <TableBody>
            {list.loading && !data &&
              Array.from({ length: 6 }).map((_, i) => (
                <TableRow key={i}>
                  {Array.from({ length: 7 }).map((_, j) => (
                    <TableCell key={j}>
                      <Skeleton className="h-4 w-full" />
                    </TableCell>
                  ))}
                </TableRow>
              ))}
            {data?.items.map((task) => (
              <TableRow
                key={task.id}
                className="cursor-pointer"
                onClick={() => onSelectTask(task.id)}
              >
                <TableCell>
                  <div className="font-medium">{task.taskName}</div>
                  <div className="text-xs text-muted-foreground">
                    {task.functionCode ? `${task.functionCode} · ` : ""}
                    {task.functionName ?? task.beanClass}
                  </div>
                </TableCell>
                <TableCell>
                  <div className="font-mono text-xs">{task.scheduleType === "ONCE" ? "ONCE" : task.cronExpression}</div>
                  <div className="text-xs text-muted-foreground">{task.timezone}</div>
                </TableCell>
                <TableCell>
                  <StatusBadge status={task.status} />
                </TableCell>
                <TableCell>
                  <div className="text-xs">{fmtRelative(task.lastRunAt)}</div>
                  <div className="text-[11px] text-muted-foreground">{fmtDateTime(task.lastRunAt)}</div>
                </TableCell>
                <TableCell>
                  {task.nextRunAt ? (
                    <>
                      <div className="text-xs">{fmtRelative(task.nextRunAt)}</div>
                      <div className="text-[11px] text-muted-foreground">{fmtDateTime(task.nextRunAt)}</div>
                    </>
                  ) : (
                    <span className="text-xs text-muted-foreground">—</span>
                  )}
                </TableCell>
                <TableCell>
                  <span className="font-mono text-xs">{task.workerId ?? "—"}</span>
                </TableCell>
                <TableCell onClick={(e) => e.stopPropagation()}>
                  {isAdmin && (
                    <DropdownMenu>
                      <DropdownMenuTrigger asChild>
                        <Button variant="ghost" size="icon" className="h-8 w-8 rounded-lg">
                          <MoreHorizontal className="h-4 w-4" />
                        </Button>
                      </DropdownMenuTrigger>
                      <DropdownMenuContent align="end" className="w-44">
                        <DropdownMenuItem onClick={() => onSelectTask(task.id)}>
                          Chi tiết task
                        </DropdownMenuItem>
                        <DropdownMenuSeparator />
                        <DropdownMenuItem
                          disabled={task.status !== "SCHEDULED"}
                          onClick={() => mutate(task.id, "trigger", `Đã gửi lệnh chạy ngay task #${task.id}`)}
                        >
                          <Zap className="mr-2 h-4 w-4" />
                          Chạy ngay (Trigger)
                        </DropdownMenuItem>
                        <DropdownMenuItem
                          disabled={task.status !== "SCHEDULED"}
                          onClick={() => mutate(task.id, "pause", `Đã tạm dừng task #${task.id}`)}
                        >
                          <Pause className="mr-2 h-4 w-4" />
                          Tạm dừng (Pause)
                        </DropdownMenuItem>
                        <DropdownMenuItem
                          disabled={task.status !== "PAUSED"}
                          onClick={() => mutate(task.id, "resume", `Đã tiếp tục task #${task.id}`)}
                        >
                          <Play className="mr-2 h-4 w-4" />
                          Tiếp tục (Resume)
                        </DropdownMenuItem>
                        <DropdownMenuSeparator />
                        <DropdownMenuItem
                          className="text-red-600 focus:text-red-700"
                          disabled={task.status === "RUNNING"}
                          onClick={() => setPendingDelete(task.id)}
                        >
                          <Trash2 className="mr-2 h-4 w-4" />
                          Xóa task
                        </DropdownMenuItem>
                      </DropdownMenuContent>
                    </DropdownMenu>
                  )}
                </TableCell>
              </TableRow>
            ))}
            {data && data.items.length === 0 && (
              <TableRow>
                <TableCell colSpan={7} className="py-10 text-center text-sm text-muted-foreground">
                  Không có task nào khớp bộ lọc.
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </div>

      <div className="flex items-center justify-between">
        <p className="text-sm text-muted-foreground">
          Trang {data?.page ?? page}/{totalPages}
        </p>
        <div className="flex gap-2">
          <Button
            variant="outline"
            size="sm"
            className="rounded-lg"
            disabled={page <= 1}
            onClick={() => setPage((p) => Math.max(1, p - 1))}
          >
            <ChevronLeft className="mr-1 h-4 w-4" />
            Trước
          </Button>
          <Button
            variant="outline"
            size="sm"
            className="rounded-lg"
            disabled={page >= totalPages}
            onClick={() => setPage((p) => p + 1)}
          >
            Sau
            <ChevronRight className="ml-1 h-4 w-4" />
          </Button>
        </div>
      </div>

      <AlertDialog open={pendingDelete !== null} onOpenChange={(o) => !o && setPendingDelete(null)}>
        <AlertDialogContent className="rounded-2xl">
          <AlertDialogHeader>
            <AlertDialogTitle>Xóa task #{pendingDelete}?</AlertDialogTitle>
            <AlertDialogDescription>
              Toàn bộ lịch sử execution log của task này cũng sẽ bị xóa (ON DELETE CASCADE). Hành
              động không thể hoàn tác.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel className="rounded-lg">Hủy</AlertDialogCancel>
            <AlertDialogAction
              className="rounded-lg bg-red-600 text-white hover:bg-red-700"
              onClick={() => pendingDelete !== null && removeTask(pendingDelete)}
            >
              Xóa vĩnh viễn
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      {!isAdmin && (
        <p className="flex items-center gap-1.5 text-xs text-muted-foreground">
          <Clock className="h-3 w-3" />
          Bạn đang ở chế độ VIEWER — chỉ xem, không thao tác ghi.
        </p>
      )}
    </div>
  );
}
