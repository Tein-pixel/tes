# TeinLite Blank Launcher

A minimal Android 10+ launcher built with native Java views.

- The home screen starts completely empty.
- Swipe up from the blank home screen to open the app drawer.
- Tap an app in the drawer to open it.
- Long-press an app in the drawer to add it to Home.
- Long-press an empty Home area to open the add-app picker.
- Long-press an app already on Home to remove it.
- Home selections are saved locally.
- No network permission, ads, third-party UI libraries, wallpaper loading, widgets, animations, or background service.

The release workflow measures the real APK size and reports its SHA-256. It warns if the APK exceeds 16 KiB and retains build artifacts if it exceeds the 20,000-byte target. APK size and smoothness must be measured on the actual build and device; 60 FPS cannot be guaranteed on every phone.
