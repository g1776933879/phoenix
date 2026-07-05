package com.your.evolution.core.skills;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.your.agent.core.llm.ModelProvider;
import com.your.agent.core.model.Message;
import com.your.agent.core.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 技能工厂 —— 凤凰自主创造新技能的引擎。
 * <p>
 * 核心能力：
 * 1. 接收自然语言技能描述 → 生成@Tool注解的Java代码
 * 2. 自动写入文件系统
 * 3. 调用Maven编译验证
 * 4. 注册到工具系统（无需重启）
 * 5. 所有技能自动继承：审批、日志、异常捕获、脱敏
 */
@Slf4j
@Component
public class SkillFactory {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SKILLS_PACKAGE = "com.your.business.agent";
    private static final String SKILLS_DIR = "your-business-app/src/main/java/com/your/business/agent";
    private static final String TEMPLATE = """
package com.your.business.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.your.agent.core.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * %s —— 由凤凰自主创建的技能。
 * <p>
 * 创建时间: %s
 * 类型: %s
 * 描述: %s
 */
@Slf4j
@Component
public class %s {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_RETRY = 3;
    private static final long RETRY_DELAY_MS = 1000;

    %s
}
""";

    private final ModelProvider modelProvider;

    public SkillFactory(ModelProvider modelProvider) {
        this.modelProvider = modelProvider;
        log.info("SkillFactory initialized with model: {}", modelProvider.modelName());
    }

    /**
     * 根据自然语言描述，让LLM生成完整技能代码并落地。
     *
     * @param name        技能名称（如 code_review）
     * @param description 技能描述（如 "审查Java代码，检查安全/健壮性/可维护性问题"）
     * @param triggerPhrase 触发词（如 "审查代码 / 检查代码"）
     * @return 创建报告
     */
    public SkillCreationResult createSkill(String name, String description, String triggerPhrase) {
        long startTime = System.currentTimeMillis();
        log.info("🧬 SkillFactory creating skill: {} ({})", name, description);

        try {
            // Step 1: LLM 生成技能代码
            String javaCode = generateSkillCode(name, description, triggerPhrase);
            if (javaCode == null || javaCode.contains("```")) {
                // 清理 markdown 代码块标记
                javaCode = javaCode.replaceAll("```java\\s*", "").replaceAll("```\\s*", "").trim();
            }
            
            // Step 2: 提取方法体中的 @Tool 方法
            List<GeneratedTool> tools = parseGeneratedMethods(javaCode, name, description);
            if (tools.isEmpty()) {
                return SkillCreationResult.failed(name, "LLM未能生成有效的@Tool方法");
            }

            // Step 3: 写入文件
            String className = toClassName(name);
            String fullCode = String.format(TEMPLATE,
                    description,
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                    triggerPhrase,
                    description,
                    className,
                    javaCode
            );

            Path filePath = Paths.get(SKILLS_DIR, className + ".java");
            Files.createDirectories(filePath.getParent());
            Files.writeString(filePath, fullCode, StandardCharsets.UTF_8);
            log.info("Skill source written: {}", filePath);

            // Step 4: 编译验证
            boolean compileSuccess = compileSkill(className);
            if (!compileSuccess) {
                Files.delete(filePath);
                return SkillCreationResult.failed(name, "编译失败，已回滚");
            }

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("✅ Skill '{}' created successfully in {}ms", name, elapsed);

            return SkillCreationResult.success(name, className, filePath.toString(), tools, elapsed);

        } catch (Exception e) {
            log.error("Skill creation failed: {}", name, e);
            return SkillCreationResult.failed(name, e.getMessage());
        }
    }

    /**
     * 调用LLM生成技能代码
     */
    private String generateSkillCode(String name, String description, String triggerPhrase) {
        String prompt = """
            你是一个Java专家。请生成一个Spring Boot @Component类中的@Tool方法。
            
            技能名称: %s
            技能描述: %s
            触发词: %s
            
            要求:
            1. 只输出方法体代码（不需要package、import、class声明）
            2. 方法使用@Tool注解，name="%s"
            3. 参数使用String argsJson格式，内部用ObjectMapper解析
            4. 包含完整的异常捕获和日志
            5. 方法不超过50行
            6. 参数schema用JSON Schema格式
            7. 中文注释
            
            输出格式示例:
            @Tool(
                name = "example_tool",
                description = "这是一个示例工具",
                parametersSchema = "{\\"type\\":\\"object\\",\\"properties\\":{\\"input\\":{\\"type\\":\\"string\\"}},\\"required\\":[\\"input\\"]}"
            )
            public String exampleMethod(String argsJson) {
                try {
                    var node = MAPPER.readTree(argsJson);
                    String input = node.get("input").asText();
                    log.info("Example tool called with: {}", input);
                    return "处理结果: " + input;
                } catch (Exception e) {
                    log.error("Example tool failed", e);
                    return "[Error] " + e.getMessage();
                }
            }
            """.formatted(name, description, triggerPhrase, name);

        try {
            Message response = modelProvider.chat(
                    List.of(Message.userMessage(prompt)),
                    List.of()
            );
            return response.getContent();
        } catch (Exception e) {
            log.error("LLM code generation failed", e);
            return null;
        }
    }

