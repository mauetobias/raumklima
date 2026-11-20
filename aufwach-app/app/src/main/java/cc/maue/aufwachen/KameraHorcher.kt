package cc.maue.aufwachen

import android.app.Notification
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Liest zwei Dinge aus den Benachrichtigungen:
 *  1. den stillen Hinweis „Willkommen-Ausloeser“ aus der Google Home App (wird sofort ausgeblendet),
 *  2. ob gerade ein Wecker klingelt oder schlummert. Erst wenn der Wecker wirklich aus ist,
 *     startet die Morgen-Routine.
 * Andere Benachrichtigungen werden nicht angefasst.
 */
class KameraHorcher : NotificationListenerService() {
    private val handler = Handler(Looper.getMainLooper())

    override fun onListenerConnected() { instanz = this }
    override fun onListenerDisconnected() { if (instanz === this) instanz = null }
    override fun onDestroy() { if (instanz === this) instanz = null; super.onDestroy() }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (istWecker(sbn)) { Logik.weckerKlingelt(applicationContext); return }
        if (!sbn.packageName.startsWith("com.google.android.apps.chromecast")) return
        val titel = sbn.notification.extras.getCharSequence("android.title")?.toString().orEmpty()
        if (!titel.contains("Willkommen-Ausloeser")) return
        runCatching { cancelNotification(sbn.key) }
        Willkommen.kameraSahJemanden(applicationContext)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (!istWecker(sbn)) return
        // Kurz warten: beim Schlummern erscheint direkt danach die Schlummer-Benachrichtigung.
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ Logik.weckerVielleichtAus(applicationContext) }, 20_000)
    }

    companion object {
        @Volatile var instanz: KameraHorcher? = null

        private val UHR_APPS = listOf("deskclock", "clockpackage", "com.android.alarmclock", "clock")

        fun istWecker(sbn: StatusBarNotification): Boolean {
            val n = sbn.notification
            val uhr = UHR_APPS.any { sbn.packageName.contains(it) }
            if (n.category == Notification.CATEGORY_ALARM) return true
            if (!uhr) return false
            val text = listOf("android.title", "android.text", "android.subText")
                .joinToString(" ") { n.extras.getCharSequence(it)?.toString().orEmpty() }.lowercase()
            return listOf("schlumm", "snooz", "klingelt", "ringing", "wecker läuft").any { text.contains(it) }
        }

        /** null = kein Benachrichtigungszugriff, sonst: klingelt oder schlummert gerade ein Wecker? */
        fun weckerAktiv(): Boolean? {
            val h = instanz ?: return null
            return runCatching { h.activeNotifications.any { istWecker(it) } }.getOrNull()
        }
    }
}
