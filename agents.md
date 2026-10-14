# AGENTS.md — PlayTime Indicator Codebase Guide

This document is intended for AI assistants, agents, and core contributors working on the **PlayTime Indicator** codebase.

---

## 1. Project Overview

**PlayTime Indicator** is a hybrid Android application and modern Xposed/Vector/LSPosed module designed for Android handheld consoles (e.g. AYN Odin, Retroid Pocket, AYANEO, Logitech G Cloud).

It solves the problem of stock Android battery estimations factoring in hours of idle sleep by computing an active-only, rolling time-series power draw ($mA$ / $W$) during screen-on gaming, injecting the real-time gameplay estimate directly into the Android status bar (`com.android.systemui`).

---

## 2. Repository Structure

```
.
├── build.sh                                # Automated unit test & build script
├── build.gradle.kts                        # Root Gradle build configuration
├── settings.gradle.kts                     # Project settings and plugin repositories
├── gradle.properties                       # JVM arguments and AndroidX flags
├── local.properties                        # Local Android SDK directory (git-ignored)
├── gradlew, gradlew.bat, gradle/           # Gradle wrapper scripts and jar
├── .gitignore                              # Git exclusion rules
├── README.md                               # User-facing documentation
├── agents.md                               # AI agent & developer architecture guide
└── app/
    ├── build.gradle.kts                    # App module dependencies, SDK versions, packaging
    ├── proguard-rules.pro                  # ProGuard / R8 rules
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml         # Package declarations, permissions, xposed metadata
        │   ├── assets/
        │   │   └── xposed_init             # Entrypoint hook class: com.handheld.playtime.hook.SystemUIHook
        │   ├── java/com/handheld/playtime/
        │   │   ├── MainActivity.java       # Material 3 settings UI & live fuel gauge diagnostics
        │   │   ├── BootReceiver.java       # Direct Boot & BOOT_COMPLETED sync dispatcher
        │   │   ├── battery/
        │   │   │   ├── BatteryReader.java      # Dual-tier hardware reader (sysfs + BatteryManager)
        │   │   │   └── PlayTimeEstimator.java  # Rolling time-series estimator & string formatter
        │   │   ├── config/
        │   │   │   ├── PlayTimeConfig.java         # Config POJO, Bundle serialization & SharedPreferences
        │   │   │   └── PlayTimeConfigProvider.java # ContentProvider for cross-process config sharing
        │   │   └── hook/
        │   │       └── SystemUIHook.java       # Xposed hook implementation for com.android.systemui
        │   └── res/
        │       ├── drawable/
        │       │   └── ic_launcher.xml     # Vector launcher icon
        │       ├── layout/
        │       │   └── activity_main.xml   # Settings & diagnostics UI layout
        │       └── values/
        │           ├── arrays.xml          # Spinner entries, scope, and machine values
        │           ├── colors.xml          # Modern dark Material palette
        │           ├── strings.xml         # UI labels and descriptions
        │           └── themes.xml          # Material3 Theme
        └── test/
            └── java/com/handheld/playtime/
                └── PlayTimeEstimatorTest.java  # Comprehensive JUnit unit tests for estimator
```

---

## 3. Architecture & Core Components

### 3.1. Hook Lifecycle (`SystemUIHook.java`)
- **Target:** Injected into `com.android.systemui` via Xposed / Vector.
- **Self-Activation Check:** Hooks `MainActivity.isModuleActive()` in `com.handheld.playtime` to report true when active.
- **View Anchoring:**
  - Finds `BatteryMeterView` or `Clock` in `PhoneStatusBarView`.
  - Supports positions: `BATTERY_LEFT`, `BATTERY_RIGHT`, `CLOCK_LEFT`, `CLOCK_RIGHT`.
  - **`REPLACE_PERCENT` Mode:** Intercepts `updatePercentText`, `setPercentTextAtCurrentLevel`, and `updateShowPercent` on `BatteryMeterView` to repurpose the native battery percentage text field directly with native status-bar styling.
