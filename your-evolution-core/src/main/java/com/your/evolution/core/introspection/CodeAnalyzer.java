package com.your.evolution.core.introspection;

import com.your.evolution.core.CodeIssue;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 代码分析器 —— 读取凤凰自己的全部Java源文件。
 * 提供文件扫描、内容读取、行号定位能力。
 */
@Slf4j
public class CodeAnalyzer {

    private final String projectRoot;

    public CodeAnalyzer(String projectRoot) {
        this.projectRoot = projectRoot != null ? projectRoot : System.getProperty("user.dir");
        log.info("CodeAnalyzer initialized: projectRoot={}", this.projectRoot);
    }

    public CodeAnalyzer() {
        this(System.getProperty("user.dir"));
    }

    /**
     * 扫描所有Java源文件（排除test目录和target目录）
     */
    public List<SourceFile> scanAllSources() {
        List<SourceFile> sources = new ArrayList<>();
        Path root = Paths.get(projectRoot);

        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(p -> p.toString().endsWith(".java"))
                  .filter(p -> !p.toString().contains("/target/"))
                  .filter(p -> !p.toString().contains("/test/"))
                  .forEach(p -> {
                      try {
                          String content = Files.readString(p);
                          String relPath = root.relativize(p).toString();
                          sources.add(new SourceFile(relPath, content, p));
                      } catch (IOException e) {
                          log.warn("Failed to read: {}", p, e);
                      }
                  });
        } catch (IOException e) {
            log.error("Failed to scan sources", e);
        }

        log.info("Scanned {} source files", sources.size());
        return sources;
    }

    /**
     * 读取指定文件的完整内容
     */
    public String readFile(String relativePath) {
        try {
            Path fullPath = Paths.get(projectRoot, relativePath).normalize();
            return Files.readString(fullPath);
        } catch (IOException e) {
            log.error("Failed to read file: {}", relativePath, e);
            return null;
        }
    }

    /**
     * 获取指定行号的代码上下文
     */
    public String getCodeContext(String relativePath, int lineNumber, int contextLines) {
        String content = readFile(relativePath);
        if (content == null) return null;

        String[] lines = content.split("\n", -1);
        int start = Math.max(0, lineNumber - contextLines - 1);
        int end = Math.min(lines.length, lineNumber + contextLines);

        StringBuilder sb = new StringBuilder();
        for (int i = start; i < end; i++) {
            String prefix = (i == lineNumber - 1) ? ">>> " : "    ";
            sb.append(prefix).append(String.format("%4d", i + 1)).append(": ").append(lines[i]).append("\n");
        }
        return sb.toString();
    }

    /**
     * 计算项目总代码行数
     */
    public int countTotalLines() {
        return scanAllSources().stream().mapToInt(sf -> sf.content.split("\n", -1).length).sum();
    }

    public record SourceFile(String relativePath, String content, Path fullPath) {}
}