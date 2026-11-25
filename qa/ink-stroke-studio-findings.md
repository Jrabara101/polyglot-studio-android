# Ink & Stroke Studio: QA findings

Feature: `app/src/main/java/com/example/ui/games/CalligraphyGameScreen.kt`
(grading: `FirebaseAiRepository.evaluateCalligraphy`). Tested 2026-11-23 on emulator
`Medium_Phone_API_37.0` (Android 17), debug build, Japanese / Beginner.

Status legend: **Open** = found, not fixed yet. **Fixed** = fixed and verified (2026-11-24) by automated
tests and/or on the emulator. **Partly fixed** = see notes.

## Round 1: first pass (7 findings)

| # | Severity | Finding | Status |
|---|---|---|---|
| 1 | Critical | Grading always fails: "Grading…" for 60 s, then "Calligraphy evaluation timed out" | Fixed |
| 2 | High | A single tap on the pad does not draw a dot | Fixed |
| 3 | Medium | Drawing is stretched into a square before it is sent for grading | Fixed |
| 4 | Medium | No way to skip a character; learner is stuck when grading fails | Fixed |
| 5 | Low | Pass/fail verdict can disagree with the score | Fixed |
| 6 | Low | Every retry counts toward the final average and uses AI quota | Fixed |
| 7 | Low | Sound button reads symbols ("¿?", "ç") instead of a word or name | Fixed |

### 1. Grading always fails (Critical)
- **Steps:** Open Ink & Stroke Studio → draw あ → press **Check**.
- **Expected:** a score and feedback within a few seconds.
- **Actual:** spinner for 60 s, then "Calligraphy evaluation timed out. Please try again." Reproduced 2 of 2 tries.
- **Cause (confirmed):** the grading model `gemini-3.8-flash` is overloaded. The same request sent
  directly returned `503 UNAVAILABLE: This model is currently experiencing high demand`, and a retry hung
  for 150 s. The same request to `gemini-3.5-flash-lite` (already used by the quiz) answered in 2.3 s
  with a sensible score and tip.
- **Fix:** grade with `gemini-3.5-flash-lite` (or try it as a fallback), cut the timeout to ~20 s,
  show a friendly "grading is busy, try again" message.

### 2. Taps do not draw dots (High)
- **Steps:** tap once on the pad → press **Check**.
- **Actual:** "Draw 'あ' on the pad first!", so the tap was not recorded. Confirmed on emulator.
- **Cause:** the pad uses `detectDragGestures`, which ignores taps and the first few pixels of every
  stroke (touch slop).
- **Impact:** characters with dots or very short strokes (心 has three dots, Intermediate) cannot be written.
- **Fix:** handle raw pointer down/move/up so a tap becomes a dot and strokes start where the finger lands.

### 3. Drawing is stretched before grading (Medium)
- **Cause:** `renderStrokesToBitmap` scales x and y separately into a 384×384 square, but the pad is
  1.15:1. The image the AI grades is squeezed ~13% horizontally, and the prompt asks it to judge proportions.
- **Fix:** one uniform scale for both axes, centered in the square.

### 4. No way to skip a character (Medium)
- **Actual:** **Next** appears only after a grading result. When grading fails (finding 1), the learner
  cannot move past 1/4.
