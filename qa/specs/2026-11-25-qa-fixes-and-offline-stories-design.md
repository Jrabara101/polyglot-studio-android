# Design: Critical QA Fixes & Offline Story Café (100 Stories + Kokoro Audio)

Date: 2026-11-25
Status: Approved
Source QA audit: `qa/full-app-and-website-qa-findings.md`

---

## 1. Intent & Why

Two problems surfaced during the 2026-11-25 website + Android QA pass and live emulator testing:

1. **Cross-app UI & stability defects:**
   - Every full-width or weighted `TactileButton` renders a protruding solid black shadow bar on its right side (visible on Studio Home, Echo Voice Arena, and Speech & Sound Guide).
   - Four games (`CardMatchGameScreen`, `QuizGameScreen`, `VoiceQuizGameScreen`, `StoryGameScreen`) call `onCompleteSession(...)` inside a coroutine with no `catch` block, risking uncaught crashes when saving offline.
   - `VoiceQuizGameScreen` awards a fake `60%` clarity score and `+60 XP` even when the learner skips every phrase.
   - Minor UX/polish and website issues remain (`StudioHomeScreen` cold-start state flash, `ProgressScreen` `"1 games"`/`"1 days"` pluralization, website desktop CLS, lazy-image dimensions issue, relative `og:image`, `<h1>` span concatenation, and small mobile footer tap target).

2. **Story Café timeouts on Gemini:**
   - Live Story Café calls `FirebaseAiRepository.continueStoryTurn(...)` on every turn, which times out under Gemini demand (`"Story turn timed out. Please try again."`), blocking gameplay.
   - Replacing Gemini in Story Café with **100 curated offline branching stories** across Japanese, Spanish, and French — paired with **pre-generated offline Kokoro `.m4a` host audio** using the project's existing Kokoro TTS pipeline — makes Story Café instant, deterministic, and 100% playable offline with zero AI quota usage.

---

## 2. Goals, Non-Goals, and Anti-Goals

### Goals
1. **Fix `TactileButton` layout (`TactileComponents.kt`):**
   - Full-width (`Modifier.fillMaxWidth()`) and weighted (`Modifier.weight(1f)`) `TactileButton`s must stretch their foreground `Surface` to match the shadow backdrop edge-to-edge.
   - Wrap-content `TactileButton`s (e.g., `Guide`, `Undo`, `Clear`, `Check`, `Sign Out`, `Talk`, `Fresh AI Set`, `Ask Gemini Why`, `Next`) must remain compact and not expand unexpectedly.
   - Verified with a Compose UI layout test covering both constrained-width and wrap-content `TactileButton`s, plus visual verification on the Android 17 emulator.
2. **Fix remaining critical/high/medium Android QA anomalies:**
   - **Offline save crash protection (`A2`):** Wrap `onCompleteSession` in `CardMatchGameScreen`, `QuizGameScreen`, `VoiceQuizGameScreen`, and `StoryGameScreen` with `try / catch (CancellationException -> rethrow) / catch (Exception)` and display retryable save failure feedback via `GameCompletionCard`'s `syncLabel` + a retry button (matching `CalligraphyGameScreen`).
   - **Voice Arena skip scoring (`A4`):** Skipping a phrase must NOT append a fake `60` score. Only graded phrases count; skipping all phrases shows a non-saving summary (`"Nothing to save"`, `0 XP`).
   - **Studio Home cold-start flash (`A5`):** Cache last-known profile summary locally in `SharedPreferences` keyed by `uid` (`activeLanguage`, `difficulty`, `displayName`, `totalXp`, `currentStreak`), hydrated immediately in `StudioViewModel` so returning learners get accurate language theming, stats, and offline cold-start access with zero flash; clear cache on sign-out, and show a clean neutral loading state only on a brand-new uncached account while the first Firestore snapshot loads. *(Builder/design call)*
   - **ProgressScreen pluralization (`A7`):** Format `"1 game"` / `"N games"` and `"1 day"` / `"N days"` accurately.
3. **Fix website QA anomalies (`W1`–`W6`, `W8` in `docs/index.html`):**
   - Add explicit CSS `width: 100%; aspect-ratio: 1000 / 558` on `.tutor img` to clear Chrome DevTools' lazy-loaded image dimensions warning.
   - Reserve `min-height` on `#grid` (`.grid`) to reduce desktop CLS when JS populates the 6 tiles.
   - Update `og:image` to an absolute HTTPS URL (`https://polyglot-studio-beryl.vercel.app/assets/img_welcome_studio.webp`) and add `og:url`, `og:type`, and `twitter:card`.
   - Insert whitespace between the two `<span class="l">` elements inside `<h1>` so `textContent` reads `"Play your way to fluency."`.
   - Increase mobile footer link tap area to `>= 44px` height and remove unused CSS selectors (`.nav .btn span.long`, `.sr`).
