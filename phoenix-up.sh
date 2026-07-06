#!/bin/bash
# 🐦 凤凰 · 一键启动
# 用法: bash phoenix-up.sh

set -e
GREEN='\033[0;32m';CYAN='\033[0;36m';YELLOW='\033[1;33m';NC='\033[0m'
PORT=8080

cd "$(dirname "$0")"

# 检查Java
if ! command -v java &>/dev/null; then echo "❌ 需要 Java 21+"; exit 1; fi

# 检查API Key
if [ -z "${SENSENOVA_API_KEY:-}" ]; then
  read -p "输入你的 API Key: " key
  export SENSENOVA_API_KEY="$key"
fi

# 编译
if [ ! -f "your-business-app/target/classes/com/your/business/AgentApplication.class" ]; then
  echo -e "${YELLOW}📦 编译中...${NC}"
  mvn clean install -Dmaven.test.skip=true -pl your-agent-core,your-agent-spring-boot-starter,your-business-app -am -q
fi

# 启动
echo -e "${CYAN}🐦 凤凰启动中...${NC}"
mvn spring-boot:run -pl your-business-app -Dspring-boot.run.profiles=sensenova