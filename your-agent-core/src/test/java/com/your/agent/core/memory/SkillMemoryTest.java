package com.your.agent.core.memory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * SkillMemory (L3) 单元测试 —— 验证技能库的记录和搜索。
 */
class SkillMemoryTest {

    @Test
    @DisplayName("初始化时技能库应为空")
    void shouldBeEmptyOnInit() {
        SkillMemory skills = new SkillMemory();
        assertTrue(skills.read().contains("暂无"));
        assertEquals(3, skills.level());
    }

    @Test
    @DisplayName("写入技能后应能读取")
    void shouldStoreAndReadSkills() {
        SkillMemory skills = new SkillMemory();
        skills.write("search_db: 用于数据库搜索");
        String content = skills.read();
        assertTrue(content.contains("search_db"));
        assertTrue(content.contains("数据库搜索"));
    }

    @Test
    @DisplayName("recordUsage应自动记录工具使用经验")
    void shouldRecordUsage() {
        SkillMemory skills = new SkillMemory();
        skills.recordUsage("generate_report", "查看上月销售数据", "成功返回报表");
        String searchResult = skills.search("generate_report");
        assertTrue(searchResult.contains("generate_report"));
    }

    @Test
    @DisplayName("search按技能名称和描述模糊匹配")
    void shouldSearchByKeyword() {
        SkillMemory skills = new SkillMemory();
        skills.write("analyze_log: 用于分析系统日志");
        String result = skills.search("日志");
        assertTrue(result.contains("analyze_log"));
    }

    @Test
    @DisplayName("getSkillLog应返回使用日志列表")
    void shouldReturnSkillLog() {
        SkillMemory skills = new SkillMemory();
        skills.recordUsage("tool_a", "场景1", "成功");
        skills.recordUsage("tool_b", "场景2", "失败");
        assertEquals(2, skills.getSkillLog().size());
    }
}