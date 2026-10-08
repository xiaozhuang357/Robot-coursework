#!/usr/bin/env bash
# 与 stop-demo.ps1 等价，读取同一份 .lineguard-demo.pids.json。
set -euo pipefail

projectRoot="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
pidRecordPath="$projectRoot/.lineguard-demo.pids.json"

if [ ! -e "$pidRecordPath" ]; then
    echo "[LineGuard] 未发现演示进程记录。"
    # 手工启动的演示没有记录，这里把占用端口的进程列出来，省得用户去猜怎么清干净
    for port in 8080 5173; do
        if lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then
            echo "[LineGuard] 但端口 $port 仍有进程在监听（可能是手工启动的）："
            lsof -nP -iTCP:"$port" -sTCP:LISTEN -Fp | sed -n 's/^p/             kill /p'
        fi
    done
    exit 0
fi

stopProcessTree() {
    local target="$1" child
    for child in $(pgrep -P "$target" 2>/dev/null || true); do
        stopProcessTree "$child"
    done
    kill "$target" 2>/dev/null || true
}

read -r frontendPid backendPid < <(
    python3 -c "
import json, sys
record = json.load(open(sys.argv[1], encoding='utf-8'))
print(record.get('frontendPid', ''), record.get('backendPid', ''))
" "$pidRecordPath"
)

[ -n "${frontendPid:-}" ] && stopProcessTree "$frontendPid"
[ -n "${backendPid:-}" ] && stopProcessTree "$backendPid"
rm -f "$pidRecordPath"

echo "[LineGuard] 演示服务已停止，运行数据保留在 backend/runtime-data。"
