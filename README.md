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
*   **Artistic Clock & Date**: Displays the current time and date in a sophisticated, minimalist design at the bottom-right corner.
    *   **Customizable**: Can be toggled ON/OFF in the settings menu.
    *   **Visibility**: Automatically appears briefly during slideshow navigation or remains visible if configured.
*   **Shooting Date**: Reads EXIF metadata to show the "Date Taken" at the top-right corner for 5 seconds after each transition.
*   **Enhanced Pause UI**: When paused, a visual D-pad guide (Remote Control illustration) appears, showing the mapping of directional keys to specific actions.

### 3. Image Operations (During Pause)
*   **Interactive Rotation**: Rotate images 90 degrees left or right using the **Up/Down** keys. The confirmation dialog only appears when you press **OK** to finish the operation, ensuring a smooth adjustment process.
*   **Save Rotation**: Overwrite the original file with the new orientation. During saving, the app displays a "Saving..." status and locks input to prevent corruption. The image is automatically reloaded from disk after saving to verify the change.
*   **Physical Deletion**: Delete unwanted photos directly from storage. A styled confirmation dialog prevents accidental deletions.
*   **Automatic Resume**: Playback automatically resumes after confirming a deletion or saving a rotation, keeping the experience seamless.

### 4. User Interface & Accessibility
*   **Styled Dialogs**: All menus (Settings, Delete, Save) feature modern TV-optimized designs with clear titles, detailed descriptions, and focus-friendly layouts.
*   **Smart Focus Management**: The app automatically focuses on the most relevant button when a dialog opens, making it fully operational with a standard TV remote.
*   **USB Auto-Detection**: Automatically refreshes the image list when a USB drive is mounted or removed.
*   **Immersive Experience**: Keeps the screen on and hides system bars to focus entirely on the photos.

## Remote Control (D-pad) Guide

| Button | Normal Mode | Pause Mode |
| :--- | :--- | :--- |
| **Select (OK)** | Pause Slideshow | Confirm (Shows Save Dialog if rotated / Resume) |
| **Back** | Exit Application | Close Dialog or Resume Slideshow |
| **Right** | Next Image | Open **Settings** Menu |
| **Left** | Previous Image | Open **Delete** Confirmation |
| **Up** | - | Rotate 90° Clockwise |
| **Down** | - | Rotate 90° Counter-Clockwise |

## Settings
Accessible via the **Right** button while paused:
*   **Interval**: Set slideshow speed (10s / 15s / 20s). Includes a brief description of the setting.
*   **Transition**: Enable or disable random transition effects (Crossfade, Slide, Zoom).
*   **Clock Display**: Toggle the artistic clock and date overlay ON/OFF.

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
