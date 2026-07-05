package com.your.evolution.spring.tools;

import com.your.agent.core.tool.Tool;
import com.your.evolution.core.skills.SkillFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 技能创造工具 —— 让凤凰可以自主创造新技能。
 * <p>
 * 注册为@Tool，Agent在对话中可以通过自然语言描述来创造新工具。
 * 创造流程：接收描述→LLM生成代码→写入文件→编译验证→完成
 */
@Slf4j
@Component
public class SkillCreationTools {

    private final SkillFactory skillFactory;

    public SkillCreationTools(SkillFactory skillFactory) {
        this.skillFactory = skillFactory;
    }

    @Tool(
        name = "create_skill",
        description = "让凤凰自己创造一个新技能/工具。" +
                      "你需要提供技能名称、功能描述和触发词。" +
                      "凤凰将调用LLM生成Java代码，编译验证后自动注册。" +
                      "注意：新技能需要重启应用后才能被Agent使用。",
        parametersSchema = "{\"type\":\"object\",\"properties\":{" +
                "\"name\":{\"type\":\"string\",\"description\":\"技能名称，英文小写+下划线，如 code_review\"}," +
                "\"description\":{\"type\":\"string\",\"description\":\"技能的完整功能描述\"}," +
                "\"triggerPhrase\":{\"type\":\"string\",\"description\":\"触发词，如'审查代码 / 检查代码'\"}" +
                "},\"required\":[\"name\",\"description\",\"triggerPhrase\"]}"
    )
    public String createSkill(String argsJson) {
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var node = mapper.readTree(argsJson);

            String name = node.get("name").asText();
            String description = node.get("description").asText();
            String triggerPhrase = node.has("triggerPhrase") ? node.get("triggerPhrase").asText() : "";

            log.info("🧬 Skill creation requested: name={}, desc={}", name, description);

            var result = skillFactory.createSkill(name, description, triggerPhrase);
            return result.toMarkdown();

        } catch (Exception e) {
            log.error("create_skill failed", e);
            return "## ❌ 技能创建失败\n\n错误: " + e.getMessage();
        }
    }

    @Tool(
        name = "create_skill_from_template",
        description = "根据青玄的14种工作模式模板，批量创建一个完整技能。" +
                      "传入技能名称和完整的技术能力描述，凤凰将自动生成符合规范的@Tool方法。",
        parametersSchema = "{\"type\":\"object\",\"properties\":{" +
                "\"name\":{\"type\":\"string\",\"description\":\"技能名称\"}," +
                "\"template\":{\"type\":\"string\",\"description\":\"完整的技能模板文本，包括触发词、流程、输出格式等\"}" +
                "},\"required\":[\"name\",\"template\"]}"
    )
    public String createSkillFromTemplate(String argsJson) {
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var node = mapper.readTree(argsJson);

            String name = node.get("name").asText();
            String template = node.get("template").asText();

            // 从模板中提取描述和触发词
            String description = template.length() > 200 ? template.substring(0, 200) + "..." : template;
            String triggerPhrase = extractTriggerPhrase(template);

            log.info("🧬 Skill from template: name={}, trigger={}", name, triggerPhrase);

            var result = skillFactory.createSkill(name, description, triggerPhrase);
            return result.toMarkdown();

        } catch (Exception e) {
            log.error("create_skill_from_template failed", e);
            return "## ❌ 技能创建失败\n\n错误: " + e.getMessage();
        }
    }

    private String extractTriggerPhrase(String template) {
        // 尝试从模板中提取触发词
        if (template.contains("触发词：")) {
            int start = template.indexOf("触发词：") + 4;
            int end = template.indexOf("\n", start);
            if (end < 0) end = template.length();
            return template.substring(start, end).trim();
        }
        if (template.contains("触发词:")) {
            int start = template.indexOf("触发词:") + 4;
            int end = template.indexOf("\n", start);
            if (end < 0) end = template.length();
            return template.substring(start, end).trim();
        }
        return "自动生成技能";
    }
}