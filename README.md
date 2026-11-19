# Raumklima-Bote

Alle 10 Minuten liest ein GitHub-Workflow den Netatmo Home Coach aus und stellt danach die Geräte ein.
Alle Regeln stehen in [`regeln.yaml`](regeln.yaml). Neue Geräte kommen als Datei in `geraete/`, zeitgesteuerte Aufgaben in `aufgaben/`.

* **Luftentfeuchter (Comfee, NetHome Plus):** Home Coach über 56 % → Zielfeuchte 35 % (entfeuchtet), unter 52 % → 70 % (Pause).
  Nachtruhe 22:30 bis 7:30: erst ab 62 %, dann leise und ohne Piepton. An und Aus übernimmt Google Home mit den Fensterkontakten.
* **Roborock:** einmal am Tag zwischen 10 und 17 Uhr, nur wenn für 90 Minuten plus 15 Minuten Puffer kein Kalendertermin ansteht.

Zugangsdaten liegen ausschließlich in den GitHub-Secrets, Zwischenstände verschlüsselt im Actions-Cache.

Von Hand starten: *Actions* → *Raumklima-Bote* → *Run workflow*. Aktionen: `normal`, `kalender_test`,
`roborock_code_anfordern` und danach `roborock_code_eingeben` (falls die Roborock-Anmeldung einmal erneuert werden muss).
