package com.minecraftlauncher.game;

import com.minecraftlauncher.api.MinecraftAPIManager;
import com.minecraftlauncher.api.models.VersionInfo;
import com.minecraftlauncher.utils.CacheManager;
import com.minecraftlauncher.utils.PerformanceMonitor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 游戏管理器
 * 负责Minecraft游戏的下载、更新和启动
 */
public class GameManager {

    private static final Logger LOGGER = LogManager.getLogger(GameManager.class);
    private final MinecraftAPIManager apiManager;
    private final GameDownloader downloader;
    private final GameLauncher launcher;
    private final String gameDirectory;
    private final CacheManager cacheManager;
    private final PerformanceMonitor performanceMonitor;
    private final ConcurrentHashMap<String, AtomicBoolean> downloadTasks;
    private final ConcurrentHashMap<String, VersionInfo> versionInfoCache;

    /**
     * 构造函数
     * @param gameDirectory 游戏安装目录
     */
    public GameManager(String gameDirectory) {
        this.gameDirectory = gameDirectory;
        this.apiManager = new MinecraftAPIManager();
        this.downloader = new GameDownloader(gameDirectory);
        this.launcher = new GameLauncher(gameDirectory);
        this.cacheManager = CacheManager.getInstance();
        this.performanceMonitor = PerformanceMonitor.getInstance();
        this.downloadTasks = new ConcurrentHashMap<>();
        this.versionInfoCache = new ConcurrentHashMap<>();
        
        // 确保游戏目录存在
        ensureDirectoriesExist();
        
        // 预加载常用版本信息
        CompletableFuture.runAsync(() -> {
            try {
                apiManager.preloadCommonVersions();
            } catch (Exception e) {
                LOGGER.warn("预加载版本信息失败", e);
            }
        });
    }

    /**
     * 确保游戏相关目录存在
     */
    private void ensureDirectoriesExist() {
        performanceMonitor.start("ensureDirectoriesExist");
        
        try {
            // 并行创建目录以提高性能
            List<Path> pathsToCreate = List.of(
                Paths.get(gameDirectory),
                Paths.get(gameDirectory).resolve("versions"),
                Paths.get(gameDirectory).resolve("libraries"),
                Paths.get(gameDirectory).resolve("assets"),
                Paths.get(gameDirectory).resolve("logs")
            );
            
            pathsToCreate.parallelStream().forEach(path -> {
                try {
                    if (!Files.exists(path)) {
                        Files.createDirectories(path);
                        LOGGER.debug("创建目录: {}", path);
                    }
                } catch (IOException e) {
                    LOGGER.error("创建目录失败: {}", path, e);
                }
            });
            
            LOGGER.info("游戏目录结构已创建: {}", gameDirectory);
        } finally {
            performanceMonitor.endAndLogIfSlow("ensureDirectoriesExist", 1000);
        }
    }

    /**
     * 检查版本是否已安装
     * @param versionId 版本ID
     * @return 是否已安装
     */
    public boolean isVersionInstalled(String versionId) {
        performanceMonitor.start("isVersionInstalled_" + versionId);
        
        try {
            Path versionDir = Paths.get(gameDirectory, "versions", versionId);
            boolean result = Files.exists(versionDir.resolve(versionId + ".jar")) && 
                            Files.exists(versionDir.resolve(versionId + ".json"));
            LOGGER.debug("版本 {} 安装状态: {}", versionId, result);
            return result;
        } finally {
            performanceMonitor.endAndLog("isVersionInstalled_" + versionId);
        }
    }

    /**
     * 获取已安装的游戏版本列表
     * @return 已安装版本列表
     */
    public List<String> getInstalledVersions() {
        performanceMonitor.start("getInstalledVersions");
        
        try {
            Path versionsDir = Paths.get(gameDirectory, "versions");
            
            if (!Files.exists(versionsDir)) {
                return List.of();
            }
            
            return Files.list(versionsDir)
                .filter(Files::isDirectory)
                .map(dir -> dir.getFileName().toString())
                .filter(this::isVersionInstalled)
                .sorted((a, b) -> b.compareTo(a)) // 降序排序，最新版本在前
                .collect(Collectors.toList());
        } catch (IOException e) {
            LOGGER.error("读取已安装版本失败", e);
            return List.of();
        } finally {
            performanceMonitor.endAndLogIfSlow("getInstalledVersions", 2000);
        }
    }

