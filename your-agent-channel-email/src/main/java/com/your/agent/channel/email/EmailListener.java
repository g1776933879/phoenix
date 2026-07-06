package com.your.agent.channel.email;

import com.your.agent.core.loop.ReActEngine;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class EmailListener {

    private final ReActEngine reActEngine;
    private final EmailConfig config;
    private ScheduledExecutorService scheduler;

    public EmailListener(ReActEngine reActEngine, EmailConfig config) {
        this.reActEngine = reActEngine;
        this.config = config;
    }

    @PostConstruct
    public void start() {
        if (config.getImapHost() == null || config.getImapHost().isBlank()) {
            log.warn("Email channel not configured, skipping");
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::checkMail, 10, 60, TimeUnit.SECONDS);
        log.info("EmailListener started: imap={}", config.getImapHost());
    }

    private void checkMail() {
        log.debug("Checking email...");
    }

    @PreDestroy
    public void stop() {
        if (scheduler != null) scheduler.shutdown();
    }
}