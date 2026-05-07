# Crystalize — Android Image Upscaler

A free, fully offline Android app that upscales any low-resolution, blurry, or noisy image to a high-quality, sharper version using a multi-stage pixel processing pipeline.

---

## Features
- **2×, 3×, 4× upscaling** using high-quality bicubic interpolation
- **Denoising** — adaptive blur to remove grain without losing edges
- **Unsharp masking** — sharpens and recovers fine detail
- **Contrast & saturation enhancement** via color matrix
- **Before/After comparison** with tab switcher
- **Download to gallery** (saved to Pictures/Crystalize/)
- **Share sheet support** — share any image directly to the app
- **100% offline** — no internet required, no data leaves your device

---

## Requirements
- Android Studio **Hedgehog (2023.1.1)** or newer
- Android SDK 34
- JDK 8+
- Physical Android device or emulator running Android 8.0+ (API 26+)

---

## How to Build & Install

### Option 1: Android Studio (Recommended)

1. **Open the project**
   - Launch Android Studio
   - Click **File → Open**
   - Select the `CrystalizeApp` folder

2. **Sync Gradle**
   - Android Studio will prompt you to sync Gradle automatically
   - Click **Sync Now** if prompted

3. **Run the app**
   - Connect your Android phone via USB (enable USB Debugging in Developer Options)
   - Select your device from the device dropdown
   - Click the green **Run ▶** button

4. **Build APK (to share/install manually)**
   - Go to **Build → Build Bundle(s) / APK(s) → Build APK(s)**
   - APK will be at: `app/build/outputs/apk/debug/app-debug.apk`
   - Transfer to phone and install (enable "Install from unknown sources" in Settings)

### Option 2: Command Line

```bash
# Make gradlew executable
chmod +x gradlew

# Build debug APK
./gradlew assembleDebug

# Install directly to connected device
./gradlew installDebug
```

The APK will be at: `app/build/outputs/apk/debug/app-debug.apk`

---

## Project Structure

```
CrystalizeApp/
├── app/
│   ├── src/main/
│   │   ├── java/com/crystalize/upscaler/
│   │   │   └── MainActivity.java          ← All app logic + upscaling engine
│   │   ├── res/
│   │   │   ├── layout/activity_main.xml   ← Full UI layout
│   │   │   ├── drawable/                  ← All UI shapes & icons
│   │   │   ├── values/
│   │   │   │   ├── colors.xml             ← Color palette
│   │   │   │   ├── strings.xml
│   │   │   │   └── themes.xml             ← Material theme
│   │   │   └── xml/file_paths.xml         ← FileProvider config
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── build.gradle
├── settings.gradle
└── README.md
```

---

## How the Upscaling Works

The app runs a **4-stage pixel pipeline** entirely on-device:

1. **Bicubic Upscale** — `Bitmap.createScaledBitmap()` with `filter=true` uses bilinear interpolation for smooth upscaling at 2×, 3×, or 4×

2. **Selective Denoising** — Box blur blended at controlled strength to reduce noise grain without erasing real edges (controlled by Denoise slider)

3. **Unsharp Masking** — Classic sharpening technique: subtracts a blurred version from the original to amplify edges and fine detail (controlled by Sharpness + Detail sliders)

4. **Color Enhancement** — Android `ColorMatrix` boost to saturation and contrast for a vivid, crisp final result

All processing runs on a background thread (`ExecutorService`) so the UI never freezes.

---

## Permissions
- `READ_MEDIA_IMAGES` (Android 13+) or `READ_EXTERNAL_STORAGE` — to pick images from gallery
- `WRITE_EXTERNAL_STORAGE` (Android 9 and below) — to save output
- No internet permission — fully offline

---

## Tips for Best Results
- Works best on images that are **under 1080px** on either side
- For very large images (4K+), use 2× to avoid memory issues
- Set **Sharpness to 7-9** for blurry photos
- Set **Denoise to 6-8** for grainy/noisy photos
- Set **Detail to 8-10** for maximum crispness

---

## License
Free to use, modify, and distribute.
