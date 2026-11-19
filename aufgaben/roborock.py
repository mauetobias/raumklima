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

import kalender
import speicher

BERLIN = ZoneInfo("Europe/Berlin")


def _uhr(text, tag):
    h, m = map(int, text.split(":"))
    return tag.replace(hour=h, minute=m, second=0, microsecond=0)


def _email():
    return os.environ["ROBOROCK_EMAIL"].strip()


def code_anfordern():
    asyncio.run(RoborockApiClient(_email()).request_code_v4())
    return "Code wurde per E-Mail verschickt"


def code_eingeben(code):
    user_data = asyncio.run(RoborockApiClient(_email()).code_login_v4(code.strip()))
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
    if kalender.belegt(jetzt, jetzt + timedelta(minutes=aufgabe["dauer_minuten"] + aufgabe.get("puffer_minuten", 0))):
        return "Termin im Kalender, wartet"
    asyncio.run(_starten())
    speicher.schreiben("roborock_zuletzt", heute)
    return "losgeschickt"
