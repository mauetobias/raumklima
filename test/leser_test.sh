#!/bin/bash
# Bildschirm-Erkennung: Kacheln wechseln von "aus" auf "an", die App muss es merken.
set -u
P=cc.maue.aufwachen
adb install -r -g app.apk >/dev/null && echo "App installiert"
adb shell settings put secure enabled_accessibility_services $P/$P.Bildschirmleser
adb shell settings put secure accessibility_enabled 1
sleep 3
adb shell am start -n $P/.FakeHome >/dev/null; sleep 2
echo "--- nach 2 s (erwartet: kamera aus, entfeuchter aus)"
adb shell run-as $P cat shared_prefs/haus.xml | grep -E "kamera|entfeuchter|meldung_text"
sleep 5
echo "--- nach 7 s (erwartet: kamera an, entfeuchter an)"
adb shell run-as $P cat shared_prefs/haus.xml | grep -E "kamera|entfeuchter|meldung_text"
