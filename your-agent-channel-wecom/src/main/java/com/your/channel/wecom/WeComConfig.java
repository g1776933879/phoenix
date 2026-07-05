package com.your.channel.wecom;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "your.agent.channel.wecom")
public class WeComConfig {
    private String token = "perpetualMotion";
    private String corpId = "";
    private String agentId = "";
    private String secret = "";
}