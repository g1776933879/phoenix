package com.your.evolution.core.validation;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * 编译验证器 —— 执行 mvn compile 验证修改后的代码能否通过编译。
 */
@Slf4j
public class CompileValidator {

    private final String projectRoot;
    private final long timeoutSeconds;

    public CompileValidator(String projectRoot, long timeoutSeconds) {
        this.projectRoot = projectRoot;
        this.timeoutSeconds = timeoutSeconds;
    }

    public CompileValidator() {
        this(System.getProperty("user.dir"), 120);
    }

    /**
     * 执行 Maven 编译，返回结果。
     */
    public CompileResult compile() {
        return compile("compile");
    }

    /**
     * 执行指定Maven阶段
     */
    public CompileResult compile(String phase) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "mvn", phase, "-pl", "your-agent-core", "-am",
                    "-B", "-q", "-DskipTests"
            );
            pb.directory(new java.io.File(projectRoot));
            pb.redirectErrorStream(true);

            log.info("Running: mvn {} (timeout={}s)", phase, timeoutSeconds);
            long start = System.currentTimeMillis();

            Process process = pb.start();
            StringBuilder output = new StringBuilder();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            long elapsed = System.currentTimeMillis() - start;

            if (!finished) {
                process.destroyForcibly();
                log.error("Compile timed out after {}s", timeoutSeconds);
                return new CompileResult(false, "Timeout after " + timeoutSeconds + "s", elapsed);
            }

            int exitCode = process.exitValue();
            boolean success = exitCode == 0;
            String logOutput = output.toString();

            if (success) {
                log.info("Compile succeeded in {}ms", elapsed);
            } else {
                log.error("Compile failed (exit={}) in {}ms", exitCode, elapsed);
                // 只保留最后50行错误信息
                String[] lines = logOutput.split("\n");
                StringBuilder errors = new StringBuilder();
                for (int i = Math.max(0, lines.length - 50); i < lines.length; i++) {
                    errors.append(lines[i]).append("\n");
                }
                logOutput = errors.toString();
            }

            return new CompileResult(success, logOutput, elapsed);

        } catch (IOException | InterruptedException e) {
            log.error("Compile execution failed", e);
            return new CompileResult(false, e.getMessage(), 0);
        }
    }

    public record CompileResult(boolean success, String output, long durationMs) {}
}