# DevHub Batch Web — Ops Console

Next.js 16 + Tailwind CSS 4 + shadcn/ui console for the `devhub-batch-api`.
Chỉ gọi REST/WebSocket của API — web không biết tồn tại core, không nối DB.

## Trang chức năng

| Trang | Nội dung |
|---|---|
| Dashboard | KPI realtime (running/24h/success rate/failures/workers), biểu đồ 24 giờ, top task lỗi, lỗi mới nhất |
| Tasks | Bảng task: filter trạng thái, tìm kiếm, phân trang; actions Chạy ngay / Pause / Resume / Xóa (ADMIN) |
| Task detail | Sheet: schedule + cấu hình, lịch sử 20 lần thực thi gần nhất (bấm vào log để xem output) |
| Workers | Trạng thái node + heartbeat + task đang chạy + last error |
| Tạo task | Dialog với cron-check preview 3 lần chạy kế tiếp (dùng planner của core qua API) |

## Cấu hình

| Biến | Ý nghĩa | Mặc định |
|---|---|---|
| `NEXT_PUBLIC_BATCH_API_BASE` | Origin của API (vd `http://api-host:8080`). Bỏ trống khi đi qua sandbox gateway | *(empty)* |
| `NEXT_PUBLIC_BATCH_API_PORT` | Port dùng cho gateway query `XTransformPort` | `8080` |

Realtime: WebSocket `ws(s)://<api>/ws/events?token=<jwt>` — event `task`, `log`,
`workers`. Polling 5s vẫn chạy song song làm fallback, mất WS không mất dữ liệu.

## Chạy dev

```bash
bun install
NEXT_PUBLIC_BATCH_API_BASE=http://localhost:8080 bun run dev
# hoặc qua gateway:
bun run dev   # gọi /api/*?XTransformPort=8080
```

Tài khoản demo (API demo profile): `admin/admin123` (ADMIN), `viewer/viewer123` (VIEWER).

## Docker

```bash
docker build -t devhub-batch-web .
docker run -p 3000:3000 -e NEXT_PUBLIC_BATCH_API_BASE=http://api-host:8080 devhub-batch-web
```

Image build output standalone (`output: "standalone"` trong next.config.ts), chạy
`node .next/standalone/server.js` — không cần bun/node_modules trong container.
