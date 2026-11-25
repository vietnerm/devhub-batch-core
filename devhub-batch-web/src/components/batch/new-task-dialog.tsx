"use client";

import { useState } from "react";
import { Loader2, Sparkles } from "lucide-react";
import { api } from "@/lib/batch-api";
import type { CronCheckResponse } from "@/lib/batch-types";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Switch } from "@/components/ui/switch";
import { Textarea } from "@/components/ui/textarea";
import { toast } from "sonner";

const DEMO_BEANS = [
  "com.devhub.batch.api.demo.DemoHelloJob",
  "com.devhub.batch.api.demo.DemoSyncOrdersJob",
  "com.devhub.batch.api.demo.DemoHealthCheckJob",
  "com.devhub.batch.api.demo.DemoFlakeJob",
];

const TIMEZONES = ["Asia/Ho_Chi_Minh", "UTC", "Asia/Bangkok", "Asia/Singapore", "Europe/Berlin"];

interface FormState {
  taskName: string;
  functionCode: string;
  functionName: string;
  beanClass: string;
  scheduleType: "RECURRING" | "ONCE";
  cronExpression: string;
  scheduledTime: string;
  timezone: string;
  misfirePolicy: "FIRE_NOW" | "SKIP_TO_NEXT";
  priority: string;
  timeoutSeconds: string;
  requeueOnFailure: boolean;
  description: string;
}

const EMPTY_FORM: FormState = {
  taskName: "",
  functionCode: "",
  functionName: "",
  beanClass: DEMO_BEANS[0],
  scheduleType: "RECURRING",
  cronExpression: "0 0 3 * * *",
  scheduledTime: "",
  timezone: "Asia/Ho_Chi_Minh",
  misfirePolicy: "FIRE_NOW",
  priority: "0",
  timeoutSeconds: "3600",
  requeueOnFailure: false,
  description: "",
};

type NewTaskDialogProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onCreated: () => void;
};

export function NewTaskDialog(props: NewTaskDialogProps) {
  return <NewTaskDialogContent key={props.open ? "open" : "closed"} {...props} />;
}

