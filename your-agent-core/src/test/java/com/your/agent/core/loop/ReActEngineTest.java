package com.your.agent.core.loop;

import com.your.agent.core.model.AgentResponse;
import com.your.agent.core.model.Message;
import com.your.agent.core.model.ToolCall;
import com.your.agent.core.tool.ToolRegistry;
import com.your.agent.core.llm.ModelProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * ReActEngine单元测试 —— 验证Thought→Action→Observation主循环的正确性。
 */
@ExtendWith(MockitoExtension.class)
class ReActEngineTest {

    @Mock
    private ModelProvider mockModelProvider;

    @Mock
    private ToolRegistry mockToolRegistry;

    private ReActEngine engine;

    @BeforeEach
    void setUp() {
        // 模拟工具定义
        when(mockToolRegistry.getToolDefinitions()).thenReturn(
                List.of("{\"type\":\"function\",\"function\":{\"name\":\"test_tool\"}}")
        );

        engine = new ReActEngine(mockModelProvider, mockToolRegistry, 5, null);
    }

    @Test
    @DisplayName("当LLM直接返回文本时，应终止循环并返回最终答案")
    void shouldReturnFinalAnswerWhenNoToolCall() {
        // 模拟LLM直接返回文本（无工具调用）
        when(mockModelProvider.chat(anyList(), anyList()))
                .thenReturn(Message.builder()
                        .role("assistant")
                        .content("这是最终答案")
                        .build());

        AgentResponse response = engine.run("你好");

        assertEquals("这是最终答案", response.getFinalAnswer());
        assertFalse(response.isTruncated());
        assertEquals(1, response.getIterations());
        assertTrue(response.getDurationMs() >= 0);
    }

    @Test
    @DisplayName("当LLM返回工具调用时，应执行工具并将结果传回LLM")
    void shouldExecuteToolAndContinueLoop() {
        // 第一次调用LLM：返回工具调用
        Message firstResponse = Message.builder()
                .role("assistant")
                .content("我需要调用工具")
                .toolCalls(List.of(
                        ToolCall.builder()
                                .id("call_1")
                                .name("test_tool")
                                .arguments(Map.of("key", "value"))
                                .build()
                ))
                .build();

        // 第二次调用LLM：返回最终答案
        Message secondResponse = Message.builder()
                .role("assistant")
                .content("工具执行完毕，这是最终答案")
                .build();

        when(mockModelProvider.chat(anyList(), anyList()))
                .thenReturn(firstResponse, secondResponse);

        // 模拟工具执行成功
        when(mockToolRegistry.execute(any(ToolCall.class)))
                .thenReturn("工具执行结果：成功");

        AgentResponse response = engine.run("帮我查个东西");

        assertEquals("工具执行完毕，这是最终答案", response.getFinalAnswer());
        assertFalse(response.isTruncated());
        assertEquals(2, response.getIterations());
    }

    @Test
    @DisplayName("当工具执行失败时，应捕获异常并继续循环")
    void shouldHandleToolExecutionError() {
        Message toolResponse = Message.builder()
                .role("assistant")
                .content("调用工具")
                .toolCalls(List.of(
                        ToolCall.builder()
                                .id("call_err")
                                .name("failing_tool")
                                .arguments(Map.of())
                                .build()
                ))
                .build();

        Message finalResponse = Message.builder()
                .role("assistant")
                .content("已处理错误")
                .build();

        when(mockModelProvider.chat(anyList(), anyList()))
                .thenReturn(toolResponse, finalResponse);

        // 工具执行抛出异常
        when(mockToolRegistry.execute(any(ToolCall.class)))
                .thenThrow(new RuntimeException("工具执行出错：权限不足"));

        AgentResponse response = engine.run("执行一个会失败的操作");

        assertEquals("已处理错误", response.getFinalAnswer());
        // 验证工具异常被捕获，LLM收到了Observation:[Error...]
    }

    @Test
    @DisplayName("达到最大迭代次数时应截断")
    void shouldTruncateWhenMaxIterationsReached() {
        // 每次都返回工具调用，形成无限循环
        when(mockToolRegistry.execute(any(ToolCall.class)))
                .thenReturn("继续结果");
        when(mockToolRegistry.getToolDefinitions())
                .thenReturn(List.of("{\"type\":\"function\",\"function\":{\"name\":\"loop_tool\"}}"));

        when(mockModelProvider.chat(anyList(), anyList()))
                .thenReturn(Message.builder()
                        .role("assistant")
                        .content("再次调用工具")
                        .toolCalls(List.of(
                                ToolCall.builder()
                                        .id("call_loop")
                                        .name("loop_tool")
                                        .arguments(Map.of())
                                        .build()
                        ))
                        .build());

        AgentResponse response = engine.run("开始循环");

        assertTrue(response.isTruncated());
        assertEquals(5, response.getIterations()); // maxIterations=5
        assertTrue(response.getFinalAnswer().contains("max iterations"));
    }

    @Test
    @DisplayName("重置对话历史后应清空上下文")
    void shouldResetConversationHistory() {
        when(mockModelProvider.chat(anyList(), anyList()))
                .thenReturn(Message.builder()
                        .role("assistant")
                        .content("答案 A")
                        .build());

        engine.run("第一轮对话");
        assertEquals(2, engine.getConversationHistory().size()); // system + user + assistant

        engine.resetConversation();
        assertEquals(0, engine.getConversationHistory().size());

        // 重置后重新对话
        when(mockModelProvider.chat(anyList(), anyList()))
                .thenReturn(Message.builder()
                        .role("assistant")
                        .content("答案 B")
                        .build());

        AgentResponse response = engine.run("第二轮对话");
        assertEquals("答案 B", response.getFinalAnswer());
    }

    @Test
    @DisplayName("应正确处理空输入")
    void shouldHandleEmptyInput() {
        when(mockModelProvider.chat(anyList(), anyList()))
                .thenReturn(Message.builder()
                        .role("assistant")
                        .content("")
                        .build());

        AgentResponse response = engine.run("");

        assertEquals("", response.getFinalAnswer());
        assertFalse(response.isTruncated());
    }
}