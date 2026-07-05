package com.your.business;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 凤凰 —— 企业级AI Agent框架 启动入口。
 * 修复：添加scanBasePackages扫描所有模块的组件。
 * <p>
 * 运行后可通过以下方式与Agent交互：
 * - WebSocket: ws://localhost:8080/ws/agent
 * - REST API:  POST http://localhost:8080/api/agent/chat
 * - SSE流式:   GET http://localhost:8080/api/agent/chat/sse?content=xxx
 */
@SpringBootApplication(scanBasePackages = {
    "com.your.agent",
    "com.your.business",
    "com.your.evolution",
    "com.your.channel"
})
public class AgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentApplication.class, args);
    }
}