function NewTaskDialogContent({
  open,
  onOpenChange,
  onCreated,
}: NewTaskDialogProps) {
  const [form, setForm] = useState<FormState>(EMPTY_FORM);
  const [check, setCheck] = useState<CronCheckResponse | null>(null);
  const [checking, setChecking] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  function update<K extends keyof FormState>(key: K, value: FormState[K]) {
    setForm((prev) => ({ ...prev, [key]: value }));
  }

  async function runCheck() {
    setChecking(true);
    try {
      const response = await api<CronCheckResponse>("/api/cron/check", {
        method: "POST",
        body: JSON.stringify({
          scheduleType: form.scheduleType,
          cronExpression: form.scheduleType === "RECURRING" ? form.cronExpression : null,
          timezone: form.timezone,
          scheduledTime:
            form.scheduleType === "ONCE" && form.scheduledTime ? form.scheduledTime : null,
        }),
      });
      setCheck(response);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "Kiểm tra lịch chạy thất bại");
    } finally {
      setChecking(false);
    }
  }

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    if (!form.taskName.trim() || !form.beanClass.trim()) {
      toast.error("taskName và BEAN_CLASS là bắt buộc");
      return;
    }
    setSubmitting(true);
    try {
      await api("/api/tasks", {
        method: "POST",
        body: JSON.stringify({
          taskName: form.taskName.trim(),
          functionCode: form.functionCode.trim() || null,
          functionName: form.functionName.trim() || null,
          beanClass: form.beanClass.trim(),
          scheduleType: form.scheduleType,
          cronExpression: form.scheduleType === "RECURRING" ? form.cronExpression.trim() : null,
          scheduledTime: form.scheduleType === "ONCE" && form.scheduledTime ? form.scheduledTime : null,
          timezone: form.timezone,
          misfirePolicy: form.misfirePolicy,
          priority: Number(form.priority) || 0,
          timeoutSeconds: Number(form.timeoutSeconds) || 3600,
          requeueOnFailure: form.requeueOnFailure,
          description: form.description.trim() || null,
        }),
      });
      toast.success("Đã tạo task — scheduler sẽ lên lịch trong vòng 1 giây (dispatch loop)");
      onCreated();
      onOpenChange(false);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "Tạo task thất bại");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[92vh] overflow-y-auto rounded-2xl sm:max-w-xl">
        <DialogHeader>
          <DialogTitle>Tạo task mới</DialogTitle>
          <DialogDescription>
            Task được lưu qua core repository — cron 6 fields (giây phút giờ ngày tháng thứ),
            NEXT_RUN_AT được tính tự động theo timezone.
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={submit} className="space-y-4">
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-1.5">
              <Label htmlFor="taskName">Tên task (TASK_NAME) *</Label>
              <Input
                id="taskName"
                value={form.taskName}
                onChange={(e) => update("taskName", e.target.value)}
                placeholder="DEMO_MY_NEW_TASK"
                className="font-mono"
                required
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="functionCode">Function code</Label>
              <Input
                id="functionCode"
                value={form.functionCode}
                onChange={(e) => update("functionCode", e.target.value)}
                placeholder="DMO20"
                className="font-mono"
              />
            </div>
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="functionName">Tên hiển thị (Function name)</Label>
            <Input
              id="functionName"
              value={form.functionName}
              onChange={(e) => update("functionName", e.target.value)}
              placeholder="Báo cáo doanh thu hằng ngày"
            />
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="beanClass">BEAN_CLASS (Runner class) *</Label>
            <Select value={form.beanClass} onValueChange={(v) => update("beanClass", v)}>
              <SelectTrigger className="w-full font-mono text-xs">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {DEMO_BEANS.map((bean) => (
                  <SelectItem key={bean} value={bean} className="font-mono text-xs">
                    {bean.replace("com.devhub.batch.api.demo.", "")}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-1.5">
              <Label>Loại lịch (Schedule type)</Label>
              <Select
                value={form.scheduleType}
                onValueChange={(v) => update("scheduleType", v as FormState["scheduleType"])}
              >
                <SelectTrigger className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="RECURRING">RECURRING (cron lặp lại)</SelectItem>
                  <SelectItem value="ONCE">ONCE (chạy một lần)</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-1.5">
              <Label>Timezone</Label>
              <Select value={form.timezone} onValueChange={(v) => update("timezone", v)}>
                <SelectTrigger className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {TIMEZONES.map((tz) => (
                    <SelectItem key={tz} value={tz} className="font-mono text-xs">
                      {tz}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>

          {form.scheduleType === "RECURRING" ? (
            <div className="space-y-1.5">
              <Label htmlFor="cron">Cron 6 fields (giây phút giờ ngày tháng thứ) *</Label>
              <div className="flex gap-2">
                <Input
                  id="cron"
                  value={form.cronExpression}
                  onChange={(e) => update("cronExpression", e.target.value)}
                  placeholder="0 0 3 * * *"
                  className="font-mono"
                />
                <Button
                  type="button"
                  variant="outline"
                  className="shrink-0 rounded-xl"
                  onClick={runCheck}
                  disabled={checking}
                >
                  {checking ? (
                    <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  ) : (
                    <Sparkles className="mr-2 h-4 w-4" />
                  )}
                  Kiểm tra
                </Button>
              </div>
            </div>
          ) : (
            <div className="space-y-1.5">
              <Label htmlFor="scheduledTime">Thời gian chạy một lần *</Label>
              <Input
                id="scheduledTime"
                type="datetime-local"
                value={form.scheduledTime}
                onChange={(e) => update("scheduledTime", e.target.value)}
              />
            </div>
          )}

          {check && (
            <div
              className={
                check.valid
                  ? "rounded-xl border border-teal-200 bg-teal-50/70 px-3 py-2.5 text-sm"
                  : "rounded-xl border border-red-200 bg-red-50 px-3 py-2.5 text-sm"
              }
            >
              {check.valid ? (
                <>
                  <p className="font-medium text-teal-800">Lịch chạy hợp lệ ({check.scheduleType})</p>
                  {check.nextRuns.length > 0 && (
                    <ul className="mt-1 space-y-0.5 font-mono text-xs text-teal-700">
                      {check.nextRuns.map((run) => (
                        <li key={run}>→ {run}</li>
                      ))}
                    </ul>
                  )}
                </>
              ) : (
                <p className="text-red-700">{check.message || "Lịch chạy không hợp lệ"}</p>
              )}
            </div>
          )}

          <div className="grid gap-4 sm:grid-cols-3">
            <div className="space-y-1.5">
              <Label htmlFor="misfire">Misfire policy</Label>
              <Select
                value={form.misfirePolicy}
                onValueChange={(v) => update("misfirePolicy", v as FormState["misfirePolicy"])}
              >
                <SelectTrigger className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="FIRE_NOW">FIRE_NOW</SelectItem>
                  <SelectItem value="SKIP_TO_NEXT">SKIP_TO_NEXT</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="priority">Priority</Label>
              <Input
                id="priority"
                type="number"
                value={form.priority}
                onChange={(e) => update("priority", e.target.value)}
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="timeout">Timeout (s)</Label>
              <Input
                id="timeout"
                type="number"
                value={form.timeoutSeconds}
                onChange={(e) => update("timeoutSeconds", e.target.value)}
              />
            </div>
          </div>

          <div className="flex items-center justify-between rounded-xl border border-stone-200 px-4 py-3">
            <div>
              <p className="text-sm font-medium">Requeue khi lỗi</p>
              <p className="text-xs text-muted-foreground">
                Tự động đặt lại lịch chạy nếu lần chạy FAILED
              </p>
            </div>
            <Switch
              checked={form.requeueOnFailure}
              onCheckedChange={(v) => update("requeueOnFailure", v)}
            />
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="description">Mô tả (Description)</Label>
            <Textarea
              id="description"
              value={form.description}
              onChange={(e) => update("description", e.target.value)}
              placeholder="Task chạy lúc 03:00 mỗi ngày theo giờ Việt Nam..."
              rows={2}
            />
          </div>

          <div className="flex justify-end gap-2 pt-1">
            <Button
              type="button"
              variant="outline"
              className="rounded-xl"
              onClick={() => onOpenChange(false)}
            >
              Hủy
            </Button>
            <Button type="submit" className="rounded-xl" disabled={submitting}>
              {submitting && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
              Tạo task
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}
