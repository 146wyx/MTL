package com.minecraftlauncher.accounts;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Microsoft OAuth 2.0 客户端
 * 负责处理Microsoft账户的OAuth认证流程
 */
public class MicrosoftOAuthClient {
    private static final Logger LOGGER = LogManager.getLogger(MicrosoftOAuthClient.class);
    
    // Microsoft OAuth 端点
    private static final String AUTHORITY = "https://login.microsoftonline.com/consumers/oauth2/v2.0";
    private static final String AUTHORIZATION_ENDPOINT = AUTHORITY + "/authorize";
    private static final String TOKEN_ENDPOINT = AUTHORITY + "/token";
    private static final String MINECRAFT_AUTH_ENDPOINT = "https://api.minecraftservices.com/authentication/login_with_xbox";
    private static final String PROFILE_ENDPOINT = "https://api.minecraftservices.com/minecraft/profile";
    
    // 重定向URI（根据用户提供的信息）
    private static final String REDIRECT_URI = "http://localhost:3483";
    
    // 客户端ID（Minecraft启动器的默认客户端ID）
    private static final String CLIENT_ID = "00000000402b5328";
    
    // OAuth 范围
    private static final String SCOPE = "XboxLive.signin offline_access";
    
    private final HttpClient httpClient;
    
    public MicrosoftOAuthClient() {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }
    
    /**
     * 生成Microsoft OAuth登录URL
     * @return 登录URL
     */
    public String generateLoginUrl() {
        String state = UUID.randomUUID().toString();
        String nonce = UUID.randomUUID().toString();
        
        return String.format(
            "%s?client_id=%s&response_type=code&redirect_uri=%s&scope=%s&state=%s&nonce=%s",
            AUTHORIZATION_ENDPOINT,
            URLEncoder.encode(CLIENT_ID, StandardCharsets.UTF_8),
            URLEncoder.encode(REDIRECT_URI, StandardCharsets.UTF_8),
            URLEncoder.encode(SCOPE, StandardCharsets.UTF_8),
            URLEncoder.encode(state, StandardCharsets.UTF_8),
            URLEncoder.encode(nonce, StandardCharsets.UTF_8)
        );
    }
    
    /**
     * 解析回调URL中的授权码
     * @param callbackUrl 回调URL
     * @return 授权码
     */
    public String extractCodeFromCallbackUrl(String callbackUrl) {
        try {
            // 从URL中提取code参数
            int codeStart = callbackUrl.indexOf("code=") + 5;
            int codeEnd = callbackUrl.indexOf("&", codeStart);
            if (codeEnd == -1) codeEnd = callbackUrl.length();
            return callbackUrl.substring(codeStart, codeEnd);
        } catch (Exception e) {
            LOGGER.error("解析回调URL失败", e);
            return null;
        }
    }
    
