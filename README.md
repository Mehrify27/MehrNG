# MehrNG - Premium V2Ray & Xray Client for Android

**MehrNG** is a highly polished, premium, and customized Android client for V2Ray and Xray protocols. Built on top of the robust v2rayNG core, it features a fully rebranded, modern, and skeuomorphic user interface with fluid animations, auto-subscription synchronization, real delay latency testing, and an iOS-style elastic overscroll.

---

## 🌟 Key Features

* **🎨 Skeuomorphic 3D UI:** 
  - **Embossed Server Cards:** Custom 3D bevel cards featuring a light highlight on the top-left and shadow on the bottom-right for a tactile, physical look.
  - **Recessed Protocol Badges:** Sunken acronym badges (`SS`, `VL`, `VM`, etc.) matching the skeuomorphic depth language.
  - **Dynamic Theme Adaptability:** Full day/night mode support. The app transforms into a clean, bright iOS-style light theme during the day, and shifts to a deep premium slate theme at night.
* **🦅 Branded Wings AppBar:** Re-themed Toolbar featuring the signature symmetrical wings pair on the left and a feathered emblem attached to the centered "MehrNG" title.
* **🔄 Auto-Subscription Sync:** Automatic, encrypted background updates from your dedicated Worker endpoint (`https://broad-cake-8d0c.mehrshop16.workers.dev/sub`) on first launch and app resume.
* **📶 Real Latency Testing (Real Ping):** Measures real HTTP download delay through the active V2Ray core proxy instead of simple TCP handshakes, giving you accurate connection quality statistics.
* **📱 iPhone Elastic Overscroll:** A custom, physics-based elastic drag effect on the configuration list matching the tactile feel of iOS scroll boundaries.
* **🌎 Country Geolocation Badges:** Dynamic flag emoji parsing from configuration remarks and background download of high-quality national flag badges.

---

## 🚀 Getting Started

### 1. Download & Installation
Go to the **[Releases](https://github.com/PooyaMaleki/MehrNG-/releases)** section and download the latest compiled `MehrNG.apk`.

### 2. Add Subscription
The app comes pre-configured with the default **MehrNetVPN** subscription node list. To update:
1. Tap the **Sync** button at the top-right of the server card list.
2. The app will fetch the latest VLESS/Shadowsocks configurations automatically.

---

## 🛠️ Development & Build Guide

### Prerequisites
* Android Studio (Koala or newer)
* Android SDK (API 24+)
* JDK 17+

### Compiling from Command Line
You can build the debug or release APK directly using the Gradle wrapper:

```powershell
# Compile F-Droid Release APK
.\gradlew assembleFdroidRelease

# Compile Play Store Release APK
.\gradlew assemblePlaystoreRelease
```

The output APK will be generated under `app/build/outputs/apk/`.

---

## 🎁 Support & Donation (حمایت مالی)

If you find this project helpful and want to support its active development, you can support us by donating cryptocurrency:

* **Tether (USDT - TRC20):** `YOUR_USDT_TRC20_WALLET_ADDRESS`
* **Bitcoin (BTC):** `YOUR_BTC_WALLET_ADDRESS`
* **Toncoin (TON):** `YOUR_TON_WALLET_ADDRESS`

*(Open the "Support Us" dialog inside the app to copy these addresses with a single tap!)*

---

## 📄 License
This project is licensed under the **GNU General Public License v3.0** - see the [LICENSE](LICENSE) file for details.
