package com.your.agent.core.memory;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CoreMemory implements MemoryLayer {

    private String content;

    public CoreMemory(String initialContent) {
        this.content = initialContent != null ? initialContent : "";
        log.info("CoreMemory (L1) initialized, length={} chars", this.content.length());
    }

    public CoreMemory() {
        this("\u4f60\u662f\u6c38\u52a8\u673aAI Agent\uff0c\u4e00\u4e2a\u4f01\u4e1a\u7ea7\u667a\u80fd\u52a9\u624b\u3002" +
                "\u4f60\u7684\u6838\u5fc3\u80fd\u529b\uff1a\u901a\u8fc7ReAct\u5faa\u73af\u8c03\u7528\u5de5\u5177\u89e3\u51b3\u7528\u6237\u95ee\u9898\u3002" +
                "\u4f60\u59cb\u7ec8\u4fdd\u6301\u4e13\u4e1a\u3001\u9ad8\u6548\u3001\u5b89\u5168\u3002");
    }

    @Override public int level() { return 1; }
    @Override public String read() { return content; }
    @Override public void write(String content) { this.content = content; }
    @Override public String search(String query) { return content; }
}
