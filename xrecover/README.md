# XRecover Android

Kotlin + Jetpack Compose + Material 3; minimum Android 10 (API 29).

Features: issue selection, editable appeal/support text, copy/share, local history and manual status tracking, FAQ with official WhatsApp help links, open WhatsApp, and persisted dark/light/system theme.

Privacy by design: no internet permission, account login, OTP, password, or API key. The entered number and optional description are only used to compose the text in memory. History only keeps issue type, timestamp, and the status selected by the user.

XRecover is not a WhatsApp product and cannot unban an account or inspect the real status of an appeal. Official decisions remain in WhatsApp.

Build with JDK 17, Gradle 8.9, Android SDK platform 35, and Build Tools 35.0.0: gradle assembleRelease. The release build enables R8/resource shrinking and uses a debug key for personal sideload testing. APK output: app/build/outputs/apk/release/app-release.apk.