4. **Offline Story Café (Approach A — 100 stories total + Kokoro host audio, Japanese first):**
   - Remove all Gemini calls (`continueStoryTurn` and Gemini TTS fallback for bundled host lines) from `StoryGameScreen`.
   - Bundle curated branching stories in `app/src/main/assets/stories/<language>.json` loaded lazily via a new `StoryCatalog` initialized in `MainActivity.onCreate` (matching `Lexicon`).
   - **Distribution (100 stories total — built Japanese first, shipped straight through across all 3 languages):**
     - **Step 1 (Japanese first):** **34 Japanese stories** (12 Beginner, 11 Intermediate, 11 Advanced) + **204 Kokoro `.m4a` clips** (`lang_code="j"`, voice `jf_alpha`, speed `0.9`, spoken from kana `reading` so kanji is never mispronounced), verified first.
     - **Step 2 (Spanish & French):** **33 Spanish stories** (11/11/11, `lang_code="e"`, voice `ef_dora`, **198 clips**) + **33 French stories** (11/11/11, `lang_code="f"`, voice `ff_siwis`, **198 clips**), shipping all **100 stories and 600 Kokoro clips** together with strict 3-language validation.
   - **Story graph per story (6-node diamond DAG — design call):** 3 learner decision turns with 6 NPC host nodes (`n0`, `n1a`, `n1b`, `n2a`, `n2b`, `n3`):
     - `n0` (opening host line) → 2 learner choices leading to distinct Turn-1 replies `n1a` and `n1b`.
     - Both `n1a` and `n1b` offer 2 Turn-2 choices that map cleanly into two distinct Turn-2 follow-up nodes (`n2a`, `n2b` — diamond reconvergence on the next conversational beat, authored so each Turn-2 host line reads naturally from either Turn-1 path).
     - Both `n2a` and `n2b` offer 2 final choices leading into `n3` (closing host farewell, `choices: []`), completing the 3-turn story.
     - Scale: 6 NPC nodes × 34 JP stories = **204 clips** (~3.5 MB); across all 100 stories = **600 clips** (~10 MB, matching the existing 600 lexicon clips = 9.2 MB).
   - **Story selection & UI:**
     - Replace the hardcoded `.first()` scenario lookup with a clean in-screen story picker/stepper (showing story list or `"Story 1/12"` selector for the active language + difficulty, plus `"Next Story"` on completion).
     - Remove the Gemini free-text input box (`"Or type your own reply…"`), keeping instant branching choice cards, naturalness score + pragmatic feedback banner, host audio playback button, and phrasebook bookmarking.
     - Audio lookup checks `StoryCatalog` (direct `audioAsset` path per node, or text-normalized fallback) in `StudioViewModel.speakText` before falling back to `DeviceSpeech` offline TTS (never blocking Story Café on Gemini TTS).
   - **Kokoro story audio generator (`tools/generate_story_audio_kokoro.py`):**
     - Uses `tools/.venv-kokoro/Scripts/python.exe`, `KPipeline(lang_code=code, repo_id="hexgrad/Kokoro-82M")`, voices `jf_alpha` (JP), `ef_dora` (ES), `ff_siwis` (FR), default `--speed 0.9`, ffmpeg AAC 48 kbps mono `.m4a`.
     - Reads `app/src/main/assets/stories/<language>.json` and outputs `app/src/main/assets/audio/stories/<language>/<story_id>_<node_id>.m4a`.
     - For Japanese, synthesizes from each node's `reading` (kana) field rather than raw kanji, matching `tools/generate_audio_kokoro.py`.
     - Supports `--lang`, `--test` (outputs to `tools/audio_test/stories/`), `--overwrite`, and `--speed`.
   - **Automated tests:**
     - `StoryCatalogTest` validating story counts per language/difficulty, valid graph links (every `nextNodeId` exists, no cycles, every path reaches a terminal closing node in 3 turns, non-blank readings/phonetics/English, and every NPC node has a matching bundled `.m4a` audio clip).
     - `StoryGameScreenTest` (Robolectric Compose UI test) verifying offline 3-turn playthrough, branching replies, story switching, completion XP, and offline save-error retry.
     - `TactileButtonTest` verifying constrained-width buttons match outer box width while wrap-content buttons stay compact.
     - `VoiceQuizGameScreenTest` (or scoring logic unit test) verifying skipped phrases do not award fake `60%` / XP and save failures do not crash.

### Non-Goals
- Do **not** remove Gemini from Ink & Stroke Studio grading, Echo Voice Arena speech evaluation, Syntax Sprint Quiz `"Fresh AI Set"`, or Lumi Live AI Tutor.
- Do **not** generate Kokoro audio for learner choice cards (only `LOCAL HOST` bubbles have speaker playback buttons).
- Do **not** change `tools/generate_audio_kokoro.py`'s existing lexicon workflow or overwrite existing lexicon `.m4a` clips.
- Do **not** publish a new GitHub Release APK as part of this task (unless requested separately).

