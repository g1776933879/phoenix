package com.your.evolution.core.mutation;

import com.your.evolution.core.CodeIssue;
import com.your.evolution.core.introspection.CodeAnalyzer;
import com.your.agent.core.llm.ModelProvider;
import com.your.agent.core.model.Message;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 代码生成器 —— 调用LLM分析代码问题并生成修复补丁。
 */
@Slf4j
public class CodeGenerator {

    private final ModelProvider modelProvider;
    private final CodeAnalyzer analyzer;

    public CodeGenerator(ModelProvider modelProvider, CodeAnalyzer analyzer) {
        this.modelProvider = modelProvider;
        this.analyzer = analyzer;
        log.info("CodeGenerator initialized with model: {}", modelProvider.modelName());
    }

    /**
     * 为指定的代码问题生成修复补丁。
     *
     * @param issue 代码问题
     * @return Patch对象，包含oldCode和newCode；如果无法生成返回null
     */
    public Patch generatePatch(CodeIssue issue) {
        // 获取问题代码的上下文（前后5行）
        String context = analyzer.getCodeContext(issue.getFilePath(), issue.getLineNumber(), 5);
        if (context == null) {
            log.warn("Cannot read context for {}", issue.getFilePath());
            return null;
        }

        // 构建给LLM的提示
        String prompt = buildPrompt(issue, context);

        try {
            Message response = modelProvider.chat(
                    List.of(Message.userMessage(prompt)),
                    List.of()
            );

            String content = response.getContent();
            if (content == null || content.trim().isEmpty()) {
                log.warn("LLM returned empty patch for {}", issue.getFilePath());
                return null;
            }

            return parsePatch(content, issue);

        } catch (Exception e) {
            log.error("Failed to generate patch for {}", issue.getFilePath(), e);
            return null;
        }
    }

    private String buildPrompt(CodeIssue issue, String context) {
        return "你是凤凰AI Agent框架的自我修复系统。你的任务是修复以下代码问题。\n\n" +
               "文件: " + issue.getFilePath() + "\n" +
               "行号: " + issue.getLineNumber() + "\n" +
               "严重度: " + issue.getSeverity() + "\n" +
               "类别: " + issue.getCategory() + "\n" +
               "问题描述: " + issue.getDescription() + "\n\n" +
               "问题代码上下文:\n```\n" + context + "\n```\n\n" +
               "请按以下格式输出修复方案：\n" +
               "---OLD---\n(需要被替换的精确代码行)\n---NEW---\n(替换后的代码行)\n---\n" +
               "只输出OLD和NEW部分，不要额外解释。";
    }

    private Patch parsePatch(String llmResponse, CodeIssue issue) {
        String oldMarker = "---OLD---";
        String newMarker = "---NEW---";
        String endMarker = "---";

        int oldStart = llmResponse.indexOf(oldMarker);
        int newStart = llmResponse.indexOf(newMarker);
        int endStart = llmResponse.indexOf(endMarker, newStart + newMarker.length());

        if (oldStart < 0 || newStart < 0) {
            log.warn("Cannot parse LLM patch response: markers not found");
            return new Patch(issue.getFilePath(), issue.getCodeSnippet(), issue.getCodeSnippet());
        }

        String oldCode = llmResponse.substring(oldStart + oldMarker.length(), newStart).trim();
        String newCode = llmResponse.substring(newStart + newMarker.length(),
                endStart > 0 ? endStart : llmResponse.length()).trim();

        if (oldCode.isEmpty() || newCode.isEmpty()) {
            log.warn("Generated patch has empty old/new code");
            return null;
        }

        log.info("Generated patch for {}: old={}chars, new={}chars",
                issue.getFilePath(), oldCode.length(), newCode.length());
        return new Patch(issue.getFilePath(), oldCode, newCode);
    }

    public record Patch(String filePath, String oldCode, String newCode) {}
}