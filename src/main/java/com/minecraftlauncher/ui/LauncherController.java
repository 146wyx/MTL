package com.minecraftlauncher.ui;

import com.minecraftlauncher.api.MinecraftAPIManager;
import com.minecraftlauncher.api.models.VersionManifest;
import com.minecraftlauncher.accounts.AccountManager;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.net.URL;
import java.util.ResourceBundle;

/**
 * 启动器FXML控制器
 * 负责处理所有UI交互事件和逻辑
 */
public class LauncherController implements Initializable {

    private static final Logger LOGGER = LogManager.getLogger(LauncherController.class);
    private MinecraftAPIManager apiManager;
    private AccountManager accountManager;

    // FXML组件引用
    @FXML private BorderPane root;
    @FXML private Label statusLabel;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private CheckBox rememberMeCheckBox;
    @FXML private Button loginButton;
    @FXML private Button offlineLoginButton;
    @FXML private Button microsoftLoginButton;
    @FXML private Button launchButton;
    @FXML private ComboBox<String> versionComboBox;
    @FXML private ListView<String> installedVersionsList;
    @FXML private Button refreshVersionsButton;
    @FXML private Button downloadVersionButton;
    @FXML private Button deleteVersionButton;
    @FXML private TextField maxMemoryField;
    @FXML private TextField javaPathField;
    @FXML private Button browseJavaButton;
    @FXML private CheckBox fullscreenCheckBox;
    @FXML private CheckBox enableModsCheckBox;
    @FXML private ProgressBar progressBar;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        LOGGER.info("初始化启动器控制器");
        apiManager = new MinecraftAPIManager();
        // 获取启动器目录作为账户目录
        String launcherDir = System.getProperty("user.dir");
        accountManager = new AccountManager(launcherDir);
        
        // 初始化UI状态
        progressBar.setVisible(false);
        
        // 加载已安装版本列表（实际实现时需要从文件系统读取）
        installedVersionsList.getItems().add("暂无已安装版本");
        
        // 加载版本列表
        loadVersions();
        
