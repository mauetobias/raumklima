package cc.maue.aufwachen.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
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
import cc.maue.aufwachen.R

/**
 * Eine Handschrift für alle Widgets (Finanzen, Sport, Pflanzen, Essensplan, Zentrale).
 *
 * - Grund: dunkler Ton aus dem Hintergrundbild (Material You, accent2 800), Schrift cremeweiß (accent2 50)
 * - 24 dp Ecken, 12 dp Rand (in einer Reihe Höhe 8 dp oben und unten)
 * - Kopf immer gleich: Logo im Kästchen links, Titel fett, darunter eine Zeile Stand, rechts höchstens ein Abzeichen
 * - Inhalt in einer Karte (eine Stufe heller), die den restlichen Platz füllt: egal wie groß, nie ein leeres Loch
 * - Abhaken: Kästchen mit cremefarbenem Rand. Ein Klick erledigt es.
 * - Kein Fortschrittsbalken, der kommt nur beim Finanz-Widget (Budget) vor.
 *
 * Die Datei ist in allen Apps gleich, nur das Paket unterscheidet sich.
 */
object Fam {
    val surface = ColorProvider(R.color.fam_surface)
    val card = ColorProvider(R.color.fam_card)
    val ink = ColorProvider(R.color.fam_ink)
    val muted = ColorProvider(R.color.fam_muted)
    val onPill = ColorProvider(R.color.fam_on_pill)
    val radius = 24.dp
    val pad = 12.dp

    // Ampel wie beim Finanz-Widget, für Abzeichen
    val GREEN = Color(0xFF4CAF50)
    val YELLOW = Color(0xFFFFD54F)
    val ORANGE = Color(0xFFFF9800)
    val RED = Color(0xFFF44336)

    /** Ein bis zwei Reihen hoch: alles in eine Zeile (Kopf mit dem Wichtigsten und Haken), mittig. */
    fun oneRow(s: DpSize) = s.height < 140.dp
    /** Nur eine Spalte breit (oder sehr schmal): Logo und Haken, kaum Text. */
    fun narrow(s: DpSize) = s.width < 110.dp
}

/** Rahmen jedes Widgets. */
@Composable
fun FamShell(onClick: Action?, content: @Composable ColumnScope.() -> Unit) {
    val one = Fam.oneRow(LocalSize.current)
    var m = GlanceModifier.fillMaxSize().background(Fam.surface).cornerRadius(Fam.radius)
        .padding(horizontal = if (Fam.narrow(LocalSize.current)) 8.dp else Fam.pad, vertical = if (one) 8.dp else Fam.pad)
    if (onClick != null) m = m.clickable(onClick)
    Column(m, verticalAlignment = if (one) Alignment.CenterVertically else Alignment.Top, content = content)
}

/** Logo im Kästchen: immer links oben. */
@Composable
fun FamLogo(emoji: String, size: Dp = 36.dp) {
    Box(GlanceModifier.size(size).cornerRadius(12.dp).background(Fam.card), contentAlignment = Alignment.Center) {
        Text(emoji, style = TextStyle(fontSize = (size.value * 0.5f).sp))
    }
}

/** Kopfzeile: Logo, Titel, eine Zeile Stand, rechts optional Abzeichen oder Haken. */
@Composable
fun FamHeader(
    logo: @Composable () -> Unit,
    title: String,
    sub: String,
    modifier: GlanceModifier = GlanceModifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val size = LocalSize.current
    val narrow = Fam.narrow(size)
    val compact = Fam.oneRow(size)
    // Eine Spalte breit: in der kompakten Ansicht nur der Haken (falls da), sonst nur das Logo
    if (narrow) {
        Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
            if (compact && trailing != null) trailing() else logo()
        }
        return
    }
    // Zwei Spalten mit Haken oder Abzeichen rechts: Logo weglassen, damit der Text Platz hat
    val showLogo = !(size.width < 170.dp && trailing != null)
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (showLogo) logo()
        run {
            if (showLogo) Spacer(GlanceModifier.width(10.dp))
            Column(GlanceModifier.defaultWeight()) {
                Text(title, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Fam.ink), maxLines = 1)
                if (sub.isNotBlank()) Text(sub, style = TextStyle(fontSize = 12.sp, color = Fam.muted), maxLines = 1)
            }
        }
        if (trailing != null) { Spacer(GlanceModifier.width(8.dp)); trailing() }
    }
}

