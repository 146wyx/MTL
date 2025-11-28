package com.minecraftlauncher.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 缓存管理器
 * 用于管理内存和磁盘缓存，提高启动器性能
 */
public class CacheManager {

    private static final Logger LOGGER = LogManager.getLogger(CacheManager.class);
    private static final CacheManager INSTANCE = new CacheManager();
    
    private final Map<String, CacheEntry> memoryCache;
    private final String cacheDirectory;
    private final long defaultExpirationTimeMs;
    
    /**
     * 缓存条目类
     */
    private static class CacheEntry {
        private final Object data;
        private final long timestamp;
        private final long expirationTimeMs;
        
        public CacheEntry(Object data, long expirationTimeMs) {
            this.data = data;
            this.timestamp = System.currentTimeMillis();
            this.expirationTimeMs = expirationTimeMs;
        }
        
        public boolean isExpired() {
            return System.currentTimeMillis() - timestamp > expirationTimeMs;
        }
        
        public Object getData() {
            return data;
        }
    }
    
    private CacheManager() {
        this.memoryCache = new ConcurrentHashMap<>();
        this.cacheDirectory = System.getProperty("user.home") + "/.minecraftlauncher/cache";
        this.defaultExpirationTimeMs = TimeUnit.MINUTES.toMillis(30); // 默认过期时间30分钟
        
        // 确保缓存目录存在
        ensureCacheDirectoryExists();
        
        // 启动后台清理任务
        startCleanupTask();
    }
    
    /**
     * 获取单例实例
     */
    public static CacheManager getInstance() {
        return INSTANCE;
    }
    
    /**
     * 确保缓存目录存在
     */
    private void ensureCacheDirectoryExists() {
        try {
            Files.createDirectories(Paths.get(cacheDirectory));
        } catch (IOException e) {
            LOGGER.error("创建缓存目录失败", e);
        }
    }
    
    /**
     * 启动后台清理任务
     */
    private void startCleanupTask() {
        Thread cleanupThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    // 每小时清理一次过期缓存
                    TimeUnit.HOURS.sleep(1);
                    cleanupExpiredCache();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "CacheCleanupThread");
        
