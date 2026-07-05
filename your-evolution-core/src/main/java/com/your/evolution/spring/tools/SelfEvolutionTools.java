package com.your.evolution.spring.tools;

import com.your.agent.core.tool.Tool;
import com.your.evolution.core.EvolutionEngine;
import com.your.evolution.core.EvolutionReport;
import com.your.evolution.core.introspection.CodeAnalyzer;
import com.your.evolution.core.introspection.CodeSmellDetector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 自进化工具集 —— 让凤凰可以通过Agent对话来触发自我进化。
 * 注册为@Tool，Agent可以自主调用这些工具来改进自己。
 */
@Slf4j
@Component
public class SelfEvolutionTools {

    private final EvolutionEngine evolutionEngine;
    private final CodeAnalyzer analyzer;
    private final CodeSmellDetector detector;

    public SelfEvolutionTools(EvolutionEngine evolutionEngine) {
        this.evolutionEngine = evolutionEngine;
        this.analyzer = new CodeAnalyzer();
        this.detector = new CodeSmellDetector();
    }

    @Tool(
        name = "self_evolve",
        description = "执行一次完整的自进化循环。凤凰将扫描自己的源代码，发现问题，" +
                      "调用LLM生成修复，编译验证，运行测试，通过后提交修改。" +
                      "返回详细的演化报告，包含成功/失败的每次尝试。",
        parametersSchema = "{\"type\":\"object\",\"properties\":{}}"
    )
    public String selfEvolve() {
        log.info("🧬 自进化触发");
        EvolutionReport report = evolutionEngine.evolve();
        return report.toMarkdown();
    }

    @Tool(
        name = "self_analyze",
        description = "只读分析凤凰的源代码，发现代码质量问题。" +
                      "不会修改任何代码。返回按严重度分组的分析报告。",
        parametersSchema = "{\"type\":\"object\",\"properties\":{}}"
    )
    public String selfAnalyze() {
        log.info("🔍 代码分析触发");
        return evolutionEngine.analyzeOnly();
    }

    @Tool(
        name = "self_read_source",
        description = "读取凤凰自己的Java源文件内容。用于自我审查和调试。" +
                      "参数filePath相对于项目根目录。",
        parametersSchema = "{\"type\":\"object\",\"properties\":{" +
                "\"filePath\":{\"type\":\"string\",\"description\":\"文件相对路径，如 your-agent-core/src/main/java/com/your/agent/core/loop/ReActEngine.java\"}" +
                "},\"required\":[\"filePath\"]}"
    )
    public String readOwnSource(String argsJson) {
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var node = mapper.readTree(argsJson);
            String filePath = node.get("filePath").asText();
            String content = analyzer.readFile(filePath);
            if (content == null) {
                return "[Error] 文件不存在: " + filePath;
            }
            String[] lines = content.split("\n", -1);
            StringBuilder sb = new StringBuilder();
            sb.append("File: ").append(filePath).append("\n");
            sb.append("Lines: ").append(lines.length).append("\n\n");
            for (int i = 0; i < lines.length; i++) {
                sb.append(String.format("%4d", i + 1)).append(": ").append(lines[i]).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return "[Error] " + e.getMessage();
        }
    }

    @Tool(
        name = "self_count_lines",
        description = "统计凤凰项目总代码行数。返回Java源文件总数和总行数。",
        parametersSchema = "{\"type\":\"object\",\"properties\":{}}"
    )
    public String countLines() {
        var sources = analyzer.scanAllSources();
        int totalLines = sources.stream().mapToInt(sf -> sf.content().split("\n", -1).length).sum();
        return String.format("""
                ## 📊 代码统计
                - 源文件数: %d
                - 总代码行数: %d
                - 平均每文件: %.1f 行
                """, sources.size(), totalLines,
                sources.isEmpty() ? 0 : (double) totalLines / sources.size());
    }
}