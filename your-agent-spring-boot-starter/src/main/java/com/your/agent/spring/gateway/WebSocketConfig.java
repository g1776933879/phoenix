package com.your.agent.spring.gateway;

import com.your.agent.spring.config.AgentProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final WebSocketGateway webSocketGateway;
    private final AgentProperties properties;
    public WebSocketConfig(WebSocketGateway webSocketGateway, AgentProperties properties) {
        this.webSocketGateway = webSocketGateway; this.properties = properties;
    }
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(webSocketGateway, properties.getWebsocket().getEndpoint()).setAllowedOrigins(properties.getWebsocket().getAllowedOrigins());
        registry.addHandler(webSocketGateway, properties.getWebsocket().getEndpoint() + "/sockjs").setAllowedOrigins(properties.getWebsocket().getAllowedOrigins()).withSockJS();
    }
}
