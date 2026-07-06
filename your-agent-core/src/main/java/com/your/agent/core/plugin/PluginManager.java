package com.your.agent.core.plugin;

import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 插件系统 —— 参考 OpenClaw plugin 架构。
 * 支持通过 SPI 机制加载第三方扩展。
 */
@Slf4j
public class PluginManager {

    private final Map<String, Plugin> plugins = new ConcurrentHashMap<>();
    private final List<Plugin> loadedPlugins = new ArrayList<>();

    public PluginManager() {
        log.info("PluginManager initialized");
    }

    public void register(Plugin plugin) {
        plugins.put(plugin.name(), plugin);
        loadedPlugins.add(plugin);
        log.info("Plugin registered: {} v{} - {}", plugin.name(), plugin.version(), plugin.description());
    }

    public Plugin getPlugin(String name) { return plugins.get(name); }
    public List<Plugin> listPlugins() { return new ArrayList<>(loadedPlugins); }

    public interface Plugin {
        String name();
        String version();
        String description();
        void onLoad();
        void onUnload();
    }

    public abstract static class BasePlugin implements Plugin {
        private boolean loaded = false;
        public boolean isLoaded() { return loaded; }
        public void setLoaded(boolean loaded) { this.loaded = loaded; }
    }
}