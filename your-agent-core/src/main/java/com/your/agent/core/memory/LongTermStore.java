package com.your.agent.core.memory;

import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;

/**
 * L4 长程记忆 —— 基于SQLite + FTS5全文检索引擎。
 * 修复：自动创建数据库父目录，避免因目录不存在导致启动失败。
 */
@Slf4j
public class LongTermStore implements MemoryLayer {

    private final String dbPath;
    private Connection connection;

    public LongTermStore(String dbPath) {
        this.dbPath = dbPath != null ? dbPath : System.getProperty("user.home") + "/.agent/long_term.db";
        ensureParentDir();
        initializeDatabase();
    }

    public LongTermStore() {
        this(null);
    }

    /**
     * 确保数据库父目录存在
     */
    private void ensureParentDir() {
        try {
            Path parent = Paths.get(dbPath).getParent();
            if (parent != null) {
                Files.createDirectories(parent);
                log.debug("Created database directory: {}", parent);
            }
        } catch (Exception e) {
            log.warn("Failed to create database directory: {}", e.getMessage());
        }
    }

    private void initializeDatabase() {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);

            try (Statement stmt = connection.createStatement()) {
                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS memories (" +
                    "  id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "  title TEXT NOT NULL," +
                    "  content TEXT NOT NULL," +
                    "  category TEXT DEFAULT 'general'," +
                    "  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")"
                );
            }

            try (Statement stmt = connection.createStatement()) {
                stmt.execute(
                    "CREATE VIRTUAL TABLE IF NOT EXISTS memories_fts USING fts5(" +
                    "  title, content, category," +
                    "  content='memories', content_rowid='id'" +
                    ")"
                );
            }

            try (Statement stmt = connection.createStatement()) {
                stmt.execute(
                    "CREATE TRIGGER IF NOT EXISTS memories_ai AFTER INSERT ON memories BEGIN " +
                    "  INSERT INTO memories_fts(rowid, title, content, category) " +
                    "  VALUES (new.id, new.title, new.content, new.category); " +
                    "END"
                );
            }
            try (Statement stmt = connection.createStatement()) {
                stmt.execute(
                    "CREATE TRIGGER IF NOT EXISTS memories_ad AFTER DELETE ON memories BEGIN " +
                    "  INSERT INTO memories_fts(memories_fts, rowid, title, content, category) " +
                    "  VALUES ('delete', old.id, old.title, old.content, old.category); " +
                    "END"
                );
            }
            try (Statement stmt = connection.createStatement()) {
                stmt.execute(
                    "CREATE TRIGGER IF NOT EXISTS memories_au AFTER UPDATE ON memories BEGIN " +
                    "  INSERT INTO memories_fts(memories_fts, rowid, title, content, category) " +
                    "  VALUES ('delete', old.id, old.title, old.content, old.category); " +
                    "  INSERT INTO memories_fts(rowid, title, content, category) " +
                    "  VALUES (new.id, new.title, new.content, new.category); " +
                    "END"
                );
            }

            log.info("LongTermStore (L4) initialized: dbPath={}", dbPath);

        } catch (Exception e) {
            log.error("Failed to initialize SQLite LongTermStore", e);
            throw new RuntimeException("LongTermStore initialization failed", e);
        }
    }

    @Override
    public int level() { return 4; }

    @Override
    public String read() {
        try (Statement stmt = connection.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) as cnt FROM memories");
            if (rs.next()) {
                return String.format("=== 长程记忆 ===\n总记录数: %d 条\n", rs.getInt("cnt"));
            }
        } catch (SQLException e) {
            log.error("Failed to read LongTermStore stats", e);
        }
        return "=== 长程记忆 ===\n暂无数据";
    }

    @Override
    public void write(String content) {
        String[] segments = content.split("\n\n");
        String sql = "INSERT INTO memories (title, content, category) VALUES (?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (String segment : segments) {
                if (segment.trim().isEmpty()) continue;
                String title = segment.length() > 50 ? segment.substring(0, 50) + "..." : segment;
                ps.setString(1, title.trim());
                ps.setString(2, segment.trim());
                ps.setString(3, "general");
                ps.addBatch();
            }
            ps.executeBatch();
            log.info("LongTermStore wrote {} segments", segments.length);
        } catch (SQLException e) {
            log.error("Failed to write to LongTermStore", e);
        }
    }

    @Override
    public String search(String query) {
        if (query == null || query.trim().isEmpty()) return read();
        String sql = "SELECT m.title, SUBSTR(m.content, 1, 500) as snippet, m.category " +
                     "FROM memories_fts f JOIN memories m ON f.rowid = m.id " +
                     "WHERE memories_fts MATCH ? ORDER BY rank LIMIT 10";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            String ftsQuery = query.replaceAll("\\s+", " * ") + " *";
            ps.setString(1, ftsQuery);
            ResultSet rs = ps.executeQuery();
            StringBuilder result = new StringBuilder("=== FTS5全文检索结果 ===\n关键词: " + query + "\n\n");
            int count = 0;
            while (rs.next()) {
                count++;
                result.append("[").append(count).append("] ")
                      .append(rs.getString("title")).append("\n")
                      .append("   分类: ").append(rs.getString("category")).append("\n")
                      .append("   片段: ").append(rs.getString("snippet")).append("...\n\n");
            }
            result.append(count == 0 ? "未找到匹配结果。\n" : "共找到 " + count + " 条匹配记录\n");
            return result.toString();
        } catch (SQLException e) {
            log.error("FTS5 search failed: query={}", query, e);
            return "[搜索异常: " + e.getMessage() + "]";
        }
    }

    public void store(String title, String content, String category) {
        String sql = "INSERT INTO memories (title, content, category) VALUES (?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, title);
            ps.setString(2, content);
            ps.setString(3, category != null ? category : "general");
            ps.executeUpdate();
            log.info("Memory stored: title={}, category={}", title, category);
        } catch (SQLException e) {
            log.error("Failed to store memory", e);
        }
    }

    public void delete(long id) {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM memories WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to delete memory", e);
        }
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                log.info("LongTermStore connection closed");
            }
        } catch (SQLException e) {
            log.error("Failed to close LongTermStore", e);
        }
    }
}