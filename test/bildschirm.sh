#!/bin/bash
# Bildschirmfotos der App im Emulator (Demo-Daten), hell und dunkel.
set -u
P=cc.maue.aufwachen
mkdir -p fotos
adb shell setprop persist.sys.timezone Europe/Berlin
adb install -r -g app.apk >/dev/null && echo "App installiert"
adb shell cmd notification allow_listener $P/$P.KameraHorcher
adb shell dumpsys deviceidle whitelist +$P >/dev/null
adb shell pm grant $P android.permission.ACCESS_FINE_LOCATION
adb shell pm grant $P android.permission.ACCESS_BACKGROUND_LOCATION
heute=$(adb shell date +%Y-%m-%d | tr -d '\r')
cat > /tmp/a.xml <<X
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name="schluessel">demo</string>
    <string name="url">https://www.virtualsmarthome.xyz/url_routine_trigger/beispiel</string>
    <string name="willkommen_url">https://www.virtualsmarthome.xyz/url_routine_trigger/beispiel2</string>
    <string name="ab">06:00</string>
    <string name="bis">11:00</string>
    <boolean name="wecker" value="true" />
    <boolean name="entsperren" value="true" />
    <boolean name="willkommen_aktiv" value="true" />
    <boolean name="willkommen_abends" value="true" />
    <int name="willkommen_radius" value="15" />
    <float name="lat" value="52.52" />
    <float name="lon" value="13.405" />
    <string name="protokoll">$heute 07:04:43  Wecker aus → Routine gestartet&#10;$heute 07:00:02  Wecker schlummert, warte weiter&#10;$heute 06:50:00  Wecker klingelt, warte aufs Ausschalten&#10;$heute 06:41:10  Entsperrt, aber Wecker läuft noch oder kommt noch: nichts&#10;$heute 00:12:31  Willkommen (durch die Tür) → Begrüßung gestartet</string>
</map>
X
adb shell am force-stop $P
adb push /tmp/a.xml /data/local/tmp/a.xml >/dev/null
adb shell chmod 644 /data/local/tmp/a.xml
adb shell run-as $P mkdir -p shared_prefs
adb shell run-as $P cp /data/local/tmp/a.xml shared_prefs/aufwachen.xml
cat > /tmp/h.xml <<X
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <boolean name="licht|Bett" value="true" />
    <boolean name="licht|Schreibtisch" value="false" />
    <boolean name="fenster|links" value="false" />
    <boolean name="fenster|rechts" value="false" />
    <boolean name="kamera|Haustür" value="false" />
</map>
X
adb push /tmp/h.xml /data/local/tmp/h.xml >/dev/null
adb shell chmod 644 /data/local/tmp/h.xml
adb shell run-as $P cp /data/local/tmp/h.xml shared_prefs/haus.xml
adb shell settings put system screen_off_timeout 600000
# Der Launcher des Emulators hängt gern beim Start und zeigt dann einen Fehlerdialog über allem
adb shell pm disable-user --user 0 com.google.android.apps.nexuslauncher >/dev/null 2>&1
adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1
sleep 5
adb shell svc power stayon true
for modus in no yes; do
  adb shell cmd uimode night $modus; sleep 3
  adb shell am force-stop $P
  adb shell am start -n $P/.MainActivity >/dev/null; sleep 8
  adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1; sleep 2
  name=$([ $modus = yes ] && echo dunkel || echo hell)
  for seite in 1 2 3; do
    for i in 1 2 3; do
      adb exec-out screencap -p > fotos/${name}-$seite-$i.png
      adb shell input swipe 700 2300 700 900 800; sleep 2
    done
    adb shell input swipe 1300 1500 120 1500 300; sleep 3
  done
done
# Dialog Zeitwahl
adb shell am force-stop $P; adb shell am start -n $P/.MainActivity >/dev/null; sleep 6
adb shell uiautomator dump /sdcard/ui.xml >/dev/null; adb pull /sdcard/ui.xml fotos/ui.xml >/dev/null
ls -la fotos
