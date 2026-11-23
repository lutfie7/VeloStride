# VeloStride

**VeloStride** (`com.aistudio.velostride.kxqrmz`) is a native Android GPS activity tracker, wearable companion, and athletic performance analytics app built with **Kotlin**, **Jetpack Compose (Material 3)**, and **Firebase**.

---

## Key Features

- **GPS Workout Recording (`RecordScreen`, `WorkoutRecorderController`, `GpsTrackingService`)**
  - Foreground location service tracking with live distance, moving duration, speed/pace, elevation gain, barometer/GPS altitude smoothing, manual lap splitting, and auto-split per kilometer.
  - Signed split elevation calculation (`+Xm` climbs, `-Xm` descents, `0m` flat) across live recording, GPX imports, Health Connect syncs, and distance-interpolated route recomputation.
  - Real-time audio coaching cues via `LiveVoiceCoachManager` and optional BLE heart rate streaming.
- **Wearables & Health Connect Integration (`WearablesScreen`, `HuaweiBleManager`, `HealthConnectManager`, `BandSyncHelper`)**
  - Native Bluetooth Low Energy (BLE) GATT scanning and heart rate / wearable telemetry pairing (including **HUAWEI Band 11 Pro** support).
  - Full **Android Health Connect** sync for workouts, GPS exercise routes, heart rate, resting HR, HRV (RMSSD), SpO2, steps, active/total calories, sleep stages, and VO2 Max.
- **Interactive Maps, Routes & Elevation Enrichment (`MapsRoutesScreen`, `MapProjection`, `ElevationRepository`, `GpxSerializer`)**
  - OpenStreetMap (`tile.openstreetmap.org`) conformal Web Mercator projection with hue-preserving dark-mode rendering.
  - Topographic elevation enrichment via Open-Meteo Copernicus/SRTM DEM when hardware barometric data is unavailable.
  - GPX import and export (`GpxSerializer`).
- **3D Flyover Map Animator & MP4 Video Export (`FlyoverMapAnimator`, `FlyoverVideoExporter`)**
  - Interactive animated route playback with **`3D Follow`**, **`Cinematic 3D`** (heading-aligned drone camera, smooth orbit sweep, horizon atmosphere, and 3D elevation route ribbon), and **`Full Route`** camera modes, plus `1x–8x` playback speeds and a synchronized elevation sparkline scrubber.
  - Aspect ratio selection (**`1:1`**, **`16:9`**, **`9:16`**) and hardware-accelerated **H.264 `.mp4` video export** (`MediaCodec` + `EGL14` + `MediaMuxer`) with direct save to `Movies/VeloStride` / Storage Access Framework and `FileProvider` sharing.
- **Activity Sharing (`ActivityShareHelper`, `PastWorkoutSessionsComponent`)**
  - Customizable share cards with selectable aspect ratios (`1:1`, `16:9`, `9:16`), visual themes (`Obsidian`, `Summit`, `Sunset`, `Neon HUD`, `Transparent`), and individual metric toggles (`Map`, `Distance`, `Duration`, `Elevation`, `Pace/Speed`, `Heart Rate`, `Calories`, `Effort`, `Date & Info`).
- **Performance Analytics & Athlete Intelligence (`ProgressScreen`, `PerformanceEngine`, `AthleteIntelligenceRepository`)**
  - Training load modeling (CTL Fitness, ATL Fatigue, TSB Form), VO2 Max estimation, recovery scoring, and HR zone breakdowns.
  - Powered by **Firebase AI Logic** (`Firebase.ai`) and **App Check** for streaming workout analysis, recovery coaching, and Google Maps-grounded outdoor route discovery.
- **Cloud Sync & Authentication (`AuthManager`, `VeloStrideRepository`)**
  - Google Sign-In via Jetpack **Credential Manager** paired with **Firebase Authentication** and per-user **Cloud Firestore** storage (`firestore.rules`).

---

## Tech Stack

