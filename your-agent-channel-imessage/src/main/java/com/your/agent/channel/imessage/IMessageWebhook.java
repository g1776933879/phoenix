package com.your.agent.channel.imessage;

import com.your.agent.core.loop.ReActEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/api/channel/imessage")
public class IMessageWebhook {

    private final ReActEngine reActEngine;

    public IMessageWebhook(ReActEngine reActEngine) {
        this.reActEngine = reActEngine;
    }

    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> webhook(@RequestBody Map<String, Object> body) {
        String text = (String) body.get("text");
        String from = (String) body.get("from");
        if (text == null) return ResponseEntity.ok(Map.of("status", "ok"));

        log.info("📩 iMessage from {}: {}", from, text);
        CompletableFuture.runAsync(() -> {
            try {
                var response = reActEngine.run(text);
                log.info("iMessage reply sent: {}", response.getFinalAnswer());
            } catch (Exception e) {
                log.error("iMessage error", e);
            }
        });
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of("status", "iMessage webhook ready", "endpoint", "POST /api/channel/imessage/webhook"));
    }
}