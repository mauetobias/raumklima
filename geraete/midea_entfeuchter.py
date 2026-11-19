"""Comfee/Midea-Luftentfeuchter über die NetHome-Plus-Cloud.

Verstellt nur die Zielfeuchte. An und Aus bleiben bei Google Home (Fensterkontakte).
"""
import os

from midea_beautiful import appliance_state, connect_to_cloud

_cloud = None


def _verbindung():
    global _cloud
    if _cloud is None:
        _cloud = connect_to_cloud(
            account=os.environ["NETHOME_EMAIL"].strip(),
            password=os.environ["NETHOME_PASSWORT"],
            appname=os.environ.get("MIDEA_APP", "NetHome Plus"),
        )
    return _cloud


def _entfeuchter(cloud):
    liste = [g for g in cloud.list_appliances() if str(g.get("type", "")).lower() in ("a1", "0xa1")]
    if not liste:
        raise RuntimeError("Kein Luftentfeuchter im NetHome-Plus-Konto gefunden.")
    return liste[0]["id"]


def schalten(regel, aktiv):
    """aktiv=True: entfeuchten, aktiv=False: Pause, None: nichts tun."""
    if aktiv is None:
        return "bleibt wie er ist"
    cloud = _verbindung()
    geraet = appliance_state(cloud=cloud, use_cloud=True, appliance_id=_entfeuchter(cloud))
    ziel = regel["ziel_beim_entfeuchten"] if aktiv else regel["ziel_bei_pause"]
    leise = bool(regel.get("leise")) and aktiv
    if geraet.state.target_humidity == ziel and bool(geraet.state.sleep_mode) == leise:
        return "schon richtig eingestellt"
    # beep_prompt bleibt aus: der Comfee piept beim Umstellen nicht
    geraet.set_state(target_humidity=ziel, sleep_mode=leise, beep_prompt=False, cloud=cloud)
    return "entfeuchtet jetzt" if aktiv else "macht Pause"
