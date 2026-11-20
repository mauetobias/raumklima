package cc.maue.aufwachen

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Hält zu Hause eine offene Leitung zur Hue Bridge. Die Bridge meldet jede Änderung sofort
 * (Licht an, Licht aus), die App übernimmt sie und zeichnet Widget und App neu.
 * Ist die Bridge nicht erreichbar (unterwegs), wird es nach einer Minute erneut versucht.
 */
class HueHorcher : Service() {
    @Volatile private var aktiv = true
    private var faden: Thread? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("hue", "Licht live", NotificationManager.IMPORTANCE_MIN))
        val n = Notification.Builder(this, "hue").setSmallIcon(R.drawable.ic_lampe)
            .setContentTitle("Licht live").setContentText("Zu Hause kommt jede Lichtänderung sofort ins Widget").build()
        startForeground(11, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        faden = Thread { schleife() }.apply { isDaemon = true; start() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Hue.gekoppelt(this)) stopSelf()
        return START_STICKY
    }

    override fun onDestroy() { aktiv = false; faden?.interrupt(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null

    private fun schleife() {
        while (aktiv) {
            val ok = runCatching { lauschen() }.isSuccess
            if (!aktiv) break
            runCatching { Thread.sleep(if (ok) 2_000 else 60_000) }
        }
    }

    /** Lampen-Namen holen, dann den Ereignis-Strom der Bridge lesen, bis er abreißt. */
    private fun lauschen() {
        val ip = Hue.ip(this) ?: error("keine Bridge")
        val schluessel = Hue.nutzer(this) ?: error("nicht gekoppelt")
        val namen = mutableMapOf<String, String>()
        val liste = JSONObject(verbinden("https://$ip/clip/v2/resource/light", schluessel, 4000).inputStream.bufferedReader().readText())
        val daten = liste.getJSONArray("data")
        for (i in 0 until daten.length()) {
            val l = daten.getJSONObject(i)
            val name = l.getJSONObject("metadata").getString("name")
            namen[l.getString("id")] = name
            Haus.setzen(this, "licht", name, l.optJSONObject("on")?.optBoolean("on") == true)
        }
        widget()
        val v = verbinden("https://$ip/eventstream/clip/v2", schluessel, 4000, lesen = 0)
        v.setRequestProperty("Accept", "text/event-stream")
        v.inputStream.bufferedReader().useLines { zeilen ->
            for (z in zeilen) {
                if (!aktiv) break
                if (!z.startsWith("data:")) continue
                var geaendert = false
                val ereignisse = runCatching { JSONArray(z.removePrefix("data:").trim()) }.getOrNull() ?: continue
                for (i in 0 until ereignisse.length()) {
                    val inhalt = ereignisse.getJSONObject(i).optJSONArray("data") ?: continue
                    for (j in 0 until inhalt.length()) {
                        val d = inhalt.getJSONObject(j)
                        if (d.optString("type") != "light") continue
                        val an = d.optJSONObject("on")?.optBoolean("on") ?: continue
                        val name = namen[d.optString("id")] ?: continue
                        Haus.setzen(this, "licht", name, an); geaendert = true
                    }
                }
                if (geaendert) widget()
            }
        }
    }

    private fun widget() = cc.maue.aufwachen.widget.KlimaWidget.aktualisieren(this)

    private fun verbinden(url: String, schluessel: String, verbinden: Int, lesen: Int = 6000): HttpsURLConnection {
        val v = URL(url).openConnection() as HttpsURLConnection
        // Die Bridge hat ein eigenes Zertifikat, das nur im eigenen WLAN gilt
        v.sslSocketFactory = VERTRAUEN.socketFactory
        v.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
        v.connectTimeout = verbinden; v.readTimeout = lesen
        v.setRequestProperty("hue-application-key", schluessel)
        return v
    }

    companion object {
        private val VERTRAUEN: SSLContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(c: Array<out X509Certificate>?, a: String?) {}
                override fun checkServerTrusted(c: Array<out X509Certificate>?, a: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            }), null)
        }

        fun starten(c: Context) {
            if (!Hue.gekoppelt(c)) return
            runCatching { c.startForegroundService(Intent(c, HueHorcher::class.java)) }
                .onFailure { Logik.protokoll(c, "Licht live konnte nicht starten (${it.javaClass.simpleName})") }
        }
    }
}
