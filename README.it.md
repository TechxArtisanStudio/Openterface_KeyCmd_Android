# KeyCmd per Android

<p align="center"><strong>Lingua del README</strong> · <em>GitHub mostra README.md nella root per impostazione predefinita (inglese)</em></p>
<p align="center">
<a href="README.md"><img src="https://img.shields.io/badge/English-README-656d76?style=for-the-badge" alt="README in inglese"/></a>
<a href="README.zh-CN.md"><img src="https://img.shields.io/badge/Chinese%20(Simplified)-README-656d76?style=for-the-badge" alt="简体中文 README"/></a>
<a href="README.zh-TW.md"><img src="https://img.shields.io/badge/繁體中文%28台灣%29-README-656d76?style=for-the-badge" alt="README zh-TW"/></a>
<a href="README.zh-HK.md"><img src="https://img.shields.io/badge/繁體中文%28香港%29-README-656d76?style=for-the-badge" alt="README zh-HK"/></a>
<a href="README.es.md"><img src="https://img.shields.io/badge/Español-README-656d76?style=for-the-badge" alt="README en español"/></a>
<a href="README.fr.md"><img src="https://img.shields.io/badge/Français-README-656d76?style=for-the-badge" alt="README en français"/></a>
<a href="README.de.md"><img src="https://img.shields.io/badge/Deutsch-README-656d76?style=for-the-badge" alt="README auf Deutsch"/></a>
<a href="README.ja.md"><img src="https://img.shields.io/badge/日本語-README-656d76?style=for-the-badge" alt="日本語 README"/></a>
<a href="README.ko.md"><img src="https://img.shields.io/badge/한국어-README-656d76?style=for-the-badge" alt="한국어 README"/></a>
<a href="README.it.md"><img src="https://img.shields.io/badge/Italiano-current-2ea043?style=for-the-badge" alt="Attuale: italiano"/></a>
<a href="README.ru.md"><img src="https://img.shields.io/badge/Русский-README-656d76?style=for-the-badge" alt="README на русском"/></a>
<a href="README.pt-BR.md"><img src="https://img.shields.io/badge/Portugu%C3%AAs%20(Brasil)-README-656d76?style=for-the-badge" alt="README em português (Brasil)"/></a>
</p>

---

**KeyCmd** è l’app Android companion di [Openterface](https://openterface.com/): un ponte hardware in stile KVM che consente di controllare un PC host dallo smartphone tramite **USB** o **Bluetooth**. Questo repository contiene l’implementazione Java/Android (`com.openterface.keymod`).

- **Requisiti:** Android 8.0+ (API 26); USB OTG se usi il controllo USB  
- **Documentazione:** passaggi di connessione, modalità e scorciatoie in [docs/USER_GUIDE.md](docs/USER_GUIDE.md) (inglese)  
- **Installazione:** APK precompilati su [GitHub Releases](https://github.com/TechxArtisanStudio/Openterface_KeyMod_Android/releases) e artefatti [GitHub Actions](https://github.com/TechxArtisanStudio/Openterface_KeyMod_Android/actions)  

## Funzioni principali

| Area | Cosa offre |
|------|------------|
| **Tastiera e mouse** | Touchpad, QWERTY completo, modificatori, scorciatoie di modifica, riga macro opzionale, layout tipo tastierino numerico e buffer per testo lungo da inviare all’host |
| **Presentazione** | Controlli da presentatore per app come Google Slides (timer, prec/succ, presenta, schermo nero, cambio app, touchpad) |
| **Hub scorciatoie** | Profili di scorciatoie per strumenti creativi e di sviluppo (es. Blender, KiCAD, Photoshop, VS Code) con creazione/import/export |
| **Gamepad, macro, voce** | Modalità aggiuntive nella stessa app (vedi il menu laterale e la guida utente) |

## Screenshot

I file sono in [`demo/`](demo/). La larghezza è impostata in HTML per una lettura comoda su GitHub; le viste orizzontali usano più larghezza.

### Benvenuto e navigazione

<table>
<tr>
<td align="center" valign="top" width="50%">
<b>Benvenuto — scegli una modalità</b><br/>
<small>Tastiera e mouse, presentazione, gamepad, hub scorciatoie, ecc.</small><br/><br/>
<img src="demo/demo-welcome-mode-selection.jpg" alt="Schermata di benvenuto" width="300" />
</td>
<td align="center" valign="top" width="50%">
<b>Menu laterale</b><br/>
<small>Passa tra modalità, macro, voce e impostazioni.</small><br/><br/>
<img src="demo/demo-navigation-drawer.jpg" alt="Menu di navigazione" width="300" />
</td>
</tr>
</table>

### Tastiera e mouse

<table>
<tr>
<td align="center" valign="top" colspan="2">
<b>Verticale — gesti touchpad + tastiera</b><br/>
<small>Aiuto gesti sopra la tastiera personalizzata (possibili avvisi senza dispositivo connesso).</small><br/><br/>
<img src="demo/demo-keyboard-mouse-portrait-touchpad-gestures.jpg" alt="Gesti e tastiera verticale" width="300" />
</td>
</tr>
<tr>
<td align="center" valign="top" width="50%">
<b>Orizzontale — tastiera divisa + touchpad</b><br/><br/>
<img src="demo/demo-keyboard-mouse-landscape-split-keyboard-touchpad.jpg" alt="Tastiera divisa e touchpad centrale" width="420" />
</td>
<td align="center" valign="top" width="50%">
<b>Orizzontale — riga macro + profili</b><br/>
<small>es. Default / KiCAD.</small><br/><br/>
<img src="demo/demo-keyboard-mouse-landscape-macro-strip.jpg" alt="Macro e profili" width="420" />
</td>
</tr>
<tr>
<td align="center" valign="top" width="50%">
<b>Verticale — touchpad + tastiera estesa</b><br/><br/>
<img src="demo/demo-keyboard-mouse-portrait-touchpad-numpad.jpg" alt="Touchpad e griglia tasti" width="300" />
</td>
<td align="center" valign="top" width="50%">
<b>Verticale — testo lungo + Invia</b><br/><br/>
<img src="demo/demo-keyboard-mouse-portrait-long-text-compose.jpg" alt="Composizione testo lungo" width="300" />
</td>
</tr>
</table>

### Presentazione

<p align="center">
<b>Google Slides</b> — timer e pulsanti grandi (altre app nella barra superiore).<br/><br/>
<img src="demo/demo-presentation-google-slides.jpg" alt="Telecomando presentazione" width="440" />
</p>

### Hub scorciatoie

<p align="center">
<b>Hub scorciatoie</b> — profili e conteggio scorciatoie.<br/><br/>
<img src="demo/demo-shortcut-hub.jpg" alt="Elenco hub scorciatoie" width="300" />
</p>

### Input vocale

<p align="center">
<b>Input vocale</b> — area trascrizione, destinazioni, cronologia, microfono (configura la chiave API in Impostazioni; vedi guida).<br/><br/>
<img src="demo/demo-voice-input.jpg" alt="Schermata voce" width="300" />
</p>

## Compilare dal sorgente

Apri il progetto in Android Studio (Giraffe o più recente) o usa la riga di comando:

```bash
./gradlew assembleDebug
```

APK debug: `app/build/outputs/apk/`.

## Upstream

Contesto hardware e prodotto: [TechxArtisan — Openterface](https://openterface.com/).
