# TeinLite Launcher

Ultra-minimal Android 10+ launcher focused on low APK size, fast app launch, low background activity and a fixed pure-black interface.

## Design rules
- Native Java Android views only; no AndroidX, Compose, WebView or third-party UI libraries.
- No themes, wallpaper picker, widgets, ads, network access, telemetry, sync, background service or decorative animations.
- Launcher app discovery and icon loading run on a lower-priority worker thread.
- Native GridView recycles app tiles to avoid constructing a full grid of views on every scroll.

## Size target
The GitHub Actions workflow measures the actual signed release APK and uploads it as an artifact. Desired maximum: 35 KiB (35,840 bytes). The value must be confirmed by a real build; this README does not claim that the APK already meets the target.

The launcher avoids custom animations and recurring background work, but 60 FPS cannot be guaranteed on every device because frame rate depends on the phone, display and system load.