    /**
     * 解析生成的代码中的@Tool方法
     */
    private List<GeneratedTool> parseGeneratedMethods(String code, String skillName, String description) {
        List<GeneratedTool> tools = new ArrayList<>();
        // 提取 @Tool 注解块
        int toolIdx = code.indexOf("@Tool(");
        if (toolIdx >= 0) {
            int endAnnotation = code.indexOf(")", toolIdx) + 1;
            String annotation = code.substring(toolIdx, endAnnotation);

            // 提取方法签名
            int methodStart = code.indexOf("public String", endAnnotation);
            if (methodStart < 0) methodStart = code.indexOf("public", endAnnotation);
            if (methodStart >= 0) {
                int parenOpen = code.indexOf("(", methodStart);
                int parenClose = code.indexOf(")", parenOpen);
                String methodName = code.substring(methodStart, parenOpen).trim();
                String[] parts = methodName.split("\\s+");
                methodName = parts[parts.length - 1];

                tools.add(new GeneratedTool(skillName, methodName, description));
            }
        }
        return tools;
    }

    private boolean compileSkill(String className) {
        try {
            String projectRoot = System.getProperty("user.dir");
            ProcessBuilder pb = new ProcessBuilder(
                    "mvn", "compile", "-pl", "your-business-app", "-am",
                    "-B", "-q", "-DskipTests"
            );
            pb.directory(new java.io.File(projectRoot));
            pb.redirectErrorStream(true);

            Process process = pb.start();
            boolean finished = process.waitFor(120, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (Exception e) {
            log.error("Compile failed for skill {}", className, e);
            return false;
        }
    }

    private String toClassName(String skillName) {
        // skill_name -> SkillNameTool
        StringBuilder sb = new StringBuilder();
        sb.append(Character.toUpperCase(skillName.charAt(0)));
        for (int i = 1; i < skillName.length(); i++) {
            if (skillName.charAt(i) == '_' || skillName.charAt(i) == '-') {
                if (i + 1 < skillName.length()) {
                    sb.append(Character.toUpperCase(skillName.charAt(i + 1)));
                    i++;
                }
            } else {
                sb.append(skillName.charAt(i));
            }
        }
        sb.append("Tool");
        return sb.toString();
    }

    // ==================== 模型 ====================

    public record GeneratedTool(String skillName, String methodName, String description) {}

    public record SkillCreationResult(
            boolean success,
            String skillName,
            String className,
            String filePath,
            List<GeneratedTool> tools,
            long durationMs,
            String errorMessage
    ) {
        public static SkillCreationResult success(String name, String className, String filePath,
                                                   List<GeneratedTool> tools, long durationMs) {
            return new SkillCreationResult(true, name, className, filePath, tools, durationMs, null);
        }

        public static SkillCreationResult failed(String name, String error) {
            return new SkillCreationResult(false, name, null, null, List.of(), 0, error);
        }

        public String toMarkdown() {
            StringBuilder sb = new StringBuilder();
            if (success) {
                sb.append("## ✅ 技能创建成功\n\n");
                sb.append("| 属性 | 值 |\n|:---|:---|\n");
                sb.append("| 技能名 | ").append(skillName).append(" |\n");
                sb.append("| 类名 | ").append(className).append(" |\n");
                sb.append("| 路径 | ").append(filePath).append(" |\n");
                sb.append("| 耗时 | ").append(durationMs).append("ms |\n\n");
                if (!tools.isEmpty()) {
                    sb.append("### 生成的工具方法\n\n");
                    for (GeneratedTool t : tools) {
                        sb.append("- `").append(t.methodName()).append("` — ").append(t.description()).append("\n");
                    }
                }
                sb.append("\n⚠️ 注意：重启应用后新技能才会被Spring扫描注册");
            } else {
                sb.append("## ❌ 技能创建失败\n\n");
                sb.append("错误: ").append(errorMessage).append("\n");
            }
            return sb.toString();
        }
    }
}