        cleanupThread.setDaemon(true);
        cleanupThread.start();
    }
    
    /**
     * 清理过期的缓存
     */
    public void cleanupExpiredCache() {
        int initialSize = memoryCache.size();
        memoryCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        int removedCount = initialSize - memoryCache.size();
        
        if (removedCount > 0) {
            LOGGER.info("清理了 {} 个过期的内存缓存项", removedCount);
        }
        
        // 清理磁盘缓存
        cleanupDiskCache();
    }
    
    /**
     * 清理磁盘缓存
     */
    private void cleanupDiskCache() {
        try {
            Path cachePath = Paths.get(cacheDirectory);
            if (!Files.exists(cachePath)) return;
            
            long threshold = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7); // 删除7天前的文件
            int removedCount = 0;
            
            try (var stream = Files.newDirectoryStream(cachePath)) {
                for (Path file : stream) {
                    if (Files.isRegularFile(file) && Files.getLastModifiedTime(file).toMillis() < threshold) {
                        Files.delete(file);
                        removedCount++;
                    }
                }
            }
            
            if (removedCount > 0) {
                LOGGER.info("清理了 {} 个过期的磁盘缓存文件", removedCount);
            }
        } catch (IOException e) {
            LOGGER.error("清理磁盘缓存失败", e);
        }
    }
    
    /**
     * 放入内存缓存
     * @param key 缓存键
     * @param data 缓存数据
     */
    public void put(String key, Object data) {
        put(key, data, defaultExpirationTimeMs);
    }
    
    /**
     * 放入内存缓存，指定过期时间
     * @param key 缓存键
     * @param data 缓存数据
     * @param expirationTimeMs 过期时间（毫秒）
     */
    public void put(String key, Object data, long expirationTimeMs) {
        if (key == null || data == null) {
            return;
        }
        
        memoryCache.put(key, new CacheEntry(data, expirationTimeMs));
        LOGGER.debug("放入内存缓存: {}", key);
    }
    
    /**
     * 放入内存缓存二进制数据，指定过期时间
     * @param key 缓存键
     * @param data 二进制缓存数据
     * @param expirationTimeMs 过期时间（毫秒）
     */
    public void putBinary(String key, byte[] data, long expirationTimeMs) {
        put(key, data, expirationTimeMs);
    }
    
    /**
     * 从内存缓存获取数据
     * @param key 缓存键
     * @return 缓存的数据，如果不存在或已过期则返回null
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        CacheEntry entry = memoryCache.get(key);
        
        if (entry == null || entry.isExpired()) {
            if (entry != null) {
                memoryCache.remove(key);
                LOGGER.debug("缓存已过期，移除: {}", key);
            }
            return null;
        }
        
        LOGGER.debug("从内存缓存获取: {}", key);
        return (T) entry.getData();
    }
    
    /**
     * 从内存缓存获取二进制数据
     * @param key 缓存键
     * @return 缓存的二进制数据，如果不存在或已过期则返回null
     */
    public byte[] getBinary(String key) {
        return get(key);
    }
    
    /**
     * 从内存缓存移除数据
     * @param key 缓存键
     */
    public void remove(String key) {
        memoryCache.remove(key);
        LOGGER.debug("从内存缓存移除: {}", key);
    }
    
    /**
     * 清空内存缓存
     */
    public void clearMemoryCache() {
        memoryCache.clear();
        LOGGER.info("内存缓存已清空");
    }
    
    /**
     * 保存数据到磁盘缓存
     * @param filename 文件名
     * @param data 要保存的数据（字节数组）
     */
    public void saveToDisk(String filename, byte[] data) {
        if (filename == null || data == null) {
            return;
        }
        
        Path filePath = Paths.get(cacheDirectory, filename);
        try {
            Files.write(filePath, data);
            LOGGER.debug("保存到磁盘缓存: {}", filename);
        } catch (IOException e) {
            LOGGER.error("保存到磁盘缓存失败: {}", filename, e);
        }
    }
    
    /**
     * 从磁盘缓存读取数据
     * @param filename 文件名
     * @return 文件内容，如果文件不存在或已过期则返回null
     */
    public byte[] loadFromDisk(String filename) {
        return loadFromDisk(filename, defaultExpirationTimeMs);
    }
    
    /**
     * 从磁盘缓存读取数据，指定过期时间
     * @param filename 文件名
     * @param expirationTimeMs 过期时间（毫秒）
     * @return 文件内容，如果文件不存在或已过期则返回null
     */
    public byte[] loadFromDisk(String filename, long expirationTimeMs) {
        if (filename == null) {
            return null;
        }
        
        Path filePath = Paths.get(cacheDirectory, filename);
        try {
            if (!Files.exists(filePath)) {
                return null;
            }
            
            // 检查文件是否过期
            long fileAge = System.currentTimeMillis() - Files.getLastModifiedTime(filePath).toMillis();
            if (fileAge > expirationTimeMs) {
                Files.delete(filePath); // 删除过期文件
                LOGGER.debug("磁盘缓存已过期，移除: {}", filename);
                return null;
            }
            
            LOGGER.debug("从磁盘缓存读取: {}", filename);
            return Files.readAllBytes(filePath);
        } catch (IOException e) {
            LOGGER.error("从磁盘缓存读取失败: {}", filename, e);
            return null;
        }
    }
    
    /**
     * 移除磁盘缓存文件
     * @param filename 文件名
     */
    public void removeFromDisk(String filename) {
        if (filename == null) {
            return;
        }
        
        Path filePath = Paths.get(cacheDirectory, filename);
        try {
            if (Files.exists(filePath)) {
                Files.delete(filePath);
                LOGGER.debug("从磁盘缓存移除: {}", filename);
            }
        } catch (IOException e) {
            LOGGER.error("从磁盘缓存移除失败: {}", filename, e);
        }
    }
    
    /**
     * 清空所有缓存
     */
    public void clearAll() {
        clearMemoryCache();
        clearDiskCache();
    }
    
    /**
     * 清空磁盘缓存
     */
    private void clearDiskCache() {
        try {
            Path cachePath = Paths.get(cacheDirectory);
            if (!Files.exists(cachePath)) return;
            
            int removedCount = 0;
            try (var stream = Files.newDirectoryStream(cachePath)) {
                for (Path file : stream) {
                    if (Files.isRegularFile(file)) {
                        Files.delete(file);
                        removedCount++;
                    }
                }
            }
            
            LOGGER.info("磁盘缓存已清空，移除了 {} 个文件", removedCount);
        } catch (IOException e) {
            LOGGER.error("清空磁盘缓存失败", e);
        }
    }
    
    /**
     * 获取内存缓存大小
     */
    public int getMemoryCacheSize() {
        return memoryCache.size();
    }
    
    /**
     * 获取缓存目录大小（字节）
     */
    public long getCacheDirectorySize() {
        try {
            Path cachePath = Paths.get(cacheDirectory);
            if (!Files.exists(cachePath)) return 0;
            
            long size = 0;
            try (var stream = Files.walk(cachePath)) {
                size = stream
                    .filter(Files::isRegularFile)
                    .mapToLong(p -> {
                        try {
                            return Files.size(p);
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .sum();
            }
            
            return size;
        } catch (IOException e) {
            LOGGER.error("计算缓存目录大小失败", e);
            return 0;
        }
    }
}