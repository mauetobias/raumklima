package cc.maue.aufwachen

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Fängt den stillen Hinweis „Willkommen-Ausloeser“ aus der Google Home App ab, blendet ihn sofort aus
 * und gibt ihn an die Begrüßung weiter. Alle anderen Benachrichtigungen werden nicht angefasst.
 */
class KameraHorcher : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!sbn.packageName.startsWith("com.google.android.apps.chromecast")) return
        val titel = sbn.notification.extras.getCharSequence("android.title")?.toString().orEmpty()
        if (!titel.contains("Willkommen-Ausloeser")) return
        runCatching { cancelNotification(sbn.key) }
        Willkommen.kameraSahJemanden(applicationContext)
    }
}
