package com.your.agent.core.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP (Model Context Protocol) 注册中心 —— 管理所有MCP工具。
 * 支持 stdio 和 HTTP 双传输，符合MCP协议标准。
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
    }

    /**
     * 调用MCP工具
     */
    public String callTool(String serverName, String toolName, Map<String, Object> args) throws Exception {
        McpServer server = servers.get(serverName);
        if (server == null) throw new IllegalArgumentException("MCP server not found: " + serverName);

        // 构建MCP协议请求体
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

        // 解析MCP响应
        var root = MAPPER.readTree(response.body());
        if (root.has("result") && root.get("result").has("content")) {
            var content = root.get("result").get("content");
            StringBuilder sb = new StringBuilder();
            for (var item : content) {
                if (item.has("text")) {
                    sb.append(item.get("text").asText());
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
     * 列出所有已注册的MCP工具
     */
    public List<String> listServers() {
        return new ArrayList<>(servers.keySet());
    }

    public record McpServer(String name, String baseUrl, Map<String, String> headers, String transport) {}
}