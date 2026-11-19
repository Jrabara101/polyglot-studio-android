# Polyglot Studio — Interactive Language Game App

An interactive Android language-learning game studio that helps English speakers master **Japanese**, **Spanish**, and **French** through five skill-focused games paired with an AI Speech Guide and real-time Gemini Tutor Hub, with cloud-synced progress via Google Sign-In.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> **Confirmed Phase 1 Decisions** — All four product preferences from your clarification responses are locked into this plan:
> - **Visual Identity**: *Playful Tactile Studio* — warm paper canvas, crisp ink-bordered tactile cards with subtle offset depth, and vibrant language-coded accents.
> - **App Structure**: *5 Core Games + Dedicated AI Speech & Tutor Hub* — every game targets a distinct linguistic skill, complemented by an interactive Speech Guide and multi-turn + Live Voice AI Tutor.
> - **Persistence & Auth**: *Cloud Sync with Google Sign-In* — Firebase Authentication (Jetpack Credential Manager) and Cloud Firestore sync XP, daily streaks, skill mastery, and saved phrases across devices.
> - **Progression**: *Open Access with XP & Streaks* — all five games, all three languages, and difficulty tiers (Beginner, Intermediate, Advanced) are immediately playable from day one.

> [!NOTE]
> **Upcoming Interactive Provisioning Step** — Once you approve this plan, the first build step will ask you to confirm Firebase provisioning (Firestore, Google Sign-In, and Firebase AI Logic + App Check) via the AI Studio setup dialog before any cloud or Gemini features are wired.

---

## 1. Overview & Core Concept

- **What It Does**: Polyglot Studio turns language comprehension into hands-on play. Learners keep English as their base UI language and switch freely between **Japanese (日本語)**, **Spanish (Español)**, and **French (Français)**. Each of the five games trains a separate cognitive and linguistic muscle—vocabulary association, script writing, grammar syntax, phonetic listening/speaking, and real-world conversational pragmatics—while the Speech & Tutor Hub provides phonetic drills, Gemini Text-to-Speech (`TTS`), multi-turn AI tutoring, and real-time voice conversations.
- **Target Audience**: Curious beginners and casual-to-intermediate learners who want tactile, varied, game-first practice instead of repetitive flashcard drills.
- **Key Value**: Combines structured game mechanics with live multimodal Gemini intelligence (vision grading for calligraphy, audio grading for voice quizzes, natural TTS, and live voice roleplay) while persisting every earned XP point and streak to the cloud.

### The 5 Skill-Distinct Games + Companion Hub

| Experience | Linguistic Skill Focus | Core Mechanic |
| :--- | :--- | :--- |
| **1. LexiMatch Cards** | Vocabulary, Orthography & Meaning Recall | Flip tactile paired cards matching target words/scripts with English meanings, enhanced by instant TTS pronunciation and streak combos. |
| **2. Ink & Stroke Studio** | Writing Systems & Script Recognition | Draw Japanese Kana/Kanji or Spanish/French diacritic phrases on a guided brush canvas; Gemini Vision evaluates stroke accuracy and form. |
| **3. Syntax Sprint Quiz** | Grammar, Particles & Sentence Structure | Rapid-fire contextual challenges (tile ordering, particle/tense selection, false friends) with AI-generated explanations for every answer. |
| **4. Echo Voice Arena** | Listening Comprehension & Pronunciation | Hear spoken prompts via Gemini TTS, speak your response into the mic, and get phonetic accuracy scoring and articulation tips. |
| **5. Story Café Dialogues** | Pragmatics, Discourse & Cultural Context | Branching illustrated cultural scenarios (Tokyo ramen shop, Madrid train station, Paris café) with spoken NPC audio and dynamic AI turns. |
| **Speech & Tutor Hub** | Phonetics & Freeform Conversation | Interactive soundboard & articulation guide, plus a multi-turn Gemini chat tutor with a real-time Gemini Live Voice conversation mode. |

---

## 2. User Experience & Visual Design

### Key User Flows

1. **Onboarding & Google Sign-In Gate**:
   - Cold start runs silent Google credential check (`GetGoogleIdOption`). If no session exists, a tactile welcome screen introduces the three languages and five games alongside a prominent **Sign in with Google** button (`GetSignInWithGoogleOption`).
   - First-time users land directly on the **Studio Home** with all games unlocked and an active language selector defaulting to Japanese (switchable to Spanish or French in one tap).
