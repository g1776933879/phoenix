package com.your.evolution.core.validation;

import com.your.evolution.core.CodeIssue;
import com.your.evolution.core.introspection.CodeAnalyzer;
import com.your.evolution.core.introspection.CodeSmellDetector;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 质量门禁 —— 在编译和测试通过后，执行质量检查。
 * 确保修改不会降低代码质量。
 */
@Slf4j
public class QualityGate {

    private final CodeSmellDetector detector;
    private final CodeAnalyzer analyzer;
    private final int maxFunctionsOver50Lines;

    public QualityGate(CodeSmellDetector detector, CodeAnalyzer analyzer, int maxFunctionsOver50Lines) {
        this.detector = detector;
        this.analyzer = analyzer;
        this.maxFunctionsOver50Lines = maxFunctionsOver50Lines;
    }

    public QualityGate(CodeSmellDetector detector, CodeAnalyzer analyzer) {
        this(detector, analyzer, 3);
    }

    /**
     * 执行质量门禁检查。
     *
     * @return QualityGateResult
     */
    public QualityGateResult check() {
        List<CodeAnalyzer.SourceFile> sources = analyzer.scanAllSources();
        List<CodeIssue> allIssues = detector.scanAll(sources);

        // 统计各类问题
        long highCount = allIssues.stream().filter(i -> "HIGH".equals(i.getSeverity())).count();
        long mediumCount = allIssues.stream().filter(i -> "MEDIUM".equals(i.getSeverity())).count();
        long funcOver50 = allIssues.stream()
                .filter(i -> "code_style".equals(i.getCategory()) && i.getDescription().contains("过长"))
                .count();

        boolean passed = true;
        StringBuilder report = new StringBuilder();

        if (highCount > 0) {
            passed = false;
            report.append("❌ HIGH级别问题 ").append(highCount).append(" 个，必须修复\n");
        }
        if (funcOver50 > maxFunctionsOver50Lines) {
            passed = false;
            report.append("❌ 过长函数 ").append(funcOver50).append(" 个，超过阈值 ").append(maxFunctionsOver50Lines).append("\n");
        }
        if (mediumCount > 10) {
            report.append("⚠️ MEDIUM级别问题 ").append(mediumCount).append(" 个，建议修复\n");
        }

        if (passed) {
            report.append("✅ 质量门禁通过");
        }

        log.info("QualityGate: passed={}, high={}, medium={}, funcOver50={}",
                passed, highCount, mediumCount, funcOver50);

        return new QualityGateResult(passed, report.toString(), allIssues.size());
    }

    public record QualityGateResult(boolean passed, String report, int totalIssues) {}
}