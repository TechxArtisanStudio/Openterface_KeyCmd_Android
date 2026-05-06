# KeyMod für Android

<p align="center"><strong>Sprache des README</strong> · <em>GitHub zeigt standardmäßig README.md im Repository-Root (Englisch)</em></p>
<p align="center">
<a href="README.md"><img src="https://img.shields.io/badge/English-README-656d76?style=for-the-badge" alt="README in English"/></a>
<a href="README.zh-CN.md"><img src="https://img.shields.io/badge/Chinese%20(Simplified)-README-656d76?style=for-the-badge" alt="简体中文 README"/></a>
<a href="README.zh-TW.md"><img src="https://img.shields.io/badge/繁體中文%28台灣%29-README-656d76?style=for-the-badge" alt="README zh-TW"/></a>
<a href="README.zh-HK.md"><img src="https://img.shields.io/badge/繁體中文%28香港%29-README-656d76?style=for-the-badge" alt="README zh-HK"/></a>
<a href="README.es.md"><img src="https://img.shields.io/badge/Español-README-656d76?style=for-the-badge" alt="README en español"/></a>
<a href="README.fr.md"><img src="https://img.shields.io/badge/Français-README-656d76?style=for-the-badge" alt="README en français"/></a>
<a href="README.de.md"><img src="https://img.shields.io/badge/Deutsch-current-2ea043?style=for-the-badge" alt="Aktuell: Deutsch"/></a>
<a href="README.ja.md"><img src="https://img.shields.io/badge/日本語-README-656d76?style=for-the-badge" alt="日本語 README"/></a>
</p>

---

**KeyMod** ist die Android-Begleit-App zu [Openterface](https://openterface.com/) — eine KVM-ähnliche Hardware-Brücke, mit der du einen Host-PC vom Smartphone per **USB** oder **Bluetooth** steuerst. Dieses Repository enthält die Java/Android-Implementierung (`com.openterface.keymod`).

- **Voraussetzungen:** Android 8.0+ (API 26); USB OTG bei USB-Steuerung  
- **Dokumentation:** Verbindung, Modi und Tastenkürzel in [docs/USER_GUIDE.md](docs/USER_GUIDE.md) (Englisch)  
- **Installation:** Fertige APKs unter [GitHub Releases](https://github.com/TechxArtisanStudio/Openterface_KeyMod_Android/releases) und [GitHub Actions](https://github.com/TechxArtisanStudio/Openterface_KeyMod_Android/actions)-Artefakten  

## Funktionen im Überblick

| Bereich | Inhalt |
|---------|--------|
| **Tastatur & Maus** | Touchpad, vollständiges QWERTY, Modifier, Bearbeitungs-Tastenkürzel, optionale Makrozeile, Ziffernblock-ähnliche Layouts und Puffer für längeren Text zum Senden an den Host |
| **Präsentation** | Presenter-Steuerung z. B. für Google Slides (Timer, vor/zurück, starten, schwarzer Bildschirm, App-Wechsel, Touchpad) |
| **Shortcut-Hub** | Profile mit Kurzbefehlen für Kreativ- und Dev-Tools (z. B. Blender, KiCAD, Photoshop, VS Code) inkl. Erstellen/Import/Export |
| **Gamepad, Makros, Sprache** | Weitere Modi derselben App (siehe Seitenmenü und Benutzerhandbuch) |

## Screenshots

Dateien liegen unter [`demo/`](demo/). Breiten sind per HTML gesetzt; Querformat nutzt mehr Breite.

### Willkommen & Navigation

<table>
<tr>
<td align="center" valign="top" width="50%">
<b>Willkommen — Modus wählen</b><br/>
<small>Tastatur & Maus, Präsentation, Gamepad, Shortcut-Hub u. a.</small><br/><br/>
<img src="demo/demo-welcome-mode-selection.jpg" alt="Willkommensbildschirm" width="300" />
</td>
<td align="center" valign="top" width="50%">
<b>Navigationsleiste</b><br/>
<small>Wechsel zwischen Modi, Makros, Sprache und Einstellungen.</small><br/><br/>
<img src="demo/demo-navigation-drawer.jpg" alt="Seitenmenü" width="300" />
</td>
</tr>
</table>

### Tastatur & Maus

<table>
<tr>
<td align="center" valign="top" colspan="2">
<b>Hochformat — Touchpad-Gesten + Tastatur</b><br/>
<small>Gestenhilfe über der Tastatur (Hinweise ohne verbundenes Gerät möglich).</small><br/><br/>
<img src="demo/demo-keyboard-mouse-portrait-touchpad-gestures.jpg" alt="Gesten und Tastatur Hochformat" width="300" />
</td>
</tr>
<tr>
<td align="center" valign="top" width="50%">
<b>Querformat — geteilte Tastatur + Touchpad</b><br/><br/>
<img src="demo/demo-keyboard-mouse-landscape-split-keyboard-touchpad.jpg" alt="Geteilte Tastatur mit Touchpad" width="420" />
</td>
<td align="center" valign="top" width="50%">
<b>Querformat — Makrozeile + Profile</b><br/>
<small>z. B. Default / KiCAD.</small><br/><br/>
<img src="demo/demo-keyboard-mouse-landscape-macro-strip.jpg" alt="Makros und Profile" width="420" />
</td>
</tr>
<tr>
<td align="center" valign="top" width="50%">
<b>Hochformat — Touchpad + erweiterte Tasten</b><br/><br/>
<img src="demo/demo-keyboard-mouse-portrait-touchpad-numpad.jpg" alt="Touchpad und Tastenfeld" width="300" />
</td>
<td align="center" valign="top" width="50%">
<b>Hochformat — Langtext + Senden</b><br/><br/>
<img src="demo/demo-keyboard-mouse-portrait-long-text-compose.jpg" alt="Langtexteingabe" width="300" />
</td>
</tr>
</table>

### Präsentation

<p align="center">
<b>Google Slides</b> — Timer und große Steuerung (weitere Apps in der oberen Leiste).<br/><br/>
<img src="demo/demo-presentation-google-slides.jpg" alt="Präsentationsfernbedienung" width="440" />
</p>

### Shortcut-Hub

<p align="center">
<b>Shortcut-Hub</b> — Profile und Anzahl der Kurzbefehle.<br/><br/>
<img src="demo/demo-shortcut-hub.jpg" alt="Shortcut-Hub-Liste" width="300" />
</p>

### Spracheingabe

<p align="center">
<b>Spracheingabe</b> — Transkript, Ziele, Verlauf, Mikrofon (API-Schlüssel in den Einstellungen; siehe Handbuch).<br/><br/>
<img src="demo/demo-voice-input.jpg" alt="Spracheingabe" width="300" />
</p>

## Aus dem Quellcode bauen

Projekt in Android Studio (Giraffe oder neuer) öffnen oder per Kommandozeile:

```bash
./gradlew assembleDebug
```

Debug-APK: `app/build/outputs/apk/`.

## Upstream

Hardware und Produkt: [TechxArtisan — Openterface](https://openterface.com/).
