package cc.maue.aufwachen

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.concurrent.thread

/** Alles, was die App wissen und tun muss, an einem Ort. */
object Logik {
    private const val PREFS = "aufwachen"
    const val AKTION_WAECHTER_START = "cc.maue.aufwachen.WAECHTER_START"

    fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // Einstellungen
    fun url(c: Context) = prefs(c).getString("url", "")!!.trim()
    fun ab(c: Context) = LocalTime.parse(prefs(c).getString("ab", "06:00")!!)
    fun bis(c: Context) = LocalTime.parse(prefs(c).getString("bis", "11:00")!!)
    fun nachWecker(c: Context) = prefs(c).getBoolean("wecker", true)
    fun nachEntsperren(c: Context) = prefs(c).getBoolean("entsperren", true)

    fun heuteSchon(c: Context) = prefs(c).getString("zuletzt", "") == LocalDate.now().toString()

    fun imFenster(c: Context, t: LocalTime = LocalTime.now()) = !t.isBefore(ab(c)) && t.isBefore(bis(c))

    /**
     * Startet die Morgen-Routine, höchstens einmal am Tag.
     * grund: "Wecker", "Entsperren" oder "Test"
     */
    fun aufwachen(c: Context, grund: String, fertig: (() -> Unit)? = null) {
        val app = c.applicationContext
        if (grund != "Test" && heuteSchon(app)) { fertig?.invoke(); return }
        val ziel = url(app)
        if (ziel.isEmpty()) { protokoll(app, "$grund erkannt, aber kein Link eingetragen"); fertig?.invoke(); return }
        if (grund != "Test") prefs(app).edit().putString("zuletzt", LocalDate.now().toString()).apply()
        thread {
            val ergebnis = try {
                val v = URL(ziel).openConnection() as HttpURLConnection
                v.connectTimeout = 15000; v.readTimeout = 15000
                val code = v.responseCode
                v.disconnect()
                if (code in 200..299) "Routine gestartet" else "Fehler: Antwort $code"
            } catch (e: Exception) {
                "Fehler: ${e.javaClass.simpleName}"
            }
            protokoll(app, "$grund → $ergebnis")
            if (grund != "Test") app.stopService(Intent(app, Waechter::class.java))
            fertig?.invoke()
        }
    }

    fun protokoll(c: Context, text: String) {
        val zeile = "${LocalDate.now()} ${LocalTime.now().withNano(0)}  $text"
        val alt = prefs(c).getString("protokoll", "")!!.lines().filter { it.isNotBlank() }.take(14)
        prefs(c).edit().putString("protokoll", (listOf(zeile) + alt).joinToString("\n")).apply()
    }

    /** Merkt sich den nächsten Wecker. Ausgelöst wird hier nichts mehr: Beim Klingeln springt Android schon weiter. */
    fun weckerGeaendert(c: Context) {
        val am = c.getSystemService(AlarmManager::class.java)
        prefs(c).edit().putLong("naechster_wecker", am.nextAlarmClock?.triggerTime ?: 0L).apply()
    }

    fun weckerKlingelt(c: Context) {
        val p = prefs(c)
        if (System.currentTimeMillis() - p.getLong("klingelte", 0L) > 30 * 60_000) protokoll(c, "Wecker klingelt, warte aufs Ausschalten")
        p.edit().putLong("klingelte", System.currentTimeMillis()).apply()
    }

    /** Ein Wecker-Hinweis ist verschwunden. Wenn nichts mehr klingelt oder schlummert: Routine starten. */
    fun weckerVielleichtAus(c: Context) {
        if (!nachWecker(c) || !LocalTime.now().isBefore(LocalTime.of(13, 0))) return
        if (KameraHorcher.weckerAktiv() == true) { protokoll(c, "Wecker schlummert, warte weiter"); return }
        val naechster = c.getSystemService(AlarmManager::class.java).nextAlarmClock?.triggerTime ?: 0L
        if (naechster in 1..(System.currentTimeMillis() + 20 * 60_000)) { protokoll(c, "Wecker schlummert, warte weiter"); return }
        aufwachen(c, "Wecker aus")
    }

    /** Darf das Entsperren die Routine starten? Nicht, solange ein Wecker klingelt, schlummert oder heute früh noch kommt. */
    fun entsperrenErlaubt(c: Context): Boolean {
        if (KameraHorcher.weckerAktiv() == true) return false
        val jetzt = System.currentTimeMillis()
        if (jetzt - prefs(c).getLong("klingelte", 0L) < 2 * 60_000) return false
        val naechster = c.getSystemService(AlarmManager::class.java).nextAlarmClock?.triggerTime ?: 0L
        if (naechster > jetzt) {
            val zeit = java.time.Instant.ofEpochMilli(naechster).atZone(java.time.ZoneId.systemDefault())
            if (zeit.toLocalDate() == LocalDate.now() && zeit.toLocalTime().isBefore(bis(c))) return false
        }
        return true
    }

    /** Plant den Start des Wächters für den nächsten Morgen und startet ihn sofort, falls wir im Fenster sind. */
    fun planen(c: Context) {
        val app = c.applicationContext
        prefs(app).edit().putLong("naechster_wecker",
            app.getSystemService(AlarmManager::class.java).nextAlarmClock?.triggerTime ?: 0L).apply()
        val am = app.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(app, 1,
            Intent(app, Empfaenger::class.java).setAction(AKTION_WAECHTER_START),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        var start = ZonedDateTime.now().with(ab(app)).withSecond(0).withNano(0)
        if (!start.isAfter(ZonedDateTime.now())) start = start.plusDays(1)
        val ms = start.toInstant().toEpochMilli()
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms())
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
        else
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
        waechterStarten(app)
        Willkommen.registrieren(app)
        HueHorcher.starten(app)
    }

    fun waechterStarten(c: Context) {
        if (!nachEntsperren(c) || heuteSchon(c) || !imFenster(c)) return
        try {
            c.startForegroundService(Intent(c, Waechter::class.java))
        } catch (e: Exception) {
            protokoll(c, "Wächter konnte nicht starten (${e.javaClass.simpleName})")
        }
    }
}
