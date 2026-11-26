"""Generate bundled pronunciation clips for Speech Guide & Voice Arena phrases missing from Lexicon.

Writes clips to:
    app/src/main/assets/audio/curated/<language>/<id>.m4a
and writes the lookup manifest to:
    app/src/main/assets/curated_audio.json

Needs the Kokoro environment in tools/.venv-kokoro and ffmpeg on PATH.

Usage (from project root):
    tools/.venv-kokoro/Scripts/python.exe tools/generate_curated_audio_kokoro.py
    tools/.venv-kokoro/Scripts/python.exe tools/generate_curated_audio_kokoro.py --overwrite
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
AUDIO_DIR = ROOT / "app" / "src" / "main" / "assets" / "audio" / "curated"
MANIFEST_PATH = ROOT / "app" / "src" / "main" / "assets" / "curated_audio.json"
SAMPLE_RATE = 24000
BITRATE = "28k"

VOICES = {
    "japanese": ("j", "jf_alpha"),
    "spanish": ("e", "ef_dora"),
    "french": ("f", "ff_siwis"),
}

# Each entry: (id, spoken_text_for_kokoro, [indexed_texts_in_app])
# Japanese entries pass pure kana for spoken_text so Kokoro never misreads kanji.
CURATED_CLIPS = {
    "japanese": [
        # Speech Guide
        ("jp_p2", "つき", ["つき"]),
        ("jp_p3", "ふじさん", ["ふじさん"]),
        ("jp_p4", "にほん", ["にほん"]),
        ("jp_p5", "きって", ["きって"]),
        # Voice Arena
        ("jv_2", "おみずをください", ["お水をください", "おみずをください"]),
        ("jv_3", "これはいくらですか", ["これはいくらですか"]),
        ("jv_4", "えきはどこですか", ["駅はどこですか", "えきはどこですか"]),
        ("jv_5", "にほんごをべんきょうしています", ["日本語を勉強しています", "にほんごをべんきょうしています"]),
        ("jv_6", "またおあいできてうれしいです", ["またお会いできて嬉しいです", "またおあいできてうれしいです"]),
    ],
    "spanish": [
        # Speech Guide
        ("es_p2", "perro", ["perro"]),
        ("es_p3", "jardín", ["jardín"]),
        ("es_p4", "amigo", ["amigo"]),
        ("es_p5", "llamar", ["llamar"]),
        # Voice Arena
        ("sv_1", "Buenos días, ¿cómo estás?", ["Buenos días, ¿cómo estás?"]),
        ("sv_2", "Una mesa para dos, por favor", ["Una mesa para dos, por favor"]),
        ("sv_4", "¿Dónde está la estación de tren?", ["¿Dónde está la estación de tren?"]),
        ("sv_5", "Quisiera probar el plato del día", ["Quisiera probar el plato del día"]),
        ("sv_6", "El perro corre rápido por el parque", ["El perro corre rápido por el parque"]),
    ],
    "french": [
        # Speech Guide
        ("fr_p2", "tu, rue", ["tu, rue", "tu / rue"]),
        ("fr_p3", "pain, vin", ["pain, vin", "pain / vin"]),
        ("fr_p4", "fleur", ["fleur"]),
        ("fr_p5", "les amis", ["les amis"]),
        # Voice Arena
        ("fv_1", "Bonjour, je voudrais un café", ["Bonjour, je voudrais un café"]),
        ("fv_2", "Merci beaucoup, bonne journée", ["Merci beaucoup, bonne journée"]),
        ("fv_4", "Je ne comprends pas très bien", ["Je ne comprends pas très bien"]),
        ("fv_5", "L'addition, s'il vous plaît", ["L'addition, s'il vous plaît"]),
        ("fv_6", "Il pleut doucement sur les rues de Paris", ["Il pleut doucement sur les rues de Paris"]),
    ],
}


def synthesize(pipeline: KPipeline, text: str, voice: str, speed: float) -> np.ndarray:
    chunks = [np.asarray(audio) for _, _, audio in pipeline(text, voice=voice, speed=speed) if audio is not None]
    if not chunks:
        raise RuntimeError(f"Kokoro returned no audio for {text!r}.")
    return np.concatenate(chunks)


def save_m4a(audio: np.ndarray, out_path: Path) -> None:
    out_path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        wav_path = Path(tmp) / "clip.wav"
        sf.write(str(wav_path), audio, SAMPLE_RATE)
        subprocess.run(
            [
                "ffmpeg", "-y", "-loglevel", "error", "-i", str(wav_path),
                "-c:a", "aac", "-b:a", BITRATE, "-ac", "1", str(out_path),
            ],
            check=True,
        )


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--overwrite", action="store_true", help="replace clips that already exist")
    parser.add_argument("--speed", type=float, default=0.9, help="speaking speed (1.0 = normal)")
    args = parser.parse_args()

    manifest: dict[str, list[dict[str, object]]] = {}
    made = skipped = failed = 0

    for lang, items in CURATED_CLIPS.items():
        code, voice = VOICES[lang]
        pipeline = KPipeline(lang_code=code, repo_id="hexgrad/Kokoro-82M")
        manifest_entries: list[dict[str, object]] = []

        for clip_id, spoken, indexed_texts in items:
            out_path = AUDIO_DIR / lang / f"{clip_id}.m4a"
            rel_asset = f"audio/curated/{lang}/{clip_id}.m4a"
            manifest_entries.append({"id": clip_id, "asset": rel_asset, "texts": indexed_texts})

            if out_path.exists() and not args.overwrite:
                skipped += 1
                continue
            try:
                save_m4a(synthesize(pipeline, spoken, voice, args.speed), out_path)
                made += 1
                print(f"[{made}] {rel_asset} <- {spoken}", flush=True)
            except Exception as exc:
                failed += 1
                print(f"FAILED {clip_id}: {exc}", file=sys.stderr, flush=True)

        manifest[lang] = manifest_entries

    MANIFEST_PATH.parent.mkdir(parents=True, exist_ok=True)
    MANIFEST_PATH.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"\nWrote manifest: {MANIFEST_PATH.relative_to(ROOT)}")
    print(f"Done: {made} created, {skipped} skipped, {failed} failed.")
    if failed:
        sys.exit(1)


if __name__ == "__main__":
    main()