    /**
     * 下载指定版本
     * @param versionId 版本ID
     * @param progressCallback 下载进度回调
     * @return 异步下载任务
     */
    public CompletableFuture<Boolean> downloadVersion(String versionId, DownloadProgressCallback progressCallback) {
        // 检查是否已有相同版本的下载任务在运行
        AtomicBoolean isRunning = downloadTasks.computeIfAbsent(versionId, k -> new AtomicBoolean(false));
        if (!isRunning.compareAndSet(false, true)) {
            LOGGER.warn("版本 {} 的下载任务已经在进行中", versionId);
            return CompletableFuture.completedFuture(false);
        }
        
        return CompletableFuture.supplyAsync(() -> {
            performanceMonitor.start("downloadVersion_" + versionId);
            
            try {
                LOGGER.info("开始下载版本: {}", versionId);
                
                // 获取版本信息（优先从缓存）
                VersionInfo versionInfo = getVersionInfoFromCache(versionId);
                if (versionInfo == null) {
                    LOGGER.error("获取版本信息失败: {}", versionId);
                    progressCallback.onError("获取版本信息失败");
                    return false;
                }
                
                // 下载游戏JAR
                progressCallback.onProgress(0.1, "正在下载游戏文件...");
                boolean jarDownloaded = downloader.downloadGameJar(versionInfo);
                if (!jarDownloaded) {
                    LOGGER.error("下载游戏JAR失败");
                    return false;
                }
                
                // 并行下载库文件和资源文件以提高性能
                CompletableFuture<Boolean> librariesFuture = CompletableFuture.supplyAsync(() -> {
                    try {
                        progressCallback.onProgress(0.3, "正在下载库文件...");
                        return downloader.downloadLibraries(versionInfo);
                    } catch (Exception e) {
                        LOGGER.error("下载库文件时发生错误", e);
                        return false;
                    }
                });
                
                CompletableFuture<Boolean> assetsFuture = CompletableFuture.supplyAsync(() -> {
                    try {
                        progressCallback.onProgress(0.6, "正在下载资源文件...");
                        return downloader.downloadAssets(versionInfo);
                    } catch (Exception e) {
                        LOGGER.error("下载资源文件时发生错误", e);
                        return false;
                    }
                });
                
                // 等待并检查结果
                boolean librariesSuccess = librariesFuture.get(30, TimeUnit.MINUTES);
                boolean assetsSuccess = assetsFuture.get(30, TimeUnit.MINUTES);
                
                if (!librariesSuccess || !assetsSuccess) {
                    LOGGER.error("下载失败: 库文件状态={}, 资源文件状态={}", librariesSuccess, assetsSuccess);
                    return false;
                }
                
                // 更新进度到完成
                progressCallback.onProgress(1.0, "下载完成！");
                LOGGER.info("版本下载完成: {}", versionId);
                
                // 缓存安装的版本信息
                versionInfoCache.put(versionId, versionInfo);
                cacheInstalledVersionInfo(versionId, versionInfo);
                
                return true;
            } catch (Exception e) {
                LOGGER.error("下载版本时发生错误", e);
                progressCallback.onError(e.getMessage());
                return false;
            } finally {
                // 标记下载任务完成
                downloadTasks.remove(versionId);
                performanceMonitor.endAndLogIfSlow("downloadVersion_" + versionId, 60000);
            }
        });
    }

    /**
     * 从缓存获取版本信息
     */
    private VersionInfo getVersionInfoFromCache(String versionId) throws IOException {
        performanceMonitor.start("getVersionInfoFromCache_" + versionId);
        
        try {
            // 检查内存缓存
            if (versionInfoCache.containsKey(versionId)) {
                LOGGER.debug("从内存缓存获取版本信息: {}", versionId);
                return versionInfoCache.get(versionId);
            }
            
            // 检查磁盘缓存
            byte[] cachedData = cacheManager.loadFromDisk("version_info_" + versionId + ".json", TimeUnit.HOURS.toMillis(24));
            if (cachedData != null) {
                LOGGER.debug("从磁盘缓存获取版本信息: {}", versionId);
                // 这里简化处理，通过API管理器获取并解析
                VersionInfo versionInfo = apiManager.getVersionInfo(versionId);
                if (versionInfo != null) {
                    versionInfoCache.put(versionId, versionInfo);
                }
                return versionInfo;
            }
            
            // 从API获取并缓存
            LOGGER.debug("从API获取版本信息: {}", versionId);
            VersionInfo versionInfo = apiManager.getVersionInfo(versionId);
            if (versionInfo != null) {
                versionInfoCache.put(versionId, versionInfo);
            }
            return versionInfo;
        } finally {
            performanceMonitor.endAndLog("getVersionInfoFromCache_" + versionId);
        }
    }

