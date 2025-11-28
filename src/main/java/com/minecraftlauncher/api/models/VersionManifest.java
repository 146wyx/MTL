package com.minecraftlauncher.api.models;

import java.util.List;

/**
 * Minecraft版本清单模型
 * 对应Minecraft官方API返回的版本清单数据结构
 */
public class VersionManifest {
    private LatestVersion latest;
    private List<Version> versions;

    public LatestVersion getLatest() {
        return latest;
    }

    public void setLatest(LatestVersion latest) {
        this.latest = latest;
    }

    public List<Version> getVersions() {
        return versions;
    }

    public void setVersions(List<Version> versions) {
        this.versions = versions;
    }

    /**
     * 获取最新版本信息的内部类
     */
    public static class LatestVersion {
        private String release;
        private String snapshot;

        public String getRelease() {
            return release;
        }

        public void setRelease(String release) {
            this.release = release;
        }

        public String getSnapshot() {
            return snapshot;
        }

        public void setSnapshot(String snapshot) {
            this.snapshot = snapshot;
        }
    }

    /**
     * 版本信息的内部类
     */
    public static class Version {
        private String id;
        private String type;
        private String url;
        private String time;
        private String releaseTime;

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

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
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
    }
}