- **Fix:** add a **Skip** button (skipped items don't count toward the score).

### 5. Verdict can disagree with score (Low)
- **Cause:** `isPass` comes from the AI separately from `score`, so "Great Form! 40/100" is possible.
  The prompt's own rule is pass when score ≥ 65.
- **Fix:** compute `isPass = score >= 65` in the app.

### 6. Retries inflate or deflate the average (Low)
- **Cause:** each **Check** appends to `scores`, so retrying one character several times weights it more
  and spends more AI quota.
- **Fix:** keep only the best score per character.

### 7. Sound button reads symbols (Low)
- **Cause:** the button speaks `currentItem.character` (e.g. "¿?", "ñ", "ç") in slow mode. These have no
  recorded clip, so they go to Gemini TTS (free tier: 3 requests per minute) and then the phone voice.
- **Fix:** speak the item's name or example (e.g. "interrogación", "c cédille", "mañana").

## Round 2: deeper testing (8 more findings)

How it was tested:
- **Screen tests** (Compose UI tests on Robolectric, the Android equivalent of Playwright):
  `app/src/test/java/com/example/ui/games/CalligraphyGameScreenTest.kt`. 5 tests pass; 3 `BUG_` tests fail
  on purpose and will pass once findings 2, 7 and 8 are fixed.
- **Emulator**: drawing, rotation, landscape, and Spanish / French / Japanese at every level.
- **Code review** of the result, Next/Finish and save steps. These could not be reached on the emulator
  because grading is down (finding 1); the grading model still returned `503` at the end of testing.

| # | Severity | Finding | How found | Status |
|---|---|---|---|---|
| 8 | High | Rotating the phone erases the drawing and resets progress to 1/N | Screen test + emulator | Fixed |
| 9 | High | Landscape: the pad is cut off; Check button and guide are off-screen | Emulator | Fixed |
| 10 | High | App can crash when saving the result fails (e.g. offline) | Code review | Fixed |
| 11 | Medium | The AI only sees the finished picture, so it cannot check stroke order or direction | Code review | Partly fixed |
| 12 | Medium | Very few characters per level (French/Spanish Advanced: 1, Intermediate: 2) | Emulator + code | Fixed |
| 13 | Low | Final score is floored at 40% and defaults to 75% | Code review | Fixed |
| 14 | Low | Undo/Clear stay active while grading, so the pad can change under the AI | Code review | Fixed |
| 15 | Low | Test tags sit on the button wrapper, not the clickable part | Screen tests | Fixed |

### 8. Rotation erases the drawing (High)
- **Steps:** draw two strokes → rotate to landscape → rotate back.
- **Actual:** pad is empty, Undo/Clear greyed out. Also the round restarts at 1/N and earlier scores are lost.
- **Cause:** `strokes`, `index`, `scores` and `evaluation` use `remember`, and `MainActivity` is recreated on rotation.
- **Fix:** keep the game state in a ViewModel (or `rememberSaveable` with a saver for strokes).
- **Test:** `BUG_drawing_survives_screen_rotation`.

### 9. Landscape layout unusable (High)
- **Actual:** on a landscape phone the pad is ~1700 px wide and 1.15:1, so it is taller than the screen. The
  guide character and the Check button are below the fold, and scrolling on the pad draws instead of scrolling.
- **Fix:** size the pad to the smaller of width and available height, or use a side-by-side layout in landscape.

### 10. Crash when saving the result fails (High)
- **Where:** `CalligraphyGameScreen.kt:457`. The Finish button runs
  `scope.launch { try { onCompleteSession(...) } finally { ... } }` with no `catch`.
  `ProgressRepository.recordGameCompletion` rethrows Firestore errors, so an offline save or a rules error
  becomes an uncaught exception in the screen's coroutine scope, which crashes the app.
- **Not reproduced yet** (Finish cannot be reached while grading is down).
- **Fix:** catch the error, keep the result on screen, and show "Couldn't save, tap to retry".

### 11. Stroke order and direction are not checked (Medium)
- The hints teach stroke order ("center hook first…"), and the prompt asks the AI about "stroke structure",
  but only a flat image is sent. A character drawn in the wrong order or direction can still pass.
- **Fix options:** send the ordered stroke list (start/end points, direction) with the image, or color-code
  strokes by order in the image, or check order locally against stroke data.

### 12. Very few characters per level (Medium)
- Japanese 4 / 3 / 2, Spanish 3 / 2 / 1, French 3 / 2 / 1 (Beginner / Intermediate / Advanced). A French
  Advanced round is a single word.
- **Fix:** add items, or reuse the 300-word lexicon (kana/kanji for Japanese, accented words for ES/FR).

### 13. Score floor and default (Low)
- `CalligraphyGameScreen.kt:452`: `avg = if (scores.isEmpty()) 75 else ... .coerceIn(40, 100)`. A learner who
  scored 10 is saved as 40%; with no scores the saved value would be 75%. This inflates `writingScore` on the
  Progress screen.
- **Fix:** save the real average (0–100).

### 14. Pad can be changed during grading (Low)
- Undo and Clear stay enabled while "Grading…" runs (`line 351`), so the learner can clear the pad and then
  see a score for a drawing that is no longer there.
- **Fix:** disable Undo/Clear and drawing while grading.

### 15. Test tags on the wrong element (Low, testability)
- `TactileButton` puts the caller's `modifier` (with its test tag) on an outer `Box`; the clickable,
  enabled/disabled part is an inner `Surface`. Tests and automation that look up `undo_stroke_button`
  see an element that is always "enabled". Real users and TalkBack are not affected.
- **Fix:** apply the test tag to the clickable surface (or merge semantics on the wrapper).

## Not bugs (checked and fine)
- Empty-pad Check shows "Draw 'X' on the pad first!".
- Guide toggle, Undo and Clear work.
- All 9 Japanese stroke counts and hints are correct; Spanish and French prompts fit on screen.
- Check is disabled while grading, so double taps don't send two requests.

## Round 3: fixes (2026-11-24)

All 15 findings were fixed in `CalligraphyGameScreen.kt`, `FirebaseAiRepository.kt`, `CuratedContent.kt`,
`GameResultOverlay.kt` and `TactileComponents.kt`. **Tests:** 24 automated tests in
`CalligraphyGameScreenTest.kt` + `CalligraphyGradingLogicTest.kt`, all passing.

| # | Fix | Verified |
|---|---|---|
| 1 | Grades with `gemini-3.5-flash-lite`, falls back to `gemini-3.8-flash`; 20 s per model; friendly "Grading is busy… or tap Skip" message | Emulator: real grading answered in ~8–12 s (3 of 3 tries). Test for the busy message |
| 2 | Raw pointer handling: a tap is a dot; strokes start where the finger lands | Emulator: a single tap was graded as a dot. Test |
| 3 | Even scale, centered (`padTransform`); strokes stored as 0–1 coordinates | Test |
| 4 | **Skip this one / Skip and finish** button; skipped items don't count | Emulator: skipped 5 of 6 and finished. Tests |
| 5 | App sets pass = score ≥ 65; a missing score triggers the backup model | Emulator: 60/100 shown as "Keep Practicing". Tests |
| 6 | Best score per character only | Test (50/90/70 → 90) |
| 7 | Symbols read by name ("ñ" → "eñe", "¿?" → "interrogación"); words and Japanese read as written | Tests |
| 8 | Game state in `rememberSaveable` (drawing, progress, scores, result) | Emulator: drawing and 60/100 result kept through portrait → landscape → portrait. Tests |
| 9 | Landscape: pad on the left at full height, prompt/controls/result on the right | Emulator screenshot. Landscape test (pad, Check, Skip all on screen) |
| 10 | Save errors are caught; banner "Couldn't save your result…" + **Retry save**; card says "Not saved yet" | Emulator: finished while offline, no crash, retry after reconnecting saved "Japanese Calligraphy (60% avg, 1/6 graded)" to Progress. Test |
| 11 | **Partly fixed.** The app now checks the stroke **count** itself ("Strokes: you drew 3 of 3 ✓" / "You drew 2 strokes; 'あ' has 3."). The AI is told not to judge stroke order. | Tests. See note below |
| 12 | 3–6 characters per level (was 1–4); 12 new prompts incl. い, 人, 本, 雨, 書, 愛, ü, Canción, Pingüino, Murciélago, Añoranza, ê, Noël, Hôtel, Cœur, Garçon | Test (every level ≥ 3) |
| 13 | Real average saved (no 40% floor, no 75% default); all skipped → nothing saved, card says "Nothing to save" | Emulator (60% avg). Tests |
| 14 | Undo, Clear, Skip and drawing are locked while grading | Test |
| 15 | `TactileButton` merges its clickable's semantics and marks itself disabled, so tags hit the real button | Tests |

### Note on 11 (stroke order)
Sending the ordered stroke list to the AI was tried first. On the emulator the fast model said "the stroke
order is incorrect (horizontal stroke before the vertical stroke)" for a あ drawn in the **correct** order,
in 2 of 2 tries, even with explicit instructions. Wrong feedback is worse than none, so the AI no longer
comments on order; the stroke **count** is checked by the app instead. Real stroke-order checking needs
per-character stroke data (for example KanjiVG for Japanese, CC BY-SA) and is a future improvement.

### New issues found while verifying
| # | Severity | Finding | Status |
|---|---|---|---|
| 16 | Medium | Fast model gave wrong stroke-order feedback (see note on 11) | Fixed (order no longer judged by AI) |
| 17 | Low | Result card said "Synced to Cloud" even when the save had failed | Fixed: shows "Not saved yet" / "Nothing to save". Test |
| 18 | n/a | Emulator graphics crashed (`SIGILL` in `libhwui` RenderThread) in both this app and the Google app after many restarts | Not an app bug; fixed by restarting the emulator |

### Not covered by tests
- Real grading quality depends on the AI model; scores for the same drawing can vary.
- The 5 older tests that fail (`ExampleRobolectricTest` expects the name "My Application";
  `ProgressRepositoryRuleTest` needs the Firebase emulators running) are unrelated and were failing before.
