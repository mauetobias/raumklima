package cc.maue.aufwachen.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cc.maue.aufwachen.widget.Klima

/** Grenzen aus regeln.yaml (Tag): ab 58 % an, unter 50 % aus. */
private const val ZIEL_VON = 50
private const val ZIEL_BIS = 58
private const val SKALA_VON = 30f
private const val SKALA_BIS = 80f

fun feuchteFarbe(f: Int?) = when {
    f == null -> Color.Gray
    f > 62 -> Ampel.rot
    f > ZIEL_BIS -> Ampel.orange
    f < 40 -> Ampel.gelb
    else -> Ampel.gruen
}

fun co2Farbe(x: Int?) = when {
    x == null -> Color.Gray
    x >= 1400 -> Ampel.rot
    x >= 1000 -> Ampel.orange
    x >= 800 -> Ampel.gelb
    else -> Ampel.gruen
}

private fun feuchteSatz(f: Int?) = when {
    f == null -> "Noch keine Messung"
    f > 62 -> "Zu feucht, Schimmelgefahr"
    f > ZIEL_BIS -> "Etwas feucht, Entfeuchter übernimmt"
    f < 40 -> "Eher trocken"
    else -> "Im Wohlfühlbereich"
}

@Composable
fun KlimaKarte(k: Klima, laedt: Boolean, neuLaden: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Logo(Icons.Rounded.WaterDrop, feuchteFarbe(k.feuchte))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Luftfeuchte", style = MaterialTheme.typography.titleLarge)
                    Text(
                        when {
                            k.fehler != null && k.stand.isEmpty() -> k.fehler
                            k.fehler != null -> "Stand ${k.stand} · ${k.fehler}"
                            else -> "Stand ${k.stand} · Home Coach"
                        },
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (laedt) CircularProgressIndicator(Modifier.size(24.dp).padding(2.dp), strokeWidth = 2.5.dp)
                else IconButton(onClick = neuLaden) { Icon(Icons.Rounded.Refresh, "Neu laden") }
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(k.feuchte?.toString() ?: "··", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(" %", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 10.dp))
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(bottom = 12.dp)) {
                    Status(feuchteSatz(k.feuchte), feuchteFarbe(k.feuchte))
                    Spacer(Modifier.height(4.dp))
                    Status(
                        when {
                            k.tankVoll -> "Tank voll, bitte leeren"
                            k.entfeuchterAn -> "Entfeuchter läuft"
                            else -> "Entfeuchter pausiert"
                        },
                        when { k.tankVoll -> Ampel.rot; k.entfeuchterAn -> Ampel.sonne; else -> MaterialTheme.colorScheme.outlineVariant },
                    )
                }
            }
            Skala(k.feuchte)
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text("${SKALA_VON.toInt()} %", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text("Ziel $ZIEL_VON bis $ZIEL_BIS %", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text("${SKALA_BIS.toInt()} %", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Kachel(Icons.Rounded.Air, "CO₂", k.co2?.let { "$it" } ?: "··", "ppm", co2Farbe(k.co2), Modifier.weight(1f))
                Kachel(Icons.Rounded.Thermostat, "Wärme", k.temperatur?.let { "%.1f".format(it).replace('.', ',') } ?: "··", "°C",
                    MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                Kachel(if (k.nacht) Icons.Rounded.Bedtime else Icons.Rounded.GraphicEq, if (k.nacht) "Nachtruhe" else "Lärm",
                    k.laerm?.let { "$it" } ?: "··", "dB", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            }
        }
    }
}

/** Waagerechte Skala mit hervorgehobenem Zielbereich und Punkt für den aktuellen Wert. */
@Composable
private fun Skala(feuchte: Int?) {
    val spur = MaterialTheme.colorScheme.surfaceContainerHighest
    val ziel = Ampel.gruen.copy(alpha = 0.45f)
    val punktFarbe = feuchteFarbe(feuchte)
    val rand = MaterialTheme.colorScheme.surfaceContainer
    val anteil by animateFloatAsState(
        (((feuchte ?: 0) - SKALA_VON) / (SKALA_BIS - SKALA_VON)).coerceIn(0f, 1f), tween(700), label = "skala")
    Canvas(Modifier.fillMaxWidth().height(18.dp)) {
        val h = 10.dp.toPx(); val y = (size.height - h) / 2
        drawRoundRect(spur, Offset(0f, y), Size(size.width, h), CornerRadius(h / 2))
        val a = (ZIEL_VON - SKALA_VON) / (SKALA_BIS - SKALA_VON) * size.width
        val b = (ZIEL_BIS - SKALA_VON) / (SKALA_BIS - SKALA_VON) * size.width
        drawRoundRect(ziel, Offset(a, y), Size(b - a, h), CornerRadius(h / 2))
        if (feuchte != null) {
            val x = anteil * size.width
            drawCircle(rand, size.height / 2, Offset(x, size.height / 2))
            drawCircle(punktFarbe, size.height / 2 - 3.dp.toPx(), Offset(x, size.height / 2))
        }
    }
}

@Composable
private fun Kachel(icon: ImageVector, titel: String, wert: String, einheit: String, farbe: Color, modifier: Modifier) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(20.dp), modifier = modifier) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = farbe, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(titel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(wert, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                Text(" $einheit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 3.dp))
            }
        }
    }
}
