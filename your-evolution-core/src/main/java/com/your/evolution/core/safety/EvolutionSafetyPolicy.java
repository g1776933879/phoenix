package com.your.evolution.core.safety;

import java.util.Set;

/**
 * 自进化安全策略 —— 定义凤凰不允许自己修改的保护区域。
 */
public class EvolutionSafetyPolicy {

    // 永不修改的保护文件（这些文件改动需要主人手动审批）
    private static final Set<String> PROTECTED_FILES = Set.of(
            "AgentApplication.java",
            "pom.xml",
            "application.yml",
            ".github/workflows/ci.yml",
            "deploy/Dockerfile",
            "deploy/docker-compose.yml",
            "README.md"
    );

    // 禁止注入到代码中的模式（防止提示注入攻击）
    private static final Set<String> BLOCKED_CODE_PATTERNS = Set.of(
            "Runtime.exec",
            "Runtime.getRuntime().exec",
            "System.exit",
            "new ProcessBuilder",
            ".delete()",
            "Files.delete",
            "FileOutputStream"
    );

    // 最多每个循环改几处
    public static final int MAX_PATCHES_PER_CYCLE = 3;

    // 函数最大行数
    public static final int MAX_FUNCTION_LINES = 50;

    /**
     * 检查文件是否受保护
     */
    public static boolean isProtected(String filePath) {
        for (String protectedFile : PROTECTED_FILES) {
            if (filePath.endsWith(protectedFile)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查补丁是否包含危险代码
     */
    public static boolean containsBlockedPattern(String patch) {
        for (String pattern : BLOCKED_CODE_PATTERNS) {
            if (patch.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 完整的安全检查
     */
    public static SafetyCheckResult checkPatch(String filePath, String patch) {
        if (isProtected(filePath)) {
            return SafetyCheckResult.deny("文件受保护，禁止自动修改: " + filePath);
        }
        if (containsBlockedPattern(patch)) {
            return SafetyCheckResult.deny("修改包含危险代码模式");
        }
        return SafetyCheckResult.allow();
    }

    public record SafetyCheckResult(boolean allowed, String reason) {
        public static SafetyCheckResult allow() {
            return new SafetyCheckResult(true, "OK");
        }
        public static SafetyCheckResult deny(String reason) {
            return new SafetyCheckResult(false, reason);
        }
    }
}