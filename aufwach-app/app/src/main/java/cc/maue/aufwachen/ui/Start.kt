package cc.maue.aufwachen.ui

import android.Manifest
import android.app.AlarmManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import cc.maue.aufwachen.BuildConfig
import cc.maue.aufwachen.Daten
import cc.maue.aufwachen.Haus
import cc.maue.aufwachen.Logik
import cc.maue.aufwachen.Updater
import cc.maue.aufwachen.Willkommen
import cc.maue.aufwachen.widget.Klima
import cc.maue.aufwachen.widget.KlimaWidget
import cc.maue.aufwachen.widget.KlimaWidgetEmpfaenger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

const val DATENSCHUTZ = "https://mau-e.com/raumklima/datenschutz.html"

private fun Context.erlaubt(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
private fun Context.oeffnen(i: Intent) = runCatching { startActivity(i) }.isSuccess

/** Ein offener Punkt der Einrichtung. */
private data class Offen(val icon: androidx.compose.ui.graphics.vector.ImageVector, val titel: String, val text: String, val los: () -> Unit)

@Composable
fun Startseite(tick: Int) {
    val c = LocalContext.current
    val p = remember { Logik.prefs(c) }
    val scope = rememberCoroutineScope()
    var neu by remember { mutableStateOf(0) } // nach jeder Änderung neu zeichnen
    val stand = tick + neu
    fun gespeichert() { neu++ }

    // Messwerte
    var klima by remember { mutableStateOf(p.getString("letzter_status", null)?.let { runCatching { Klima.aus(org.json.JSONObject(it)) }.getOrNull() } ?: Klima.LEER) }
    var laedt by remember { mutableStateOf(false) }
    fun laden() {
        laedt = true
        scope.launch {
            klima = withContext(Dispatchers.IO) { KlimaWidget.laden(c) }
            laedt = false
            KlimaWidget.aktualisieren(c)
        }
    }
    LaunchedEffect(tick) { laden() }

    var update by remember { mutableStateOf<Updater.Info?>(null) }
    LaunchedEffect(Unit) { if (!BuildConfig.STORE) update = withContext(Dispatchers.IO) { Updater.pruefen(c) } }

    // Dialoge
    var dialog by remember { mutableStateOf<String?>(null) }

    // Berechtigungen
    val hinweise = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { gespeichert() }
    val hintergrund = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { gespeichert() }
    val standort = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.any { it }) Willkommen.standortSetzen(c) { t -> scope.launch { Toast.makeText(c, t, Toast.LENGTH_LONG).show(); gespeichert() } }
        else gespeichert()
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { ziel ->
        if (ziel == null) return@rememberLauncherForActivityResult
        Toast.makeText(c, "Export läuft …", Toast.LENGTH_SHORT).show()
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                try {
                    val csv = Daten.exportCsv(c)
                    c.contentResolver.openOutputStream(ziel)!!.use { it.write(csv.toByteArray()) }
                    "Export fertig: ${csv.lines().size - 2} Messungen"
                } catch (e: Exception) { "Export fehlgeschlagen: ${e.message ?: e.javaClass.simpleName}" }
            }
            Toast.makeText(c, text, Toast.LENGTH_LONG).show()
        }
    }

    // Was noch fehlt
    val offen = remember(stand) {
        buildList {
            if (Build.VERSION.SDK_INT >= 33 && !c.erlaubt(Manifest.permission.POST_NOTIFICATIONS))
                add(Offen(Icons.Rounded.Notifications, "Benachrichtigungen", "Für Update- und Statushinweise") {
                    hinweise.launch(Manifest.permission.POST_NOTIFICATIONS) })
            if (c.packageName !in NotificationManagerCompat.getEnabledListenerPackages(c))
                add(Offen(Icons.Rounded.NotificationsActive, "Benachrichtigungszugriff", "Damit die App sieht, ob dein Wecker noch klingelt oder schlummert, und den Hinweis der Kamera erkennt") {
                    c.oeffnen(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) })
            if (!c.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(c.packageName))
                add(Offen(Icons.Rounded.BatteryChargingFull, "Akku nicht einschränken", "Sonst schläft die App morgens, wenn sie gebraucht wird. Raumklima auf „Nicht optimieren“ stellen.") {
                    c.oeffnen(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) })
            if (Willkommen.aktiv(c) && Build.VERSION.SDK_INT >= 29 && !c.erlaubt(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
                add(Offen(Icons.Rounded.LocationOn, "Standort „Immer zulassen“", "Nur so merkt die App, wenn du nach Hause kommst. Der Standort bleibt auf dem Handy.") {
                    if (!c.erlaubt(Manifest.permission.ACCESS_FINE_LOCATION))
                        standort.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    else hintergrund.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) })
        }
    }

    val seiten = listOf("Übersicht" to Icons.Rounded.Dashboard, "Automationen" to Icons.Rounded.AutoAwesome, "Protokoll" to Icons.AutoMirrored.Rounded.ReceiptLong)
    val pager = rememberPagerState { seiten.size }
    val haus = remember(stand, klima) { Haus.stand(c) }

    @Composable
    fun Seite(titel: String, untertitel: @Composable () -> Unit, innen: PaddingValues, inhalt: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = innen.calculateTopPadding() + 16.dp, bottom = innen.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                    Text(titel, style = MaterialTheme.typography.headlineLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
                    Spacer(Modifier.height(4.dp))
                    untertitel()
                }
            }
            inhalt()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars).background(MaterialTheme.colorScheme.background))
        },
        bottomBar = {
            NavigationBar {
                seiten.forEachIndexed { i, (name, icon) ->
                    NavigationBarItem(selected = pager.currentPage == i, onClick = { scope.launch { pager.animateScrollToPage(i) } },
                        icon = { Icon(icon, null) }, label = { Text(name) })
                }
            }
        },
    ) { innen ->
        HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1) { seite ->
            when (seite) {
                0 -> Seite("Raumklima", {
                    Status(if (offen.isEmpty()) "Alles eingerichtet" else "${offen.size} ${if (offen.size == 1) "Punkt fehlt" else "Punkte fehlen"} noch",
                        if (offen.isEmpty()) Ampel.gruen else Ampel.orange)
                }, innen) {
                    update?.let { u ->
                        item {
                            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(20.dp), onClick = { Updater.oeffnen(c, u) },
                                modifier = Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.SystemUpdate, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text("Version ${u.version} ist da", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                        Text("Tippen zum Laden, deine Einstellungen bleiben", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                    }
                                }
                            }
                        }
                    }
                    if (offen.isNotEmpty()) item {
                        Karte(Icons.Rounded.Shield, "Einrichtung", "Ein Tipp pro Punkt, dann läuft alles von allein", akzent = Ampel.orange) {
                            offen.forEach { Zeile(it.icon, it.titel, "Erlauben", beschreibung = it.text, onClick = it.los) }
                        }
                    }
                    item { KlimaKarte(klima, haus, laedt) { laden() } }
                    uebersicht(stand)
                }
                1 -> Seite("Automationen", { Status("Alles, was automatisch passiert, mit Test", MaterialTheme.colorScheme.primary) }, innen) {
                    item { MorgenKarte(stand, ::gespeichert) { dialog = it } }
                    item { AnkommenKarte(stand, ::gespeichert, onDialog = { dialog = it }) {
                        if (!c.erlaubt(Manifest.permission.ACCESS_FINE_LOCATION))
                            standort.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                        else Willkommen.standortSetzen(c) { t -> scope.launch { Toast.makeText(c, t, Toast.LENGTH_LONG).show(); gespeichert() } }
                    } }
                    boteAutomationen(stand)
                }
                else -> Seite("Protokoll", { Status("Was passiert ist, Daten und Export", MaterialTheme.colorScheme.primary) }, innen) {
                    item { VerlaufKarte(stand, alleZeigen = true) }
                    protokollSeite(stand)
                    item {
                        Karte(Icons.Rounded.Key, "Daten", "Messwerte, Widget und Export") {
                            Zeile(Icons.Rounded.Key, "Schlüssel der Messdaten", if (Daten.schluessel(c).isEmpty()) "fehlt" else "hinterlegt",
                                if (Daten.schluessel(c).isEmpty()) Ampel.orange else null) { dialog = "schluessel" }
                            Zeile(Icons.Rounded.Widgets, "Widget auf den Startbildschirm", beschreibung = "Von einer Kachel bis groß, passend zu deinen anderen Widgets") {
                                val am = c.getSystemService(AppWidgetManager::class.java)
                                if (am.isRequestPinAppWidgetSupported) am.requestPinAppWidget(ComponentName(c, KlimaWidgetEmpfaenger::class.java), null, null)
                                else Toast.makeText(c, "Startbildschirm lang drücken, Widgets, Raumklima", Toast.LENGTH_LONG).show()
                            }
                            Zeile(Icons.Rounded.FileDownload, "Verlauf exportieren", beschreibung = "Alle Messungen als CSV, z. B. für Excel") {
                                export.launch("raumklima-${LocalDate.now()}.csv")
                            }
                        }
                    }
                    item {
                        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            Text("Version ${Updater.installiertName(c)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = { c.oeffnen(Intent(Intent.ACTION_VIEW, Uri.parse(DATENSCHUTZ))) }) { Text("Datenschutz") }
                        }
                    }
                }
            }
        }
    }

    when (dialog) {
        "morgen_url" -> TextDialog("Alexa-Routine „Guten Morgen“", "Den Link aus „URL Routine Trigger“ einfügen. Er startet Licht und Musik.",
            Logik.url(c), "https://www.virtualsmarthome.xyz/…") { t -> if (t != null) { p.edit().putString("url", t).apply(); gespeichert() }; dialog = null }
        "willkommen_url" -> TextDialog("Alexa-Routine „Willkommen“", "Den Link aus „URL Routine Trigger“ einfügen. Er macht Licht an und begrüßt dich.",
            Willkommen.url(c), "https://www.virtualsmarthome.xyz/…") { t -> if (t != null) { p.edit().putString("willkommen_url", t).apply(); gespeichert() }; dialog = null }
        "schluessel" -> TextDialog("Schlüssel der Messdaten", "Damit entschlüsselt die App die Werte deines Home Coach. Er bleibt nur auf diesem Handy.",
            Daten.schluessel(c), "Schlüssel", geheim = true) { t ->
            if (t != null) { p.edit().putString("schluessel", t).apply(); KlimaWidget.planen(c); laden(); gespeichert() }; dialog = null }
        "ab", "bis" -> ZeitDialog(if (dialog == "ab") "Frühestens" else "Spätestens", if (dialog == "ab") Logik.ab(c) else Logik.bis(c)) { z ->
            if (z != null) {
                val ab = if (dialog == "ab") z else Logik.ab(c)
                val bis = if (dialog == "bis") z else Logik.bis(c)
                if (ab.isBefore(bis)) { p.edit().putString("ab", ab.toString()).putString("bis", bis.toString()).apply(); Logik.planen(c); gespeichert() }
                else Toast.makeText(c, "„Frühestens“ muss vor „Spätestens“ liegen", Toast.LENGTH_LONG).show()
            }
            dialog = null
        }
    }
}

