package com.your.agent.core.loop;

import com.your.agent.core.model.ToolCall;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Action —— ReAct循环中的"行动"步骤模型。
 * <p>
 * 当LLM决定调用某个工具时，Thought之后产生Action。
 * Action是对ToolCall的进一步封装，包含执行前后的状态追踪。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Action {

    /** 对应的工具调用ID */
    private String toolCallId;

    /** 工具名称 */
    private String toolName;

    /** 工具参数 */
    private Map<String, Object> arguments;

    /** 执行前的状态：pending | approved | rejected | running | completed | failed */
    private String status;

    /** 执行结果 */
    private String result;

    /** 执行耗时（毫秒） */
    private long durationMs;

    /** 错误信息（如果失败） */
    private String errorMessage;

    /** 是否需要人工审批 */
    @Builder.Default
    private boolean requiresApproval = false;

    /** 审批ID（如果触发了审批流程） */
    private String approvalId;

    /**
     * 从ToolCall快速创建Action
     */
    public static Action fromToolCall(ToolCall toolCall) {
        return Action.builder()
                .toolCallId(toolCall.getId())
                .toolName(toolCall.getName())
                .arguments(toolCall.getArguments())
                .status("pending")
                .requiresApproval(toolCall.isRequireApproval())
                .build();
    }

    /**
     * 标记为"运行中"
     */
    public Action markRunning() {
        this.status = "running";
        return this;
    }

    /**
     * 标记为"已完成"
     */
    public Action markCompleted(String result, long durationMs) {
        this.status = "completed";
        this.result = result;
        this.durationMs = durationMs;
        return this;
    }

    /**
     * 标记为"失败"
     */
    public Action markFailed(String error, long durationMs) {
        this.status = "failed";
        this.errorMessage = error;
        this.durationMs = durationMs;
        return this;
    }
}