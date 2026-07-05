package com.your.agent.core.tool;

import com.your.agent.core.model.ToolCall;

/**
 * 工具执行器接口 —— 接收ToolCall并执行，返回结果字符串。
 * <p>
 * 核心模块只定义接口，具体实现在Spring Starter/Business模块中完成。
 * 这种分层保证了core模块零Spring依赖。
 */
public interface ToolExecutor {

    /**
     * 执行一个工具调用。
     *
     * @param toolCall LLM发起的工具调用请求
     * @return 工具执行结果（字符串格式，将被追加回LLM上下文中作为Observation）
     * @throws Exception 工具执行过程中的任何异常
     */
    String execute(ToolCall toolCall) throws Exception;
}