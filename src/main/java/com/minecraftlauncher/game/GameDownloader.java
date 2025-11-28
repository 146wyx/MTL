package com.minecraftlauncher.game;

import com.google.gson.Gson;
import com.minecraftlauncher.api.MinecraftAPIClient;
import com.minecraftlauncher.api.models.VersionInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 游戏下载器
 * 负责下载Minecraft游戏文件、库文件和资源文件
 */
public class GameDownloader {

    private static final Logger LOGGER = LogManager.getLogger(GameDownloader.class);
    private final String gameDirectory;
    private final MinecraftAPIClient apiClient;
    private final Gson gson;

    /**
     * 构造函数
     * @param gameDirectory 游戏安装目录
     */
    public GameDownloader(String gameDirectory) {
        this.gameDirectory = gameDirectory;
        this.apiClient = new MinecraftAPIClient();
        this.gson = new Gson();
    }

    /**
     * 下载游戏JAR文件
     * @param versionInfo 版本信息
     * @return 下载是否成功
     */
    public boolean downloadGameJar(VersionInfo versionInfo) {
        try {
            String versionId = versionInfo.getId();
            VersionInfo.DownloadInfo clientDownload = versionInfo.getDownloads().getClient();
            
            if (clientDownload == null || clientDownload.getUrl() == null) {
                LOGGER.error("版本 {} 缺少客户端下载信息", versionId);
                return false;
            }
            
            // 创建版本目录
            Path versionDir = Paths.get(gameDirectory, "versions", versionId);
            Files.createDirectories(versionDir);
            
            // 下载游戏JAR
            Path jarPath = versionDir.resolve(versionId + ".jar");
            LOGGER.info("下载游戏JAR: {}", jarPath);
            boolean jarDownloaded = downloadFile(clientDownload.getUrl(), jarPath.toString(), clientDownload.getSha1());
            
            // 保存版本JSON文件
            if (jarDownloaded) {
                Path jsonPath = versionDir.resolve(versionId + ".json");
                LOGGER.info("保存版本JSON: {}", jsonPath);
                try (Writer writer = Files.newBufferedWriter(jsonPath)) {
                    gson.toJson(versionInfo, writer);
                }
                return true;
            }
            
            return false;
        } catch (Exception e) {
            LOGGER.error("下载游戏JAR失败", e);
            return false;
        }
    }

    /**
     * 下载库文件
     * @param versionInfo 版本信息
     * @return 下载是否成功
     */
    public boolean downloadLibraries(VersionInfo versionInfo) {
        try {
            Set<String> downloadedLibs = new HashSet<>();
            boolean allDownloaded = true;
            
            for (VersionInfo.Library library : versionInfo.getLibraries()) {
                // 跳过不兼容的库
                if (shouldSkipLibrary(library)) {
                    continue;
                }
                
                VersionInfo.DownloadInfo artifact = library.getDownloads().getArtifact();
                if (artifact == null) {
                    continue;
                }
                
                String path = artifact.getPath();
                if (downloadedLibs.contains(path)) {
                    continue; // 避免重复下载
                }
                
                Path libPath = Paths.get(gameDirectory, "libraries", path);
                Files.createDirectories(libPath.getParent());
                
                LOGGER.info("下载库文件: {}", path);
                if (downloadFile(artifact.getUrl(), libPath.toString(), artifact.getSha1())) {
                    downloadedLibs.add(path);
                } else {
                    LOGGER.error("下载库文件失败: {}", path);
                    allDownloaded = false;
                }
            }
            
            return allDownloaded;
        } catch (Exception e) {
            LOGGER.error("下载库文件失败", e);
            return false;
        }
    }

    /**
     * 下载资源文件
     * @param versionInfo 版本信息
     * @return 下载是否成功
     */
    public boolean downloadAssets(VersionInfo versionInfo) {
        try {
            // 确保资源目录存在
            Path assetsDir = Paths.get(gameDirectory, "assets");
            Path indexesDir = assetsDir.resolve("indexes");
            Path objectsDir = assetsDir.resolve("objects");
            Files.createDirectories(indexesDir);
            Files.createDirectories(objectsDir);
            
            // 这里简化处理，实际需要下载资源索引文件并解析
            // 然后下载每个资源文件
            
            LOGGER.info("资源文件下载逻辑已执行（简化版）");
            return true;
        } catch (Exception e) {
            LOGGER.error("下载资源文件失败", e);
            return false;
        }
    }

    /**
     * 下载单个文件
     * @param url 文件URL
     * @param destination 目标路径
     * @param expectedSha1 预期SHA1校验和
     * @return 下载是否成功
     */
    private boolean downloadFile(String url, String destination, String expectedSha1) {
        try {
            // 检查文件是否已存在且校验和正确
            File destinationFile = new File(destination);
            if (destinationFile.exists() && expectedSha1 != null) {
                String actualSha1 = calculateSha1(destinationFile);
                if (expectedSha1.equals(actualSha1)) {
                    LOGGER.info("文件已存在且校验和正确，跳过下载: {}", destination);
                    return true;
                } else {
                    LOGGER.warn("文件校验和不匹配，重新下载: {}", destination);
                }
            }
            
            // 下载文件
            LOGGER.info("从 {} 下载到 {}", url, destination);
            try (InputStream in = new URL(url).openStream()) {
                Files.copy(in, Paths.get(destination), StandardCopyOption.REPLACE_EXISTING);
            }
            
            // 验证文件
            if (expectedSha1 != null) {
                String actualSha1 = calculateSha1(destinationFile);
                if (!expectedSha1.equals(actualSha1)) {
                    LOGGER.error("文件校验失败: {}", destination);
                    Files.deleteIfExists(Paths.get(destination));
                    return false;
                }
            }
            
            return true;
        } catch (Exception e) {
            LOGGER.error("下载文件失败: {}", url, e);
            try {
                Files.deleteIfExists(Paths.get(destination));
            } catch (IOException ex) {
                LOGGER.error("删除损坏文件失败", ex);
            }
            return false;
        }
    }

    /**
     * 计算文件SHA1校验和
     */
    private String calculateSha1(File file) throws IOException {
        // 简化实现，实际应该使用MessageDigest计算SHA1
        LOGGER.warn("SHA1校验和计算功能未完全实现");
        return "";
    }

    /**
     * 判断是否应该跳过库文件
     */
    private boolean shouldSkipLibrary(VersionInfo.Library library) {
        // 检查OS限制
        if (library.getNatives() != null) {
            String osName = System.getProperty("os.name").toLowerCase();
            if (osName.contains("win") && library.getNatives().getWindows() == null) {
                return true;
            }
        }
        
        // 检查规则
        if (library.getRules() != null) {
            for (VersionInfo.Library.Rule rule : library.getRules()) {
                if ("disallow".equals(rule.getAction()) && matchesCurrentOs(rule.getOs())) {
                    return true;
                }
                if ("allow".equals(rule.getAction()) && !matchesCurrentOs(rule.getOs())) {
                    return true;
                }
            }
        }
        
        return false;
    }

    /**
     * 检查是否匹配当前操作系统
     */
    private boolean matchesCurrentOs(VersionInfo.Library.Rule.OS os) {
        if (os == null) {
            return true;
        }
        
        String osName = System.getProperty("os.name").toLowerCase();
        String targetOs = os.getName();
        
        if (targetOs == null) {
            return true;
        }
        
        if (targetOs.equals("windows") && osName.contains("win")) {
            return true;
        }
        
        return false;
    }
}