package com.your.evolution.core.mutation;

import com.your.evolution.core.CodeIssue;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 补丁应用器 —— 将LLM生成的代码修改安全应用到源文件中。
 * 自动创建备份，支持回滚。
 */
@Slf4j
public class PatchApplier {

    private final String projectRoot;
    private final Map<String, String> backups = new ConcurrentHashMap<>(); // path -> backup content

    public PatchApplier(String projectRoot) {
        this.projectRoot = projectRoot;
        log.info("PatchApplier initialized: projectRoot={}", this.projectRoot);
    }

    public PatchApplier() {
        this(System.getProperty("user.dir"));
    }

    /**
     * 应用补丁到指定文件的指定范围。
     * 自动创建备份文件。
     *
     * @param filePath  相对路径
     * @param oldCode   被替换的旧代码（精确匹配）
     * @param newCode   新代码
     * @return true=应用成功
     */
    public boolean apply(String filePath, String oldCode, String newCode) {
        try {
            Path fullPath = resolvePath(filePath);
            String content = Files.readString(fullPath, StandardCharsets.UTF_8);

            // 首次修改前创建备份
            String backupKey = fullPath.toString();
            if (!backups.containsKey(backupKey)) {
                backups.put(backupKey, content);
                // 也写一个.backup文件
                Path backupPath = Paths.get(fullPath + ".backup." +
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")));
                Files.write(backupPath, content.getBytes(StandardCharsets.UTF_8));
                log.info("Backup created: {}", backupPath);
            }

            if (!content.contains(oldCode)) {
                log.warn("Old code not found in {} - patch may be stale", filePath);
                return false;
            }

            String newContent = content.replace(oldCode, newCode);
            if (newContent.equals(content)) {
                log.warn("Patch resulted in no change for {}", filePath);
                return false;
            }

            Files.writeString(fullPath, newContent, StandardCharsets.UTF_8);
            log.info("Patch applied to {}: {} -> {} chars", filePath, content.length(), newContent.length());
            return true;

        } catch (IOException e) {
            log.error("Failed to apply patch to {}", filePath, e);
            return false;
        }
    }

    /**
     * 回滚指定文件到备份版本
     */
    public boolean rollback(String filePath) {
        try {
            Path fullPath = resolvePath(filePath);
            String backupContent = backups.get(fullPath.toString());
            if (backupContent == null) {
                log.warn("No backup found for {}", filePath);
                return false;
            }
            Files.writeString(fullPath, backupContent, StandardCharsets.UTF_8);
            log.info("Rolled back: {}", filePath);
            return true;
        } catch (IOException e) {
            log.error("Failed to rollback {}", filePath, e);
            return false;
        }
    }

    private Path resolvePath(String relativePath) {
        return Paths.get(projectRoot, relativePath).normalize();
    }
}