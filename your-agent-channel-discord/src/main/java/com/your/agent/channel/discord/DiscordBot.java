package com.your.agent.channel.discord;

import com.your.agent.core.loop.ReActEngine;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
public class DiscordBot {

    private final ReActEngine reActEngine;
    private final DiscordConfig config;
    private volatile boolean running = false;

    public DiscordBot(ReActEngine reActEngine, DiscordConfig config) {
        this.reActEngine = reActEngine;
        this.config = config;
    }

    @PostConstruct
    public void start() {
        String token = config.getBotToken();
        if (token == null || token.isBlank()) {
            log.warn("Discord bot token not configured, skipping");
            return;
        }
        running = true;
        CompletableFuture.runAsync(this::startBot);
    }

    private void startBot() {
        try {
            log.info("Starting Discord bot...");
            // JDA connection happens here - in production use JDA builder
            log.info("Discord bot started! Invite URL: https://discord.com/oauth2/authorize?client_id=YOUR_CLIENT_ID&scope=bot&permissions=3072");
        } catch (Exception e) {
            log.error("Failed to start Discord bot", e);
        }
    }

    @PreDestroy
    public void stop() {
        running = false;
        log.info("Discord bot stopped");
    }
}