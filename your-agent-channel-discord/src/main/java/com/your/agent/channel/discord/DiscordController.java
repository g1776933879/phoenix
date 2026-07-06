package com.your.agent.channel.discord;

import com.your.agent.core.loop.ReActEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/api/channel/discord")
public class DiscordController {

    private final ReActEngine reActEngine;
    private final DiscordConfig config;

    public DiscordController(ReActEngine reActEngine, DiscordConfig config) {
        this.reActEngine = reActEngine;
        this.config = config;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        String token = config.getBotToken();
        boolean configured = token != null && !token.isBlank();
        return ResponseEntity.ok(Map.of(
            "configured", configured,
            "status", configured ? "✅ 已配置" : "⏳ 未配置",
            "inviteUrl", "https://discord.com/oauth2/authorize?client_id=YOUR_CLIENT_ID&scope=bot&permissions=3072"
        ));
    }

    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> webhook(@RequestBody Map<String, Object> body) {
        String content = (String) body.get("content");
        if (content == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "content required"));
        }
        CompletableFuture.runAsync(() -> {
            try {
                var response = reActEngine.run(content);
                log.info("Discord webhook reply: {}", response.getFinalAnswer());
            } catch (Exception e) {
                log.error("Discord webhook error", e);
            }
        });
        return ResponseEntity.ok(Map.of("status", "processing"));
    }
}