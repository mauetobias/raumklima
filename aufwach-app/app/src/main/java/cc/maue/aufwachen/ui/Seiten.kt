package cc.maue.aufwachen.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Co2
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.TableRows
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.TrendingDown
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Window
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import cc.maue.aufwachen.Daten
import cc.maue.aufwachen.Logik
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val HM = DateTimeFormatter.ofPattern("HH:mm")
private val TAG_HM = DateTimeFormatter.ofPattern("dd.MM. HH:mm")

/** Verlauf der letzten Tage laden (für Kurven und Tabelle). */
@Composable
private fun rememberVerlauf(stand: Int, tage: Int): Pair<List<Daten.Messung>?, String?> {
    val c = LocalContext.current
    var daten by remember { mutableStateOf<List<Daten.Messung>?>(null) }
    var fehler by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(stand, tage) {
        if (Daten.schluessel(c).isEmpty()) { fehler = "Schlüssel fehlt"; return@LaunchedEffect }
        val r = withContext(Dispatchers.IO) { runCatching { Daten.verlauf(c, tage) } }
        r.onSuccess { daten = it; fehler = null }.onFailure { fehler = "Verlauf nicht erreichbar" }
    }
    return daten to fehler
}

/** Der letzte Stand des Boten als JSON (für Regeln und Aktionen). */
@Composable
private fun boteStatus(stand: Int): JSONObject? {
    val c = LocalContext.current
    return remember(stand) { Logik.prefs(c).getString("letzter_status", null)?.let { runCatching { JSONObject(it) }.getOrNull() } }
}

private fun schnitt(l: List<Float>) = if (l.isEmpty()) null else l.average().toFloat()
private fun zahl(v: Float?, nk: Boolean = false) = when {
    v == null -> "··"
    nk -> "%.1f".format(v).replace('.', ',')
    else -> v.roundToInt().toString()
}

/** Zeiträume, in denen der Entfeuchter lief. */
private fun laufzeiten(m: List<Daten.Messung>): List<Pair<LocalDateTime, LocalDateTime>> {
    val aus = mutableListOf<Pair<LocalDateTime, LocalDateTime>>()
    var start: LocalDateTime? = null; var letzte: LocalDateTime? = null
    m.forEach { x ->
        if (x.entfeuchter && start == null) start = x.zeit
        if (!x.entfeuchter && start != null) { aus.add(start!! to x.zeit); start = null }
        letzte = x.zeit
    }
    if (start != null && letzte != null) aus.add(start!! to letzte!!)
    return aus
}

private fun dauerText(min: Long) = if (min < 60) "$min min" else "${min / 60} h ${min % 60} min"

