# Open Cache Cleaner for Android 🧹

An open-source Android application built with **Kotlin**, **Jetpack Compose (Material 3)**, and **Coroutines/Flow** that automates clearing temporary application cache across all installed applications.

---

## 🛠️ Key Technical Capabilities

### 1. Non-Root First Strategy (`CacheCleanerAccessibilityService`)
- Uses Android's `AccessibilityService` API to automate the manual workflow of clearing application cache.
- Navigates to `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` per package.
- **Safety Barriers**:
  - Strictly targets **"Clear Cache"** (and localized variations across German, French, Spanish, etc.) and explicit resource IDs (`clear_cache_button`).
  - **Zero Risk of Data Loss**: Explicit safeguards prevent clicking **"Clear Storage"** or **"Clear Data"**.
  - **Watchdog Timeout**: 3.5s per-app timeout prevents automation deadlocks on non-standard OEM skins (MIUI/HyperOS, One UI, ColorOS, OxygenOS).

### 2. High-Speed Root & Shizuku Backends (`RootCleanerEngine` & `ShizukuCleanerEngine`)
- **Root Shell (SuperUser / LibSU)**:
  - Instant background execution without opening settings or cycling UI screens.
  - Purges `/data/data/<pkg>/cache/*`, `/data/data/<pkg>/code_cache/*`, and external cache paths.
  - Invokes system `pm trim-caches 4096G` to instruct Android OS to reclaim all cache allocations.
- **Shizuku (ADB / Wireless Debugging)**:
  - Executes package manager cache trim commands via privileged ADB Binder IPC without requiring device root.

### 3. Architecture & Patterns
- **MVI / MVVM Pattern**: Unidirectional data flow via `CleanerUiState` and Kotlin `StateFlow`.
- **Jetpack Compose + Material 3**: Dynamic theming, custom animated progress rings, search filtering, and multi-select.
- **Storage Measurement**: Queries `StorageStatsManager` with `PACKAGE_USAGE_STATS` permission to display exact cache sizes before cleaning.

---

## 📁 Project Structure

```
app/src/main/
├── AndroidManifest.xml
├── java/com/opencachecleaner/app/
│   ├── MainActivity.kt                      # Main Activity & Screen router
│   ├── CacheCleanerApp.kt                  # Application class & LibSU config
│   ├── data/
│   │   ├── engine/
│   │   │   ├── CleanerEngine.kt             # Engine Interface (StateFlow, pause/resume/stop)
│   │   │   ├── AccessibilityCleanerEngine.kt# Non-root UI automation controller
│   │   │   ├── RootCleanerEngine.kt         # Direct root shell execution
│   │   │   └── ShizukuCleanerEngine.kt      # Shizuku privileged ADB engine
│   │   ├── model/
│   │   │   ├── AppCacheInfo.kt              # App metadata, icon, & formatted cache sizes
│   │   │   ├── CleaningMode.kt              # ACCESSIBILITY, ROOT, SHIZUKU enum
│   │   │   └── CleaningState.kt             # Idle, Running, Paused, Completed, Error
│   │   └── repository/
│   │       ├── AppRepository.kt             # StorageStatsManager & PackageManager scanner
│   │       └── PermissionHelper.kt          # Accessibility, UsageStats, Root, Shizuku checks
│   ├── service/
│   │   └── CacheCleanerAccessibilityService.kt # Core Accessibility Service & safe node matcher
│   └── ui/
│       ├── screens/
│       │   ├── OnboardingScreen.kt          # Permission setup wizard & engine selector
│       │   ├── AppListScreen.kt             # Multi-select list, cache badges, search
│       │   └── CleaningScreen.kt            # Live circular progress, pause, & stop controls
│       ├── theme/
│       │   ├── Color.kt
│       │   ├── Theme.kt
│       │   └── Type.kt
│       └── viewmodel/
│           └── CacheCleanerViewModel.kt     # ViewModel managing state & engine lifecycle
└── res/
    ├── xml/
    │   └── accessibility_service_config.xml # Accessibility flags & window configuration
    └── values/
        ├── strings.xml
        └── themes.xml
```

---

## 🚀 Setup & Build Instructions

1. Open this project in **Android Studio Meerkat / Ladybug or newer**.
2. Sync Gradle files.
3. Build & Run on an Android device or emulator running **Android 8.0 (API 26) through Android 15 (API 35)**.
4. On first launch, follow the onboarding wizard:
   - Enable **Cache Cleaner Automation** in Accessibility Settings.
   - Grant **Usage Access** to read accurate cache sizes.
