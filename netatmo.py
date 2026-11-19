"""Liest den Netatmo Home Coach aus.

Netatmo tauscht den Refresh-Token bei jeder Erneuerung aus. Der jeweils aktuelle
Token wird verschlüsselt in der Datei token.enc abgelegt, die der Workflow über den
GitHub-Actions-Cache von Lauf zu Lauf weiterreicht. Der Schlüssel liegt nur in den
Secrets. Fehlt die Datei, wird der Start-Token aus den Secrets genommen.
"""
import os
from pathlib import Path

import requests
from cryptography.fernet import Fernet, InvalidToken

API = "https://api.netatmo.com"
TOKEN_DATEI = Path(os.environ.get("TOKEN_DATEI", "token.enc"))


def _fernet():
    return Fernet(os.environ["TOKEN_SCHLUESSEL"].encode())


def _refresh_token():
    if TOKEN_DATEI.exists():
        try:
            return _fernet().decrypt(TOKEN_DATEI.read_bytes()).decode()
        except InvalidToken:
            print("Gespeicherter Token unlesbar, nehme Start-Token.")
    return os.environ["NETATMO_REFRESH_TOKEN"]


def _access_token():
    r = requests.post(f"{API}/oauth2/token", data={
        "grant_type": "refresh_token",
        "refresh_token": _refresh_token(),
        "client_id": os.environ["NETATMO_CLIENT_ID"],
        "client_secret": os.environ["NETATMO_CLIENT_SECRET"],
    }, timeout=30)
    r.raise_for_status()
    daten = r.json()
    TOKEN_DATEI.write_bytes(_fernet().encrypt(daten["refresh_token"].encode()))
    return daten["access_token"]


def messwerte():
    """Gibt die aktuellen Werte des (ersten) Home Coach zurück."""
    r = requests.get(f"{API}/api/gethomecoachsdata",
                     headers={"Authorization": f"Bearer {_access_token()}"}, timeout=30)
    r.raise_for_status()
    geraete = r.json()["body"]["devices"]
    if not geraete:
        raise RuntimeError("Kein Home Coach im Netatmo-Konto gefunden.")
    coach = geraete[0]
    if not coach.get("reachable", True):
        raise RuntimeError("Home Coach ist gerade nicht erreichbar.")
    d = coach["dashboard_data"]
    return {
        "feuchte": d.get("Humidity"),
        "co2": d.get("CO2"),
        "temperatur": d.get("Temperature"),
        "laerm": d.get("Noise"),
    }
