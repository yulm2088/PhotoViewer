# PhotoViewer - Android TV Digital Photo Frame

PhotoViewer is a high-performance digital photo frame application specifically designed for Android TV and Set-Top Boxes (STB). It automatically scans images from external storage (such as USB drives) and displays them with various transition effects, providing a seamless and immersive viewing experience.

## Download
https://github.com/yulm2088/PhotoViewer/releases

## Key Features

### 1. Slideshow & Playback
*   **Auto-Play**: Automatically scans USB storage and starts a shuffled slideshow upon launch.
*   **Wide Format Support**: Compatible with JPEG, PNG, BMP, GIF, and WebP.
*   **Configurable Intervals**: Choose between 10, 15, or 20 seconds playback intervals.
*   **Random Transitions**: Applies beautiful effects during transitions (can be toggled off):
    *   **Crossfade**: Smoothly blends one image into the next.
    *   **Slide**: New images slide in from the right.
    *   **Zoom & Fade**: Images scale up while fading in.

### 2. Information Overlays
*   **Clock & Date**: Displays the current time and date at the bottom-right corner. It automatically hides after 20 seconds of inactivity.
*   **Shooting Date**: Reads EXIF metadata to show the "Date Taken" at the top-right corner for 5 seconds after each transition.
*   **Pause UI**: Displays a remote control guide and D-pad illustration when the slideshow is paused.

### 3. Image Operations (During Pause)
*   **Rotation**: Rotate images 90 degrees left or right using the remote.
*   **Save Rotation**: Option to overwrite the original file on the USB drive with the new orientation when resuming playback.
*   **Physical Deletion**: Delete unwanted photos directly from the USB storage (includes a confirmation dialog).

### 4. System Integration
*   **USB Auto-Detection**: Automatically refreshes the image list when a USB drive is mounted or removed.
*   **Keep Screen On**: Prevents the device from sleeping while the app is running.
*   **Immersive Full Screen**: Hides system bars to focus entirely on the photos.

## Remote Control (D-pad) Guide

| Button | Normal Mode | Pause Mode |
| :--- | :--- | :--- |
| **Select (OK)** | Pause | Resume (shows Save Confirmation if rotated) |
| **Back** | Exit App | Resume without saving |
| **Right** | Next Image | Show Settings Menu |
| **Left** | Previous Image | Show Delete Confirmation |
| **Up** | - | Rotate 90° Clockwise |
| **Down** | - | Rotate 90° Counter-Clockwise |

## Settings
Accessible via the **Right** button while paused:
*   **Interval**: Set slideshow speed (10s / 15s / 20s).
*   **Transition**: Enable or disable random transition effects.

## Technical Specifications
*   **Minimum OS**: Android 8.0 (API 26)
*   **Target SDK**: Android 14 (API 34)
*   **Key Permissions**:
    *   `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE`: For image access and rotation saving.
    *   `MANAGE_EXTERNAL_STORAGE`: For full file access on Android 11+.
    *   `RECEIVE_BOOT_COMPLETED`: Optional auto-start on boot.
*   **Tech Stack**:
    *   Kotlin + Jetpack Compose (TV Material 3)
    *   Coil (Image Loading)
    *   ExifInterface (Metadata Handling)

## Build Instructions
1. Open the project in **Android Studio**.
2. Perform a **Gradle Sync**.
3. Build the APK using `./gradlew assembleDebug` or via the IDE build menu.
