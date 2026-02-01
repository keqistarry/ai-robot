#!/usr/bin/env bash
# 一键启动后端 + 前端（后端后台运行，前端前台运行；Ctrl+C 只停前端，后端继续跑）
set -e
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

mkdir -p "$ROOT/logs"

echo "=========================================="
echo "  小哈 AI 机器人 - 一键启动"
echo "=========================================="
echo ""

# 1. 后台启动后端，日志写入 logs/backend.log
echo "[1/3] 正在后台启动后端（端口 8080）..."
(
  export JAVA_HOME="/Users/apple/Library/Java/JavaVirtualMachines/temurin-24/Contents/Home"
  [ ! -d "$JAVA_HOME" ] && JAVA_HOME=$(/usr/libexec/java_home -v 24 2>/dev/null || /usr/libexec/java_home -v 21 2>/dev/null)
  export PATH="$JAVA_HOME/bin:$PATH"
  cd "$ROOT/xiaoha-ai-robot-springboot"
  mvn -q -DskipTests spring-boot:run
) >> "$ROOT/logs/backend.log" 2>&1 &
BACKEND_PID=$!
echo "      后端 PID: $BACKEND_PID，日志: logs/backend.log"
echo ""

# 2. 等待后端就绪（最多约 30 秒）
echo "[2/3] 等待后端就绪..."
for i in {1..30}; do
  if curl -sS -o /dev/null -w "%{http_code}" "http://localhost:8080/" 2>/dev/null | grep -q 200; then
    echo "      后端已就绪（约 ${i} 秒）"
    break
  fi
  sleep 1
  if [ "$i" -eq 30 ]; then
    echo "      警告：等待超时，前端将照常启动，请稍后自行确认后端是否正常。"
  fi
done
echo ""

# 3. 前台启动前端（占用当前终端；Ctrl+C 只停前端）
echo "[3/3] 启动前端（端口 5173），当前终端为前端日志..."
echo "      访问: http://localhost:5173"
echo "      按 Ctrl+C 仅停止前端，后端继续运行；停后端请: kill $BACKEND_PID"
echo "=========================================="
echo ""

cd "$ROOT/xiaoha-ai-robot-vue3"
export NODE_TLS_REJECT_UNAUTHORIZED=0
if [ ! -d "node_modules" ] || [ ! -d "node_modules/vite" ]; then
  echo "首次运行：正在安装前端依赖..."
  npm install --no-audit --no-fund --loglevel=info
fi
exec npm run dev
