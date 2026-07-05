package com.your.agent.spring.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.your.agent.core.loop.ReActEngine;
import com.your.agent.core.model.Message;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 会话管理器 —— 持久化对话历史，支持多会话切换。
 * 每次对话自动保存，下次启动可查看历史记录。
 */
@Slf4j
@Component
public class SessionManager {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final String dbPath;
    private Connection connection;
    private final List<SessionSummary> sessions = new CopyOnWriteArrayList<>();
    private String currentSessionId;

    public SessionManager() {
        this.dbPath = System.getProperty("user.home") + "/.agent/sessions.db";
        ensureParentDir();
        initDatabase();
        loadSessions();
        createNewSession();
        log.info("SessionManager initialized: {} sessions", sessions.size());
    }

    private void ensureParentDir() {
        try { Files.createDirectories(Paths.get(dbPath).getParent()); } catch (Exception ignored) {}
    }

    private void initDatabase() {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS sessions (" +
                        "id TEXT PRIMARY KEY, title TEXT, created_at TEXT, updated_at TEXT, message_count INTEGER DEFAULT 0)");
                stmt.execute("CREATE TABLE IF NOT EXISTS messages (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, session_id TEXT, role TEXT, content TEXT, " +
                        "tool_calls TEXT, timestamp TEXT, FOREIGN KEY(session_id) REFERENCES sessions(id))");
            }
        } catch (Exception e) {
            log.error("Session DB init failed", e);
            throw new RuntimeException("Session DB init failed", e);
        }
    }

    private void loadSessions() {
        sessions.clear();
        try (Statement stmt = connection.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT id, title, created_at, message_count FROM sessions ORDER BY updated_at DESC LIMIT 50");
            while (rs.next()) {
                sessions.add(new SessionSummary(rs.getString("id"), rs.getString("title"),
                        rs.getString("created_at"), rs.getInt("message_count")));
            }
        } catch (SQLException e) {
            log.error("Load sessions failed", e);
        }
    }

    public String createNewSession() {
        currentSessionId = UUID.randomUUID().toString().substring(0, 8);
        String now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        try (PreparedStatement ps = connection.prepareStatement("INSERT INTO sessions (id, title, created_at, updated_at) VALUES (?,?,?,?)")) {
            ps.setString(1, currentSessionId);
            ps.setString(2, "新对话 " + now.substring(0, 10));
            ps.setString(3, now);
            ps.setString(4, now);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Create session failed", e);
        }
        loadSessions();
        return currentSessionId;
    }

    public void saveMessage(String role, String content, String toolCallsJson) {
        if (currentSessionId == null) return;
        String now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        try (PreparedStatement ps = connection.prepareStatement("INSERT INTO messages (session_id, role, content, tool_calls, timestamp) VALUES (?,?,?,?,?)")) {
            ps.setString(1, currentSessionId);
            ps.setString(2, role);
            ps.setString(3, content != null ? content : "");
            ps.setString(4, toolCallsJson);
            ps.setString(5, now);
            ps.executeUpdate();
            // 更新会话计数和时间
            try (PreparedStatement up = connection.prepareStatement("UPDATE sessions SET message_count = message_count + 1, updated_at = ? WHERE id = ?")) {
                up.setString(1, now);
                up.setString(2, currentSessionId);
                up.executeUpdate();
            }
            // 截取第一条用户消息作为标题
            if ("user".equals(role)) {
                String title = content.length() > 30 ? content.substring(0, 30) + "..." : content;
                try (PreparedStatement up = connection.prepareStatement("UPDATE sessions SET title = ? WHERE id = ? AND title LIKE '新对话%'")) {
                    up.setString(1, title);
                    up.setString(2, currentSessionId);
                    up.executeUpdate();
                }
            }
        } catch (SQLException e) {
            log.error("Save message failed", e);
        }
        loadSessions();
    }

    public List<Map<String, Object>> getSessionMessages(String sessionId) {
        List<Map<String, Object>> msgs = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement("SELECT role, content, tool_calls, timestamp FROM messages WHERE session_id = ? ORDER BY id")) {
            ps.setString(1, sessionId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, Object> m = new HashMap<>();
                m.put("role", rs.getString("role"));
                m.put("content", rs.getString("content"));
                m.put("timestamp", rs.getString("timestamp"));
                msgs.add(m);
            }
        } catch (SQLException e) {
            log.error("Load messages failed", e);
        }
        return msgs;
    }

    public void deleteSession(String sessionId) {
        try {
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM messages WHERE session_id = ?")) {
                ps.setString(1, sessionId); ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM sessions WHERE id = ?")) {
                ps.setString(1, sessionId); ps.executeUpdate();
            }
        } catch (SQLException e) {
            log.error("Delete session failed", e);
        }
        loadSessions();
    }

    public void switchSession(String sessionId) {
        currentSessionId = sessionId;
    }

    public String getCurrentSessionId() { return currentSessionId; }
    public List<SessionSummary> getSessions() { return new ArrayList<>(sessions); }

    public record SessionSummary(String id, String title, String createdAt, int messageCount) {}

    @PreDestroy
    public void close() {
        try { if (connection != null && !connection.isClosed()) connection.close(); }
        catch (SQLException e) { log.error("Close session DB failed", e); }
    }
}