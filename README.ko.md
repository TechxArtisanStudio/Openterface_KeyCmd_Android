# KeyCmd for Android

<p align="center"><strong>README 언어</strong> · <em>GitHub 저장소 홈에서는 기본적으로 루트의 README.md(영어)가 표시됩니다</em></p>
<p align="center">
<a href="README.md"><img src="https://img.shields.io/badge/English-README-656d76?style=for-the-badge" alt="English README"/></a>
<a href="README.zh-CN.md"><img src="https://img.shields.io/badge/Chinese%20(Simplified)-README-656d76?style=for-the-badge" alt="简体中文 README"/></a>
<a href="README.zh-TW.md"><img src="https://img.shields.io/badge/繁體中文%28台灣%29-README-656d76?style=for-the-badge" alt="台灣繁體 README"/></a>
<a href="README.zh-HK.md"><img src="https://img.shields.io/badge/繁體中文%28香港%29-README-656d76?style=for-the-badge" alt="香港繁體中文 README"/></a>
<a href="README.es.md"><img src="https://img.shields.io/badge/Español-README-656d76?style=for-the-badge" alt="README en español"/></a>
<a href="README.fr.md"><img src="https://img.shields.io/badge/Français-README-656d76?style=for-the-badge" alt="README en français"/></a>
<a href="README.de.md"><img src="https://img.shields.io/badge/Deutsch-README-656d76?style=for-the-badge" alt="README auf Deutsch"/></a>
<a href="README.ja.md"><img src="https://img.shields.io/badge/日本語-README-656d76?style=for-the-badge" alt="日本語 README"/></a>
<a href="README.ko.md"><img src="https://img.shields.io/badge/한국어-current-2ea043?style=for-the-badge" alt="현재: 한국어"/></a>
<a href="README.it.md"><img src="https://img.shields.io/badge/Italiano-README-656d76?style=for-the-badge" alt="README in italiano"/></a>
<a href="README.ru.md"><img src="https://img.shields.io/badge/Русский-README-656d76?style=for-the-badge" alt="README на русском"/></a>
<a href="README.pt-BR.md"><img src="https://img.shields.io/badge/Portugu%C3%AAs%20(Brasil)-README-656d76?style=for-the-badge" alt="README em português (Brasil)"/></a>
</p>

---

