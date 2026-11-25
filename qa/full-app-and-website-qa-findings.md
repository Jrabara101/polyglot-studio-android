# Polyglot Studio: Website & Android App QA Findings

Audited **2026-11-25** across:
- **Website:** local `docs/index.html` (served via HTTP) and live production deployment `https://polyglot-studio-beryl.vercel.app/` using **Chrome DevTools MCP** (console, network, DOM/a11y snapshot, interactive mini-game tests, desktop & mobile viewports, Lighthouse desktop & mobile) plus QR payload decoding and external link checks.
- **Android App:** `com.aistudio.polyglotstudio.kxpvqm` debug build on emulator `Medium_Phone_API_37.0` (`emulator-5554`, Android 17, 1080×2400) via **ARTEMIS** (`mobile_diagnose`, `mobile_get_device_state`) + live multi-screen ADB walkthrough + ADB logcat + Gradle JUnit / Robolectric Compose UI test suite.

---

## Part 1: Website QA Findings (`docs/index.html`)

### Summary Metrics
- **Lighthouse (Mobile, local):** Accessibility **100**, Best Practices **100**, SEO **100**, Agentic Browsing **100** (53/53 passed).
- **Lighthouse (Desktop, local & live Vercel):** Accessibility **100**, Best Practices **100**, SEO **100**, Agentic Browsing **99–100** (**1 failed audit:** `cumulative-layout-shift`).
- **Interactive LexiMatch mini-game:** All tested paths passed (same-kind selection switch, deselect, mismatch shake + combo reset, 3-pair completion with phonetic reveals & hot combo badge, clicking already-matched tile no-op, Play again reset).
- **External links & QR code:**
  - `https://github.com/Jrabara101/polyglot-studio-android` → HTTP `200`.
  - `https://github.com/Jrabara101/polyglot-studio-android/releases/latest` → HTTP `200` (redirects to `v.0.1.0`).
  - `docs/assets/qr.svg` decodes cleanly to `https://github.com/Jrabara101/polyglot-studio-android/releases/latest/download/app-debug.apk` → HTTP `200` (32.5 MB).
  - Zero console JS errors, zero failed network requests, zero horizontal overflow at `1444px` and `375px`.

### Website Anomalies Found

| # | Severity | Area | Finding | Location |
|---|---|---|---|---|
| W1 | **Medium** | Performance / CLS | Chrome DevTools emits `[issue] Lazy-loaded images should have explicit dimensions` on `assets/img_welcome_studio.webp` (both locally and on live Vercel). `.place img` sets CSS `aspect-ratio: 900/672`, while `.tutor img` relies only on HTML attributes (`auto 1000 / 558`) without CSS `width: 100%; aspect-ratio: 1000 / 558`. | `docs/index.html:130, 281` |
| W2 | **Medium** | Performance / CLS | Desktop Lighthouse fails `cumulative-layout-shift` (`0.065` locally, `0.044` live). Webfont swap (`Fredoka` / `Plus Jakarta Sans`) and empty `#grid` populated by JS on load shift content below the fold; reserving `min-height` on `.grid` and CSS `aspect-ratio` on `.tutor img` will reduce CLS. | `docs/index.html:15, 83, 214` |
| W3 | **Medium** | SEO / Social Sharing | `<meta property="og:image" content="assets/img_welcome_studio.webp">` uses a **relative** URL (Open Graph crawlers require an absolute `https://` URL, and some crawlers ignore `.webp`). Also missing `og:url`, `og:type`, and `twitter:card`. | `docs/index.html:8-10` |
| W4 | **Medium** | Release / Distribution | Both download buttons and `qr.svg` distribute `v.0.1.0/app-debug.apk` (a debug APK built Nov 21, before local QA fixes). In addition, the Vercel deployment URL (`polyglot-studio-hxg91n06o-jrabara101s-projects.vercel.app`) redirects to Vercel SSO login while the public alias is `https://polyglot-studio-beryl.vercel.app/` (`polyglot-studio.vercel.app` returns 404). | `docs/index.html:194, 296`, `docs/assets/qr.svg` |
| W5 | **Low** | Accessibility / Copy | `<h1><span class="l">Play your way</span><span class="l">to fluency.</span></h1>` lacks whitespace between spans, causing DOM `textContent` and copy-paste to concatenate as `"Play your wayto fluency."`. | `docs/index.html:186` |
| W6 | **Low** | Mobile Usability | At `375×812`, footer link `"Source on GitHub"` measures `129 × 24 px`, below the 44px minimum mobile touch-target height. | `docs/index.html:151, 313` |
| W7 | **Low** | Mobile Layout | CSS comment notes *"on phones the QR is pointless"*, yet `.qr` (`343 × 426 px`, *"On a computer? Scan with your phone's camera..."*) still renders prominently on mobile viewports (`375px`). | `docs/index.html:153, 301-305` |
| W8 | **Low** | Maintainability / Polish | Unused CSS rules `.nav .btn span.long` and `.sr` match 0 DOM nodes; British spelling `"Practise"` (`docs/index.html:273-274`) contrasts with US spelling elsewhere; `assets/qr.svg` (`viewBox="0 0 41 41"`) has no built-in quiet zone if viewed outside `.qr`. | `docs/index.html:162, 164, 273-274`, `docs/assets/qr.svg` |

