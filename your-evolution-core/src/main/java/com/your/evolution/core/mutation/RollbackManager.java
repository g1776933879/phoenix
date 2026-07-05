package com.your.evolution.core.mutation;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 回滚管理器 —— 管理所有备份文件并提供回滚能力。
 * 支持按文件回滚和批量回滚所有修改。
 */
@Slf4j
public class RollbackManager {

    private final String projectRoot;
    private final Map<String, String> backups = new ConcurrentHashMap<>();

    public RollbackManager(String projectRoot) {
        this.projectRoot = projectRoot;
    }

    public RollbackManager() {
        this(System.getProperty("user.dir"));
    }

    /**
     * 记录备份
     */
    public void recordBackup(String relativePath, String content) {
        backups.put(relativePath, content);
        log.debug("Backup recorded: {}", relativePath);
    }

    /**
     * 回滚单个文件
     */
    public boolean rollback(String relativePath) {
        String backup = backups.get(relativePath);
        if (backup == null) {
            log.warn("No backup found for {}", relativePath);
            return false;
        }
        try {
            Path fullPath = Paths.get(projectRoot, relativePath).normalize();
            Files.writeString(fullPath, backup);
            log.info("Rolled back: {}", relativePath);
            return true;
        } catch (IOException e) {
            log.error("Rollback failed: {}", relativePath, e);
            return false;
        }
    }

    /**
     * 回滚所有已修改的文件
     */
    public int rollbackAll() {
        int count = 0;
        for (String path : backups.keySet()) {
            if (rollback(path)) count++;
        }
        log.info("Rolled back {} files", count);
        return count;
    }

    public boolean hasBackup(String relativePath) {
        return backups.containsKey(relativePath);
    }
}