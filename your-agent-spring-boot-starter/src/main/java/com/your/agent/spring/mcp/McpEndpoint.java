package com.your.agent.spring.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.your.agent.core.loop.ReActEngine;
import com.your.agent.core.mcp.McpRegistry;
import com.your.agent.spring.session.SessionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * MCP 端点 —— 凤凰暴露给 Hermes 的 MCP 协议接口。
 * Hermes 可以通过 MCP 协议调用凤凰的工具。
 */
@Slf4j
@RestController
@RequestMapping("/mcp")
public class McpEndpoint {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final ReActEngine reActEngine;
    private final McpRegistry mcpRegistry;
    private final SessionManager sessionManager;

    public McpEndpoint(ReActEngine reActEngine, McpRegistry mcpRegistry, SessionManager sessionManager) {
        this.reActEngine = reActEngine;
        this.mcpRegistry = mcpRegistry;
        this.sessionManager = sessionManager;
        log.info("McpEndpoint initialized at /mcp (Phoenix ↔ Hermes)");
    }

    @PostMapping
    public ResponseEntity<?> handleMcp(@RequestBody Map<String, Object> body) {
        String method = (String) body.get("method");
        String id = (String) body.get("id");
        Map<String, Object> params = (Map<String, Object>) body.get("params");

        if (method == null) {
            return ResponseEntity.badRequest().body(error(id, -32600, "Invalid Request: method required"));
        }

        try {
            return switch (method) {
                case "tools/list" -> handleToolsList(id);
                case "tools/call" -> handleToolCall(id, params);
                default -> ResponseEntity.ok(error(id, -32601, "Method not found: " + method));
            };
        } catch (Exception e) {
            log.error("MCP error", e);
            return ResponseEntity.ok(error(id, -32603, e.getMessage()));
        }
    }

    private ResponseEntity<?> handleToolsList(String id) {
        return ResponseEntity.ok(result(id, mcpRegistry.getToolsList()));
    }

    private ResponseEntity<?> handleToolCall(String id, Map<String, Object> params) throws Exception {
        if (params == null) return ResponseEntity.ok(error(id, -32602, "Invalid params"));

        String toolName = (String) params.get("name");
        Map<String, Object> args = (Map<String, Object>) params.get("arguments");

        if (toolName == null) return ResponseEntity.ok(error(id, -32602, "Tool name required"));

        String result = switch (toolName) {
            case "phoenix_chat" -> {
                String content = args != null ? (String) args.get("content") : "";
                if (content == null || content.isEmpty()) yield "content required";
                var response = reActEngine.run(content);
                yield response.getFinalAnswer();
            }
            case "phoenix_evolve" -> {
                yield "Evolution triggered via MCP";
            }
            case "phoenix_memory" -> {
                String query = args != null ? (String) args.get("query") : "";
                yield "Memory search: " + query;
            }
            case "phoenix_skills" -> "凤凰已注册工具列表";
            case "phoenix_sessions" -> {
                var sessions = sessionManager.getSessions();
                yield sessions.size() + " 个会话";
            }
            default -> "Unknown tool: " + toolName;
        };

        var contentNode = MAPPER.createObjectNode();
        contentNode.put("type", "text");
        contentNode.put("text", result);

        var resultNode = MAPPER.createObjectNode();
        resultNode.set("content", MAPPER.createArrayNode().add(contentNode));

        return ResponseEntity.ok(result(id, resultNode));
    }

    private Map<String, Object> result(String id, Object result) {
        return Map.of("jsonrpc", "2.0", "id", id, "result", result);
    }

    private Map<String, Object> error(String id, int code, String message) {
        return Map.of("jsonrpc", "2.0", "id", id, "error", Map.of("code", code, "message", message));
    }
}