2. **Studio Home & Game Launch**:
   - Top bar displays the active language pill (`JP` / `ES` / `FR`), daily streak flame, and total XP counter.
   - Difficulty chip selector (`Beginner`, `Intermediate`, `Advanced`) updates challenges across all games.
   - Asymmetric bento grid of **5 Tactile Game Cards**, each stamped with its target linguistic skill tag (e.g., *Vocabulary*, *Writing*, *Grammar*, *Phonetics*, *Pragmatics*) and current mastery progress bar.
3. **In-Game Loop**:
   - Launching any game opens a distraction-free arena with a clear progress meter, tactile controls, immediate audio/visual feedback, optional AI hint/explanation drawer, and a **Session Complete** celebration modal showing XP earned, accuracy, and streak update synced to Firestore.
4. **Speech Guide & AI Tutor Hub**:
   - **Speech Guide**: Filterable phonetic grid (e.g., Japanese Hiragana/tricky syllables, Spanish `ñ`/`rr`/vowels, French nasal vowels/`u`/`r`), slow-vs-normal TTS playback, mouth-shape tips, and a "Try Saying It" mic check.
   - **AI Tutor**: Multi-turn chat thread with language-specific tutor persona, quick-starter chips ("Correct my grammar", "Quiz me on café phrases", "Explain polite vs casual"), streaming replies, tap-to-hear TTS on any tutor message, and a **Live Voice Call** sheet for real-time bidirectional Gemini Live conversations.
5. **Progress & Profile**:
   - Skill mastery breakdown across the 5 linguistic pillars per language, recent game session log, saved vocabulary/phrase bookmarks, and account sign-out.

### Visual Identity & Theme (*Playful Tactile Studio*)

- **Aesthetic Direction**: Tactile stationery-meets-arcade studio—crisp rounded surfaces that feel like physical card stock and ink stamps rather than generic flat cards.
- **Color Palette & Mood**:
  - *Studio Canvas (`background` / `surface`)*: Warm Parchment `#FAF7F2` (Light) and Deep Charcoal Ink `#17161B` (Dark), with elevated pure-cream `#FFFFFF` / `#22202A` tactile cards.
  - *Ink & Structure (`outline` / `onSurface`)*: Espresso Ink `#1E1B24` used for crisp `1.5dp–2dp` borders and tactile bottom-offset shadows (`3dp` solid drop layers).
  - *Language-Coded Primary Accents*:
    - **Japanese**: Persimmon Vermilion `#E84A3C` with Sakura Mist `#FFE8E5`
    - **Spanish**: Marigold Saffron `#E89B1C` with Sunlit Cream `#FFF3D6`
    - **French**: Riviera Cobalt `#2F6FED` with Sky Tint `#E6EEFF`
  - *Semantic Feedback*: Mint Emerald `#1EA87A` (correct/streak), Coral Red `#D93848` (retry), Violet Spark `#7C4DFF` (AI Tutor & Gemini intelligence badges).
- **Typography & Hierarchy**:
  - Bundled locally via `font-util` into `res/font/`:
    - **Display / Game Titles**: *Fredoka* (friendly, rounded studio character)
    - **Body / UI Copy**: *Plus Jakarta Sans* (crisp geometric readability across Latin & multilingual UI)
    - **Phonetics / Romaji / Scores**: *JetBrains Mono* (tabular precision for XP, timers, and IPA/romaji notation)
- **Component Styling, Motion & Adaptive Layout**:
  - Custom `TactileCard` and `TactileButton` primitives with spring press-down physics (`animateDpAsState` compressing the bottom shadow offset on tap) and Material ripple feedback.
  - Bottom `NavigationBar` (`Studio`, `Speech`, `Tutor`, `Progress`) on compact phones, automatically adapting to a `NavigationRail` and two-pane layouts (e.g., list + detail in Story/Speech) on `Medium`/`Expanded` tablets and foldables, with full edge-to-edge `WindowInsets` support.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Dedicated AI Speech & Tutor Hub Alongside 5 Skill-Focused Games**
  - *Chosen Approach*: Organize the five distinct interactive games on the main Studio arena (Card Match, Calligraphy Ink, Syntax Sprint, Voice Quiz, Story Café) while elevating the Speech Guide and Gemini Chatbot/Live Voice into dedicated top-level tabs.
  - *Why*: Keeps each of the 5 games crisp, replayable, and mapped 1:1 to a core linguistic skill without crowding the game count or burying the conversational tutor.
  - *Alternatives Considered*: Merging Speech Guide into Voice Quiz—rejected because phonetic reference tables and articulation guides work best as an always-accessible utility tab.

