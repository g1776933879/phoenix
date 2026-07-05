package com.your.evolution.core;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * 一次自进化尝试的记录。
 */
@Data
@AllArgsConstructor
@Builder
public class EvolutionAttempt {
    private boolean success;
    private CodeIssue issue;
    private String failureReason;

    public static EvolutionAttempt success(CodeIssue issue) {
        return EvolutionAttempt.builder().success(true).issue(issue).build();
    }

    public static EvolutionAttempt failed(CodeIssue issue, String reason) {
        return EvolutionAttempt.builder().success(false).issue(issue).failureReason(reason).build();
    }
}