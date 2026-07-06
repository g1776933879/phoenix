package com.your.agent.core.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP (Model Context Protocol) 注册中心 —— 凤凰 ↔ Hermes 双向互通。
 * 支持 HTTP 传输，符合 MCP 协议标准。
 * 凤凰可以调用 Hermes 的渠道工具，Hermes 也可以调用凤凰的工具。
 */
@Slf4j
public class McpRegistry {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final Map<String, McpServer> servers = new ConcurrentHashMap<>();
    private final HttpClient httpClient;

    public McpRegistry() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        log.info("McpRegistry initialized");
    }

    /**
     * 注册一个MCP服务器（HTTP传输）
     */
    public void registerServer(String name, String baseUrl, Map<String, String> headers) {
        servers.put(name, new McpServer(name, baseUrl, headers, "http"));
        log.info("MCP server registered: {} @ {}", name, baseUrl);

        // 注册后自动发现对方工具
        discoverTools(name);
    }

    /**
     * 自动发现 Hermes 的渠道工具
     */
    private void discoverTools(String serverName) {
        McpServer server = servers.get(serverName);
        if (server == null) return;

        try {
            // 调用 MCP tools/list 发现可用工具
            Map<String, Object> body = new HashMap<>();
            body.put("jsonrpc", "2.0");
            body.put("id", "discover_" + System.currentTimeMillis());
            body.put("method", "tools/list");
            body.put("params", new HashMap<>());

            String jsonBody = MAPPER.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(server.baseUrl() + "/mcp"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                var root = MAPPER.readTree(response.body());
                if (root.has("result") && root.get("result").has("tools")) {
                    var tools = root.get("result").get("tools");
                    log.info("Discovered {} tools from {}:", tools.size(), serverName);
                    for (var tool : tools) {
                        String name = tool.has("name") ? tool.get("name").asText() : "?";
                        String desc = tool.has("description") ? tool.get("description").asText() : "";
                        log.info("  📡 {} - {}", name, desc.length() > 60 ? desc.substring(0, 60) + "..." : desc);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to discover tools from {}: {}", serverName, e.getMessage());
        }
    }

    /**
     * 调用MCP工具
     */
    public String callTool(String serverName, String toolName, Map<String, Object> args) throws Exception {
        McpServer server = servers.get(serverName);
        if (server == null) throw new IllegalArgumentException("MCP server not found: " + serverName);

        Map<String, Object> body = new HashMap<>();
        body.put("jsonrpc", "2.0");
        body.put("id", UUID.randomUUID().toString());
        body.put("method", "tools/call");
        Map<String, Object> params = new HashMap<>();
        params.put("name", toolName);
        params.put("arguments", args);
        body.put("params", params);

        String jsonBody = MAPPER.writeValueAsString(body);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(server.baseUrl() + "/mcp"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json");

        if (server.headers() != null) {
            server.headers().forEach(builder::header);
        }

        HttpRequest request = builder.POST(HttpRequest.BodyPublishers.ofString(jsonBody)).build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            return "[MCP Error: HTTP " + response.statusCode() + "]";
        }

        var root = MAPPER.readTree(response.body());
        if (root.has("result") && root.get("result").has("content")) {
            var content = root.get("result").get("content");
            StringBuilder sb = new StringBuilder();
            for (var item : content) {
                if (item.has("text")) sb.append(item.get("text").asText());
                if (item.has("type") && "tool_result".equals(item.get("type").asText())) {
                    if (item.has("content")) sb.append(item.get("content").asText());
                }
            }
            return sb.toString();
        }
        if (root.has("error")) {
            return "[MCP Error: " + root.get("error").get("message").asText() + "]";
        }

        return response.body();
    }

    /**
     * 暴露凤凰自身的工具给 MCP 客户端（如 Hermes）
     */
    public ObjectNode getToolsList() {
        ObjectNode result = MAPPER.createObjectNode();
        ArrayNode tools = MAPPER.createArrayNode();

        // 凤凰暴露给 Hermes 的核心工具
        addToolDef(tools, "phoenix_chat", "向凤凰发送消息并获取回复", createSchema("content", "string", "消息内容"));
        addToolDef(tools, "phoenix_evolve", "触发凤凰自进化", createSchema());
        addToolDef(tools, "phoenix_memory", "查询凤凰的记忆", createSchema("query", "string", "搜索关键词"));
        addToolDef(tools, "phoenix_skills", "列出凤凰的技能", createSchema());
        addToolDef(tools, "phoenix_sessions", "获取凤凰的对话历史", createSchema());

        result.set("tools", tools);
        return result;
    }

    private void addToolDef(ArrayNode tools, String name, String desc, ObjectNode schema) {
        ObjectNode tool = MAPPER.createObjectNode();
        tool.put("name", name);
        tool.put("description", desc);
        tool.set("inputSchema", schema);
        tools.add(tool);
    }

    private ObjectNode createSchema(String... keyValueDesc) {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        if (keyValueDesc.length >= 3) {
            ObjectNode props = MAPPER.createObjectNode();
            for (int i = 0; i < keyValueDesc.length; i += 3) {
                ObjectNode prop = MAPPER.createObjectNode();
                prop.put("type", keyValueDesc[i + 1]);
                prop.put("description", keyValueDesc[i + 2]);
                props.set(keyValueDesc[i], prop);
            }
            schema.set("properties", props);
        }
        return schema;
    }

    public List<String> listServers() { return new ArrayList<>(servers.keySet()); }

    public record McpServer(String name, String baseUrl, Map<String, String> headers, String transport) {}
}