        // 尝试自动登录上次选中的账户
        autoLogin();
    }
    
    /**
     * 尝试自动登录
     */
    private void autoLogin() {
        try {
            var accounts = accountManager.getAllAccounts();
            var selectedAccount = accountManager.getSelectedAccount();
            
            if (selectedAccount != null) {
                // 刷新令牌
                boolean refreshed = accountManager.refreshToken(selectedAccount);
                if (refreshed) {
                    statusLabel.setText("已自动登录: " + selectedAccount.getUsername());
                    launchButton.setDisable(false);
                }
            }
        } catch (Exception e) {
            LOGGER.error("自动登录失败", e);
        }
    }
    
    /**
     * 处理Microsoft登录按钮点击事件
     */
    @FXML
    private void handleMicrosoftLogin(ActionEvent event) {
        LOGGER.info("Microsoft登录功能暂未实现");
        statusLabel.setText("Microsoft登录功能暂未实现");
        // 显示提示对话框
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("提示");
        alert.setHeaderText(null);
        alert.setContentText("Microsoft登录功能正在开发中，请使用其他登录方式。");
        alert.showAndWait();
        statusLabel.setText("准备就绪");
    }
    
    // 自动登录功能暂时禁用
    private void autoLogin() {
        LOGGER.info("自动登录功能暂未实现");
        webEngine.locationProperty().addListener((observable, oldValue, newValue) -> {
            LOGGER.info("WebView URL变化: {} -> {}", oldValue, newValue);
            
            // 检查是否包含回调URL中的关键字
            if (newValue != null && newValue.contains("code=")) {
                // 关闭对话框
                dialog.close();
                
                // 在后台线程处理登录
                Task<Boolean> loginTask = new Task<>() {
                    @Override
                    protected Boolean call() throws Exception {
                        // 完成登录流程
                        var account = accountManager.completeMicrosoftLogin(newValue);
                        accountManager.setSelectedAccount(account);
                        return true;
                    }
                    
                    @Override
                    protected void succeeded() {
                        statusLabel.setText("Microsoft登录成功！");
                        launchButton.setDisable(false);
                    }
                    
                    @Override
                    protected void failed() {
                        Throwable e = getException();
                        LOGGER.error("Microsoft登录失败", e);
                        statusLabel.setText("Microsoft登录失败: " + e.getMessage());
                        showAlert("登录失败", "Microsoft账户登录失败: " + e.getMessage(), Alert.AlertType.ERROR);
                    }
                };
                
                new Thread(loginTask).start();
            }
        });
        
        // 显示对话框
        dialog.showAndWait();
    }

    /**
     * 加载Minecraft版本列表
     */
    private void loadVersions() {
        LOGGER.info("加载Minecraft版本列表");
        statusLabel.setText("正在加载版本列表...");
        
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                try {
                    VersionManifest manifest = apiManager.getVersionManifest();
                    Platform.runLater(() -> {
                        versionComboBox.getItems().clear();
                        for (VersionManifest.Version version : manifest.getVersions()) {
                            String displayText = version.getId() + " (" + version.getType() + ")";                            versionComboBox.getItems().add(displayText);
                        }
                        // 默认选中最新版本
                        if (!versionComboBox.getItems().isEmpty()) {
                            versionComboBox.getSelectionModel().selectFirst();
                        }
                        statusLabel.setText("版本列表加载完成");
                    });
                } catch (Exception e) {
                    LOGGER.error("加载版本列表失败", e);
                    Platform.runLater(() -> {
                        statusLabel.setText("加载版本列表失败: " + e.getMessage());
                    });
                }
                return null;
            }
        };
        
        new Thread(task).start();
    }

    /**
     * 处理登录按钮点击事件（传统Mojang账户登录）
     */
    @FXML
    private void handleLogin(ActionEvent event) {
        String username = usernameField.getText();
        String password = passwordField.getText();
        
        if (username.isEmpty()) {
            showAlert("登录失败", "用户名不能为空", Alert.AlertType.ERROR);
            return;
        }
        
        statusLabel.setText("正在登录...");
        loginButton.setDisable(true);
        
        // 使用AccountManager进行实际登录
        Task<Boolean> task = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                // 调用账户管理模块进行登录
                boolean rememberMe = rememberMeCheckBox.isSelected();
                var account = accountManager.loginWithCredentials(username, password, rememberMe);
                accountManager.setSelectedAccount(account);
                return true;
            }

            @Override
            protected void succeeded() {
                statusLabel.setText("登录成功！欢迎，" + username + "");
                launchButton.setDisable(false);
                loginButton.setDisable(false);
            }

            @Override
            protected void failed() {
                Throwable e = getException();
                LOGGER.error("登录失败", e);
                statusLabel.setText("登录失败: " + e.getMessage());
                loginButton.setDisable(false);
            }
        };
        
        new Thread(task).start();
    }

    /**
     * 处理离线登录按钮点击事件
     */
    @FXML
    private void handleOfflineLogin(ActionEvent event) {
        String username = usernameField.getText();
        if (username.isEmpty()) {
            showAlert("登录失败", "请输入用户名", Alert.AlertType.ERROR);
            return;
        }
        
        try {
            // 使用AccountManager创建离线账户
            var account = accountManager.createOfflineAccount(username);
            accountManager.setSelectedAccount(account);
            
            statusLabel.setText("离线模式已激活。欢迎，" + username + "");
            launchButton.setDisable(false);
        } catch (Exception e) {
            LOGGER.error("离线登录失败", e);
            showAlert("登录失败", "离线登录失败: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    /**
     * 处理启动游戏按钮点击事件
     */
    @FXML
    private void handleLaunchGame(ActionEvent event) {
        statusLabel.setText("正在启动游戏...");
        launchButton.setDisable(true);
        progressBar.setVisible(true);
        
        // 模拟游戏启动过程
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                for (int i = 0; i <= 100; i++) {
                    updateProgress(i, 100);
                    Thread.sleep(50); // 模拟加载过程
                }
                return null;
            }

            @Override
            protected void succeeded() {
                statusLabel.setText("游戏启动成功！");
                progressBar.setVisible(false);
                launchButton.setDisable(false);
                // 实际实现时应该启动Minecraft进程
            }
        };
        
        progressBar.progressProperty().bind(task.progressProperty());
        new Thread(task).start();
    }

    /**
     * 处理刷新版本列表按钮点击事件
     */
    @FXML
    private void handleRefreshVersions(ActionEvent event) {
        loadVersions();
    }

    /**
     * 处理下载版本按钮点击事件
     */
    @FXML
    private void handleDownloadVersion(ActionEvent event) {
        String selectedVersion = versionComboBox.getSelectionModel().getSelectedItem();
        if (selectedVersion == null) {
            showAlert("下载失败", "请选择要下载的版本", Alert.AlertType.ERROR);
            return;
        }
        
        statusLabel.setText("正在下载版本: " + selectedVersion);
        progressBar.setVisible(true);
        downloadVersionButton.setDisable(true);
        
        // 模拟版本下载过程
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                for (int i = 0; i <= 100; i++) {
                    updateProgress(i, 100);
                    Thread.sleep(100); // 模拟下载过程
                }
                return null;
            }

            @Override
            protected void succeeded() {
                statusLabel.setText("版本下载完成: " + selectedVersion);
                progressBar.setVisible(false);
                downloadVersionButton.setDisable(false);
                // 实际实现时应该将下载的版本添加到已安装版本列表
            }
        };
        
        progressBar.progressProperty().bind(task.progressProperty());
        new Thread(task).start();
    }

    /**
     * 处理删除版本按钮点击事件
     */
    @FXML
    private void handleDeleteVersion(ActionEvent event) {
        String selectedVersion = installedVersionsList.getSelectionModel().getSelectedItem();
        if (selectedVersion == null || selectedVersion.equals("暂无已安装版本")) {
            showAlert("删除失败", "请选择要删除的版本", Alert.AlertType.ERROR);
            return;
        }
        
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("确认删除");
        confirmAlert.setHeaderText("删除版本");
        confirmAlert.setContentText("确定要删除版本: " + selectedVersion + " 吗？");
        
        confirmAlert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                // 实际实现时应该从文件系统删除版本文件
                installedVersionsList.getItems().remove(selectedVersion);
                if (installedVersionsList.getItems().isEmpty()) {
                    installedVersionsList.getItems().add("暂无已安装版本");
                }
                statusLabel.setText("已删除版本: " + selectedVersion);
            }
        });
    }

    /**
     * 处理浏览Java路径按钮点击事件
     */
    @FXML
    private void handleBrowseJava(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("选择Java可执行文件");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("可执行文件", "*.exe"));
        
        File selectedFile = fileChooser.showOpenDialog(RootReference.getScene().getWindow());
        if (selectedFile != null) {
            javaPathField.setText(selectedFile.getAbsolutePath());
        }
    }

    /**
     * 显示警告对话框
     */
    private void showAlert(String title, String content, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}