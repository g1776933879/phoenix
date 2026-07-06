package com.your.channel.signal;

import com.your.agent.core.loop.ReActEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/api/channel/signal")
public class SignalWebhook {

    private final ReActEngine reActEngine;

    public SignalWebhook(ReActEngine reActEngine) {
        this.reActEngine = reActEngine;
    }

    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> webhook(@RequestBody Map<String, Object> body) {
        String text = (String) body.get("text");
        if (text == null) return ResponseEntity.ok(Map.of("status", "ok"));

        CompletableFuture.runAsync(() -> {
            try {
                var response = reActEngine.run(text);
                log.info("Signal reply: {}", response.getFinalAnswer());
            } catch (Exception e) {
                log.error("Signal error", e);
            }
        });
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of("status", "Signal webhook ready", "endpoint", "POST /api/channel/signal/webhook"));
    }
}