---

## Part 2: Android App QA Findings (`com.aistudio.polyglotstudio.kxpvqm`)

### Environment & Test Execution Summary
- **Unit & Robolectric Compose UI tests:**
  - Initial `./gradlew testDebugUnitTest` **failed immediately** on `ExampleRobolectricTest` (expected `"My Application"`, actual `"Polyglot Studio"`), and `failFast = true` aborted the remaining 34 tests.
  - **Fixed during CI setup:** Updated `ExampleRobolectricTest.kt` to assert `"Polyglot Studio"` and added `-PexcludeEmulatorTests=true` in `app/build.gradle.kts` so `ProgressRepositoryRuleTest` (which requires Firebase Local Emulator on ports `8085`/`9099`) can be separated in CI.
  - Verified fresh run: **35/35 unit + Robolectric Compose UI tests PASSED** (`ExampleUnitTest`, `ExampleRobolectricTest`, `AgeGateTest`, `LexiconTest`, `CalligraphyGameLandscapeTest`, `CalligraphyGameScreenTest`, `CalligraphyGradingLogicTest`).
- **ARTEMIS & Live Emulator Walkthrough (`emulator-5554`, Android 17):**
  - `mobile_diagnose` booted `Medium_Phone_API_37.0` and confirmed all 5 required checks passing (`verdict: ready`, helper v6 reachable).
  - Note: ARTEMIS's autonomous Pro runner (`config/artemis.jsonc` default model `gemini-3.8-flash`) stalled on upstream Gemini `503 Service Unavailable (high demand)` retries. The live interactive audit was completed directly on `emulator-5554` using ARTEMIS `mobile_get_device_state` (screenshot + accessibility hierarchy) and ADB across Studio Home, all 5 games, all 4 bottom tabs, and logcat.
  - **Stability / Logcat:** **0 crashes (`FATAL`), 0 `AndroidRuntime` exceptions, 0 ANRs** observed across the entire walkthrough.

### Android Anomalies Found

