package com.your.agent.core.memory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * CoreMemory (L1) 单元测试 —— 验证核心记忆的读写和初始化。
 */
class CoreMemoryTest {

    @Test
    @DisplayName("无参构造应使用默认系统提示词")
    void shouldUseDefaultSystemPrompt() {
        CoreMemory memory = new CoreMemory();
        String content = memory.read();
        assertTrue(content.contains("凤凰"));
        assertTrue(content.contains("AI Agent"));
        assertEquals(1, memory.level());
    }

    @Test
    @DisplayName("自定义初始化内容后应能正确读取")
    void shouldStoreAndReadCustomContent() {
        CoreMemory memory = new CoreMemory("自定义核心记忆内容");
        assertEquals("自定义核心记忆内容", memory.read());
    }

    @Test
    @DisplayName("write后read应返回最新内容")
    void shouldReturnLatestContentAfterWrite() {
        CoreMemory memory = new CoreMemory("初始内容");
        memory.write("更新后的内容");
        assertEquals("更新后的内容", memory.read());
    }

    @Test
    @DisplayName("search应直接返回全文（L1特性）")
    void shouldReturnFullContentOnSearch() {
        CoreMemory memory = new CoreMemory("全文搜索测试");
        assertEquals("全文搜索测试", memory.search("任何关键词"));
    }
}