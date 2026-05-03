# KeyMod para Android

<p align="center"><strong>Idioma del README</strong> · <em>GitHub muestra README.md en la raíz por defecto (inglés)</em></p>
<p align="center">
<a href="README.md"><img src="https://img.shields.io/badge/English-README-656d76?style=for-the-badge" alt="README en inglés"/></a>
<a href="README.zh-CN.md"><img src="https://img.shields.io/badge/Chinese%20(Simplified)-README-656d76?style=for-the-badge" alt="简体中文 README"/></a>
<a href="README.zh-HK.md"><img src="https://img.shields.io/badge/繁體中文%28香港%29-README-656d76?style=for-the-badge" alt="Traditional Chinese (Hong Kong) README"/></a>
<a href="README.es.md"><img src="https://img.shields.io/badge/Español-current-2ea043?style=for-the-badge" alt="Actual: español"/></a>
<a href="README.fr.md"><img src="https://img.shields.io/badge/Français-README-656d76?style=for-the-badge" alt="README en français"/></a>
<a href="README.de.md"><img src="https://img.shields.io/badge/Deutsch-README-656d76?style=for-the-badge" alt="README auf Deutsch"/></a>
<a href="README.ja.md"><img src="https://img.shields.io/badge/日本語-README-656d76?style=for-the-badge" alt="日本語 README"/></a>
</p>

---

**KeyMod** es la app Android complementaria de [Openterface KeyMod](https://openterface.com/): un puente KVM por hardware que permite controlar un PC host desde el teléfono por **USB** o **Bluetooth**. Este repositorio contiene la implementación Java/Android (`com.openterface.keymod`).

- **Requisitos:** Android 8.0+ (API 26); USB OTG si usas control por USB  
- **Documentación:** Pasos de conexión, modos y atajos en [docs/USER_GUIDE.md](docs/USER_GUIDE.md) (inglés)  
- **Instalación:** APK precompilados en [GitHub Releases](https://github.com/TechxArtisanStudio/Openterface_KeyMod_Android/releases) y artefactos de [GitHub Actions](https://github.com/TechxArtisanStudio/Openterface_KeyMod_Android/actions)  

## Funciones principales

| Área | Qué ofrece |
|------|------------|
| **Teclado y ratón** | Panel táctil, QWERTY completo, modificadores, atajos de edición, fila de macros opcional, rejilla tipo teclado numérico y un buffer para redactar texto largo y enviarlo al host |
| **Presentación** | Controles de presentador para apps como Google Slides (temporizador, anterior/siguiente, presentar, pantalla negra, cambiar de app, panel táctil) |
| **Centro de atajos** | Perfiles de atajos para herramientas creativas y de desarrollo (p. ej. Blender, KiCAD, Photoshop, VS Code) con crear/importar/exportar |
| **Mando, macros, voz** | Modos adicionales en la misma app (ver el menú lateral y la guía de usuario) |

## Capturas

Los recursos están en [`demo/`](demo/). El ancho se fija con HTML para que en GitHub se lean bien; las vistas horizontales usan más ancho.

### Bienvenida y navegación

<table>
<tr>
<td align="center" valign="top" width="50%">
<b>Bienvenida — elige un modo</b><br/>
<small>Teclado y ratón, presentación, mando, centro de atajos, etc.</small><br/><br/>
<img src="demo/demo-welcome-mode-selection.jpg" alt="Pantalla de bienvenida" width="300" />
</td>
<td align="center" valign="top" width="50%">
<b>Menú lateral</b><br/>
<small>Cambia entre modos, macros, voz y ajustes.</small><br/><br/>
<img src="demo/demo-navigation-drawer.jpg" alt="Cajón de navegación" width="300" />
</td>
</tr>
</table>

### Teclado y ratón

<table>
<tr>
<td align="center" valign="top" colspan="2">
<b>Vertical — ayuda de gestos del panel + teclado</b><br/>
<small>Ayuda de gestos encima del teclado (pueden aparecer avisos si no hay dispositivo).</small><br/><br/>
<img src="demo/demo-keyboard-mouse-portrait-touchpad-gestures.jpg" alt="Gestos y teclado en vertical" width="300" />
</td>
</tr>
<tr>
<td align="center" valign="top" width="50%">
<b>Horizontal — teclado partido + panel</b><br/><br/>
<img src="demo/demo-keyboard-mouse-landscape-split-keyboard-touchpad.jpg" alt="Teclado partido y panel central" width="420" />
</td>
<td align="center" valign="top" width="50%">
<b>Horizontal — fila de macros + perfiles</b><br/>
<small>p. ej. Default / KiCAD.</small><br/><br/>
<img src="demo/demo-keyboard-mouse-landscape-macro-strip.jpg" alt="Macros y perfiles" width="420" />
</td>
</tr>
<tr>
<td align="center" valign="top" width="50%">
<b>Vertical — panel + teclado extendido</b><br/><br/>
<img src="demo/demo-keyboard-mouse-portrait-touchpad-numpad.jpg" alt="Panel y rejilla de teclas" width="300" />
</td>
<td align="center" valign="top" width="50%">
<b>Vertical — texto largo + Enviar</b><br/><br/>
<img src="demo/demo-keyboard-mouse-portrait-long-text-compose.jpg" alt="Composición de texto largo" width="300" />
</td>
</tr>
</table>

### Presentación

<p align="center">
<b>Google Slides</b> — temporizador y botones grandes (otras apps en la franja superior).<br/><br/>
<img src="demo/demo-presentation-google-slides.jpg" alt="Control de presentación" width="440" />
</p>

### Centro de atajos

<p align="center">
<b>Centro de atajos</b> — perfiles y recuento de atajos.<br/><br/>
<img src="demo/demo-shortcut-hub.jpg" alt="Lista del centro de atajos" width="300" />
</p>

### Entrada por voz

<p align="center">
<b>Entrada por voz</b> — área de transcripción, destinos, historial y micrófono (configura la clave API en Ajustes; ver guía).<br/><br/>
<img src="demo/demo-voice-input.jpg" alt="Pantalla de voz" width="300" />
</p>

## Compilar desde el código

Abre el proyecto en Android Studio (Giraffe o más reciente) o usa la terminal:

```bash
./gradlew assembleDebug
```

El APK de depuración queda en la ruta habitual de Gradle: `app/build/outputs/apk/`.

## Origen del producto

Contexto de hardware y producto: [TechxArtisan — Openterface](https://openterface.com/).
