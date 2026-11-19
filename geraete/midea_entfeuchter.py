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
    if geraet.state.target_humidity == ziel:
        return "schon richtig eingestellt"
    geraet.set_state(target_humidity=ziel, cloud=cloud)
    return "entfeuchtet jetzt" if aktiv else "macht Pause"
