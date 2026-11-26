"""Wann bist du heute aufgestanden? Die Raumklima-App meldet es beim Aufwachen.

Weg: verschlüsselt (Fernet, TOKEN_SCHLUESSEL) über ntfy.sh auf einem geheimen Kanal, der aus dem Schlüssel
abgeleitet ist (gleiche Rechnung wie Signal.kt in der App). Der Bote fragt den Kanal ab und merkt sich die Zeit.
"""
import hashlib
import json
import os
from datetime import datetime, time

import requests
from cryptography.fernet import Fernet, InvalidToken

import speicher


def kanal(schluessel=None):
    s = (schluessel or os.environ["TOKEN_SCHLUESSEL"]).strip()
    return "raumklima-" + hashlib.sha256(("aufstehen:" + s).encode()).hexdigest()[:24]


def _abholen():
    """Meldungen der letzten 12 Stunden (so lange hebt ntfy.sh sie auf)."""
    r = requests.get(f"https://ntfy.sh/{kanal()}/json", params={"poll": "1", "since": "12h"}, timeout=20)
    r.raise_for_status()
    f = Fernet(os.environ["TOKEN_SCHLUESSEL"].strip().encode())
    aus = []
    for zeile in r.text.splitlines():
        try:
            m = json.loads(zeile)
            if m.get("event") != "message":
                continue
            aus.append(json.loads(f.decrypt(m.get("message", "").strip().encode()).decode()))
        except (ValueError, InvalidToken):
            continue  # fremde oder kaputte Meldung
    return aus


def heute(jetzt):
    """Aufstehzeit heute als datetime, oder None, wenn (noch) nichts gemeldet wurde."""
    tag = jetzt.date().isoformat()
    try:
        alt = json.loads(speicher.lesen("aufgestanden") or "{}")
    except ValueError:
        alt = {}
    if alt.get("tag") != tag:
        try:
            heutige = [m for m in _abholen() if m.get("tag") == tag and m.get("zeit")]
        except Exception as e:  # ntfy nicht erreichbar: beim nächsten Mal wieder
            print(f"Aufstehzeit: nicht abrufbar ({type(e).__name__})")
            heutige = []
        if heutige:
            alt = min(heutige, key=lambda m: m["zeit"])  # die früheste Meldung zählt
            speicher.schreiben("aufgestanden", json.dumps(alt))
    if alt.get("tag") != tag:
        return None
    h, m = map(int, alt["zeit"].split(":"))
    return jetzt.replace(hour=h, minute=m, second=0, microsecond=0)
