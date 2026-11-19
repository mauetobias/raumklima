"""Liest den Netatmo Home Coach aus.

Netatmo tauscht den Refresh-Token bei jeder Erneuerung aus. Der aktuelle Token liegt
verschlüsselt im Speicher, beim allerersten Lauf wird der Start-Token aus den Secrets genommen.
"""
import os

import requests

import speicher

API = "https://api.netatmo.com"


def _alter_token():
    """Übergang: frühere Version legte den Token in token.enc ab."""
    from pathlib import Path
    alt = Path("token.enc")
    if alt.exists():
        try:
            return speicher._fernet().decrypt(alt.read_bytes()).decode()
        except Exception:
            return None
    return None


def _access_token():
    r = requests.post(f"{API}/oauth2/token", data={
        "grant_type": "refresh_token",
        "refresh_token": speicher.lesen("netatmo") or _alter_token() or os.environ["NETATMO_REFRESH_TOKEN"].strip(),
        "client_id": os.environ["NETATMO_CLIENT_ID"].strip(),
        "client_secret": os.environ["NETATMO_CLIENT_SECRET"].strip(),
    }, timeout=30)
    if not r.ok:
        # nur der Fehlercode (z. B. invalid_grant), keine Geheimnisse
        raise RuntimeError(f"Netatmo-Anmeldung abgelehnt: {r.status_code} {r.json().get('error', '?')}")
    daten = r.json()
    speicher.schreiben("netatmo", daten["refresh_token"])
    return daten["access_token"]


def messwerte():
    """Gibt die aktuellen Werte des (ersten) Home Coach zurück. Netatmo hakt gelegentlich, daher 3 Versuche."""
    import time
    token = _access_token()
    for versuch in range(3):
        r = requests.get(f"{API}/api/gethomecoachsdata", headers={"Authorization": f"Bearer {token}"}, timeout=30)
        if r.status_code < 500:
            break
        time.sleep(15)
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
