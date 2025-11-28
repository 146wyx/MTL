package com.minecraftlauncher.api.models;

import java.util.List;
import java.util.Map;

/**
 * Minecraft版本详细信息模型
 * 对应Minecraft官方API返回的单个版本详细数据结构
 */
public class VersionInfo {
    private String id;
    private String type;
    private String time;
    private String releaseTime;
    private String minecraftArguments;
    private String mainClass;
    private int minimumLauncherVersion;
    private List<Library> libraries;
    private Downloads downloads;
    private Map<String, String> arguments;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String getReleaseTime() {
        return releaseTime;
    }

    public void setReleaseTime(String releaseTime) {
        this.releaseTime = releaseTime;
    }

    public String getMinecraftArguments() {
        return minecraftArguments;
    }

    public void setMinecraftArguments(String minecraftArguments) {
        this.minecraftArguments = minecraftArguments;
    }

    public String getMainClass() {
        return mainClass;
    }

    public void setMainClass(String mainClass) {
        this.mainClass = mainClass;
    }

    public int getMinimumLauncherVersion() {
        return minimumLauncherVersion;
    }

    public void setMinimumLauncherVersion(int minimumLauncherVersion) {
        this.minimumLauncherVersion = minimumLauncherVersion;
    }

    public List<Library> getLibraries() {
        return libraries;
    }

    public void setLibraries(List<Library> libraries) {
        this.libraries = libraries;
    }

    public Downloads getDownloads() {
        return downloads;
    }

    public void setDownloads(Downloads downloads) {
        this.downloads = downloads;
    }

    public Map<String, String> getArguments() {
        return arguments;
    }

    public void setArguments(Map<String, String> arguments) {
        this.arguments = arguments;
    }

    /**
     * 库信息的内部类
     */
    public static class Library {
        private String name;
        private Map<String, String> downloads;
        private NativeInfo natives;
        private ExtractInfo extract;
        private List<Rule> rules;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Map<String, String> getDownloads() {
            return downloads;
        }

        public void setDownloads(Map<String, String> downloads) {
            this.downloads = downloads;
        }

        public NativeInfo getNatives() {
            return natives;
        }

        public void setNatives(NativeInfo natives) {
            this.natives = natives;
        }

        public ExtractInfo getExtract() {
            return extract;
        }

        public void setExtract(ExtractInfo extract) {
            this.extract = extract;
        }

        public List<Rule> getRules() {
            return rules;
        }

        public void setRules(List<Rule> rules) {
            this.rules = rules;
        }

        /**
         * 规则信息的内部类，用于判断库文件的兼容性
         */
        public static class Rule {
            private String action;
            private OS os;

            public String getAction() {
                return action;
            }

            public void setAction(String action) {
                this.action = action;
            }

            public OS getOs() {
                return os;
            }

            public void setOs(OS os) {
                this.os = os;
            }

            /**
             * 操作系统信息的内部类
             */
            public static class OS {
                private String name;

                public String getName() {
                    return name;
                }

                public void setName(String name) {
                    this.name = name;
                }
            }
        }
    }

    /**
     * 原生库信息的内部类
     */
    public static class NativeInfo {
        private Map<String, String> windows;
        private Map<String, String> linux;
        private Map<String, String> osx;

        public Map<String, String> getWindows() {
            return windows;
        }

        public void setWindows(Map<String, String> windows) {
            this.windows = windows;
        }

        public Map<String, String> getLinux() {
            return linux;
        }

        public void setLinux(Map<String, String> linux) {
            this.linux = linux;
        }

        public Map<String, String> getOsx() {
            return osx;
        }

        public void setOsx(Map<String, String> osx) {
            this.osx = osx;
        }
    }

    /**
     * 提取信息的内部类
     */
    public static class ExtractInfo {
        private List<String> exclude;

        public List<String> getExclude() {
            return exclude;
        }

        public void setExclude(List<String> exclude) {
            this.exclude = exclude;
        }
    }

    /**
     * 下载信息的内部类
     */
    public static class Downloads {
        private DownloadInfo client;
        private DownloadInfo server;
        private Map<String, DownloadInfo> classifiers;

        public DownloadInfo getClient() {
            return client;
        }

        public void setClient(DownloadInfo client) {
            this.client = client;
        }

        public DownloadInfo getServer() {
            return server;
        }

        public void setServer(DownloadInfo server) {
            this.server = server;
        }

        public Map<String, DownloadInfo> getClassifiers() {
            return classifiers;
        }

        public void setClassifiers(Map<String, DownloadInfo> classifiers) {
            this.classifiers = classifiers;
        }
    }

    /**
     * 下载信息的内部类
     */
    public static class DownloadInfo {
        private String sha1;
        private int size;
        private String url;

        public String getSha1() {
            return sha1;
        }

        public void setSha1(String sha1) {
            this.sha1 = sha1;
        }

        public int getSize() {
            return size;
        }

        public void setSize(int size) {
            this.size = size;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }
    }
}