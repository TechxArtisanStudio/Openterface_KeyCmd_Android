# KeyMod（Android）

<p align="center"><strong>README の言語</strong> · <em>GitHub のリポジトリトップでは既定でルートの README.md（英語）が表示されます</em></p>
<p align="center">
<a href="README.md"><img src="https://img.shields.io/badge/English-README-656d76?style=for-the-badge" alt="English README"/></a>
<a href="README.zh-CN.md"><img src="https://img.shields.io/badge/Chinese%20(Simplified)-README-656d76?style=for-the-badge" alt="简体中文 README"/></a>
<a href="README.zh-TW.md"><img src="https://img.shields.io/badge/繁體中文%28台灣%29-README-656d76?style=for-the-badge" alt="台湾繁体字 README"/></a>
<a href="README.zh-HK.md"><img src="https://img.shields.io/badge/繁體中文%28香港%29-README-656d76?style=for-the-badge" alt="香港繁體 README"/></a>
<a href="README.es.md"><img src="https://img.shields.io/badge/Español-README-656d76?style=for-the-badge" alt="README en español"/></a>
<a href="README.fr.md"><img src="https://img.shields.io/badge/Français-README-656d76?style=for-the-badge" alt="README en français"/></a>
<a href="README.de.md"><img src="https://img.shields.io/badge/Deutsch-README-656d76?style=for-the-badge" alt="README auf Deutsch"/></a>
<a href="README.ja.md"><img src="https://img.shields.io/badge/日本語-current-2ea043?style=for-the-badge" alt="現在：日本語"/></a>
</p>

---

**KeyMod** は [Openterface](https://openterface.com/) 向けの Android コンパニオンアプリです。KVM 風のハードウェアブリッジを通じ、スマートフォンから **USB** または **Bluetooth** でホスト PC を操作します。本リポジトリは Java/Android 実装（`com.openterface.keymod`）です。

- **要件:** Android 8.0 以上（API 26）。USB 経由で使う場合は USB OTG が必要です。  
- **ドキュメント:** 接続手順・モード・ショートカットは [docs/USER_GUIDE.md](docs/USER_GUIDE.md)（英語）を参照してください。  
- **インストール:** ビルド済み APK は [GitHub Releases](https://github.com/TechxArtisanStudio/Openterface_KeyMod_Android/releases) および [GitHub Actions](https://github.com/TechxArtisanStudio/Openterface_KeyMod_Android/actions) の成果物から入手できます。  

## 機能概要

| 分野 | 内容 |
|------|------|
| **キーボードとマウス** | タッチパッド、フル QWERTY、修飾キー、編集系ショートカット、任意のマクロ行、テンキー風レイアウト、長文入力バッファからホストへ送信 |
| **プレゼン** | Google スライドなど向けの発表者ビュー操作（タイマー、前後へ、開始、ブラックアウト、アプリ切替、タッチパッド） |
| **ショートカット ハブ** | Blender、KiCAD、Photoshop、VS Code など向けショートカットプロファイルの作成／インポート／エクスポート |
| **ゲームパッド・マクロ・音声** | 同一シェル内のその他モード（サイドメニューとユーザーガイド参照） |

## スクリーンショット

画像は [`demo/`](demo/) にあります。GitHub 上で読みやすいよう HTML で幅を指定しています。横長画面はより広い幅にしています。

### ウェルカムとナビ

<table>
<tr>
<td align="center" valign="top" width="50%">
<b>ウェルカム — モード選択</b><br/>
<small>キーボードとマウス、プレゼン、ゲームパッド、ショートカット ハブなど。</small><br/><br/>
<img src="demo/demo-welcome-mode-selection.jpg" alt="ウェルカム画面" width="300" />
</td>
<td align="center" valign="top" width="50%">
<b>ナビゲーションドロワー</b><br/>
<small>モード・マクロ・音声・設定の切替。</small><br/><br/>
<img src="demo/demo-navigation-drawer.jpg" alt="サイドメニュー" width="300" />
</td>
</tr>
</table>

### キーボードとマウス

<table>
<tr>
<td align="center" valign="top" colspan="2">
<b>縦向き — タッチパッドジェスチャ + キーボード</b><br/>
<small>カスタムキーボード上にジェスチャ説明（未接続時はトーストが出ることがあります）。</small><br/><br/>
<img src="demo/demo-keyboard-mouse-portrait-touchpad-gestures.jpg" alt="縦向きジェスチャとキーボード" width="300" />
</td>
</tr>
<tr>
<td align="center" valign="top" width="50%">
<b>横向き — 分割キーボード + タッチパッド</b><br/><br/>
<img src="demo/demo-keyboard-mouse-landscape-split-keyboard-touchpad.jpg" alt="分割キーボードと中央タッチパッド" width="420" />
</td>
<td align="center" valign="top" width="50%">
<b>横向き — マクロ行 + プロファイル</b><br/>
<small>例: Default / KiCAD。</small><br/><br/>
<img src="demo/demo-keyboard-mouse-landscape-macro-strip.jpg" alt="マクロ行とプロファイル" width="420" />
</td>
</tr>
<tr>
<td align="center" valign="top" width="50%">
<b>縦向き — タッチパッド + 拡張キー</b><br/><br/>
<img src="demo/demo-keyboard-mouse-portrait-touchpad-numpad.jpg" alt="タッチパッドとキー配列" width="300" />
</td>
<td align="center" valign="top" width="50%">
<b>縦向き — 長文入力 + 送信</b><br/><br/>
<img src="demo/demo-keyboard-mouse-portrait-long-text-compose.jpg" alt="長文と送信" width="300" />
</td>
</tr>
</table>

### プレゼン

<p align="center">
<b>Google スライド</b> — タイマーと大きな操作ボタン（上部のタブで他アプリも選択可能）。<br/><br/>
<img src="demo/demo-presentation-google-slides.jpg" alt="プレゼンリモコン" width="440" />
</p>

### ショートカット ハブ

<p align="center">
<b>ショートカット ハブ</b> — プロファイルとショートカット数。<br/><br/>
<img src="demo/demo-shortcut-hub.jpg" alt="ショートカット ハブ一覧" width="300" />
</p>

### 音声入力

<p align="center">
<b>音声入力</b> — 書き起こしエリア、送信先、履歴、マイク（API キーは設定から。詳細はユーザーガイド）。<br/><br/>
<img src="demo/demo-voice-input.jpg" alt="音声入力画面" width="300" />
</p>

## ソースからビルド

Android Studio（Giraffe 以降推奨）で開くか、コマンドラインで次を実行します。

```bash
./gradlew assembleDebug
```

デバッグ APK の出力先は Gradle の既定どおり `app/build/outputs/apk/` です。

## アップストリーム

製品・ハードウェアの文脈: [TechxArtisan — Openterface](https://openterface.com/)。
