# <img src="assets/icon.png" width="40" height="40" alt="Saturatah Icon" valign="middle"> Saturatah

A minimalist, high-performance photo gallery app for Android built with Jetpack Compose.

Saturatah focuses on an uncompromised, distraction-free photo viewing experience. Designed with an AMOLED black aesthetic, it puts photos front and center with clean navigation, smooth transitions, and instant actions.

---

## Features

- **Minimalist Photo Grid**:
  - Displays all local device photos sorted chronologically in a 3-column square thumbnail grid.
  - Asynchronous thumbnail loading and memory caching powered by Coil.
  - Clean edge-to-edge layout with zero header distractions.

- **Full-Screen Photo Viewer**:
  - Pure edge-to-edge viewing without distracting overlays or metadata bars.
  - Smooth horizontal swipe navigation between photos (`HorizontalPager`).
  - Native gesture and hardware back button navigation back to the grid.

- **Photo Editing Suite (Crop, Color, Light, Filters)**:
  - Edit icon on the full-screen photo viewer opens an AMOLED editing workspace.
  - Bottom menu bar featuring four dedicated editing tools:
    - **Crop & Rotate**: Interactive freeform crop window with draggable corner, edge, and center handles, plus 90° clockwise rotation.
    - **Color**: Discrete stepper ranging from complete desaturation (`Color: B&W`) to original (`Color: 0`) and boost levels (`+1` to `+10`). Perceptually-tuned color matrix applies +20% saturation to blues and greens and +5% to reds, keeping skin tones natural while enhancing foliage and skies.
    - **Light & Darks**: Dual-stepper panel for precise tonal balance:
      - **Light** (`0` to `+10`): Exposure lift anchored at the shadow floor, brightening midtones and highlights without washing out pure blacks.
      - **Darks** (`0` to `+10`): Black point control to deepen shadows and enhance contrast.
    - **Filters**: Curated one-tap film and photographic presets:
      - **Warm**: Golden hour / Portra warmth with gentle red/yellow lift.
      - **Cool**: Nordic / Provia clean editorial coolness with crisp blue roll-off.
      - **Noir**: High-contrast Leica-inspired monochrome.
      - **Retro**: Lifted matte shadows with vintage warm roll-off.
      - Tapping an active filter toggles back to original (None).
  - Top action bar featuring Cancel, Reset, and Save menu with haptic feedback.
  - **Save & Save as Copy**: Dropdown options to either overwrite the original photo seamlessly or save as an edited copy (`<name>_edit.jpg`) in `Pictures/simplah`.
  - **EXIF & Timestamp Preservation**: Preserves complete EXIF metadata (camera model, focal length, aperture, ISO, exposure, flash, white balance, GPS) and original capture timestamps (`DATE_TAKEN`, `DATE_MODIFIED`, `DATE_ADDED`), keeping photos in their exact chronological order in the gallery.

- **Long-Press Photo Deletion**:
  - Floating action button in the upper right corner to delete the active photo.
  - Zero confirmation prompts: a 500ms long press removes the photo from device storage and updates the gallery, preventing accidental deletions.
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
| **Media & EXIF** | Android `MediaStore` ContentProvider & AndroidX `ExifInterface` |
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
│           │   │   ├── Photo.kt         # Photo entity (URI, file path, dimensions, timestamps)
│           │   │   └── PhotoFilter.kt   # Photographic filter presets & color matrices
│           │   ├── data/
│           │   │   └── PhotoRepository.kt # MediaStore queries, EXIF preservation & image saving
│           │   └── ui/
│           │       ├── GalleryViewModel.kt  # StateFlow UI state & deletion management
│           │       ├── GalleryScreen.kt     # Thumbnail grid & permission prompt UI
│           │       ├── PhotoDetailScreen.kt # Full-size swiping viewer & action buttons
│           │       ├── EditPhotoScreen.kt   # Crop, rotate, color, light, and filter image editor
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
