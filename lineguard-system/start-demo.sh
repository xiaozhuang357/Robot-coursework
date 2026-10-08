#!/usr/bin/env bash
# 与 start-demo.ps1 等价的 macOS / Linux 启动脚本，共用同一份 .lineguard-demo.pids.json。
set -euo pipefail

projectRoot="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
backendDirectory="$projectRoot/backend"
frontendDirectory="$projectRoot/frontend"
runtimeLogDirectory="$projectRoot/.run-logs"
pidRecordPath="$projectRoot/.lineguard-demo.pids.json"

mkdir -p "$runtimeLogDirectory"

if [ -e "$pidRecordPath" ]; then
    echo "[LineGuard] 已存在演示进程记录，请先执行 stop-demo.sh。" >&2
    exit 1
fi

# 端口预检：上一次演示若是手工启动的（没有 PID 记录），新进程会因端口被占而启动失败，
# 但旧进程仍在应答 /api/v1/devices，就绪检查会通过——脚本报"已就绪"，跑的却是旧版本。
assertPortFree() {
    local port="$1" name="$2"
    lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1 || return 0
    echo "[LineGuard] 端口 $port 已被占用，$name 无法启动。" >&2
    echo "[LineGuard] 上一次演示很可能还在运行：旧进程会继续应答，让你误以为新版本已经生效。" >&2
    echo "[LineGuard] 先结束占用它的进程，再重跑本脚本：" >&2
    lsof -nP -iTCP:"$port" -sTCP:LISTEN -Fp | sed -n 's/^p/             kill /p' >&2
    exit 1
}

assertPortFree 8080 "后端"
assertPortFree 5173 "前端"

if [ ! -d "$frontendDirectory/node_modules" ]; then
    echo "[LineGuard] 正在安装前端依赖..."
    (cd "$frontendDirectory" && npm install)
fi

echo "[LineGuard] 正在构建后端..."
(cd "$backendDirectory" && mvn -q -DskipTests package)

backendJar="$(find "$backendDirectory/target" -maxdepth 1 -name 'lineguard-backend-*.jar' ! -name '*.original' -print | sort | tail -1)"
if [ -z "$backendJar" ]; then
    echo "[LineGuard] 未找到后端可执行包。" >&2
    exit 1
fi

echo "[LineGuard] 正在启动 Java 后端..."
(cd "$backendDirectory" && java -jar "$backendJar") \
    >"$runtimeLogDirectory/backend.log" 2>"$runtimeLogDirectory/backend-error.log" &
backendPid=$!

echo "[LineGuard] 正在启动 Vue 前端..."
(cd "$frontendDirectory" && npm run dev) \
    >"$runtimeLogDirectory/frontend.log" 2>"$runtimeLogDirectory/frontend-error.log" &
frontendPid=$!

printf '{\n  "backendPid": %s,\n  "frontendPid": %s\n}\n' \
    "$backendPid" "$frontendPid" >"$pidRecordPath"

waitForEndpoint() {
    local address="$1" name="$2"
    for _ in $(seq 1 60); do
        if curl -sf -o /dev/null --max-time 2 "$address"; then
            echo "[LineGuard] $name 已就绪。"
            return 0
        fi
        sleep 0.75
    done
    echo "[LineGuard] $name 启动超时，请检查 .run-logs 目录。" >&2
    return 1
}

if ! waitForEndpoint "http://127.0.0.1:8080/api/v1/devices" "后端" \
    || ! waitForEndpoint "http://127.0.0.1:5173" "前端"; then
    echo "[LineGuard] 请执行 stop-demo.sh 清理已启动的进程。" >&2
    exit 1
fi

echo ""
echo "[LineGuard] 演示已就绪：http://localhost:5173"
echo "[LineGuard] 执行 stop-demo.sh 结束演示。"
if command -v open >/dev/null 2>&1; then
    open "http://localhost:5173"
elif command -v xdg-open >/dev/null 2>&1; then
    xdg-open "http://localhost:5173"
fi