- **Tint Adaptation:** Hooks `onDarkChanged` and `updateColors` on `BatteryMeterView` to match status bar icon and text colors dynamically.
- **Tap Cycling:** Attaches an `OnClickListener` to the injected/replaced view to cycle metrics on tap (`Configured` $\to$ `Watts` $\to$ `Volts` $\to$ `Battery %`).

### 3.2. Telemetry & Hardware Reading (`BatteryReader.java`)
- **Direct Linux Sysfs Parsing:** Reads `/sys/class/power_supply/battery/*` or `/sys/class/power_supply/bms/*`:
  - `current_now` ($mA$ or $\mu A$)
  - `voltage_now` ($mV$ or $\mu V$)
  - `charge_counter` / `capacity`
- **Fallback:** Uses Android standard `BatteryManager` APIs (`BATTERY_PROPERTY_CURRENT_NOW`, etc.) when sysfs nodes are restricted by SELinux.
- **Charge Detection:** Listens to `Intent.ACTION_BATTERY_CHANGED` for plugged status and charging rate.

### 3.3. Estimation Algorithm (`PlayTimeEstimator.java`)
- **Rolling Window:** Stores timestamped `PowerSample` entries over a configurable window (1, 3, 5, or 10 minutes).
- **Active-Only Invalidation:** When `ACTION_SCREEN_OFF` is triggered, old samples are purged so standby idle time does not skew gameplay estimates.
- **Estimates:**
  - **Discharging:** $T_{\text{remaining}} = \frac{\text{Current Capacity (mAh)}}{\text{Rolling Average Discharge Rate (mA)}}$
  - **Charging:** $T_{\text{to\_full}} = \frac{\text{Max Capacity} - \text{Current Capacity}}{\text{Rolling Average Charge Rate (mA)}}$
- **Formatting:** Supports `COMPACT` (`2h 45m`), `SHORT` (`2h45`), `COLON` (`2:45`), and `DETAILED` (`2 hr 45 min`), with customizable detail levels (Watts, Volts).

### 3.4. Inter-Process Communication (IPC) & Boot Persistence
- Companion App writes preferences to `SharedPreferences` in both standard and Device-Protected (`createDeviceProtectedStorageContext()`) storage, allowing Direct Boot access before user unlock.
- Settings are shared cross-process through `PlayTimeConfigProvider` (`content://com.handheld.playtime.provider/config`) with `directBootAware="true"`.
- **SystemUI Local Cache:** `SystemUIHook` persists received configs directly inside SystemUI's private SharedPreferences (`playtime_systemui_cache`). Upon handheld restart, SystemUI reads this local cache on the exact first millisecond of boot, guaranteeing 0ms latency and eliminating default-setting fallbacks before third-party apps boot.
- **Boot Dispatcher:** `BootReceiver` listens for `ACTION_LOCKED_BOOT_COMPLETED`, `ACTION_BOOT_COMPLETED`, and quickboot intents, proactively broadcasting `com.handheld.playtime.ACTION_UPDATE_CONFIG` to SystemUI to ensure complete state synchronization.

---

## 4. Development & Build Workflow

### 4.1. Requirements
- Linux or macOS environment with Bash.
- JDK 17 or JDK 21.
- Android SDK with platform tools and `build-tools;34.0.0`.

### 4.2. Running Tests
Unit tests run on JVM without an emulator:
```bash
./gradlew testDebugUnitTest
```
Ensure all tests pass before making commits.

### 4.3. Building the Module APK
Use the helper script or Gradle directly:
```bash
./build.sh
# or
./gradlew assembleDebug
```
The output debug APK is copied to `./PlayTimeIndicator-debug.apk`.

---

## 5. Coding Standards & Conventions

1. **SystemUI Safety:** `SystemUIHook` runs inside the critical system process. Never throw uncaught exceptions. Wrap view manipulations and reflection calls in `try ... catch (Throwable t)`.
2. **Resource Management:** Always cancel `Handler` callbacks and unregister receivers when views detach to prevent memory leaks in `SystemUI`.
3. **No Unnecessary Dependencies:** Keep the app lean. Do not add heavy third-party libraries; rely on AndroidX, Material Components, and standard Android APIs.
4. **Backward Compatibility:** Maintain support from Android 8.0 (API 26) through Android 15.
