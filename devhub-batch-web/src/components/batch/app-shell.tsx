"use client";

import { useEffect, useState, useSyncExternalStore } from "react";
import { LayoutDashboard, ListChecks, LogOut, Server, Wifi, WifiOff, Workflow } from "lucide-react";
import { clearAuth, subscribeAuth, authSnapshot } from "@/lib/batch-api";
import {
  connectBatchEvents,
  disconnectBatchEvents,
  onSocketStatus,
  type SocketStatus,
} from "@/lib/batch-events";
import type { BatchAuth } from "@/lib/batch-types";
import { cn } from "@/lib/utils";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { LoginGate } from "./login-gate";
import { DashboardView } from "./dashboard-view";
import { TasksView } from "./tasks-view";
import { TaskDetailSheet } from "./task-detail-sheet";
import { WorkersView } from "./workers-view";
import { NewTaskDialog } from "./new-task-dialog";

type View = "dashboard" | "tasks" | "workers";

const NAV_ITEMS: { key: View; label: string; icon: React.ReactNode }[] = [
  { key: "dashboard", label: "Dashboard", icon: <LayoutDashboard className="h-4 w-4" /> },
  { key: "tasks", label: "Tasks", icon: <ListChecks className="h-4 w-4" /> },
  { key: "workers", label: "Workers", icon: <Server className="h-4 w-4" /> },
];

function SocketPill({ status }: { status: SocketStatus }) {
  const map: Record<SocketStatus, { label: string; className: string; icon: React.ReactNode }> = {
    connected: {
      label: "Realtime",
      className: "border-emerald-200 bg-emerald-50 text-emerald-700",
      icon: <Wifi className="h-3 w-3" />,
    },
    connecting: {
      label: "Đang kết nối",
      className: "border-amber-200 bg-amber-50 text-amber-700",
      icon: <Wifi className="h-3 w-3 animate-pulse" />,
    },
    offline: {
      label: "Polling 5s",
      className: "border-stone-300 bg-stone-100 text-stone-600",
      icon: <WifiOff className="h-3 w-3" />,
    },
  };
  const item = map[status];
  return (
    <Badge variant="outline" className={cn("gap-1.5 font-normal", item.className)}>
      {item.icon}
      {item.label}
    </Badge>
  );
}