private val UHR = DateTimeFormatter.ofPattern("HH:mm")
private val TAG_UHR = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.GERMAN)

@Composable
private fun MorgenKarte(stand: Int, gespeichert: () -> Unit, onDialog: (String) -> Unit) {
    val c = LocalContext.current
    val p = Logik.prefs(c)
    val wecker = remember(stand) { c.getSystemService(AlarmManager::class.java).nextAlarmClock?.triggerTime }
    val weckerText = wecker?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(TAG_UHR) } ?: "keiner gestellt"
    val heute = Logik.heuteSchon(c)
    Karte(Icons.Rounded.WbSunny, "Morgen",
        if (heute) "Heute schon gestartet" else "Licht und Musik, sobald du wach bist", akzent = Ampel.sonne) {
        Zeile(Icons.Rounded.Alarm, "Nächster Wecker", weckerText, MaterialTheme.colorScheme.onSurfaceVariant)
        Zeile(Icons.Rounded.Link, "Alexa-Routine", if (Logik.url(c).isEmpty()) "Link fehlt" else "verbunden",
            if (Logik.url(c).isEmpty()) Ampel.orange else null) { onDialog("morgen_url") }
        Zeile(Icons.Rounded.Schedule, "Frühestens", Logik.ab(c).format(UHR)) { onDialog("ab") }
        Zeile(Icons.Rounded.Schedule, "Spätestens", Logik.bis(c).format(UHR)) { onDialog("bis") }
        HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        SchalterZeile(Icons.Rounded.Alarm, "Nach dem Wecker", "Startet erst, wenn du ihn ausschaltest. Schlummern bleibt ruhig.",
            p.getBoolean("wecker", true)) { p.edit().putBoolean("wecker", it).apply(); gespeichert() }
        SchalterZeile(Icons.Rounded.LockOpen, "Ohne Wecker", "Beim ersten Entsperren im Zeitfenster",
            p.getBoolean("entsperren", true)) { p.edit().putBoolean("entsperren", it).apply(); Logik.planen(c); gespeichert() }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            FilledTonalButton(onClick = {
                Logik.aufwachen(c, "Test") { }
                Toast.makeText(c, "Licht und Musik werden gestartet", Toast.LENGTH_SHORT).show()
            }) { Icon(Icons.Rounded.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Jetzt testen") }
        }
    }
}

