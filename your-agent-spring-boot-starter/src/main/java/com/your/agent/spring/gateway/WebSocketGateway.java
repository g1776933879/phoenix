package com.your.agent.spring.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.your.agent.core.loop.ReActEngine;
import com.your.agent.core.model.AgentResponse;
import com.your.agent.core.model.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket网关 —— Agent的统一长连接入口。
 * <p>
 * 客户端通过WebSocket连接后，发送JSON格式消息即可与Agent交互。
 * 支持多会话隔离，每个WebSocket连接独立维护对话上下文。
 * <p>
 * 消息格式：
 * <pre>
 * 请求：{"type":"chat","content":"你好，帮我查一下..."}
 * 响应：{"type":"message","content":"最终答案","intermediateSteps":[...]}
 * 错误：{"type":"error","content":"错误描述"}
 * </pre>
 */
@Slf4j
public class WebSocketGateway extends TextWebSocketHandler {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ReActEngine reActEngine;
    private final Map<String, ReActEngine> sessionEngines = new ConcurrentHashMap<>();

    public WebSocketGateway(ReActEngine reActEngine) {
        this.reActEngine = reActEngine;
        log.info("WebSocketGateway initialized at /ws/agent");
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("WebSocket connected: sessionId={}, remote={}",
                session.getId(), session.getRemoteAddress());
        // 每个连接分配独立的ReActEngine实例（深拷贝同一配置）
        sessionEngines.put(session.getId(), reActEngine);
        sendMessage(session, Map.of("type", "connected", "content", "Agent ready"));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage textMessage) {
        try {
            String payload = textMessage.getPayload();
            log.debug("WebSocket received: sessionId={}, payload={}",
                    session.getId(), payload.length() > 200 ? payload.substring(0, 200) + "..." : payload);

            @SuppressWarnings("unchecked")
            Map<String, Object> request = MAPPER.readValue(payload, Map.class);
            String type = (String) request.getOrDefault("type", "chat");

            if ("chat".equals(type)) {
                String content = (String) request.get("content");
                if (content == null || content.trim().isEmpty()) {
                    sendMessage(session, Map.of("type", "error", "content", "content is required"));
                    return;
                }

                // 执行ReAct循环（异步执行，避免阻塞WebSocket线程）
                ReActEngine engine = sessionEngines.get(session.getId());
                if (engine == null) {
                    engine = reActEngine;
                    sessionEngines.put(session.getId(), engine);
                }

                AgentResponse response = engine.run(content);

                // 发送响应
                sendMessage(session, Map.of(
                        "type", "message",
                        "content", response.getFinalAnswer(),
                        "intermediateSteps", response.getIntermediateSteps(),
                        "iterations", response.getIterations(),
                        "durationMs", response.getDurationMs(),
                        "truncated", response.isTruncated()
                ));

            } else if ("reset".equals(type)) {
                // 重置对话
                ReActEngine engine = sessionEngines.get(session.getId());
                if (engine != null) {
                    engine.resetConversation();
                }
                sendMessage(session, Map.of("type", "connected", "content", "Conversation reset"));

            } else {
                sendMessage(session, Map.of("type", "error", "content", "Unknown type: " + type));
            }

        } catch (Exception e) {
            log.error("WebSocket message handling failed", e);
            sendMessage(session, Map.of("type", "error", "content", "Internal error: " + e.getMessage()));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("WebSocket disconnected: sessionId={}, status={}", session.getId(), status);
        sessionEngines.remove(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("WebSocket transport error: sessionId={}", session.getId(), exception);
    }

    /**
     * 发送JSON消息到客户端
     */
    private void sendMessage(WebSocketSession session, Object data) {
        try {
            String json = MAPPER.writeValueAsString(data);
            session.sendMessage(new TextMessage(json));
        } catch (Exception e) {
            log.error("Failed to send WebSocket message to session={}", session.getId(), e);
        }
    }
}