| # | Severity | Screen / Component | Finding | Status / Source |
|---|---|---|---|---|
| A1 | **High** | Cross-app (`TactileButton`) | **Protruding black shadow bar on every full-width / weighted `TactileButton`.** The outer `Box` receives `Modifier.fillMaxWidth()` / `Modifier.weight(1f)` and the shadow layer uses `.matchParentSize()`, but the inner `Surface` omits `.fillMaxWidth()`. Confirmed visually on Studio Home (`Speech Guide`, `Live AI Tutor`), Echo Voice Arena (`Listen`, `0.75× Slow`, `Record My Voice`, `Skip Phrase →`), and Speech & Sound Guide (`Normal`, `Slow`, `Try` on every card) — leaving 20%–65% of the button covered by an exposed solid black backdrop bar. | Open (`app/src/main/java/com/example/ui/components/TactileComponents.kt:140-170`) |
| A2 | **High** | `VoiceQuizGameScreen` & `StoryGameScreen` | **Uncaught exception crash risk when saving results offline.** `onCompleteSession(...)` runs inside `scope.launch { try { ... } finally { ... } }` with no `catch` block (the same crash fixed in `CalligraphyGameScreen` as Finding #10). | Open (`VoiceQuizGameScreen.kt:567-582`, `StoryGameScreen.kt:514-529`) |
| A3 | **High** | Unit Test Suite | **`ExampleRobolectricTest` failed out of the box** (`assertEquals("My Application", appName)` vs `"Polyglot Studio"`), and `failFast = true` halted the entire suite before running 34 valid tests. | **Fixed** (`ExampleRobolectricTest.kt:19`, `app/build.gradle.kts:145-157`) |
| A4 | **Medium** | `VoiceQuizGameScreen` | **Skipping every phrase still awards `60%` clarity and `+60 XP`.** Tapping `Skip Phrase` unconditionally appends `60` to `scores`, even if the user never records audio. | Open (`VoiceQuizGameScreen.kt:556-564`) |
| A5 | **Medium** | `StudioHomeScreen` | **Cold-start state flash & layout shift.** While Firestore profile syncs (`StudioUiState.Loading`), Studio Home renders `"Hi Learner"`, `0d`, `0 XP`, Japanese selected, `0%` bars, and a `"Syncing cloud studio…"` banner, then abruptly jumps to the user's real profile (`Hi Polyglot`, `2d`, `709 XP`, Spanish, `27%`), shifting card positions under taps. Cold display time on emulator was also `+9s114ms` alongside eager `TextToSpeech` superpack init. | Open (`StudioHomeScreen.kt:78-80, 177-187`) |
| A6 | **Medium** | `StoryGameScreen` | **Suggested reply chips show no disabled/loading visual state while `isThinking` is true.** While `"Story host is replying…"` spins after Turn 1, the prior turn's reply cards remain rendered at full opacity below the spinner. | Open (`StoryGameScreen.kt:439-466`) |
| A7 | **Low** | `ProgressScreen` | **Pluralization bug (`"1 games"` / `"1 days"`).** Observed `"1 games"` live on `Spanish 5-Skill Mastery`; streak card similarly formats `"${streak} days"`. | Open (`ProgressScreen.kt:164, 196`) |
| A8 | **Low** | `ProgressScreen` | **Inconsistent language filtering & game names.** Selecting Spanish filters `Spanish 5-Skill Mastery` and `Spanish Phrasebook (0)`, while `Recent Sessions` right below mixes all languages (mostly Japanese) without saying `"All Recent Sessions"`. The empty phrasebook prompt also cites `"Card Match, Voice Arena, Story Café, or Speech Guide"` instead of the UI titles (`LexiMatch Cards`, `Echo Voice Arena`, `Story Café Dialogues`, `Speech & Sound Guide`). | Open (`ProgressScreen.kt:75, 263, 340`) |
| A9 | **Low** | `StudioHomeScreen` & `SpeechGuideScreen` | Minor copy/theming polish: `"Open Difficulty"` heading on Studio Home; bottom nav active pill uses default Material lavender instead of the active language accent; Speech Guide subtitle says `"Tap any sound for native Gemini TTS..."` though bundled offline Kokoro clips play first. | Open (`StudioHomeScreen.kt:221`, `AppNavigation.kt:197`, `SpeechGuideScreen.kt`) |

### Screens Verified Passing (No Crashes / Core Flow Working)
- **Studio Home:** Language switching (Japanese ↔ Spanish) updates theme accent, XP badge (`648 JP XP` → `61 ES XP`), and per-language mastery percentages.
- **LexiMatch Cards:** 12-card grid renders cleanly; card tap flips and reveals Spanish vocabulary card (`train` / `Travel`); back navigation returns cleanly.
- **Ink & Stroke Studio:** Spanish `ñ` guide renders cleanly; `Undo`/`Clear` start disabled and enable immediately after drawing a stroke; `Guide`, `Check`, and `Skip this one` all render cleanly with no shadow defect.
- **Syntax Sprint Quiz:** Selecting correct option (`B. estoy`) highlights green with check icon, increments `Streak 0` → `Streak 1`, and displays grammar explanation, `Ask Gemini Why`, and `Next →`.
- **Echo Voice Arena:** Prompt, phonetics, waveform, and controls render (aside from `TactileButton` shadow bug A1).
- **Story Café Dialogues:** Madrid Atocha illustration, host dialogue, reply selection, user bubble (`YOU`), turn counter (`Turn 1/3+`), and reply spinner all work.
- **Lumi AI Tutor:** Language switcher, `GEMINI LIVE` Talk card, suggestion chips, coach intro card, and bottom input bar render cleanly with no bottom-bar overlap.
- **Progress & Phrasebook:** XP, streak, language selector, mastery bars, phrasebook, and session list all load and scroll cleanly.
