package com.your.agent.integration;

import com.your.agent.core.llm.ModelProvider;
import com.your.agent.core.llm.OpenAiModelProvider;
import com.your.agent.core.loop.ReActEngine;
import com.your.agent.core.memory.*;
import com.your.agent.core.model.AgentResponse;
import com.your.agent.core.model.Message;
import com.your.agent.core.model.ToolCall;
import com.your.agent.core.tool.ToolRegistry;
import org.junit.jupiter.api.*;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 凤凰集成测试 —— 端到端验证所有模块协作。
 * <p>
 * 使用模拟LLM和模拟工具，不依赖外部API。
 * 覆盖场景：
 * 1. 完整ReAct循环（Thought→Action→Observation→Final）
 * 2. 四层记忆联合读写
 * 3. 工具注册与调用
 * 4. 错误恢复
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AgentIntegrationTest {

    private static ReActEngine engine;
    private static CoreMemory coreMemory;
    private static UserProfile userProfile;
    private static SkillMemory skillMemory;
    private static LongTermStore longTermStore;
    private static TestToolRegistry toolRegistry;
    private static TestModelProvider modelProvider;

    private static final String TEST_DB_PATH = "/tmp/int_test_" + System.currentTimeMillis() + ".db";

    @BeforeAll
    static void setUp() {
        // 1. 初始化记忆层
        coreMemory = new CoreMemory("你是凤凰AI Agent，一个企业级智能助手。");
        userProfile = new UserProfile();
        skillMemory = new SkillMemory();
        longTermStore = new LongTermStore(TEST_DB_PATH);

        // 2. 初始化工具注册中心
        toolRegistry = new TestToolRegistry();

        // 3. 初始化模拟LLM
        modelProvider = new TestModelProvider();

        // 4. 组装ReAct引擎
        String systemPrompt = coreMemory.read() + "\n" + userProfile.read();
        engine = new ReActEngine(modelProvider, toolRegistry, 10, systemPrompt);
    }

    @AfterAll
    static void tearDown() {
        longTermStore.close();
        new File(TEST_DB_PATH).delete();
    }

    @Test
    @Order(1)
    @DisplayName("场景1: 用户直接提问，LLM无工具调用，应直接返回答案")
    void testDirectQuery() {
        modelProvider.setNextResponse("直接回答：今天是星期六，天气晴朗。");

        AgentResponse response = engine.run("今天天气怎么样？");

        assertEquals("直接回答：今天是星期六，天气晴朗。", response.getFinalAnswer());
        assertFalse(response.isTruncated());
        assertEquals(1, response.getIterations());
    }

    @Test
    @Order(2)
    @DisplayName("场景2: 用户请求查询数据，LLM调用search_tool，返回结果")
    void testToolCallFlow() {
        modelProvider.setNextResponses(List.of(
                "我需要调用搜索工具来查询。",        // 第一次：Thought
                "查询结果如下：销售额100万"          // 第二次：最终答案
        ));

        // 给LLM发送带tool_calls的模拟响应
        modelProvider.setNextToolCalls(List.of(
                ToolCall.builder()
                        .id("search_001")
                        .name("search_data")
                        .arguments(Map.of("query", "本月销售额"))
                        .build()
        ));

        AgentResponse response = engine.run("查一下本月的销售额");

        assertTrue(response.getFinalAnswer().contains("100万"));
        assertEquals(2, response.getIterations());

        // 验证工具被正确调用
        assertTrue(toolRegistry.getCallHistory().contains("search_data"));
    }

    @Test
    @Order(3)
    @DisplayName("场景3: 用户画像更新和读取")
    void testUserProfileInteraction() {
        userProfile.set("department", "技术部");
        userProfile.set("role", "架构师");

        String profileContent = userProfile.read();
        assertTrue(profileContent.contains("技术部"));
        assertTrue(profileContent.contains("架构师"));
    }

    @Test
    @Order(4)
    @DisplayName("场景4: 技能记忆自动记录")
    void testSkillMemoryRecording() {
        skillMemory.recordUsage("search_data", "查询本月销售额", "成功返回100万");
        skillMemory.recordUsage("generate_report", "生成季度报告", "成功返回报表");

        String searchResult = skillMemory.search("search_data");
        assertTrue(searchResult.contains("search_data"));

        assertEquals(2, skillMemory.getSkillLog().size());
    }

    @Test
    @Order(5)
    @DisplayName("场景5: 长程记忆持久化和FTS5检索")
    void testLongTermStorePersistence() {
        longTermStore.store("Q1销售总结", "2026年Q1总销售额500万，同比增长20%", "business");
        longTermStore.store("系统架构决策", "采用ReAct模式 + 四层记忆架构", "technical");

        String result = longTermStore.search("销售");
        assertTrue(result.contains("Q1销售总结"));

        result = longTermStore.search("ReAct");
        assertTrue(result.contains("系统架构决策"));
    }

    @Test
    @Order(6)
    @DisplayName("场景6: 工具执行异常恢复")
    void testToolErrorRecovery() {
        modelProvider.setNextToolCalls(List.of(
                ToolCall.builder()
                        .id("failing_001")
                        .name("failing_tool")
                        .arguments(Map.of())
                        .build()
        ));
        modelProvider.setNextResponses(List.of(
                "我要调用一个会失败的工具",
                "工具失败了，但我会给出备用方案"
        ));

        AgentResponse response = engine.run("执行一个可能失败的操作");

        // 应该能正常返回
        assertNotNull(response.getFinalAnswer());
        assertEquals(2, response.getIterations());
    }

    @Test
    @Order(7)
    @DisplayName("场景7: 多工具连续调用")
    void testMultipleToolCalls() {
        modelProvider.setNextToolCalls(List.of(
                ToolCall.builder().id("m1").name("search_data").arguments(Map.of("q", "库存")).build(),
                ToolCall.builder().id("m2").name("search_data").arguments(Map.of("q", "订单")).build()
        ));
        modelProvider.setNextResponses(List.of(
                "我需要查询库存和订单两个数据",
                "库存: 1000件, 订单: 500件"
        ));

        AgentResponse response = engine.run("查库存和订单");

        assertTrue(response.getFinalAnswer().contains("库存"));
        assertTrue(response.getFinalAnswer().contains("订单"));
        assertEquals(2, response.getIterations());
    }

    @Test
    @Order(8)
    @DisplayName("场景8: 重置对话清空历史")
    void testConversationReset() {
        // 先跑一轮
        modelProvider.setNextResponse("第一轮回答");
        engine.run("第一轮");

        assertTrue(engine.getConversationHistory().size() >= 2);

        // 重置
        engine.resetConversation();
        assertEquals(0, engine.getConversationHistory().size());
    }

    // =============================================
    // 辅助测试工具
    // =============================================

    /**
     * 模拟工具注册中心
     */
    static class TestToolRegistry implements ToolRegistry {
        private final List<String> callHistory = new ArrayList<>();
        private final AtomicInteger counter = new AtomicInteger(0);

        @Override
        public String execute(ToolCall toolCall) {
            callHistory.add(toolCall.getName());
            String name = toolCall.getName();

            return switch (name) {
                case "search_data" -> "查询结果：共找到 100 条记录，总金额 ¥1,000,000";
                case "failing_tool" -> throw new RuntimeException("模拟工具执行失败");
                default -> "未知工具: " + name;
            };
        }

        @Override
        public List<String> getToolDefinitions() {
            return List.of(
                    "{\"type\":\"function\",\"function\":{\"name\":\"search_data\",\"description\":\"搜索数据\",\"parameters\":{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}},\"required\":[\"query\"]}}}",
                    "{\"type\":\"function\",\"function\":{\"name\":\"failing_tool\",\"description\":\"一个会失败的工具\",\"parameters\":{\"type\":\"object\",\"properties\":{}}}}"
            );
        }

        @Override
        public boolean requiresApproval(String toolName) {
            return "failing_tool".equals(toolName);
        }

        public List<String> getCallHistory() { return callHistory; }
    }

    /**
     * 模拟模型提供者 —— 可预设响应链
     */
    static class TestModelProvider implements ModelProvider {
        private final Queue<Message> presetResponses = new LinkedList<>();
        private final Queue<List<ToolCall>> presetToolCalls = new LinkedList<>();

        public void setNextResponse(String content) {
            presetResponses.clear();
            presetResponses.add(Message.builder().role("assistant").content(content).build());
        }

        public void setNextResponses(List<String> contents) {
            presetResponses.clear();
            for (String c : contents) {
                presetResponses.add(Message.builder().role("assistant").content(c).build());
            }
        }

        public void setNextToolCalls(List<ToolCall> toolCalls) {
            presetToolCalls.clear();
            presetToolCalls.add(toolCalls);
        }

        @Override
        public Message chat(List<Message> messages, List<String> tools) {
            // 检查是否有预设工具调用
            List<ToolCall> calls = presetToolCalls.poll();

            // 获取预设响应
            Message response = presetResponses.poll();
            if (response == null) {
                response = Message.builder()
                        .role("assistant")
                        .content("默认模拟回答：" + System.currentTimeMillis())
                        .build();
            }

            if (calls != null && !calls.isEmpty()) {
                response.setToolCalls(calls);
            }

            return response;
        }

        @Override
        public String modelName() {
            return "test-mock-model";
        }
    }
}