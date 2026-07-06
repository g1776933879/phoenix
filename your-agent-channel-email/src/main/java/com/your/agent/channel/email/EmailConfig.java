package com.your.agent.channel.email;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter @Setter
@Component
@ConfigurationProperties(prefix = "your.agent.channel.email")
public class EmailConfig {
    private String imapHost = "";
    private int imapPort = 993;
    private String username = "";
    private String password = "";
    private String smtpHost = "";
    private int smtpPort = 587;
    private String from = "";
}