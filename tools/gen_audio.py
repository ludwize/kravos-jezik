#!/usr/bin/env python3
"""Generates Slovenian audio for every course string with Piper (runs on the GitHub runner).

usage: gen_audio.py course.json voice.onnx out_dir
Files are named sha1(text)[:16].ogg — the app computes the same hash. Existing files are reused,
files for texts no longer in the course are removed.
"""
import hashlib
import json
import os
import subprocess
import sys
import tempfile
import wave

sys.path.insert(0, os.path.dirname(__file__))
from build_course import audio_texts  # noqa: E402


def key(text):
    return hashlib.sha1(text.strip().encode("utf-8")).hexdigest()[:16]


def main():
    course = json.load(open(sys.argv[1], encoding="utf-8"))
    voice_path, out = sys.argv[2], sys.argv[3]
    os.makedirs(out, exist_ok=True)
    texts = audio_texts(course)
    wanted = {key(t): t for t in texts}
    for f in os.listdir(out):
        if f.endswith(".ogg") and f[:-4] not in wanted:
            os.remove(os.path.join(out, f))
    todo = [(k, t) for k, t in wanted.items() if not os.path.exists(os.path.join(out, k + ".ogg"))]
    print(f"{len(wanted)} clips, {len(todo)} to generate")
    if not todo:
        return
    from piper import PiperVoice
    voice = PiperVoice.load(voice_path)
    syn = None
    try:
        from piper import SynthesisConfig
        syn = SynthesisConfig(length_scale=1.08)  # a touch slower for learners
    except Exception:
        pass
    tmp = tempfile.mkdtemp()
    for i, (k, t) in enumerate(todo):
        wav = os.path.join(tmp, k + ".wav")
        with wave.open(wav, "wb") as w:
            if hasattr(voice, "synthesize_wav"):
                voice.synthesize_wav(t, w, syn_config=syn) if syn else voice.synthesize_wav(t, w)
            else:
                voice.synthesize(t, w)
        subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", wav, "-af", "silenceremove=start_periods=1:start_threshold=-50dB,areverse,silenceremove=start_periods=1:start_threshold=-50dB,areverse,apad=pad_dur=0.15",
                        "-c:a", "libopus", "-b:a", "24k", "-ac", "1", os.path.join(out, k + ".ogg")], check=True)
        os.remove(wav)
        if i % 50 == 0:
            print(f"  {i}/{len(todo)} {t}")
    open(os.path.join(out, "_count.txt"), "w").write(str(len(wanted)))


if __name__ == "__main__":
    main()
