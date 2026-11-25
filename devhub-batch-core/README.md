# DevHub Batch Core

Standalone, distributed batch scheduler for DevHub — a clean rewrite of the
`devhub-rbac-batch` core (scheduler engine only). Pure JDBC, no Spring runtime,
Java 21+, MySQL 8.

**Scope note:** this repository contains the **core scheduler only**. Business
batches (`zz/*`, `go/*`) stay in the original project and are out of scope for
now; they keep running through the same `Runner` contract.

[![CI](https://github.com/vietnerm/devhub-batch-core/actions/workflows/ci.yml/badge.svg)](https://github.com/vietnerm/devhub-batch-core/actions/workflows/ci.yml)

## Why a rewrite

The 1.x scheduler worked, but code review found structural defects that were
safer to rewrite than to patch:

| # | Defect in devhub-rbac-batch 1.x | Fix in devhub-batch-core |
|---|---|---|
| Y1 | `initFromDatabase()` unconditionally rewrote every `RUNNING` task to `TIMEOUT` at startup, killing runs that other live workers were executing | `TaskRecovery.recoverTimedOutTasks` only rewrites a run when its own claim is stale (`CLAIMED_AT` older than the task's `TIMEOUT_SECONDS`) |
| Y2 | `CronCalculator.nextExecution()` used `ZoneId.systemDefault()`, silently ignoring the task's `TIMEZONE` column | Every cron calculation runs on the task timezone wall clock (`CronCalculator.zoneOf`), zones are validated on save |
| Y3 | `calculateDelay()` clamped with `Math.max(0, ...)`, `MISFIRE_POLICY` was stored but never enforced | `SchedulePlanner.misfireAdvance` + leader loop enforce `FIRE_NOW` / `SKIP_TO_NEXT` (legacy values `FIRE_ONCE`, `SKIP`, `RESCHEDULE_NEXT` are normalized) |
| Y5 | The reconcile loop rescheduled whenever `abs(requested - owned) > 5000 ms`, a drift condition that could re-fire in a loop | No in-memory futures at all: the database (`NEXT_RUN_AT`) is the single source of truth; `SchedulePlanner.afterRun` is strictly increasing |
| Y6 | CLI `run` started the engine, slept 1 second, then shut down; `pause`/`list`/`logs` also booted the full engine | DB-only commands never start the engine; `run` executes synchronously and prints the real outcome; daemon blocks on a latch until SIGINT |
| — | `batch.cleanup-on-startup` config was dead code | drives hourly log-retention purge (`batch.log-retention-days`) |
| — | Worker startup called `api.ipify.org` / `ifconfig.me` to detect the public IP | removed; public IP comes only from explicit config |
| — | Claim state could not be trusted without worker heartbeats | worker registry heartbeat (5 s) + stale marking + `GET_LOCK` leader election for reconcile |

## CVE-2026-114938 ("Java4Shell") policy

CVE-2026-114938 is a memory-corruption/RCE vulnerability in the HotSpot
intrinsics of `java.util.Base64` (affects Java 17.0.6+/21/25/27 on x86-64
CPUs with AVX-512 VBMI; fixed Temurin builds 17.0.21.1 / 21.0.13.1 /
25.0.5.1 / 27.0.1.1, released 2026-11-17).

This project is hardened on two layers:

1. **Never call the Base64 decoder.** All binary-to-text output (AES-256-GCM
   ciphertexts, master keys) uses `com.devhub.batch.core.crypto.HexCodec`.
   CI fails if the banned decoder is referenced (`scripts/check-no-base64.sh`).
2. **Only patched JDKs.** CI pins Temurin `25.0.5.1`; the Dockerfile runs
   `eclipse-temurin:25.0.5.1_1-jre` and also disables the decoder intrinsics
   (`-XX:-UseBASE64Intrinsics` behind `IgnoreUnrecognizedVMOptions`) as
   defense in depth. Encrypted config values use `enc:<hex>` + the
   `BATCH_MASTER_KEY` env var (64 hex chars = 32 bytes).

## Architecture

```
                 ┌────────────────────────── MySQL 8 ──────────────────────────┐
                 │ SYS_SCHEDULED_TASKS   SYS_TASK_EXECUTION_LOGS               │
                 │ SYS_SCHEDULER_WORKERS SYS_BATCH_MIGRATIONS                  │
                 └────────▲───────────────────▲───────────────────▲────────────┘
                          │ poll/claim        │ heartbeat         │ reconcile (leader)
              ┌───────────┴───────┐   ┌───────┴────────┐   ┌──────┴──────────┐
              │ worker A (daemon) │   │ worker B       │   │ worker C        │
              │ dispatch 1s       │   │ (same image)   │   │ GET_LOCK leader │
              │ pool size N       │   │                │   │ recovery/misfire│
              └───────────┬───────┘   └───────┬────────┘   └──────┬──────────┘
                          │ claim (atomic UPDATE)                 │
                          ▼                                       │
                    TaskJob: claim → RUNNING → Runner.execute() → log → next run
```

- **Dispatch loop** (every `batch.poll-interval-ms`, default 1 s): finds
  `SCHEDULED` tasks with `NEXT_RUN_AT <= now`, submits claims up to the free
  pool slots. The claim is one conditional `UPDATE` — exactly one worker wins.
- **Heartbeat loop** (5 s): upserts the worker registry row with the current
  task, so the web UI (`devhub-rbac-web` kanban) sees live state.
- **Leader loop** (5 s, one node wins `GET_LOCK('devhub-batch-core-leader')`):
  marks stale workers, recovers timed-out claims (Y1), advances overdue tasks
  per misfire policy (Y3), purges old execution logs by retention.

## Quick start

```bash
mvn -B verify                 # unit tests (H2) + MySQL ITs (Testcontainers)
mvn -B package                # shaded jar: target/devhub-batch-core.jar

# 1. Schema
java -jar target/devhub-batch-core.jar migrate

# 2. Register a task (6-field Spring cron: sec min hour dom mon dow)
java -jar target/devhub-batch-core.jar add Demo com.example.DemoBatch "0 */2 * * * *" 1 "runs every 2 minutes"

# 3. Run the daemon
java -jar target/devhub-batch-core.jar daemon
```

Configuration priority: `ENV var` > `-Dconfig.file=/path` > classpath
`config.properties` > defaults. Secrets are never stored in the file:

```bash
BATCH_MASTER_KEY=$(openssl rand -hex 32)           # keep this safe
echo -n 'real-db-password' | java -jar app.jar encrypt
# -> enc:4226e2db...   paste into db.password (or keep ${BATCH_DB_PASSWORD})
```

## CLI

| Command | Starts engine? | Description |
|---|---|---|
| `daemon` | yes | run the scheduler until SIGINT/SIGTERM |
| `migrate` | no | apply versioned SQL migrations (`db/migration/*.sql`) |
| `list` / `status` / `logs <id>` | no | inspect tasks and history |
| `add` / `add-once` | no | register RECURRING / ONCE tasks (validated before insert) |
| `run <id>` | no | execute one attempt synchronously and print the outcome (fix Y6) |
| `pause` / `resume` / `stop` / `delete` | no | lifecycle control via DB |
| `validate-cron` / `validate-schedule` | no | offline validation |
| `encrypt` | no | stdin secret → `enc:<hex>` (AES-256-GCM, hex only) |

## Database

Same table names, columns and status vocabulary as 1.x, so `devhub-rbac-web`
(ZZA0_1300 scheduler screens) keeps working unchanged:
`SCHEDULED / RUNNING / PAUSED / STOPPED / ERROR / TIMEOUT / COMPLETED`.

Migration `V20260815__batch_core_schema.sql` is idempotent: fresh installs
create the full schema; existing 1.x databases are upgraded in place via
guarded `ALTER`s. Applied versions are tracked in `SYS_BATCH_MIGRATIONS`.

## CI/CD testing (GitHub Actions)

`.github/workflows/ci.yml` runs on every push/PR to `main`:

1. **JDK gate** — Temurin `25.0.5.1` (CVE-2026-114938 patched, released
   2026-11-17). Downgrading the JDK is a policy violation, not an option.
2. **Security gate** — `scripts/check-no-base64.sh` fails the build when the
   banned decoder appears anywhere in `src/` or `config/`.
3. **Tests** — `mvn verify`: 65 unit tests (H2, covering claim atomicity,
   timezone-aware cron, misfire policies, recovery rules, crypto roundtrips)
   plus Testcontainers MySQL integration tests (real migrations + end-to-end
   `TaskJob` execution + concurrent claim race).
4. **Docker** — builds the image on the patched runtime (no push; add your
   registry credentials to publish).

## Project layout

```
src/main/java/com/devhub/batch/core/
├── Scheduler.java            # poll/claim engine (dispatch, heartbeat, leader loops)
├── SchedulerBootstrap.java   # singleton entry point + leader elector selection
├── TaskJob.java              # one execution attempt (claim → run → log → next)
├── TaskRecovery.java         # Y1-safe recovery + Y3 misfire enforcement
├── Runner.java               # abstract batch base (1.x plugin contract kept)
├── cli/                      # App, Cli, DataSourceFactory
├── config/BatchConfig.java   # layered config, ${VAR} interpolation, enc:<hex> secrets
├── cron/CronCalculator.java  # cron-utils SPRING53, zone-aware (fix Y2)
├── crypto/                   # HexCodec + AesGcm (no Base64 anywhere)
├── migrate/DatabaseMigrator.java
├── misfire/MisfirePolicy.java
├── model/                    # ScheduledTask, TaskExecutionLog, WorkerInfo
├── repository/               # TaskRepository + JdbcTaskRepository (atomic claim)
├── schedule/                 # ScheduleValidator, SchedulePlanner
└── worker/                   # LeaderElector (MySQL GET_LOCK / always-leader)
```

## Tổng quan (Tiếng Việt)

Đây là bản viết lại phần **core scheduler** của `devhub-rbac-batch` (không gồm
các batch nghiệp vụ `zz/`, `go/`). Đã sửa toàn bộ lỗi Y1–Y6 tìm thấy khi review:
không còn tự đánh dấu `RUNNING→TIMEOUT` khi khởi động, cron tôn trọng cột
`TIMEZONE`, `MISFIRE_POLICY` thực sự có hiệu lực, bỏ vòng drift-check gây lặp,
CLI chạy đồng bộ đúng nghĩa. Bảo mật: **cấm hoàn toàn Base64** (policy
CVE-2026-114938) — mọi mã hóa dùng hex + AES-256-GCM; CI chỉ build trên JDK
Temurin đã vá (25.0.5.1) và tự chặn nếu source xuất hiện lại Base64.
