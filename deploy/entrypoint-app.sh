#!/bin/sh
set -eu

echo "[ikantvs] starting backend..."
java \
  --add-opens java.base/java.net=ALL-UNNAMED \
  --add-opens java.base/java.lang=ALL-UNNAMED \
  --add-opens java.base/java.lang.reflect=ALL-UNNAMED \
  --add-opens java.base/sun.net.www.protocol.https=ALL-UNNAMED \
  -jar /app/app.jar &
JAVA_PID=$!

cleanup() {
  kill "$JAVA_PID" 2>/dev/null || true
  nginx -s quit 2>/dev/null || true
}
trap cleanup INT TERM

i=0
until wget -q --spider http://127.0.0.1:8888/api/health 2>/dev/null; do
  i=$((i + 1))
  if [ "$i" -gt 60 ]; then
    echo "[ikantvs] backend health check timeout" >&2
    cleanup
    exit 1
  fi
  if ! kill -0 "$JAVA_PID" 2>/dev/null; then
    echo "[ikantvs] backend exited early" >&2
    exit 1
  fi
  sleep 2
done

echo "[ikantvs] starting nginx (web:80 admin:81)..."
nginx -g 'daemon off;' &
NGINX_PID=$!

# busybox 无 wait -n：任一进程退出则收尾
while kill -0 "$JAVA_PID" 2>/dev/null && kill -0 "$NGINX_PID" 2>/dev/null; do
  sleep 2
done

echo "[ikantvs] process exited, shutting down" >&2
cleanup
exit 1
