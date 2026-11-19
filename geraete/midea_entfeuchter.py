"""Comfee/Midea-Luftentfeuchter über die NetHome-Plus-Cloud.

Entfeuchten: einschalten und Zielfeuchte tief setzen, damit er durchzieht.
Pause: ganz ausschalten, denn bei erreichter Zielfeuchte läuft sonst der Lüfter weiter.
Google Home schaltet zusätzlich beim Öffnen eines Fensters aus.
"""
import os

from midea_beautiful import appliance_state, connect_to_cloud
from midea_beautiful.exceptions import CloudAuthenticationError

_cloud = None


def _verbindung():
    """Meldet sich an. NetHome-Plus-Konten laufen teils über die MSmartHome-Cloud,
    deshalb werden beide versucht."""
    global _cloud
    if _cloud is None:
        letzter = None
        pw = os.environ["NETHOME_PASSWORT"]
        versuche = [(app, p) for app in ("NetHome Plus", "MSmartHome") for p in dict.fromkeys([pw, pw.strip()])]
        for app, p in versuche:
            try:
                _cloud = connect_to_cloud(
                    account=os.environ["NETHOME_EMAIL"].strip(), password=p, appname=app)
                break
            except CloudAuthenticationError as e:
                print(f"Midea-Anmeldung über {app} abgelehnt: {e.message} ({e.error_code})")
                letzter = e
            except Exception as e:
                print(f"Midea-Anmeldung über {app} fehlgeschlagen: {type(e).__name__} {str(e)[:120]}")
                letzter = e
        if _cloud is None:
            raise letzter
    return _cloud


def _entfeuchter(cloud):
    liste = [g for g in cloud.list_appliances() if str(g.get("type", "")).lower() in ("a1", "0xa1")]
    if not liste:
        raise RuntimeError("Kein Luftentfeuchter im NetHome-Plus-Konto gefunden.")
    return liste[0]["id"]


def schalten(regel, aktiv):
    """aktiv=True: entfeuchten, aktiv=False: aus, None: in der Lücke."""
    cloud = _verbindung()
    geraet = appliance_state(cloud=cloud, use_cloud=True, appliance_id=_entfeuchter(cloud))
    s = geraet.state
    ziel = regel["ziel_beim_entfeuchten"]
    if aktiv is None:
        # In der Lücke: entfeuchtet er gerade auf unser Ziel, darf er weitermachen.
        # Läuft er mit einer anderen Einstellung (z. B. von Hand), ist das nicht unser Modus: aus.
        if s.running and s.target_humidity != ziel:
            aktiv = False
        else:
            return "bleibt wie er ist"
    if aktiv:
        leise = bool(regel.get("leise"))
        if s.running and s.target_humidity == ziel and bool(s.sleep_mode) == leise:
            return "entfeuchtet bereits"
        # beep_prompt aus: der Comfee piept beim Umstellen nicht
        geraet.set_state(running=True, target_humidity=ziel, sleep_mode=leise, beep_prompt=False, cloud=cloud)
        return "entfeuchtet jetzt" + (" (leise)" if leise else "")
    if not s.running:
        return "ist aus"
    geraet.set_state(running=False, beep_prompt=False, cloud=cloud)
    return "ausgeschaltet, Raum ist trocken genug"
