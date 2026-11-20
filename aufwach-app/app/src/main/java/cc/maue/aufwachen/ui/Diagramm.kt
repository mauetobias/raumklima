package cc.maue.aufwachen.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Ein Punkt der Kurve: Zeit und Wert. */
data class Punkt(val zeit: LocalDateTime, val wert: Float)

private fun LocalDateTime.ms() = atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

/**
 * Verlaufskurve im Stil der Karten: weiche Linie mit Verlauf darunter, optional
 * ein Zielbereich (Band), eine Grenzlinie und schattierte Zeiten (z. B. Entfeuchter lief).
 */
@Composable
fun Kurve(
    punkte: List<Punkt>,
    farbe: Color,
    von: LocalDateTime,
    bis: LocalDateTime,
    einheit: String,
    band: ClosedFloatingPointRange<Float>? = null,
    grenze: Float? = null,
    schatten: List<Pair<LocalDateTime, LocalDateTime>> = emptyList(),
    schattenFarbe: Color = Ampel.tuerkis,
    nachkomma: Boolean = false,
) {
    val messer = rememberTextMeasurer()
    val achse = MaterialTheme.colorScheme.onSurfaceVariant
    val gitter = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val stil = TextStyle(fontSize = 10.sp, color = achse)
    Column {
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            if (punkte.size < 2) {
                val t = messer.measure("Noch zu wenig Messungen", stil)
                drawText(t, topLeft = Offset((size.width - t.size.width) / 2, (size.height - t.size.height) / 2))
                return@Canvas
            }
            val links = 34.dp.toPx(); val oben = 6.dp.toPx(); val unten = 6.dp.toPx()
            val w = size.width - links; val h = size.height - oben - unten
            val werte = punkte.map { it.wert } + listOfNotNull(band?.start, band?.endInclusive, grenze)
            var min = werte.min(); var max = werte.max()
            val rand = ((max - min) * 0.12f).coerceAtLeast(1f); min -= rand; max += rand
            val t0 = von.ms().toFloat(); val t1 = bis.ms().toFloat()
            fun x(t: LocalDateTime) = links + ((t.ms() - t0) / (t1 - t0)).coerceIn(0f, 1f) * w
            fun y(v: Float) = oben + (1 - (v - min) / (max - min)) * h

            // Gitter mit drei Beschriftungen
            for (i in 0..2) {
                val v = min + (max - min) * (i / 2f)
                val yy = y(v)
                drawLine(gitter, Offset(links, yy), Offset(size.width, yy), 1.dp.toPx())
                val t = messer.measure(if (nachkomma) "%.1f".format(v).replace('.', ',') else v.toInt().toString(), stil)
                drawText(t, topLeft = Offset(0f, yy - t.size.height / 2))
            }
            // Zeiten, in denen etwas lief
            schatten.forEach { (a, b) ->
                drawRect(schattenFarbe.copy(alpha = 0.13f), Offset(x(a), oben), Size((x(b) - x(a)).coerceAtLeast(2f), h))
            }
            // Zielbereich
            band?.let { drawRect(Ampel.gruen.copy(alpha = 0.12f), Offset(links, y(it.endInclusive)), Size(w, y(it.start) - y(it.endInclusive))) }
            // Grenze
            grenze?.let {
                drawLine(Ampel.orange.copy(alpha = 0.8f), Offset(links, y(it)), Offset(size.width, y(it)), 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
            }
            // Linie, Lücken über 30 Minuten nicht verbinden
            val linie = Path(); val flaeche = Path()
            var start = true; var erstesX = 0f; var letztesX = 0f; var letzte: LocalDateTime? = null
            punkte.forEach { p ->
                val px = x(p.zeit); val py = y(p.wert)
                val luecke = letzte != null && java.time.Duration.between(letzte, p.zeit).toMinutes() > 30
                if (start || luecke) {
                    if (!start) { flaeche.lineTo(letztesX, oben + h); flaeche.lineTo(erstesX, oben + h); flaeche.close() }
                    linie.moveTo(px, py); flaeche.moveTo(px, py); erstesX = px; start = false
                } else { linie.lineTo(px, py); flaeche.lineTo(px, py) }
                letztesX = px; letzte = p.zeit
            }
            flaeche.lineTo(letztesX, oben + h); flaeche.lineTo(erstesX, oben + h); flaeche.close()
            drawPath(flaeche, Brush.verticalGradient(listOf(farbe.copy(alpha = 0.35f), farbe.copy(alpha = 0f)), oben, oben + h))
            drawPath(linie, farbe, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            // aktueller Wert als Punkt
            val l = punkte.last()
            drawCircle(farbe, 4.5.dp.toPx(), Offset(x(l.zeit), y(l.wert)))
        }
        // Zeitachse
        val stunden = java.time.Duration.between(von, bis).toHours()
        val f = if (stunden <= 30) DateTimeFormatter.ofPattern("HH:mm") else DateTimeFormatter.ofPattern("EEE", Locale.GERMAN)
        Row(Modifier.fillMaxWidth().padding(start = 34.dp, top = 4.dp)) {
            Text(von.format(f), style = MaterialTheme.typography.labelSmall, color = achse)
            Spacer(Modifier.weight(1f))
            Text(von.plusMinutes(java.time.Duration.between(von, bis).toMinutes() / 2).format(f), style = MaterialTheme.typography.labelSmall, color = achse)
            Spacer(Modifier.weight(1f))
            Text("jetzt", style = MaterialTheme.typography.labelSmall, color = achse)
        }
    }
}

/** Kleine Kennzahlen unter einer Kurve. */
@Composable
fun Kennzahlen(vararg werte: Pair<String, String>) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        werte.forEach { (titel, wert) ->
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f)) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    Text(titel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    Text(wert, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), maxLines = 1)
                }
            }
        }
    }
}
