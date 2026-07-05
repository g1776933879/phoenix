package com.your.agent.core.llm;

import com.your.agent.core.model.Message;

import java.util.List;

/**
 * LLM模型提供者接口 —— 抽象底层模型调用，支持多模型切换。
 * <p>
 * 初期实现可以用OpenAI API / Ollama / 本地Qwen，
 * 通过此接口保证ReActEngine不依赖具体模型实现。
 */
public interface ModelProvider {

    /**
     * 向LLM发送完整对话历史，获取下一步响应（同步非流式）。
     *
     * @param messages 对话历史
     * @param tools    可用工具定义列表
     * @return LLM生成的Message（可能包含toolCalls）
     */
    Message chat(List<Message> messages, List<String> tools);

    /**
     * 向LLM发送完整对话历史，获取流式响应。
     * <p>
     * LLM逐token返回内容，通过callback.onNext()实时推送。
     * 默认实现回退到非流式。
     *
     * @param messages 对话历史
     * @param tools    可用工具定义列表
     * @param callback 流式回调，接收逐片内容
     */
    default void chatStream(List<Message> messages, List<String> tools, StreamCallback callback) {
        try {
            Message result = chat(messages, tools);
            String content = result.getContent() != null ? result.getContent() : "";
            callback.onNext(content, true);
            callback.onComplete();
        } catch (Exception e) {
            callback.onError(e);
        }
    }

    /**
     * 获取当前模型名称（用于日志和监控）
     */
    String modelName();
}