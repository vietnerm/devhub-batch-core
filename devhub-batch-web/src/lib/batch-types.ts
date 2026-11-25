/** Shared types mirroring devhub-batch-api DTOs. */

export interface TaskDto {
  id: number;
  functionCode: string | null;
  functionName: string | null;
  taskName: string;
  beanClass: string;
  scheduleType: "ONCE" | "RECURRING" | string;
  cronExpression: string | null;
  scheduledTime: string | null;
  startAt: string | null;
  endAt: string | null;
  timezone: string;
  misfirePolicy: string;
  status: string;
  priority: number;
  description: string | null;
  requeueOnFailure: boolean;
  timeoutSeconds: number;
  workerId: string | null;
  claimedAt: string | null;
  lastRunAt: string | null;
  nextRunAt: string | null;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface TaskPage {
  items: TaskDto[];
  total: number;
  page: number;
  size: number;
}

export interface LogDto {
  id: number;
  taskId: number;
  taskName: string | null;
  workerId: string | null;
  status: string;
  result: string | null;
  errorMessage: string | null;
  executedAt: string;
  startedAt: string | null;
  finishedAt: string | null;
  durationMs: number | null;
}

export interface TaskDetail {
  task: TaskDto;
  recentLogs: LogDto[];
}

export interface WorkerDto {
  workerId: string;
  ownerId: string;
  ownerName: string;
  machineName: string;
  hostname: string;
  privateIp: string;
  publicIp: string;
  osName: string;
  osArch: string;
  javaVersion: string;
  appVersion: string;
  environment: string;
  location: string;
  status: string;
  currentTaskId: number | null;
  currentTaskName: string | null;
  startedAt: string | null;
  lastHeartbeatAt: string | null;
  lastError: string | null;
}

export interface FailureTop {
  taskName: string;
  count: number;
}

export interface StatsSummary {
  tasksByStatus: Record<string, number>;
  runningNow: number;
  workersByStatus: Record<string, number>;
  workersOnline: number;
  executions24h: number;
  success24h: number;
  failed24h: number;
  timeout24h: number;
  successRatePct: number;
  avgDurationMs: number;
  topFailures: FailureTop[];
}

export interface TimelinePoint {
  hour: string;
  success: number;
  failed: number;
  timeout: number;
  total: number;
}

export interface CronCheckResponse {
  valid: boolean;
  message: string;
  scheduleType: string;
  nextRuns: string[];
}

export interface BatchAuth {
  token: string;
  username: string;
  role: "ADMIN" | "VIEWER";
  expiresAt: string;
}

export interface BatchWsEvent {
  type: "log" | "task" | "task_removed" | "workers";
  payload: unknown;
  occurredAt: string;
}
