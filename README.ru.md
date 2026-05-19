# KeyCmd для Android

<p align="center"><strong>Язык README</strong> · <em>На GitHub по умолчанию отображается README.md в корне (английский)</em></p>
<p align="center">
<a href="README.md"><img src="https://img.shields.io/badge/English-README-656d76?style=for-the-badge" alt="README на английском"/></a>
<a href="README.zh-CN.md"><img src="https://img.shields.io/badge/Chinese%20(Simplified)-README-656d76?style=for-the-badge" alt="简体中文 README"/></a>
<a href="README.zh-TW.md"><img src="https://img.shields.io/badge/繁體中文%28台灣%29-README-656d76?style=for-the-badge" alt="README zh-TW"/></a>
<a href="README.zh-HK.md"><img src="https://img.shields.io/badge/繁體中文%28香港%29-README-656d76?style=for-the-badge" alt="README zh-HK"/></a>
<a href="README.es.md"><img src="https://img.shields.io/badge/Español-README-656d76?style=for-the-badge" alt="README en español"/></a>
<a href="README.fr.md"><img src="https://img.shields.io/badge/Français-README-656d76?style=for-the-badge" alt="README en français"/></a>
<a href="README.de.md"><img src="https://img.shields.io/badge/Deutsch-README-656d76?style=for-the-badge" alt="README auf Deutsch"/></a>
<a href="README.ja.md"><img src="https://img.shields.io/badge/日本語-README-656d76?style=for-the-badge" alt="日本語 README"/></a>
<a href="README.ko.md"><img src="https://img.shields.io/badge/한국어-README-656d76?style=for-the-badge" alt="한국어 README"/></a>
<a href="README.it.md"><img src="https://img.shields.io/badge/Italiano-README-656d76?style=for-the-badge" alt="README in italiano"/></a>
<a href="README.ru.md"><img src="https://img.shields.io/badge/Русский-current-2ea043?style=for-the-badge" alt="Текущий: русский"/></a>
<a href="README.pt-BR.md"><img src="https://img.shields.io/badge/Portugu%C3%AAs%20(Brasil)-README-656d76?style=for-the-badge" alt="README em português (Brasil)"/></a>
</p>

---

**KeyCmd** — Android-приложение-компаньон для [Openterface](https://openterface.com/): аппаратный KVM-мост, позволяющий управлять хост-компьютером со смартфона по **USB** или **Bluetooth**. Этот репозиторий содержит реализацию на Java/Android (`com.openterface.keymod`).

- **Требования:** Android 8.0+ (API 26); USB OTG при управлении по USB  
- **Документация:** подключение, режимы и сочетания клавиш — в [docs/USER_GUIDE.md](docs/USER_GUIDE.md) (английский)  
- **Установка:** готовые APK — на [GitHub Releases](https://github.com/TechxArtisanStudio/Openterface_KeyMod_Android/releases) и в артефактах [GitHub Actions](https://github.com/TechxArtisanStudio/Openterface_KeyMod_Android/actions)  

## Основные функции

| Область | Возможности |
|---------|-------------|
| **Клавиатура и мышь** | Сенсорная панель, полная QWERTY, модификаторы, сочетания для редактирования, необязательная строка макросов, раскладка в стиле цифровой клавиатуры и буфер для длинного текста с отправкой на хост |
| **Презентация** | Управление в стиле докладчика для приложений вроде Google Slides (таймер, назад/вперёд, показ, чёрный экран, переключение приложений, сенсорная панель) |
| **Shortcut Hub** | Профили сочетаний для творческих и dev-инструментов (например Blender, KiCAD, Photoshop, VS Code) с созданием/импортом/экспортом |
| **Геймпад, макросы, голос** | Дополнительные режимы в том же приложении (см. боковое меню и руководство пользователя) |

## Скриншоты

Файлы находятся в [`demo/`](demo/). Ширина задана через HTML для удобного просмотра на GitHub; альбомные снимки шире.

### Приветствие и навигация

<table>
<tr>
<td align="center" valign="top" width="50%">
<b>Приветствие — выбор режима</b><br/>
<small>Клавиатура и мышь, презентация, геймпад, Shortcut Hub и др.</small><br/><br/>
<img src="demo/demo-welcome-mode-selection.jpg" alt="Экран приветствия" width="300" />
</td>
<td align="center" valign="top" width="50%">
<b>Боковое меню</b><br/>
<small>Переключение режимов, макросов, голоса и настроек.</small><br/><br/>
<img src="demo/demo-navigation-drawer.jpg" alt="Меню навигации" width="300" />
</td>
</tr>
</table>

### Клавиатура и мышь

<table>
<tr>
<td align="center" valign="top" colspan="2">
<b>Портрет — жесты сенсорной панели + клавиатура</b><br/>
<small>Подсказки по жестам над пользовательской клавиатурой (без подключённого устройства могут появляться уведомления).</small><br/><br/>
<img src="demo/demo-keyboard-mouse-portrait-touchpad-gestures.jpg" alt="Жесты и клавиатура в портрете" width="300" />
</td>
</tr>
<tr>
<td align="center" valign="top" width="50%">
<b>Альбом — разделённая клавиатура + сенсорная панель</b><br/><br/>
<img src="demo/demo-keyboard-mouse-landscape-split-keyboard-touchpad.jpg" alt="Разделённая клавиатура и центральная панель" width="420" />
</td>
<td align="center" valign="top" width="50%">
<b>Альбом — строка макросов + профили</b><br/>
<small>напр. Default / KiCAD.</small><br/><br/>
<img src="demo/demo-keyboard-mouse-landscape-macro-strip.jpg" alt="Макросы и профили" width="420" />
</td>
</tr>
<tr>
<td align="center" valign="top" width="50%">
<b>Портрет — сенсорная панель + расширенные клавиши</b><br/><br/>
<img src="demo/demo-keyboard-mouse-portrait-touchpad-numpad.jpg" alt="Сенсорная панель и сетка клавиш" width="300" />
</td>
<td align="center" valign="top" width="50%">
<b>Портрет — длинный текст + Отправить</b><br/><br/>
<img src="demo/demo-keyboard-mouse-portrait-long-text-compose.jpg" alt="Ввод длинного текста" width="300" />
</td>
</tr>
</table>

### Презентация

<p align="center">
<b>Google Slides</b> — таймер и крупные элементы управления (другие приложения в верхней полосе).<br/><br/>
<img src="demo/demo-presentation-google-slides.jpg" alt="Пульт презентации" width="440" />
</p>

### Shortcut Hub

<p align="center">
<b>Shortcut Hub</b> — профили и количество сочетаний.<br/><br/>
<img src="demo/demo-shortcut-hub.jpg" alt="Список Shortcut Hub" width="300" />
</p>

### Голосовой ввод

<p align="center">
<b>Голосовой ввод</b> — область транскрипции, цели, история, микрофон (настройте API-ключ в Настройках; см. руководство).<br/><br/>
<img src="demo/demo-voice-input.jpg" alt="Экран голосового ввода" width="300" />
</p>

## Сборка из исходников

Откройте проект в Android Studio (Giraffe или новее) или соберите из командной строки:

```bash
./gradlew assembleDebug
```

Отладочный APK: `app/build/outputs/apk/`.

## Upstream

Контекст продукта и оборудования: [TechxArtisan — Openterface](https://openterface.com/).
