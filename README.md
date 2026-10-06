# NiceNotify

A private Android notification-history app.

## Features

- Saves notification title, text, source app, and timestamp locally after Notification Access is enabled.
- Keeps a searchable history with unread mode and app/date filters.
- Lets you exclude individual apps from future capture.
- Can dismiss an active notification while keeping its saved history.
- Encrypts app labels, titles, and message bodies at rest with an Android Keystore AES-GCM key.
- Optional numeric app PIN, stored as a salted PBKDF2 hash; screenshots are blocked while the app is locked.
- Optional automatic history deletion after 7, 30, or 90 days, or never.
- Stores no notification history in a cloud service and requests no Internet permission.

## Limitations

- Android only provides notifications after Notification Access is granted; earlier messages cannot be recovered.
- A message can only be saved when its content is included in the notification preview.
- Notification access may be restricted by device or work-profile policies.

## Build

GitHub Actions builds debug APKs for armeabi-v7a and arm64-v8a. Download the NiceNotify-APKs artifact from the latest successful [Android APK workflow run](https://github.com/Abirnewtry2580/nicenotify-android/actions/workflows/android.yml).

Local build requires Android SDK 35 and JDK 17:

    gradle assembleDebug