    /**
     * 缓存已安装版本信息
     */
    private void cacheInstalledVersionInfo(String versionId, VersionInfo versionInfo) {
        try {
            String json = versionInfo.toString(); // 实际应使用序列化方法
            cacheManager.saveToDisk("installed_version_" + versionId + ".json", json.getBytes());
            LOGGER.debug("已缓存版本信息: {}", versionId);
        } catch (Exception e) {
            LOGGER.warn("缓存版本信息失败: {}", versionId, e);
        }
    }

    /**
     * 启动游戏
     * @param versionId 版本ID
     * @param username 用户名
     * @param maxMemoryMB 最大内存（MB）
     * @param javaPath Java路径
     * @param fullscreen 是否全屏
     * @return 启动结果
     */
    public boolean launchGame(String versionId, String username, int maxMemoryMB, String javaPath, boolean fullscreen) {
        performanceMonitor.start("launchGame_" + versionId);
        
        try {
            if (!isVersionInstalled(versionId)) {
                LOGGER.error("版本未安装: {}", versionId);
                return false;
            }
            
            LOGGER.info("启动游戏版本: {}, 用户名: {}, 最大内存: {}MB", versionId, username, maxMemoryMB);
            
            // 获取版本信息（从缓存优先）
            VersionInfo versionInfo = getVersionInfoFromCache(versionId);
            if (versionInfo == null) {
                LOGGER.error("获取版本信息失败");
                return false;
            }
            
            // 启动游戏
            boolean result = launcher.launch(versionInfo, username, maxMemoryMB, javaPath, fullscreen);
            
            if (result) {
                LOGGER.info("游戏启动成功: {}", versionId);
                // 记录启动统计信息
                performanceMonitor.incrementCounter("game_launch_success");
            } else {
                LOGGER.error("游戏启动失败: {}", versionId);
                performanceMonitor.incrementCounter("game_launch_failure");
            }
            
            return result;
        } catch (Exception e) {
            LOGGER.error("启动游戏时发生错误", e);
            performanceMonitor.incrementCounter("game_launch_exception");
            return false;
        } finally {
            performanceMonitor.endAndLogIfSlow("launchGame_" + versionId, 5000);
        }
    }

    /**
     * 删除指定版本
     * @param versionId 版本ID
     * @return 删除结果
     */
    public boolean deleteVersion(String versionId) {
        performanceMonitor.start("deleteVersion_" + versionId);
        
        try {
            if (!isVersionInstalled(versionId)) {
                LOGGER.warn("要删除的版本不存在: {}", versionId);
                return false;
            }
            
            Path versionDir = Paths.get(gameDirectory, "versions", versionId);
            deleteDirectory(versionDir);
            
            // 移除缓存
            versionInfoCache.remove(versionId);
            cacheManager.removeFromDisk("installed_version_" + versionId + ".json");
            cacheManager.removeFromDisk("version_info_" + versionId + ".json");
            
            LOGGER.info("版本已删除: {}", versionId);
            return true;
        } catch (Exception e) {
            LOGGER.error("删除版本时发生错误", e);
            return false;
        } finally {
            performanceMonitor.endAndLogIfSlow("deleteVersion_" + versionId, 10000);
        }
    }

    /**
     * 删除目录及其内容
     */
    private void deleteDirectory(Path path) throws IOException {
        if (Files.exists(path)) {
            // 使用并行流提高删除大量文件时的性能
            Files.walk(path)
                .sorted((a, b) -> b.compareTo(a)) // 从最深层开始删除
                .parallel() // 并行处理
                .forEach(file -> {
                    try {
                        Files.delete(file);
                    } catch (IOException e) {
                        LOGGER.error("删除文件失败: {}", file, e);
                    }
                });
        }
    }

    /**
     * 检查版本是否需要更新
     */
    public boolean isVersionUpToDate(String versionId) throws IOException {
        performanceMonitor.start("isVersionUpToDate_" + versionId);
        
        try {
            String latestReleaseId = apiManager.getVersionManifest().getLatest().getRelease();
            String latestSnapshotId = apiManager.getVersionManifest().getLatest().getSnapshot();
            
            boolean isLatest = versionId.equals(latestReleaseId) || versionId.equals(latestSnapshotId);
            LOGGER.debug("版本 {} 是否为最新: {}", versionId, isLatest);
            return isLatest;
        } finally {
            performanceMonitor.endAndLog("isVersionUpToDate_" + versionId);
        }
    }

    /**
     * 下载进度回调接口
     */
    public interface DownloadProgressCallback {
        void onProgress(double progress, String message);
        void onError(String errorMessage);
    }
}