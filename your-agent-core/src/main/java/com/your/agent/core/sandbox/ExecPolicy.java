package com.your.agent.core.sandbox;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

public class ExecPolicy {

    private final List<PolicyRule> rules = new CopyOnWriteArrayList<>();

    public ExecPolicy() {
        addRule(PolicyRule.deny("rm -rf /", "deny rm -rf /"));
        addRule(PolicyRule.deny("mkfs.", "deny mkfs"));
        addRule(PolicyRule.deny("dd if=", "deny dd"));
        addRule(PolicyRule.deny("chmod -R 777 /", "deny chmod 777"));
        addRule(PolicyRule.approveIf("curl.*|.*sh", "approve curl pipe"));
        addRule(PolicyRule.approveIf("wget.*|.*sh", "approve wget pipe"));
        addRule(PolicyRule.allowIf("ls", "allow ls"));
        addRule(PolicyRule.allowIf("cat", "allow cat"));
        addRule(PolicyRule.allowIf("echo", "allow echo"));
        addRule(PolicyRule.allowIf("pwd", "allow pwd"));
        addRule(PolicyRule.allowIf("whoami", "allow whoami"));
        addRule(PolicyRule.allowIf("date", "allow date"));
        addRule(PolicyRule.allowIf("df", "allow df"));
        addRule(PolicyRule.allowIf("free", "allow free"));
        addRule(PolicyRule.allowIf("ps", "allow ps"));
        addRule(PolicyRule.allowIf("git", "allow git"));
        System.out.println("ExecPolicy: " + rules.size() + " rules");
    }

    public PolicyResult evaluate(String command) {
        if (command == null || command.trim().isEmpty()) return PolicyResult.deny("empty");
        String trimmed = command.trim();
        for (PolicyRule rule : rules) {
            if (rule.pattern().matcher(trimmed).find()) {
                if (PolicyRule.DENY.equals(rule.action())) return PolicyResult.deny(rule.reason());
                else if (PolicyRule.APPROVE_IF.equals(rule.action())) return PolicyResult.approve(rule.reason());
                else if (PolicyRule.ALLOW_IF.equals(rule.action())) return PolicyResult.allow(rule.reason());
            }
        }
        return PolicyResult.approve("default");
    }

    public void addRule(PolicyRule rule) { rules.add(rule); }
    public List<PolicyRule> getRules() { return new ArrayList<>(rules); }

    public record PolicyRule(Pattern pattern, String action, String reason) {
        public static final String DENY = "deny";
        public static final String ALLOW_IF = "allow_if";
        public static final String APPROVE_IF = "approve_if";
        public static PolicyRule deny(String regex, String reason) { return new PolicyRule(Pattern.compile(regex, 2), DENY, reason); }
        public static PolicyRule allowIf(String regex, String reason) { return new PolicyRule(Pattern.compile(regex, 2), ALLOW_IF, reason); }
        public static PolicyRule approveIf(String regex, String reason) { return new PolicyRule(Pattern.compile(regex, 2), APPROVE_IF, reason); }
    }

    public record PolicyResult(boolean allowed, boolean requiresApproval, String reason) {
        public static PolicyResult allow(String r) { return new PolicyResult(true, false, r); }
        public static PolicyResult approve(String r) { return new PolicyResult(true, true, r); }
        public static PolicyResult deny(String r) { return new PolicyResult(false, false, r); }
    }
}