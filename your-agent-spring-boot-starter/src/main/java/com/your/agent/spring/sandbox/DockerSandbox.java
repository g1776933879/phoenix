package com.your.agent.spring.sandbox;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Docker沙箱实现 —— 通过在Docker容器中执行命令实现安全隔离。
 * <p>
 * 所有命令在指定的Docker镜像中运行，与宿主机完全隔离。
 * 适用于生产环境中的敏感操作执行。
 * <p>
 * 前置条件：宿主机需要安装Docker，且当前用户有权限执行docker命令。
 */
@Slf4j
public class DockerSandbox implements SandboxStrategy {

    private final String dockerImage;
    private final int timeoutSeconds;

    public DockerSandbox(String dockerImage, int timeoutSeconds) {
        this.dockerImage = dockerImage != null ? dockerImage : "ubuntu:22.04";
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : 30;
        log.info("DockerSandbox initialized: image={}, timeout={}s", this.dockerImage, this.timeoutSeconds);
    }

    public DockerSandbox() {
        this("ubuntu:22.04", 30);
    }

    @Override
    public String execute(String command) throws Exception {
        // 构造docker run命令
        String[] cmd = {
                "docker", "run", "--rm",
                "--network", "none",          // 无网络，防止数据外泄
                "--memory", "256m",           // 内存限制
                "--cpus", "0.5",              // CPU限制
                "--read-only",                // 只读文件系统
                "--tmpfs", "/tmp:rw,size=64m",// 仅/tmp可写
                dockerImage,
                "/bin/sh", "-c", command
        };

        log.info("Docker sandbox executing: image={}, command={}", dockerImage, command);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        // 读取输出
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        // 等待完成（带超时）
        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            String msg = "[Sandbox Timeout] Docker execution exceeded " + timeoutSeconds + " seconds";
            log.warn(msg);
            return msg;
        }

        int exitCode = process.exitValue();
        String result = output.toString().trim();

        log.info("Docker sandbox completed: exitCode={}, outputLength={}", exitCode, result.length());
        return String.format("Exit code: %d\nOutput:\n%s", exitCode, result.isEmpty() ? "(empty)" : result);
    }
}