# TeinLite Blank Launcher

Blank-first Android launcher for Android 10+.

## How it works
- The Home screen starts completely empty. No apps are inserted automatically.
- Long-press the empty Home screen to choose which apps to add.
- Swipe up from the bottom of Home to open the app drawer.
- Long-press an app in the drawer to add it to Home.
- Tap an app on Home to launch it; long-press it to remove it.
- Home selections are saved locally.

## Lightweight design
Native Java views only: no third-party UI libraries, no network permission, no ads, no analytics, no services, no polling and no decorative animations. Installed apps are scanned only when the drawer or picker is opened; selected Home icons load on a worker thread.

## Strict size target
GitHub Actions measures the final signed APK in bytes. The workflow only publishes a direct release when the APK is **20,000 bytes or smaller**; it always uploads the artifact so a larger result can be inspected. The size has to be measured after compiling.

## Performance limits
A mostly static native screen avoids continuous animation and background work, but 60 FPS and device temperature depend on the device, firmware, refresh rate and other running apps; they cannot be guaranteed by APK size alone.
