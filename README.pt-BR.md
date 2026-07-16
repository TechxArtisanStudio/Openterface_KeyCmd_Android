# KeyCmd para Android

<p align="center"><strong>Idioma do README</strong> · <em>O GitHub exibe README.md na raiz por padrão (inglês)</em></p>
<p align="center">
<a href="README.md"><img src="https://img.shields.io/badge/English-README-656d76?style=for-the-badge" alt="README em inglês"/></a>
<a href="README.zh-CN.md"><img src="https://img.shields.io/badge/Chinese%20(Simplified)-README-656d76?style=for-the-badge" alt="简体中文 README"/></a>
<a href="README.zh-TW.md"><img src="https://img.shields.io/badge/繁體中文%28台灣%29-README-656d76?style=for-the-badge" alt="README zh-TW"/></a>
<a href="README.zh-HK.md"><img src="https://img.shields.io/badge/繁體中文%28香港%29-README-656d76?style=for-the-badge" alt="README zh-HK"/></a>
<a href="README.es.md"><img src="https://img.shields.io/badge/Español-README-656d76?style=for-the-badge" alt="README en español"/></a>
<a href="README.fr.md"><img src="https://img.shields.io/badge/Français-README-656d76?style=for-the-badge" alt="README en français"/></a>
<a href="README.de.md"><img src="https://img.shields.io/badge/Deutsch-README-656d76?style=for-the-badge" alt="README auf Deutsch"/></a>
<a href="README.ja.md"><img src="https://img.shields.io/badge/日本語-README-656d76?style=for-the-badge" alt="日本語 README"/></a>
<a href="README.ko.md"><img src="https://img.shields.io/badge/한국어-README-656d76?style=for-the-badge" alt="한국어 README"/></a>
<a href="README.it.md"><img src="https://img.shields.io/badge/Italiano-README-656d76?style=for-the-badge" alt="README in italiano"/></a>
<a href="README.ru.md"><img src="https://img.shields.io/badge/Русский-README-656d76?style=for-the-badge" alt="README на русском"/></a>
<a href="README.pt-BR.md"><img src="https://img.shields.io/badge/Portugu%C3%AAs%20(Brasil)-current-2ea043?style=for-the-badge" alt="Atual: português (Brasil)"/></a>
</p>

---

**KeyCmd** é o app Android complementar do [Openterface](https://openterface.com/) — uma ponte de hardware estilo KVM que permite controlar um computador host pelo celular via **USB** ou **Bluetooth**. Este repositório contém a implementação Java/Android (`com.openterface.keymod`).

- **Requisitos:** Android 8.0+ (API 26); USB OTG ao usar controle por USB  
- **Documentação:** etapas de conexão, modos e atalhos em [docs/USER_GUIDE.md](docs/USER_GUIDE.md) (inglês)  
- **Instalação:** APKs pré-compilados em [GitHub Releases](https://github.com/TechxArtisanStudio/Openterface_KeyMod_Android/releases) e artefatos do [GitHub Actions](https://github.com/TechxArtisanStudio/Openterface_KeyMod_Android/actions)  

## Recursos principais

| Área | O que você obtém |
|------|------------------|
| **Teclado e mouse** | Touchpad, QWERTY completo, modificadores, atalhos de edição, linha de macros opcional, layouts estilo teclado numérico e buffer para compor texto longo e enviar ao host |
| **Apresentação** | Controles de apresentador para apps como Google Slides (timer, anterior/próximo, apresentar, tela preta, trocar app, touchpad) |
| **Shortcut Hub** | Perfis de atalhos para ferramentas criativas e de desenvolvimento (ex.: Blender, KiCAD, Photoshop, VS Code) com criar/importar/exportar |
| **Gamepad, macros, voz** | Modos adicionais no mesmo app (veja o menu lateral e o guia do usuário) |

## Capturas de tela

Os arquivos estão em [`demo/`](demo/). A largura é definida em HTML para leitura confortável no GitHub; telas em paisagem usam largura maior.

### Boas-vindas e navegação

<table>
<tr>
<td align="center" valign="top" width="50%">
<b>Boas-vindas — escolha um modo</b><br/>
<small>Teclado e mouse, apresentação, gamepad, Shortcut Hub e mais.</small><br/><br/>
<img src="demo/demo-welcome-mode-selection.jpg" alt="Tela de boas-vindas" width="300" />
</td>
<td align="center" valign="top" width="50%">
<b>Menu lateral</b><br/>
<small>Alterne entre modos, macros, voz e configurações.</small><br/><br/>
<img src="demo/demo-navigation-drawer.jpg" alt="Menu de navegação" width="300" />
</td>
</tr>
</table>

### Teclado e mouse

<table>
<tr>
<td align="center" valign="top" colspan="2">
<b>Retrato — gestos do touchpad + teclado</b><br/>
<small>Ajuda de gestos acima do teclado personalizado (avisos podem aparecer sem dispositivo conectado).</small><br/><br/>
<img src="demo/demo-keyboard-mouse-portrait-touchpad-gestures.jpg" alt="Gestos e teclado em retrato" width="300" />
</td>
</tr>
<tr>
<td align="center" valign="top" width="50%">
<b>Paisagem — teclado dividido + touchpad</b><br/><br/>
<img src="demo/demo-keyboard-mouse-landscape-split-keyboard-touchpad.jpg" alt="Teclado dividido e touchpad central" width="420" />
</td>
<td align="center" valign="top" width="50%">
<b>Paisagem — linha de macros + perfis</b><br/>
<small>ex.: Default / KiCAD.</small><br/><br/>
<img src="demo/demo-keyboard-mouse-landscape-macro-strip.jpg" alt="Macros e perfis" width="420" />
</td>
</tr>
<tr>
<td align="center" valign="top" width="50%">
<b>Retrato — touchpad + teclado estendido</b><br/><br/>
<img src="demo/demo-keyboard-mouse-portrait-touchpad-numpad.jpg" alt="Touchpad e grade de teclas" width="300" />
</td>
<td align="center" valign="top" width="50%">
<b>Retrato — texto longo + Enviar</b><br/><br/>
<img src="demo/demo-keyboard-mouse-portrait-long-text-compose.jpg" alt="Composição de texto longo" width="300" />
</td>
</tr>
</table>

### Apresentação

<p align="center">
<b>Google Slides</b> — timer e controles grandes (outros apps na faixa superior).<br/><br/>
<img src="demo/demo-presentation-google-slides.jpg" alt="Controle remoto de apresentação" width="440" />
</p>

### Shortcut Hub

<p align="center">
<b>Shortcut Hub</b> — perfis e contagem de atalhos.<br/><br/>
<img src="demo/demo-shortcut-hub.jpg" alt="Lista do Shortcut Hub" width="300" />
</p>

### Entrada por voz

<p align="center">
<b>Entrada por voz</b> — área de transcrição, destinos, histórico, microfone (configure a chave de API em Configurações; veja o guia).<br/><br/>
<img src="demo/demo-voice-input.jpg" alt="Tela de voz" width="300" />
</p>

## Compilar a partir do código-fonte

Abra o projeto no Android Studio (Giraffe ou mais recente) ou use a linha de comando:

```bash
./gradlew assembleDebug
```

APK de depuração: `app/build/outputs/apk/`.

## Upstream

Contexto de hardware e produto: [TechxArtisan — Openterface](https://openterface.com/).
