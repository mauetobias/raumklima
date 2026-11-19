"""Raumklima-Bote: Home Coach messen, Regeln prüfen, Geräte steuern."""
import importlib
import os
import sys
from datetime import datetime, time
from zoneinfo import ZoneInfo

import yaml

import netatmo


def entscheidung(regel, wert):
    """True = Gerät soll arbeiten, False = Pause, None = in der Lücke, nichts ändern."""
    if wert is None:
        return None
    if "an_ueber" in regel:
        if wert > regel["an_ueber"]:
            return True
        if wert < regel["aus_unter"]:
            return False
    if "an_unter" in regel:
        if wert < regel["an_unter"]:
            return True
        if wert > regel["aus_ueber"]:
            return False
    return None


PFLICHT = ["NETATMO_CLIENT_ID", "NETATMO_CLIENT_SECRET", "NETATMO_REFRESH_TOKEN",
           "TOKEN_SCHLUESSEL", "NETHOME_EMAIL", "NETHOME_PASSWORT"]


def _uhrzeit(text):
    h, m = text.split(":")
    return time(int(h), int(m))


def nachts(nacht, jetzt=None):
    jetzt = jetzt or datetime.now(ZoneInfo("Europe/Berlin")).time()
    von, bis = _uhrzeit(nacht["von"]), _uhrzeit(nacht["bis"])
    return jetzt >= von or jetzt < bis if von > bis else von <= jetzt < bis


def aktive_regel(regel, jetzt=None):
    """Nachts gelten die Werte aus 'nachtruhe' statt der Tageswerte."""
    nacht = regel.get("nachtruhe")
    if nacht and nachts(nacht, jetzt):
        return {**regel, **{k: v for k, v in nacht.items() if k not in ("von", "bis")}, "nacht": True}
    return {**regel, "nacht": False, "leise": False}


def main():
    fehlt = [n for n in PFLICHT if not os.environ.get(n, "").strip()]
    if fehlt:
        print("Fehlende oder leere Secrets: " + ", ".join(fehlt))
        sys.exit(1)
    regeln = yaml.safe_load(open("regeln.yaml", encoding="utf-8"))["geraete"]
    werte = netatmo.messwerte()
    fehler = False
    for regel in regeln:
        try:
            modul = importlib.import_module(f"geraete.{regel['typ']}")
            regel = aktive_regel(regel)
            ergebnis = modul.schalten(regel, entscheidung(regel, werte.get(regel["messwert"])))
            if regel["nacht"]:
                ergebnis += " (Nachtruhe)"
            print(f"{regel['name']}: {ergebnis}")
        except Exception as e:  # ein Gerät darf die anderen nicht blockieren
            fehler = True
            print(f"{regel['name']}: Fehler ({type(e).__name__})")
    sys.exit(1 if fehler else 0)


if __name__ == "__main__":
    main()
