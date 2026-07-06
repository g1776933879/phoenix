package com.your.agent.channel.whatsapp;

import com.your.agent.core.loop.ReActEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/api/channel/whatsapp")
public class WhatsAppWebhook {

    private final ReActEngine reActEngine;

    public WhatsAppWebhook(ReActEngine reActEngine) {
        this.reActEngine = reActEngine;
    }

    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> webhook(@RequestBody Map<String, Object> body) {
        String text = null;
        if (body.containsKey("entry")) {
            var entries = (java.util.List<Map<String, Object>>) body.get("entry");
            if (!entries.isEmpty()) {
                var changes = (java.util.List<Map<String, Object>>) entries.get(0).get("changes");
                if (!changes.isEmpty()) {
                    var value = (Map<String, Object>) changes.get(0).get("value");
                    if (value != null && value.containsKey("messages")) {
                        var msgs = (java.util.List<Map<String, Object>>) value.get("messages");
                        if (!msgs.isEmpty()) text = (String) msgs.get(0).get("text").toString();
                    }
                }
            }
        }
        if (text == null) return ResponseEntity.ok(Map.of("status", "ok"));

        String finalText = text;
        CompletableFuture.runAsync(() -> {
            try {
                var response = reActEngine.run(finalText);
                log.info("WhatsApp reply: {}", response.getFinalAnswer());
            } catch (Exception e) {
                log.error("WhatsApp error", e);
            }
        });
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of("status", "WhatsApp webhook ready", "endpoint", "POST /api/channel/whatsapp/webhook"));
    }
}