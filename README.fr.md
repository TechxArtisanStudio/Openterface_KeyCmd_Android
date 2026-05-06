# Openterface KM pour Android

<p align="center"><strong>Langue du README</strong> · <em>GitHub affiche README.md à la racine par défaut (anglais)</em></p>
<p align="center">
<a href="README.md"><img src="https://img.shields.io/badge/English-README-656d76?style=for-the-badge" alt="README in English"/></a>
<a href="README.zh-CN.md"><img src="https://img.shields.io/badge/Chinese%20(Simplified)-README-656d76?style=for-the-badge" alt="简体中文 README"/></a>
<a href="README.zh-TW.md"><img src="https://img.shields.io/badge/繁體中文%28台灣%29-README-656d76?style=for-the-badge" alt="README zh-TW"/></a>
<a href="README.zh-HK.md"><img src="https://img.shields.io/badge/繁體中文%28香港%29-README-656d76?style=for-the-badge" alt="README zh-HK"/></a>
<a href="README.es.md"><img src="https://img.shields.io/badge/Español-README-656d76?style=for-the-badge" alt="README en español"/></a>
<a href="README.fr.md"><img src="https://img.shields.io/badge/Français-current-2ea043?style=for-the-badge" alt="Actuel : français"/></a>
<a href="README.de.md"><img src="https://img.shields.io/badge/Deutsch-README-656d76?style=for-the-badge" alt="README auf Deutsch"/></a>
<a href="README.ja.md"><img src="https://img.shields.io/badge/日本語-README-656d76?style=for-the-badge" alt="日本語 README"/></a>
</p>

---

**Openterface KM** est l’application Android compagnon d’[Openterface](https://openterface.com/) : un pont matériel de type KVM qui permet de contrôler un ordinateur hôte depuis le téléphone en **USB** ou **Bluetooth**. Ce dépôt contient l’implémentation Java/Android (`com.openterface.keymod`).

- **Prérequis :** Android 8.0+ (API 26) ; USB OTG si vous utilisez le contrôle USB  
- **Documentation :** connexion, modes et raccourcis dans [docs/USER_GUIDE.md](docs/USER_GUIDE.md) (anglais)  
- **Installation :** APK précompilés sur [GitHub Releases](https://github.com/TechxArtisanStudio/Openterface_KeyMod_Android/releases) et artefacts [GitHub Actions](https://github.com/TechxArtisanStudio/Openterface_KeyMod_Android/actions)  

## Aperçu des fonctions

| Zone | Contenu |
|------|---------|
| **Clavier et souris** | Pavé tactile, QWERTY complet, modificateurs, raccourcis d’édition, ligne de macros optionnelle, disposition type pavé numérique, zone de texte long à envoyer vers l’hôte |
| **Présentation** | Télécommande pour apps comme Google Slides (minuteur, préc/suiv, lancer, écran noir, changement d’app, pavé tactile) |
| **Hub de raccourcis** | Profils de raccourcis pour outils créatifs et de dev (ex. Blender, KiCAD, Photoshop, VS Code) avec création/import/export |
| **Manette, macros, voix** | Autres modes dans la même app (voir le tiroir et le guide utilisateur) |

## Captures d’écran

Les fichiers sont dans [`demo/`](demo/). La largeur est fixée en HTML pour une lecture confortable sur GitHub ; les vues paysage sont plus larges.

### Accueil et navigation

<table>
<tr>
<td align="center" valign="top" width="50%">
<b>Accueil — choisir un mode</b><br/>
<small>Clavier et souris, présentation, manette, hub de raccourcis, etc.</small><br/><br/>
<img src="demo/demo-welcome-mode-selection.jpg" alt="Écran d’accueil" width="300" />
</td>
<td align="center" valign="top" width="50%">
<b>Menu latéral</b><br/>
<small>Bascule entre modes, macros, voix et réglages.</small><br/><br/>
<img src="demo/demo-navigation-drawer.jpg" alt="Tiroir de navigation" width="300" />
</td>
</tr>
</table>

### Clavier et souris

<table>
<tr>
<td align="center" valign="top" colspan="2">
<b>Portrait — aide gestes du pavé + clavier</b><br/>
<small>Aide au-dessus du clavier (toasts possibles sans appareil).</small><br/><br/>
<img src="demo/demo-keyboard-mouse-portrait-touchpad-gestures.jpg" alt="Gestes et clavier portrait" width="300" />
</td>
</tr>
<tr>
<td align="center" valign="top" width="50%">
<b>Paysage — clavier scindé + pavé</b><br/><br/>
<img src="demo/demo-keyboard-mouse-landscape-split-keyboard-touchpad.jpg" alt="Clavier scindé et pavé central" width="420" />
</td>
<td align="center" valign="top" width="50%">
<b>Paysage — ligne de macros + profils</b><br/>
<small>ex. Default / KiCAD.</small><br/><br/>
<img src="demo/demo-keyboard-mouse-landscape-macro-strip.jpg" alt="Macros et profils" width="420" />
</td>
</tr>
<tr>
<td align="center" valign="top" width="50%">
<b>Portrait — pavé + clavier étendu</b><br/><br/>
<img src="demo/demo-keyboard-mouse-portrait-touchpad-numpad.jpg" alt="Pavé et grille" width="300" />
</td>
<td align="center" valign="top" width="50%">
<b>Portrait — texte long + Envoyer</b><br/><br/>
<img src="demo/demo-keyboard-mouse-portrait-long-text-compose.jpg" alt="Composition texte long" width="300" />
</td>
</tr>
</table>

### Présentation

<p align="center">
<b>Google Slides</b> — minuteur et gros boutons (autres apps dans la barre du haut).<br/><br/>
<img src="demo/demo-presentation-google-slides.jpg" alt="Télécommande présentation" width="440" />
</p>

### Hub de raccourcis

<p align="center">
<b>Hub de raccourcis</b> — profils et nombre de raccourcis.<br/><br/>
<img src="demo/demo-shortcut-hub.jpg" alt="Liste du hub" width="300" />
</p>

### Saisie vocale

<p align="center">
<b>Saisie vocale</b> — transcription, cibles, historique, micro (clé API dans Réglages ; voir le guide).<br/><br/>
<img src="demo/demo-voice-input.jpg" alt="Écran voix" width="300" />
</p>

## Compiler depuis les sources

Android Studio (Giraffe ou plus récent) ou ligne de commande :

```bash
./gradlew assembleDebug
```

APK debug : `app/build/outputs/apk/`.

## Amont

Produit et matériel : [TechxArtisan — Openterface](https://openterface.com/).
