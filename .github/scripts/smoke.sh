#!/usr/bin/env bash
# Installs the APK on the emulator and walks every screen in test mode (all answers accepted),
# recording screenshots, the app log and crashes.
set -u
OUT=smoke
mkdir -p "$OUT"
APK=$(ls out/*.apk | head -1)
PKG=net.kravos.jezik

adb install -r "$APK" > "$OUT/install.txt" 2>&1
adb logcat -c
adb shell am start -W -n $PKG/.ui.MainActivity --ez kravos_test true > "$OUT/start.txt" 2>&1
sleep 12

N=0
shot() { N=$((N + 1)); adb exec-out screencap -p > "$OUT/$(printf %03d $N)-$1.png"; echo "shot $1" >> "$OUT/steps.txt"; }

dump() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb pull /sdcard/ui.xml "$OUT/ui.xml" >/dev/null 2>&1
}

# find "<label1>|<label2>|..." -> prints "label x y" for the first label present (in list order)
find_any() {
  dump
  python3 - "$1" "$OUT/ui.xml" <<'PY'
import re, sys, xml.etree.ElementTree as ET
wants, path = sys.argv[1].split('|'), sys.argv[2]
try:
    root = ET.parse(path).getroot()
except Exception:
    sys.exit(0)
nodes = list(root.iter('node'))
for w in wants:
    for n in nodes:
        if n.get('text') == w or n.get('content-desc') == w:
            x1, y1, x2, y2 = map(int, re.findall(r'\d+', n.get('bounds')))
            print(w, (x1 + x2) // 2, (y1 + y2) // 2)
            sys.exit(0)
PY
}

tap_text() {
  local r
  r=$(find_any "$1")
  if [ -n "$r" ]; then
    set -- $r
    adb shell input tap "${@: -2:1}" "${@: -1}"
    echo "tapped $r" >> "$OUT/steps.txt"
    sleep 2
    return 0
  fi
  echo "not found: $1" >> "$OUT/steps.txt"
  return 1
}

SIZE=$(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1)
W=${SIZE%x*}; H=${SIZE#*x}
scroll() { adb shell input swipe $((W / 2)) $((H * 3 / 4)) $((W / 2)) $((H / 4)) 400; sleep 2; }

# Runs through a lesson/review session: screenshot each exercise and its feedback.
run_session() {
  local tag=$1 max=$2 i=0
  while [ $i -lt $max ]; do
    i=$((i + 1))
    r=$(find_any "NASTAVI|PROVERI|DALJE")
    case "$r" in
      NASTAVI*) shot "$tag-done"; tap_text "NASTAVI"; return ;;
      PROVERI*) shot "$tag-ex$i"; tap_text "PROVERI"; sleep 1; shot "$tag-fb$i"; tap_text "DALJE|U REDU" ;;
      DALJE*) shot "$tag-ex$i"; tap_text "DALJE" ;;
      *) shot "$tag-unknown$i"; sleep 2 ;;
    esac
  done
}

shot path
tap_text "Pozdravi"; sleep 2; shot sheet
tap_text "POČNI +15 XP"; sleep 3
run_session lesson 40
sleep 2; shot path-after

# a story (locked → "Ipak počni")
scroll
tap_text "Na kafi"; sleep 2; shot story-sheet
tap_text "IPAK POČNI|POČNI PRIČU +15 XP"; sleep 3
j=0
while [ $j -lt 25 ]; do
  j=$((j + 1))
  r=$(find_any "ZAVRŠI PRIČU|NASTAVI|DALJE")
  case "$r" in
    "ZAVRŠI PRIČU"*) shot story-end; tap_text "ZAVRŠI PRIČU"; sleep 2; shot story-done; tap_text "NASTAVI"; break ;;
    NASTAVI*) tap_text "NASTAVI"; break ;;
    DALJE*) [ $((j % 4)) -eq 1 ] && shot "story-$j"; tap_text "DALJE" ;;
    *) shot "story-unknown$j"; break ;;
  esac
done

# unit guide
adb shell input swipe $((W / 2)) $((H / 4)) $((W / 2)) $((H * 3 / 4)) 300; sleep 1
adb shell input swipe $((W / 2)) $((H / 4)) $((W / 2)) $((H * 3 / 4)) 300; sleep 1
tap_text "VODIČ"; sleep 2; shot guide
scroll; shot guide-scroll
adb shell input keyevent KEYCODE_BACK; sleep 2

tap_text "Ponavljanje"; sleep 2; shot review
tap_text "POČNI PONAVLJANJE|VEŽBAJ SLABE REČI"; sleep 3
run_session review 20
tap_text "Lažni prijatelji"; sleep 2; shot false-friends
adb shell input keyevent KEYCODE_BACK; sleep 2

tap_text "Rečnik"; sleep 2; shot words
tap_text "Naučene (0)|Naučene (10)|Naučene (11)" ; sleep 1; shot words-learned

tap_text "Profil"; sleep 2; shot profile
scroll; shot profile-scroll
tap_text "⚙️  PODEŠAVANJA"; sleep 2; shot settings
scroll; shot settings-scroll

PID=$(adb shell pidof $PKG | tr -d '\r')
echo "pid=$PID" > "$OUT/status.txt"
adb logcat -d > "$OUT/logcat.txt"
grep -n -A 40 "FATAL EXCEPTION" "$OUT/logcat.txt" > "$OUT/crash.txt" || true
grep -n -E "KravosJezik|AndroidRuntime|net.kravos|TextToSpeech|MediaPlayer" "$OUT/logcat.txt" | tail -500 > "$OUT/app-log.txt" || true
if [ -s "$OUT/crash.txt" ]; then echo "CRASH" >> "$OUT/status.txt"; fi
rm -f "$OUT/logcat.txt" "$OUT/ui.xml"
cat "$OUT/status.txt" "$OUT/steps.txt"
exit 0
