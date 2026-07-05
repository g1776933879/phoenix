package com.your.agent.core.loop;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Thought —— ReAct循环中的"思考"步骤模型。
 * <p>
 * 代表Agent在每一轮迭代中的内部推理过程。
 * 用于日志记录、审计追踪和前端流式展示。
 * Thought通常包含：
 * <ul>
 *   <li>当前问题的理解</li>
 *   <li>选择某个工具的原因</li>
 *   <li>对前一步Observation的分析</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Thought {

    /** 思考内容的文本 */
    private String content;

    /** 思考所属的迭代轮次（从1开始） */
    private int iteration;

    /** 思考的时间戳 */
    @Builder.Default
    private long timestamp = System.currentTimeMillis();

    /** 思考类型：reasoning | planning | analyzing | summarizing */
    private String type;

    /** 与此思考相关的工具名称（如果有） */
    private String relatedTool;

    /** 置信度（0.0 ~ 1.0），Agent自我评估 */
    private double confidence;

    /**
     * 快速创建一个简单思考记录
     */
    public static Thought of(String content, int iteration) {
        return Thought.builder()
                .content(content)
                .iteration(iteration)
                .type("reasoning")
                .confidence(0.9)
                .build();
    }

    /**
     * 创建一个带工具关联的思考记录
     */
    public static Thought ofTool(String content, int iteration, String toolName) {
        return Thought.builder()
                .content(content)
                .iteration(iteration)
                .type("planning")
                .relatedTool(toolName)
                .confidence(0.85)
                .build();
    }
}