package com.your.agent.channel.slack;

import com.your.agent.core.loop.ReActEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/api/channel/slack")
public class SlackWebhook {

    private final ReActEngine reActEngine;

    public SlackWebhook(ReActEngine reActEngine) {
        this.reActEngine = reActEngine;
    }

    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> webhook(@RequestBody Map<String, Object> body) {
        String challenge = (String) body.get("challenge");
        if (challenge != null) return ResponseEntity.ok(Map.of("challenge", challenge));

        String text = null;
        if (body.containsKey("event")) {
            Map<String, Object> event = (Map<String, Object>) body.get("event");
            text = (String) event.get("text");
        }
        if (text == null) return ResponseEntity.ok(Map.of("ok", true));

        String finalText = text;
        CompletableFuture.runAsync(() -> {
            try {
                var response = reActEngine.run(finalText);
                log.info("Slack reply: {}", response.getFinalAnswer());
            } catch (Exception e) {
                log.error("Slack error", e);
            }
        });
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of("status", "Slack webhook ready", "endpoint", "POST /api/channel/slack/webhook"));
    }
}