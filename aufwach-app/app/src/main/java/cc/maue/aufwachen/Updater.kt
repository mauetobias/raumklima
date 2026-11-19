package cc.maue.aufwachen

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Updates wie bei den anderen Apps: neueste Version aus den GitHub-Releases von mauetobias/raumklima.
 * Geladen wird im Browser (die App installiert selbst nichts, damit Play Protect sie nicht beanstandet).
 * Einmal am Tag wird nachgeschaut, bei einer neuen Version kommt eine Benachrichtigung.
 */
object Updater {
    private const val GITHUB = "https://api.github.com/repos/mauetobias/raumklima/releases/latest"

    data class Info(val version: String, val code: Int, val url: String)

    fun installiert(c: Context): Long =
        runCatching { c.packageManager.getPackageInfo(c.packageName, 0).longVersionCode }.getOrDefault(0L)

    fun installiertName(c: Context): String =
        runCatching { c.packageManager.getPackageInfo(c.packageName, 0).versionName }.getOrNull() ?: "?"

    fun neueste(): Info {
        val v = URL(GITHUB).openConnection() as HttpURLConnection
        v.setRequestProperty("Accept", "application/vnd.github+json")
        v.setRequestProperty("User-Agent", "Raumklima-App")
        v.connectTimeout = 15000; v.readTimeout = 15000
        val o = JSONObject(v.inputStream.bufferedReader().readText())
        v.disconnect()
        val code = o.getString("tag_name").substringAfterLast("-").toInt()
        val assets = o.getJSONArray("assets")
        val url = (0 until assets.length()).map { assets.getJSONObject(it) }
            .first { it.optString("name").endsWith(".apk") }.getString("browser_download_url")
        return Info("1.$code", code, url)
    }

    /** Neuere Version oder null. */
    fun pruefen(c: Context): Info? = runCatching { neueste().takeIf { it.code > installiert(c) } }.getOrNull()

    fun oeffnen(c: Context, info: Info) {
        c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun planen(c: Context) {
        WorkManager.getInstance(c).enqueueUniquePeriodicWork("update-check", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<Arbeiter>(1, TimeUnit.DAYS).build())
    }

    class Arbeiter(c: Context, p: WorkerParameters) : Worker(c, p) {
        override fun doWork(): Result {
            val info = pruefen(applicationContext) ?: return Result.success()
            val p = Logik.prefs(applicationContext)
            if (p.getInt("gemeldet", 0) == info.code) return Result.success()
            p.edit().putInt("gemeldet", info.code).apply()
            val nm = applicationContext.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel("updates", "Updates", NotificationManager.IMPORTANCE_DEFAULT))
            val pi = PendingIntent.getActivity(applicationContext, 3,
                Intent(Intent.ACTION_VIEW, Uri.parse(info.url)), PendingIntent.FLAG_IMMUTABLE)
            nm.notify(9, android.app.Notification.Builder(applicationContext, "updates")
                .setSmallIcon(R.drawable.ic_sonne)
                .setContentTitle("Raumklima ${info.version} ist da")
                .setContentText("Tippen zum Herunterladen, danach installieren. Deine Einstellungen bleiben.")
                .setContentIntent(pi).setAutoCancel(true).build())
            return Result.success()
        }
    }
}
