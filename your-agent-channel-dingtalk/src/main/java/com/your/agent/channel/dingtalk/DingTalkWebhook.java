package com.your.agent.channel.dingtalk;

import com.your.agent.core.loop.ReActEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/api/channel/dingtalk")
public class DingTalkWebhook {

    private final ReActEngine reActEngine;

    public DingTalkWebhook(ReActEngine reActEngine) {
        this.reActEngine = reActEngine;
    }

    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> webhook(@RequestBody Map<String, Object> body) {
        String text = null;
        if (body.containsKey("text")) {
            Map<String, Object> t = (Map<String, Object>) body.get("text");
            text = (String) t.get("content");
        }
        if (text == null) return ResponseEntity.ok(Map.of("errcode", 0));

        String finalText = text;
        CompletableFuture.runAsync(() -> {
            try {
                var response = reActEngine.run(finalText);
                log.info("DingTalk reply: {}", response.getFinalAnswer());
            } catch (Exception e) {
                log.error("DingTalk error", e);
            }
        });
        return ResponseEntity.ok(Map.of("errcode", 0));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of("status", "钉钉 webhook ready", "endpoint", "POST /api/channel/dingtalk/webhook"));
    }
}