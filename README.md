<div align="center">
  <img src="assets/Chorus-new.png" alt="Chorus Music Logo" width="160"/>

  # 🎵 Chorus Music

  ### *The ultimate, ad-free Android music experience powered by lossless audio, smart synchronization, and multiple lyric backends.*

  <p align="center">
    <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPL--3.0-32CD32?style=for-the-badge" alt="GPL-3.0 License"/></a>
    <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Platform: Android"/>
    <img src="https://img.shields.io/badge/Language-Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Language: Kotlin"/>
    <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white" alt="UI: Jetpack Compose"/>
    <img src="https://img.shields.io/badge/Experience-Ad--Free-FF8C00?style=for-the-badge" alt="Ad-Free"/>
  </p>
</div>

---

## 🌟 Overview

**Chorus Music** is a high-fidelity, open-source Android music streaming client designed for audiophiles and casual listeners alike. By tapping into online music streams and combining it with multi-source synchronized lyrics, lossless playback, and high-performance audio engine support, Chorus Music delivers a premium listening experience—without advertisements or tracking.

---



## ⚡ Features at a Glance

### 🎧 Superior Streaming & Playback
*   **Ad-Free by Default:** Dive into uninterrupted sessions of your favorite albums.
*   **Lossless FLAC Audio:** Integrated support for high-fidelity 16-bit and 24-bit streams.
*   **Seamless Offline Mode:** Download tracks, entire albums, or playlists for offline listening.
*   **Crossfade & Smooth Transitions:** No abrupt cuts between songs.
*   **Listen Together:** Sync and enjoy music in real time with your friends.

### 🔍 Discovery & Smart Tools
*   **Chorus Find:** Identify music playing in your surroundings instantly via advanced audio fingerprinting.
*   **Chorus Brain:** An intelligent on-device recommendation engine that maps your listening momentum to build the perfect next queue.
*   **Spotify Import:** Migrating is simple—import your existing Spotify playlists and tracks seamlessly.
*   **App Widgets:** Control playback right from your home screen with customizable Jetpack Glance widgets.

### ✍️ Next-Gen Synchronized Lyrics
*   **Word-by-Word Syncing:** Karaoke-style, precise word highlights.
*   **Multiple Engines:** Powered by **Lyrics+**, **LrcLib**, **KuGou**, and **SimpMusic** API providers.
*   **AI Translations:** Auto-translate non-native lyrics in real time using built-in translation models.

### 🎨 Premium Customization
*   **Dynamic Canvas Visualizations:** Immersive background animations matching the album art.
*   **UI Density Scaling:** Customize spacing to fit any screen resolution perfectly.
*   **High Refresh Rate Support:** Smooth scrolling and transitions at up to 120Hz+.
*   **Minimalist Player:** Optional toggle to hide thumbnails and focus entirely on the sound.

---

## 🏗️ Architecture

Chorus Music is built with modularity and clean architecture principles. The project is split into several independent Gradle modules:

*   [`app`](file:///c:/Users/pushk/OneDrive/Desktop/Chorus-Music/app): Main Android application entry point containing UI components and Jetpack Compose screens.
*   [`innertube`](file:///c:/Users/pushk/OneDrive/Desktop/Chorus-Music/innertube): Internal client wrapper interfacing with the InnerTube API.
*   [`canvas`](file:///c:/Users/pushk/OneDrive/Desktop/Chorus-Music/canvas) / [`applecanvas`](file:///c:/Users/pushk/OneDrive/Desktop/Chorus-Music/applecanvas) / [`chorusmusiccanvas`](file:///c:/Users/pushk/OneDrive/Desktop/Chorus-Music/chorusmusiccanvas): Visualizer engines and audio-reactive animations.
*   [`unison`](file:///c:/Users/pushk/OneDrive/Desktop/Chorus-Music/unison): Real-time sync engine supporting shared sessions (Listen Together).
*   [`lrclib`](file:///c:/Users/pushk/OneDrive/Desktop/Chorus-Music/lrclib) / [`kugou`](file:///c:/Users/pushk/OneDrive/Desktop/Chorus-Music/kugou) / [`paxsenixlyrics`](file:///c:/Users/pushk/OneDrive/Desktop/Chorus-Music/paxsenixlyrics) / [`simpmusic`](file:///c:/Users/pushk/OneDrive/Desktop/Chorus-Music/simpmusic): Dedicated APIs and scrapers to fetch and sync lyrics.

---

## 💻 Integrations & Under the Hood

*   **Discord Rich Presence (RPC):** Show off what you're listening to directly on your Discord profile.
*   **YouTube Link Parsing:** Native capability to intercept and parse YouTube links directly into the app for immediate playback.
*   **Automated Releases:** Built with robust GitHub Actions workflows for automated FOSS (Free and Open Source Software) releases.
*   **Modern Android Tech Stack:** Leverages the power of Kotlin and Jetpack Compose to deliver a fluid, high-performance UI experience.

---

<div align="center">
  Released under the GPL-3.0 License.
</div>
