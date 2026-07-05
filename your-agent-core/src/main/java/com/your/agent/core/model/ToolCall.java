package com.your.agent.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 工具调用请求 —— LLM给出"我想调用某个工具"时解析出的结构化指令。
 * <p>
 * 对应OpenAI Function Calling规范中的 tool_calls 条目，
 * 框架内部统一用此模型表达。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ToolCall {

    /** 工具调用的唯一标识符（用于后续 tool 消息回带） */
    private String id;

    /** 工具名称（与Tool注解的name对应） */
    private String name;

    /** 工具参数（JSON格式的键值对） */
    private Map<String, Object> arguments;

    /** 是否标记为需要人工审批 */
    @Builder.Default
    private boolean requireApproval = false;
}