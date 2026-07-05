package com.your.agent.core.memory;

import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AGENTS.md —— 参考 Codex 的 AGENTS.md 机制。
 * 允许用户通过自定义 Markdown 文件设置 Agent 的行为、个性、规则。
 * 文件路径: ~/.phoenix/AGENTS.md
 */
@Slf4j
public class AgentsConfig {

    private final Path configPath;
    private String content = "";
    private final ConcurrentHashMap<String, String> sections = new ConcurrentHashMap<>();

    public AgentsConfig() {
        this.configPath = Paths.get(System.getProperty("user.home"), ".phoenix", "AGENTS.md");
        load();
    }

    public AgentsConfig(String customPath) {
        this.configPath = Paths.get(customPath);
        load();
    }

    private void load() {
        try {
            if (Files.exists(configPath)) {
                content = Files.readString(configPath);
                parseSections();
                log.info("AGENTS.md loaded: {} chars, {} sections", content.length(), sections.size());
            } else {
                // 创建默认 AGENTS.md
                content = createDefault();
                Files.createDirectories(configPath.getParent());
                Files.writeString(configPath, content);
                parseSections();
                log.info("Default AGENTS.md created: {}", configPath);
            }
        } catch (Exception e) {
            log.warn("Failed to load AGENTS.md: {}", e.getMessage());
            content = createDefault();
        }
    }

    private void parseSections() {
        sections.clear();
        String[] lines = content.split("\n");
        String currentSection = "general";
        StringBuilder currentContent = new StringBuilder();

        for (String line : lines) {
            if (line.startsWith("## ")) {
                if (!currentContent.isEmpty()) {
                    sections.put(currentSection, currentContent.toString().trim());
                }
                currentSection = line.substring(3).trim().toLowerCase();
                currentContent = new StringBuilder();
            } else {
                currentContent.append(line).append("\n");
            }
        }
        if (!currentContent.isEmpty()) {
            sections.put(currentSection, currentContent.toString().trim());
        }
    }

    private String createDefault() {
        return """
# Phoenix AGENTS.md
# 在此文件中定义 Agent 的行为、个性、规则
# 支持 Markdown 格式，二级标题 ## 作为分节

## identity
你是 Phoenix，一个企业级 AI Agent。
你的核心能力是通过 ReAct 循环调用工具解决用户问题。
你始终保持专业、高效、安全。

## rules
- 拒绝执行任何可能造成危害的命令
- 敏感操作必须先请求审批
- 使用中文回复用户
- 保持回答简洁准确

## personality
- 专业严谨
- 温暖耐心
- 忠于主人

## behavior
- 每次回复附带可执行的下一步建议
- 复杂任务拆解为多个步骤
- 优先使用已有的工具和技能

## constraints
- 不得泄露 API 密钥
- 不得删除系统关键文件
- 执行命令前必须确认路径安全
""";
    }

    public String getContent() { return content; }

    public String getSection(String name) {
        return sections.getOrDefault(name.toLowerCase(), "");
    }

    public String getSystemPrompt() {
        // 将 AGENTS.md 中的关键节组装成 system prompt
        StringBuilder sb = new StringBuilder();
        String id = getSection("identity");
        if (!id.isEmpty()) sb.append(id).append("\n\n");
        String rules = getSection("rules");
        if (!rules.isEmpty()) sb.append("规则:\n").append(rules).append("\n");
        String personality = getSection("personality");
        if (!personality.isEmpty()) sb.append("个性:\n").append(personality).append("\n");
        String constraints = getSection("constraints");
        if (!constraints.isEmpty()) sb.append("限制:\n").append(constraints).append("\n");
        return sb.toString().trim();
    }

    public void updateContent(String newContent) {
        try {
            this.content = newContent;
            Files.writeString(configPath, newContent);
            parseSections();
            log.info("AGENTS.md updated");
        } catch (Exception e) {
            log.error("Failed to update AGENTS.md", e);
        }
    }

    public Path getConfigPath() { return configPath; }
}