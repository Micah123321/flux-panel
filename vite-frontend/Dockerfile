# syntax=docker/dockerfile:1
# 多阶段构建 - 构建阶段
# $BUILDPLATFORM 固定为构建机原生平台（amd64）。Node 构建产物是平台无关的静态文件，
# 因此两个目标平台共用同一次原生构建，避免 arm64 在 QEMU 模拟下执行 npm ci / vite build（慢 10 倍以上）。
FROM --platform=$BUILDPLATFORM node:20.19.0 AS builder

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY . .
RUN npm run build

# 生产阶段
FROM nginx:stable-alpine AS production-stage

COPY nginx.conf /etc/nginx/nginx.conf

COPY --from=builder /app/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