- **Decision 2: Hybrid Curated Core + Dynamic Gemini Generation**
  - *Chosen Approach*: Bundle curated starter decks, calligraphy character templates, phonetic guides, and story openers for instant zero-latency launches, while calling Gemini (`gemini-3.8-flash` / `gemini-3.5-flash-lite`) for fresh quiz sets, calligraphy bitmap grading, spoken audio evaluation, dynamic story branches, TTS, and live chat.
  - *Why*: Ensures every game opens instantaneously even before the first AI call finishes, while delivering genuinely adaptive, intelligent feedback instead of static templates.
  - *Alternatives Considered*: 100% cloud-generated content on every screen open—rejected due to startup wait times on mobile networks.

- **Decision 3: Multi-Model Gemini Routing via Firebase AI Logic**
  - *Chosen Approach*: Route all Gemini calls through `Firebase.ai` protected by Firebase App Check (no hardcoded API keys):
    - `gemini-3.5-flash-lite` for rapid card hints, grammar micro-explanations, and quick structured quiz generation.
    - `gemini-3.8-flash` for multimodal calligraphy canvas bitmap evaluation, voice audio comprehension/pronunciation grading, story branching, and the multi-turn AI Tutor chat.
    - `gemini-3.1-flash-tts-preview` (with graceful AudioTrack playback) for natural native-accent Text-to-Speech across cards, quizzes, stories, and the Speech Guide.
    - Gemini Live API (`liveModel` with `ResponseModality.AUDIO` + `AudioRecord`/`AudioTrack`) for hands-free real-time voice tutor conversations.
  - *Why*: Balances low latency, cost efficiency, and multimodal richness without exposing API keys in the APK.

- **Decision 4: User-Scoped Cloud Sync with Google Sign-In & Firestore**
  - *Chosen Approach*: Gate app access behind Jetpack Credential Manager Google Sign-In and persist user profile, per-language mastery stats, game session scores, and saved phrases under user-isolated Firestore subcollections (`/users/{userId}/...`).
  - *Why*: Guarantees streaks, XP, and mastered words survive device changes and reinstalls while enforcing zero-trust per-user data isolation in `firestore.rules`.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

### Architecture & Component Diagram

```text
┌───────────────────────────────────────────────────────────────────────────┐
│                         MainActivity (Edge-to-Edge)                       │
│   1. configureAppCheck(this, intent)  ──►  2. AuthGate (CredentialMgr)    │
└───────────────────────────────────┬───────────────────────────────────────┘
                                    │ Authenticated (uid)
                                    ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                  AppNavigation (Type-Safe @Serializable)                  │
│   Studio Home  │  5 Game Arenas  │  Speech Guide  │  AI Tutor  │ Progress │
└───────┬──────────────────┬───────────────┬──────────────┬─────────────────┘
        │                  │               │              │
        ▼                  ▼               ▼              ▼
┌───────────────┐  ┌───────────────┐  ┌──────────┐  ┌───────────────────────┐
│ Studio &      │  │ 5 Game        │  │ Speech & │  │ Progress & Profile    │
│ Progress VM   │  │ ViewModels    │  │ Tutor VM │  │ ViewModel             │
└───────┬───────┘  └───────┬───────┘  └────┬─────┘  └───────────┬───────────┘
        │                  │               │                    │
        └──────────┬───────┴───────────────┴──────────┬─────────┘
                   ▼                                  ▼
┌─────────────────────────────────────┐  ┌──────────────────────────────────┐
│        FirebaseAiRepository         │  │       ProgressRepository         │
│  • Structured JSON (Quiz/Stories)   │  │  • UserProfile & Streaks         │
│  • Multimodal Vision (Calligraphy)  │  │  • LanguageMastery (JP/ES/FR)    │
│  • Multimodal Audio (Voice Quiz)    │  │  • GameSessionLogs               │
│  • Streaming Chat & LiveSession     │  │  • SavedPhrases                  │
│  • Gemini TTS + AudioTrack Engine   │  │  (Firestore Named DB + Rules)    │
└─────────────────────────────────────┘  └──────────────────────────────────┘
```

