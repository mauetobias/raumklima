package cc.maue.aufwachen.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ColorFilter
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import cc.maue.aufwachen.Haus
import cc.maue.aufwachen.R
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import cc.maue.aufwachen.Daten
import cc.maue.aufwachen.Logik
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Was das Widget anzeigt. */
data class Klima(
    val feuchte: Int?, val co2: Int?, val temperatur: Double?, val laerm: Int?,
    val entfeuchterAn: Boolean, val tankVoll: Boolean, val nacht: Boolean,
    val stand: String, val fehler: String? = null, val live: Boolean = false,
) {
    companion object {
        fun aus(s: JSONObject, fehler: String? = null) = Klima(
            s.optInt("feuchte", -1).takeIf { it >= 0 }, s.optInt("co2", -1).takeIf { it >= 0 },
            if (s.has("temperatur")) s.optDouble("temperatur") else null, s.optInt("laerm", -1).takeIf { it >= 0 },
            s.optBoolean("entfeuchter_an"), s.optBoolean("entfeuchter_tank_voll"), s.optBoolean("nachtruhe"),
            s.optString("zeit", "").let { if (it.length >= 16) it.substring(11, 16) else "" }, fehler, s.optBoolean("live"),
        )
        val LEER = Klima(null, null, null, null, false, false, false, "", "Tippen zum Laden")
    }
}

private object Farbe {
    fun feuchte(f: Int?) = when {
        f == null -> Fam.MUTED_RAW
        f > 62 -> Fam.RED
        f > 58 -> Fam.ORANGE
        f < 40 -> Color(0xFF64B5F6)
        else -> Fam.GREEN
    }
    fun co2(x: Int?) = when {
        x == null -> Fam.MUTED_RAW
        x >= 1400 -> Fam.RED
        x >= 1000 -> Fam.ORANGE
        else -> Fam.GREEN
    }
}

private val Fam.MUTED_RAW get() = Color(0xFFC5C8B9)

class KlimaWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val k = withContext(Dispatchers.IO) { laden(context) }
        provideContent { KlimaInhalt(k) }
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        fun aktualisieren(c: Context) { scope.launch { runCatching { KlimaWidget().updateAll(c.applicationContext) } } }

        fun planen(c: Context) {
            WorkManager.getInstance(c).enqueueUniquePeriodicWork("klima-widget", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<Arbeiter>(15, TimeUnit.MINUTES).build())
        }

        /** Holt den neuesten Stand. Klappt es nicht, wird der letzte bekannte gezeigt. */
        fun laden(c: Context): Klima {
            val p = Logik.prefs(c)
            if (Daten.schluessel(c).isEmpty()) return Klima.LEER.copy(fehler = "Schlüssel in der App eintragen")
            return try {
                val s = Daten.status(c)
                p.edit().putString("letzter_status", s.toString()).apply()
                Klima.aus(s)
            } catch (e: Exception) {
                val alt = p.getString("letzter_status", null)
                if (alt != null) Klima.aus(JSONObject(alt), "offline") else Klima.LEER.copy(fehler = "Keine Verbindung")
            }
        }
    }

    class Arbeiter(c: Context, p: WorkerParameters) : Worker(c, p) {
        override fun doWork(): Result { runBlocking { KlimaWidget().updateAll(applicationContext) }; return Result.success() }
    }
}

class KlimaWidgetEmpfaenger : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = KlimaWidget()
    override fun onEnabled(context: Context) { super.onEnabled(context); KlimaWidget.planen(context) }
}

class Neuladen : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        KlimaWidget().updateAll(context)
    }
}

/** Kleiner runder Knopf zum sofortigen Neuladen, im Stil der Abhak-Kästchen. */
@Composable
private fun NeuladenKnopf(size: androidx.compose.ui.unit.Dp = 28.dp) {
    Box(GlanceModifier.size(size).cornerRadius(9.dp).background(Fam.card).clickable(actionRunCallback<Neuladen>()),
        contentAlignment = Alignment.Center) {
        Text("↻", style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Fam.ink))
    }
}

