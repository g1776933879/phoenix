#!/bin/bash
# 🐦 Phoenix TUI - 终端交互界面
# 用法: bash phoenix-tui.sh

PMM="$(cd "$(dirname "$0")" && pwd)"
API="http://localhost:8080/api/tui"

GREEN='\033[0;32m'
CYAN='\033[0;36m'
YELLOW='\033[1;33m'
NC='\033[0m'

check() {
  curl -s -o /dev/null -w '%{http_code}' "http://localhost:8080/api/agent/health" 2>/dev/null | grep -q 200
}

if ! check; then
  echo -e "${YELLOW}Phoenix 未运行。启动? [Y/n]${NC}"
  read -r y
  if [[ "$y" != "n" ]]; then
    cd "$PMM" && bash phoenix.sh &
    sleep 10
  fi
fi

echo -e "${CYAN}╔══════════════════════════════╗${NC}"
echo -e "${CYAN}║   🐦 Phoenix TUI v2.0       ║${NC}"
echo -e "${CYAN}╚══════════════════════════════╝${NC}"
echo -e "${YELLOW}/help 查看命令  /exit 退出${NC}"

SID=""
HISTFILE="$HOME/.phoenix_history.txt"
touch "$HISTFILE" 2>/dev/null || HISTFILE="/dev/null"

while true; do
  echo -ne "${GREEN}phx> ${NC}"
  read -r input
  [[ -z "$input" ]] && continue
  echo "$input" >> "$HISTFILE"

  case "$input" in
    /exit|/quit) echo "bye!"; break ;;
    /help) echo -e "命令:\n  /help    帮助\n  /reset   重置\n  /sessions 查看会话\n  /new     新会话\n  /stats   状态\n  /exit    退出" ;;
    /reset) curl -s -X POST "http://localhost:8080/api/agent/reset" > /dev/null; echo "已重置" ;;
    /new) SID=$(curl -s -X POST "http://localhost:8080/api/sessions" | grep -o '"sessionId":"[^"]*"' | cut -d'"' -f4); echo "新会话: $SID" ;;
    /sessions) curl -s "http://localhost:8080/api/sessions" | python3 -c "import sys,json; [print(f'  {s[\"id\"]}: {s[\"title\"]}') for s in json.load(sys.stdin)]" 2>/dev/null || echo "(error)" ;;
    /stats) curl -s "http://localhost:8080/api/agent/health" | python3 -m json.tool 2>/dev/null || echo "(error)" ;;
    *) echo -e "${YELLOW}⏳...${NC}"
       result=$(curl -s -X POST "$API/chat" -H "Content-Type: application/json" \
         -d "{\"content\":\"$input\",\"sessionId\":\"$SID\"}" 2>/dev/null)
       content=$(echo "$result" | python3 -c "import sys,json; print(json.load(sys.stdin).get('content','(error)'))" 2>/dev/null || echo "(error)")
       SID=$(echo "$result" | python3 -c "import sys,json; print(json.load(sys.stdin).get('sessionId',''))" 2>/dev/null)
       echo -e "${CYAN}$content${NC}" ;;
  esac
done