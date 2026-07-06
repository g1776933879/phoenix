package com.your.agent.channel.feishu;

import com.your.agent.core.loop.ReActEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/api/channel/feishu")
public class FeishuWebhook {

    private final ReActEngine reActEngine;

    public FeishuWebhook(ReActEngine reActEngine) {
        this.reActEngine = reActEngine;
    }

    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> webhook(@RequestBody Map<String, Object> body) {
        // 飞书事件订阅验证
        String challenge = (String) body.get("challenge");
        if (challenge != null) return ResponseEntity.ok(Map.of("challenge", challenge));

        String text = null;
        if (body.containsKey("event")) {
            Map<String, Object> event = (Map<String, Object>) body.get("event");
            if (event.containsKey("message")) {
                Map<String, Object> msg = (Map<String, Object>) event.get("message");
                text = (String) msg.get("content");
            }
        }
        if (text == null) return ResponseEntity.ok(Map.of("code", 0));

        String finalText = text;
        CompletableFuture.runAsync(() -> {
            try {
                var response = reActEngine.run(finalText);
                log.info("Feishu reply: {}", response.getFinalAnswer());
            } catch (Exception e) {
                log.error("Feishu error", e);
            }
        });
        return ResponseEntity.ok(Map.of("code", 0));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of("status", "飞书 webhook ready", "endpoint", "POST /api/channel/feishu/webhook"));
    }
}