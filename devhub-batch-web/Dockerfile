# Build stage
FROM node:22-alpine AS builder
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
ARG NEXT_PUBLIC_BATCH_API_BASE=""
ENV NEXT_PUBLIC_BATCH_API_BASE=${NEXT_PUBLIC_BATCH_API_BASE}
RUN npm run build && cp -r .next/static .next/standalone/.next/ && cp -r public .next/standalone/

# Runtime stage
FROM node:22-alpine
WORKDIR /app
ENV NODE_ENV=production TZ=Asia/Ho_Chi_Minh PORT=3000 HOSTNAME=0.0.0.0
COPY --from=builder /app/.next/standalone ./
EXPOSE 3000
CMD ["node", "server.js"]
