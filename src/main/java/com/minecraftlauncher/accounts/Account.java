package com.minecraftlauncher.accounts;

import java.util.Date;
import java.util.UUID;

/**
 * 用户账户类
 * 表示Minecraft用户账户信息
 */
public class Account {
    private String username;
    private String accessToken;
    private String clientToken;
    private UUID uuid;
    private String refreshToken;
    private Date tokenExpiresAt;
    private AccountType type;
    private boolean rememberMe;
    private boolean selected;
    

    /**
     * 账户类型枚举
     */
    public enum AccountType {
        MOJANG,      // Mojang账户
        MICROSOFT,   // Microsoft账户
        OFFLINE      // 离线账户
    }

    /**
     * 构造函数
     * @param username 用户名
     * @param type 账户类型
     */
    public Account(String username, AccountType type) {
        this.username = username;
        this.type = type;
        this.clientToken = generateClientToken();
        this.rememberMe = false;
        this.selected = false;
        
        // 对于离线账户，生成UUID
        if (type == AccountType.OFFLINE) {
            this.uuid = generateOfflineUUID(username);
        }
    }

    /**
     * 生成客户端令牌
     */
    private String generateClientToken() {
        return UUID.randomUUID().toString();
    }

    /**
     * 为离线账户生成UUID
     */
    private UUID generateOfflineUUID(String username) {
        String offlinePrefix = "OfflinePlayer:" + username.toLowerCase();
        return UUID.nameUUIDFromBytes(offlinePrefix.getBytes());
    }

    /**
     * 检查令牌是否过期
     */
    public boolean isTokenExpired() {
        if (type == AccountType.OFFLINE) {
            return false; // 离线账户永不过期
        }
        if (tokenExpiresAt == null) {
            return true;
        }
        return new Date().after(tokenExpiresAt);
    }

    /**
     * 获取可用的UUID（优先使用真实UUID，离线账户使用生成的UUID）
     */
    public UUID getEffectiveUUID() {
        return uuid != null ? uuid : generateOfflineUUID(username);
    }

    /**
     * 获取可用的令牌（优先使用访问令牌，离线账户返回固定值）
     */
    public String getEffectiveToken() {
        if (type == AccountType.OFFLINE) {
            return "0";
        }
        return accessToken;
    }

    // Getters and Setters
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getClientToken() {
        return clientToken;
    }

    public void setClientToken(String clientToken) {
        this.clientToken = clientToken;
    }

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }
    
    /**
     * 设置UUID（从字符串）
     */
    public void setUuid(String uuidStr) {
        try {
            this.uuid = UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            this.uuid = null;
        }
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public Date getTokenExpiresAt() {
        return tokenExpiresAt;
    }

    public void setTokenExpiresAt(Date tokenExpiresAt) {
        this.tokenExpiresAt = tokenExpiresAt;
    }

    public AccountType getType() {
        return type;
    }

    public void setType(AccountType type) {
        this.type = type;
    }

    public boolean isRememberMe() {
        return rememberMe;
    }

    public void setRememberMe(boolean rememberMe) {
        this.rememberMe = rememberMe;
    }
    
    public boolean isSelected() {
        return selected;
    }
    
    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    /**
     * 获取账户的显示名称
     */
    public String getDisplayName() {
        return username + " (" + type.name().toLowerCase() + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Account account = (Account) o;

        if (type != account.type) return false;
        if (username != null ? !username.equals(account.username) : account.username != null) return false;
        return uuid != null ? uuid.equals(account.uuid) : account.uuid == null;
    }

    @Override
    public int hashCode() {
        int result = username != null ? username.hashCode() : 0;
        result = 31 * result + (uuid != null ? uuid.hashCode() : 0);
        result = 31 * result + (type != null ? type.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Account{" +
                "username='" + username + '\'' +
                ", type=" + type +
                ", uuid=" + uuid +
                ", tokenExpired=" + isTokenExpired() +
                "}";
    }
}