export default function AppShell() {
  // Reads localStorage via an external store — no hydration mismatch and no
  // setState-in-effect (React 19 lint rules).
  const auth = useSyncExternalStore(subscribeAuth, authSnapshot, () => null);
  const [view, setView] = useState<View>("dashboard");
  const [selectedTaskId, setSelectedTaskId] = useState<number | null>(null);
  const [newTaskOpen, setNewTaskOpen] = useState(false);
  const [socketStatus, setSocketStatus] = useState<SocketStatus>("offline");

  useEffect(() => onSocketStatus(setSocketStatus), []);

  const token = auth?.token;
  useEffect(() => {
    if (!token) return;
    connectBatchEvents(token);
    return () => disconnectBatchEvents();
  }, [token]);

  if (!auth) {
    return <LoginGate onLogin={() => setView("dashboard")} />;
  }

  const isAdmin = auth.role === "ADMIN";

  function logout() {
    clearAuth();
    disconnectBatchEvents();
    setSelectedTaskId(null);
  }

  return (
    <div className="flex min-h-screen bg-[#fafaf9]">
      {/* Sidebar (desktop) */}
      <aside className="sticky top-0 hidden h-screen w-60 shrink-0 flex-col border-r border-stone-200/70 bg-white md:flex">
        <div className="flex items-center gap-3 px-5 py-5">
          <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-teal-600 text-white">
            <Workflow className="h-5 w-5" />
          </div>
          <div>
            <p className="text-sm font-semibold leading-tight">DevHub Batch</p>
            <p className="text-xs text-muted-foreground">Ops Console</p>
          </div>
        </div>

        <nav className="mt-2 flex flex-col gap-1 px-3">
          {NAV_ITEMS.map((item) => (
            <button
              key={item.key}
              onClick={() => setView(item.key)}
              className={cn(
                "flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm transition",
                view === item.key
                  ? "bg-teal-50 font-medium text-teal-800"
                  : "text-stone-600 hover:bg-stone-50",
              )}
            >
              {item.icon}
              {item.label}
            </button>
          ))}
        </nav>

        <div className="mt-auto px-4 pb-5">
          <div className="rounded-2xl border border-stone-200/70 p-3">
            <div className="flex items-center gap-2.5">
              <div className="flex h-8 w-8 items-center justify-center rounded-full bg-teal-600 text-xs font-semibold text-white">
                {auth.username.slice(0, 2).toUpperCase()}
              </div>
              <div className="min-w-0">
                <p className="truncate text-sm font-medium">{auth.username}</p>
                <Badge
                  variant="outline"
                  className={cn(
                    "mt-0.5 text-[10px]",
                    isAdmin
                      ? "border-teal-200 bg-teal-50 text-teal-700"
                      : "border-stone-300 bg-stone-100 text-stone-600",
                  )}
                >
                  {auth.role}
                </Badge>
              </div>
            </div>
            <div className="mt-3 flex items-center justify-between">
              <SocketPill status={socketStatus} />
              <Button
                variant="ghost"
                size="sm"
                className="h-8 rounded-lg text-muted-foreground hover:text-red-600"
                onClick={logout}
              >
                <LogOut className="mr-1.5 h-3.5 w-3.5" />
                Thoát
              </Button>
            </div>
          </div>
        </div>
      </aside>

      {/* Main content */}
      <div className="flex min-w-0 flex-1 flex-col">
        {/* Mobile top bar */}
        <header className="sticky top-0 z-10 flex items-center justify-between border-b border-stone-200/70 bg-white/90 px-4 py-3 backdrop-blur md:hidden">
          <div className="flex items-center gap-2">
            <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-teal-600 text-white">
              <Workflow className="h-4 w-4" />
            </div>
            <span className="text-sm font-semibold">DevHub Batch</span>
          </div>
          <div className="flex items-center gap-2">
            <SocketPill status={socketStatus} />
            <Button variant="ghost" size="icon" className="h-8 w-8" onClick={logout}>
              <LogOut className="h-4 w-4" />
            </Button>
          </div>
        </header>

        {/* Mobile nav */}
        <nav className="flex gap-1 border-b border-stone-200/70 bg-white px-3 pb-2 md:hidden">
          {NAV_ITEMS.map((item) => (
            <button
              key={item.key}
              onClick={() => setView(item.key)}
              className={cn(
                "flex items-center gap-2 rounded-xl px-3 py-2 text-sm",
                view === item.key ? "bg-teal-50 font-medium text-teal-800" : "text-stone-600",
              )}
            >
              {item.icon}
              {item.label}
            </button>
          ))}
        </nav>

        <main className="mx-auto w-full max-w-[1440px] flex-1 px-4 py-6 md:px-8">
          {view === "dashboard" && <DashboardView onOpenTask={(id) => setSelectedTaskId(id)} />}
          {view === "tasks" && (
            <TasksView
              isAdmin={isAdmin}
              onSelectTask={(id) => setSelectedTaskId(id)}
              onNewTask={() => setNewTaskOpen(true)}
            />
          )}
          {view === "workers" && <WorkersView />}
        </main>
      </div>

      <TaskDetailSheet
        taskId={selectedTaskId}
        open={selectedTaskId !== null}
        isAdmin={isAdmin}
        onOpenChange={(next) => {
          if (!next) setSelectedTaskId(null);
        }}
        onChanged={() => undefined}
      />
      <NewTaskDialog
        open={newTaskOpen}
        onOpenChange={setNewTaskOpen}
        onCreated={() => setView("tasks")}
      />
    </div>
  );
}
