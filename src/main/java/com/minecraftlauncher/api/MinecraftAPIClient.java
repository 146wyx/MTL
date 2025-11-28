package com.minecraftlauncher.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minecraftlauncher.api.models.VersionManifest;
import com.minecraftlauncher.utils.CacheManager;
import com.minecraftlauncher.utils.PerformanceMonitor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * Minecraft官方API通信客户端
 * 负责与Minecraft官方API进行所有通信
 */
public class MinecraftAPIClient {

    private static final Logger LOGGER = LogManager.getLogger(MinecraftAPIClient.class);
    private static final String VERSION_MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    
    private final OkHttpClient client;
    private final ObjectMapper objectMapper;
    private final CacheManager cacheManager;
    private final PerformanceMonitor performanceMonitor;

    public MinecraftAPIClient() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)  // 增加超时时间
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .connectionPool(new okhttp3.ConnectionPool(5, 5, TimeUnit.MINUTES)) // 连接池优化
                .build();
        
        this.objectMapper = new ObjectMapper();
        this.cacheManager = CacheManager.getInstance();
        this.performanceMonitor = PerformanceMonitor.getInstance();
    }

    /**
     * 获取Minecraft版本清单
     * @return 版本清单对象
     * @throws IOException 当API请求失败时抛出
     */
    public VersionManifest getVersionManifest() throws IOException {
        final String cacheKey = "version_manifest";
        performanceMonitor.start("getVersionManifest");
        
        try {
            // 尝试从缓存获取
            String cachedManifest = cacheManager.get(cacheKey);
            if (cachedManifest != null) {
                LOGGER.info("从缓存获取Minecraft版本清单");
                return objectMapper.readValue(cachedManifest, VersionManifest.class);
            }
            
            LOGGER.info("获取Minecraft版本清单...");
            String manifestContent = fetchUrl(VERSION_MANIFEST_URL);
            
            // 缓存版本清单，10分钟过期
            cacheManager.put(cacheKey, manifestContent, TimeUnit.MINUTES.toMillis(10));
            
            // 同时缓存到磁盘，24小时过期
            String cacheFilename = "version_manifest.json";
            cacheManager.saveToDisk(cacheFilename, manifestContent.getBytes());
            
            LOGGER.info("成功获取版本清单");
            return objectMapper.readValue(manifestContent, VersionManifest.class);
        } catch (IOException e) {
            LOGGER.error("获取版本清单时发生异常", e);
            throw e;
        } finally {
            performanceMonitor.endAndLogIfSlow("getVersionManifest", 2000); // 2秒阈值
        }
    }

    /**
     * 获取指定URL的内容
     * @param url 要获取内容的URL
     * @return URL的内容字符串
     * @throws IOException 当请求失败时抛出
     */
    public String getContent(String url) throws IOException {
        performanceMonitor.start("getContent_" + truncateUrl(url, 50));
        
        try {
            return fetchUrl(url);
        } finally {
            performanceMonitor.endAndLogIfSlow("getContent_" + truncateUrl(url, 50), 3000); // 3秒阈值
        }
    }
    
    /**
     * 获取指定URL的内容（内部方法）
     * @param url 要获取内容的URL
     * @return URL内容
     * @throws IOException 如果发生IO异常
     */
    private String fetchUrl(String url) throws IOException {
        String cacheKey = generateCacheKey(url);
        
        // 尝试从缓存获取
        String cachedContent = cacheManager.get(cacheKey);
        if (cachedContent != null) {
            LOGGER.debug("从缓存获取URL内容: {}", truncateUrl(url, 50));
            return cachedContent;
        }
        
        // 尝试从磁盘缓存获取
        String diskCacheFilename = generateFilenameFromUrl(url);
        byte[] diskContent = cacheManager.loadFromDisk(diskCacheFilename, TimeUnit.HOURS.toMillis(1));
        if (diskContent != null) {
            String content = new String(diskContent);
            // 同时缓存到内存
            cacheManager.put(cacheKey, content, TimeUnit.MINUTES.toMillis(30));
            LOGGER.debug("从磁盘缓存获取URL内容: {}", truncateUrl(url, 50));
            return content;
        }
        
        LOGGER.info("获取URL内容: {}", truncateUrl(url, 50));
        Request request = new Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", "MinecraftLauncher/1.0")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                LOGGER.error("获取URL内容失败: {} - URL: {}", response, truncateUrl(url, 100));
                throw new IOException("Unexpected response: " + response);
            }
            
            String content = response.body().string();
            
            // 缓存到内存和磁盘
            cacheManager.put(cacheKey, content, TimeUnit.MINUTES.toMillis(30));
            cacheManager.saveToDisk(diskCacheFilename, content.getBytes());
            
            return content;
        } catch (SocketTimeoutException e) {
            LOGGER.error("请求超时: {}", truncateUrl(url, 100));
            throw new IOException("请求超时: " + truncateUrl(url, 100), e);
        }
    }
    
    /**
     * 获取指定URL的二进制内容
     * @param url 要获取内容的URL
     * @return 二进制内容字节数组
     * @throws IOException 如果发生IO异常
     */
    public byte[] fetchBinaryUrl(String url) throws IOException {
        String cacheKey = generateCacheKey(url) + "_binary";
        performanceMonitor.start("fetchBinaryUrl_" + truncateUrl(url, 50));
        
        try {
            // 尝试从缓存获取
            byte[] cachedContent = cacheManager.getBinary(cacheKey);
            if (cachedContent != null) {
                LOGGER.debug("从缓存获取二进制URL内容: {}", truncateUrl(url, 50));
                return cachedContent;
            }
            
            // 尝试从磁盘缓存获取
            String diskCacheFilename = generateFilenameFromUrl(url) + ".bin";
            byte[] diskContent = cacheManager.loadFromDisk(diskCacheFilename, TimeUnit.HOURS.toMillis(24));
            if (diskContent != null) {
                // 同时缓存到内存
                cacheManager.putBinary(cacheKey, diskContent, TimeUnit.MINUTES.toMillis(30));
                LOGGER.debug("从磁盘缓存获取二进制URL内容: {}", truncateUrl(url, 50));
                return diskContent;
            }
            
            LOGGER.info("获取二进制URL内容: {}", truncateUrl(url, 50));
            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "MinecraftLauncher/1.0")
                    .build();
            
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    LOGGER.error("获取二进制URL内容失败: {} - URL: {}", response, truncateUrl(url, 100));
                    throw new IOException("Unexpected response: " + response);
                }
                
                byte[] content = response.body().bytes();
                
                // 缓存到内存和磁盘
                cacheManager.putBinary(cacheKey, content, TimeUnit.MINUTES.toMillis(30));
                cacheManager.saveToDisk(diskCacheFilename, content);
                
                return content;
            }
        } catch (SocketTimeoutException e) {
            LOGGER.error("请求超时: {}", truncateUrl(url, 100));
            throw new IOException("请求超时: " + truncateUrl(url, 100), e);
        } finally {
            performanceMonitor.endAndLogIfSlow("fetchBinaryUrl_" + truncateUrl(url, 50), 5000); // 5秒阈值
        }
    }
    
    /**
     * 生成缓存键
     */
    private String generateCacheKey(String url) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(url.getBytes());
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            return "url_" + url.hashCode();
        }
    }
    
    /**
     * 从URL生成文件名
     */
    private String generateFilenameFromUrl(String url) {
        // 替换URL中的特殊字符，生成安全的文件名
        String safeFilename = url.replaceAll("[^a-zA-Z0-9.-]", "_");
        // 限制文件名长度
        if (safeFilename.length() > 100) {
            safeFilename = safeFilename.substring(0, 100);
        }
        return safeFilename;
    }
    
    /**
     * 截断URL以用于日志显示
     */
    private String truncateUrl(String url, int maxLength) {
        if (url.length() <= maxLength) {
            return url;
        }
        return url.substring(0, maxLength) + "...";
    }
}