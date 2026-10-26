#!/usr/bin/env bash
# Builds course JSON for every course/<lang>.txt and generates the Piper recordings
# (shared by the app build and the web build). Writes audio.log.
set -u
for f in course/*.txt; do
  l=$(basename "$f" .txt)
  python3 tools/build_course.py "$f" "app/src/main/assets/courses/$l.json" || exit 1
done
if ! command -v ffmpeg >/dev/null; then sudo apt-get update -qq && sudo apt-get install -y -qq ffmpeg; fi
pip install -q piper-tts onnxruntime 2>&1 | tail -2
mkdir -p "$RUNNER_TEMP/voice"
: > audio.log
# language -> Piper voice path under rhasspy/piper-voices
for spec in "sl sl/sl_SI/artur/medium/sl_SI-artur-medium" "es es/es_ES/davefx/medium/es_ES-davefx-medium"; do
  set -- $spec
  l=$1; v=$2
  base=https://huggingface.co/rhasspy/piper-voices/resolve/main
  curl -sSfL -o "$RUNNER_TEMP/voice/$l.onnx" "$base/$v.onnx" || { echo "voice download failed: $l" | tee -a audio.log; continue; }
  curl -sSfL -o "$RUNNER_TEMP/voice/$l.onnx.json" "$base/$v.onnx.json"
  echo "== $l ($v)" >> audio.log
  python3 tools/gen_audio.py "app/src/main/assets/courses/$l.json" "$RUNNER_TEMP/voice/$l.onnx" "app/src/main/assets/audio/$l" 2>&1 | grep -v onnxruntime | tee -a audio.log
  echo "$l: $(ls app/src/main/assets/audio/$l | wc -l) files, $(du -sh app/src/main/assets/audio/$l | cut -f1)" | tee -a audio.log
done
exit 0
