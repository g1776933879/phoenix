package com.your.agent.channel.discord;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "your.agent.channel.discord")
public class DiscordConfig {
    private String botToken = "";
    private String clientId = "";
}