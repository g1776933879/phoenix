package com.your.agent.core.memory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * UserProfile (L2) 单元测试 —— 验证用户画像的读写和搜索。
 */
class UserProfileTest {

    @Test
    @DisplayName("初始化应包含默认字段")
    void shouldHaveDefaultFields() {
        UserProfile profile = new UserProfile();
        String content = profile.read();
        assertTrue(content.contains("language"));
        assertTrue(content.contains("中文"));
        assertTrue(content.contains("response_style"));
        assertEquals(2, profile.level());
    }

    @Test
    @DisplayName("set和get应正确工作")
    void shouldSetAndGetFields() {
        UserProfile profile = new UserProfile();
        profile.set("favorite_color", "蓝色");
        assertEquals("蓝色", profile.get("favorite_color"));
    }

    @Test
    @DisplayName("write应解析key:value格式并更新")
    void shouldParseKeyValueOnWrite() {
        UserProfile profile = new UserProfile();
        profile.write("timezone: Asia/Shanghai\nlanguage: English");
        assertEquals("Asia/Shanghai", profile.get("timezone"));
        assertEquals("English", profile.get("language"));
    }

    @Test
    @DisplayName("search按关键词匹配字段名或值")
    void shouldSearchByKeyword() {
        UserProfile profile = new UserProfile();
        profile.set("department", "Engineering");
        String result = profile.search("Engineering");
        assertTrue(result.contains("Engineering"));

        result = profile.search("department");
        assertTrue(result.contains("Engineering"));
    }

    @Test
    @DisplayName("不匹配的关键词返回空字符串")
    void shouldReturnEmptyForUnmatched() {
        UserProfile profile = new UserProfile();
        assertEquals("", profile.search("不存在的字段"));
    }
}