@Composable
private fun Wert(text: String, farbe: Color, groesse: Float) {
    Text(text, style = TextStyle(fontSize = groesse.sp, fontWeight = FontWeight.Bold, color = ColorProvider(farbe)), maxLines = 1)
}

private val GELB = Color(0xFFFFC94D)
private val HIMMEL = Color(0xFF7CC4FF)

/** Lampe gelb, wenn irgendein Licht an ist. Fenster himmelblau, wenn eins offen ist. Kamera rot, wenn sie aufnimmt. */
private val KAMERA_ROT = Color(0xFFFF6B6B)

@Composable
private fun HausSymbol(bild: Int, beschreibung: String, an: Boolean, farbe: Color, text: String?, groesse: androidx.compose.ui.unit.Dp = 18.dp) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(ImageProvider(bild), contentDescription = beschreibung, modifier = GlanceModifier.size(groesse),
            colorFilter = ColorFilter.tint(if (an) ColorProvider(farbe) else Fam.muted))
        if (text != null) {
            Spacer(GlanceModifier.width(4.dp))
            Text(text, style = TextStyle(fontSize = 12.sp, color = if (an) Fam.ink else Fam.muted,
                fontWeight = if (an) FontWeight.Bold else FontWeight.Normal), maxLines = 1)
        }
    }
}

private fun lichtText(h: Haus.Stand) = when {
    h.lichterBekannt == 0 -> "?"
    h.lichterAn.isEmpty() -> "aus"
    h.lichterAn.size == 1 -> h.lichterAn.first()
    else -> "${h.lichterAn.size} an"
}
private fun fensterText(h: Haus.Stand) = when {
    h.fensterBekannt == 0 -> "?"
    h.fensterAuf.isEmpty() -> "zu"
    h.fensterAuf.size == 1 -> "offen"
    else -> "${h.fensterAuf.size} offen"
}
private fun kameraText(h: Haus.Stand) = when (h.kameraAn) { null -> "?"; true -> "an"; false -> "aus" }

private val TUERKIS = Color(0xFF4DD0E1)

private fun entfeuchterText(k: Klima) = when { k.tankVoll -> "voll"; k.entfeuchterAn -> if (k.nacht) "leise" else "läuft"; else -> "aus" }

@Composable
private fun EntfeuchterSymbol(k: Klima, mitText: Boolean) =
    HausSymbol(R.drawable.ic_entfeuchter, "Entfeuchter", k.entfeuchterAn || k.tankVoll, if (k.tankVoll) KAMERA_ROT else TUERKIS,
        if (mitText) entfeuchterText(k) else null)

@Composable
private fun HausZeile(h: Haus.Stand, k: Klima, mitText: Boolean) {
    val abstand = if (mitText) 10.dp else 5.dp
    Row(verticalAlignment = Alignment.CenterVertically) {
        HausSymbol(R.drawable.ic_lampe, "Licht", h.lichterAn.isNotEmpty(), GELB, if (mitText) lichtText(h) else null)
        Spacer(GlanceModifier.width(abstand))
        HausSymbol(R.drawable.ic_fenster, "Fenster", h.fensterAuf.isNotEmpty(), HIMMEL, if (mitText) fensterText(h) else null)
        Spacer(GlanceModifier.width(abstand))
        HausSymbol(R.drawable.ic_kamera, "Kamera", h.kameraAn == true, KAMERA_ROT, if (mitText) kameraText(h) else null)
        Spacer(GlanceModifier.width(abstand))
        EntfeuchterSymbol(k, mitText)
    }
}

/** Schmale Kachel: zwei mal zwei Symbole. */
@Composable
private fun HausRaster(h: Haus.Stand, k: Klima) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row {
            HausSymbol(R.drawable.ic_lampe, "Licht", h.lichterAn.isNotEmpty(), GELB, null)
            Spacer(GlanceModifier.width(6.dp))
            HausSymbol(R.drawable.ic_fenster, "Fenster", h.fensterAuf.isNotEmpty(), HIMMEL, null)
        }
        Spacer(GlanceModifier.height(4.dp))
        Row {
            HausSymbol(R.drawable.ic_kamera, "Kamera", h.kameraAn == true, KAMERA_ROT, null)
            Spacer(GlanceModifier.width(6.dp))
            EntfeuchterSymbol(k, false)
        }
    }
}

