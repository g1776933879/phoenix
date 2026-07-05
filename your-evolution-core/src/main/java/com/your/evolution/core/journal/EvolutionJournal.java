package com.your.evolution.core.journal;

import com.your.evolution.core.EvolutionReport;
import com.your.evolution.core.introspection.CodeAnalyzer;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 演化日志 —— 记录每次自进化循环的完整历史。
 * 包括：时间、修改了哪些文件、通过了哪些验证、耗时等。
 * 日志存储为 Markdown 格式，便于阅读。
 */
@Slf4j
public class EvolutionJournal {

    private final String journalPath;

    public EvolutionJournal(String journalPath) {
        this.journalPath = journalPath;
        try {
            Path path = Paths.get(journalPath);
            Files.createDirectories(path.getParent());
            if (!Files.exists(path)) {
                Files.writeString(path, "# 凤凰 · 自进化日志\n\n", StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            log.error("Failed to initialize journal at {}", journalPath, e);
        }
    }

    public EvolutionJournal() {
        this(System.getProperty("user.home") + "/.agent/evolution-journal.md");
    }

    /**
     * 记录一次自进化循环
     */
    public void log(EvolutionReport report) {
        try {
            String entry = buildEntry(report);
            Path path = Paths.get(journalPath);
            Files.writeString(path, entry, StandardCharsets.UTF_8,
                    StandardOpenOption.APPEND, StandardOpenOption.CREATE);
            log.info("Evolution logged to {}", journalPath);
        } catch (IOException e) {
            log.error("Failed to write journal entry", e);
        }
    }

    private String buildEntry(EvolutionReport report) {
        StringBuilder sb = new StringBuilder();
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        sb.append("---\n\n");
        sb.append("## 🕐 ").append(timestamp).append("\n\n");
        sb.append("| 指标 | 数值 |\n|:---|:---:|\n");
        sb.append("| 扫描问题数 | ").append(report.getTotalIssues()).append(" |\n");
        sb.append("| 成功修补 | ").append(report.getPatchesApplied()).append(" |\n");
        sb.append("| 失败修补 | ").append(report.getPatchesFailed()).append(" |\n");
        sb.append("| 有变更 | ").append(report.isHasChanges()).append(" |\n");
        sb.append("| 耗时 | ").append(report.getDurationMs()).append("ms |\n\n");

        if (report.getAttempts() != null && !report.getAttempts().isEmpty()) {
            sb.append("### 修改记录\n\n");
            for (var attempt : report.getAttempts()) {
                String icon = attempt.isSuccess() ? "✅" : "❌";
                sb.append("- ").append(icon).append(" ")
                  .append(attempt.getIssue().getFilePath()).append(":")
                  .append(attempt.getIssue().getLineNumber()).append(" — ")
                  .append(attempt.getIssue().getDescription());
                if (!attempt.isSuccess() && attempt.getFailureReason() != null) {
                    sb.append(" (原因: ").append(attempt.getFailureReason()).append(")");
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * 读取完整演化日志
     */
    public String read() {
        try {
            return Files.readString(Paths.get(journalPath));
        } catch (IOException e) {
            log.error("Failed to read journal", e);
            return "日志文件不可用";
        }
    }
}