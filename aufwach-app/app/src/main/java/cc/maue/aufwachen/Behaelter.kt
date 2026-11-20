package cc.maue.aufwachen

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.json.JSONObject

/**
 * Staubbehälter des Roborock. Der S5 Max hat keinen Füllsensor, deshalb zählt die App die
 * Saugminuten seit dem letzten Leeren (aus der Gesamtlaufzeit, die der Bote mitliefert).
 * Ist die eingestellte Grenze erreicht, kommt ein Hinweis mit Knopf „Geleert“.
 */
object Behaelter {
    private fun p(c: Context) = c.getSharedPreferences("behaelter", Context.MODE_PRIVATE)
    fun grenze(c: Context) = p(c).getInt("grenze", 60)
    fun grenzeSetzen(c: Context, min: Int) = p(c).edit().putInt("grenze", min.coerceIn(10, 600)).apply()

    /** Minuten seit dem letzten Leeren, oder null, solange der Bote noch keine Gesamtzeit kennt. */
    fun seitLeeren(c: Context, status: JSONObject?): Int? {
        val gesamt = status?.optInt("roborock_gesamt_minuten", -1)?.takeIf { it >= 0 } ?: return null
        val bei = p(c).getInt("geleert_bei", -1)
        if (bei < 0 || bei > gesamt) { p(c).edit().putInt("geleert_bei", gesamt).apply(); return 0 }
        return gesamt - bei
    }

    fun geleert(c: Context) {
        val s = Logik.prefs(c).getString("letzter_status", null)?.let { runCatching { JSONObject(it) }.getOrNull() }
        val gesamt = s?.optInt("roborock_gesamt_minuten", -1) ?: -1
        p(c).edit().putInt("geleert_bei", if (gesamt >= 0) gesamt else 0).putBoolean("gemeldet", false).apply()
        c.getSystemService(NotificationManager::class.java).cancel(21)
        Logik.protokoll(c, "Roborock: Behälter geleert")
    }

    /** Nach jedem Laden prüfen: voll, dann einmal melden. */
    fun pruefen(c: Context, status: JSONObject?) {
        val min = seitLeeren(c, status) ?: return
        if (min < grenze(c) || p(c).getBoolean("gemeldet", false)) return
        p(c).edit().putBoolean("gemeldet", true).apply()
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("roborock", "Roborock", NotificationManager.IMPORTANCE_DEFAULT))
        val leeren = PendingIntent.getBroadcast(c, 22, Intent(c, Geleert::class.java), PendingIntent.FLAG_IMMUTABLE)
        nm.notify(21, android.app.Notification.Builder(c, "roborock")
            .setSmallIcon(R.drawable.ic_entfeuchter)
            .setContentTitle("Roborock: Behälter leeren")
            .setContentText("Seit dem letzten Leeren $min Minuten gesaugt. Voll saugt er schlechter und der Motor arbeitet mehr.")
            .addAction(android.app.Notification.Action.Builder(null, "Geleert", leeren).build())
            .setAutoCancel(true).build())
    }

    class Geleert : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { geleert(c); cc.maue.aufwachen.widget.KlimaWidget.aktualisieren(c) }
    }
}
