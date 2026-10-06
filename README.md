# Saturatah

A minimalist, high-performance photo gallery app for Android built with Jetpack Compose.

Saturatah focuses on an uncompromised, distraction-free photo viewing experience. Designed with an AMOLED black aesthetic, it puts photos front and center with clean navigation, smooth transitions, and instant actions.

---

## Features

- **Minimalist Photo Grid**:
  - Displays all local device photos sorted chronologically in a 3-column square thumbnail grid.
  - Asynchronous thumbnail loading and memory caching powered by Coil.
  - Photo count indicator and quick refresh option.

- **Full-Screen Photo Viewer**:
  - Pure edge-to-edge viewing without distracting overlays or metadata bars.
  - Smooth horizontal swipe navigation between photos (`HorizontalPager`).
  - Native gesture and hardware back button navigation back to the grid.

- **Instant Photo Deletion**:
  - Floating action button in the upper right corner to delete the active photo.
  - Zero confirmation prompts: tapping the delete icon instantly removes the photo from device storage and updates the gallery.
  - Haptic feedback and confirmation toast.

- **Modern Android Architecture**:
  - Built with Kotlin 2.0 and Jetpack Compose (Material 3).
  - Unidirectional data flow using `ViewModel` and Kotlin Coroutines / `StateFlow`.
  - Android 14+ / 15+ storage permission handling (`READ_MEDIA_IMAGES` and `MANAGE_EXTERNAL_STORAGE`).

---

## Tech Stack

| Layer | Technology |
|---|---|
| **Language** | Kotlin 2.0.21 |
| **UI Toolkit** | Jetpack Compose (Compose BOM 2024.09.03) |
| **Design System** | Material 3 (AMOLED Dark Theme) |
| **Image Loading** | Coil Compose 2.7.0 |
| **Media Queries** | Android `MediaStore` ContentProvider |
| **Build System** | Gradle 8.10.2 + Android Gradle Plugin 8.7.1 |
| **Target SDK** | Android 15 (API level 35), Min SDK 26 |

---

## Project Structure

```text
saturatah/
├── app/
│   ├── build.gradle.kts                 # App-level dependencies & Compose configuration
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml      # App permissions & launcher activity declaration
│           ├── java/com/saturatah/gallery/
│           │   ├── MainActivity.kt      # Edge-to-edge entry point & permission handling
│           │   ├── model/
│           │   │   └── Photo.kt         # Photo entity (URI, file path, dimensions, timestamps)
│           │   ├── data/
│           │   │   └── PhotoRepository.kt # MediaStore queries for device photos
│           │   └── ui/
│           │       ├── GalleryViewModel.kt  # StateFlow UI state & deletion management
│           │       ├── GalleryScreen.kt     # Thumbnail grid & permission prompt UI
│           │       ├── PhotoDetailScreen.kt # Full-size swiping viewer & delete button
│           │       └── theme/               # Colors, typography, and dark theme definitions
│           └── res/                     # Vector icons, themes, and string resources
├── build.gradle.kts                     # Root build configuration
├── settings.gradle.kts                  # Project modules and repositories
└── README.md
```

---

## Build and Deployment

### Prerequisites

- **Java Development Kit**: JDK 17
- **Android SDK**: API 35 with Build Tools 34.0.0+ / 35.0.0
- An Android device connected via USB with USB debugging enabled (e.g., Pixel 10 Pro)

### 1. Build the Debug APK

```bash
./gradlew assembleDebug
```

The APK will be generated at:
```text
app/build/outputs/apk/debug/app-debug.apk
```

### 2. Install onto Connected Device

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 3. Grant Permissions for Prompt-Free Deletion

To allow Saturatah to delete photos without triggering the system confirmation dialog for each photo, grant `MANAGE_EXTERNAL_STORAGE` and `MANAGE_MEDIA`:

```bash
adb shell appops set com.saturatah.gallery MANAGE_EXTERNAL_STORAGE allow
adb shell appops set com.saturatah.gallery MANAGE_MEDIA allow
```

### 4. Launch the App

```bash
adb shell am start -n com.saturatah.gallery/.MainActivity
```
