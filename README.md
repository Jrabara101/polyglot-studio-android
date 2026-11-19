# 🌏 Polyglot Studio

An Android language-learning app for **Japanese, Spanish & French**, built for English speakers.

Polyglot Studio teaches through short, tactile games. Each one trains a different skill:

| Game | Skill |
|---|---|
| **LexiMatch Cards** | Vocabulary & orthography: pair scripts and meanings under combo-streak pressure |
| **Ink & Stroke Studio** | Writing: draw characters on a canvas, graded by Gemini Vision |
| **Syntax Sprint Quiz** | Grammar: fast particle and sentence-building rounds |
| **Echo Voice Arena** | Pronunciation: hear native TTS prompts and speak back for feedback |
| **Story Café** | Reading & conversation in context |

A **Gemini Live Tutor** provides real-time AI speech coaching. Progress (XP, daily streaks, skill mastery and saved phrases) syncs to the cloud. Sign in with Google or continue as a guest.

**Tech:** Kotlin · Jetpack Compose · Material 3 · Firebase Auth / Firestore / AI Logic / App Check · minSdk 24 (Android 7.0+)

---

## Run locally

**Prerequisites:** [Android Studio](https://developer.android.com/studio)

1. Open the project in Android Studio (**File → Open**) and let it sync.
2. Put a debug keystore at the project root named `debug.keystore` (password `android`, alias `androiddebugkey`). Your standard one at `~/.android/debug.keystore` works.
3. In the Firebase console for the project in `app/google-services.json`:
   - Enable the **Anonymous** (guest) and/or **Google** sign-in providers.
   - Add your keystore's SHA-1 fingerprint to the Android app (needed for Google sign-in).
   - Register the App Check debug token that the app prints to Logcat on first launch.
4. Run the app on an emulator or device, or from the command line:
   ```sh
   ./gradlew assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

---

<sub>Originally generated with Google AI Studio.</sub>
