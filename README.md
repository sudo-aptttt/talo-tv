<div align="center">

# 📺 Talo TV

*L'expérience télévisuelle pure, fluide et sans publicité conçue pour le **Google TV Streamer 4K** et tous les téléviseurs **Android TV**.*

<br/>

[![Platform](https://img.shields.io/badge/PLATFORM-GOOGLE%20TV%20STREAMER%204K%20%2F%20ANDROID%20TV-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://github.com/sudo-aptttt/talo-tv)
[![Downloader Code](https://img.shields.io/badge/DOWNLOADER%20CODE-6790525-FF6F00?style=for-the-badge&logo=googleplay&logoColor=white)](http://aftv.news/6790525)

[![Language](https://img.shields.io/badge/LANGUAGE-KOTLIN%20%7C%20WEB-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![UI Engine](https://img.shields.io/badge/UI-LEANBACK%20HYBRID-4285F4?style=for-the-badge&logo=googlechrome&logoColor=white)](https://developer.android.com/training/tv)
[![Footprint](https://img.shields.io/badge/APK%20FOOTPRINT-9.6%20MB-success?style=for-the-badge)](https://github.com/sudo-aptttt/talo-tv/releases/latest)

[![Privacy](https://img.shields.io/badge/ADS-100%25%20Z%C3%89RO%20PUB-brightgreen?style=for-the-badge)](https://github.com/sudo-aptttt/talo-tv)
[![License](https://img.shields.io/badge/LICENSE-POLYFORM%20NONCOMMERCIAL-red?style=for-the-badge)](LICENSE)

<br/>

[![Android](https://img.shields.io/badge/Android-14%2B%20(API%2034)-3DDC84?style=flat-square&logo=android&logoColor=white)](https://developer.android.com/)
[![DRM](https://img.shields.io/badge/DRM-Widevine%20L1%204K-orange?style=flat-square)](https://github.com/sudo-aptttt/talo-tv)
[![Google Cast](https://img.shields.io/badge/Cast-100%25%20Compatible-blue?style=flat-square&logo=googlecast&logoColor=white)](https://github.com/sudo-aptttt/talo-tv)
[![Status](https://img.shields.io/badge/Status-Production%20Ready-brightgreen?style=flat-square&logo=github&logoColor=white)](https://github.com/sudo-aptttt/talo-tv)
[![Wiki](https://img.shields.io/badge/Documentation-Wiki-blueviolet?style=flat-square&logo=gitbook&logoColor=white)](https://github.com/sudo-aptttt/talo-tv/wiki)

</div>

---

## 📲 Installation Rapide (Google TV & Android TV)

### Via l'application Downloader :
1. Installez et ouvrez l'application **Downloader** sur votre Google TV ou Android TV.
2. Entrez simplement le code rapide permanent :
   ### `6790525`
3. Cliquez sur **Go** : le téléchargement démarre immédiatement et la TV vous propose d'installer Talo TV.

> [!TIP]
> Ce code est permanent : il délivre automatiquement la toute dernière version officielle de Talo TV à chaque mise à jour.

---

## 🚀 Fonctionnalités Clés

1. **Expérience TV 100% Sans Publicité** :
   * Interface épurée sans bannières intrusives, rangées sponsorisées ni suggestions forcées.
   * Grille d'applications fluide et dock personnalisable au bas de l'écran.
   * Scènes météo dynamiques en arrière-plan (Alpine Mountain) et horloge typographique grand format.

2. **Bouclier de Sécurité Talo Shield (VPN Local & DNS Privé)** :
   * **VPN local haute performance** : intercepte les requêtes de serveurs publicitaires et de télémétrie en temps réel au niveau DNS.
   * **100% compatible Google Cast** : exclusion stricte des paquets Cast (`com.google.android.apps.mediashell`, `com.google.android.gms`) et activation du bypass local. Vous pouvez diffuser vos vidéos, musiques et écrans depuis votre smartphone sans aucune interruption.
   * **DNS Privé natif (DoT)** : support intégré d'AdGuard DNS sans application tierce.
   * **Contrôle par application** : activez ou désactivez la protection pour chaque application ou pour l'ensemble du système en un clic.

3. **Débloat Système Direct sur la TV (Zéro PC Requis)** :
   * Détecte les composants inutiles de Google TV (recommandations intrusives, services publicitaires d'arrière-plan, collecteurs de télémétrie).
   * Neutralisation groupée en tâche de fond (« Optimiser ») ou désactivation individuelle au niveau système directement depuis la télécommande, sans connexion USB, ni Wi-Fi, ni ordinateur.

4. **Navigation D-Pad Pure & Ergonomie Leanback** :
   * Prise en charge native de toutes les télécommandes Android TV et Google TV.
   * Volet latéral Paramètres unifié accessible instantanément via la touche Réglages de la télécommande.
   * Raccourcis directs pour les boutons Favoris/Watchlist, Chaînes TV/Direct et Accueil.

5. **Maison Connectée (Smart Home)** :
   * Intégration Home Assistant avec contrôle des pièces, des scènes lumineuses et affichage des caméras (sonnette / doorbell).

---

## 🏗️ Architecture du Projet

* **`/android`** — Couche native Android TV (Kotlin / Android 14 API 34) :
  * `MainActivity.kt` : conteneur WebView durci (sandbox sécurisée, protection path traversal et désactivation des accès fichiers non autorisés).
  * `TaloAccessibilityService.kt` : intercepteur matériel des boutons télécommande (Paramètres, Live TV, Favoris, Accueil).
  * `TaloVpnService.kt` : proxy DNS UDP local zéro-allocation avec basculement automatique.
  * Tâche Gradle `bundleTvWeb` : empaquetage hors-ligne automatique des assets web dans l'APK.

* **`/web`** — Interface utilisateur TV moderne (Web Leanback 60 FPS) :
  * `index.html`, `app.js`, `app.css` : moteur de rendu TV ultra-léger et fluide.
  * `i18n.js` : prise en charge multilingue complète (français, anglais, etc.).
  * `fonts/` : polices vectorielles locales Antonio et Inter.
  * `backgrounds/` : fonds d'écran dynamiques haute définition.

* **`config.json`** : configuration par défaut (profils utilisateurs, localisation météo, boutons d'accès rapide).

---

## 🛠️ Compilation & Déploiement

### Prérequis
* Android Studio (Koala, Ladybug ou supérieur)
* JDK 17
* Android SDK 34 (Android 14)

### Compilation APK
Depuis la racine du projet :
```bash
./gradlew bundleTvWeb assembleRelease
```
Ou depuis le sous-dossier `android/` :
```bash
cd android
./gradlew bundleTvWeb assembleRelease
```
L'APK de production minifié et optimisé par R8 se trouve dans :
`android/app/build/outputs/apk/release/app-release.apk` (taille : ~9,6 Mo).

---

## 📄 Licence

Ce projet est distribué sous la licence **PolyForm Noncommercial License 1.0.0**.  
Toute utilisation, modification et contribution non commerciale est autorisée. La revente commerciale est strictement interdite.
