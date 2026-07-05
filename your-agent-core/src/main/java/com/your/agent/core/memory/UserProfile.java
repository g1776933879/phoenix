package com.your.agent.core.memory;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * L2 用户画像 —— 对应USER.md，持久化存储用户的偏好和习惯。
 * <p>
 * 随着与用户的交互不断更新，包括但不限于：
 * - 用户的语言偏好
 * - 用户常用功能
 * - 用户的业务领域
 * - 用户的行为模式
 */
@Slf4j
public class UserProfile implements MemoryLayer {

    private final Map<String, String> profile = new HashMap<>();

    public UserProfile() {
        // 初始化默认画像
        profile.put("language", "中文");
        profile.put("response_style", "专业严谨");
        log.info("UserProfile (L2) initialized");
    }

    @Override
    public int level() {
        return 2;
    }

    @Override
    public String read() {
        StringBuilder sb = new StringBuilder("=== 用户画像 ===\n");
        profile.forEach((key, value) -> sb.append(key).append(": ").append(value).append("\n"));
        return sb.toString();
    }

    @Override
    public void write(String content) {
        // 解析键值对格式 "key: value" 并更新画像
        String[] lines = content.split("\n");
        for (String line : lines) {
            int colonIdx = line.indexOf(":");
            if (colonIdx > 0 && colonIdx < line.length() - 1) {
                String key = line.substring(0, colonIdx).trim();
                String value = line.substring(colonIdx + 1).trim();
                profile.put(key, value);
                log.debug("UserProfile updated: {}={}", key, value);
            }
        }
    }

    @Override
    public String search(String query) {
        // L2按关键词匹配
        for (Map.Entry<String, String> entry : profile.entrySet()) {
            if (entry.getKey().contains(query) || entry.getValue().contains(query)) {
                return entry.getKey() + ": " + entry.getValue();
            }
        }
        return "";
    }

    /**
     * 获取某个画像字段
     */
    public String get(String key) {
        return profile.get(key);
    }

    /**
     * 设置某个画像字段
     */
    public void set(String key, String value) {
        profile.put(key, value);
        log.debug("UserProfile set: {}={}", key, value);
    }
}