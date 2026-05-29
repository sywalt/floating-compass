# 🧭 悬浮指南针 (Floating Compass)

一个 Android 全局悬浮指南针 App，可在任何应用上层显示实时方向。

## ✨ 功能特性

- 🌐 **全局悬浮**：在任何 App 上层显示，不影响正常使用
- 👆 **自由拖动**：可拖动到屏幕任意位置
- 🧭 **实时方向**：融合加速度计 + 磁力计，平滑显示方位
- 📍 **角度 + 方向**：同时显示 0-360° 数值和中英文方向名
- 🔴 **红北白南**：经典指南针配色，一眼识别

## 📦 通过 GitHub Actions 编译

1. **Fork 本仓库** 到你自己的 GitHub 账号
2. 进入你的仓库 → **Actions** 标签页
3. 点击左侧 **Build APK** 工作流
4. 点击 **Run workflow** → **Run workflow**
5. 等待约 3-5 分钟编译完成
6. 在 Actions 运行结果页面下载 **Artifacts**：
   - `floating-compass-debug` → 调试版 APK（可直接安装）
   - `floating-compass-release` → 发布版（未签名）

## 📲 安装方法

1. 下载 `floating-compass-debug.zip`，解压得到 `app-debug.apk`
2. 将 APK 传到手机（微信/QQ/数据线均可）
3. 打开文件，允许"安装未知来源应用"
4. 安装完成后打开 App
5. 点击"启动悬浮指南针"，按提示授予**悬浮窗权限**
6. 返回主界面点击启动，然后切换到任意 App 即可看到悬浮指南针

## 🛠️ 本地编译

需要：Android Studio / JDK 17 / Android SDK

```bash
git clone https://github.com/你的用户名/floating-compass.git
cd floating-compass

# 如果 gradle-wrapper.jar 缺失，先获取它：
./gradlew wrapper --gradle-version=8.4

# 编译调试版
./gradlew assembleDebug

# APK 输出路径
# app/build/outputs/apk/debug/app-debug.apk
```

## 📋 权限说明

| 权限 | 用途 |
|------|------|
| `SYSTEM_ALERT_WINDOW` | 显示全局悬浮窗（必需） |
| `android.hardware.sensor.compass` | 使用磁力计传感器（必需） |

## 📱 系统要求

- Android 6.0 (API 23) 及以上
- 需要磁力计传感器（绝大多数手机均具备）

## 🏗️ 项目结构

```
floating-compass/
├── .github/workflows/build.yml   # GitHub Actions 编译工作流
├── app/
│   ├── src/main/
│   │   ├── java/com/example/floatingcompass/
│   │   │   ├── MainActivity.kt           # 主界面
│   │   │   └── FloatingCompassService.kt # 悬浮窗服务
│   │   ├── res/
│   │   │   ├── layout/activity_main.xml
│   │   │   ├── layout/floating_compass.xml
│   │   │   └── drawable/...
│   │   └── AndroidManifest.xml
│   └── build.gradle
└── build.gradle
```

## 📄 License

MIT License
