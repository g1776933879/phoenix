package com.your.agent.spring.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.your.agent.core.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.stream.Stream;

@Slf4j
@Component
public class FileTool {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String BASE_PATH = System.getProperty("user.dir") + "/sandbox_files";

    @Tool(name = "read_file", description = "读取指定文件内容", parametersSchema = "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"}},\"required\":[\"path\"]}")
    public String readFile(String argsJson) {
        try { var node = MAPPER.readTree(argsJson); String relativePath = node.get("path").asText(); Path fullPath = resolvePath(relativePath); String content = Files.readString(fullPath, StandardCharsets.UTF_8); return String.format("File: %s\nSize: %d bytes\n\n%s", relativePath, content.length(), content); }
        catch (Exception e) { return "[Error] " + e.getMessage(); }
    }
    @Tool(name = "write_file", description = "写入文件", parametersSchema = "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"},\"content\":{\"type\":\"string\"}},\"required\":[\"path\",\"content\"]}", requireApproval = true)
    public String writeFile(String argsJson) {
        try { var node = MAPPER.readTree(argsJson); String relativePath = node.get("path").asText(); String content = node.get("content").asText(); Path fullPath = resolvePath(relativePath); Files.createDirectories(fullPath.getParent()); Files.writeString(fullPath, content, StandardCharsets.UTF_8); return "OK: " + relativePath; }
        catch (Exception e) { return "[Error] " + e.getMessage(); }
    }
    @Tool(name = "list_files", description = "列出目录文件", parametersSchema = "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"},\"recursive\":{\"type\":\"boolean\"}},\"required\":[\"path\"]}")
    public String listFiles(String argsJson) {
        try { var node = MAPPER.readTree(argsJson); String relativePath = node.get("path").asText(); boolean recursive = node.has("recursive") && node.get("recursive").asBoolean(false); Path fullPath = resolvePath(relativePath); StringBuilder result = new StringBuilder();
            if (recursive) { Files.walkFileTree(fullPath, new SimpleFileVisitor<>() { public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) { result.append(file.getFileName()).append(" (").append(attrs.size()).append(" bytes)\n"); return FileVisitResult.CONTINUE; } }); }
            else { try (Stream<Path> stream = Files.list(fullPath)) { stream.sorted().forEach(p -> { try { result.append(p.getFileName()).append(" (").append(Files.size(p)).append(" bytes)\n"); } catch (IOException e) {} }); } }
            return result.toString();
        } catch (Exception e) { return "[Error] " + e.getMessage(); }
    }
    @Tool(name = "delete_file", description = "删除文件", parametersSchema = "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"}},\"required\":[\"path\"]}", requireApproval = true)
    public String deleteFile(String argsJson) {
        try { var node = MAPPER.readTree(argsJson); Path fullPath = resolvePath(node.get("path").asText()); Files.delete(fullPath); return "Deleted"; }
        catch (Exception e) { return "[Error] " + e.getMessage(); }
    }
    private Path resolvePath(String relativePath) throws Exception {
        Path baseDir = Paths.get(BASE_PATH).toAbsolutePath().normalize(); Files.createDirectories(baseDir);
        Path resolved = baseDir.resolve(relativePath).normalize();
        if (!resolved.startsWith(baseDir)) throw new SecurityException("Path traversal detected");
        return resolved;
    }
}
