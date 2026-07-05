package com.your.agent.core.loop;

import com.your.agent.core.model.AgentResponse;
import com.your.agent.core.model.Message;
import com.your.agent.core.model.ToolCall;
import com.your.agent.core.tool.ToolRegistry;
import com.your.agent.core.llm.ModelProvider;
import com.your.agent.core.llm.StreamCallback;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * ReAct引擎 —— Agent的核心执行循环（Thought → Action → Observation）。
 * <p>
 * 支持同步(run)和流式(runStream)两种执行模式。
 * 修复：conversationHistory使用CopyOnWriteArrayList保证线程安全，
 * 流式tool_calls解析使用__TOOL_CALLS__聚合格式。
 */
@Slf4j
public class ReActEngine {

    private final ModelProvider modelProvider;
    private final ToolRegistry toolRegistry;
    private final int maxIterations;
    // 使用CopyOnWriteArrayList保证线程安全，避免并发修改异常
    private final List<Message> conversationHistory = new CopyOnWriteArrayList<>();

    public ReActEngine(
            ModelProvider modelProvider,
            ToolRegistry toolRegistry,
            int maxIterations,
            String systemPrompt
    ) {
        this.modelProvider = modelProvider;
        this.toolRegistry = toolRegistry;
        this.maxIterations = maxIterations > 0 ? maxIterations : 10;

        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            Message sysMsg = new Message();
            sysMsg.setRole("system");
            sysMsg.setContent(systemPrompt);
            conversationHistory.add(sysMsg);
        }

