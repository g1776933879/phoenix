package com.your.evolution.core.validation;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * 测试运行器 —— 执行 mvn test 验证修改后的代码能否通过单元测试。
 */
@Slf4j
public class TestRunner {

    private final String projectRoot;
    private final long timeoutSeconds;

    public TestRunner(String projectRoot, long timeoutSeconds) {
        this.projectRoot = projectRoot;
        this.timeoutSeconds = timeoutSeconds;
    }

    public TestRunner() {
        this(System.getProperty("user.dir"), 180);
    }

    /**
     * 运行核心模块的单元测试
     */
    public TestResult runCoreTests() {
        return run("test -pl your-agent-core -am -B -q");
    }

    /**
     * 运行集成测试
     */
    public TestResult runIntegrationTests() {
        return run("test -pl your-integration-tests -am -B -q");
    }

    /**
     * 运行所有测试
     */
    public TestResult runAll() {
        return run("test -B -q");
    }

    private TestResult run(String mvnArgs) {
        try {
            String[] cmdParts = mvnArgs.split("\\s+");
            String[] cmd = new String[cmdParts.length + 1];
            cmd[0] = "mvn";
            System.arraycopy(cmdParts, 0, cmd, 1, cmdParts.length);
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(new java.io.File(projectRoot));
            pb.redirectErrorStream(true);

            log.info("Running: mvn {} (timeout={}s)", mvnArgs, timeoutSeconds);
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
                return new TestResult(false, "Timeout after " + timeoutSeconds + "s", 0, 0, elapsed);
            }

            int exitCode = process.exitValue();
            String logOutput = output.toString();

            // 解析测试结果
            int passed = 0, failed = 0;
            for (String line : logOutput.split("\n")) {
                if (line.contains("Tests run:")) {
                    // Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
                    String[] parts = line.split(",");
                    for (String part : parts) {
                        if (part.contains("Tests run:")) {
                            passed = Integer.parseInt(part.replaceAll("[^0-9]", ""));
                        }
                        if (part.contains("Failures:") || part.contains("Errors:")) {
                            failed += Integer.parseInt(part.replaceAll("[^0-9]", ""));
                        }
                    }
                }
            }

            boolean success = exitCode == 0;
            log.info("Tests {}: passed={}, failed={}, duration={}ms",
                    success ? "PASSED" : "FAILED", passed, failed, elapsed);

            return new TestResult(success, logOutput, passed, failed, elapsed);

        } catch (Exception e) {
            log.error("Test execution failed", e);
            return new TestResult(false, e.getMessage(), 0, 0, 0);
        }
    }

    public record TestResult(boolean passed, String output, int passedTests, int failedTests, long durationMs) {}
}