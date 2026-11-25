# DevHub Batch Platform

Monorepo gồm 3 lớp độc lập cho hệ thống batch scheduler:

```
devhub-batch/
├── devhub-batch-core/   Engine scheduler thuần JDBC (Scheduler, TaskJob,
│                        Repository, Misfire/Planner, AES-GCM + HexCodec).
│                        Không dính HTTP/framework — chạy standalone CLI hoặc thư viện.
├── devhub-batch-api/    Spring Boot 3 facade: REST + WebSocket, JWT (hex-HMAC),
│                        read model JDBC, scheduler chạy embedded.
├── devhub-batch-web/    Next.js 16 + shadcn/ui Ops Console: dashboard, tasks,
│                        log viewer realtime, workers, tạo task với cron preview.
├── scripts/             CI gates (no-Base64 policy CVE-2026-114938).
└── docker-compose.yml   MySQL 8 + API + Web, một lệnh dựng full stack.
```

## Luồng dữ liệu

```
Web (Next.js)  ──REST/WS──▶  API (Spring Boot 3)  ──TaskRepository──▶  Core engine
                                   │                                    │
                                   └──────── JdbcTemplate read ─────────┤
                                                                        ▼
                                                            MySQL 8 / H2 (demo)
                                              SYS_SCHEDULED_TASKS · SYS_TASK_EXECUTION_LOGS
                                              SYS_SCHEDULER_WORKERS · SYS_BATCH_MIGRATIONS
```

Nguyên tắc: **ghi** phải đi qua `TaskRepository` của core (validation cron,
NEXT_RUN_AT planning Y5, claim atomic Y1); API chỉ thêm read model (phân trang,
thống kê) và KHÔNG BAO GIỜ issue UPDATE riêng vào SYS_ tables.

## Bảo mật

- Token đăng nhập: `v1.<hex(payload)>.<hex(HMAC-SHA256)>` — hex 100%, đúng chính
  sách cấm JDK Base64 decoder của CVE-2026-114938 (`scripts/check-no-base64.sh`
  chặn ở CI). Secrets cấu hình dạng hex/`enc:<hex>`, không Base64.
- 2 vai trò: **ADMIN** (tạo/sửa/xóa/pause/resume/trigger) và **VIEWER** (chỉ xem).
- Dockerfile API dùng `eclipse-temurin:25.0.5.1_2-jre` + `-XX:-UseBASE64Intrinsics`.

## Chạy demo nhanh (không cần MySQL)

```bash
mvn -B -ntp -pl devhub-batch-api -am package -DskipTests
java -Dslf4j.provider=ch.qos.logback.classic.spi.LogbackServiceProvider \
     -jar devhub-batch-api/target/devhub-batch-api.jar --spring.profiles.active=demo
# API: http://localhost:8080 — H2 file ./data/batch-demo, seed 13 task + ~600 log
# Tài khoản: admin/admin123, viewer/viewer123
```

Web: xem `devhub-batch-web/README.md` (`bun install && bun run dev`).

## Chạy production stack

```bash
mvn -B -ntp -DskipTests package
export MYSQL_ROOT_PASSWORD=... BATCH_API_JWT_SECRET_HEX=<64 hex> ...
docker compose up -d --build
# web: http://localhost:3000 · api: http://localhost:8080
```

## API v1

| Method | Path | Vai trò |
|---|---|---|
| POST | `/api/auth/login` | public |
| GET | `/api/tasks?page&size&status&q&sort&dir` | authenticated |
| GET | `/api/tasks/{id}` | authenticated |
| POST | `/api/tasks` | ADMIN |
| PUT | `/api/tasks/{id}` | ADMIN |
| POST | `/api/tasks/{id}/pause` · `/resume` · `/trigger` | ADMIN |
| DELETE | `/api/tasks/{id}` | ADMIN |
| GET | `/api/workers` | authenticated |
| GET | `/api/stats/summary` · `/api/stats/timeline?hours` · `/api/stats/latest-failure` | authenticated |
| POST | `/api/cron/check` | authenticated |
| WS | `/ws/events?token=...` (event: `task`, `log`, `workers`, `task_removed`) | authenticated |

## CI

`.github/workflows/ci.yml`: Temurin **25.0.5.1** (patched JDK) → no-Base64 gate →
`mvn verify` (unit + Testcontainers MySQL IT) → docker build API + Web.

## Ghi chú kỹ thuật

- Core jar là shaded CLI jar nên mang theo `slf4j-simple`; API ép provider Logback
  bằng `-Dslf4j.provider=ch.qos.logback.classic.spi.LogbackServiceProvider`.
- Demo profile dùng H2 MODE=MySQL với `sql/h2-schema.sql` (schema MySQL migration
  V20260815 dùng PREPARE/EXECUTE chỉ chạy được trên MySQL thật).
- Realtime không cắm hook vào core: poller tail `SYS_TASK_EXECUTION_LOGS` theo ID
  tăng dần + diff fingerprint 2 bảng còn lại (bảng nhỏ; production lớn nên thêm
  version column/trigger).
- Timestamps lưu wall-clock Asia/Ho_Chi_Minh; API set `user.timezone` mặc định,
  container đặt `TZ` tương ứng.
