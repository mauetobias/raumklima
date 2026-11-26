"""Roborock: einmal am Tag saugen, im erlaubten Zeitfenster und nur ohne Termin.

Anmeldung: einmalig per E-Mail-Code (Workflow von Hand starten, siehe README).
Die Zugangsdaten liegen danach verschlüsselt im Speicher.
"""
import asyncio
import json
import os
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo

from roborock import RoborockCommand
from roborock.data import UserData
from roborock.devices.device_manager import UserParams, create_device_manager
from roborock.web_api import RoborockApiClient

import aufstehen
import kalender
import speicher

BERLIN = ZoneInfo("Europe/Berlin")


def _uhr(text, tag):
    h, m = map(int, text.split(":"))
    return tag.replace(hour=h, minute=m, second=0, microsecond=0)


def _email():
    return os.environ["ROBOROCK_EMAIL"].strip()


def _client():
    """Roborock bindet den Mail-Code an eine Geräte-Kennung. Die muss beim Anfordern
    und beim Eingeben gleich sein, deshalb wird sie fest aus dem Schlüssel abgeleitet."""
    import base64
    import hashlib
    c = RoborockApiClient(_email())
    roh = hashlib.sha256(("roborock" + os.environ["TOKEN_SCHLUESSEL"].strip()).encode()).digest()
    c._device_identifier = base64.urlsafe_b64encode(roh[:16]).decode().rstrip("=")
    return c


def code_anfordern():
    asyncio.run(_client().request_code_v4())
    return "Code wurde per E-Mail verschickt"


def code_eingeben(code):
    user_data = asyncio.run(_client().code_login_v4(code.strip()))
    speicher.schreiben("roborock", json.dumps(user_data.as_dict()))
    return "Anmeldung gespeichert"


async def _starten():
    daten = speicher.lesen("roborock")
    if not daten:
        raise RuntimeError("Roborock noch nicht angemeldet")
    params = UserParams(username=_email(), user_data=UserData.from_dict(json.loads(daten)))
    manager = await create_device_manager(params)
    try:
        sauger = [g for g in await manager.get_devices() if g.v1_properties is not None]
        if not sauger:
            raise RuntimeError("Kein Roborock-Sauger im Konto gefunden")
        await sauger[0].v1_properties.command.send(RoborockCommand.APP_START)
    finally:
        await manager.close()


DEUTSCH = {
    "charging": "lädt", "charging_complete": "voll geladen", "idle": "bereit", "cleaning": "saugt",
    "segment_cleaning": "saugt Räume", "zoned_cleaning": "saugt Bereich", "spot_cleaning": "saugt Stelle",
    "returning_home": "fährt zur Station", "docking": "dockt an", "paused": "pausiert", "error": "Fehler",
    "charger_disconnected": "nicht an der Station", "emptying_the_bin": "leert Behälter", "starting": "startet",
    "updating": "Update", "sleeping": "schläft", "shutting_down": "schaltet aus",
}


async def _zustand_holen():
    daten = speicher.lesen("roborock")
    if not daten:
        return {"roborock_zustand": "nicht angemeldet"}
    params = UserParams(username=_email(), user_data=UserData.from_dict(json.loads(daten)))
    manager = await create_device_manager(params)
    try:
        sauger = [g for g in await manager.get_devices() if g.v1_properties is not None]
        if not sauger:
            return {"roborock_zustand": "kein Sauger gefunden"}
        p = sauger[0].v1_properties
        await p.status.refresh()
        name = getattr(p.status, "state_name", None) or (p.status.state.name if p.status.state else "")
        aus = {"roborock_akku": p.status.battery, "roborock_zustand": DEUTSCH.get(name, name or "unbekannt")}
        try:
            await p.clean_summary.refresh()
            gesamt = getattr(p.clean_summary, "clean_time", None)
            if gesamt:
                aus["roborock_gesamt_minuten"] = round(gesamt / 60)  # Lebenszeit, für den Behälter-Zähler
            r = getattr(p.clean_summary, "last_clean_record", None)
            if r and r.begin:
                aus["roborock_letzte_runde"] = datetime.fromtimestamp(r.begin, BERLIN).isoformat(timespec="minutes")
                aus["roborock_letzte_dauer"] = round((r.duration or 0) / 60)
                aus["roborock_letzte_flaeche"] = round((r.area or 0) / 1_000_000, 1)
        except Exception:
            pass
        return aus
    finally:
        await manager.close()


def zustand():
    """Akku und Zustand für die App. Die Abfrage dauert, deshalb nur alle 5 Minuten,
    dazwischen wird der letzte Stand wiederverwendet. Höchstens 75 Sekunden."""
    import time as _t
    heute = datetime.now(BERLIN).date().isoformat()
    try:
        alt = json.loads(speicher.lesen("roborock_zustand") or "{}")
    except Exception:
        alt = {}
    if alt and _t.time() - alt.get("_zeit", 0) < 300:
        aus = {k: v for k, v in alt.items() if k != "_zeit"}
    else:
        try:
            aus = asyncio.run(asyncio.wait_for(_zustand_holen(), 75))
        except Exception as e:
            aus = {"roborock_zustand": "nicht erreichbar", "roborock_fehler": type(e).__name__}
        speicher.schreiben("roborock_zustand", json.dumps({**aus, "_zeit": _t.time()}))
    # Ist er heute schon gefahren (auch von Hand), zählt das als Tagesrunde
    letzte = aus.get("roborock_letzte_runde", "")
    if letzte.startswith(heute) and aus.get("roborock_letzte_dauer", 0) >= 10:
        speicher.schreiben("roborock_zuletzt", heute)
    aus["roborock_heute"] = speicher.lesen("roborock_zuletzt") == heute
    return aus


def aufstehen_warten(aufgabe, jetzt, aufgestanden=None):
    """Text, worauf er noch wartet, oder None. [aufgestanden] nur für Tests, sonst von der App."""
    minuten = aufgabe.get("nach_aufstehen_minuten", 0)
    if not minuten:
        return None
    wach = aufgestanden if aufgestanden is not None else aufstehen.heute(jetzt)
    if wach:
        frei = wach + timedelta(minutes=minuten)
        return None if jetzt >= frei else f"wartet bis {frei:%H:%M} (eine Stunde nach dem Aufstehen)"
    spaet = aufgabe.get("ohne_aufstehen_ab")
    if spaet and jetzt < _uhr(spaet, jetzt):
        return f"wartet aufs Aufstehen (ohne Meldung ab {spaet})"
    return None


def ausfuehren(aufgabe, jetzt=None):
    jetzt = jetzt or datetime.now(BERLIN)
    heute = jetzt.date().isoformat()
    if speicher.lesen("roborock_zuletzt") == heute:
        return "heute schon gesaugt"
    if not speicher.lesen("roborock"):
        return "wartet auf Anmeldung"
    if not (_uhr(aufgabe["ab"], jetzt) <= jetzt and
            jetzt + timedelta(minutes=aufgabe["dauer_minuten"]) <= _uhr(aufgabe["bis"], jetzt)):
        return "außerhalb des Zeitfensters"
    # Erst eine Stunde nach dem Aufstehen (die App meldet es). Ohne Meldung: erst ab einer späteren Uhrzeit.
    warten = aufstehen_warten(aufgabe, jetzt)
    if warten:
        return warten
    if kalender.belegt(jetzt, jetzt + timedelta(minutes=aufgabe["dauer_minuten"] + aufgabe.get("puffer_minuten", 0))):
        return "Termin im Kalender, wartet"
    asyncio.run(_starten())
    speicher.schreiben("roborock_zuletzt", heute)
    return "losgeschickt"
