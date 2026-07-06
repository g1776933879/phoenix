package com.your.agent.spring.channel;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.*;

/**
 * 微信二维码终端 —— 在浏览器中显示微信登录二维码。
 * 访问 http://localhost:8080/api/channel/wechat/qr 查看二维码。
 */
@Slf4j
@RestController
@RequestMapping("/api/channel/wechat")
public class WeChatQRController {

    private static final Path QR_DIR = Paths.get(System.getProperty("user.home"), ".phoenix");
    private static final Path QR_FILE = QR_DIR.resolve("wechat_qr.png");
    private static final Path WECHAT_PKL = QR_DIR.resolve("wechat.pkl");
    private volatile boolean running = false;

    @PostConstruct
    public void init() {
        try { Files.createDirectories(QR_DIR); } catch (Exception e) { log.warn("Failed to create QR dir", e); }
    }

    @GetMapping("/qr/image")
    public ResponseEntity<byte[]> getQRImage() {
        try {
            if (Files.exists(QR_FILE)) {
                byte[] image = Files.readAllBytes(QR_FILE);
                return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(image);
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/qr")
    public ResponseEntity<Map<String, Object>> getQRInfo() {
        boolean exists = Files.exists(QR_FILE);
        boolean loggedIn = Files.exists(WECHAT_PKL);
        return ResponseEntity.ok(Map.of(
            "qrExists", exists,
            "loggedIn", loggedIn,
            "qrImageUrl", "/api/channel/wechat/qr/image",
            "command", "bash phoenix-wechat.sh login",
            "status", loggedIn ? "已登录" : "未登录"
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> startLogin() {
        if (running) {
            return ResponseEntity.ok(Map.of("status", "already_running", "message", "登录进程已在运行"));
        }
        running = true;
        CompletableFuture.runAsync(() -> {
            try {
                ProcessBuilder pb = new ProcessBuilder("bash", "-c",
                    "cd " + System.getProperty("user.home") + "/phoenix && bash phoenix-wechat.sh login");
                pb.inheritIO();
                Process p = pb.start();
                p.waitFor(120, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.error("WeChat login failed", e);
            } finally {
                running = false;
            }
        });
        return ResponseEntity.ok(Map.of("status", "started", "message", "登录进程已启动"));
    }
}