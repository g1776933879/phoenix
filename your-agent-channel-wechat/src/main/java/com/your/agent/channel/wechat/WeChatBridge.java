package com.your.agent.channel.wechat;

import com.your.agent.core.loop.ReActEngine;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 个人微信桥接器 —— 通过itchat-uos协议连接个人微信。
 * 扫码登录后，自动接收消息并调用ReActEngine回复。
 */
@Slf4j
@Component
public class WeChatBridge {

    private final ReActEngine reActEngine;
    private Process bridgeProcess;
    private Thread readerThread;
    private volatile boolean running = false;
    private volatile boolean connected = false;
    private String loginQrPath = "";

    public boolean isConnected() { return connected; }
    public String getLoginQrPath() { return loginQrPath; }

    public WeChatBridge(ReActEngine reActEngine) {
        this.reActEngine = reActEngine;
    }

    @PostConstruct
    public void start() {
        running = true;
        // 在单独线程中启动Python桥接进程
        CompletableFuture.runAsync(this::startBridge);
        log.info("WeChatBridge initializing...");
    }

    private void startBridge() {
        try {
            // 检查Python环境和itchat
            Process checkProcess = Runtime.getRuntime().exec(
                new String[]{"python3", "-c", "import itchat"}
            );
            boolean hasItchat = checkProcess.waitFor(5, TimeUnit.SECONDS);

            if (!hasItchat || checkProcess.exitValue() != 0) {
                log.warn("itchat not found, installing...");
                Runtime.getRuntime().exec(new String[]{
                    "pip3", "install", "itchat-uos", "--quiet"
                }).waitFor(10, TimeUnit.SECONDS);
            }

            // 启动桥接Python进程
            String bridgeScript = createBridgeScript();
            File scriptFile = new File(System.getProperty("java.io.tmpdir"), "wechat_bridge.py");
            try (FileWriter fw = new FileWriter(scriptFile)) {
                fw.write(bridgeScript);
            }

            ProcessBuilder pb = new ProcessBuilder("python3", scriptFile.getAbsolutePath());
            pb.redirectErrorStream(true);
            bridgeProcess = pb.start();

            log.info("WeChat bridge process started, waiting for QR code...");

            // 读取进程输出（包含QR码路径和消息）
            readerThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(bridgeProcess.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while (running && (line = reader.readLine()) != null) {
                        handleBridgeOutput(line);
                    }
                } catch (IOException e) {
                    log.error("WeChat bridge reader error", e);
                }
            }, "wechat-bridge-reader");
            readerThread.setDaemon(true);
            readerThread.start();

            // 等待登录完成
            log.info("请扫描二维码登录微信: {}", loginQrPath);

        } catch (Exception e) {
            log.error("Failed to start WeChat bridge", e);
        }
    }

    private void handleBridgeOutput(String line) {
        if (line.startsWith("QR:")) {
            loginQrPath = line.substring(3);
            log.info("📱 微信登录二维码: {}", loginQrPath);
        } else if (line.startsWith("MSG:")) {
            // 消息格式: MSG:fromUser:content
            String[] parts = line.substring(4).split(":", 2);
            if (parts.length == 2) {
                String fromUser = parts[0];
                String content = parts[1];
                log.info("📩 微信消息 from {}: {}", fromUser, content);
                processWeChatMessage(fromUser, content);
            }
        } else if (line.startsWith("LOGIN_OK")) {
            connected = true;
            log.info("✅ 微信登录成功！");
        } else if (line.startsWith("ERROR:")) {
            log.error("WeChat bridge error: {}", line.substring(6));
        }
    }

    private void processWeChatMessage(String fromUser, String content) {
        CompletableFuture.runAsync(() -> {
            try {
                var response = reActEngine.run(content);
                String answer = response.getFinalAnswer();
                if (answer == null || answer.isEmpty()) answer = "(空回复)";
                // 通过进程stdin发送回复
                sendToBridge("REPLY:" + fromUser + ":" + answer);
                log.info("✅ WeChat reply sent to {}, iterations={}", fromUser, response.getIterations());
            } catch (Exception e) {
                log.error("WeChat message processing failed", e);
                sendToBridge("REPLY:" + fromUser + ":❌ 处理出错: " + e.getMessage());
            }
        });
    }

    private void sendToBridge(String command) {
        if (bridgeProcess != null && bridgeProcess.isAlive()) {
            try {
                OutputStreamWriter writer = new OutputStreamWriter(
                    bridgeProcess.getOutputStream(), StandardCharsets.UTF_8);
                writer.write(command + "\n");
                writer.flush();
            } catch (IOException e) {
                log.error("Failed to send to bridge", e);
            }
        }
    }

    private String createBridgeScript() {
        return """
import sys, json, threading, time, os, base64
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

def reader_thread():
    while True:
        try:
            line = sys.stdin.readline()
            if not line:
                break
            line = line.strip()
            if line.startswith("REPLY:"):
                parts = line[6:].split(":", 1)
                if len(parts) == 2:
                    itchat.send(parts[1], toUserName=parts[0])
        except:
            break

itchat.auto_login(enableCmdQR=2, hotReload=True, printReload2=False)
print("LOGIN_OK", flush=True)

t = threading.Thread(target=reader_thread, daemon=True)
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