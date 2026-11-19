"""Schreibt bei jedem Lauf eine Zeile in den Verlauf und den aktuellen Stand für das Widget.

Alles landet verschlüsselt (TOKEN_SCHLUESSEL) im Ordner datenzweig/, den der Workflow
auf den Branch „daten“ schiebt. Ohne Schlüssel kann niemand etwas damit anfangen.

  status.enc                 aktueller Stand als JSON (fürs Widget)
  verlauf-JJJJ-MM.csv.enc    alle Messungen eines Monats als CSV (für Export und Analysen)
"""
import csv
import io
import json
import os
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo

from cryptography.fernet import Fernet, InvalidToken

ORDNER = Path(os.environ.get("DATENZWEIG", "datenzweig"))
SPALTEN = ["zeit", "feuchte", "co2", "temperatur", "laerm",
           "entfeuchter_an", "entfeuchter_ziel", "entfeuchter_feuchte", "entfeuchter_tank_voll",
           "entfeuchter_aktion", "nachtruhe", "roborock"]

ZEILE = {}


def _f():
    return Fernet(os.environ["TOKEN_SCHLUESSEL"].strip().encode())


def setzen(**werte):
    ZEILE.update({k: v for k, v in werte.items() if v is not None})


def speichern():
    if not ORDNER.exists():
        return
    jetzt = datetime.now(ZoneInfo("Europe/Berlin"))
    ZEILE["zeit"] = jetzt.isoformat(timespec="seconds")

    datei = ORDNER / f"verlauf-{jetzt:%Y-%m}.csv.enc"
    alt = ""
    if datei.exists():
        try:
            alt = _f().decrypt(datei.read_bytes()).decode()
        except InvalidToken:
            alt = ""
    puffer = io.StringIO()
    w = csv.DictWriter(puffer, fieldnames=SPALTEN, extrasaction="ignore", lineterminator="\n")
    if not alt:
        w.writeheader()
    w.writerow({k: _text(ZEILE.get(k)) for k in SPALTEN})
    datei.write_bytes(_f().encrypt((alt + puffer.getvalue()).encode()))

    monate = sorted(p.name[len("verlauf-"):-len(".csv.enc")] for p in ORDNER.glob("verlauf-*.csv.enc"))
    status = {**ZEILE, "monate": monate}
    (ORDNER / "status.enc").write_bytes(_f().encrypt(json.dumps(status, ensure_ascii=False).encode()))


def _text(v):
    if isinstance(v, bool):
        return "1" if v else "0"
    return "" if v is None else str(v)
