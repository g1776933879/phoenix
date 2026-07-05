package com.your.agent.spring.sandbox;

/**
 * 沙箱策略接口 —— 定义工具在执行敏感操作时的安全隔离策略。
 * <p>
 * 根据配置选择不同的实现：
 * <ul>
 *   <li>none     —— 不隔离（开发环境）</li>
 *   <li>docker   —— Docker容器隔离（生产环境）</li>
 * </ul>
 */
@FunctionalInterface
public interface SandboxStrategy {

    /**
     * 在沙箱中执行命令。
     *
     * @param command 要执行的命令
     * @return 命令执行结果
     * @throws Exception 执行失败或沙箱超时
     */
    String execute(String command) throws Exception;
}