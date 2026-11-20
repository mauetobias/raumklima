#!/usr/bin/env bash
# Läuft knapp eine Stunde lang alle 2 Minuten. Danach startet der Workflow sich selbst neu.
# Der Zeitplan von GitHub ist dabei nur noch Reserve, falls die Kette einmal reißt.
set -u
ENDE=$((SECONDS + ${LAUFZEIT:-3300}))
ZIEL="https://x-access-token:${GH_TOKEN}@github.com/${GITHUB_REPOSITORY}"

holen() {
  rm -rf datenzweig && mkdir -p datenzweig
  if git fetch -q --depth 1 origin daten 2>/dev/null; then
    git --work-tree=datenzweig checkout FETCH_HEAD -- . 2>/dev/null || true
    git reset -q
  fi
  # Übergang: alter Speicher aus dem Actions-Cache
  if [ -d speicher ] && [ ! -d datenzweig/speicher ]; then cp -r speicher datenzweig/speicher; fi
}

sichern() {
  ( cd datenzweig && rm -rf .git && git init -q -b daten && git add -A \
    && git -c user.name=raumklima-bote -c user.email=actions@users.noreply.github.com commit -qm "Messdaten (verschlüsselt)" \
    && git push -qf "$ZIEL" daten ) || echo "Datenzweig: Hochladen fehlgeschlagen"
}

while :; do
  holen
  SPEICHER=datenzweig/speicher DATENZWEIG=datenzweig python bote.py || echo "Lauf mit Fehler"
  sichern
  [ "${AKTION:-normal}" != "normal" ] && break
  [ $SECONDS -ge $ENDE ] && break
  sleep 120
done
