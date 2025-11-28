# Minecraft启动器

一个使用Java 21开发的Minecraft启动器。

## 功能特点
- 与Minecraft官方API通信
- 游戏下载、更新与启动
- 多账户管理
- 现代化用户界面

## 技术栈
- Java 21
- JavaFX
- Maven
- Jackson (JSON处理)
- OkHttp (HTTP客户端)

## 项目结构
```
src/main/java/com/minecraftlauncher/
├── core/       # 核心功能和主类
├── api/        # Minecraft API相关功能
├── ui/         # 用户界面
├── utils/      # 工具类
├── game/       # 游戏管理相关
└── accounts/   # 账户管理
```

## 构建与运行
```bash
mvn clean package
java -jar target/minecraft-launcher-1.0-SNAPSHOT-jar-with-dependencies.jar
```