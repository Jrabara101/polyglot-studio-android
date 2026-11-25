"""Generate pronunciation audio for offline Story Cafe dialogues with Kokoro (free, offline TTS).

Reads app/src/main/assets/stories/<language>.json and writes, per NPC host node:
    app/src/main/assets/audio/stories/<language>/<story_id>_<node_id>.m4a

Runs fully offline on this PC: no API key, no daily limit. Needs the Kokoro
environment in tools/.venv-kokoro and ffmpeg on PATH.

Usage (from the project folder):
    tools/.venv-kokoro/Scripts/python.exe tools/generate_story_audio_kokoro.py --lang japanese --test
    tools/.venv-kokoro/Scripts/python.exe tools/generate_story_audio_kokoro.py --lang japanese
    tools/.venv-kokoro/Scripts/python.exe tools/generate_story_audio_kokoro.py --overwrite
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
STORIES_DIR = ROOT / "app" / "src" / "main" / "assets" / "stories"
AUDIO_DIR = ROOT / "app" / "src" / "main" / "assets" / "audio" / "stories"
TEST_DIR = ROOT / "tools" / "audio_test" / "stories"
SAMPLE_RATE = 24000

# Kokoro language code and voice per app language (same as tools/generate_audio_kokoro.py).
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
                        help="generate clips for only the first story of each selected language into tools/audio_test/stories/")
    parser.add_argument("--overwrite", action="store_true", help="replace clips that already exist")
    parser.add_argument("--speed", type=float, default=0.9, help="speaking speed (1.0 = normal)")
    args = parser.parse_args()

    languages = [args.lang] if args.lang else list(VOICES)
    made = failed = skipped = 0
    for lang in languages:
        story_file = STORIES_DIR / f"{lang}.json"
        if not story_file.exists():
            print(f"Skipping {lang}: {story_file.relative_to(ROOT)} does not exist.", flush=True)
            continue
        code, voice = VOICES[lang]
        pipeline = KPipeline(lang_code=code, repo_id="hexgrad/Kokoro-82M")
        stories = json.loads(story_file.read_text(encoding="utf-8"))
        if args.test:
            stories = stories[:1]
        out_dir = (TEST_DIR / lang) if args.test else (AUDIO_DIR / lang)

        for story in stories:
            story_id = story["id"]
            for node in story["nodes"]:
                node_id = node["id"]
                # Japanese: speak the kana reading, not raw kanji, so Kokoro never misreads kanji.
                text = (node.get("npcReading") or node["npcText"]) if lang == "japanese" else node["npcText"]
                out = out_dir / f"{story_id}_{node_id}.m4a"
                if out.exists() and not (args.overwrite or args.test):
                    skipped += 1
                    continue
                try:
                    save_m4a(synthesize(pipeline, text, voice, args.speed), out)
                    made += 1
                    print(f"[{made}] {out.relative_to(ROOT)}  <-  {text}", flush=True)
                except Exception as exc:
                    failed += 1
                    print(f"FAILED {out.name}: {exc}", file=sys.stderr, flush=True)

    print(f"\nDone: {made} created, {skipped} skipped (already exist), {failed} failed.")
    if failed:
        sys.exit(1)


if __name__ == "__main__":
    main()