| Layer | Technologies |
| --- | --- |
| **Language & UI** | Kotlin `2.4.21`, Jetpack Compose (`compose-bom 2024.09.00`), Material 3, Navigation Compose |
| **Build & Toolchain** | Android Gradle Plugin `9.1.1`, `compileSdk 36`, `targetSdk 36`, `minSdk 24`, JDK 11 / CI JDK 21 |
| **Backend & AI** | Firebase BOM `35.1.0` (Auth, Firestore, Firebase AI Logic, App Check ReCAPTCHA & Debug), Credential Manager |
| **Location & Health** | Google Play Services Location (`21.3.0`), Android Health Connect (`1.1.0`), Android BLE GATT |
| **Media & Graphics** | `MediaCodec` / `MediaMuxer` / `EGL14`, Coil (`2.7.0`), OpenStreetMap Tiles |
| **Testing** | JUnit 4, Robolectric (`4.16.1`), Coroutines Test, Compose UI Test |

---

## Project Structure

```text
app/src/main/java/com/example/
├── MainActivity.kt                  # App entry point, App Check, Theme, Auth & ViewModel wiring
├── core/
│   ├── ActivityShareHelper.kt       # Share card bitmap generator & ratio layout
│   ├── AppCheckHelper.kt            # Firebase App Check provider configuration
│   ├── AuthManager.kt               # Credential Manager Google Sign-In & Auth state
│   ├── FlyoverVideoExporter.kt      # Cinematic 3D camera math & H.264 MP4 EGL renderer
│   ├── Formatters.kt                # Distance, pace, speed, duration & Haversine helpers
│   ├── GpxSerializer.kt             # GPX XML parsing & serialization
│   ├── MapProjection.kt             # Conformal Web Mercator math & OSM tile grids
│   ├── PerformanceEngine.kt         # CTL/ATL/TSB, HR zones, VO2 Max & recovery calculations
│   └── ThemePreferences.kt          # DataStore light/dark mode persistence
├── data/
│   ├── model/                       # WorkoutActivity, TrackPoint, SplitKm, AthleteProfile, etc.
│   └── repository/
│       ├── AthleteIntelligenceRepository.kt  # Firebase AI Logic analysis & Maps grounding
│       ├── ElevationRepository.kt            # Open-Meteo DEM enrichment & split elevation math
│       ├── LiveVoiceCoachManager.kt          # Audio coaching prompts during workouts
│       └── VeloStrideRepository.kt           # Firestore CRUD & snapshot listeners
├── hardware/
│   ├── BandSyncHelper.kt            # Health Connect & BLE workout normalization
│   ├── GpsTrackingService.kt        # Foreground location tracking service
│   ├── HealthConnectManager.kt      # Health Connect read/permissions client
│   ├── HuaweiBleManager.kt          # BLE GATT wearable discovery & live HR stream
│   └── WorkoutRecorderController.kt # Live workout state machine & split/lap recorder
└── ui/
    ├── MainScaffold.kt              # Adaptive NavigationBar / NavigationRail & NavHost
    ├── MainViewModel.kt             # Centralized UI state & repository coordination
    ├── components/                  # Charts, OSM map views, Flyover animator, Share dialogs
    ├── screens/                     # Auth, Feed, MapsRoutes, Record, ActivityDetail, Progress, Wearables
    └── theme/                       # Material 3 colors, typography & shapes
```

---

## Setup & Build

### Local Build & Unit Tests

> Note: Always invoke `gradle` directly (`./gradlew` is not used in this container environment).

```bash
# Build debug APK
gradle :app:assembleDebug

# Run JVM unit tests (includes Robolectric, MapProjection, GPX, Share/Flyover, and SplitElevation tests)
gradle :app:testDebugUnitTest
```

### Firebase & GitHub Actions CI (`.github/workflows/build-apk.yml`)

The GitHub Actions workflow builds `app-debug.apk` on push or manual dispatch (`workflow_dispatch`) and supports the following repository secrets:

- `GOOGLE_SERVICES_JSON`: Raw JSON or Base64-encoded `app/google-services.json` for Firebase Auth, Firestore, and AI Logic.
- `FIREBASE_APPCHECK_DEBUG_TOKEN`: UUID debug token registered in Firebase App Check for debug APK builds.
- `DEBUG_KEYSTORE_BASE64`: Optional Base64-encoded debug keystore so APK signatures remain consistent across CI builds (a fallback keystore is generated automatically if omitted).
