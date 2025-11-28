package com.minecraftlauncher.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minecraftlauncher.api.models.VersionInfo;
import com.minecraftlauncher.api.models.VersionManifest;
import com.minecraftlauncher.api.models.VersionManifest.Version;
import com.minecraftlauncher.utils.CacheManager;
import com.minecraftlauncher.utils.PerformanceMonitor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Minecraft API 管理器
 * 负责管理与Minecraft API的交互，提供版本信息的获取和缓存功能
 */
public class MinecraftAPIManager {
    private static final Logger LOGGER = LogManager.getLogger(MinecraftAPIManager.class);
    private final ObjectMapper objectMapper;
    private final MinecraftAPIClient apiClient;
    private final ConcurrentHashMap<String, VersionInfo> versionInfoCache;
    private VersionManifest cachedManifest;
    private long lastManifestUpdateTime;
    private final CacheManager cacheManager;
    private final PerformanceMonitor performanceMonitor;
    
    public MinecraftAPIManager() {
        this.apiClient = new MinecraftAPIClient();
        this.objectMapper = new ObjectMapper();
        this.versionInfoCache = new ConcurrentHashMap<>();
        this.cachedManifest = null;
        this.lastManifestUpdateTime = 0;
        this.cacheManager = CacheManager.getInstance();
        this.performanceMonitor = PerformanceMonitor.getInstance();
    }
    
    /**
     * 获取Minecraft版本清单
     * @return 版本清单对象
     * @throws IOException 当API请求失败时抛出
     */
    public VersionManifest getVersionManifest() throws IOException {
        performanceMonitor.start("getVersionManifest");
        
        try {
            // 检查是否需要更新版本清单（如果超过30分钟或不存在）
            boolean needsUpdate = cachedManifest == null || 
                    (System.currentTimeMillis() - lastManifestUpdateTime > 1800000); // 30分钟过期
            
            if (needsUpdate) {
                LOGGER.info("更新版本清单");
                cachedManifest = apiClient.getVersionManifest();
                lastManifestUpdateTime = System.currentTimeMillis();
            }
            
            return cachedManifest;
        } finally {
            performanceMonitor.endAndLogIfSlow("getVersionManifest", 1000);
        }
    }
    
    /**
     * 刷新版本清单缓存
     * @return 最新的版本清单
     * @throws IOException 当API请求失败时抛出
     */
    public VersionManifest refreshVersionManifest() throws IOException {
        performanceMonitor.start("refreshVersionManifest");
        
        try {
            LOGGER.info("强制刷新版本清单");
            cachedManifest = apiClient.getVersionManifest();
            lastManifestUpdateTime = System.currentTimeMillis();
            return cachedManifest;
        } finally {
            performanceMonitor.endAndLogIfSlow("refreshVersionManifest", 2000);
        }
    }
    
    /**
     * 获取指定版本的详细信息
     * @param versionId 版本ID
     * @return 版本详细信息
     * @throws IOException 当API请求失败时抛出
     */
    public VersionInfo getVersionInfo(String versionId) throws IOException {
        performanceMonitor.start("getVersionInfo_" + versionId);
        
        try {
            // 检查内存缓存中是否已有版本信息
            if (versionInfoCache.containsKey(versionId)) {
                LOGGER.debug("从内存缓存获取版本信息: {}", versionId);
                return versionInfoCache.get(versionId);
            }
            
            // 检查磁盘缓存
            String cacheKey = "version_info_" + versionId;
            byte[] cachedData = cacheManager.loadFromDisk(cacheKey + ".json", TimeUnit.HOURS.toMillis(24));
            if (cachedData != null) {
                String json = new String(cachedData);
                VersionInfo versionInfo = objectMapper.readValue(json, VersionInfo.class);
                // 同时缓存到内存
                versionInfoCache.put(versionId, versionInfo);
                LOGGER.debug("从磁盘缓存获取版本信息: {}", versionId);
                return versionInfo;
            }
            
            // 获取版本清单
            VersionManifest manifest = getVersionManifest();
            
            // 使用优化的版本查找方法
            Version version = findVersionById(manifest, versionId);
            
            if (version == null) {
                throw new IOException("找不到版本: " + versionId);
            }
            
            // 获取版本详细信息
            LOGGER.info("获取版本详细信息: {}", versionId);
            String versionJson = apiClient.getContent(version.getUrl());
            VersionInfo versionInfo = objectMapper.readValue(versionJson, VersionInfo.class);
            
            // 缓存版本信息到内存
            versionInfoCache.put(versionId, versionInfo);
            
            // 缓存版本信息到磁盘
            cacheManager.saveToDisk(cacheKey + ".json", versionJson.getBytes());
            
            return versionInfo;
        } finally {
            performanceMonitor.endAndLogIfSlow("getVersionInfo_" + versionId, 3000);
        }
    }
    
    /**
     * 优化的版本查找方法
     */
    private Version findVersionById(VersionManifest manifest, String versionId) {
        // 使用流式API进行优化查找
        return manifest.getVersions().stream()
                .filter(v -> v.getId().equals(versionId))
                .findFirst()
                .orElse(null);
    }
    
