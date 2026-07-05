package com.your.evolution.core;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 自进化报告 —— 一次自进化循环的完整输出。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EvolutionReport {
    private int totalIssues;
    private int patchesApplied;
    private int patchesFailed;
    private List<EvolutionAttempt> attempts;
    private long durationMs;
    private boolean hasChanges;

    public static EvolutionReport noChange() {
        return EvolutionReport.builder()
                .totalIssues(0).patchesApplied(0)
                .hasChanges(false).durationMs(0)
                .build();
    }

    public String toMarkdown() {
        StringBuilder sb = new StringBuilder();
        sb.append("## 🔄 自进化报告\n\n");
        sb.append("| 指标 | 数值 |\n|:---|:---:|\n");
        sb.append("| 扫描问题数 | ").append(totalIssues).append(" |\n");
        sb.append("| 成功修补 | ").append(patchesApplied).append(" |\n");
        sb.append("| 失败修补 | ").append(patchesFailed).append(" |\n");
        sb.append("| 耗时 | ").append(durationMs).append("ms |\n");
        sb.append("| 有变更 | ").append(hasChanges).append(" |\n\n");
        if (attempts != null) {
            for (EvolutionAttempt a : attempts) {
                sb.append("- ").append(a.isSuccess() ? "✅" : "❌")
                  .append(" ").append(a.getIssue().getFilePath())
                  .append(":").append(a.getIssue().getLineNumber())
                  .append(" — ").append(a.getIssue().getDescription())
                  .append("\n");
            }
        }
        return sb.toString();
    }
}