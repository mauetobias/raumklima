# Play Store Eintrag: Raumklima

## App-Name (max. 30 Zeichen)
Raumklima: Luft & Routinen

## Kurzbeschreibung (max. 80 Zeichen)
Luftfeuchte im Blick, sanft aufwachen und zu Hause begrüßt werden.

## Vollständige Beschreibung
Raumklima zeigt dir, wie es um die Luft in deiner Wohnung steht, und verbindet dein Smart Home mit deinem Alltag.

LUFT IM BLICK
• Luftfeuchte, CO₂, Temperatur und Lautstärke auf einen Blick
• Zielbereich gegen Schimmel und für deine Pflanzen, mit Ampelfarben
• Zustand des Luftentfeuchters, Hinweis bei vollem Tank
• Widget in jeder Größe, von einer Kachel bis groß, in den Farben deines Hintergrundbilds
• Verlauf als CSV exportieren

SANFT AUFWACHEN
• Startet deine Alexa-Routine erst, wenn du den Wecker wirklich ausschaltest
• Schlummern bleibt ruhig, kein Licht, keine Musik
• Ohne Wecker reicht das erste Entsperren am Morgen

WILLKOMMEN ZU HAUSE
• Dein Handy kommt in die Nähe und die Kamera an der Haustür sieht dich: Licht an, Alexa begrüßt dich
• Nur wenn beides zusammenpasst, im Flur stehen bleiben löst nichts aus
• Auf Wunsch nur, wenn es draußen dunkel wird

PRIVAT
• Kein Konto, keine Werbung, kein Tracking
• Einstellungen und Standort bleiben auf deinem Handy

Zum Ausprobieren als Schlüssel einfach „demo“ eintragen.

## Kategorie
Haus & Wohnen (House & Home)

## Kontakt
E-Mail: maue.tobias@gmail.com
Website: https://mau-e.com
Datenschutz: https://mau-e.com/raumklima/datenschutz.html

## Datensicherheit (Formular in der Play Console)
- Erhebt die App Nutzerdaten? Ja, aber nur auf dem Gerät verarbeitet:
  - Standort (genau): wird NICHT erhoben im Sinne von Google (verlässt das Gerät nicht). Angabe: "Nicht erhoben"
  - Benachrichtigungen: werden nur lokal gelesen, nicht gespeichert, nicht übertragen. Angabe: "Nicht erhoben"
- Werden Daten geteilt? Nein
- Verschlüsselung bei Übertragung: Ja (https)
- Kann man die Löschung beantragen? Alle Daten liegen lokal, Löschung über Deinstallation

## Berechtigungs-Erklärungen
### Standort im Hintergrund (ACCESS_BACKGROUND_LOCATION)
Zweck: Funktion "Willkommen zu Hause". Die App legt einen Geofence um die vom Nutzer gespeicherte Wohnung. Betritt das Handy diesen Bereich, ist die Begrüßung für 20 Minuten bereit. Ausgelöst wird sie erst, wenn zusätzlich die Haustürkamera eine Person meldet. Ohne Hintergrundstandort kann die Ankunft nicht erkannt werden, weil die App dann geschlossen ist.
Video: Bildschirmaufnahme zeigt Einschalten der Begrüßung, "Zuhause festlegen", Berechtigungsdialog, Probelauf "Ankunft proben".

### Vordergrunddienst "specialUse"
Subtyp: Erkennt am Morgen das erste Entsperren, um die Aufwach-Routine zu starten. Läuft nur im vom Nutzer eingestellten Zeitfenster und beendet sich danach selbst. Es gibt keine andere Android-Schnittstelle, die das Entsperren im Hintergrund meldet (ACTION_USER_PRESENT ist nur dynamisch registrierbar).

### Benachrichtigungszugriff (NotificationListenerService)
Zweck: erkennen, ob der Wecker klingelt oder schlummert, und den stillen Hinweis der Google-Home-Automation an der Haustürkamera erkennen. Keine Inhalte werden gespeichert oder übertragen.

### Exakte Wecker (SCHEDULE_EXACT_ALARM)
Startet den Morgen-Wächter pünktlich zu Beginn des Zeitfensters. Ohne Erlaubnis nutzt die App einen ungenaueren Wecker.

## Grafiken
- Icon 512 x 512: play/icon-512.png
- Vorschaubild 1024 x 500: play/vorschau-1024x500.png
- Bildschirmfotos: play/fotos/
