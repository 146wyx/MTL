package com.minecraftlauncher.accounts;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 账户管理器
 * 负责管理所有用户账户的添加、删除、保存和加载
 */
public class AccountManager {

    private static final Logger LOGGER = LogManager.getLogger(AccountManager.class);
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(Account.class, new AccountSerializer())
            .registerTypeAdapter(Account.class, new AccountDeserializer())
            .create();
    private static final String ACCOUNTS_FILE = "accounts.json";
    private final MicrosoftOAuthClient oAuthClient;
    
    private final String accountsDirectory;
    private final List<Account> accounts;
    private Account selectedAccount;

    /**
     * 构造函数
     * @param accountsDirectory 账户文件目录
     */
    public AccountManager(String accountsDirectory) {
        this.accountsDirectory = accountsDirectory;
        this.accounts = new ArrayList<>();
        this.oAuthClient = new MicrosoftOAuthClient();
        
        // 确保账户目录存在
        ensureDirectoryExists();
        
        // 加载账户
        loadAccounts();
    }
    
    /**
     * 生成Microsoft登录URL
     * @return 登录URL
     */
    public String generateMicrosoftLoginUrl() {
        return oAuthClient.generateLoginUrl();
    }
    
    /**
     * 通过回调URL完成Microsoft登录
     * @param callbackUrl 回调URL
     * @return 登录的账户
     * @throws Exception 如果登录失败
     */
    public Account completeMicrosoftLogin(String callbackUrl) throws Exception {
        LOGGER.info("开始Microsoft登录流程");
        
        // 1. 从回调URL中提取授权码
        String code = oAuthClient.extractCodeFromCallbackUrl(callbackUrl);
        if (code == null) {
            throw new Exception("无法从回调URL中提取授权码");
        }
        
        // 2. 使用授权码获取访问令牌
        MicrosoftOAuthClient.OAuthTokenResponse tokenResponse = oAuthClient.getAccessToken(code);
        
        // 3. 使用Microsoft访问令牌登录Minecraft（这里简化了Xbox认证流程）
        // 注意：实际流程还需要通过Xbox Live认证，这里为了演示做了简化
        String minecraftToken = tokenResponse.getAccessToken();
        
        // 4. 获取Minecraft个人资料（这里返回模拟数据，实际需要调用API）
        String username = "Microsoft_" + UUID.randomUUID().toString().substring(0, 8);
        String uuid = UUID.randomUUID().toString().replace("-", "");
        
        // 5. 创建账户
        Account account = new Account(username, minecraftToken, tokenResponse.getRefreshToken());
        account.setUuid(uuid);
        account.setType(Account.AccountType.MICROSOFT);
        
        // 6. 添加账户并保存
        addAccount(account);
        saveAccounts();
        
        LOGGER.info("Microsoft账户登录成功: {}", username);
        return account;
    }
    
    /**
     * 刷新Microsoft账户令牌
     * @param account 要刷新令牌的Microsoft账户
     * @return 刷新是否成功
     */
    public boolean refreshToken(Account account) {
        if (account.getType() == Account.AccountType.OFFLINE) {
            return true; // 离线账户不需要刷新
        }
        
        if (account.getType() == Account.AccountType.MICROSOFT) {
            if (account.getRefreshToken() == null) {
                LOGGER.warn("没有刷新令牌，无法刷新Microsoft账户");
                return false;
            }
            
            try {
                LOGGER.info("刷新Microsoft账户令牌: {}", account.getUsername());
                
                // 使用刷新令牌获取新的访问令牌
                MicrosoftOAuthClient.OAuthTokenResponse tokenResponse = oAuthClient.refreshAccessToken(account.getRefreshToken());
                
                // 更新账户令牌
                account.setAccessToken(tokenResponse.getAccessToken());
                account.setRefreshToken(tokenResponse.getRefreshToken());
                
                return true;
            } catch (Exception e) {
                LOGGER.error("刷新Microsoft账户令牌失败", e);
                return false;
            }
        }
        
        // 处理Mojang账户
        if (account.getRefreshToken() == null) {
            LOGGER.warn("没有刷新令牌，无法刷新");
            return false;
        }
        
        try {
            LOGGER.info("刷新账户令牌: {}", account.getUsername());
            
            // 模拟刷新过程
            Thread.sleep(500);
            
            // 更新访问令牌
            account.setAccessToken(generateDummyToken());
            
            return true;
        } catch (Exception e) {
            LOGGER.error("刷新令牌失败", e);
            return false;
        }
    }
    
    /**
     * 使用凭据登录Mojang账户
     * @param username 用户名
     * @param password 密码
     * @param rememberMe 是否记住密码
     * @return 登录的账户，如果登录失败则返回null
     */
    public Account loginWithCredentials(String username, String password, boolean rememberMe) {
        try {
            LOGGER.info("尝试登录Mojang账户: {}", username);
            
            // 模拟登录过程
            Thread.sleep(1000);
            
            // 创建或更新账户
            Optional<Account> existingAccount = accounts.stream()
                    .filter(a -> a.getUsername().equals(username) && a.getType() == Account.AccountType.MOJANG)
                    .findFirst();
            
            Account account;
            if (existingAccount.isPresent()) {
                account = existingAccount.get();
            } else {
                account = new Account(username, Account.AccountType.MOJANG);
                accounts.add(account);
            }
            
            // 设置账户信息
            account.setAccessToken(generateDummyToken());
            account.setRefreshToken(generateDummyToken());
            account.setRememberMe(rememberMe);
            account.setLastLogin(System.currentTimeMillis());
            
            // 设置为选中账户
            this.selectedAccount = account;
            
            // 保存账户列表
            saveAccounts();
            
            LOGGER.info("Mojang账户登录成功: {}", username);
            return account;
        } catch (Exception e) {
            LOGGER.error("Mojang账户登录失败", e);
            return null;
        }
    }