### Data Model & Cloud Firestore Schema

All user progress is stored in the provisioned named Firestore database (`R.string.firestore_database_id`) and isolated strictly by `request.auth.uid == userId`:

- **`/users/{userId}` (`UserProfile`)**: Display name, active target language (`japanese` | `spanish` | `french`), preferred difficulty (`beginner` | `intermediate` | `advanced`), total XP, current streak days, last active date string, timestamps.
- **`/users/{userId}/mastery/{languageCode}` (`LanguageMastery`)**: Per-language skill scores (`vocabularyScore`, `writingScore`, `grammarScore`, `phoneticsScore`, `pragmaticsScore`), total XP in that language, games played count, updated timestamp.
- **`/users/{userId}/sessions/{sessionId}` (`GameSessionRecord`)**: Game type (`cards` | `calligraphy` | `quiz` | `voice` | `story`), language code, difficulty, score percent, XP awarded, duration seconds, created timestamp.
- **`/users/{userId}/savedPhrases/{phraseId}` (`SavedPhrase`)**: Target text, romaji/phonetic guide, English translation, language code, source game, created timestamp.

### Interactive Component & State Mapping

- **Auth & Lifecycle**: `AuthGate` listens to `Firebase.auth` state and runs silent `GetGoogleIdOption` auto-sign-in on launch; if unavailable, displays the Playful Tactile Studio login card with an interactive `GetSignInWithGoogleOption` button and proper `GetCredentialCancellationException` recovery. Feature ViewModels are keyed by `uid` so account switching cleanly resets state.
- **Game 1 — LexiMatch Cards**: Card deck state machine (`Hidden` -> `Flipped` -> `Matched`/`MismatchShake`) tracks pair selection, combo multiplier, and triggers Gemini TTS audio on reveal or speaker tap.
- **Game 2 — Ink & Stroke Studio**: Touch-path `Canvas` records user ink strokes, renders an offscreen `Bitmap` of the drawn character against faint guide lines, and submits it via `content { image(bitmap); text(...) }` with `responseSchema` JSON grading (score 0–100, stroke feedback, tip). Includes undo, clear, ghost toggle, and next character controls.
- **Game 3 — Syntax Sprint Quiz**: Sealed `QuizUiState` manages curated + AI-generated questions (`responseSchema`), countdown/streak bonus, answer lock-in, and expandable AI grammar explanation drawer.
- **Game 4 — Echo Voice Arena**: Runtime `RECORD_AUDIO` permission flow (`ActivityResultContracts.RequestPermission`) gates an `AudioRecord` PCM capture controller with live amplitude waveform visualization. Plays prompt via Gemini TTS, captures user speech, sends audio bytes to Gemini for transcription + pronunciation score + tips.
- **Game 5 — Story Café Dialogues**: Branching turn-based dialogue state machine displays NPC speech bubbles with TTS playback, user reply choices or custom input, and AI-evaluated naturalness + next story turn via structured JSON.
- **Speech & Tutor Hub**:
  - *Speech Guide*: Language-filtered phoneme cards with IPA/romaji, articulation tips, normal/0.75x TTS playback, and quick mic pronunciation check.
  - *AI Tutor*: `startChat()` streaming thread (`generateContentStream()`) with language-specific system instruction, message bubbles with tap-to-speak TTS, plus a **Live Voice** modal using `Firebase.ai.liveModel(...)` + `AudioRecord`/`AudioTrack` for real-time spoken back-and-forth with interruption support and clean session teardown.

### Security, Permissions & Verification Plan

- **Permissions (`AndroidManifest.xml`)**: Only `INTERNET` and `RECORD_AUDIO` (requested dynamically in Compose exclusively when entering Voice Quiz, Speech Guide mic check, or Live Voice Tutor, with graceful text fallback if denied).
- **Zero-Trust Security & Testing**:
  - Author `firebase-blueprint.json`, `firestore.rules` (owner-only reads/writes, strict key allowlists, size bounds, server timestamp checks), and `firebase.json` bound solely to the provisioned named database ID.
  - Verify security rules locally against Firebase Auth & Firestore emulators using both `firestore.test.js` (`node --test`) and Robolectric Kotlin `*RuleTest.kt` suites before calling `DeployRules`.
  - Verify App Check launch intent wiring (`configureAppCheck(this, intent)`) in `MainActivity.onCreate()` before `setContent` and verify compilation with `compile_applet`.
