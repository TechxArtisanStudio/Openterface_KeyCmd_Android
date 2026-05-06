# KeyMod（Android）

<p align="center"><strong>README 語言</strong> · <em>GitHub 存放庫首頁預設顯示根目錄的 README.md（英文）</em></p>
<p align="center">
<a href="README.md"><img src="https://img.shields.io/badge/English-README-656d76?style=for-the-badge" alt="English README"/></a>
<a href="README.zh-CN.md"><img src="https://img.shields.io/badge/Chinese%20(Simplified)-README-656d76?style=for-the-badge" alt="简体中文 README"/></a>
<a href="README.zh-TW.md"><img src="https://img.shields.io/badge/繁體中文%28台灣%29-current-2ea043?style=for-the-badge" alt="目前：台灣繁體中文"/></a>
<a href="README.zh-HK.md"><img src="https://img.shields.io/badge/繁體中文%28香港%29-README-656d76?style=for-the-badge" alt="香港繁體中文 README"/></a>
<a href="README.es.md"><img src="https://img.shields.io/badge/Español-README-656d76?style=for-the-badge" alt="README en español"/></a>
<a href="README.fr.md"><img src="https://img.shields.io/badge/Français-README-656d76?style=for-the-badge" alt="README en français"/></a>
<a href="README.de.md"><img src="https://img.shields.io/badge/Deutsch-README-656d76?style=for-the-badge" alt="README auf Deutsch"/></a>
<a href="README.ja.md"><img src="https://img.shields.io/badge/日本語-README-656d76?style=for-the-badge" alt="日本語 README"/></a>
</p>

---

**KeyMod** 是 [Openterface](https://openterface.com/) 的 Android 配套 App——透過類 KVM 硬體橋接，用手機以 **USB** 或 **藍牙** 控制主機電腦。本儲存庫為 Java／Android 實作（`com.openterface.keymod`）。

- **系統需求：** Android 8.0+（API 26）；使用 USB 控制時需 USB OTG  
- **文件：** 連線步驟、模式與快捷鍵見 [docs/USER_GUIDE.md](docs/USER_GUIDE.md)（英文）  
- **安裝：** 預先編譯 APK 見 [GitHub Releases](https://github.com/TechxArtisanStudio/Openterface_KeyMod_Android/releases) 與 [GitHub Actions](https://github.com/TechxArtisanStudio/Openterface_KeyMod_Android/actions) 產物  

## 功能概覽

| 區塊 | 說明 |
|------|------|
| **鍵盤與滑鼠** | 觸控板、完整 QWERTY、修飾鍵、編輯快捷鍵、可選巨集列、類數字鍵盤版面，以及長文編輯後傳送到主機 |
| **簡報** | 針對投影片應用（如 Google 簡報）的簡報者控制：計時、上／下一頁、播放、黑畫面、切換 App、觸控板等 |
| **捷徑中心** | 設計／開發等軟體的捷徑設定組（如 Blender、KiCAD、Photoshop、VS Code），支援建立／匯入／匯出 |
| **手把、巨集、語音** | 同一 App 架構下的其他模式（見側邊選單與使用手冊） |

## 螢幕截圖

資源位於 [`demo/`](demo/)。以下使用 HTML 指定寬度，在 GitHub 上較易閱讀；橫向畫面使用較大寬度。

### 歡迎畫面與導覽

<table>
<tr>
<td align="center" valign="top" width="50%">
<b>歡迎 — 選擇模式</b><br/>
<small>鍵盤與滑鼠、簡報、手把、捷徑中心等。</small><br/><br/>
<img src="demo/demo-welcome-mode-selection.jpg" alt="歡迎畫面與模式方塊" width="300" />
</td>
<td align="center" valign="top" width="50%">
<b>導覽抽屜</b><br/>
<small>在模式、巨集、語音與設定之間切換。</small><br/><br/>
<img src="demo/demo-navigation-drawer.jpg" alt="側邊模式清單" width="300" />
</td>
</tr>
</table>

### 鍵盤與滑鼠

<table>
<tr>
<td align="center" valign="top" colspan="2">
<b>直向 — 觸控板手勢說明 + 鍵盤</b><br/>
<small>自訂鍵盤上方為手勢說明（未連線時可能出現狀態提示）。</small><br/><br/>
<img src="demo/demo-keyboard-mouse-portrait-touchpad-gestures.jpg" alt="直向手勢說明與鍵盤" width="300" />
</td>
</tr>
<tr>
<td align="center" valign="top" width="50%">
<b>橫向 — 分離鍵盤 + 觸控板</b><br/><br/>
<img src="demo/demo-keyboard-mouse-landscape-split-keyboard-touchpad.jpg" alt="橫向分離鍵盤與中央觸控板" width="420" />
</td>
<td align="center" valign="top" width="50%">
<b>橫向 — 巨集列 + 設定檔</b><br/>
<small>例如 Default／KiCAD。</small><br/><br/>
<img src="demo/demo-keyboard-mouse-landscape-macro-strip.jpg" alt="橫向巨集列與設定檔" width="420" />
</td>
</tr>
<tr>
<td align="center" valign="top" width="50%">
<b>直向 — 觸控板 + 擴充鍵區</b><br/><br/>
<img src="demo/demo-keyboard-mouse-portrait-touchpad-numpad.jpg" alt="直向觸控板與鍵區" width="300" />
</td>
<td align="center" valign="top" width="50%">
<b>直向 — 長文編輯 + 傳送</b><br/><br/>
<img src="demo/demo-keyboard-mouse-portrait-long-text-compose.jpg" alt="直向長文與傳送" width="300" />
</td>
</tr>
</table>

### 簡報

<p align="center">
<b>Google 簡報</b> — 計時與大型控制鈕（頂部列可切換其他 App）。<br/><br/>
<img src="demo/demo-presentation-google-slides.jpg" alt="Google 簡報遙控" width="440" />
</p>

### 捷徑中心

<p align="center">
<b>捷徑中心</b> — 設定檔與捷徑數量。<br/><br/>
<img src="demo/demo-shortcut-hub.jpg" alt="捷徑中心清單" width="300" />
</p>

### 語音輸入

<p align="center">
<b>語音輸入</b> — 轉寫區、目標平台、紀錄與麥克風（API 金鑰請於設定中配置，詳見使用手冊）。<br/><br/>
<img src="demo/demo-voice-input.jpg" alt="語音輸入畫面" width="300" />
</p>

## 從原始碼建置

使用 Android Studio（建議 Giraffe 或更新），或命令列：

```bash
./gradlew assembleDebug
```

偵錯 APK 位於 Gradle 預設輸出路徑：`app/build/outputs/apk/`。

## 上游與產品

硬體與產品脈絡：[TechxArtisan — Openterface](https://openterface.com/)。
