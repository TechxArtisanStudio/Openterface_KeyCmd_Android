# KeyCmd（Android）

<p align="center"><strong>README 語言</strong> · <em>GitHub 儲存庫首頁預設顯示根目錄嘅 README.md（英文）</em></p>
<p align="center">
<a href="README.md"><img src="https://img.shields.io/badge/English-README-656d76?style=for-the-badge" alt="English README"/></a>
<a href="README.zh-CN.md"><img src="https://img.shields.io/badge/Chinese%20(Simplified)-README-656d76?style=for-the-badge" alt="简体中文 README"/></a>
<a href="README.zh-TW.md"><img src="https://img.shields.io/badge/繁體中文%28台灣%29-README-656d76?style=for-the-badge" alt="台灣繁體 README"/></a>
<a href="README.zh-HK.md"><img src="https://img.shields.io/badge/繁體中文%28香港%29-current-2ea043?style=for-the-badge" alt="目前：香港繁體中文"/></a>
<a href="README.es.md"><img src="https://img.shields.io/badge/Español-README-656d76?style=for-the-badge" alt="README en español"/></a>
<a href="README.fr.md"><img src="https://img.shields.io/badge/Français-README-656d76?style=for-the-badge" alt="README en français"/></a>
<a href="README.de.md"><img src="https://img.shields.io/badge/Deutsch-README-656d76?style=for-the-badge" alt="README auf Deutsch"/></a>
<a href="README.ja.md"><img src="https://img.shields.io/badge/日本語-README-656d76?style=for-the-badge" alt="日本語 README"/></a>
<a href="README.ko.md"><img src="https://img.shields.io/badge/한국어-README-656d76?style=for-the-badge" alt="한국어 README"/></a>
<a href="README.it.md"><img src="https://img.shields.io/badge/Italiano-README-656d76?style=for-the-badge" alt="README in italiano"/></a>
<a href="README.ru.md"><img src="https://img.shields.io/badge/Русский-README-656d76?style=for-the-badge" alt="README на русском"/></a>
<a href="README.pt-BR.md"><img src="https://img.shields.io/badge/Portugu%C3%AAs%20(Brasil)-README-656d76?style=for-the-badge" alt="README em português (Brasil)"/></a>
</p>

---

**KeyCmd** 係 [Openterface](https://openterface.com/) 嘅 Android 配套應用程式——透過類 KVM **硬件**橋接，用手機經 **USB** 或者 **藍牙** 控制主機電腦。本儲存庫為 Java／Android 實作（`com.openterface.keymod`）。

- **系統需求：** Android 8.0+（API 26）；使用 USB 控制時需要 USB OTG  
- **文件：** 連接步驟、模式同快捷鍵見 [docs/USER_GUIDE.md](docs/USER_GUIDE.md)（英文）  
- **安裝：** 預先編譯嘅 APK 見 [GitHub Releases](https://github.com/TechxArtisanStudio/Openterface_KeyMod_Android/releases) 同 [GitHub Actions](https://github.com/TechxArtisanStudio/Openterface_KeyMod_Android/actions) 構建產物  

## 功能概覽

| 區塊 | 說明 |
|------|------|
| **鍵盤同滑鼠** | 觸控板、完整 QWERTY、修飾鍵、編輯快捷鍵、可選巨集列、類似數字鍵盤版面，以及長文編輯之後傳送到主機 |
| **簡報** | 針對投影片應用（例如 Google 簡報）嘅簡報者控制：計時、上一頁／下一頁、播放、黑畫面、切換 App、觸控板等 |
| **捷徑中心** | 設計／開發等**軟件**嘅捷徑設定組（例如 Blender、KiCAD、Photoshop、VS Code），支援建立／匯入／匯出 |
| **手掣、巨集、語音** | 同一個 App 架構底下嘅其他模式（見側邊欄選單同使用手冊） |

## 螢幕截圖

資源位於 [`demo/`](demo/)。以下用 HTML 指定寬度，喺 GitHub 上面較易閱讀；橫向畫面用較大寬度。

### 歡迎畫面同導航

<table>
<tr>
<td align="center" valign="top" width="50%">
<b>歡迎 — 選擇模式</b><br/>
<small>鍵盤同滑鼠、簡報、手掣、捷徑中心等。</small><br/><br/>
<img src="demo/demo-welcome-mode-selection.jpg" alt="歡迎畫面同模式卡片" width="300" />
</td>
<td align="center" valign="top" width="50%">
<b>導航側欄</b><br/>
<small>喺模式、巨集、語音同設定之間切換。</small><br/><br/>
<img src="demo/demo-navigation-drawer.jpg" alt="側邊模式清單" width="300" />
</td>
</tr>
</table>

### 鍵盤同滑鼠

<table>
<tr>
<td align="center" valign="top" colspan="2">
<b>直向 — 觸控板手勢說明 + 鍵盤</b><br/>
<small>自訂鍵盤上面係手勢說明（尚未連接裝置時可能出現狀態提示）。</small><br/><br/>
<img src="demo/demo-keyboard-mouse-portrait-touchpad-gestures.jpg" alt="直向手勢說明同鍵盤" width="300" />
</td>
</tr>
<tr>
<td align="center" valign="top" width="50%">
<b>橫向 — 分離鍵盤 + 觸控板</b><br/><br/>
<img src="demo/demo-keyboard-mouse-landscape-split-keyboard-touchpad.jpg" alt="橫向分離鍵盤同中央觸控板" width="420" />
</td>
<td align="center" valign="top" width="50%">
<b>橫向 — 巨集列 + 設定檔</b><br/>
<small>例如 Default／KiCAD。</small><br/><br/>
<img src="demo/demo-keyboard-mouse-landscape-macro-strip.jpg" alt="橫向巨集列同設定檔" width="420" />
</td>
</tr>
<tr>
<td align="center" valign="top" width="50%">
<b>直向 — 觸控板 + 擴充鍵區</b><br/><br/>
<img src="demo/demo-keyboard-mouse-portrait-touchpad-numpad.jpg" alt="直向觸控板同鍵區" width="300" />
</td>
<td align="center" valign="top" width="50%">
<b>直向 — 長文編輯 + 傳送</b><br/><br/>
<img src="demo/demo-keyboard-mouse-portrait-long-text-compose.jpg" alt="直向長文同傳送" width="300" />
</td>
</tr>
</table>

### 簡報

<p align="center">
<b>Google 簡報</b> — 計時同大型控制掣（頂部列可以切換其他 App）。<br/><br/>
<img src="demo/demo-presentation-google-slides.jpg" alt="Google 簡報遙控" width="440" />
</p>

### 捷徑中心

<p align="center">
<b>捷徑中心</b> — 設定檔同捷徑數量。<br/><br/>
<img src="demo/demo-shortcut-hub.jpg" alt="捷徑中心清單" width="300" />
</p>

### 語音輸入

<p align="center">
<b>語音輸入</b> — 轉寫區、目標平台、紀錄同咪高峰（API 金鑰請喺設定入面配置，詳情見使用手冊）。<br/><br/>
<img src="demo/demo-voice-input.jpg" alt="語音輸入畫面" width="300" />
</p>

## 從原始碼編譯

使用 Android Studio（建議 Giraffe 或更新版本），或者命令列：

```bash
./gradlew assembleDebug
```

除錯版 APK 位於 Gradle 預設輸出路徑：`app/build/outputs/apk/`。

## 上游同產品

硬件同產品背景：[TechxArtisan — Openterface](https://openterface.com/)。
