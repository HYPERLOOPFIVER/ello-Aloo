# Ello Android frontend

Ello is a native Android app with Firebase Authentication and Firestore profile sync. Chat messages and attachments are saved on the device.

## Included

- Compose screens, onboarding, profile, chat preview, news/feed UI, and navigation.
- Firebase email OTP sign-in, Firebase Authentication, and Firestore profile sync.
- Firebase Functions endpoints for email OTP delivery and verification.
- Local UI preferences and chat preview history.

## Build

Install Android SDK platform 36 and JDK 17. Set the Android SDK path in `local.properties`, then run:

```powershell
.\gradlew.bat :app:assembleDebug
```

The debug APK is written to `app\build\outputs\apk\debug\app-debug.apk`.

## Firebase

See [FIREBASE_SETUP.md](FIREBASE_SETUP.md) for Firebase Authentication, Firestore rules, and email OTP function setup. Keep server-side email secrets in Firebase Functions secrets; never add them to the Android app.
