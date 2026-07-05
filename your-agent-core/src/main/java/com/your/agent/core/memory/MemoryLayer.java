package com.your.agent.core.memory;

/**
 * 记忆层接口 —— 四层记忆系统的统一抽象。
 * <p>
 * 借鉴Hermes/MemGPT的设计，Agent记忆分为四个层次：
 * <ul>
 *   <li>L1 CoreMemory：核心记忆（如MEMORY.md），常驻上下文</li>
 *   <li>L2 UserProfile：用户画像（如USER.md），持久化用户偏好</li>
 *   <li>L3 SkillMemory：技能库，记录Agent已学会的能力</li>
 *   <li>L4 LongTermStore：长程存储（SQLite + FTS5全文检索）</li>
 * </ul>
 */
public interface MemoryLayer {

    /**
     * 记忆层级编号（1-4）
     */
    int level();

    /**
     * 读取当前记忆内容。
     *
     * @return 记忆内容字符串
     */
    String read();

    /**
     * 写入/更新记忆内容。
     *
     * @param content 新的记忆内容
     */
    void write(String content);

    /**
     * 搜索记忆（L3/L4支持语义搜索，L1/L2直接返回全文）
     *
     * @param query 搜索关键词
     * @return 匹配的记忆片段
     */
    String search(String query);
}