    /**
     * 获取最新的发布版本信息
     * @return 最新发布版本的详细信息
     * @throws IOException 当API请求失败时抛出
     */
    public VersionInfo getLatestReleaseVersionInfo() throws IOException {
        performanceMonitor.start("getLatestReleaseVersionInfo");
        
        try {
            VersionManifest manifest = getVersionManifest();
            String latestReleaseId = manifest.getLatest().getRelease();
            return getVersionInfo(latestReleaseId);
        } finally {
            performanceMonitor.endAndLogIfSlow("getLatestReleaseVersionInfo", 1000);
        }
    }
    
    /**
     * 获取最新的快照版本信息
     * @return 最新快照版本的详细信息
     * @throws IOException 当API请求失败时抛出
     */
    public VersionInfo getLatestSnapshotVersionInfo() throws IOException {
        performanceMonitor.start("getLatestSnapshotVersionInfo");
        
        try {
            VersionManifest manifest = getVersionManifest();
            String latestSnapshotId = manifest.getLatest().getSnapshot();
            return getVersionInfo(latestSnapshotId);
        } finally {
            performanceMonitor.endAndLogIfSlow("getLatestSnapshotVersionInfo", 1000);
        }
    }
    
    /**
     * 获取最新的发布版本
     * @return 最新发布版本
     * @throws IOException 如果发生IO异常
     */
    public Version getLatestReleaseVersion() throws IOException {
        performanceMonitor.start("getLatestReleaseVersion");
        
        try {
            VersionManifest manifest = getVersionManifest();
            String releaseId = manifest.getLatest().getRelease();
            
            // 查找发布版本（使用优化的查找方法）
            Version version = findVersionById(manifest, releaseId);
            if (version == null) {
                throw new IOException("未找到最新发布版本: " + releaseId);
            }
            
            return version;
        } finally {
            performanceMonitor.endAndLogIfSlow("getLatestReleaseVersion", 500);
        }
    }
    
    /**
     * 获取最新的快照版本
     * @return 最新快照版本
     * @throws IOException 如果发生IO异常
     */
    public Version getLatestSnapshotVersion() throws IOException {
        performanceMonitor.start("getLatestSnapshotVersion");
        
        try {
            VersionManifest manifest = getVersionManifest();
            String snapshotId = manifest.getLatest().getSnapshot();
            
            // 查找快照版本（使用优化的查找方法）
            Version version = findVersionById(manifest, snapshotId);
            if (version == null) {
                throw new IOException("未找到最新快照版本: " + snapshotId);
            }
            
            return version;
        } finally {
            performanceMonitor.endAndLogIfSlow("getLatestSnapshotVersion", 500);
        }
    }
    
    /**
     * 获取所有发布版本
     * @return 发布版本列表
     * @throws IOException 如果发生IO异常
     */
    public List<Version> getReleaseVersions() throws IOException {
        performanceMonitor.start("getReleaseVersions");
        
        try {
            VersionManifest manifest = getVersionManifest();
            
            // 使用流式API进行优化筛选
            return manifest.getVersions().stream()
                    .filter(version -> version.getType().equals("release"))
                    .collect(Collectors.toList());
        } finally {
            performanceMonitor.endAndLogIfSlow("getReleaseVersions", 1000);
        }
    }
    
    /**
     * 获取所有快照版本
     * @return 快照版本列表
     * @throws IOException 如果发生IO异常
     */
    public List<Version> getSnapshotVersions() throws IOException {
        performanceMonitor.start("getSnapshotVersions");
        
        try {
            VersionManifest manifest = getVersionManifest();
            
            // 使用流式API进行优化筛选
            return manifest.getVersions().stream()
                    .filter(version -> version.getType().equals("snapshot"))
                    .collect(Collectors.toList());
        } finally {
            performanceMonitor.endAndLogIfSlow("getSnapshotVersions", 1000);
        }
    }
    
    /**
     * 清除版本信息缓存
     */
    public void clearVersionInfoCache() {
        versionInfoCache.clear();
        LOGGER.info("版本信息缓存已清除");
    }
    
    /**
     * 获取缓存的版本数量
     */
    public int getCachedVersionCount() {
        return versionInfoCache.size();
    }
    
    /**
     * 预加载常用版本信息
     */
    public void preloadCommonVersions() {
        performanceMonitor.start("preloadCommonVersions");
        
        try {
            LOGGER.info("预加载常用版本信息...");
            
            // 异步预加载最新的发布版和快照版
            new Thread(() -> {
                try {
                    Version releaseVersion = getLatestReleaseVersion();
                    if (releaseVersion != null) {
                        getVersionInfo(releaseVersion.getId());
                    }
                } catch (Exception e) {
                    LOGGER.warn("预加载最新发布版失败", e);
                }
            }).start();
            
            new Thread(() -> {
                try {
                    Version snapshotVersion = getLatestSnapshotVersion();
                    if (snapshotVersion != null) {
                        getVersionInfo(snapshotVersion.getId());
                    }
                } catch (Exception e) {
                    LOGGER.warn("预加载最新快照版失败", e);
                }
            }).start();
            
            LOGGER.info("预加载任务已启动");
        } finally {
            performanceMonitor.endAndLog("preloadCommonVersions");
        }
    }
}