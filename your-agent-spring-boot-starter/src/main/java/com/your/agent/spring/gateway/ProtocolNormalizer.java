package com.your.agent.spring.gateway;

import com.your.agent.core.model.Message;
import com.your.agent.core.model.ToolCall;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 协议净化器 —— 负责消息格式的清洗、校验和转换。
 * <p>
 * 借鉴OpenClaw协议净化思路，确保进入ReActEngine的消息总是规范格式。
 * 功能：
 * - 移除控制字符和异常Unicode
 * - 校验消息字段完整性
 * - 截断超长消息防上下文溢出
 * - 敏感信息脱敏（手机号、邮箱、密码等）
 */
@Slf4j
public class ProtocolNormalizer {

    /** 最大消息内容长度 */
    private static final int MAX_CONTENT_LENGTH = 32768;

    private static final Pattern PHONE_PATTERN = Pattern.compile("1[3-9]\\d{9}");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}");
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("\\d{17}[\\dXx]");
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("(?i)(password|pwd|secret|token|api_key)\\s*[:=]\\s*\\S+");
    /** 合法角色集合，预编译避免每次调用创建Set */
    private static final Set<String> VALID_ROLES = Set.of("user", "assistant", "tool", "system");

    /**
     * 净化单条消息
     *
     * @param message 原始消息
     * @return 净化后的消息
     */
    public Message normalize(Message message) {
        if (message == null) {
            log.warn("ProtocolNormalizer received null message");
            return null;
        }

        Message normalized = new Message();
        normalized.setId(message.getId() != null ? message.getId() : UUID.randomUUID().toString());
        normalized.setRole(sanitizeRole(message.getRole()));
        normalized.setContent(sanitizeContent(message.getContent()));
        normalized.setToolCallId(message.getToolCallId());
        normalized.setTimestamp(message.getTimestamp() > 0 ? message.getTimestamp() : System.currentTimeMillis());

        // 处理工具调用
        if (message.getToolCalls() != null && !message.getToolCalls().isEmpty()) {
            List<ToolCall> normalizedCalls = new ArrayList<>();
            for (ToolCall tc : message.getToolCalls()) {
                if (tc != null && tc.getName() != null && !tc.getName().isEmpty()) {
                    normalizedCalls.add(tc);
                } else {
                    log.warn("Skipping invalid ToolCall: {}", tc);
                }
            }
            normalized.setToolCalls(normalizedCalls.isEmpty() ? null : normalizedCalls);
        }

        return normalized;
    }

    /**
     * 净化角色字段
     */
    private String sanitizeRole(String role) {
        if (role == null) return "user";
        String r = role.trim().toLowerCase();
        if (VALID_ROLES.contains(r)) {
            return r;
        }
        log.warn("Unknown role '{}', defaulting to 'user'", role);
        return "user";
    }

    /**
     * 净化内容字段：去控制字符、截断、脱敏
     */
    public String sanitizeContent(String content) {
        if (content == null || content.isEmpty()) return "";

        String sanitized = content;

        // 1. 移除控制字符（保留常见换行和制表符）
        sanitized = sanitized.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "");

        // 2. 脱敏
        sanitized = PHONE_PATTERN.matcher(sanitized).replaceAll("138****0000");
        sanitized = EMAIL_PATTERN.matcher(sanitized).replaceAll("***@***.com");
        sanitized = ID_CARD_PATTERN.matcher(sanitized).replaceAll("*****************");
        sanitized = PASSWORD_PATTERN.matcher(sanitized)
                .replaceAll(mr -> mr.group(1) + ": ***");

        // 3. 截断超长内容
        if (sanitized.length() > MAX_CONTENT_LENGTH) {
            sanitized = sanitized.substring(0, MAX_CONTENT_LENGTH)
                    + "\n... [截断：消息长度超过" + MAX_CONTENT_LENGTH + "字符]";
            log.warn("Content truncated from {} to {} chars", content.length(), MAX_CONTENT_LENGTH);
        }

        return sanitized;
    }

    /**
     * 校验消息是否合法
     *
     * @return true=合法
     */
    public boolean validate(Message message) {
        if (message == null) return false;
        if (message.getRole() == null) return false;
        if (!VALID_ROLES.contains(message.getRole())) return false;
        if ("tool".equals(message.getRole()) && message.getToolCallId() == null) {
            log.warn("Tool message without toolCallId: {}", message.getId());
            return false;
        }
        return true;
    }

    /**
     * 批量净化消息列表
     */
    public List<Message> normalizeBatch(List<Message> messages) {
        if (messages == null) return Collections.emptyList();
        return messages.stream()
                .map(this::normalize)
                .filter(Objects::nonNull)
                .filter(this::validate)
                .toList();
    }
}