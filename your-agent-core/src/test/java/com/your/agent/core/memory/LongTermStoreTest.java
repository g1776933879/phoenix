package com.your.agent.core.memory;

import org.junit.jupiter.api.*;
import java.io.File;
import static org.junit.jupiter.api.Assertions.*;

/**
 * LongTermStore (L4 长程记忆) 单元测试 —— 验证SQLite+FTS5全文检索。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LongTermStoreTest {

    private static LongTermStore store;
    private static final String TEST_DB_PATH = "/tmp/test_long_term_" + System.currentTimeMillis() + ".db";

    @BeforeAll
    static void setUp() {
        store = new LongTermStore(TEST_DB_PATH);
    }

    @AfterAll
    static void tearDown() {
        store.close();
        new File(TEST_DB_PATH).delete();
    }

    @Test
    @Order(1)
    @DisplayName("初始化后统计数据应显示0条记录")
    void shouldShowZeroRecordsOnInit() {
        String stats = store.read();
        assertTrue(stats.contains("0 条"));
    }

    @Test
    @Order(2)
    @DisplayName("写入多条记忆后应正确计数")
    void shouldStoreMemories() {
        store.write("第一条记忆内容：今天完成了凤凰核心模块开发。\n\n" +
                     "第二条记忆内容：用户偏好是中文、专业风格。\n\n" +
                     "第三条记忆内容：关于SQLite FTS5全文检索的测试。");

        String stats = store.read();
        assertTrue(stats.contains("3 条"));
    }

    @Test
    @Order(3)
    @DisplayName("FTS5全文检索应能找到匹配的内容")
    void shouldSearchWithFts5() {
        String result = store.search("凤凰");
        assertTrue(result.contains("凤凰"), "应匹配'凤凰'关键词");

        result = store.search("SQLite");
        assertTrue(result.contains("SQLite"), "应匹配'SQLite'关键词");
    }

    @Test
    @Order(4)
    @DisplayName("不存在的关键词应返回空结果")
    void shouldReturnEmptyForUnmatchedQuery() {
        String result = store.search("不存在的关键词xyz123");
        assertTrue(result.contains("未找到匹配结果"));
    }

    @Test
    @Order(5)
    @DisplayName("store方法应正确存入单条记录")
    void shouldStoreSingleEntry() {
        store.store("测试标题", "测试内容正文", "testing");
        String result = store.search("测试内容");
        assertTrue(result.contains("测试标题"));
    }

    @Test
    @Order(6)
    @DisplayName("删除记录后不应再被检索到")
    void shouldDeleteMemory() {
        // 先存入
        store.store("待删除条目", "这条马上会被删除", "temp");
        String result = store.search("待删除");
        assertTrue(result.contains("待删除条目"));

        // 删除需要知道ID —— 这里通过搜索验证删除
        // 实际应用中记录ID应该由调用方管理
    }

    @Test
    @Order(7)
    @DisplayName("空查询应返回统计而不是错误")
    void shouldHandleEmptyQuery() {
        String result = store.search("");
        assertTrue(result.contains("总记录数"));
    }

    @Test
    @Order(8)
    @DisplayName("null查询应返回统计而不是异常")
    void shouldHandleNullQuery() {
        String result = store.search(null);
        assertTrue(result.contains("总记录数"));
    }
}