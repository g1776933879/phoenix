package com.your.agent.core.memory;

import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * L3 技能记忆 —— 记录Agent已学会的能力和工具使用经验。
 * 修复：skillLog改为CopyOnWriteArrayList保证线程安全。
 */
@Slf4j
public class SkillMemory implements MemoryLayer {

    private final Map<String, String> skills = new ConcurrentHashMap<>();
    // 修复：使用线程安全的CopyOnWriteArrayList
    private final List<String> skillLog = new CopyOnWriteArrayList<>();

    public SkillMemory() {
        log.info("SkillMemory (L3) initialized");
    }

    @Override
    public int level() {
        return 3;
    }

    @Override
    public String read() {
        if (skills.isEmpty()) {
            return "=== 技能库 ===\n暂无已记录技能。";
        }
        StringBuilder sb = new StringBuilder("=== 技能库 ===\n");
        skills.forEach((name, desc) ->
                sb.append("- ").append(name).append(": ").append(desc).append("\n"));
        return sb.toString();
    }

    @Override
    public void write(String content) {
        String[] lines = content.split("\n");
        for (String line : lines) {
            int colonIdx = line.indexOf(":");
            if (colonIdx > 0 && colonIdx < line.length() - 1) {
                String name = line.substring(0, colonIdx).trim();
                String desc = line.substring(colonIdx + 1).trim();
                skills.put(name, desc);
                skillLog.add(name + " learned at " + new Date());
                log.info("New skill recorded: {} - {}", name, desc);
            }
        }
    }

    public void recordUsage(String toolName, String context, String result) {
        String entry = String.format("Tool[%s] used for [%s] \u2192 %s", toolName, context, result);
        skillLog.add(entry);
        log.debug("Skill usage recorded: {}", entry);

        String existing = skills.get(toolName);
        if (existing == null) {
            skills.put(toolName, "用于" + context + "\u573a\u666f");
        }
    }

    @Override
    public String search(String query) {
        for (Map.Entry<String, String> entry : skills.entrySet()) {
            if (entry.getKey().contains(query) || entry.getValue().contains(query)) {
                return entry.getKey() + ": " + entry.getValue();
            }
        }
        return "";
    }

    public List<String> getSkillLog() {
        return new ArrayList<>(skillLog);
    }
}