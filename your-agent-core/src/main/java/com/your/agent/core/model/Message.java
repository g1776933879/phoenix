package com.your.agent.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * 统一消息体 —— 整个Agent框架的核心协议数据单元。
 * <p>
 * 借鉴Codex JSON-RPC设计理念，消息在整个ReAct循环中流转。
 * role决定消息类型：
 * <ul>
 *   <li>"user"      —— 用户输入</li>
 *   <li>"assistant" —— AI回复（可携带 toolCalls）</li>
 *   <li>"tool"      —— 工具执行结果（需回带 toolCallId）</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Message {

    /** 消息唯一ID */
    @Builder.Default
    private String id = UUID.randomUUID().toString();

    /** 消息角色：user | assistant | tool */
    private String role;

    /** 文本内容 */
    private String content;

    /** 仅assistant消息可用：代理请求的工具调用列表 */
    private List<ToolCall> toolCalls;

    /** 仅tool消息可用：对应工具调用的ID */
    private String toolCallId;

    /** 消息时间戳 */
    @Builder.Default
    private long timestamp = System.currentTimeMillis();

    /**
     * 快速创建一个用户消息
     * @param content 用户输入文本
     * @return Message实例
     */
    public static Message userMessage(String content) {
        return Message.builder()
                .role("user")
                .content(content)
                .build();
    }

    /**
     * 快速创建一个工具响应消息
     * @param toolCallId 对应工具调用的ID
     * @param content    工具执行返回内容
     * @return Message实例
     */
    public static Message toolMessage(String toolCallId, String content) {
        return Message.builder()
                .role("tool")
                .toolCallId(toolCallId)
                .content(content)
                .build();
    }
}