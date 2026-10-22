#!/usr/bin/env python3
"""Converts course/<lang>.txt into app/src/main/assets/courses/<lang>.json and validates it.

usage: build_course.py course/sl.txt app/src/main/assets/courses/sl.json
"""
import json
import re
import sys

META = {
    "sl": {"lang": "sl", "name": "Slovenački", "native": "Slovenščina", "flag": "🇸🇮", "tts": "sl-SI",
           "loc": "slovenačkom", "acc": "slovenački", "chars": ["č", "š", "ž"], "voice": "Piper · sl_SI artur"},
    "es": {"lang": "es", "name": "Španski", "native": "Español", "flag": "🇪🇸", "tts": "es-ES",
           "loc": "španskom", "acc": "španski", "chars": ["á", "é", "í", "ó", "ú", "ñ", "¿", "¡"], "voice": "Piper · es_ES davefx"},
}


def alts(s):
    return [x.strip() for x in s.split("|") if x.strip()]


def fail(n, line, msg):
    sys.exit(f"line {n}: {msg}\n  {line}")


def parse(path):
    lang = re.sub(r"\.txt$", "", path.split("/")[-1])
    course = dict(META[lang])
    course["units"] = []
    unit = None
    lesson = None
    for n, raw in enumerate(open(path, encoding="utf-8"), 1):
        line = raw.rstrip("\n").strip()
        if not line or (line.startswith("#") and not line.startswith("## ")):
            continue
        if line.startswith("= UNIT"):
            parts = [p.strip() for p in line.split("|")]
            if len(parts) != 3:
                fail(n, line, "unit needs '= UNIT | title | desc'")
            unit = {"id": f"u{len(course['units']) + 1}", "title": parts[1], "desc": parts[2], "guide": [], "lessons": []}
            course["units"].append(unit)
            lesson = None
            continue
        if unit is None:
            fail(n, line, "content before first unit")
        if line.startswith("guide:"):
            unit["guide"].append(line[6:].strip())
            continue
        m = re.match(r"^## ([LS]) (\w+) \| (.+)$", line)
        if m:
            kind, slug, title = m.groups()
            # ids must stay stable when units are inserted: lessons are "<slug>", stories "p-<slug>"
            lesson = {"id": slug if kind == "L" else f"p-{slug}", "type": "lesson" if kind == "L" else "story", "title": title.strip()}
            if kind == "L":
                lesson.update({"tip": "", "words": [], "sentences": [], "grammar": []})
            else:
                lesson["lines"] = []
            unit["lessons"].append(lesson)
            continue
        if lesson is None:
            fail(n, line, "content outside a lesson")
        tag, _, body = line.partition(":")
        body = body.strip()
        if tag == "tip":
            lesson["tip"] = (lesson["tip"] + " " + body).strip()
        elif tag == "w":
            note = ""
            if " # " in body:
                body, note = body.split(" # ", 1)
            if " = " not in body:
                fail(n, line, "word needs ' = '")
            sl, sr = body.split(" = ", 1)
            lesson["words"].append({"sl": sl.strip(), "sr": alts(sr), "note": note.strip()})
        elif tag == "s":
            if " = " not in body:
                fail(n, line, "sentence needs ' = '")
            sl, sr = body.split(" = ", 1)
            lesson["sentences"].append({"sl": alts(sl), "sr": alts(sr)})
        elif tag == "g":
            parts = body.split(" :: ")
            if len(parts) != 3 or " = " not in parts[0]:
                fail(n, line, "grammar needs 'prompt = sr :: answer :: wrong, wrong'")
            prompt, sr = parts[0].split(" = ", 1)
            ans = parts[1].strip()
            if "___" not in prompt:
                fail(n, line, "grammar prompt needs ___")
            wrong = [w.strip() for w in parts[2].split(",") if w.strip()]
            if ans in wrong:
                fail(n, line, "answer listed as wrong")
            lesson["grammar"].append({"prompt": prompt.strip(), "sr": sr.strip(), "answer": ans, "wrong": wrong,
                                      "full": prompt.strip().replace("___", ans)})
        elif tag == "l":
            m2 = re.match(r"^([^:]+): (.+?) = (.+)$", body)
            if not m2:
                fail(n, line, "story line needs 'Who: sl = sr'")
            lesson["lines"].append({"who": m2.group(1).strip(), "sl": m2.group(2).strip(), "sr": m2.group(3).strip()})
        elif tag == "q":
            parts = body.split(" :: ")
            if len(parts) != 3:
                fail(n, line, "question needs 'q :: answer :: wrong, wrong'")
            lesson["lines"].append({"q": parts[0].strip(), "answer": parts[1].strip(),
                                    "wrong": [w.strip() for w in parts[2].split(",") if w.strip()]})
        else:
            fail(n, line, f"unknown tag '{tag}'")
    return course


def audio_texts(course):
    """Every Slovenian string the app may speak (must match the Kotlin side exactly)."""
    out = []
    for u in course["units"]:
        for l in u["lessons"]:
            for w in l.get("words", []):
                out.append(w["sl"])
            for s in l.get("sentences", []):
                out.append(s["sl"][0])
            for g in l.get("grammar", []):
                out.append(g["full"])
            for ln in l.get("lines", []):
                if "sl" in ln:
                    out.append(ln["sl"])
    seen, res = set(), []
    for t in out:
        t = t.strip()
        if t and t not in seen:
            seen.add(t)
            res.append(t)
    return res


if __name__ == "__main__":
    src, dst = sys.argv[1], sys.argv[2]
    c = parse(src)
    ids = [l["id"] for u in c["units"] for l in u["lessons"]]
    dup = {i for i in ids if ids.count(i) > 1}
    if dup:
        sys.exit(f"duplicate lesson ids: {sorted(dup)}")
    seen = {}
    for u in c["units"]:
        for l in u["lessons"]:
            for w in l.get("words", []):
                k = w["sl"].lower()
                if k in seen:
                    print(f"warning: word '{w['sl']}' in {l['id']} already in {seen[k]}")
                else:
                    seen[k] = l["id"]
    for u in c["units"]:
        for l in u["lessons"]:
            if l["type"] == "lesson" and (len(l["words"]) < 4 or len(l["sentences"]) < 3):
                sys.exit(f"{l['id']}: too little content")
            if l["type"] == "story" and not any("q" in x for x in l["lines"]):
                sys.exit(f"{l['id']}: story without questions")
    json.dump(c, open(dst, "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
    nl = sum(1 for u in c["units"] for l in u["lessons"] if l["type"] == "lesson")
    ns = sum(1 for u in c["units"] for l in u["lessons"] if l["type"] == "story")
    nw = len({w["sl"] for u in c["units"] for l in u["lessons"] for w in l.get("words", [])})
    nsent = sum(len(l.get("sentences", [])) for u in c["units"] for l in u["lessons"])
    print(f"{len(c['units'])} units, {nl} lessons, {ns} stories, {nw} words, {nsent} sentences, {len(audio_texts(c))} audio clips")
