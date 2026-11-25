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

| # | Severity | Area | Finding | Status / Location |
|---|---|---|---|---|
| W1 | **Medium** | Performance / CLS | Chrome DevTools emitted `[issue] Lazy-loaded images should have explicit dimensions` on `assets/img_welcome_studio.webp`. Added explicit CSS `width: 100%; aspect-ratio: 1000 / 558; object-fit: cover;` on `.tutor img` (verified 0 DevTools console issues). | **Fixed** (`docs/index.html`) |
| W2 | **Medium** | Performance / CLS | Desktop Lighthouse failed `cumulative-layout-shift` (`0.065` local, `0.044` live). Reserved `.grid` `min-height: 276px` (`252px` mobile), added explicit `.tutor img` `aspect-ratio`, and switched Google Fonts to `&display=optional` (verified Desktop Lighthouse **100/100/100/100, 53/53 passed**). | **Fixed** (`docs/index.html`) |
| W3 | **Medium** | SEO / Social Sharing | Replaced relative `og:image` with absolute `https://polyglot-studio-beryl.vercel.app/assets/img_welcome_studio.webp` and added `og:type`, `og:url`, and `twitter:card`. | **Fixed** (`docs/index.html`) |
| W4 | **Medium** | Release / Distribution | Both download buttons and `qr.svg` distribute `v.0.1.0/app-debug.apk` (a debug APK built Nov 21, before local QA fixes). In addition, the Vercel deployment URL (`polyglot-studio-hxg91n06o-jrabara101s-projects.vercel.app`) redirects to Vercel SSO login while the public alias is `https://polyglot-studio-beryl.vercel.app/` (`polyglot-studio.vercel.app` returns 404). | Deferred — requires publishing a new GitHub Release APK (`docs/index.html`, `docs/assets/qr.svg`) |
| W5 | **Low** | Accessibility / Copy | Added whitespace between `<h1>` spans so DOM `textContent` and copy-paste read `"Play your way to fluency."`. | **Fixed** (`docs/index.html`) |
| W6 | **Low** | Mobile Usability | Updated `footer a` to `display: inline-flex; align-items: center; min-height: 44px;` so the mobile tap target meets `>= 44px` (verified `44px` in Chrome DevTools). | **Fixed** (`docs/index.html`) |
| W7 | **Low** | Mobile Layout | CSS comment notes *"on phones the QR is pointless"*, yet `.qr` (`343 × 426 px`, *"On a computer? Scan with your phone's camera..."*) still renders prominently on mobile viewports (`375px`). | Deferred (`docs/index.html`) |
| W8 | **Low** | Maintainability / Polish | Removed unused CSS rules `.nav .btn span.long` and `.sr`. (British spelling `"Practise"` and `qr.svg` quiet zone left untouched as minor copy/asset polish.) | **Fixed (CSS cleanup)** (`docs/index.html`) |

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
| A1 | **High** | Cross-app (`TactileButton`) | **Protruding black shadow bar on every full-width / weighted `TactileButton`.** Fixed via `propagateMinConstraints = true` on the outer `Box` in `TactileButton` so full-width and weighted buttons fill cleanly while compact buttons (`Guide`, `Undo`, `Clear`, `Check`, `Sign Out`, `Talk`) remain compact; pinned by `TactileButtonTest`. | **Fixed** (`TactileComponents.kt`, `TactileButtonTest.kt`) |
| A2 | **High** | `CardMatchGameScreen`, `QuizGameScreen`, `VoiceQuizGameScreen` & `StoryGameScreen` | **Uncaught exception crash risk when saving results offline.** Added `CompletionSaveState`, inline error banner, and `Retry Save` button in `GameCompletionCard`, wrapping all session saves in `try / catch (CancellationException -> throw) / catch (Exception)` across all four screens; pinned by `VoiceQuizGameScreenTest` & `StoryGameScreenTest`. | **Fixed** (`GameResultOverlay.kt`, `CardMatchGameScreen.kt`, `QuizGameScreen.kt`, `VoiceQuizGameScreen.kt`, `StoryGameScreen.kt`) |
| A3 | **High** | Unit Test Suite | **`ExampleRobolectricTest` failed out of the box** (`assertEquals("My Application", appName)` vs `"Polyglot Studio"`), and `failFast = true` halted the entire suite before running 34 valid tests. | **Fixed** (`ExampleRobolectricTest.kt:19`, `app/build.gradle.kts:145-157`) |
| A4 | **Medium** | `VoiceQuizGameScreen` | **Skipping every phrase awarded `60%` clarity and `+60 XP`.** Extracted `calculateVoiceRoundResult`, stopped appending fake `60` scores on skip, and show `"Nothing to save"` / `0%` / `+0 XP` without writing to cloud when all prompts are skipped; pinned by `VoiceQuizGameScreenTest`. | **Fixed** (`VoiceQuizGameScreen.kt`, `VoiceQuizGameScreenTest.kt`) |
| A5 | **Medium** | `StudioHomeScreen` | **Cold-start state flash & layout shift.** Added `uid`-scoped `ProfileSummaryCache` hydrated before Firestore emission, cleared on sign-out, and replaced placeholder `"Hi Learner"` / `0 XP` flash with a neutral loading state when uncached; pinned by `ProfileSummaryCacheTest`. | **Fixed** (`ProfileSummaryCache.kt`, `StudioViewModel.kt`, `StudioHomeScreen.kt`, `AppNavigation.kt`) |
| A6 | **Medium** | `StoryGameScreen` | **Gemini turn timeouts & unstyled loading reply chips.** Replaced Gemini in Story Café with **100 curated offline branching stories** (34 Japanese, 33 Spanish, 33 French) and **600 pre-generated Kokoro `.m4a` host audio clips** with instant deterministic branching, story picker, `Next Story`, and zero network timeouts; pinned by `StoryCatalogTest` & `StoryGameScreenTest`. | **Fixed** (`StoryCatalog.kt`, `StoryGameScreen.kt`, `assets/stories/*.json`, `assets/audio/stories/*/*.m4a`) |
| A7 | **Low** | `ProgressScreen` | **Pluralization bug (`"1 games"` / `"1 days"`).** Added `formatCountLabel` for proper singular/plural formatting (`"1 game"`, `"1 day"`); pinned by `ProfileSummaryCacheTest`. | **Fixed** (`ProgressScreen.kt`, `ProfileSummaryCacheTest.kt`) |
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