### Anti-Goals
- Fixing `TactileButton` must not expand wrap-content buttons (`Guide`, `Undo`, `Clear`, `Check`, `Sign Out`, `Talk`, `Fresh AI Set`).
- Stories must not be repetitive fill-in-the-blank templates; every story must have a unique real-life cultural setting and natural dialogue appropriate to its difficulty level.
- Japanese story audio must never be synthesized from raw kanji without a kana `reading`.
- No regressions in existing 35 passing unit/Compose UI tests or GitHub Actions CI.

---

## 3. Approaches Considered

| Approach | Scope & Trade-offs | Decision |
|---|---|---|
| **A: 100 stories total (34 JP / 33 ES / 33 FR) + full Kokoro `.m4a` audio** | ~600 story audio clips (~10 MB APK delta, matching existing 600 lexicon clips = 9.2 MB). Every story is distinct and idiomatic, with instant offline playback and fast generation (~3 min for Japanese, ~8–10 min total). | **Selected** (starting with Japanese first) |
| **B: 100 stories per language (300 total) + full Kokoro `.m4a` audio** | ~1,800 audio clips (~28–35 MB APK delta, heavier git repo, ~25–30 min generation, higher template-repetition risk). | Rejected due to APK/repo bloat and authoring dilution |
| **C: 300 stories total + hybrid audio (Kokoro for starter set, Android TTS fallback for rest)** | Keeps APK leaner, but Android built-in TTS sounds noticeably uneven/robotic across stories and depends on user device voice packs. | Rejected — inconsistent audio quality |

---

## 4. Technical Decisions & Architecture

### 4.1 `TactileButton` Fix (`app/src/main/java/com/example/ui/components/TactileComponents.kt`)
- Set `propagateMinConstraints = true` on the outer `Box(modifier = modifier...)` in `TactileButton`.
- When a caller passes `Modifier.fillMaxWidth()` or `Modifier.weight(1f)`, the outer `Box` receives fixed `minWidth == maxWidth`, which propagates to the inner `Surface`, making `Surface` fill the exact same width as the `.matchParentSize()` shadow plate.
- When a caller passes no width modifier, `minWidth == 0`, so `Surface` wraps its content and the shadow plate wraps `Surface`.

### 4.2 Offline Save Safety Across All Games
- Extract or follow `CalligraphyGameScreen`'s `SaveState` (`Idle`, `Saving`, `Saved`, `Failed`) pattern in `CardMatchGameScreen`, `QuizGameScreen`, `VoiceQuizGameScreen`, and `StoryGameScreen`.
- Pass `syncLabel = when (saveState) { Failed -> "Not saved yet"; ... }` to `GameCompletionCard` and show a `"Retry Save"` button when `saveState == Failed` so offline play never crashes and never silently claims `"Synced to Cloud"`.
- In `VoiceQuizGameScreen`, track `scores` only when `evaluateSpokenAudio` succeeds; if `scores.isEmpty()` on Finish, display `0%` / `+0 XP` and `"Nothing to save"` without calling `onCompleteSession`.

### 4.3 Offline Story Data Model & Assets
- JSON files: `app/src/main/assets/stories/{japanese,spanish,french}.json`
- Schema per story object:
  ```json
  {
    "id": "jp_s_001",
    "level": "beginner",
    "title": "Midnight Ramen Counter",
    "setting": "A cozy lantern-lit ramen shop in Shimokitazawa, Tokyo.",
    "category": "Dining",
    "startNodeId": "n0",
    "nodes": [
      {
        "id": "n0",
        "npcText": "いらっしゃいませ！何名様ですか？",
        "npcReading": "いらっしゃいませ！なんめいさまですか？",
        "npcPhonetic": "I-ra-ssha-i-ma-se! Nan-mei-sa-ma de-su ka?",
        "npcEnglish": "Welcome! How many in your party?",
        "choices": [
          {
            "text": "ひとりです。カウンターでもいいですか？",
            "reading": "ひとりです。カウンターでもいいですか？",
            "phonetic": "Hi-to-ri de-su. Ka-un-taa de mo i-i de-su ka?",
            "english": "Just one. Is the counter okay?",
            "naturalness": 95,
            "feedback": "Polite and natural for solo dining at a ramen counter.",
            "nextNodeId": "n1a"
          },
          {
            "text": "ふたりです。おすすめのラーメンはどれですか？",
            "reading": "ふたりです。おすすめのラーメンはどれですか？",
            "phonetic": "Fu-ta-ri de-su. O-su-su-me no raa-men wa do-re de-su ka?",
            "english": "Two of us. Which ramen do you recommend?",
            "naturalness": 92,
            "feedback": "Great way to state party size and ask for a recommendation at once.",
            "nextNodeId": "n1b"
          }
        ]
      }
    ]
  }
  ```
