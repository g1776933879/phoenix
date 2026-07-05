package com.your.evolution.core.introspection;

import com.your.evolution.core.CodeIssue;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 代码坏味道检测器 —— 检测常见的代码问题和改进点。
 * 检测项：硬编码密钥、空指针风险、过长的函数、TODO未处理等。
 */
@Slf4j
public class CodeSmellDetector {

    private static final int MAX_FUNCTION_LINES = 50;

    // 检测规则列表
    private final List<SmellRule> rules = new ArrayList<>();

    public CodeSmellDetector() {
        registerBuiltinRules();
    }

    private void registerBuiltinRules() {
        rules.add(new SmellRule("hardcoded_secret", "HIGH", "security",
                Pattern.compile("(?i)(password|secret|api_key|token)\\s*=\\s*\"[^\"]{4,}\""),
                "检测到硬编码密钥，应使用环境变量"));

        rules.add(new SmellRule("print_stacktrace", "MEDIUM", "code_style",
                Pattern.compile("\\.printStackTrace\\(\\)"),
                "应使用logger.error()替代printStackTrace()"));

        rules.add(new SmellRule("system_out", "LOW", "code_style",
                Pattern.compile("System\\.out\\.println"),
                "应使用logger.info/debug替代System.out.println"));

        rules.add(new SmellRule("raw_thread_sleep", "LOW", "reliability",
                Pattern.compile("Thread\\.sleep\\("),
                "考虑使用ScheduledExecutorService替代Thread.sleep()"));

        rules.add(new SmellRule("unformatted_todo", "LOW", "tech_debt",
                Pattern.compile("//\\s*TODO[^(\\(\\w+)]"),
                "TODO注释应包含责任人标识，如// TODO(owner): ..."));

        rules.add(new SmellRule("generic_exception", "MEDIUM", "code_style",
                Pattern.compile("catch\\s*\\(\\s*Exception\\s+\\w+\\s*\\)\\s*\\{"),
                "应捕获更具体的异常类型而非通用的Exception"));

        rules.add(new SmellRule("empty_catch", "HIGH", "reliability",
                Pattern.compile("catch\\s*\\([^)]+\\)\\s*\\{\\s*\\}"),
                "空的catch块会吞掉异常"));

        rules.add(new SmellRule("deprecated_api", "MEDIUM", "compatibility",
                Pattern.compile("@Deprecated"),
                "使用了@Deprecated标记的API"));

        rules.add(new SmellRule("magic_number", "LOW", "code_style",
                Pattern.compile("[^a-zA-Z](1000|30000|60000|86400000)[^a-zA-Z]"),
                "魔法数字，建议定义为常量"));
    }

    /**
     * 对单个文件执行所有检测
     */
    public List<CodeIssue> detect(String filePath, String content) {
        List<CodeIssue> issues = new ArrayList<>();
        String[] lines = content.split("\n", -1);

        // 逐规则检测
        for (SmellRule rule : rules) {
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                if (rule.pattern().matcher(line).find()) {
                    issues.add(CodeIssue.builder()
                            .filePath(filePath)
                            .lineNumber(i + 1)
                            .severity(rule.severity())
                            .category(rule.category())
                            .description(rule.description())
                            .codeSnippet(line.trim())
                            .build());
                }
            }
        }

        // 检测函数长度（超过50行）
        detectLongFunctions(filePath, content, issues);

        return issues;
    }

    /**
     * 检测过长的函数
     */
    private void detectLongFunctions(String filePath, String content, List<CodeIssue> issues) {
        String[] lines = content.split("\n", -1);
        int funcStart = -1;
        int braceCount = 0;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (funcStart < 0 && (line.contains("{") && !line.contains("class ") && !line.contains("enum "))) {
                funcStart = i;
                braceCount = 1;
            } else if (funcStart >= 0) {
                braceCount += line.chars().filter(c -> c == '{').count();
                braceCount -= line.chars().filter(c -> c == '}').count();
                if (braceCount <= 0) {
                    int funcLines = i - funcStart;
                    if (funcLines > MAX_FUNCTION_LINES) {
                        issues.add(CodeIssue.builder()
                                .filePath(filePath)
                                .lineNumber(funcStart + 1)
                                .severity("MEDIUM")
                                .category("code_style")
                                .description("函数过长（" + funcLines + "行），建议拆分为不超过" + MAX_FUNCTION_LINES + "行")
                                .codeSnippet(lines[funcStart].trim())
                                .build());
                    }
                    funcStart = -1;
                }
            }
        }
    }

    /**
     * 全量扫描
     */
    public List<CodeIssue> scanAll(List<CodeAnalyzer.SourceFile> sources) {
        List<CodeIssue> allIssues = new ArrayList<>();
        for (CodeAnalyzer.SourceFile sf : sources) {
            List<CodeIssue> fileIssues = detect(sf.relativePath(), sf.content());
            allIssues.addAll(fileIssues);
            log.debug("Scanned {}: found {} issues", sf.relativePath(), fileIssues.size());
        }
        log.info("CodeSmellDetector: found {} issues across {} files", allIssues.size(), sources.size());
        return allIssues;
    }

    private record SmellRule(String name, String severity, String category, Pattern pattern, String description) {}
}