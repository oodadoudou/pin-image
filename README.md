# Pin Image

A lightweight Android tool for quick screenshots, floating reference images, and simple multi-image boards.

Pin Image is a personal, offline-only utility. It is designed for reference workflows while drawing, reading, or browsing: capture something on screen, pin it as a floating image above other apps, and pan/zoom the content inside a fixed frame.

## Core ideas

- **One tap to screenshot and pin.** An Accessibility-service floating button captures the screen and immediately shows it as a pinned floating image.
- **Frame and content are separate.** The floating window (position, size, opacity, lock state) is independent from the image inside it (zoom, pan, rotation). Resizing the window never zooms the image, and zooming the image never resizes the window.
- **Multiple pins.** Keep several reference images on screen at once, each with its own position, size, zoom, opacity, and lock state.
- **Minimal editor.** Crop, rotate 90°, flip horizontal/vertical, reset. No filters, brushes, AI, cloud, or accounts.
- **Simple board.** Drop multiple images onto a canvas, move/resize/flip each one, fit the canvas to content, and export PNG/JPEG — or pin the result as a new floating image.
- **Local only.** No internet permission, no analytics, no upload.

## Tech stack

- Kotlin
- Jetpack Compose for in-app UI
- Traditional `View` + `WindowManager` for system floating windows
- `AccessibilityService.takeScreenshot` (API 30+) for one-tap screenshots
- AndroidX MediaStore for saving images
- Single-module Gradle project, no DI framework

## Requirements

- Android 11 (API 30) or newer
- JDK 17 to build

## Build

```sh
./gradlew :app:assembleDebug
```

The resulting debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

Install on a connected device:

```sh
./gradlew :app:installDebug
```

## Project layout

```
app/src/main/java/app/pinimage/
    MainActivity.kt          # entry point
    ui/theme/                # Compose theming
    float/                   # floating window service, frame + content model
    screenshot/              # accessibility screenshot capture
    board/                   # multi-image board canvas
    edit/                    # crop / rotate / flip editor
    data/                    # persistent settings and recent pins
```

The exact package structure will grow as features land; each feature is added in its own commit.

## Permissions

The app asks only for what it needs:

- Accessibility Service — to take screenshots without a system dialog
- Display over other apps (`SYSTEM_ALERT_WINDOW`) — to show floating images
- Notifications — for the persistent control notification
- Photo Picker — to import images without broad storage access

## License

Personal project. All rights reserved unless otherwise stated.
