package com.minecraftlauncher.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 性能监控工具类
 * 用于测量和记录代码执行时间
 */
public class PerformanceMonitor {

    private static final Logger LOGGER = LogManager.getLogger(PerformanceMonitor.class);
    private static final PerformanceMonitor INSTANCE = new PerformanceMonitor();
    
    private final Map<String, Long> startTimeMap;
    private final Map<String, Long> durationMap;
    private final Map<String, Integer> callCountMap;
    
    private PerformanceMonitor() {
        startTimeMap = new HashMap<>();
        durationMap = new HashMap<>();
        callCountMap = new HashMap<>();
    }
    
    /**
     * 获取单例实例
     */
    public static PerformanceMonitor getInstance() {
        return INSTANCE;
    }
    
    /**
     * 开始测量性能
     * @param key 标识测量点的键
     */
    public void start(String key) {
        startTimeMap.put(key, System.nanoTime());
        callCountMap.put(key, callCountMap.getOrDefault(key, 0) + 1);
    }
    
    /**
     * 结束测量并返回执行时间（毫秒）
     * @param key 标识测量点的键
     * @return 执行时间（毫秒）
     */
    public long end(String key) {
        if (!startTimeMap.containsKey(key)) {
            LOGGER.warn("尝试结束未开始的性能测量: {}", key);
            return -1;
        }
        
        long duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTimeMap.get(key));
        startTimeMap.remove(key);
        
        // 更新总时长
        durationMap.put(key, durationMap.getOrDefault(key, 0L) + duration);
        
        LOGGER.debug("性能测量 [{}]: {} ms", key, duration);
        return duration;
    }
    
    /**
     * 结束测量并记录日志，仅当日志级别为DEBUG时
     * @param key 标识测量点的键
     */
    public void endAndLog(String key) {
        long duration = end(key);
        if (duration > 0 && LOGGER.isDebugEnabled()) {
            LOGGER.debug("性能测量 [{}]: {} ms", key, duration);
        }
    }
    
    /**
     * 结束测量并记录日志，当执行时间超过阈值时
     * @param key 标识测量点的键
     * @param thresholdMs 阈值（毫秒）
     */
    public void endAndLogIfSlow(String key, long thresholdMs) {
        long duration = end(key);
        if (duration > thresholdMs) {
            LOGGER.warn("性能警告 [{}]: {} ms (超过阈值 {} ms)", key, duration, thresholdMs);
        }
    }
    
    /**
     * 输出性能统计报告
     */
    public void printReport() {
        LOGGER.info("======= 性能统计报告 =======");
        durationMap.forEach((key, totalDuration) -> {
            int count = callCountMap.getOrDefault(key, 0);
            long avgDuration = count > 0 ? totalDuration / count : 0;
            LOGGER.info("[{}]: 总时间 {} ms, 调用次数 {}, 平均时间 {} ms", 
                    key, totalDuration, count, avgDuration);
        });
        LOGGER.info("==========================");
    }
    
    /**
     * 递增计数器
     * @param key 计数器标识
     */
    public void incrementCounter(String key) {
        callCountMap.put(key, callCountMap.getOrDefault(key, 0) + 1);
    }
    
    /**
     * 重置性能统计数据
     */
    public void reset() {
        durationMap.clear();
        callCountMap.clear();
        startTimeMap.clear();
    }
    
    /**
     * 检查是否正在进行测量
     */
    public boolean isRunning(String key) {
        return startTimeMap.containsKey(key);
    }
    
    /**
     * 获取当前正在运行的测量数量
     */
    public int getActiveMeasurementCount() {
        return startTimeMap.size();
    }
}