/** Abzeichen rechts im Kopf, z.B. eine Zahl in Ampelfarbe. */
@Composable
fun FamBadge(text: String, color: Color) {
    Box(GlanceModifier.height(26.dp).cornerRadius(13.dp).background(ColorProvider(color)).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
        Text(text, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ColorProvider(Color(0xFF1B1C18))), maxLines = 1)
    }
}

/** Die Inhaltskarte unter dem Kopf. Füllt den restlichen Platz. */
@Composable
fun ColumnScope.FamCard(highlight: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Spacer(GlanceModifier.height(8.dp))
    Column(
        GlanceModifier.fillMaxWidth().defaultWeight().cornerRadius(16.dp).background(if (highlight) Fam.ink else Fam.card)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        content = content,
    )
}

/** Kurzer Hinweis in der Karte, wenn es nichts aufzulisten gibt. */
@Composable
fun FamEmpty(text: String) {
    Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = TextStyle(fontSize = 13.sp, color = Fam.muted, textAlign = TextAlign.Center), maxLines = 3)
    }
}

/**
 * Abhak-Kästchen: cremefarbener Rand, innen die Farbe dahinter ([inside]). [glyph] optional (z.B. 💧).
 * [done] = abgehakt: ganz gefüllt mit Haken. [dark] = auf einer cremefarbenen Karte: dann dunkler Rand.
 */
@Composable
fun FamCheck(action: Action, inside: ColorProvider = Fam.card, glyph: String = "", dark: Boolean = false, size: Dp = 30.dp, done: Boolean = false) {
    val rim = if (dark) Fam.onPill else Fam.ink
    Box(GlanceModifier.size(size).cornerRadius(9.dp).background(rim).clickable(action), contentAlignment = Alignment.Center) {
        if (done) Text("✓", style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (dark) Fam.ink else Fam.onPill))
        else Box(GlanceModifier.size(size - 5.dp).cornerRadius(7.dp).background(if (dark) Fam.ink else inside), contentAlignment = Alignment.Center) {
            if (glyph.isNotBlank()) Text(glyph, style = TextStyle(fontSize = 13.sp))
        }
    }
}

/** Eine Zeile in der Karte: links klein Zeit oder Tag, dann Titel und Unterzeile, rechts optional Haken. */
@Composable
fun FamLine(lead: String, title: String, sub: String = "", onClick: Action? = null, end: (@Composable () -> Unit)? = null, dark: Boolean = false) {
    val wide = LocalSize.current.width >= 260.dp
    val ink = if (dark) Fam.onPill else Fam.ink
    val muted = if (dark) Fam.onPill else Fam.muted
    var m = GlanceModifier.fillMaxWidth().padding(vertical = 3.dp)
    Row(m, verticalAlignment = Alignment.CenterVertically) {
        var body = GlanceModifier.defaultWeight()
        if (onClick != null) body = body.clickable(onClick)
        if (wide && lead.isNotBlank()) {
            Text(lead, style = TextStyle(fontSize = 12.sp, color = muted), maxLines = 1, modifier = GlanceModifier.width(84.dp))
        }
        Column(body) {
            if (!wide && lead.isNotBlank()) Text(lead, style = TextStyle(fontSize = 11.sp, color = muted), maxLines = 1)
            Text(title, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = ink), maxLines = 1)
            if (sub.isNotBlank()) Text(sub, style = TextStyle(fontSize = 11.sp, color = muted), maxLines = 1)
        }
        if (end != null) { Spacer(GlanceModifier.width(8.dp)); end() }
    }
}
