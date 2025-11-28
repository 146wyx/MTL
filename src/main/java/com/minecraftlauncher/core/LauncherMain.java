package com.minecraftlauncher.core;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Objects;

/**
 * Minecraft启动器主类
 * 程序入口点
 */
public class LauncherMain extends Application {

    private static final Logger LOGGER = LogManager.getLogger(LauncherMain.class);

    @Override
    public void start(Stage primaryStage) {
        LOGGER.info("启动Minecraft启动器");
        try {
            // 加载FXML文件
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/Launcher.fxml"));
            Parent root = loader.load();
            
            // 创建场景并加载CSS样式
            Scene scene = new Scene(root, 800, 600);
            scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/css/launcher.css")).toExternalForm());
            
            // 设置窗口属性
            primaryStage.setTitle("Minecraft启动器");
            primaryStage.setScene(scene);
            primaryStage.setResizable(true);
            
            // 设置窗口图标
            primaryStage.getIcons().add(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/images/icon.png"))));
            
            primaryStage.show();
            
            LOGGER.info("启动器界面初始化完成");
        } catch (IOException e) {
            LOGGER.error("无法加载FXML文件", e);
            showErrorAndExit("无法加载界面", "启动器无法加载界面资源，请检查安装是否完整。" + e.getMessage());
        } catch (Exception e) {
            LOGGER.error("启动器初始化失败", e);
            showErrorAndExit("启动失败", "启动器初始化过程中发生错误: " + e.getMessage());
        }
    }

    private void showErrorAndExit(String title, String message) {
        System.err.println(title + ": " + message);
        try {
            // 尝试显示JavaFX错误对话框
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        } catch (Exception ex) {
            // 如果JavaFX对话框无法显示，则使用标准错误输出
            System.err.println("错误详情: " + ex.getMessage());
        } finally {
            System.exit(1);
        }
    }

    /**
     * 程序主入口
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        LOGGER.info("Minecraft启动器启动中...");
        launch(args);
    }
}