    /**
     * 确保账户目录存在
     */
    private void ensureDirectoryExists() {
        try {
            Files.createDirectories(Paths.get(accountsDirectory));
        } catch (IOException e) {
            LOGGER.error("创建账户目录失败", e);
        }
    }

    /**
     * 加载账户列表
     */
    public void loadAccounts() {
        File accountsFile = new File(accountsDirectory, ACCOUNTS_FILE);
        
        if (!accountsFile.exists()) {
            LOGGER.info("账户文件不存在");
            return;
        }
        
        try (FileReader reader = new FileReader(accountsFile)) {
            List<Account> loadedAccounts = GSON.fromJson(reader, new TypeToken<List<Account>>(){}.getType());
            if (loadedAccounts != null) {
                accounts.clear();
                accounts.addAll(loadedAccounts);
                
                // 查找上次记住的账户
                Optional<Account> rememberedAccount = accounts.stream()
                        .filter(Account::isRememberMe)
                        .findFirst();
                
                rememberedAccount.ifPresent(account -> {
                    this.selectedAccount = account;
                    LOGGER.info("已加载记住的账户: {}", account.getUsername());
                });
                
                LOGGER.info("成功加载 {} 个账户", accounts.size());
            }
        } catch (Exception e) {
            LOGGER.error("加载账户文件失败", e);
        }
    }

    /**
     * 保存账户列表
     */
    public void saveAccounts() {
        File accountsFile = new File(accountsDirectory, ACCOUNTS_FILE);
        
        try (FileWriter writer = new FileWriter(accountsFile)) {
            // 保存账户列表，但只保存rememberMe为true的账户
            List<Account> accountsToSave = accounts.stream()
                    .filter(Account::isRememberMe)
                    .collect(Collectors.toList());
            
            GSON.toJson(accountsToSave, writer);
            LOGGER.info("成功保存 {} 个账户", accountsToSave.size());
        } catch (IOException e) {
            LOGGER.error("保存账户文件失败", e);
        }
    }

    /**
     * 添加新账户
     * @param account 要添加的账户
     * @return 是否添加成功
     */
    public boolean addAccount(Account account) {
        // 检查账户是否已存在
        if (accounts.contains(account)) {
            LOGGER.warn("账户已存在: {}", account.getUsername());
            return false;
        }
        
        accounts.add(account);
        selectedAccount = account;
        LOGGER.info("添加新账户: {}", account.getUsername());
        return true;
    }

    /**
     * 移除账户
     * @param account 要移除的账户
     * @return 是否移除成功
     */
    public boolean removeAccount(Account account) {
        boolean removed = accounts.remove(account);
        if (removed) {
            LOGGER.info("移除账户: {}", account.getUsername());
            
            // 如果移除的是当前选中的账户，清空选中状态
            if (selectedAccount != null && selectedAccount.equals(account)) {
                selectedAccount = null;
            }
        }
        return removed;
    }

    /**
     * 登录Mojang账户（模拟实现）
     * @param username 用户名
     * @param password 密码
     * @param rememberMe 是否记住密码
     * @return 登录的账户，如果登录失败返回null
     */
    public Account loginMojang(String username, String password, boolean rememberMe) {
        try {
            LOGGER.info("尝试登录Mojang账户: {}", username);
            
            // 模拟登录过程
            Thread.sleep(1000);
            
            // 创建账户对象（实际应该从API响应创建）
            Account account = new Account(username, Account.AccountType.MOJANG);
            account.setAccessToken(generateDummyToken());
            account.setRefreshToken(generateDummyToken());
            account.setRememberMe(rememberMe);
            
            // 添加到账户列表
            addAccount(account);
            
            // 保存账户
            if (rememberMe) {
                saveAccounts();
            }
            
            return account;
        } catch (Exception e) {
            LOGGER.error("Mojang账户登录失败", e);
            return null;
        }
    }

    /**
     * 创建离线账户
     * @param username 用户名
     * @return 创建的离线账户
     */
    public Account createOfflineAccount(String username) {
        Account account = new Account(username, Account.AccountType.OFFLINE);
        account.setRememberMe(true);
        
        addAccount(account);
        saveAccounts();
        
        return account;
    }

    /**
     * 生成虚拟令牌（用于测试）
     */
    private String generateDummyToken() {
        try {
            String data = System.currentTimeMillis() + "-" + Math.random();
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(data.getBytes());
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            return UUID.randomUUID().toString();
        }
    }

    /**
     * 获取所有账户
     */
    public List<Account> getAllAccounts() {
        return new ArrayList<>(accounts);
    }

    /**
     * 获取当前选中的账户
     */
    public Account getSelectedAccount() {
        return selectedAccount;
    }

    /**
     * 设置选中的账户
     * @param account 要选中的账户
     */
    public void setSelectedAccount(Account account) {
        if (accounts.contains(account)) {
            this.selectedAccount = account;
            LOGGER.info("选中账户: {}", account.getUsername());
        } else {
            LOGGER.warn("尝试选中不存在的账户: {}", account.getUsername());
        }
    }

    // refreshToken方法已在前面重写，包含了Microsoft账户支持

    /**
     * 登出当前选中的账户
     */
    public void logout() {
        if (selectedAccount != null) {
            LOGGER.info("登出账户: {}", selectedAccount.getUsername());
            selectedAccount.setAccessToken(null);
            selectedAccount.setRefreshToken(null);
            selectedAccount = null;
        }
    }

    // 用于生成UUID的辅助方法（避免引入额外依赖）
    private static class UUID {
        public static String randomUUID() {
            return java.util.UUID.randomUUID().toString();
        }
    }
}