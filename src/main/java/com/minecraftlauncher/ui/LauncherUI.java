package com.minecraftlauncher.ui;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Stage;

/**
 * 启动器用户界面类
 * 负责管理和显示启动器的所有UI组件
 */
public class LauncherUI {

    private TextField usernameField;
    private PasswordField passwordField;
    private Button loginButton;
    private Button launchButton;
    private Label statusLabel;

    /**
     * 初始化启动器用户界面
     * @param primaryStage 主舞台
     */
    public void initializeUI(Stage primaryStage) {
        // 设置窗口标题和大小
        primaryStage.setTitle("Minecraft启动器");
        primaryStage.setWidth(800);
        primaryStage.setHeight(600);
        primaryStage.setResizable(false);

        // 创建根布局
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(20));

        // 创建顶部标题
        Label titleLabel = new Label("Minecraft启动器");
        titleLabel.setFont(Font.font(24));
        VBox topBox = new VBox(titleLabel);
        topBox.setAlignment(javafx.geometry.Pos.CENTER);
        topBox.setPadding(new Insets(20));
        root.setTop(topBox);

        // 创建登录表单
        GridPane loginGrid = new GridPane();
        loginGrid.setHgap(10);
        loginGrid.setVgap(10);
        loginGrid.setPadding(new Insets(20));
        loginGrid.setAlignment(javafx.geometry.Pos.CENTER);

        usernameField = new TextField();
        usernameField.setPromptText("用户名");
        passwordField = new PasswordField();
        passwordField.setPromptText("密码");
        loginButton = new Button("登录");
        launchButton = new Button("启动游戏");
        launchButton.setDisable(true);
        statusLabel = new Label("请先登录");

        loginGrid.add(new Label("用户名:"), 0, 0);
        loginGrid.add(usernameField, 1, 0);
        loginGrid.add(new Label("密码:"), 0, 1);
        loginGrid.add(passwordField, 1, 1);
        loginGrid.add(loginButton, 0, 2);
        loginGrid.add(launchButton, 1, 2);
        loginGrid.add(statusLabel, 0, 3, 2, 1);

        root.setCenter(loginGrid);

        // 添加事件监听器
        setupEventListeners();

        // 创建场景并显示
        Scene scene = new Scene(root);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    /**
     * 设置UI组件的事件监听器
     */
    private void setupEventListeners() {
        loginButton.setOnAction(e -> handleLogin());
        launchButton.setOnAction(e -> handleLaunchGame());
    }

    /**
     * 处理登录按钮点击事件
     */
    private void handleLogin() {
        String username = usernameField.getText();
        String password = passwordField.getText();
        
        if (username.isEmpty() || password.isEmpty()) {
            statusLabel.setText("用户名和密码不能为空");
            return;
        }
        
        statusLabel.setText("正在登录...");
        // 这里将在后续实现中调用账户管理模块进行实际登录
        statusLabel.setText("登录成功");
        launchButton.setDisable(false);
    }

    /**
     * 处理启动游戏按钮点击事件
     */
    private void handleLaunchGame() {
        statusLabel.setText("正在启动游戏...");
        // 这里将在后续实现中调用游戏管理模块启动Minecraft
        statusLabel.setText("游戏启动中...");
    }
}