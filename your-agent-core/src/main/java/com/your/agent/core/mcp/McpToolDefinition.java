package com.your.agent.core.mcp;

import java.util.Map;

/**
 * MCP (Model Context Protocol) 工具定义 —— 遵循MCP协议标准。
 * 参考: https://modelcontextprotocol.io
 */
public class McpToolDefinition {
    private String name;
    private String description;
    private Map<String, Object> inputSchema;

    public McpToolDefinition() {}
    public McpToolDefinition(String name, String description, Map<String, Object> inputSchema) {
        this.name = name; this.description = description; this.inputSchema = inputSchema;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Map<String, Object> getInputSchema() { return inputSchema; }
    public void setInputSchema(Map<String, Object> inputSchema) { this.inputSchema = inputSchema; }
}