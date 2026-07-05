package com.your.evolution.core;

import com.your.evolution.core.introspection.CodeAnalyzer;
import com.your.evolution.core.introspection.CodeSmellDetector;
import com.your.evolution.core.journal.EvolutionJournal;
import com.your.evolution.core.mutation.CodeGenerator;
import com.your.evolution.core.mutation.PatchApplier;
import com.your.evolution.core.mutation.RollbackManager;
import com.your.evolution.core.safety.EvolutionSafetyPolicy;
import com.your.evolution.core.validation.CompileValidator;
import com.your.evolution.core.validation.QualityGate;
import com.your.evolution.core.validation.TestRunner;
import com.your.agent.core.llm.ModelProvider;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ⭐ 自进化引擎 —— 凤凰自我进化的核心循环。
 * <p>
 * 执行流程：
 * 1. 扫描所有源文件
 * 2. 检测代码坏味道和问题
 * 3. 对每个问题，调用LLM生成修复补丁
 * 4. 安全策略检查（受保护文件？危险代码模式？）
 * 5. 应用补丁到源文件
 * 6. 编译验证（mvn compile）
 * 7. 测试验证（mvn test）
 * 8. 质量门禁检查
 * 9. 通过→提交；失败→回滚
 * 10. 记录演化日志到L4记忆
 */
@Slf4j
public class EvolutionEngine {

    private final CodeAnalyzer analyzer;
    private final CodeSmellDetector smellDetector;
    private final CodeGenerator codeGenerator;
    private final PatchApplier patchApplier;
    private final RollbackManager rollbackManager;
    private final CompileValidator compileValidator;
    private final TestRunner testRunner;
    private final QualityGate qualityGate;
    private final EvolutionJournal journal;
    private final ModelProvider modelProvider;

    public EvolutionEngine(ModelProvider modelProvider) {
        this.modelProvider = modelProvider;
        String projectRoot = System.getProperty("user.dir");

        this.analyzer = new CodeAnalyzer(projectRoot);
        this.smellDetector = new CodeSmellDetector();
        this.codeGenerator = new CodeGenerator(modelProvider, analyzer);
        this.patchApplier = new PatchApplier(projectRoot);
        this.rollbackManager = new RollbackManager(projectRoot);
        this.compileValidator = new CompileValidator(projectRoot, 120);
        this.testRunner = new TestRunner(projectRoot, 180);
        this.qualityGate = new QualityGate(smellDetector, analyzer);
        this.journal = new EvolutionJournal();

        log.info("EvolutionEngine initialized: model={}, projectRoot={}",
                modelProvider.modelName(), projectRoot);
    }

