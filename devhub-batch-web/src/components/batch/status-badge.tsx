"use client";

import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import { statusVi } from "@/lib/batch-format";

const STATUS_CLASSES: Record<string, string> = {
  SCHEDULED: "border-teal-200 bg-teal-50 text-teal-700",
  RUNNING: "border-amber-300 bg-amber-50 text-amber-800",
  PAUSED: "border-stone-300 bg-stone-100 text-stone-600",
  STOPPED: "border-stone-300 bg-stone-100 text-stone-500",
  ERROR: "border-red-200 bg-red-50 text-red-700",
  TIMEOUT: "border-orange-200 bg-orange-50 text-orange-700",
  COMPLETED: "border-emerald-200 bg-emerald-50 text-emerald-700",
  SUCCESS: "border-emerald-200 bg-emerald-50 text-emerald-700",
  FAILED: "border-red-200 bg-red-50 text-red-700",
  ONLINE: "border-emerald-200 bg-emerald-50 text-emerald-700",
  STALE: "border-amber-200 bg-amber-50 text-amber-700",
  OFFLINE: "border-stone-300 bg-stone-100 text-stone-500",
};

const PULSE_STATUSES = new Set(["RUNNING", "ONLINE"]);

export function StatusBadge({ status, className }: { status: string; className?: string }) {
  return (
    <Badge variant="outline" className={cn("font-medium", STATUS_CLASSES[status], className)}>
      {PULSE_STATUSES.has(status) && (
        <span className="mr-1.5 inline-block h-1.5 w-1.5 rounded-full bg-current animate-pulse" />
      )}
      {statusVi(status)}
    </Badge>
  );
}
