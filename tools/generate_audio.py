"""Generate pronunciation audio for the lexicon with Gemini text-to-speech.

Reads app/src/main/assets/lexicon/<language>.json and writes, per entry:
    app/src/main/assets/audio/<language>/<id>.m4a      (the word)
    app/src/main/assets/audio/<language>/<id>_ex.m4a   (the example sentence)

Safe to re-run: clips that already exist are skipped, so after hitting the
free-tier daily limit just run it again the next day and it resumes.

Needs GEMINI_API_KEY in the project's .env file (never printed or logged) and
ffmpeg on PATH (WAV -> AAC .m4a, which every Android version can play).

Usage (from the project folder):
    python tools/generate_audio.py --lang japanese --limit 2     # quick test
    python tools/generate_audio.py                               # everything
"""

import argparse
import base64
import json
import os
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request
import wave
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LEXICON_DIR = ROOT / "app" / "src" / "main" / "assets" / "lexicon"
AUDIO_DIR = ROOT / "app" / "src" / "main" / "assets" / "audio"

# Same voices and locales the app already uses (FirebaseAiRepository.kt).
VOICES = {
    "japanese": ("Kore", "ja-JP", "standard Tokyo Japanese"),
    "spanish": ("Aoede", "es-ES", "Castilian Spanish from Spain"),
    "french": ("Charon", "fr-FR", "standard Parisian French"),
}

DEFAULT_MODEL = "gemini-3.1-flash-tts-preview"
API_URL = "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent"
SAMPLE_RATE = 24000  # Gemini TTS returns 24 kHz, 16-bit, mono PCM


class QuotaExhausted(Exception):
    pass


def load_api_key() -> str:
    key = os.environ.get("GEMINI_API_KEY", "").strip()
    env_file = ROOT / ".env"
    if not key and env_file.exists():
        for line in env_file.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if line.startswith("GEMINI_API_KEY="):
                key = line.split("=", 1)[1].strip().strip('"').strip("'")
    if not key:
        sys.exit("GEMINI_API_KEY not found. Add it to the .env file in the project folder.")
    return key


def synthesize(text: str, language: str, api_key: str, model: str) -> bytes:
    voice, locale, accent = VOICES[language]
    body = {
        "contents": [{"parts": [{"text": f"Read aloud slowly and clearly in {accent}: {text}"}]}],
        "generationConfig": {
            "responseModalities": ["AUDIO"],
            "speechConfig": {
                "languageCode": locale,
                "voiceConfig": {"prebuiltVoiceConfig": {"voiceName": voice}},
            },
        },
    }
    req = urllib.request.Request(
        API_URL.format(model=model),
        data=json.dumps(body).encode("utf-8"),
        headers={"Content-Type": "application/json", "x-goog-api-key": api_key},
        method="POST",
    )
    for attempt in range(3):
        try:
            with urllib.request.urlopen(req, timeout=120) as resp:
                data = json.loads(resp.read())
            break
        except urllib.error.HTTPError as e:
            detail = e.read().decode("utf-8", "replace")
            if e.code == 429:
                if "PerDay" in detail or "per day" in detail.lower():
                    raise QuotaExhausted("Daily free-tier limit reached.")
                wait = 60 * (attempt + 1)
                print(f"  rate limited, waiting {wait}s...")
                time.sleep(wait)
                continue
            if e.code >= 500 and attempt < 2:
                time.sleep(10)
                continue
            raise RuntimeError(f"HTTP {e.code}: {detail[:300]}")
    else:
        raise QuotaExhausted("Still rate limited after retries.")

    for part in data["candidates"][0]["content"]["parts"]:
        if "inlineData" in part:
            return base64.b64decode(part["inlineData"]["data"])
    raise RuntimeError("Response contained no audio.")


def save_m4a(pcm: bytes, out_path: Path) -> None:
    out_path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        wav_path = Path(tmp) / "clip.wav"
        with wave.open(str(wav_path), "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(SAMPLE_RATE)
            w.writeframes(pcm)
        subprocess.run(
            ["ffmpeg", "-y", "-loglevel", "error", "-i", str(wav_path),
             "-c:a", "aac", "-b:a", "48k", "-ac", "1", str(out_path)],
            check=True,
        )


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--lang", choices=sorted(VOICES), help="only this language")
    parser.add_argument("--limit", type=int, default=0, help="stop after N new clips (0 = no limit)")
    parser.add_argument("--words-only", action="store_true", help="skip example sentences")
    parser.add_argument("--delay", type=float, default=6.0, help="seconds between requests")
    parser.add_argument("--model", default=os.environ.get("TTS_MODEL", DEFAULT_MODEL))
    args = parser.parse_args()

    api_key = load_api_key()
    languages = [args.lang] if args.lang else list(VOICES)

    jobs = []
    for lang in languages:
        entries = json.loads((LEXICON_DIR / f"{lang}.json").read_text(encoding="utf-8"))
        for e in entries:
            jobs.append((lang, e["text"], AUDIO_DIR / lang / f"{e['id']}.m4a"))
            if not args.words_only:
                jobs.append((lang, e["exampleSentence"], AUDIO_DIR / lang / f"{e['id']}_ex.m4a"))

    todo = [j for j in jobs if not j[2].exists()]
    print(f"{len(jobs) - len(todo)} of {len(jobs)} clips already exist, {len(todo)} to generate.")

    made = failed = 0
    for lang, text, out in todo:
        if args.limit and made >= args.limit:
            break
        try:
            save_m4a(synthesize(text, lang, api_key, args.model), out)
            made += 1
            print(f"[{made}] {out.relative_to(ROOT)}")
        except QuotaExhausted as e:
            print(f"\nStopped: {e} Run the same command again later to continue.")
            break
        except Exception as e:  # keep going; the failed clip is retried next run
            failed += 1
            print(f"FAILED {out.name}: {e}")
        time.sleep(args.delay)

    left = sum(1 for j in jobs if not j[2].exists())
    print(f"\nDone this run: {made} created, {failed} failed, {left} still missing.")


if __name__ == "__main__":
    main()
