#!/bin/bash
# Phoenix WeChat Connector
set -euo pipefail
mkdir -p "$HOME/.phoenix"
QR="$HOME/.phoenix/wx_qr.png"
LOG="$HOME/.phoenix/wx.log"
PID="$HOME/.phoenix/wx.pid"
BRIDGE="$HOME/.phoenix/bridge.py"
GREEN="[0;32m"; CYAN="[0;36m"; YELLOW="[1;33m"; NC="[0m"
cmd_login(){
  python3 -c "import itchat" 2>/dev/null || pip3 install itchat-uos -q --break-system-packages
  cat > "$BRIDGE" << "PYEOF"
import sys,threading,os,json,urllib.request
QR=os.path.expanduser("~/.phoenix/wx_qr.png")
import itchat
from itchat.content import TEXT
def on_text(m):
 u=m["User"]["NickName"]or m["User"]["UserName"]
 c=m["Text"]
 print(f"[WX] {u}: {c}",flush=True)
 try:
  d=json.dumps({"content":c}).encode()
  r=urllib.request.urlopen(urllib.request.Request("http://localhost:8080/api/agent/chat",data=d,headers={"Content-Type":"application/json"}),timeout=60)
  rp=json.loads(r.read()).get("content","")
  if rp: itchat.send(rp,toUserName=m["FromUserName"])
 except Exception as e: print(f"[API] {e}",flush=True)
print("STATUS:qr",flush=True)
itchat.auto_login(enableCmdQR=2,hotReload=True,picDir=QR)
print("STATUS:connected",flush=True)
itchat.msg_register(TEXT)(on_text)
def reader():
 while True:
  try:
   l=sys.stdin.readline()
   if not l or l.strip()=="exit": itchat.logout();break
  except: break
threading.Thread(target=reader,daemon=True).start()
itchat.run(block=True)
PYEOF
  echo -e "${CYAN}Starting WeChat...${NC}"
  python3 "$BRIDGE" > "$LOG" 2>&1 &
  echo $! > "$PID"
  sleep 4
  if [ -f "$QR" ]; then
   echo -e "\
${CYAN}Scan QR code:${NC}"
   echo -e "${YELLOW}File: $QR${NC}"
   echo -e "${YELLOW}URL: http://localhost:8080/api/channel/wechat/qr/image${NC}\
"
  fi
  echo -e "${CYAN}Waiting for scan...${NC}"
  tail -f "$LOG" &
  TPID=$!
  while true; do
   if grep -q "connected" "$LOG" 2>/dev/null; then
    echo -e "\
${GREEN}WeChat connected!${NC}"; kill $TPID 2>/dev/null; break
   fi; sleep 1
  done
}
cmd_status(){
 if [ -f "$PID" ] && kill -0 \$(cat "$PID") 2>/dev/null; then
  echo -e "${GREEN}WeChat connected${NC}"; tail -3 "$LOG" 2>/dev/null
 else echo -e "${YELLOW}Not connected${NC}"; fi
}
cmd_logout(){
 [ -f "$PID" ] && kill \$(cat "$PID") 2>/dev/null && rm -f "$PID" && echo -e "${GREEN}Logged out${NC}" || echo -e "${YELLOW}Not connected${NC}"
}
case "\${1:-help}" in
 login) cmd_login ;;
 status) cmd_status ;;
 logout) cmd_logout ;;
 *) echo "Usage: phoenix-wechat.sh login|status|logout" ;;
esac
