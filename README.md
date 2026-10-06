# NiceNotify

A private, on-device Android notification-history app.

## Features

- Saves notification title, text, source app, and timestamp after Notification Access is enabled.
- Searchable history with unread mode and app/date filters.
- Exclude individual apps from future capture.
- Dismiss active notifications while keeping saved history.
- Encrypt app labels, titles, and message bodies at rest with an Android Keystore AES-GCM key.
- Optional numeric app PIN, stored as a salted PBKDF2 hash; screenshots are blocked while locked.
- Optional automatic history deletion after 7, 30, or 90 days, or never.
- Keeps history on the device and requests no Internet permission.

## Limitations

- Android only provides notifications after Notification Access is granted; earlier messages cannot be recovered.
- A message can only be saved when its content is included in the notification preview.
- Notification access may be restricted by device or work-profile policies.

## Build

GitHub Actions builds one universal debug APK for 32-bit and 64-bit ARM devices. This app uses Java and Android framework APIs only, so it does not need separate ABI APKs. Download the **NiceNotify-APK** artifact from the latest successful [Android APK workflow run](https://github.com/Abirnewtry2580/nicenotify-android/actions/workflows/android.yml).

The GitHub artifact is a ZIP wrapper around the APK. Its displayed size is not the exact APK size; the workflow log prints the APK byte size and SHA-256 checksum.

Local build requires Android SDK 35 and JDK 17:

    gradle assembleDebug

Minimum supported Android version: Android 8.0 (API 26).
