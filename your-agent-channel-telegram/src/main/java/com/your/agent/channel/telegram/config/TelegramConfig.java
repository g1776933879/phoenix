package com.your.agent.channel.telegram.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "your.agent.channel.telegram")
public class TelegramConfig {
    private String botToken = "";
    private String botUsername = "perpetual_motion_bot";
}