/** Roborock: Zustand, Akku, heute schon gefahren, letzte Runde. */
@Composable
private fun RoborockKarte(stand: Int) {
    val ctx = LocalContext.current
    val s = boteStatus(stand)
    val akku = s?.optInt("roborock_akku", -1)?.takeIf { it >= 0 }
    val live = remember(stand) { cc.maue.aufwachen.Haus.stand(ctx) }.roborockFaehrt
    val zustand = if (live == true) "saugt" else s?.optString("roborock_zustand", "")?.ifEmpty { null }
    val heuteDatum = java.time.LocalDate.now().toString()
    val heute = s?.optBoolean("roborock_heute", false) == true ||
        (s?.optString("roborock_letzte_runde", "").orEmpty().startsWith(heuteDatum) && (s?.optInt("roborock_letzte_dauer", 0) ?: 0) >= 10)
    var behNeu by remember { mutableIntStateOf(0) }
    val seit = remember(stand, behNeu) { cc.maue.aufwachen.Behaelter.seitLeeren(ctx, s) }
    val grenze = remember(stand, behNeu) { cc.maue.aufwachen.Behaelter.grenze(ctx) }
    val faehrt = zustand?.startsWith("saugt") == true || zustand == "fährt zur Station"
    val letzte = s?.optString("roborock_letzte_runde", "").orEmpty()
    val erreichbar = zustand != null && zustand != "nicht erreichbar" && zustand != "nicht angemeldet"
    Karte(Icons.Rounded.CleaningServices, "Roborock",
        when { zustand == null -> "Noch keine Daten vom Boten"; !erreichbar -> zustand; else -> zustand.replaceFirstChar { it.uppercase() } },
        akzent = when { faehrt -> Ampel.gruen; !erreichbar -> Ampel.orange; else -> MaterialTheme.colorScheme.primary }) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            if (akku != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Akku", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text("$akku %", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(6.dp))
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { akku / 100f }, modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = when { akku < 20 -> Ampel.rot; akku < 50 -> Ampel.orange; else -> Ampel.gruen },
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest, drawStopIndicator = {})
            }
            Kennzahlen(
                "Heute" to if (heute) "gesaugt ✓" else "noch nicht",
                "Letzte Runde" to if (letzte.length >= 16) "${letzte.substring(8, 10)}.${letzte.substring(5, 7)}. ${letzte.substring(11, 16)}" else "··",
                "Dauer" to (s?.optInt("roborock_letzte_dauer", -1)?.takeIf { it >= 0 }?.let { "$it min" } ?: "··"),
                "Fläche" to (s?.optDouble("roborock_letzte_flaeche", -1.0)?.takeIf { it >= 0 }?.let { "${"%.0f".format(it)} m²" } ?: "··"),
            )
            if (seit != null) {
                val anteil = (seit.toFloat() / grenze).coerceIn(0f, 1f)
                Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Staubbehälter", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(if (seit >= grenze) "leeren!" else "ca. ${(anteil * 100).roundToInt()} %", style = MaterialTheme.typography.labelLarge,
                        color = if (seit >= grenze) Ampel.rot else MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(6.dp))
                androidx.compose.material3.LinearProgressIndicator(progress = { anteil }, modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = when { anteil >= 1f -> Ampel.rot; anteil >= 0.7f -> Ampel.orange; else -> Ampel.tuerkis },
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest, drawStopIndicator = {})
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("$seit min gesaugt seit dem Leeren · Hinweis ab $grenze min", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    androidx.compose.material3.TextButton(onClick = { cc.maue.aufwachen.Behaelter.geleert(ctx); behNeu++ }) { Text("Geleert") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(30, 60, 90, 120).forEach { m ->
                        FilterChip(grenze == m, { cc.maue.aufwachen.Behaelter.grenzeSetzen(ctx, m); behNeu++ }, label = { Text("$m min") })
                    }
                }
            }
            Text(if (heute) "Fertig für heute. Morgen wieder zwischen 10 und 17 Uhr, wenn kein Termin ist."
                 else "Fährt heute zwischen 10 und 17 Uhr los, sobald kein Termin im Kalender steht.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

/** Seite 1: alle Daten mit Kurven. */
fun LazyListScope.uebersicht(stand: Int) {
    item { RoborockKarte(stand) }
    item {
        var tage by remember { mutableIntStateOf(1) }
        val (verlauf, fehler) = rememberVerlauf(stand, if (tage == 1) 1 else 7)
        val bis = LocalDateTime.now()
        val von = bis.minusDays(tage.toLong())
        val m = verlauf?.filter { it.zeit.isAfter(von) }.orEmpty()
        val lauf = laufzeiten(m)

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.padding(start = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(tage == 1, { tage = 1 }, label = { Text("24 Stunden") })
                FilterChip(tage == 7, { tage = 7 }, label = { Text("7 Tage") })
                Spacer(Modifier.weight(1f))
                Text(when { fehler != null -> fehler; verlauf == null -> "lädt …"; else -> "${m.size} Messungen" },
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Luftfeuchte
            val f = m.mapNotNull { x -> x.feuchte?.let { Punkt(x.zeit, it.toFloat()) } }
            val imZiel = if (f.isEmpty()) null else f.count { it.wert in 50f..58f } * 100f / f.size
            Karte(Icons.Rounded.WaterDrop, "Luftfeuchte", "Grün: Zielbereich 50 bis 58 % · Türkis: Entfeuchter lief", akzent = feuchteFarbe(f.lastOrNull()?.wert?.toInt())) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Kurve(f, Ampel.himmel, von, bis, "%", band = 50f..58f, schatten = lauf)
                    Kennzahlen("Tief" to "${zahl(f.minOfOrNull { it.wert })} %", "Hoch" to "${zahl(f.maxOfOrNull { it.wert })} %",
                        "Schnitt" to "${zahl(schnitt(f.map { it.wert }))} %", "Im Ziel" to "${zahl(imZiel)} %")
                }
            }

            // CO2
            val co = m.mapNotNull { x -> x.co2?.let { Punkt(x.zeit, it.toFloat()) } }
            val ueber = co.count { it.wert >= 1000 } * 5L
            Karte(Icons.Rounded.Co2, "CO₂", "Gestrichelt: ab 1000 ppm lüften", akzent = co2Farbe(co.lastOrNull()?.wert?.toInt())) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Kurve(co, Ampel.orange, von, bis, "ppm", grenze = 1000f)
                    Kennzahlen("Tief" to zahl(co.minOfOrNull { it.wert }), "Hoch" to zahl(co.maxOfOrNull { it.wert }),
                        "Schnitt" to zahl(schnitt(co.map { it.wert })), "Über 1000" to dauerText(ueber))
                }
            }

            // Temperatur
            val t = m.mapNotNull { x -> x.temperatur?.let { Punkt(x.zeit, it.toFloat()) } }
            Karte(Icons.Rounded.Thermostat, "Temperatur", null, akzent = Ampel.sonne) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Kurve(t, Ampel.sonne, von, bis, "°C", nachkomma = true)
                    Kennzahlen("Tief" to "${zahl(t.minOfOrNull { it.wert }, true)} °C", "Hoch" to "${zahl(t.maxOfOrNull { it.wert }, true)} °C",
                        "Schnitt" to "${zahl(schnitt(t.map { it.wert }), true)} °C")
                }
            }

            // Lärm
            val l = m.mapNotNull { x -> x.laerm?.let { Punkt(x.zeit, it.toFloat()) } }
            Karte(Icons.Rounded.GraphicEq, "Lautstärke", "Wie ruhig es war, gut für den Schlaf", akzent = MaterialTheme.colorScheme.primary) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Kurve(l, MaterialTheme.colorScheme.primary, von, bis, "dB")
                    Kennzahlen("Leiseste" to "${zahl(l.minOfOrNull { it.wert })} dB", "Lauteste" to "${zahl(l.maxOfOrNull { it.wert })} dB",
                        "Schnitt" to "${zahl(schnitt(l.map { it.wert }))} dB")
                }
            }

            // Entfeuchter
            val minuten = lauf.sumOf { Duration.between(it.first, it.second).toMinutes() }
            Karte(Icons.Rounded.Air, "Entfeuchter", if (lauf.isEmpty()) "Lief in diesem Zeitraum nicht" else "Lief ${lauf.size} Mal", akzent = Ampel.tuerkis) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Kennzahlen("Laufzeit" to dauerText(minuten), "Starts" to "${lauf.size}",
                        "Anteil" to "${zahl(if (m.isEmpty()) null else minuten * 100f / Duration.between(von, bis).toMinutes())} %")
                    lauf.takeLast(4).reversed().forEach { (a, b) ->
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            Text("${a.format(if (tage == 1) HM else TAG_HM)} bis ${b.format(HM)}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(dauerText(Duration.between(a, b).toMinutes()), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

/** Hue Bridge koppeln: danach kommt der Lichtstand zu Hause sofort statt über Google. */
@Composable
private fun HueKarte(stand: Int) {
    val c = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var neu by remember { mutableIntStateOf(0) }
    var laeuft by remember { mutableStateOf<String?>(null) }
    val gekoppelt = remember(stand, neu) { cc.maue.aufwachen.Hue.gekoppelt(c) }
    val zuletzt = remember(stand, neu) { cc.maue.aufwachen.Hue.zuletzt(c) }
    Karte(Icons.Rounded.Lightbulb, "Philips Hue direkt",
        if (gekoppelt) "Zu Hause kommt der Lichtstand sofort von der Bridge" else "Schneller als über Google: einmal koppeln", akzent = Ampel.lampe) {
        if (gekoppelt) {
            Zeile(Icons.Rounded.Lightbulb, "Bridge", "verbunden · ${cc.maue.aufwachen.Hue.lampen(c)} Lampen", MaterialTheme.colorScheme.onSurfaceVariant)
            Zeile(Icons.Rounded.Schedule, "Zuletzt abgefragt",
                if (zuletzt == 0L) "noch nie" else java.time.Instant.ofEpochMilli(zuletzt).atZone(java.time.ZoneId.systemDefault()).format(TAG_HM),
                MaterialTheme.colorScheme.onSurfaceVariant, beschreibung = "Zu Hause meldet die Bridge jede Änderung sofort. Unterwegs über Google.")
        } else {
            Text(laeuft ?: "Tippe auf Koppeln und drück dann innerhalb von 30 Sekunden den großen runden Knopf auf der Hue Bridge. Dein Handy muss dafür im WLAN zu Hause sein.",
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            if (!gekoppelt) androidx.compose.material3.FilledTonalButton(enabled = laeuft == null, onClick = {
                laeuft = "Suche die Bridge …"
                scope.launch {
                    val ip = withContext(Dispatchers.IO) { runCatching { cc.maue.aufwachen.Hue.finden(c) }.getOrNull() }
                    if (ip == null) { laeuft = null; android.widget.Toast.makeText(c, "Keine Hue Bridge gefunden. Bist du im WLAN zu Hause?", android.widget.Toast.LENGTH_LONG).show(); return@launch }
                    var ok = false
                    for (i in 30 downTo 1) {
                        laeuft = "Jetzt den runden Knopf auf der Bridge drücken … noch $i s"
                        ok = withContext(Dispatchers.IO) { runCatching { cc.maue.aufwachen.Hue.koppeln(c) }.getOrDefault(false) }
                        if (ok) break
                        kotlinx.coroutines.delay(1000)
                    }
                    laeuft = null; neu++
                    android.widget.Toast.makeText(c, if (ok) "Hue Bridge verbunden" else "Knopf wurde nicht gedrückt, bitte nochmal", android.widget.Toast.LENGTH_LONG).show()
                    if (ok) { cc.maue.aufwachen.widget.KlimaWidget.aktualisieren(c); cc.maue.aufwachen.HueHorcher.starten(c) }
                }
            }) { Text("Koppeln") }
            else OutlinedButton(onClick = {
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { cc.maue.aufwachen.Hue.abfragen(c) }
                    neu++
                    android.widget.Toast.makeText(c, if (ok) "Licht abgefragt" else "Bridge nicht erreichbar (nicht zu Hause?)", android.widget.Toast.LENGTH_SHORT).show()
                    if (ok) cc.maue.aufwachen.widget.KlimaWidget.aktualisieren(c)
                }
            }) { Text("Jetzt abfragen") }
        }
    }
}

/** Seite 2: Automationen außerhalb der App (Bote bei GitHub und Google Home). */
fun LazyListScope.boteAutomationen(stand: Int) {
    item { HueKarte(stand) }
    item {
        val s = boteStatus(stand)
        val r = s?.optJSONObject("regeln")
        val e = r?.optJSONArray("geraete")?.optJSONObject(0)
        val n = e?.optJSONObject("nachtruhe")
        Karte(Icons.Rounded.Air, "Luftentfeuchter", "Der Bote prüft alle 5 Minuten den Home Coach und schaltet", akzent = Ampel.tuerkis) {
            Zeile(Icons.Rounded.TrendingUp, "Einschalten ab", "${e?.optInt("an_ueber", 58) ?: 58} %")
            Zeile(Icons.Rounded.TrendingDown, "Ganz aus unter", "${e?.optInt("aus_unter", 50) ?: 50} %")
            Zeile(Icons.Rounded.Bedtime, "Nachtruhe ${n?.optString("von", "22:30") ?: "22:30"} bis ${n?.optString("bis", "07:30") ?: "07:30"}",
                "${n?.optInt("an_ueber", 62) ?: 62} / ${n?.optInt("aus_unter", 55) ?: 55} %", beschreibung = "Nachts nur bei Schimmelgefahr, leise und ohne Piepen")
            Zeile(Icons.Rounded.Window, "Fenster auf", "aus", beschreibung = "Google Home schaltet ihn ab, solange ein Fenster offen ist")
            Zeile(Icons.Rounded.SmartToy, "Zuletzt", s?.optString("entfeuchter_aktion", "")?.ifEmpty { "noch nichts" } ?: "noch nichts",
                MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    item {
        val s = boteStatus(stand)
        val a = s?.optJSONObject("regeln")?.optJSONArray("aufgaben")?.optJSONObject(0)
        Karte(Icons.Rounded.CleaningServices, "Roborock", "Einmal am Tag eine Runde, wenn kein Termin im Kalender steht", akzent = MaterialTheme.colorScheme.primary) {
            Zeile(Icons.Rounded.Schedule, "Zeitfenster", "${a?.optString("ab", "10:00") ?: "10:00"} bis ${a?.optString("bis", "17:00") ?: "17:00"}")
            Zeile(Icons.Rounded.Schedule, "Dauer einer Runde", "${a?.optInt("dauer_minuten", 90) ?: 90} min",
                beschreibung = "Plus ${a?.optInt("puffer_minuten", 15) ?: 15} min Abstand zum nächsten Termin")
            Zeile(Icons.Rounded.SmartToy, "Zuletzt", s?.optString("roborock", "")?.ifEmpty { "noch nichts" } ?: "noch nichts",
                MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    item {
        val c = LocalContext.current
        Karte(Icons.Rounded.Home, "Google Home", "Diese Abläufe laufen in Google Home, ohne Handy") {
            Zeile(Icons.Rounded.Window, "Fenster auf, Entfeuchter aus", beschreibung = "Sobald ein Fensterkontakt öffnet")
            Zeile(Icons.Rounded.Videocam, "Kamera Willkommen", beschreibung = "Kamera sieht abends jemanden: stiller Hinweis an die App")
            Zeile(Icons.Rounded.Videocam, "Kamera Ruhe nach Ankunft", beschreibung = "Du bist zu Hause und gehst rein: Kamera nach 2 min aus")
            Zeile(Icons.Rounded.Lightbulb, "Status Licht und Fenster", beschreibung = "Jede Änderung und stündlich von 7 bis 23 Uhr an die App")
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                OutlinedButton(onClick = {
                    val app = c.packageManager.getLaunchIntentForPackage("com.google.android.apps.chromecast.app")
                    runCatching { c.startActivity(app ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://home.google.com/automations"))) }
                }) { Icon(Icons.Rounded.OpenInNew, null, Modifier.padding(end = 8.dp)); Text("Google Home öffnen") }
            }
        }
    }
}

/** Seite 3: was der Bote zuletzt getan hat und die letzten Messungen als Tabelle. */
fun LazyListScope.protokollSeite(stand: Int) {
    item {
        val s = boteStatus(stand)
        val zeit = s?.optString("zeit", "").orEmpty()
        Karte(Icons.Rounded.SmartToy, "Bote", "Läuft bei GitHub und schaltet nach dem Home Coach") {
            Zeile(Icons.Rounded.Schedule, "Letzter Lauf", if (zeit.length >= 16) "${zeit.substring(8, 10)}.${zeit.substring(5, 7)}. ${zeit.substring(11, 16)}" else "··",
                MaterialTheme.colorScheme.onSurfaceVariant)
            Zeile(Icons.Rounded.Air, "Entfeuchter", s?.optString("entfeuchter_aktion", "")?.ifEmpty { "··" } ?: "··", MaterialTheme.colorScheme.onSurfaceVariant)
            Zeile(Icons.Rounded.CleaningServices, "Roborock", s?.optString("roborock", "")?.ifEmpty { "··" } ?: "··", MaterialTheme.colorScheme.onSurfaceVariant)
            val m = cc.maue.aufwachen.Haus.letzteMeldung(LocalContext.current)
            Zeile(Icons.Rounded.Home, "Letzte Meldung von Google Home",
                m?.let { java.time.Instant.ofEpochMilli(it.first).atZone(java.time.ZoneId.systemDefault()).format(TAG_HM) } ?: "noch keine",
                if (m == null) Ampel.orange else MaterialTheme.colorScheme.onSurfaceVariant, beschreibung = m?.second)
            Zeile(Icons.Rounded.WaterDrop, "Live-Abfrage beim Home Coach",
                if ((s?.optLong("netatmo_bis", 0L) ?: 0L) * 1000 > System.currentTimeMillis()) "bereit" else "wartet auf den Boten",
                MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    item {
        val (verlauf, fehler) = rememberVerlauf(stand, 1)
        Karte(Icons.Rounded.TableRows, "Letzte Messungen", fehler ?: "Alle 5 Minuten eine Zeile") {
            val zeilen = verlauf.orEmpty().takeLast(24).reversed()
            Column(Modifier.padding(horizontal = 16.dp).horizontalScroll(rememberScrollState())) {
                @Composable
                fun Zelle(t: String, breite: Int, fett: Boolean = false) = Text(t, Modifier.width(breite.dp),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace,
                        fontWeight = if (fett) androidx.compose.ui.text.font.FontWeight.Bold else null),
                    color = if (fett) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                Row { Zelle("Zeit", 52, true); Zelle("Feuchte", 62, true); Zelle("CO₂", 50, true); Zelle("Temp", 50, true); Zelle("dB", 34, true); Zelle("Entf.", 50, true) }
                Spacer(Modifier.height(4.dp))
                zeilen.forEach { x ->
                    Row(Modifier.padding(vertical = 3.dp)) {
                        Zelle(x.zeit.format(HM), 52); Zelle(x.feuchte?.let { "$it %" } ?: "··", 62); Zelle(x.co2?.toString() ?: "··", 50)
                        Zelle(x.temperatur?.let { "%.1f".format(it).replace('.', ',') } ?: "··", 50); Zelle(x.laerm?.toString() ?: "··", 34)
                        Zelle(if (x.entfeuchter) "an" else "aus", 50)
                    }
                }
                if (zeilen.isEmpty()) Text(if (verlauf == null) "lädt …" else "Keine Messungen", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