@Composable
fun KlimaInhalt(k: Klima) {
    val h = Haus.stand(LocalContext.current)
    val size = LocalSize.current
    val f = k.feuchte?.let { "$it %" } ?: "··"
    val c = k.co2?.let { "$it ppm" } ?: "··"
    val t = k.temperatur?.let { String.format("%.1f °C", it).replace('.', ',') } ?: "··"
    val geraet = when { k.tankVoll -> "Tank voll!"; k.entfeuchterAn -> if (k.nacht) "läuft leise" else "läuft"; else -> "aus" }
    val stand = when {
        k.fehler != null && k.stand.isEmpty() -> k.fehler
        k.fehler != null -> "Stand ${k.stand} · ${k.fehler}"
        else -> "Stand ${k.stand}" + if ((k.co2 ?: 0) >= 1400) " · lüften!" else ""
    }

    // Eine Spalte breit: Werte untereinander, so viele wie Platz ist
    if (Fam.narrow(size)) {
        FamShell(actionRunCallback<Neuladen>()) {
            Column(GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically) {
                Wert(k.feuchte?.let { "$it%" } ?: "··", Farbe.feuchte(k.feuchte), if (size.height < 100.dp) 18f else 22f)
                Text("Feuchte", style = TextStyle(fontSize = 10.sp, color = Fam.muted), maxLines = 1)
                if (size.height >= 140.dp) {
                    Spacer(GlanceModifier.height(6.dp))
                    HausRaster(h, k)
                }
                if (size.height >= 100.dp) {
                    Spacer(GlanceModifier.height(6.dp))
                    Wert(k.co2?.toString() ?: "··", Farbe.co2(k.co2), 15f)
                    Text("CO₂", style = TextStyle(fontSize = 10.sp, color = Fam.muted), maxLines = 1)
                }
                if (size.height >= 170.dp) {
                    Spacer(GlanceModifier.height(6.dp))
                    Text(k.temperatur?.let { String.format("%.0f°", it) } ?: "··",
                        style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Fam.ink), maxLines = 1)
                    Text("Temp.", style = TextStyle(fontSize = 10.sp, color = Fam.muted), maxLines = 1)
                }
                if (size.height >= 290.dp) {
                    Spacer(GlanceModifier.height(8.dp))
                    NeuladenKnopf(26.dp)
                }
            }
        }
        return
    }

    FamShell(null) {
        // Ein bis zwei Reihen hoch: alles Wichtige in den Kopf
        if (Fam.oneRow(size)) {
            FamHeader({ FamLogo("💧", 32.dp) }, "$f · $c", stand, trailing = { NeuladenKnopf() })
            Spacer(GlanceModifier.height(4.dp))
            HausZeile(h, k, false)
            return@FamShell
        }
        FamHeader({ FamLogo("🏠") }, "Raumklima", stand, trailing = { NeuladenKnopf() })
        FamCard {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Column(GlanceModifier.defaultWeight()) {
                    Text("Luftfeuchte", style = TextStyle(fontSize = 11.sp, color = Fam.muted), maxLines = 1)
                    Wert(f, Farbe.feuchte(k.feuchte), 26f)
                }
                Column(GlanceModifier.defaultWeight()) {
                    Text("CO₂", style = TextStyle(fontSize = 11.sp, color = Fam.muted), maxLines = 1)
                    Wert(c, Farbe.co2(k.co2), 18f)
                }
            }
            if (size.height >= 180.dp) {
                FamLine("Temperatur", t, if (k.laerm != null) "${k.laerm} dB Lautstärke" else "")
                Spacer(GlanceModifier.height(10.dp))
                HausZeile(h, k, size.width >= 250.dp)
            } else {
                Row(GlanceModifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(t, style = TextStyle(fontSize = 12.sp, color = Fam.muted), maxLines = 1,
                        modifier = GlanceModifier.defaultWeight())
                    HausZeile(h, k, false)
                }
            }
        }
    }
}
