package com.your.agent.spring.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.your.agent.core.mcp.McpRegistry;
import com.your.agent.core.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class McpTools {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final McpRegistry mcpRegistry;

    public McpTools(McpRegistry mcpRegistry) {
        this.mcpRegistry = mcpRegistry;
    }

    @Tool(
        name = "mcp_call",
        description = "通过MCP协议调用外部工具服务，支持任何MCP兼容的服务端",
        parametersSchema = "{\"type\":\"object\",\"properties\":{" +
                "\"server\":{\"type\":\"string\"},\"tool\":{\"type\":\"string\"},\"args\":{\"type\":\"object\"}" +
                "},\"required\":[\"server\",\"tool\",\"args\"]}"
    )
    public String mcpCall(String argsJson) {
        try {
            var node = MAPPER.readTree(argsJson);
            String result = mcpRegistry.callTool(
                node.get("server").asText(),
                node.get("tool").asText(),
                MAPPER.convertValue(node.get("args"), Map.class)
            );
            return result;
        } catch (Exception e) {
            return "[MCP Error] " + e.getMessage();
        }
    }

    @Tool(
        name = "mcp_register",
        description = "注册MCP服务器，注册后可通过mcp_call调用其工具",
        parametersSchema = "{\"type\":\"object\",\"properties\":{" +
                "\"name\":{\"type\":\"string\"},\"url\":{\"type\":\"string\"}" +
                "},\"required\":[\"name\",\"url\"]}"
    )
    public String mcpRegister(String argsJson) {
        try {
            var node = MAPPER.readTree(argsJson);
            mcpRegistry.registerServer(node.get("name").asText(), node.get("url").asText(), Map.of());
            return "✅ 已注册MCP服务器: " + node.get("name").asText();
        } catch (Exception e) {
            return "[Error] " + e.getMessage();
        }
    }
}