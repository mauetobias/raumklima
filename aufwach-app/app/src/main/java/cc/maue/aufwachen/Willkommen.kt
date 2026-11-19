package cc.maue.aufwachen

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.*

/**
 * „Willkommen zu Hause“ in zwei Stufen:
 *  1. Das Handy kommt in den Bereich um die Wohnung: nur „scharf“ für 20 Minuten, es passiert noch nichts.
 *  2. Die Kamera an der Haustür sieht jemanden: Google Home schickt einen stillen Hinweis, die App fängt ihn ab
 *     und startet, wenn scharf und schon dunkel, die Alexa-Routine (Licht an, Begrüßung).
 * Wer im Flur stehen bleibt, löst also nichts aus. Erst wer durch die Tür kommt.
 */
object Willkommen {
    private const val ID = "zuhause"
    private const val RADIUS = 150f

    fun url(c: Context) = Logik.prefs(c).getString("willkommen_url", "")!!.trim()
    fun aktiv(c: Context) = Logik.prefs(c).getBoolean("willkommen_aktiv", false)
    fun nurAbends(c: Context) = Logik.prefs(c).getBoolean("willkommen_abends", true)
    fun zuhause(c: Context): Pair<Double, Double>? {
        val p = Logik.prefs(c)
        if (!p.contains("lat")) return null
        return p.getFloat("lat", 0f).toDouble() to p.getFloat("lon", 0f).toDouble()
    }

    @SuppressLint("MissingPermission")
    fun standortSetzen(c: Context, fertig: (String) -> Unit) {
        LocationServices.getFusedLocationProviderClient(c).getCurrentLocation(
            com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { l ->
                if (l == null) { fertig("Kein Standort gefunden, bitte draußen oder am Fenster nochmal"); return@addOnSuccessListener }
                Logik.prefs(c).edit().putFloat("lat", l.latitude.toFloat()).putFloat("lon", l.longitude.toFloat()).apply()
                registrieren(c)
                fertig("Zuhause gespeichert (±${l.accuracy.toInt()} m)")
            }
            .addOnFailureListener { fertig("Standort nicht verfügbar: ${it.message}") }
    }

    private fun pending(c: Context) = PendingIntent.getBroadcast(c, 5, Intent(c, Ankunft::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)

    @SuppressLint("MissingPermission")
    fun registrieren(c: Context) {
        val app = c.applicationContext
        val client = LocationServices.getGeofencingClient(app)
        client.removeGeofences(pending(app))
        val (lat, lon) = zuhause(app) ?: return
        if (!aktiv(app)) return
        val fence = Geofence.Builder().setRequestId(ID).setCircularRegion(lat, lon, RADIUS)
            .setExpirationDuration(Geofence.NEVER_EXPIRE).setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
            .setNotificationResponsiveness(60_000).build()
        val req = GeofencingRequest.Builder().setInitialTrigger(0).addGeofence(fence).build()
        runCatching { client.addGeofences(req, pending(app)) }
            .onFailure { Logik.protokoll(app, "Willkommen: Geofence fehlgeschlagen (${it.javaClass.simpleName})") }
    }

    /** Sonnenuntergang (Ortszeit) nach der NOAA-Näherung. */
    fun sonnenuntergang(lat: Double, lon: Double, tag: LocalDate = LocalDate.now()): LocalTime {
        val n = tag.dayOfYear
        val g = 2 * PI / 365 * (n - 1)
        val eqt = 229.18 * (0.000075 + 0.001868 * cos(g) - 0.032077 * sin(g) - 0.014615 * cos(2 * g) - 0.040849 * sin(2 * g))
        val decl = 0.006918 - 0.399912 * cos(g) + 0.070257 * sin(g) - 0.006758 * cos(2 * g) +
            0.000907 * sin(2 * g) - 0.002697 * cos(3 * g) + 0.00148 * sin(3 * g)
        val phi = Math.toRadians(lat)
        val ha = acos((cos(Math.toRadians(90.833)) / (cos(phi) * cos(decl)) - tan(phi) * tan(decl)).coerceIn(-1.0, 1.0))
        val utcMin = 720 - 4 * (lon - Math.toDegrees(ha)) - eqt
        val offset = ZoneId.systemDefault().rules.getOffset(tag.atStartOfDay(ZoneId.systemDefault()).toInstant()).totalSeconds / 60
        val m = ((utcMin + offset).roundToInt() % 1440 + 1440) % 1440
        return LocalTime.of(m / 60, m % 60)
    }

    fun istDunkel(c: Context): Boolean {
        val (lat, lon) = zuhause(c) ?: return true
        val jetzt = LocalTime.now()
        return !jetzt.isBefore(sonnenuntergang(lat, lon).minusMinutes(20)) || jetzt.isBefore(LocalTime.of(5, 0))
    }

    fun ausloesen(c: Context, grund: String) {
        val app = c.applicationContext
        val ziel = url(app)
        if (ziel.isEmpty()) { Logik.protokoll(app, "Willkommen: kein Link eingetragen"); return }
        Thread {
            val text = try {
                val v = URL(ziel).openConnection() as HttpURLConnection
                v.connectTimeout = 15000; v.readTimeout = 15000
                val code = v.responseCode; v.disconnect()
                if (code in 200..299) "Begrüßung gestartet" else "Fehler: Antwort $code"
            } catch (e: Exception) { "Fehler: ${e.javaClass.simpleName}" }
            Logik.protokoll(app, "Willkommen ($grund) → $text")
        }.start()
    }

    private const val SCHARF_MS = 20 * 60_000L

    fun scharf(c: Context) = System.currentTimeMillis() < Logik.prefs(c).getLong("scharf_bis", 0L)

    /** Stufe 1: Handy nähert sich der Wohnung. */
    class Ankunft : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val e = GeofencingEvent.fromIntent(i) ?: return
            if (e.hasError() || e.geofenceTransition != Geofence.GEOFENCE_TRANSITION_ENTER) return
            Logik.prefs(c).edit().putLong("scharf_bis", System.currentTimeMillis() + SCHARF_MS).apply()
            Logik.protokoll(c, "Nähe Wohnung: Begrüßung 20 Min scharf")
        }
    }

    /** Stufe 2: Kamera hat jemanden gesehen (über den stillen Google-Home-Hinweis). */
    fun kameraSahJemanden(c: Context) {
        if (!aktiv(c)) return
        if (!scharf(c)) { Logik.protokoll(c, "Kamera: Person, aber Handy nicht in der Nähe: nichts"); return }
        if (nurAbends(c) && !istDunkel(c)) { Logik.protokoll(c, "Kamera: du, aber noch hell: nichts"); return }
        Logik.prefs(c).edit().putLong("scharf_bis", 0L).apply()
        ausloesen(c, "durch die Tür")
    }
}
