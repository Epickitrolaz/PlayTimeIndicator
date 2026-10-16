# PlayTime Indicator

> **Active Remaining Play Time & Power Telemetry for Android Handhelds**  
> Modern Xposed / Vector / LSPosed Module & Companion App

PlayTime Indicator is engineered specifically for Android gaming handhelds (AYN Odin / Odin 2, Retroid Pocket 3+/4/4Pro/5/Mini, AYANEO Pocket series, Logitech G Cloud, Razer Edge, ANBERNIC Android devices, etc.).

Stock Android's battery prediction calculates battery life based on historical averages that include hours or days of low-power deep sleep, resulting in wildly inaccurate estimates like *"18 Hours Remaining"* before plummeting rapidly under 3D emulation.

**PlayTime Indicator calculates remaining play time solely based on active, screen-on power draw ($mA$ / $W$) over a rolling gaming window**, displaying the result directly in your status bar with zero battery penalty.

---

## ✨ Features

- **Active-Only Rolling Window (1, 3, 5, or 10 min):**
  - Instant start upon device unlock/screen-on; displays a rough estimate after the first refresh interval (default 5s) and continuously refines accuracy as the rolling window fills.
  - Ignores idle sleep drain completely (`ACTION_SCREEN_OFF` pauses sampling and purges stale sleep data upon wake/unlock).
  - Rapidly adapts when switching between low-power retro emulators (~2W / ~8h) and heavy Switch/PS2 3D emulation (~12W / ~2h).
- **Flexible Status Bar Placement:**
  - **Left of Battery**
  - **Right of Battery**
  - **Left of Clock**
  - **Right of Clock**
  - **Replace Battery Percentage:** Replaces the stock Android battery percentage text in-place, keeping native status bar font, layout, and style.
- **Multiple Telemetry Detail Modes:**
  - Time Remaining Only (`2h 45m`)
  - Time + Wattage (`2h 45m · 5.4W`)
  - Time + Wattage + Voltage (`2h 45m · 5.4W · 3.85V`)
  - Time + Voltage (`2h 45m · 3.85V`)
  - Wattage Only (`5.4W`)
  - Voltage Only (`3.85V`)
  - Wattage + Voltage (`5.4W · 3.85V`)
- **Charging Time-to-Full & Custom Symbols:**
  - Automatically calculates remaining time until 100% full when connected to a charger.
  - Choose between non-color/monochrome symbols (`↯`, `+`, `CHG`) or standard emoji (`⚡`).
- **Tap-to-Cycle Metric:**
  - Tap the status bar indicator at any time to cycle on the fly between your configured view $\to$ **Watts** $\to$ **Volts** $\to$ **Battery %**.
- **Dark Mode Tint Synchronization:**
  - Automatically hooks status bar theme tinting and adapts colors when switching between light and dark apps/launchers.
- **Instant Live Setting Updates:**
  - Changes applied in the companion app update SystemUI instantly via `ContentProvider` and broadcast IPC without requiring a SystemUI restart or device reboot.
- **Dual-Tier Hardware Telemetry:**
  - Reads directly from Linux kernel sysfs (`/sys/class/power_supply/*`) for micro-accurate current ($mA$) and voltage ($mV$), falling back to Android `BatteryManager`.
  - Manual mAh capacity override support for custom or modified handheld batteries.

---

## 🛠️ Prerequisites

1. An Android handheld rooted with **KernelSU**, **APatch**, or **Magisk**.
2. **Vector** (recommended modern Zygisk Xposed framework) or **LSPosed** active in Zygisk mode.
3. Android 8.0 (API 26) through Android 15.

---

## 🚀 Installation & Setup

1. Build or download `PlayTimeIndicator-debug.apk` and install it:
   ```bash
   adb install -r PlayTimeIndicator-debug.apk
   ```
2. Open your Xposed manager (**Vector** or **LSPosed**).
3. Find **PlayTime Indicator** under Modules and toggle it **ON**.
4. Ensure the scope includes **System UI** (`com.android.systemui`).
5. Perform a soft reboot or restart SystemUI:
   ```bash
   adb shell su -c pkill -f com.android.systemui
   ```
6. Open the **PlayTime Indicator** companion app:
   - Confirm the status banner shows **"Module is Active"**.
   - Configure your preferred position, detail level, averaging window, and font size.
   - Tap **Apply Settings to SystemUI** to see changes immediately.

---

## 🏗️ Building from Source

The project uses Gradle with Java 17+ and the Android Gradle Plugin.

```bash
# Clone the repository
git clone https://github.com/Epickitrolaz/PlayTimeIndicator
cd PlayTimeIndicator

# Run unit tests
./gradlew testDebugUnitTest

# Build debug APK
./gradlew assembleDebug

# Or run the included all-in-one build script
./build.sh
```

The compiled APK will be generated at `app/build/outputs/apk/debug/app-debug.apk` and symlinked/copied to `PlayTimeIndicator-debug.apk`.

---

## 📄 License

Open-source under the Apache 2.0 License.
