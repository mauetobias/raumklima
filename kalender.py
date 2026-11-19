"""Prüft, ob in einem Zeitraum ein Termin im Kalender steht.

Die geheimen iCal-Adressen stehen im Secret KALENDER_ICS (mehrere durch Leerzeichen
oder Zeilenumbruch getrennt). Termintitel werden nie ausgegeben.
Ganztägige Termine (Geburtstage, Urlaub) und als „frei“ markierte Termine zählen nicht.
"""
import os
from datetime import datetime

import icalendar
import recurring_ical_events
import requests

_kalender = None


def _laden():
    global _kalender
    if _kalender is None:
        _kalender = []
        for url in os.environ.get("KALENDER_ICS", "").split():
            r = requests.get(url.strip(), timeout=30)
            r.raise_for_status()
            _kalender.append(icalendar.Calendar.from_ical(r.content))
    return _kalender


def belegt(von: datetime, bis: datetime) -> bool:
    for kal in _laden():
        for t in recurring_ical_events.of(kal).between(von, bis):
            if not isinstance(t.get("DTSTART").dt, datetime):
                continue  # ganztägig
            if str(t.get("TRANSP", "OPAQUE")).upper() == "TRANSPARENT":
                continue  # als frei markiert
            if str(t.get("STATUS", "")).upper() == "CANCELLED":
                continue
            return True
    return False
