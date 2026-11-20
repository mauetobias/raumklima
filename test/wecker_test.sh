#!/bin/bash
# Echter Ablauf im Emulator: Wecker klingelt, schlummert, Entsperren, Ausschalten.
# Die "Alexa-Routine" ist ein kleiner Server auf dem Host (10.0.2.2:8000), der jeden Aufruf mitschreibt.
set -u
P=cc.maue.aufwachen
FEHLER=0
log() { echo "[$(date +%H:%M:%S)] $*"; }
treffer() { [ -f treffer.log ] && wc -l < treffer.log || echo 0; }
protokoll() { adb shell run-as $P cat shared_prefs/aufwachen.xml | sed -n '/name="protokoll"/,/<\/string>/p'; }
pruefe() { # pruefe "Beschreibung" erwartete_treffer
  local ist; ist=$(treffer)
  if [ "$ist" -eq "$2" ]; then log "OK    $1 (Aufrufe: $ist)"; else log "FEHLER $1 (Aufrufe: $ist, erwartet $2)"; FEHLER=1; fi
}
entsperren() { adb shell input keyevent KEYCODE_SLEEP; sleep 3; adb shell input keyevent KEYCODE_WAKEUP; sleep 1; adb shell wm dismiss-keyguard; sleep 1; adb shell input keyevent 82; sleep 5; }
prefs_setzen() {
  adb shell am force-stop $P
  cat > /tmp/a.xml <<X
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name="url">http://10.0.2.2:8000/gutenmorgen</string>
    <string name="ab">00:00</string>
    <string name="bis">23:59</string>
    <boolean name="wecker" value="true" />
    <boolean name="entsperren" value="true" />
</map>
X
  adb push /tmp/a.xml /data/local/tmp/a.xml >/dev/null
  adb shell chmod 644 /data/local/tmp/a.xml
  adb shell run-as $P mkdir -p shared_prefs
  adb shell run-as $P cp /data/local/tmp/a.xml shared_prefs/aufwachen.xml
  adb shell am start -n $P/.MainActivity >/dev/null; sleep 5
  adb shell input keyevent KEYCODE_HOME
}
wecker_in() { # Minuten ab jetzt
  local t; t=$(adb shell date -d "@$(( $(adb shell date +%s | tr -d '\r') + $1*60 ))" +%H:%M 2>/dev/null | tr -d '\r')
  [ -z "$t" ] && t=$(TZ=Europe/Berlin date -d "+$1 min" +%H:%M)
  local h=${t%%:*} m=${t##*:}
  adb shell am start -a android.intent.action.SET_ALARM --ei android.intent.extra.alarm.HOUR $((10#$h)) \
    --ei android.intent.extra.alarm.MINUTES $((10#$m)) --ez android.intent.extra.alarm.SKIP_UI true \
    --es android.intent.extra.alarm.MESSAGE Test >/dev/null
  log "Wecker gestellt auf $t"; sleep 3
  adb shell input keyevent KEYCODE_HOME
}
warte_auf_klingeln() {
  for i in $(seq 1 60); do
    if adb shell dumpsys notification --noredact | grep -q "category=alarm"; then log "Wecker klingelt"; return 0; fi
    sleep 5
  done
  log "FEHLER Wecker hat nicht geklingelt"; FEHLER=1
}

python3 -c "
import http.server
class H(http.server.BaseHTTPRequestHandler):
    def do_GET(s):
        open('treffer.log','a').write(s.path+'\n'); s.send_response(200); s.end_headers(); s.wfile.write(b'ok')
    def log_message(s,*a): pass
http.server.HTTPServer(('0.0.0.0',8000),H).serve_forever()" >/dev/null 2>&1 &
SERVER=$!
trap 'kill $SERVER 2>/dev/null' EXIT

adb shell settings put global auto_time 0 >/dev/null 2>&1
adb shell setprop persist.sys.timezone Europe/Berlin
adb shell svc power stayon true
log "Uhr-Apps: $(adb shell pm list packages | grep -i clock | tr '\n' ' ')"
adb install -r -g app.apk >/dev/null && log "App installiert"
adb shell cmd notification allow_listener $P/$P.KameraHorcher
adb shell appops set $P SCHEDULE_EXACT_ALARM allow
adb shell dumpsys deviceidle whitelist +$P >/dev/null
prefs_setzen

log "=== Teil 1: Entsperren vor dem Wecker, Klingeln, Schlummern, Entsperren beim Schlummern ==="
wecker_in 2
entsperren
pruefe "Entsperren, solange heute noch ein Wecker kommt: nichts" 0
warte_auf_klingeln
sleep 40
pruefe "Wecker klingelt 40 s: nichts" 0
entsperren
pruefe "Entsperren während es klingelt: nichts" 0
adb shell am start -a android.intent.action.SNOOZE_ALARM >/dev/null; log "Schlummern gedrückt"
sleep 45
adb shell dumpsys notification --noredact | grep -iE "pkg=.*clock|android.text|android.title" | grep -iA2 clock | head -12
pruefe "Wecker schlummert 45 s: nichts" 0
entsperren
pruefe "Entsperren beim Schlummern: nichts" 0
log "Protokoll nach Teil 1:"; protokoll

log "=== Teil 2: Wecker klingelt, wird ausgeschaltet ==="
adb shell am start -a android.intent.action.DISMISS_ALARM --es android.intent.extra.alarm.SEARCH_MODE android.all >/dev/null
sleep 30
rm -f treffer.log
prefs_setzen
wecker_in 2
warte_auf_klingeln
sleep 20
pruefe "Klingeln: nichts" 0
adb shell am start -a android.intent.action.DISMISS_ALARM >/dev/null; log "Ausschalten gedrückt"
sleep 40
pruefe "Wecker aus: Routine genau einmal" 1
entsperren
pruefe "Entsperren danach: kein zweites Mal" 1
log "Protokoll nach Teil 2:"; protokoll

log "=== Teil 3: Tag ohne Wecker, Entsperren startet die Routine ==="
adb shell am start -a android.intent.action.DISMISS_ALARM --es android.intent.extra.alarm.SEARCH_MODE android.all >/dev/null
sleep 5
rm -f treffer.log
prefs_setzen
# alle Wecker löschen geht nicht per Befehl; deshalb nur prüfen, wenn keiner mehr ansteht
if [ -z "$(adb shell dumpsys alarm | grep -i 'next alarm clock' -A1 | grep -i 'triggerTime')" ]; then
  entsperren
  pruefe "Entsperren ohne Wecker: Routine einmal" 1
else
  log "Teil 3 übersprungen (Emulator hat noch einen Wecker)"
fi
log "Protokoll Ende:"; protokoll
[ $FEHLER -eq 0 ] && log "ALLE TESTS BESTANDEN" || log "ES GAB FEHLER"
exit $FEHLER
