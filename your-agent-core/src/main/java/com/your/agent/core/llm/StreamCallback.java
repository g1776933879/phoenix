package com.your.agent.core.llm;

/**
 * 流式回调接口 —— 接收LLM逐tokens输出的内容片段。
 * <p>
 * 用于支持SSE和WebSocket流式输出场景。
 * 去掉@FunctionalInterface标注，因默认方法全为default。
 */
public interface StreamCallback {

    default void onNext(String chunk, boolean done) {}

    default void onComplete() {}

    default void onError(Throwable error) {}

    static StreamCallback onChunk(java.util.function.Consumer<String> consumer) {
        return new StreamCallback() {
            @Override
            public void onNext(String chunk, boolean done) {
                consumer.accept(chunk);
            }
        };
    }
}