package com.your.agent.channel.wechat;

import com.your.agent.core.loop.ReActEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/channel/wechat")
public class WeChatController {

    private final WeChatBridge weChatBridge;

    public WeChatController(WeChatBridge weChatBridge) {
        this.weChatBridge = weChatBridge;
    }

    @GetMapping("/qr")
    public ResponseEntity<Map<String, Object>> getQR() {
        String path = weChatBridge.getLoginQrPath();
        boolean connected = weChatBridge.isConnected();
        return ResponseEntity.ok(Map.of(
            "connected", connected,
            "qrPath", path,
            "status", weChatBridge.getStatusMessage(),
            "hasQR", path != null && !path.isEmpty(),
            "qrUrl", path != null && !path.isEmpty() ? "/api/channel/wechat/qr/image" : null
        ));
    }

    @GetMapping("/qr/image")
    public ResponseEntity<Resource> getQRImage() {
        String path = weChatBridge.getLoginQrPath();
        if (path == null || path.isEmpty() || !Files.exists(Paths.get(path))) {
            return ResponseEntity.notFound().build();
        }
        try {
            Resource resource = new FileSystemResource(path);
            return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(resource);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of(
            "connected", weChatBridge.isConnected(),
            "qrPath", weChatBridge.getLoginQrPath(),
            "status", weChatBridge.getStatusMessage()
        ));
    }
}