- Every story uses a 6-node branching DAG (`n0` → `n1a`/`n1b` → `n2a`/`n2b` → `n3`), where `n3` has `choices: []` (terminal closing line).
- Audio clips live at `app/src/main/assets/audio/stories/<language>/<story_id>_<node_id>.m4a` and are indexed by `StoryCatalog` in two ways:
  1. Direct lookup by `(language, storyId, nodeId)` (exact, collision-free).
  2. Normalized text index (`audioAssetFor(language, text)`) used by `StudioViewModel.speakText` so bookmarked story phrases in `ProgressScreen`'s Phrasebook also play their bundled Kokoro clip offline!
- Fallback illustrations continue to use the language's cultural artwork (`img_story_japan`, `img_story_spain`, `img_story_france`) — no new image assets needed.

### 4.4 Story Café UI & Navigation (`StoryGameScreen.kt`, `AppNavigation.kt`)
- `StoryGameScreen` no longer takes `aiRepository`.
- Top area displays a compact story switcher row (`< Story 1/12 >` + tap-to-open story picker bottom sheet/dialog showing all stories for the current language & difficulty, with category badge and title).
- Selecting any choice immediately appends the learner's bubble, shows the choice's `naturalness` % + `feedback` tip card, and appends the branch's next `LOCAL HOST` bubble (with speaker icon playing the Kokoro `.m4a` clip and bookmark icon saving to Phrasebook).
- When the closing node (`choices.isEmpty()`) is reached after 3 turns, the bottom bar shows **`Complete Story (+XP)`** (recording average naturalness score and XP to Firestore with offline-safe retry) and **`Next Story`** on the completion card so learners can seamlessly continue through all stories.
- All 3 languages ship together with complete bundled JSON + Kokoro audio; `CuratedContent` retains only a minimal emergency fallback if assets fail to load in an uninitialized test environment.

---

## 5. Execution Order (Option 2: Straight-Through Delivery)

All work ships end-to-end in one release sequence (building and verifying Japanese stories + audio first before generating Spanish and French):

1. **Critical QA Fixes (Android + Website):**
   - Fix `TactileButton` (`propagateMinConstraints = true`), offline save-crash handling + retry across `CardMatchGameScreen`, `QuizGameScreen`, `VoiceQuizGameScreen`, and `StoryGameScreen`, `VoiceQuizGameScreen` skip scoring, `StudioViewModel`/`StudioHomeScreen` `uid`-scoped `SharedPreferences` cold-start cache, `ProgressScreen` pluralization, and `docs/index.html` CLS/lazy-image/`og:image`/`<h1>`/footer fixes.
2. **Offline Story Café Engine & Kokoro Story Audio Pipeline:**
   - Implement `StoryCatalog`, offline `StoryGameScreen` (story picker, `Next Story`, instant 3-turn branching replies, naturalness feedback, offline Kokoro playback), wire `StoryCatalog` into `MainActivity` and `StudioViewModel.speakText`, and create `tools/generate_story_audio_kokoro.py`.
3. **Japanese First (34 stories + 204 Kokoro `.m4a` clips):**
   - Author `app/src/main/assets/stories/japanese.json` (12 Beginner, 11 Intermediate, 11 Advanced, with kana `npcReading`), run `tools/generate_story_audio_kokoro.py --lang japanese`, and verify playback & graph integrity first.
4. **Spanish & French (33 + 33 stories + 396 Kokoro `.m4a` clips):**
   - Author `app/src/main/assets/stories/spanish.json` (11/11/11) and `app/src/main/assets/stories/french.json` (11/11/11), run `tools/generate_story_audio_kokoro.py --lang spanish` and `--lang french`.
5. **Verification & Ship:**
   - Run full Gradle unit + Robolectric Compose UI test suite (including strict 3-language `StoryCatalogTest`, `StoryGameScreenTest`, `TactileButtonTest`, and `VoiceQuizGameScreen` scoring/save tests).
   - Verify visually on the Android 17 emulator (TactileButton fixed across Studio Home, Echo Voice Arena, Speech Guide + offline Story Café Japanese/Spanish/French playthrough) and verify website fixes via Chrome DevTools + Lighthouse.
   - Commit, push to `origin/main`, and confirm GitHub Actions CI passes green.

**What's left to the builder:**
- Exact story plots/dialogue across the 34 Japanese, 33 Spanish, and 33 French scenarios (distinct everyday cultural settings, natural level-appropriate phrasing, accurate Japanese kana readings).
- Story picker modal/dialog layout and save-retry banner styling consistent with `LocalStudioPalette`.
- Shared helper extraction for game completion save-state management.