    /**
     * 使用授权码获取访问令牌
     * @param code 授权码
     * @return 包含访问令牌的响应
     * @throws Exception 如果获取令牌失败
     */
    public OAuthTokenResponse getAccessToken(String code) throws Exception {
        String requestBody = String.format(
            "client_id=%s&code=%s&grant_type=authorization_code&redirect_uri=%s",
            URLEncoder.encode(CLIENT_ID, StandardCharsets.UTF_8),
            URLEncoder.encode(code, StandardCharsets.UTF_8),
            URLEncoder.encode(REDIRECT_URI, StandardCharsets.UTF_8)
        );
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(TOKEN_ENDPOINT))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new Exception("获取访问令牌失败: " + response.body());
        }
        
        // 使用简单的JSON解析或返回原始响应
        return new OAuthTokenResponse(response.body());
    }
    
    /**
     * 使用刷新令牌获取新的访问令牌
     * @param refreshToken 刷新令牌
     * @return 包含访问令牌的响应
     * @throws Exception 如果刷新令牌失败
     */
    public OAuthTokenResponse refreshAccessToken(String refreshToken) throws Exception {
        String requestBody = String.format(
            "client_id=%s&refresh_token=%s&grant_type=refresh_token&scope=%s",
            URLEncoder.encode(CLIENT_ID, StandardCharsets.UTF_8),
            URLEncoder.encode(refreshToken, StandardCharsets.UTF_8),
            URLEncoder.encode(SCOPE, StandardCharsets.UTF_8)
        );
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(TOKEN_ENDPOINT))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new Exception("刷新访问令牌失败: " + response.body());
        }
        
        return new OAuthTokenResponse(response.body());
    }
    
    /**
     * 使用Xbox Live令牌登录Minecraft
     * @param xboxToken Xbox Live访问令牌
     * @return Minecraft访问令牌
     * @throws Exception 如果登录失败
     */
    public MinecraftAuthResponse loginToMinecraft(String xboxToken) throws Exception {
        // 构建请求体
        String requestBody = String.format(
            "{\"identityToken\": \"XBL3.0 x=%s;%s\"}",
            "userhash", // 这里需要从Xbox响应中获取userhash
            xboxToken
        );
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(MINECRAFT_AUTH_ENDPOINT))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new Exception("Minecraft登录失败: " + response.body());
        }
        
        return new MinecraftAuthResponse(response.body());
    }
    
    /**
     * 获取Minecraft个人资料
     * @param minecraftToken Minecraft访问令牌
     * @return Minecraft个人资料
     * @throws Exception 如果获取失败
     */
    public MinecraftProfileResponse getMinecraftProfile(String minecraftToken) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(PROFILE_ENDPOINT))
            .header("Authorization", "Bearer " + minecraftToken)
            .GET()
            .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new Exception("获取Minecraft个人资料失败: " + response.body());
        }
        
        return new MinecraftProfileResponse(response.body());
    }
    
    /**
     * OAuth令牌响应类
     */
    public static class OAuthTokenResponse {
        private final String rawResponse;
        private String accessToken;
        private String refreshToken;
        private long expiresIn;
        
        public OAuthTokenResponse(String rawResponse) {
            this.rawResponse = rawResponse;
            // 解析JSON响应
            parseResponse();
        }
        
        private void parseResponse() {
            try {
                // 简单的JSON解析逻辑
                this.accessToken = extractField("access_token");
                this.refreshToken = extractField("refresh_token");
                this.expiresIn = Long.parseLong(extractField("expires_in"));
            } catch (Exception e) {
                LOGGER.error("解析OAuth令牌响应失败", e);
            }
        }
        
        private String extractField(String fieldName) {
            String search = "\"" + fieldName + "\":\"";
            int start = rawResponse.indexOf(search) + search.length();
            int end = rawResponse.indexOf("\"", start);
            return rawResponse.substring(start, end);
        }
        
        public String getAccessToken() { return accessToken; }
        public String getRefreshToken() { return refreshToken; }
        public long getExpiresIn() { return expiresIn; }
    }
    
    /**
     * Minecraft认证响应类
     */
    public static class MinecraftAuthResponse {
        private final String rawResponse;
        private String accessToken;
        private String tokenType;
        private String refreshToken;
        private long expiresIn;
        
        public MinecraftAuthResponse(String rawResponse) {
            this.rawResponse = rawResponse;
            parseResponse();
        }
        
        private void parseResponse() {
            try {
                this.accessToken = extractField("access_token");
                this.tokenType = extractField("token_type");
                this.refreshToken = extractField("refresh_token");
                this.expiresIn = Long.parseLong(extractField("expires_in"));
            } catch (Exception e) {
                LOGGER.error("解析Minecraft认证响应失败", e);
            }
        }
        
        private String extractField(String fieldName) {
            String search = "\"" + fieldName + "\":\"";
            int start = rawResponse.indexOf(search) + search.length();
            int end = rawResponse.indexOf("\"", start);
            return rawResponse.substring(start, end);
        }
        
        public String getAccessToken() { return accessToken; }
        public String getTokenType() { return tokenType; }
        public String getRefreshToken() { return refreshToken; }
        public long getExpiresIn() { return expiresIn; }
    }
    
    /**
     * Minecraft个人资料响应类
     */
    public static class MinecraftProfileResponse {
        private final String rawResponse;
        private String id;
        private String name;
        private boolean legacy;
        private boolean suspended;
        
        public MinecraftProfileResponse(String rawResponse) {
            this.rawResponse = rawResponse;
            parseResponse();
        }
        
        private void parseResponse() {
            try {
                this.id = extractField("id");
                this.name = extractField("name");
                // 处理布尔值
                this.legacy = rawResponse.contains("\"legacy\":true");
                this.suspended = rawResponse.contains("\"suspended\":true");
            } catch (Exception e) {
                LOGGER.error("解析Minecraft个人资料响应失败", e);
            }
        }
        
        private String extractField(String fieldName) {
            String search = "\"" + fieldName + "\":\"";
            int start = rawResponse.indexOf(search) + search.length();
            int end = rawResponse.indexOf("\"", start);
            return rawResponse.substring(start, end);
        }
        
        public String getId() { return id; }
        public String getName() { return name; }
        public boolean isLegacy() { return legacy; }
        public boolean isSuspended() { return suspended; }
    }
}