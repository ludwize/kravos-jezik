#!/usr/bin/env python3
"""Assemble the web edition of Kravos Jezik.

Uses the same course JSON (from tools/build_course.py) and the same Piper recordings
(from tools/gen_audio.py) as the Android app.

usage: web/build.py <site-dir>
  expects app/src/main/assets/courses/<lang>.json and (optionally) app/src/main/assets/audio/<lang>/*.ogg
"""
import json
import os
import shutil
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from build_course import audio_texts  # noqa: E402
from gen_audio import key as audio_key  # noqa: E402

COURSES = os.path.join(ROOT, "app/src/main/assets/courses")
AUDIO = os.path.join(ROOT, "app/src/main/assets/audio")
ORDER = ["sl", "es"]


def main(out):
    if os.path.exists(out):
        shutil.rmtree(out)
    os.makedirs(os.path.join(out, "courses"))
    langs = sorted((f[:-5] for f in os.listdir(COURSES) if f.endswith(".json")), key=lambda l: ORDER.index(l) if l in ORDER else 99)
    meta = []
    for lang in langs:
        c = json.load(open(os.path.join(COURSES, f"{lang}.json"), encoding="utf-8"))
        src = os.path.join(AUDIO, lang)
        have = set(os.listdir(src)) if os.path.isdir(src) else set()
        audio = {}
        for t in audio_texts(c):
            k = audio_key(t)
            if f"{k}.ogg" in have:
                audio[t] = k
        if audio:
            dst = os.path.join(out, "audio", lang)
            os.makedirs(dst)
            for k in set(audio.values()):
                shutil.copy(os.path.join(src, f"{k}.ogg"), dst)
        c["audio"] = audio
        json.dump(c, open(os.path.join(out, "courses", f"{lang}.json"), "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
        meta.append({"lang": lang, "name": c["name"], "native": c.get("native", ""), "flag": c.get("flag", ""), "units": len(c["units"])})
        print(f"{lang}: {len(c['units'])} units, {len(audio)} clips")
    json.dump(meta, open(os.path.join(out, "courses", "index.json"), "w", encoding="utf-8"), ensure_ascii=False)
    shutil.copy(os.path.join(ROOT, "web/index.html"), out)
    open(os.path.join(out, "robots.txt"), "w").write("User-agent: *\nDisallow: /\n")
    open(os.path.join(out, ".nojekyll"), "w").close()


if __name__ == "__main__":
    main(sys.argv[1])
