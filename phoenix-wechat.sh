#!/bin/bash
# 🐦 凤凰 · 微信连接器
# 用法: bash phoenix-wechat.sh login   登录微信
#       bash phoenix-wechat.sh status  查看状态
#       bash phoenix-wechat.sh logout  退出登录

set -euo pipefail

PHOENIX_DIR="$(cd "$(dirname "$0")" && pwd)"
QR_FILE="/tmp/phoenix_wechat_qr.png"
HOTRELOAD_FILE="$HOME/.phoenix/wechat.pkl"
LOG_FILE="/tmp/phoenix_wechat.log"

GREEN='\033[0;32m'
CYAN='\033[0;36m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

ensure_python() {
  if ! command -v python3 &>/dev/null; then
    echo -e "${YELLOW}📦 安装 Python...${NC}"
    pkg install -y python 2>/dev/null || apt-get install -y python3 2>/dev/null
  fi
  if ! python3 -c "import itchat" 2>/dev/null; then
    echo -e "${YELLOW}📦 安装 itchat-uos...${NC}"
    pip3 install itchat-uos -q --break-system-packages 2>/dev/null || pip3 install itchat-uos -q
  fi
}

cmd_login() {
  ensure_python
  echo -e "${CYAN}📱 正在连接微信...${NC}"
  
  # 生成 Python 桥接脚本
  cat > /tmp/phoenix_wechat_bridge.py << 'PYEOF'
import sys, threading, os, time, json, base64

QR_FILE = "/tmp/phoenix_wechat_qr.png"
HOTRELOAD = os.path.expanduser("~/.phoenix/wechat.pkl")

try:
    import itchat
    from itchat.content import TEXT
except ImportError:
    print("ERROR:itchat not installed")
    sys.exit(1)

def on_text(msg):
    from_user = msg['User']['NickName'] or msg['User']['UserName']
    content = msg['Text']
    print(f"[微信] {from_user}: {content}", flush=True)
    # 发送给 Phoenix API
    import urllib.request, json
    try:
        data = json.dumps({"content": content}).encode()
        req = urllib.request.Request(
            "http://localhost:8080/api/agent/chat",
            data=data,
            headers={"Content-Type": "application/json"}
        )
        resp = urllib.request.urlopen(req, timeout=60)
        result = json.loads(resp.read())
        reply = result.get("content", "")
        if reply:
            itchat.send(reply, toUserName=msg['FromUserName'])
            print(f"[凤凰] → {from_user}: {reply[:50]}...", flush=True)
    except Exception as e:
        print(f"[API错误] {e}", flush=True)

# 登录
print("STATUS:正在获取二维码...", flush=True)
itchat.auto_login(
    enableCmdQR=2,
    hotReload=True,
    picDir=QR_FILE,
    statusStorageDir=HOTRELOAD
)
print("STATUS:✅ 微信已连接！", flush=True)
print(f"QR_SAVED:{QR_FILE}", flush=True)

# 注册消息处理器
itchat.msg_register(TEXT)(on_text)

# 启动后台线程读取 stdin 命令
def reader():
    while True:
        try:
            line = sys.stdin.readline()
            if not line: break
            line = line.strip()
            if line == "exit" or line == "logout":
                print("STATUS:退出登录", flush=True)
                itchat.logout()
                break
            elif line.startswith("say:"):
                parts = line[4:].split(":", 1)
                if len(parts) == 2:
                    itchat.send(parts[1], toUserName=parts[0])
        except:
            break

t = threading.Thread(target=reader, daemon=True)
t.start()

itchat.run(block=True)
PYEOF

  # 启动桥接进程
  echo -e "${GREEN}✅ 启动微信桥接...${NC}"
  python3 /tmp/phoenix_wechat_bridge.py 2>&1 | tee "$LOG_FILE" &
  PID=$!
  echo "$PID" > /tmp/phoenix_wechat.pid
  
  # 等待二维码生成
  sleep 3
  
  # 显示二维码
  if [ -f "$QR_FILE" ]; then
    echo -e "\n${CYAN}📱 请用微信扫描此二维码登录：${NC}"
    echo -e "${YELLOW}  图片路径: $QR_FILE${NC}"
    echo -e "${YELLOW}  或用浏览器打开: http://localhost:8080/api/channel/wechat/qr/image${NC}\n"
    
    # 尝试用终端显示二维码
    if command -v python3 &>/dev/null; then
      python3 -c "
try:
    from pyzbar.pyzbar import decode
    from PIL import Image
    img = Image.open('$QR_FILE')
    print('📱 二维码已生成，请用微信扫描')
except:
    print('📱 二维码图片已保存到: $QR_FILE')
" 2>/dev/null || echo "📱 二维码: $QR_FILE"
    fi
  fi
  
  # 等待登录完成
  echo -e "${CYAN}⏳ 等待扫码登录...${NC}"
  tail -f "$LOG_FILE" 2>/dev/null &
  TAIL_PID=$!
  
  # 等待登录成功
  while true; do
    if grep -q "✅ 微信已连接" "$LOG_FILE" 2>/dev/null; then
      echo -e "\n${GREEN}✅ 微信已成功连接！${NC}"
      echo -e "${GREEN}💬 现在可以用微信跟凤凰聊天了！${NC}"
      kill $TAIL_PID 2>/dev/null || true
      break
    fi
    if grep -q "ERROR" "$LOG_FILE" 2>/dev/null; then
      echo -e "\n${RED}❌ 连接失败${NC}"
      kill $TAIL_PID 2>/dev/null || true
      break
    fi
    sleep 1
  done
}

cmd_status() {
  if [ -f /tmp/phoenix_wechat.pid ]; then
    PID=$(cat /tmp/phoenix_wechat.pid)
    if kill -0 "$PID" 2>/dev/null; then
      echo -e "${GREEN}✅ 微信已连接 (PID: $PID)${NC}"
      if [ -f "$LOG_FILE" ]; then
        tail -5 "$LOG_FILE"
      fi
    else
      echo -e "${YELLOW}⚠️ 微信未连接${NC}"
      rm -f /tmp/phoenix_wechat.pid
    fi
  else
    echo -e "${YELLOW}⚠️ 微信未连接${NC}"
  fi
}

cmd_logout() {
  if [ -f /tmp/phoenix_wechat.pid ]; then
    PID=$(cat /tmp/phoenix_wechat.pid)
    kill "$PID" 2>/dev/null || true
    rm -f /tmp/phoenix_wechat.pid
    echo -e "${GREEN}✅ 已退出微信${NC}"
  else
    echo -e "${YELLOW}⚠️ 微信未连接${NC}"
  fi
}

# ===== Main =====
case "${1:-help}" in
  login|connect)
    cmd_login
    ;;
  status)
    cmd_status
    ;;
  logout|disconnect)
    cmd_logout
    ;;
  *)
    echo "🐦 凤凰 · 微信连接器"
    echo ""
    echo "用法:"
    echo "  bash phoenix-wechat.sh login   登录微信"
    echo "  bash phoenix-wechat.sh status  查看状态"
    echo "  bash phoenix-wechat.sh logout  退出登录"
    echo ""
    echo "先启动 Phoenix 服务，再登录微信："
    echo "  1. tmux new-session -d -s phoenix 'bash phoenix.sh'"
    echo "  2. bash phoenix-wechat.sh login"
    ;;
esac