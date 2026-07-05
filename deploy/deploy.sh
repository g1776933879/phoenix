# ========================================
# 凤凰 - 一键部署脚本
# 用法: bash deploy.sh [dev|prod]
# ========================================
#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_ROOT"

PROFILE="${1:-dev}"
echo "🚀 凤凰部署开始 (profile: $PROFILE)"

case "$PROFILE" in
  dev)
    echo "📦 本地Maven构建..."
    mvn clean package -pl your-business-app -am -DskipTests -B -q

    echo "🐳 Docker Compose启动 (开发模式)..."
    cd deploy
    OPENAI_API_KEY="${OPENAI_API_KEY:-sk-demo}" \
    docker compose up --build -d
    ;;

  prod)
    echo "🔒 生产环境构建 (带测试)..."
    mvn clean package -pl your-business-app -am -B -q

    echo "🐳 Docker Compose启动 (生产模式)..."
    cd deploy
    OPENAI_API_KEY="${OPENAI_API_KEY:?Error: OPENAI_API_KEY is required in production}" \
    docker compose up --build -d
    ;;

  test)
    echo "🧪 运行全部单元测试..."
    mvn test -pl your-agent-core -B
    ;;

  clean)
    echo "🧹 清理环境..."
    cd deploy
    docker compose down -v
    ;;

  logs)
    echo "📋 查看日志..."
    cd deploy
    docker compose logs -f agent
    ;;

  *)
    echo "用法: bash deploy.sh [dev|prod|test|clean|logs]"
    exit 1
    ;;
esac

echo "✅ 部署完成!"
echo ""
echo "    REST API:  http://localhost:8080/api/agent/chat"
echo "    WebSocket: ws://localhost:8080/ws/agent"
echo "    健康检查:  http://localhost:8080/api/agent/health"