        log.info("ReActEngine initialized: model={}, maxIterations={}",
                modelProvider.modelName(), this.maxIterations);
    }

    public ReActEngine(ModelProvider modelProvider, ToolRegistry toolRegistry) {
        this(modelProvider, toolRegistry, 10, null);
    }

    // ==================== 同步模式 ====================

    public AgentResponse run(String userInput) {
        long startTime = System.currentTimeMillis();
        List<String> intermediateSteps = new ArrayList<>();
        int iterationCount = 0;

        try {
            conversationHistory.add(Message.userMessage(userInput));

            for (int i = 0; i < maxIterations; i++) {
                iterationCount = i + 1;
                log.debug("ReAct iteration {}/{}", iterationCount, maxIterations);

                Message response = modelProvider.chat(
                        conversationHistory,
                        toolRegistry.getToolDefinitions()
                );

                if (response.getContent() != null && !response.getContent().isEmpty()) {
                    intermediateSteps.add("[Thought] " + response.getContent());
                }

                List<ToolCall> toolCalls = response.getToolCalls();
                if (toolCalls == null || toolCalls.isEmpty()) {
                    log.info("ReAct finished after {} iterations", iterationCount);
                    conversationHistory.add(response);
                    return AgentResponse.builder()
                            .finalAnswer(response.getContent())
                            .intermediateSteps(intermediateSteps)
                            .truncated(false)
                            .iterations(iterationCount)
                            .durationMs(System.currentTimeMillis() - startTime)
                            .build();
                }

                conversationHistory.add(response);

                for (ToolCall toolCall : toolCalls) {
                    log.info("Executing tool: name={}, id={}", toolCall.getName(), toolCall.getId());
                    intermediateSteps.add("[Action] Calling tool: " + toolCall.getName());

                    String result;
                    try {
                        result = toolRegistry.execute(toolCall);
                    } catch (Exception e) {
                        log.error("Tool execution failed: {}", toolCall.getName(), e);
                        result = "[Error: " + e.getClass().getSimpleName() + " - " + e.getMessage() + "]";
                    }

                    intermediateSteps.add("[Observation] " + (result.length() > 300
                            ? result.substring(0, 300) + "..."
                            : result));

                    conversationHistory.add(Message.toolMessage(toolCall.getId(), result));
                }
            }

            log.warn("ReAct reached max iterations ({})", maxIterations);
            return AgentResponse.builder()
                    .finalAnswer("[Agent stopped: reached max iterations " + maxIterations + "]")
                    .intermediateSteps(intermediateSteps)
                    .truncated(true)
                    .iterations(iterationCount)
                    .durationMs(System.currentTimeMillis() - startTime)
                    .build();

        } catch (Exception e) {
            log.error("ReActEngine fatal error", e);
            return AgentResponse.builder()
                    .finalAnswer("[Error: " + e.getMessage() + "]")
                    .errorMessage(e.getClass().getName() + ": " + e.getMessage())
                    .iterations(iterationCount)
                    .durationMs(System.currentTimeMillis() - startTime)
                    .build();
        }
    }

    // ==================== 流式模式 ====================

    /**
     * 执行一次完整的ReAct循环（流式模式）。
     * LLM逐token输出实时通过callback推送。
     */
    public void runStream(String userInput, StreamCallback callback) {
        try {
            conversationHistory.add(Message.userMessage(userInput));
            streamLoop(callback, 1);
        } catch (Exception e) {
            log.error("ReActEngine stream fatal error", e);
            callback.onError(e);
        }
    }

    /**
     * 流式递归循环，解析__TOOL_CALLS__聚合格式
     */
    private void streamLoop(StreamCallback outerCallback, int iteration) {
        if (iteration > maxIterations) {
            log.warn("ReAct stream reached max iterations ({})", maxIterations);
            outerCallback.onNext("\n\n[Max iterations reached]", true);
            outerCallback.onComplete();
            return;
        }

        StringBuilder fullContent = new StringBuilder();
        // 标志位：是否在工具调用模式中
        final boolean[] isToolCallMode = {false};
        // 聚合的tool calls数据
        final StringBuilder toolCallBuffer = new StringBuilder();

        modelProvider.chatStream(
                conversationHistory,
                toolRegistry.getToolDefinitions(),
                new StreamCallback() {
                    @Override
                    public void onNext(String chunk, boolean done) {
                        // 检测工具调用标记（聚合格式）
                        if (chunk.startsWith("__TOOL_CALLS__:")) {
                            isToolCallMode[0] = true;
                            toolCallBuffer.append(chunk.substring(15));
                            return;
                        }
                        if (!isToolCallMode[0]) {
                            fullContent.append(chunk);
                            outerCallback.onNext(chunk, done);
                        }
                    }

                    @Override
                    public void onComplete() {
                        if (isToolCallMode[0]) {
                            // 解析__TOOL_CALLS__格式：tc_0|name=toolName|args;tc_1|name=tool2|args2;
                            String raw = toolCallBuffer.toString();
                            String[] entries = raw.split(";");
                            List<ToolCall> pendingToolCalls = new ArrayList<>();
                            for (String entry : entries) {
                                if (entry.trim().isEmpty()) continue;
                                // 格式: tc_0|name=toolName|{"arg":"val"}
                                String[] parts = entry.split("\\|", 3);
                                if (parts.length >= 2) {
                                    String name = "";
                                    String args = "{}";
                                    for (String p : parts) {
                                        if (p.startsWith("name=")) {
                                            name = p.substring(5);
                                        } else if (p.startsWith("{")) {
                                            args = p;
                                        }
                                    }
                                    if (!name.isEmpty()) {
                                        pendingToolCalls.add(ToolCall.builder()
                                                .id("stream_" + pendingToolCalls.size())
                                                .name(name)
                                                .arguments(parseToolArgs(args))
                                                .build());
                                    }
                                }
                            }

                            // 执行工具并继续循环
                            Message assistantMsg = Message.builder()
                                    .role("assistant")
                                    .content(fullContent.toString())
                                    .toolCalls(pendingToolCalls)
                                    .build();
                            conversationHistory.add(assistantMsg);

                            outerCallback.onNext("\n\n--- 🔧 调用工具 ---\n", false);
                            for (ToolCall tc : pendingToolCalls) {
                                outerCallback.onNext("⚙️ " + tc.getName() + "...\n", false);
                                try {
                                    String result = toolRegistry.execute(tc);
                                    conversationHistory.add(Message.toolMessage(tc.getId(), result));
                                    outerCallback.onNext("✅ " + tc.getName() + " 完成\n", false);
                                } catch (Exception e) {
                                    conversationHistory.add(Message.toolMessage(tc.getId(), "[Error: " + e.getMessage() + "]"));
                                    outerCallback.onNext("❌ " + tc.getName() + " 失败: " + e.getMessage() + "\n", false);
                                }
                            }
                            outerCallback.onNext("--- 🔄 继续推理 ---\n\n", false);

                            streamLoop(outerCallback, iteration + 1);
                        } else {
                            // 无工具调用，完成
                            conversationHistory.add(Message.builder()
                                    .role("assistant")
                                    .content(fullContent.toString())
                                    .build());
                            outerCallback.onNext("", true);
                            outerCallback.onComplete();
                        }
                    }

                    @Override
                    public void onError(Throwable error) {
                        log.error("Stream error at iteration {}", iteration, error);
                        outerCallback.onError(error);
                    }
                }
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseToolArgs(String argsStr) {
        try {
            if (argsStr == null || argsStr.isEmpty() || argsStr.equals("{}")) {
                return Collections.emptyMap();
            }
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(argsStr, Map.class);
        } catch (Exception e) {
            log.warn("Failed to parse tool args: {}", argsStr, e);
            return Collections.emptyMap();
        }
    }

    public void resetConversation() {
        conversationHistory.clear();
        log.debug("Conversation history reset");
    }

    public List<Message> getConversationHistory() {
        return new ArrayList<>(conversationHistory);
    }
}