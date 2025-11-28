package com.minecraftlauncher.game;

import com.minecraftlauncher.api.models.VersionInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 游戏启动器
 * 负责构建并启动Minecraft游戏进程
 */
public class GameLauncher {

    private static final Logger LOGGER = LogManager.getLogger(GameLauncher.class);
    private final String gameDirectory;

    /**
     * 构造函数
     * @param gameDirectory 游戏安装目录
     */
    public GameLauncher(String gameDirectory) {
        this.gameDirectory = gameDirectory;
    }

    /**
     * 启动Minecraft游戏
     * @param versionInfo 版本信息
     * @param username 用户名
     * @param maxMemoryMB 最大内存（MB）
     * @param javaPath Java可执行文件路径
     * @param fullscreen 是否全屏
     * @return 启动是否成功
     */
    public boolean launch(VersionInfo versionInfo, String username, int maxMemoryMB, String javaPath, boolean fullscreen) {
        try {
            // 构建启动命令
            List<String> command = buildLaunchCommand(versionInfo, username, maxMemoryMB, javaPath, fullscreen);
            
            // 打印启动命令（用于调试）
            LOGGER.info("启动命令: {}", String.join(" ", command));
            
            // 创建进程构建器
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.directory(new File(gameDirectory));
            
            // 重定向错误流到标准输出
            processBuilder.redirectErrorStream(true);
            
            // 启动进程
            LOGGER.info("启动Minecraft进程...");
            Process process = processBuilder.start();
            
            // 启动单独的线程读取输出
            startOutputReader(process);
            
            // 非阻塞方式启动，不等待进程结束
            LOGGER.info("Minecraft已启动");
            return true;
        } catch (Exception e) {
            LOGGER.error("启动游戏失败", e);
            return false;
        }
    }

    /**
     * 构建启动命令
     */
    private List<String> buildLaunchCommand(VersionInfo versionInfo, String username, int maxMemoryMB, String javaPath, boolean fullscreen) {
        List<String> command = new ArrayList<>();
        
        // Java可执行文件路径
        if (javaPath != null && !javaPath.isEmpty()) {
            command.add(javaPath);
        } else {
            // 使用默认Java
            command.add("java");
        }
        
        // JVM参数
        command.add("-Xmx" + maxMemoryMB + "M");
        command.add("-XX:+UnlockExperimentalVMOptions");
        command.add("-XX:+UseG1GC");
        command.add("-XX:G1NewSizePercent=20");
        command.add("-XX:G1ReservePercent=20");
        command.add("-XX:MaxGCPauseMillis=50");
        command.add("-XX:G1HeapRegionSize=32M");
        
        // 游戏目录和库路径
        command.add("-Djava.library.path=" + gameDirectory + File.separator + "versions" + File.separator + versionInfo.getId() + File.separator + "natives");
        command.add("-cp");
        command.add(buildClasspath(versionInfo));
        
        // 主类
        command.add(versionInfo.getMainClass());
        
        // 游戏参数
        command.add("--username");
        command.add(username);
        command.add("--version");
        command.add(versionInfo.getId());
        command.add("--gameDir");
        command.add(gameDirectory);
        command.add("--assetsDir");
        command.add(gameDirectory + File.separator + "assets");
        command.add("--uuid");
        command.add(generateUUID(username)); // 为离线模式生成UUID
        command.add("--accessToken");
        command.add("0"); // 离线模式令牌
        command.add("--userType");
        command.add("offline");
        
        // 全屏参数
        if (fullscreen) {
            command.add("--fullscreen");
        }
        
        return command;
    }

    /**
     * 构建类路径
     */
    private String buildClasspath(VersionInfo versionInfo) {
        StringBuilder classpath = new StringBuilder();
        String separator = System.getProperty("path.separator");
        
        // 添加游戏JAR
        Path gameJar = Paths.get(gameDirectory, "versions", versionInfo.getId(), versionInfo.getId() + ".jar");
        classpath.append(gameJar);
        
        // 添加所有库文件
        for (VersionInfo.Library library : versionInfo.getLibraries()) {
            // 跳过不兼容的库
            if (shouldSkipLibrary(library)) {
                continue;
            }
            
            VersionInfo.DownloadInfo artifact = library.getDownloads().getArtifact();
            if (artifact != null) {
                Path libPath = Paths.get(gameDirectory, "libraries", artifact.getPath());
                classpath.append(separator).append(libPath);
            }
        }
        
        return classpath.toString();
    }

    /**
     * 启动输出读取线程
     */
    private void startOutputReader(Process process) {
        Thread outputThread = new Thread(() -> {
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    LOGGER.info("[MINECRAFT] {}", line);
                }
            } catch (IOException e) {
                LOGGER.error("读取游戏输出失败", e);
            }
        });
        outputThread.setDaemon(true);
        outputThread.start();
    }

    /**
     * 为用户名生成UUID
     */
    private String generateUUID(String username) {
        return UUID.nameUUIDFromBytes("OfflinePlayer:" + username.toLowerCase().getBytes()).toString();
    }

    /**
     * 判断是否应该跳过库文件
     */
    private boolean shouldSkipLibrary(VersionInfo.Library library) {
        // 简化实现，实际逻辑同GameDownloader
        if (library.getRules() != null) {
            for (VersionInfo.Library.Rule rule : library.getRules()) {
                if ("disallow".equals(rule.getAction())) {
                    return true;
                }
            }
        }
        return false;
    }
}