package com.your.agent.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Agent最终响应 —— ReAct循环结束后的统一输出。
 * <p>
 * 包含最终答案文本、中间推理步骤（用于Debug/审计）和原始消息列表。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentResponse {

    /** 最终回复文本 */
    private String finalAnswer;

    /** 中间思考步骤（可选，用于前端流式展示） */
    private List<String> intermediateSteps;

    /** 是否因达到最大迭代次数而截断 */
    @Builder.Default
    private boolean truncated = false;

    /** 总迭代次数 */
    private int iterations;

    /** 异常信息（如果有） */
    private String errorMessage;

    /** 耗时（毫秒） */
    private long durationMs;
}