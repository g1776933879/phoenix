package com.your.agent.channel.wechat;

import com.your.agent.core.loop.ReActEngine;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class WeChatBridge {

    private final ReActEngine reActEngine;
    private Process bridgeProcess;
    private Thread readerThread;
    private volatile boolean running = false;
    private volatile boolean connected = false;
    private volatile String loginQrPath = "";
    private volatile String statusMessage = "初始化中...";

    public boolean isConnected() { return connected; }
    public String getLoginQrPath() { return loginQrPath; }
    public String getStatusMessage() { return statusMessage; }

    public WeChatBridge(ReActEngine reActEngine) {
        this.reActEngine = reActEngine;
    }

    @PostConstruct
    public void start() {
        running = true;
        CompletableFuture.runAsync(this::startBridge);
        log.info("WeChatBridge initializing...");
    }

    private void startBridge() {
        try {
            // 检查 Python 和 itchat
            Process check = Runtime.getRuntime().exec(new String[]{"python3", "-c", "import itchat; print('ok')"});
            boolean ok = check.waitFor(10, TimeUnit.SECONDS);
            if (!ok || check.exitValue() != 0) {
                log.warn("Installing itchat-uos...");
                statusMessage = "安装 itchat-uos...";
                Process install = Runtime.getRuntime().exec(new String[]{
                    "pip3", "install", "itchat-uos", "-q", "--break-system-packages"
                });
                install.waitFor(30, TimeUnit.SECONDS);
            }

            // 生成桥接脚本到临时目录
            String script = createBridgeScript();
            String scriptPath = "/tmp/wechat_bridge.py";
            try (FileWriter fw = new FileWriter(scriptPath)) {
                fw.write(script);
            }

            // 启动 Python 进程
            ProcessBuilder pb = new ProcessBuilder("python3", scriptPath);
            pb.redirectErrorStream(true);
            bridgeProcess = pb.start();

            log.info("WeChat bridge started");
            statusMessage = "等待扫码登录...";

            // 读取输出
            readerThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(bridgeProcess.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while (running && (line = reader.readLine()) != null) {
                        handleOutput(line);
                    }
                } catch (IOException e) {
                    if (running) log.error("WeChat reader error", e);
                }
            }, "wc-reader");
            readerThread.setDaemon(true);
            readerThread.start();

        } catch (Exception e) {
            log.error("WeChat bridge failed", e);
            statusMessage = "启动失败: " + e.getMessage();
        }
    }

    private void handleOutput(String line) {
        if (line.startsWith("QR_PATH:")) {
            loginQrPath = line.substring(8).trim();
            log.info("📱 QR code saved: {}", loginQrPath);
            statusMessage = "请扫描二维码登录微信";
        } else if (line.startsWith("MSG:")) {
            String[] parts = line.substring(4).split(":", 2);
            if (parts.length == 2) {
                log.info("📩 WeChat from {}: {}", parts[0], parts[1]);
                processMessage(parts[0], parts[1]);
            }
        } else if (line.startsWith("LOGIN_OK")) {
            connected = true;
            statusMessage = "✅ 微信已连接";
            log.info("✅ WeChat logged in");
        } else if (line.startsWith("ERROR:")) {
            log.error("WeChat error: {}", line.substring(6));
            statusMessage = "错误: " + line.substring(6);
        } else if (line.startsWith("STATUS:")) {
            statusMessage = line.substring(7);
        } else {
            log.debug("WeChat: {}", line);
        }
    }

    private void processMessage(String fromUser, String content) {
        CompletableFuture.runAsync(() -> {
            try {
                var response = reActEngine.run(content);
                String answer = response.getFinalAnswer();
                sendToBridge("REPLY:" + fromUser + ":" + (answer != null ? answer : ""));
            } catch (Exception e) {
                sendToBridge("REPLY:" + fromUser + ":❌ " + e.getMessage());
            }
        });
    }

    private void sendToBridge(String cmd) {
        if (bridgeProcess != null && bridgeProcess.isAlive()) {
            try {
                OutputStreamWriter w = new OutputStreamWriter(
                    bridgeProcess.getOutputStream(), StandardCharsets.UTF_8);
                w.write(cmd + "\n");
                w.flush();
            } catch (IOException e) {
                log.error("Send to bridge failed", e);
            }
        }
    }

    private String createBridgeScript() {
        return """
import sys, threading, os, time

qr_path = "/tmp/wechat_qr.png"

try:
    import itchat
    from itchat.content import TEXT
except ImportError:
    print("ERROR:itchat not installed")
    sys.exit(1)

def on_text(msg):
    from_user = msg['User']['NickName'] or msg['User']['UserName']
    content = msg['Text']
    print(f"MSG:{from_user}:{content}", flush=True)

def reader():
    while True:
        try:
            line = sys.stdin.readline()
            if not line: break
            line = line.strip()
            if line.startswith("REPLY:"):
                parts = line[6:].split(":", 1)
                if len(parts) == 2:
                    itchat.send(parts[1], toUserName=parts[0])
        except:
            break

# Force QR code to save as image
itchat.auto_login(enableCmdQR=2, hotReload=True, picDir=qr_path)
print("LOGIN_OK", flush=True)

t = threading.Thread(target=reader, daemon=True)
t.start()
itchat.run(block=True)
""";
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (bridgeProcess != null && bridgeProcess.isAlive()) {
            bridgeProcess.destroyForcibly();
        }
        log.info("WeChatBridge stopped");
    }
}