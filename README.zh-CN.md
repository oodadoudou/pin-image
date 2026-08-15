# Pin Image

[English](./README.md) · **简体中文**

Pin Image 是一款完全本地运行的 Android 悬浮参考工具。你可以把图片、拼图、PDF 和 EPUB 电子书固定在其他应用上方，在不离开当前工作的情况下移动、缩放、旋转、编辑图片或继续阅读。

## 主要功能

- **窗口与内容独立控制**：调整悬浮窗口大小不会改变图片比例；平移和缩放内容也不会移动窗口。
- **支持图片、PDF 和 EPUB**：连续阅读文档、快速跳转 PDF 页码、调整 EPUB 字号并恢复上次阅读位置。
- **多图拼图画布**：自由排列、变换、分层、复制和导出多张参考图片。
- **本地图片编辑**：裁切、旋转、翻转、撤销、7 套快捷滤镜以及 13 项手动调节参数。
- **文件库管理**：显示预览和文件名，支持重命名、单项或批量移除；双击才会悬浮，减少误触。
- **隐私优先**：没有网络权限、账户、统计分析、广告或云端上传。

## 下载

需要 Android 11 或更高版本。请前往 [GitHub Releases](https://github.com/oodadoudou/pin-image/releases/latest) 下载最新 APK。

由于 APK 并非通过 Google Play 分发，Android 可能会要求你允许浏览器或文件管理器安装未知来源应用。

## 权限说明

- **在其他应用上层显示**：用于显示悬浮参考内容。
- **无障碍服务**：用于可选的一键截图悬浮按钮。Pin Image 只调用 Android 截图接口，不会分析屏幕内容。
- **通知**：提供隐藏、恢复和关闭全部悬浮内容的快捷操作。
- **Photo Picker**：无需申请完整存储权限即可导入图片。

## 从源码构建

需要 Android SDK 35 和 JDK 17。

```sh
./gradlew :app:assembleDebug
```

APK 会生成在 `app/build/outputs/apk/debug/app-debug.apk`。

## 格式说明

PDF 使用 Android 本地渲染器。EPUB 主要支持无 DRM、可重排版的 EPUB 2/3；带脚本、受 DRM 保护或结构复杂的固定版式电子书可能无法完整显示。

## 许可

Copyright © 2026. All rights reserved.
