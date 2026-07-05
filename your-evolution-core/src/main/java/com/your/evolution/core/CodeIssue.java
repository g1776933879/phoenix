package com.your.evolution.core;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * 代码问题 —— 自检发现的代码质量问题、优化点或Bug。
 */
@Data
@AllArgsConstructor
@Builder
public class CodeIssue {
    private String filePath;          // 相对项目根目录的路径
    private int lineNumber;           // 行号（0=文件级问题）
    private String severity;          // HIGH | MEDIUM | LOW
    private String category;          // security | performance | code_style | bug | tech_debt
    private String description;       // 问题描述
    private String codeSnippet;       // 相关代码片段
    private String suggestion;        // 改进建议
}