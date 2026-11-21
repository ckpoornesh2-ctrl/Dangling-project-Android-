# Dangle Heroes

A small Android floating-overlay toy for Android 8.0+ (API 26+).

## What it does
- Displays a pixel-style character hanging from a short rope above other apps.
- The character gently swings using a simple damped-pendulum animation.
- Tap the character to cycle through four color-themed heroes.
- Drag the overlay to reposition it.
- Pull downward to switch heroes and snap the overlay back.
- Stop it from the app or the persistent notification.

The included characters are original color-themed placeholders (Spider, Bat, Thunder, Trickster), not official character artwork. You can replace the pixel drawing in `DangleView.kt` with artwork you have permission to use.

## Build and install
1. Install Android Studio (current stable) and Android SDK Platform 35.
2. Open this folder as an Android Studio project.
3. Allow Gradle to sync and install any requested SDK components.
4. Connect your Android phone with USB debugging enabled, or use an emulator.
5. Press Run.

## First launch
1. Tap **START DANGLING HERO**.
2. Android opens the “Display over other apps” settings page. Enable it for Dangle Heroes.
3. Return to the app and tap Start again.
4. Android 13+ may ask for notification permission. Allow it so the Stop notification is visible.

## Gestures
- Tap: next hero.
- Pull down: next hero and spring back to the starting height.
- Drag: move the dangling character to another screen position.

## Notes
- Android requires the special “Display over other apps” permission.
- The overlay uses a user-started foreground service and a persistent notification so it is easy to stop.
- Battery optimization and manufacturer-specific background policies may affect long-running overlays.


## Build without a computer (GitHub Actions)
This project includes `.github/workflows/android-apk.yml`.
1. Create a GitHub account or sign in at https://github.com.
2. In a mobile browser, create a new **public or private** repository named `DangleHeroes`.
3. Upload the *contents* of this extracted project folder to the repository root (including the hidden `.github` folder and its workflow file).
4. Open the repository's **Actions** tab and select **Build Dangle Heroes APK**.
5. Tap **Run workflow** (or wait for the automatic build after upload). Open the completed run.
6. Under **Artifacts**, download `DangleHeroes-debug-apk`. Extract the downloaded artifact ZIP to get `app-debug.apk`.
7. Open the APK on your phone and follow Android's install prompt.

The workflow builds a debug APK in GitHub's hosted runner; it does not require Android Studio on the phone.
