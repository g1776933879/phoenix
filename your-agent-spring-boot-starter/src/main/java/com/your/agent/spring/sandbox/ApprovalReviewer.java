package com.your.agent.spring.sandbox;

import lombok.extern.slf4j.Slf4j;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class ApprovalReviewer {
    private final Map<String, PendingApproval> pendingApprovals = new ConcurrentHashMap<>();
    private final AtomicLong idCounter = new AtomicLong(0);

    public String submitApproval(String toolName, Map<String, Object> args, long timeoutMs) {
        String approvalId = "ap-" + idCounter.incrementAndGet();
        pendingApprovals.put(approvalId, new PendingApproval(approvalId, toolName, args, timeoutMs));
        log.warn("=== APPROVAL REQUIRED === Tool: {} | Args: {} | ApprovalId: {}", toolName, args, approvalId);
        return approvalId;
    }
    public String submitApproval(String toolName, Map<String, Object> args) { return submitApproval(toolName, args, 60000); }
    public boolean approve(String approvalId) {
        PendingApproval pa = pendingApprovals.get(approvalId);
        if (pa == null) return false;
        pa.setApproved(true); pa.setResolved(true);
        log.info("=== APPROVED === Tool: {}, ApprovalId: {}", pa.getToolName(), approvalId);
        return true;
    }
    public boolean reject(String approvalId) {
        PendingApproval pa = pendingApprovals.get(approvalId);
        if (pa == null) return false;
        pa.setApproved(false); pa.setResolved(true);
        log.warn("=== REJECTED === Tool: {}, ApprovalId: {}", pa.getToolName(), approvalId);
        return true;
    }
    public boolean waitForApproval(String approvalId) throws InterruptedException {
        PendingApproval pa = pendingApprovals.get(approvalId);
        if (pa == null) return false;
        long deadline = System.currentTimeMillis() + pa.getTimeoutMs();
        while (System.currentTimeMillis() < deadline) {
            if (pa.isResolved()) { pendingApprovals.remove(approvalId); return pa.isApproved(); }
            Thread.sleep(500);
        }
        pendingApprovals.remove(approvalId);
        log.warn("Approval timeout: {}, default denied", approvalId);
        return false;
    }

    public static class PendingApproval {
        private final String approvalId;
        private final String toolName;
        private final Map<String, Object> args;
        private final long timeoutMs;
        private volatile boolean approved = false;
        private volatile boolean resolved = false;
        public PendingApproval(String approvalId, String toolName, Map<String, Object> args, long timeoutMs) {
            this.approvalId = approvalId; this.toolName = toolName; this.args = args; this.timeoutMs = timeoutMs;
        }
        public String getApprovalId() { return approvalId; }
        public String getToolName() { return toolName; }
        public Map<String, Object> getArgs() { return args; }
        public long getTimeoutMs() { return timeoutMs; }
        public boolean isApproved() { return approved; }
        public void setApproved(boolean approved) { this.approved = approved; }
        public boolean isResolved() { return resolved; }
        public void setResolved(boolean resolved) { this.resolved = resolved; }
    }
}
