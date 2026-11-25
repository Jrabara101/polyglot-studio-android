"""Generate pronunciation audio for the lexicon with Kokoro (free, offline TTS).

Same output layout as generate_audio.py (the Gemini version):
    app/src/main/assets/audio/<language>/<id>.m4a      (the word)
    app/src/main/assets/audio/<language>/<id>_ex.m4a   (the example sentence)

Runs fully on this PC: no API key, no daily limit. Needs the Kokoro
environment in tools/.venv-kokoro and ffmpeg on PATH.

Usage (from the project folder):
    tools/.venv-kokoro/Scripts/python.exe tools/generate_audio_kokoro.py --test
    tools/.venv-kokoro/Scripts/python.exe tools/generate_audio_kokoro.py --overwrite
"""

import argparse
import json
import subprocess
import sys
import tempfile
from pathlib import Path

import numpy as np
import soundfile as sf
from kokoro import KPipeline

ROOT = Path(__file__).resolve().parent.parent
LEXICON_DIR = ROOT / "app" / "src" / "main" / "assets" / "lexicon"
AUDIO_DIR = ROOT / "app" / "src" / "main" / "assets" / "audio"
TEST_DIR = ROOT / "tools" / "audio_test"
SAMPLE_RATE = 24000

# Kokoro language code and voice per app language.
VOICES = {
    "japanese": ("j", "jf_alpha"),
    "spanish": ("e", "ef_dora"),
    "french": ("f", "ff_siwis"),
}


def synthesize(pipeline: KPipeline, text: str, voice: str, speed: float) -> np.ndarray:
    chunks = [np.asarray(audio) for _, _, audio in pipeline(text, voice=voice, speed=speed) if audio is not None]
    if not chunks:
        raise RuntimeError("Kokoro returned no audio.")
    return np.concatenate(chunks)


def save_m4a(audio: np.ndarray, out_path: Path) -> None:
    out_path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        wav_path = Path(tmp) / "clip.wav"
        sf.write(str(wav_path), audio, SAMPLE_RATE)
        subprocess.run(
            ["ffmpeg", "-y", "-loglevel", "error", "-i", str(wav_path),
             "-c:a", "aac", "-b:a", "48k", "-ac", "1", str(out_path)],
            check=True,
        )


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--lang", choices=sorted(VOICES), help="only this language")
    parser.add_argument("--test", action="store_true",
                        help="make word + example for the first entry of each language into tools/audio_test/")
    parser.add_argument("--overwrite", action="store_true", help="replace clips that already exist")
    parser.add_argument("--words-only", action="store_true", help="skip example sentences")
    parser.add_argument("--speed", type=float, default=0.9, help="speaking speed (1.0 = normal)")
    args = parser.parse_args()

    languages = [args.lang] if args.lang else list(VOICES)
    made = failed = skipped = 0
    for lang in languages:
        code, voice = VOICES[lang]
        pipeline = KPipeline(lang_code=code, repo_id="hexgrad/Kokoro-82M")
        entries = json.loads((LEXICON_DIR / f"{lang}.json").read_text(encoding="utf-8"))
        if args.test:
            entries = entries[:1]
        out_dir = (TEST_DIR / lang) if args.test else (AUDIO_DIR / lang)

        for e in entries:
            # Japanese: speak the kana reading, not the kanji. Kokoro guesses kanji
            # readings and sometimes picks the wrong one (明日 -> "asu", not "ashita").
            word = e.get("reading") or e["text"] if lang == "japanese" else e["text"]
            example = e.get("exampleReading") or e["exampleSentence"] if lang == "japanese" else e["exampleSentence"]
            jobs = [(word, out_dir / f"{e['id']}.m4a")]
            if not args.words_only:
                jobs.append((example, out_dir / f"{e['id']}_ex.m4a"))
            for text, out in jobs:
                if out.exists() and not (args.overwrite or args.test):
                    skipped += 1
                    continue
                try:
                    save_m4a(synthesize(pipeline, text, voice, args.speed), out)
                    made += 1
                    print(f"[{made}] {out.relative_to(ROOT)}  <-  {text}", flush=True)
                except Exception as exc:  # keep going; rerun retries missing clips
                    failed += 1
                    print(f"FAILED {out.name}: {exc}", file=sys.stderr, flush=True)

    print(f"\nDone: {made} created, {skipped} skipped (already exist), {failed} failed.")


if __name__ == "__main__":
    main()
