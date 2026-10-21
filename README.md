# Kravos Jezik

Lična Android aplikacija za učenje slovenačkog (Duolingo-stil), za govornike srpskog.

- Kurs: `course/sl.txt` (kompaktan tekst) → `tools/build_course.py` → `app/src/main/assets/courses/sl.json`
- Zvuk: `tools/gen_audio.py` generiše snimke Piper glasom `sl_SI-artur-medium` na GitHub runneru (keš po tekstu)
- Build, potpisan APK i smoke test na emulatoru: `.github/workflows/build.yml`
