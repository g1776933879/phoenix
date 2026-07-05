package com.your.agent.core.tool;

import com.your.agent.core.model.ToolCall;

/**
 * 工具注册中心 —— 管理所有可用工具的注册与查找。
 * <p>
 * 支持通过名称查找工具定义，以及获取所有工具定义的JSON Schema列表（用于发送给LLM）。
 */
public interface ToolRegistry {

    /**
     * 根据工具名称执行工具。
     *
     * @param toolCall 工具调用请求
     * @return 工具执行结果
     * @throws Exception 工具未找到或执行失败
     */
    String execute(ToolCall toolCall) throws Exception;

    /**
     * 获取所有工具的JSON Schema列表。
     * 每个元素是一个OpenAI tool definition格式的JSON字符串，
     * 用于在chat请求中告知LLM有哪些工具可用。
     *
     * @return 工具定义JSON字符串列表
     */
    java.util.List<String> getToolDefinitions();

    /**
     * 检查某个工具是否需要审批。
     *
     * @param toolName 工具名称
     * @return true表示需要人工审批
     */
    boolean requiresApproval(String toolName);
}