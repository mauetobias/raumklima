# Raumklima-Bote

Alle 10 Minuten liest ein GitHub-Workflow den Netatmo Home Coach aus und stellt danach die Geräte ein.
Die Regeln stehen in [`regeln.yaml`](regeln.yaml), neue Geräte kommen als Datei in den Ordner `geraete/`.

* **Luftentfeuchter (Comfee, NetHome Plus):** Home Coach über 56 % → Zielfeuchte 35 % (entfeuchtet kräftig), unter 52 % → Zielfeuchte 70 % (Pause).
  An und Aus übernimmt Google Home mit den Tapo-Fensterkontakten.

Zugangsdaten liegen ausschließlich in den GitHub-Secrets:
`NETATMO_CLIENT_ID`, `NETATMO_CLIENT_SECRET`, `NETATMO_REFRESH_TOKEN`, `TOKEN_SCHLUESSEL`, `NETHOME_EMAIL`, `NETHOME_PASSWORT`.

Von Hand starten: Reiter *Actions* → *Raumklima-Bote* → *Run workflow*.
