package com.minecraftlauncher.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * 配置管理器
 * 负责加载、保存和管理启动器配置
 */
public class ConfigManager {

    private static final Logger LOGGER = LogManager.getLogger(ConfigManager.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILE = "launcher_config.json";
    
    private final String configDirectory;
    private final Map<String, Object> configMap;

    /**
     * 构造函数
     * @param configDirectory 配置文件目录
     */
    public ConfigManager(String configDirectory) {
        this.configDirectory = configDirectory;
        this.configMap = new HashMap<>();
        
        // 确保配置目录存在
        ensureConfigDirectoryExists();
        
        // 加载配置
        loadConfig();
    }

    /**
     * 确保配置目录存在
     */
    private void ensureConfigDirectoryExists() {
        try {
            Files.createDirectories(Paths.get(configDirectory));
        } catch (IOException e) {
            LOGGER.error("创建配置目录失败", e);
        }
    }

    /**
     * 加载配置文件
     */
    public void loadConfig() {
        File configFile = new File(configDirectory, CONFIG_FILE);
        
        if (!configFile.exists()) {
            LOGGER.info("配置文件不存在，使用默认配置");
            loadDefaultConfig();
            saveConfig();
            return;
        }
        
        try (FileReader reader = new FileReader(configFile)) {
            Map<String, Object> loadedConfig = GSON.fromJson(reader, Map.class);
            if (loadedConfig != null) {
                configMap.clear();
                configMap.putAll(loadedConfig);
                LOGGER.info("配置文件加载成功");
            } else {
                LOGGER.warn("配置文件内容为空，使用默认配置");
                loadDefaultConfig();
            }
        } catch (Exception e) {
            LOGGER.error("加载配置文件失败", e);
            loadDefaultConfig();
        }
    }

    /**
     * 保存配置文件
     */
    public void saveConfig() {
        File configFile = new File(configDirectory, CONFIG_FILE);
        
        try (FileWriter writer = new FileWriter(configFile)) {
            GSON.toJson(configMap, writer);
            LOGGER.info("配置文件保存成功");
        } catch (IOException e) {
            LOGGER.error("保存配置文件失败", e);
        }
    }

    /**
     * 加载默认配置
     */
    private void loadDefaultConfig() {
        configMap.clear();
        
        // 默认游戏目录
        String defaultGameDir = new File(configDirectory, "minecraft").getAbsolutePath();
        configMap.put("gameDirectory", defaultGameDir);
        
        // 内存设置
        configMap.put("maxMemoryMB", 2048); // 默认2GB
        configMap.put("minMemoryMB", 512);  // 默认512MB
        
        // Java路径（默认自动查找）
        configMap.put("javaPath", "");
        
        // 界面设置
        configMap.put("width", 800);
        configMap.put("height", 600);
        configMap.put("maximized", false);
        
        // 游戏设置
        configMap.put("fullscreen", false);
        configMap.put("lastVersion", "");
        configMap.put("enableMods", false);
        
        // 账户设置
        configMap.put("rememberMe", false);
        configMap.put("savedUsername", "");
        
        LOGGER.info("默认配置已加载");
    }

    /**
     * 获取字符串配置
     */
    public String getString(String key, String defaultValue) {
        Object value = configMap.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    /**
     * 设置字符串配置
     */
    public void setString(String key, String value) {
        configMap.put(key, value);
    }

    /**
     * 获取整数配置
     */
    public int getInt(String key, int defaultValue) {
        Object value = configMap.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        } else if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                LOGGER.warn("配置项 {} 不是有效的整数", key);
            }
        }
        return defaultValue;
    }

    /**
     * 设置整数配置
     */
    public void setInt(String key, int value) {
        configMap.put(key, value);
    }

    /**
     * 获取布尔配置
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        Object value = configMap.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        } else if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return defaultValue;
    }

    /**
     * 设置布尔配置
     */
    public void setBoolean(String key, boolean value) {
        configMap.put(key, value);
    }

    /**
     * 获取游戏目录
     */
    public String getGameDirectory() {
        return getString("gameDirectory", new File(configDirectory, "minecraft").getAbsolutePath());
    }

    /**
     * 设置游戏目录
     */
    public void setGameDirectory(String directory) {
        setString("gameDirectory", directory);
    }

    /**
     * 获取最大内存（MB）
     */
    public int getMaxMemoryMB() {
        return getInt("maxMemoryMB", 2048);
    }

    /**
     * 设置最大内存（MB）
     */
    public void setMaxMemoryMB(int memory) {
        setInt("maxMemoryMB", memory);
    }

    /**
     * 获取Java路径
     */
    public String getJavaPath() {
        String path = getString("javaPath", "");
        if (path.isEmpty()) {
            // 尝试自动查找Java路径
            return findJavaPath();
        }
        return path;
    }

    /**
     * 设置Java路径
     */
    public void setJavaPath(String path) {
        setString("javaPath", path);
    }

    /**
     * 自动查找Java路径
     */
    private String findJavaPath() {
        String javaHome = System.getProperty("java.home");
        File javaExe = new File(javaHome, "bin/java" + (System.getProperty("os.name").contains("win") ? ".exe" : ""));
        
        if (javaExe.exists()) {
            return javaExe.getAbsolutePath();
        }
        
        return "java"; // 默认使用系统PATH中的java
    }
}