@Composable
private fun AnkommenKarte(stand: Int, gespeichert: () -> Unit, onDialog: (String) -> Unit, hierZuhause: () -> Unit) {
    val c = LocalContext.current
    val p = Logik.prefs(c)
    val aktiv = Willkommen.aktiv(c)
    val zuhause = remember(stand) { Willkommen.zuhause(c) }
    val untergang = zuhause?.let { (la, lo) -> Willkommen.sonnenuntergang(la, lo).format(UHR) }
    val scharfBis = p.getLong("scharf_bis", 0L).takeIf { it > System.currentTimeMillis() }
    var radius by remember(stand) { mutableFloatStateOf(Willkommen.radius(c).toFloat()) }

    Karte(Icons.Rounded.Home, "Ankommen",
        when {
            !aktiv -> "Begrüßung ist aus"
            scharfBis != null -> "Bereit bis ${Instant.ofEpochMilli(scharfBis).atZone(ZoneId.systemDefault()).format(UHR)}, die Kamera entscheidet"
            else -> "Handy in der Nähe und Kamera sieht dich: Licht an, Alexa begrüßt dich"
        }) {
        SchalterZeile(Icons.Rounded.Home, "Begrüßung", "Nur wenn beides passt, Stehenbleiben im Flur löst nichts aus", aktiv) {
            p.edit().putBoolean("willkommen_aktiv", it).apply(); Willkommen.registrieren(c); gespeichert()
        }
        AnimatedVisibility(aktiv) {
            Column {
                SchalterZeile(Icons.Rounded.NightsStay, "Nur wenn es dunkel wird",
                    untergang?.let { "Heute ab etwa $it" } ?: "Ab Sonnenuntergang", Willkommen.nurAbends(c)) {
                    p.edit().putBoolean("willkommen_abends", it).apply(); gespeichert()
                }
                Zeile(Icons.Rounded.MyLocation, "Zuhause", if (zuhause == null) "festlegen" else "neu festlegen",
                    if (zuhause == null) Ampel.orange else null,
                    beschreibung = if (zuhause == null) "Einmal zu Hause antippen" else "Gespeichert, bleibt auf dem Handy", onClick = hierZuhause)
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Radar, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(16.dp))
                        Text("Radius", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text("${radius.roundToInt()} m", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(value = radius, onValueChange = { radius = (it / 5).roundToInt() * 5f }, valueRange = 10f..200f,
                        onValueChangeFinished = {
                            p.edit().putInt("willkommen_radius", radius.roundToInt()).apply(); Willkommen.registrieren(c); gespeichert()
                        }, modifier = Modifier.padding(start = 38.dp))
                    Text("Klein ist genauer. Bleibt die Begrüßung mal aus, etwas größer stellen.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 38.dp))
                }
                Zeile(Icons.Rounded.Link, "Alexa-Routine", if (Willkommen.url(c).isEmpty()) "Link fehlt" else "verbunden",
                    if (Willkommen.url(c).isEmpty()) Ampel.orange else null) { onDialog("willkommen_url") }
                Zeile(Icons.Rounded.Videocam, "Kamera an der Haustür",
                    if (c.packageName in NotificationManagerCompat.getEnabledListenerPackages(c)) "hört mit" else "kein Zugriff",
                    if (c.packageName in NotificationManagerCompat.getEnabledListenerPackages(c)) null else Ampel.orange,
                    beschreibung = "Über den stillen Hinweis aus Google Home")
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = {
                        Willkommen.ausloesen(c, "Test"); Toast.makeText(c, "Begrüßung wird gestartet", Toast.LENGTH_SHORT).show()
                    }) { Icon(Icons.Rounded.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Testen") }
                    OutlinedButton(onClick = {
                        p.edit().putLong("scharf_bis", System.currentTimeMillis() + 20 * 60_000L).apply(); gespeichert()
                        Toast.makeText(c, "20 Minuten bereit. Jetzt einmal vor der Kamera durch die Tür gehen.", Toast.LENGTH_LONG).show()
                    }) { Icon(Icons.Rounded.DirectionsWalk, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Ankunft proben") }
                }
            }
        }
    }
}

@Composable
private fun VerlaufKarte(stand: Int, alleZeigen: Boolean = false) {
    val c = LocalContext.current
    val zeilen = remember(stand) {
        Logik.prefs(c).getString("protokoll", "").orEmpty().lines().filter { it.isNotBlank() }
    }
    var alle by remember { mutableStateOf(alleZeigen) }
    Karte(Icons.Rounded.History, "Verlauf", if (zeilen.isEmpty()) "Noch nichts passiert" else "Was die App zuletzt getan hat") {
        val heute = LocalDate.now().toString()
        (if (alle) zeilen else zeilen.take(4)).forEach { z ->
            val datum = z.take(10)
            val zeit = z.drop(11).substringBefore("  ").take(5)
            val text = z.substringAfter("  ", z)
            val gut = text.contains("gestartet")
            val fehler = text.contains("Fehler")
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.Top) {
                Icon(if (gut) Icons.Rounded.CheckCircle else Icons.Rounded.History, null,
                    tint = when { gut -> Ampel.gruen; fehler -> Ampel.rot; else -> MaterialTheme.colorScheme.onSurfaceVariant },
                    modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(if (datum == heute) "heute $zeit" else "${datum.drop(8)}.${datum.drop(5).take(2)}. $zeit",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (zeilen.size > 4) TextButton(onClick = { alle = !alle }, modifier = Modifier.padding(start = 8.dp)) {
            Text(if (alle) "Weniger zeigen" else "Alle ${zeilen.size} zeigen")
        }
    }
}
