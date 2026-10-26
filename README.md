# Kravos Jezik

Lična Android aplikacija za učenje slovenačkog (Duolingo-stil), za govornike srpskog.

- Kurs: `course/sl.txt` (kompaktan tekst) → `tools/build_course.py` → `app/src/main/assets/courses/sl.json`
- Zvuk: `tools/gen_audio.py` generiše snimke Piper glasom `sl_SI-artur-medium` na GitHub runneru (keš po tekstu)
- Build, potpisan APK i smoke test na emulatoru: `.github/workflows/build.yml`

## Web verzija

`web/index.html` je ista aplikacija za browser (isti kursevi i snimci). `web/build.py` sklapa sajt,
a `.github/workflows/web.yml` ga objavljuje na GitHub Pages i proverava živi sajt (`web/check/shots.py`).
Napredak na webu se čuva u browseru (localStorage).
