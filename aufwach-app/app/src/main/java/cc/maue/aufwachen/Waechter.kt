package cc.maue.aufwachen

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import java.time.Duration
import java.time.LocalTime

/**
 * Läuft nur morgens im eingestellten Zeitfenster und wartet auf das erste Entsperren.
 * Danach (oder am Ende des Fensters) beendet er sich selbst.
 */
class Waechter : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val entsperrt = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (!Logik.imFenster(c)) return
            if (!Logik.entsperrenErlaubt(c)) { Logik.protokoll(c, "Entsperrt, aber Wecker läuft noch oder kommt noch: nichts"); return }
            Logik.aufwachen(c, "Entsperren") { stopSelf() }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("waechter", "Morgen-Wächter", NotificationManager.IMPORTANCE_MIN))
        val n = Notification.Builder(this, "waechter")
            .setSmallIcon(R.drawable.ic_sonne)
            .setContentTitle("Wartet auf dein Aufwachen")
            .setContentText("Beim ersten Entsperren startet die Morgen-Routine")
            .build()
        startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        registerReceiver(entsperrt, IntentFilter(Intent.ACTION_USER_PRESENT))
        val rest = Duration.between(LocalTime.now(), Logik.bis(this)).toMillis()
        handler.postDelayed({ stopSelf() }, rest.coerceAtLeast(60_000))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Logik.heuteSchon(this) || !Logik.imFenster(this)) stopSelf()
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        try { unregisterReceiver(entsperrt) } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
