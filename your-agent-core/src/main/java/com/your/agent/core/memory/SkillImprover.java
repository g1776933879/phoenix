package com.your.agent.core.memory;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 技能自我改进 —— 参考 Hermes 的技能自动优化。
 * 每次使用技能后自动评估效果，记录改进建议。
 */
@Slf4j
public class SkillImprover {

    private final SkillMemory skillMemory;
    private final ConcurrentHashMap<String, Integer> usageCount = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> improvements = new ConcurrentHashMap<>();

    public SkillImprover(SkillMemory skillMemory) {
        this.skillMemory = skillMemory;
        log.info("SkillImprover initialized");
    }

    public void recordUsage(String toolName, String context, String result, boolean success) {
        usageCount.merge(toolName, 1, Integer::sum);
        int count = usageCount.get(toolName);

        if (!success) {
            String tip = "第" + count + "次使用失败: " + context + " -> " + (result.length() > 100 ? result.substring(0, 100) : result);
            improvements.merge(toolName, tip, (old, neu) -> old + "\n" + neu);
            log.info("Skill improvement recorded for {}: {}", toolName, tip);
        }

        // 每使用5次自动优化一次
        if (count % 5 == 0) {
            String existing = improvements.get(toolName);
            if (existing != null) {
                skillMemory.recordUsage(toolName, "auto-improve #" + count, "改进建议: " + existing);
                log.info("Skill {} auto-improved after {} uses", toolName, count);
            }
        }
    }

    public int getUsageCount(String toolName) { return usageCount.getOrDefault(toolName, 0); }
    public String getImprovements(String toolName) { return improvements.getOrDefault(toolName, "暂无改进建议"); }
}