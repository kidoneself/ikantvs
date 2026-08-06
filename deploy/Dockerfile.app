# 单业务镜像：backend + web + admin + 建表 SQL
# 构建（仓库根目录）：
#   docker build -f deploy/Dockerfile.app -t ghcr.io/kidoneself/ikantvs:latest .

# ---- backend ----
FROM maven:3.9-eclipse-temurin-17 AS backend-build
WORKDIR /app
COPY server/pom.xml .
RUN mvn -B dependency:go-offline
COPY server/src ./src
RUN mvn -B clean package -DskipTests

# ---- web ----
FROM node:20-alpine AS web-build
WORKDIR /app
ARG VITE_API_BASE=/api
ENV VITE_API_BASE=$VITE_API_BASE
RUN corepack enable && corepack prepare pnpm@9.15.4 --activate
COPY web/package.json web/pnpm-lock.yaml* ./
RUN pnpm install --frozen-lockfile 2>/dev/null || pnpm install
COPY web/ .
RUN pnpm build

# ---- admin ----
FROM node:20-alpine AS admin-build
WORKDIR /app
RUN corepack enable && corepack prepare pnpm@9.15.4 --activate
COPY admin/package.json admin/pnpm-lock.yaml* ./
RUN pnpm install --frozen-lockfile 2>/dev/null || pnpm install
COPY admin/ .
RUN pnpm build

# ---- runtime ----
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN apk add --no-cache nginx wget mysql-client \
  && mkdir -p /opt/ikantvs/web /opt/ikantvs/admin /opt/ikantvs/sql /run/nginx \
  && rm -f /etc/nginx/http.d/default.conf

COPY deploy/nginx-app.conf /etc/nginx/http.d/ikantvs.conf
COPY deploy/entrypoint-app.sh /entrypoint.sh
COPY deploy/db-init.sh /opt/ikantvs/db-init.sh
COPY deploy/init/*.sql /opt/ikantvs/sql/
COPY --from=backend-build /app/target/jyinshi-server.jar /app/app.jar
COPY --from=web-build /app/dist /opt/ikantvs/web
COPY --from=admin-build /app/dist /opt/ikantvs/admin

RUN chmod +x /entrypoint.sh /opt/ikantvs/db-init.sh

ENV TZ=Asia/Shanghai
EXPOSE 80 81 8888

ENTRYPOINT ["/entrypoint.sh"]
