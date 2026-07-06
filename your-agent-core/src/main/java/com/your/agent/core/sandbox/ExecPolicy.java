package com.your.agent.core.sandbox;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

public class ExecPolicy {

    private final List<PolicyRule> rules = new CopyOnWriteArrayList<>();

    public ExecPolicy() {
        addRule(PolicyRule.deny("rm -rf /", "禁止删除根目录"));
        addRule(PolicyRule.deny("mkfs.*", "禁止格式化磁盘"));
        addRule(PolicyRule.deny("dd if=.*of=/dev/.*", "禁止直接写设备"));
        addRule(PolicyRule.deny(":\\Q(){ :|:& };:\\E", "禁止 fork 炸弹"));
        addRule(PolicyRule.deny("chmod -R 777 /", "禁止开放整个系统权限"));
        addRule(PolicyRule.deny("> /dev/sda", "禁止直接写入磁盘"));
        addRule(PolicyRule.approveIf("curl.*\\|.*sh", "需要审批的远程脚本执行"));
        addRule(PolicyRule.approveIf("wget.*\\|.*sh", "需要审批的远程脚本执行"));
        addRule(PolicyRule.allowIf("ls.*", "列出文件"));
        addRule(PolicyRule.allowIf("cat.*", "查看文件"));
        addRule(PolicyRule.allowIf("echo.*", "输出文本"));
        addRule(PolicyRule.allowIf("pwd", "当前目录"));
        addRule(PolicyRule.allowIf("whoami", "当前用户"));
        addRule(PolicyRule.allowIf("date.*", "日期时间"));
        addRule(PolicyRule.allowIf("df.*", "磁盘信息"));
        addRule(PolicyRule.allowIf("free.*", "内存信息"));
        addRule(PolicyRule.allowIf("ps.*", "进程信息"));
        addRule(PolicyRule.allowIf("git.*", "Git操作"));
        String logMsg = String.format("ExecPolicy initialized: %d rules", rules.size());
        System.out.println(logMsg);
    }

    public PolicyResult evaluate(String command) {
        if (command == null || command.trim().isEmpty()) {
            return PolicyResult.deny("Empty command");
        }
        String trimmed = command.trim();
        for (PolicyRule rule : rules) {
            if (rule.pattern().matcher(trimmed).find()) {
                String action = rule.action();
                if (PolicyRule.DENY.equals(action)) {
                    logPolicy("DENY", trimmed, rule.reason());
                    return PolicyResult.deny(rule.reason());
                } else if (PolicyRule.APPROVE_IF.equals(action)) {
                    logPolicy("APPROVE", trimmed, rule.reason());
                    return PolicyResult.approve(rule.reason());
                } else if (PolicyRule.ALLOW_IF.equals(action)) {
                    logPolicy("ALLOW", trimmed, rule.reason());
                    return PolicyResult.allow(rule.reason());
                }
            }
        }
        logPolicy("DEFAULT_APPROVE", trimmed, "No matching rule");
        return PolicyResult.approve("No matching rule, requires approval");
    }

    public void addRule(PolicyRule rule) { rules.add(rule); }
    public List<PolicyRule> getRules() { return new ArrayList<>(rules); }

    private void logPolicy(String action, String command, String reason) {
        System.out.printf("[ExecPolicy] %s: cmd=%s, reason=%s%n", action,
                command.length() > 100 ? command.substring(0, 100) + "..." : command, reason);
    }

    public record PolicyRule(Pattern pattern, String action, String reason) {
        public static final String DENY = "deny";
        public static final String ALLOW_IF = "allow_if";
        public static final String APPROVE_IF = "approve_if";

        public static PolicyRule deny(String regex, String reason) {
            return new PolicyRule(Pattern.compile(regex, Pattern.CASE_INSENSITIVE), DENY, reason);
        }
        public static PolicyRule allowIf(String regex, String reason) {
            return new PolicyRule(Pattern.compile(regex, Pattern.CASE_INSENSITIVE), ALLOW_IF, reason);
        }
        public static PolicyRule approveIf(String regex, String reason) {
            return new PolicyRule(Pattern.compile(regex, Pattern.CASE_INSENSITIVE), APPROVE_IF, reason);
        }
    }

    public record PolicyResult(boolean allowed, boolean requiresApproval, String reason) {
        public static PolicyResult allow(String reason) { return new PolicyResult(true, false, reason); }
        public static PolicyResult approve(String reason) { return new PolicyResult(true, true, reason); }
        public static PolicyResult deny(String reason) { return new PolicyResult(false, false, reason); }
    }
}