**KeyCmd**는 [Openterface](https://openterface.com/)용 Android 동반 앱입니다. KVM 스타일 하드웨어 브리지를 통해 스마트폰에서 **USB** 또는 **Bluetooth**로 호스트 PC를 제어할 수 있습니다. 이 저장소에는 Java/Android 구현(`com.openterface.keymod`)이 포함되어 있습니다.

- **요구 사항:** Android 8.0+(API 26); USB 제어 사용 시 USB OTG 필요  
- **문서:** 연결 단계, 모드, 단축키는 [docs/USER_GUIDE.md](docs/USER_GUIDE.md)(영어) 참조  
- **설치:** 미리 빌드된 APK는 [GitHub Releases](https://github.com/TechxArtisanStudio/Openterface_KeyMod_Android/releases) 및 [GitHub Actions](https://github.com/TechxArtisanStudio/Openterface_KeyMod_Android/actions) 아티팩트에서 제공  

## 기능 요약

| 영역 | 제공 기능 |
|------|-----------|
| **키보드 및 마우스** | 터치패드, 전체 QWERTY, 수정자, 편집 단축키, 선택적 매크로 행, 숫자 키패드형 레이아웃, 긴 텍스트 작성 후 호스트로 전송 |
| **프레젠테이션** | Google Slides 등을 위한 발표자 스타일 컨트롤(타이머, 이전/다음, 발표, 검은 화면, 앱 전환, 터치패드) |
| **단축키 허브** | Blender, KiCAD, Photoshop, VS Code 등을 위한 단축키 프로필 생성/가져오기/내보내기 |
| **게임패드, 매크로, 음성** | 동일한 앱 셸 내 추가 모드(앱 내 서랍 및 사용자 가이드 참조) |

## 스크린샷

에셋은 [`demo/`](demo/)에 있습니다. GitHub에서 읽기 쉽도록 HTML로 너비를 지정했습니다. 가로 화면은 더 넓은 너비를 사용합니다.

### 환영 화면 및 탐색

<table>
<tr>
<td align="center" valign="top" width="50%">
<b>환영 — 모드 선택</b><br/>
<small>키보드 및 마우스, 프레젠테이션, 게임패드, 단축키 허브 등.</small><br/><br/>
<img src="demo/demo-welcome-mode-selection.jpg" alt="환영 화면" width="300" />
</td>
<td align="center" valign="top" width="50%">
<b>탐색 서랍</b><br/>
<small>모드, 매크로, 음성, 설정 간 전환.</small><br/><br/>
<img src="demo/demo-navigation-drawer.jpg" alt="탐색 서랍" width="300" />
</td>
</tr>
</table>

### 키보드 및 마우스

<table>
<tr>
<td align="center" valign="top" colspan="2">
<b>세로 — 터치패드 제스처 + 키보드</b><br/>
<small>사용자 지정 키보드 위의 제스처 도움말(장치 미연결 시 상태 토스트가 표시될 수 있음).</small><br/><br/>
<img src="demo/demo-keyboard-mouse-portrait-touchpad-gestures.jpg" alt="세로 제스처 및 키보드" width="300" />
</td>
</tr>
<tr>
<td align="center" valign="top" width="50%">
<b>가로 — 분할 키보드 + 터치패드</b><br/><br/>
<img src="demo/demo-keyboard-mouse-landscape-split-keyboard-touchpad.jpg" alt="분할 키보드 및 중앙 터치패드" width="420" />
</td>
<td align="center" valign="top" width="50%">
<b>가로 — 매크로 행 + 프로필</b><br/>
<small>예: Default / KiCAD.</small><br/><br/>
<img src="demo/demo-keyboard-mouse-landscape-macro-strip.jpg" alt="매크로 및 프로필" width="420" />
</td>
</tr>
<tr>
<td align="center" valign="top" width="50%">
<b>세로 — 터치패드 + 확장 키</b><br/><br/>
<img src="demo/demo-keyboard-mouse-portrait-touchpad-numpad.jpg" alt="터치패드 및 키 그리드" width="300" />
</td>
<td align="center" valign="top" width="50%">
<b>세로 — 긴 텍스트 + 보내기</b><br/><br/>
<img src="demo/demo-keyboard-mouse-portrait-long-text-compose.jpg" alt="긴 텍스트 작성" width="300" />
</td>
</tr>
</table>

### 프레젠테이션

<p align="center">
<b>Google Slides</b> — 타이머 및 큰 컨트롤(상단 스트립에서 다른 앱 선택 가능).<br/><br/>
<img src="demo/demo-presentation-google-slides.jpg" alt="프레젠테이션 리모컨" width="440" />
</p>

### 단축키 허브

<p align="center">
<b>단축키 허브</b> — 프로필 및 단축키 수.<br/><br/>
<img src="demo/demo-shortcut-hub.jpg" alt="단축키 허브 목록" width="300" />
</p>

### 음성 입력

<p align="center">
<b>음성 입력</b> — 전사 영역, 대상, 기록, 마이크(설정에서 API 키 구성; 사용자 가이드 참조).<br/><br/>
<img src="demo/demo-voice-input.jpg" alt="음성 입력 화면" width="300" />
</p>

## 소스에서 빌드

Android Studio(Giraffe 이상 권장)에서 프로젝트를 열거나 명령줄에서 빌드합니다.

```bash
./gradlew assembleDebug
```

디버그 APK 출력 경로는 Gradle 기본 레이아웃 `app/build/outputs/apk/`입니다.

## 업스트림

하드웨어 및 제품 정보: [TechxArtisan — Openterface](https://openterface.com/).
