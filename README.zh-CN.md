# KeyMod（Android）

<p align="center"><strong>README 语言</strong> · <em>GitHub 仓库首页默认展示根目录的 README.md（英文）</em></p>
<p align="center">
<a href="README.md"><img src="https://img.shields.io/badge/English-README-656d76?style=for-the-badge" alt="English README"/></a>
<a href="README.zh-CN.md"><img src="https://img.shields.io/badge/简体中文-current-2ea043?style=for-the-badge" alt="当前：简体中文"/></a>
<a href="README.zh-TW.md"><img src="https://img.shields.io/badge/繁體中文%28台灣%29-README-656d76?style=for-the-badge" alt="台湾繁体 README"/></a>
<a href="README.zh-HK.md"><img src="https://img.shields.io/badge/繁體中文%28香港%29-README-656d76?style=for-the-badge" alt="香港繁體中文 README"/></a>
<a href="README.es.md"><img src="https://img.shields.io/badge/Español-README-656d76?style=for-the-badge" alt="README en español"/></a>
<a href="README.fr.md"><img src="https://img.shields.io/badge/Français-README-656d76?style=for-the-badge" alt="README en français"/></a>
<a href="README.de.md"><img src="https://img.shields.io/badge/Deutsch-README-656d76?style=for-the-badge" alt="README auf Deutsch"/></a>
<a href="README.ja.md"><img src="https://img.shields.io/badge/日本語-README-656d76?style=for-the-badge" alt="日本語 README"/></a>
</p>

---

**KeyMod** 是 [Openterface](https://openterface.com/) 的 Android 配套应用——通过硬件 KVM 桥，用手机以 **USB** 或 **蓝牙** 控制主机。本仓库为 Java/Android 实现（`com.openterface.keymod`）。

- **系统要求：** Android 8.0+（API 26）；使用 USB 控制时需 USB OTG  
- **文档：** 连接步骤、模式与快捷键见 [docs/USER_GUIDE.md](docs/USER_GUIDE.md)（英文）  
- **安装：** 预编译 APK 见 [GitHub Releases](https://github.com/TechxArtisanStudio/Openterface_KeyMod_Android/releases) 与 [GitHub Actions](https://github.com/TechxArtisanStudio/Openterface_KeyMod_Android/actions) 构建产物  

## 功能概览

| 模块 | 说明 |
|------|------|
| **键盘与鼠标** | 触控板、完整 QWERTY、修饰键、编辑类快捷键、可选宏行、类数字键盘布局，以及长文本编辑后一键发送到主机 |
| **演示** | 面向幻灯类应用（如 Google 幻灯片）的演讲者控制：计时、翻页、放映、黑屏、切换应用、触控板等 |
| **快捷方式中心** | 面向设计/开发等软件的快捷方式配置包（如 Blender、KiCAD、Photoshop、VS Code），支持创建/导入/导出 |
| **手柄、宏、语音** | 同一应用壳内的其他模式（见侧栏抽屉与用户指南） |

## 截图

资源位于 [`demo/`](demo/)。以下使用 HTML 指定宽度，便于在 GitHub 上阅读；横屏界面使用更大宽度。

### 欢迎与导航

<table>
<tr>
<td align="center" valign="top" width="50%">
<b>欢迎 — 选择模式</b><br/>
<small>键盘与鼠标、演示、手柄、快捷方式中心等。</small><br/><br/>
<img src="demo/demo-welcome-mode-selection.jpg" alt="欢迎界面与模式卡片" width="300" />
</td>
<td align="center" valign="top" width="50%">
<b>导航抽屉</b><br/>
<small>在模式、宏、语音与设置之间切换。</small><br/><br/>
<img src="demo/demo-navigation-drawer.jpg" alt="侧栏模式列表" width="300" />
</td>
</tr>
</table>

### 键盘与鼠标

<table>
<tr>
<td align="center" valign="top" colspan="2">
<b>竖屏 — 触控板手势说明 + 键盘</b><br/>
<small>自定义键盘上方为手势说明（未连接设备时可能出现状态提示）。</small><br/><br/>
<img src="demo/demo-keyboard-mouse-portrait-touchpad-gestures.jpg" alt="竖屏手势说明与键盘" width="300" />
</td>
</tr>
<tr>
<td align="center" valign="top" width="50%">
<b>横屏 — 分体键盘 + 触控板</b><br/><br/>
<img src="demo/demo-keyboard-mouse-landscape-split-keyboard-touchpad.jpg" alt="横屏分体键盘与中央触控板" width="420" />
</td>
<td align="center" valign="top" width="50%">
<b>横屏 — 宏行 + 配置</b><br/>
<small>例如 Default / KiCAD。</small><br/><br/>
<img src="demo/demo-keyboard-mouse-landscape-macro-strip.jpg" alt="横屏宏条与配置选择" width="420" />
</td>
</tr>
<tr>
<td align="center" valign="top" width="50%">
<b>竖屏 — 触控板 + 扩展键盘区</b><br/><br/>
<img src="demo/demo-keyboard-mouse-portrait-touchpad-numpad.jpg" alt="竖屏触控板与键区" width="300" />
</td>
<td align="center" valign="top" width="50%">
<b>竖屏 — 长文本编辑 + 发送</b><br/><br/>
<img src="demo/demo-keyboard-mouse-portrait-long-text-compose.jpg" alt="竖屏长文本与发送" width="300" />
</td>
</tr>
</table>

### 演示

<p align="center">
<b>Google 幻灯片</b> — 计时与大按钮控制（顶部条可切换其他应用）。<br/><br/>
<img src="demo/demo-presentation-google-slides.jpg" alt="Google 幻灯片演示遥控" width="440" />
</p>

### 快捷方式中心

<p align="center">
<b>快捷方式中心</b> — 配置包与快捷键数量。<br/><br/>
<img src="demo/demo-shortcut-hub.jpg" alt="快捷方式中心列表" width="300" />
</p>

### 语音输入

<p align="center">
<b>语音输入</b> — 转写区、目标平台、历史与麦克风（API 密钥请在设置中配置，详见用户指南）。<br/><br/>
<img src="demo/demo-voice-input.jpg" alt="语音输入界面" width="300" />
</p>

## 从源码构建

使用 Android Studio（建议 Giraffe 或更新版本），或命令行：

```bash
./gradlew assembleDebug
```

调试 APK 位于 Gradle 默认输出路径：`app/build/outputs/apk/`。

## 上游与产品

硬件与产品背景：[TechxArtisan — Openterface](https://openterface.com/)。
