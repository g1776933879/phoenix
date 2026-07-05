package com.your.agent.channel.wechat;

import com.your.agent.core.loop.ReActEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 微信扫码连接REST端点 —— 提供二维码展示和连接状态查询。
 */
@Slf4j
@RestController
@RequestMapping("/api/channel/wechat")
public class WeChatController {

    private final ConcurrentHashMap<String, String> linkTokens = new ConcurrentHashMap<>();
    private final WeChatBridge weChatBridge;
    private final ReActEngine reActEngine;

    public WeChatController(WeChatBridge weChatBridge, ReActEngine reActEngine) {
        this.weChatBridge = weChatBridge;
        this.reActEngine = reActEngine;
    }

    @GetMapping("/qr")
    public ResponseEntity<Map<String, Object>> getQRCode() {
        String token = UUID.randomUUID().toString();
        linkTokens.put(token, "pending");
        // 返回二维码图片（实际使用时替换为itchat生成的二维码路径）
        return ResponseEntity.ok(Map.of(
            "token", token,
            "status", "pending",
            "message", "请使用微信扫描下方二维码登录",
            "qrImage", "/api/channel/wechat/qr/image"
        ));
    }

    @GetMapping("/qr/image")
    public ResponseEntity<Map<String, Object>> getQRImage() {
        return ResponseEntity.ok(Map.of(
            "qrPath", weChatBridge.getLoginQrPath(),
            "note", "手机微信扫码，或浏览器打开链接登录"
        ));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(Map.of(
            "connected", weChatBridge.isConnected(),
            "qrPath", weChatBridge.getLoginQrPath(),
            "message", weChatBridge.isConnected() ? "✅ 微信已连接" : "⏳ 等待扫码登录"
        ));
    }
}