    /**
     * 执行一次完整的自进化循环。
     * 返回详细报告，包含成功/失败的每次尝试。
     */
    public EvolutionReport evolve() {
        long startTime = System.currentTimeMillis();
        log.info("=== 自进化循环开始 ===");

        // Step 1: 扫描所有源文件
        var sources = analyzer.scanAllSources();
        log.info("Step 1: Scanned {} source files", sources.size());

        // Step 2: 检测代码问题
        List<CodeIssue> allIssues = smellDetector.scanAll(sources);
        log.info("Step 2: Found {} issues", allIssues.size());

        // 只处理HIGH和MEDIUM问题
        List<CodeIssue> actionableIssues = allIssues.stream()
                .filter(i -> "HIGH".equals(i.getSeverity()) || "MEDIUM".equals(i.getSeverity()))
                .limit(EvolutionSafetyPolicy.MAX_PATCHES_PER_CYCLE)
                .collect(Collectors.toList());

        if (actionableIssues.isEmpty()) {
            log.info("No actionable issues found, skipping evolution");
            EvolutionReport report = EvolutionReport.noChange();
            report.setTotalIssues(allIssues.size());
            return report;
        }

        log.info("Step 3: Attempting to fix {} issues", actionableIssues.size());
        List<EvolutionAttempt> attempts = new ArrayList<>();
        int patchesApplied = 0;
        int patchesFailed = 0;

        // Step 3-8: 对每个问题：生成补丁→安全检查→应用→编译→测试→质量门禁
        for (CodeIssue issue : actionableIssues) {
            // 3a: 安全检查 - 文件是否受保护？
            if (EvolutionSafetyPolicy.isProtected(issue.getFilePath())) {
                log.warn("Skipping protected file: {}", issue.getFilePath());
                attempts.add(EvolutionAttempt.failed(issue, "受保护文件"));
                patchesFailed++;
                continue;
            }

            // 3b: 调用LLM生成补丁
            var patch = codeGenerator.generatePatch(issue);
            if (patch == null) {
                log.warn("Failed to generate patch for {}", issue.getFilePath());
                attempts.add(EvolutionAttempt.failed(issue, "LLM生成补丁失败"));
                patchesFailed++;
                continue;
            }

            // 3c: 安全检查 - 补丁是否包含危险代码？
            var safetyCheck = EvolutionSafetyPolicy.checkPatch(patch.filePath(), patch.newCode());
            if (!safetyCheck.allowed()) {
                log.warn("Safety check failed: {}", safetyCheck.reason());
                attempts.add(EvolutionAttempt.failed(issue, "安全策略拦截: " + safetyCheck.reason()));
                patchesFailed++;
                continue;
            }

            // 4: 应用补丁
            boolean applied = patchApplier.apply(patch.filePath(), patch.oldCode(), patch.newCode());
            if (!applied) {
                log.warn("Failed to apply patch for {}", issue.getFilePath());
                attempts.add(EvolutionAttempt.failed(issue, "补丁应用失败"));
                patchesFailed++;
                continue;
            }

            // 5: 编译验证
            var compileResult = compileValidator.compile();
            if (!compileResult.success()) {
                log.error("Compile failed after patch: {}", compileResult.output());
                rollbackManager.rollback(patch.filePath());
                attempts.add(EvolutionAttempt.failed(issue, "编译失败: " + compileResult.output().substring(0, Math.min(200, compileResult.output().length()))));
                patchesFailed++;
                continue;
            }
            log.info("Compile passed in {}ms", compileResult.durationMs());

            // 6: 测试验证
            var testResult = testRunner.runCoreTests();
            if (!testResult.passed()) {
                log.error("Tests failed after patch: {} failures", testResult.failedTests());
                rollbackManager.rollback(patch.filePath());
                attempts.add(EvolutionAttempt.failed(issue, "测试失败: " + testResult.failedTests() + "个失败"));
                patchesFailed++;
                continue;
            }
            log.info("Tests passed: {}/{}", testResult.passedTests(), testResult.passedTests() + testResult.failedTests());

            // 7: 质量门禁
            var qualityResult = qualityGate.check();
            if (!qualityResult.passed()) {
                log.warn("Quality gate failed: {}", qualityResult.report());
                rollbackManager.rollback(patch.filePath());
                attempts.add(EvolutionAttempt.failed(issue, "质量门禁未通过"));
                patchesFailed++;
                continue;
            }

            // ✅ 所有验证通过！
            patchesApplied++;
            attempts.add(EvolutionAttempt.success(issue));
            log.info("✅ Successfully evolved: {}:{} - {}",
                    issue.getFilePath(), issue.getLineNumber(), issue.getDescription());
        }

        // 9: 构建报告
        long elapsed = System.currentTimeMillis() - startTime;

        EvolutionReport report = EvolutionReport.builder()
                .totalIssues(allIssues.size())
                .patchesApplied(patchesApplied)
                .patchesFailed(patchesFailed)
                .attempts(attempts)
                .durationMs(elapsed)
                .hasChanges(patchesApplied > 0)
                .build();

        // 10: 记录演化日志
        journal.log(report);

        log.info("=== 自进化循环完成: {}成功, {}失败, {}ms ===",
                patchesApplied, patchesFailed, elapsed);

        return report;
    }

    /**
     * 只执行分析（只读模式），不修改任何代码。
     */
    public String analyzeOnly() {
        var sources = analyzer.scanAllSources();
        var issues = smellDetector.scanAll(sources);

        StringBuilder sb = new StringBuilder();
        sb.append("## 🔍 代码分析报告\n\n");
        sb.append("扫描了 ").append(sources.size()).append(" 个源文件，发现 ").append(issues.size()).append(" 个问题\n\n");

        for (String severity : List.of("HIGH", "MEDIUM", "LOW")) {
            long count = issues.stream().filter(i -> severity.equals(i.getSeverity())).count();
            if (count > 0) {
                sb.append("### ").append(severity).append(" (").append(count).append(")\n\n");
                for (CodeIssue issue : issues) {
                    if (severity.equals(issue.getSeverity())) {
                        sb.append("- `").append(issue.getFilePath()).append(":").append(issue.getLineNumber())
                          .append("` — ").append(issue.getDescription()).append("\n");
                    }
                }
                sb.append("\n");
            }
        }

        return sb.toString();
    }
}