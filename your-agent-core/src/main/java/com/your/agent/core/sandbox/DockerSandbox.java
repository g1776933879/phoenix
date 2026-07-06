package com.your.agent.core.sandbox;

import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Docker 沙箱执行器 —— 在隔离的 Docker 容器中执行命令。
 * 参考 Codex sandbox.md 设计。
 * 防止命令执行对宿主机造成影响，所有系统操作都在容器内完成。
 */
@Slf4j
public class DockerSandbox {

    private final String imageName;
    private final long timeoutSeconds;
    private final boolean enabled;

    public DockerSandbox(String imageName, long timeoutSeconds) {
        this.imageName = imageName != null ? imageName : "ubuntu:22.04";
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : 30;
        this.enabled = checkDockerAvailable();
        if (this.enabled) {
            log.info("DockerSandbox initialized: image={}, timeout={}s", this.imageName, this.timeoutSeconds);
        } else {
            log.warn("Docker not available, sandbox disabled");
        }
    }

    public DockerSandbox() {
        this("ubuntu:22.04", 30);
    }

    /**
     * 在 Docker 容器中执行命令
     * @param command 要执行的 shell 命令
     * @return 执行结果
     */
    public SandboxResult execute(String command) {
        if (!enabled) {
            return new SandboxResult(false, "Docker not available", "", 0);
        }
        long start = System.currentTimeMillis();
        try {
            // 构建 docker run 命令：使用当前目录映射 + 只读 + 超时自动删除
            String[] cmd = {
                "docker", "run", "--rm",
                "-i",                                      // 交互模式
                "--network", "none",                       // 无网络（默认）
                "--read-only",                             // 只读文件系统
                "--tmpfs", "/tmp:rw,noexec,nosuid,size=64m", // 可写临时目录
                "--memory", "256m",                        // 限制内存
                "--cpus", "1",                             // 限制 CPU
                "--pids-limit", "100",                     // 限制进程数
                imageName,
                "/bin/sh", "-c", command
            };

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);

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
                long finalElapsed = System.currentTimeMillis() - start;
                return new SandboxResult(false, "Command timed out after " + timeoutSeconds + "s",
                        output.toString(), finalElapsed);
            }

            int exitCode = process.exitValue();
            boolean success = exitCode == 0;
            String result = output.toString().trim();

            log.info("Sandbox exec: exitCode={}, outputLen={}, time={}ms", exitCode, result.length(), elapsed);
            return new SandboxResult(success, success ? "OK" : "Exit code: " + exitCode, result, elapsed);

        } catch (IOException e) {
            log.error("Sandbox IO error", e);
            return new SandboxResult(false, "IO Error: " + e.getMessage(), "", System.currentTimeMillis() - start);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new SandboxResult(false, "Interrupted", "", System.currentTimeMillis() - start);
        }
    }

    /**
     * 检查 Docker 是否可用
     */
    private boolean checkDockerAvailable() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"docker", "info", "--format", "{{.ServerVersion}}"});
            boolean finished = p.waitFor(5, TimeUnit.SECONDS);
            if (finished && p.exitValue() == 0) {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String version = r.readLine();
                    log.info("Docker available: version={}", version);
                }
                return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isEnabled() { return enabled; }
    public String getImageName() { return imageName; }

    public record SandboxResult(boolean success, String message, String output, long durationMs) {}
}