package cc.maue.aufwachen

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Homescreen-Widget mit den aktuellen Werten des Raumklima-Boten. Tippen = sofort aktualisieren. */
class KlimaWidget : AppWidgetProvider() {

    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) {
        planen(c)
        aktualisieren(c)
    }

    override fun onReceive(c: Context, i: Intent) {
        super.onReceive(c, i)
        if (i.action == AKTUALISIEREN) {
            val ende = goAsync()
            thread { try { holenUndZeigen(c) } finally { ende.finish() } }
        }
    }

    class Arbeiter(c: Context, p: WorkerParameters) : Worker(c, p) {
        override fun doWork(): Result { holenUndZeigen(applicationContext); return Result.success() }
    }

    companion object {
        const val AKTUALISIEREN = "cc.maue.aufwachen.WIDGET_AKTUALISIEREN"

        fun planen(c: Context) {
            WorkManager.getInstance(c).enqueueUniquePeriodicWork("klima-widget", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<Arbeiter>(15, TimeUnit.MINUTES).build())
        }

        fun aktualisieren(c: Context) {
            c.sendBroadcast(Intent(c, KlimaWidget::class.java).setAction(AKTUALISIEREN))
        }

        fun holenUndZeigen(c: Context) {
            val v = try {
                if (Daten.schluessel(c).isEmpty()) fehler(c, "Schlüssel in der App eintragen")
                else ansicht(c, Daten.status(c))
            } catch (e: Exception) {
                fehler(c, "Keine Daten (${e.message ?: e.javaClass.simpleName})")
            }
            val m = AppWidgetManager.getInstance(c)
            m.updateAppWidget(ComponentName(c, KlimaWidget::class.java), v)
        }

        private fun basis(c: Context) = RemoteViews(c.packageName, R.layout.widget_klima).apply {
            val tippen = PendingIntent.getBroadcast(c, 0, Intent(c, KlimaWidget::class.java).setAction(AKTUALISIEREN),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            setOnClickPendingIntent(R.id.widget_wurzel, tippen)
        }

        private fun fehler(c: Context, text: String) = basis(c).apply { setTextViewText(R.id.w_stand, text) }

        private fun ansicht(c: Context, s: JSONObject) = basis(c).apply {
            val feuchte = s.optInt("feuchte", -1)
            val co2 = s.optInt("co2", -1)
            setTextViewText(R.id.w_feuchte, if (feuchte >= 0) "$feuchte %" else "–")
            setTextColor(R.id.w_feuchte, when {
                feuchte < 0 -> Color.WHITE
                feuchte > 62 -> Color.parseColor("#FF6B6B")
                feuchte > 58 -> Color.parseColor("#FFB347")
                feuchte < 40 -> Color.parseColor("#6EC6FF")
                else -> Color.parseColor("#7CE38B")
            })
            setTextViewText(R.id.w_co2, if (co2 >= 0) "$co2 ppm" else "–")
            setTextColor(R.id.w_co2, when {
                co2 < 0 -> Color.WHITE
                co2 >= 1400 -> Color.parseColor("#FF6B6B")
                co2 >= 1000 -> Color.parseColor("#FFB347")
                else -> Color.parseColor("#7CE38B")
            })
            setTextViewText(R.id.w_temp, if (s.has("temperatur")) String.format("%.1f °C", s.getDouble("temperatur")).replace('.', ',') else "–")
            setTextViewText(R.id.w_laerm, if (s.has("laerm")) "${s.getInt("laerm")} dB" else "–")
            val an = s.optBoolean("entfeuchter_an", false)
            val tank = s.optBoolean("entfeuchter_tank_voll", false)
            setTextViewText(R.id.w_entfeuchter, when {
                tank -> "Entfeuchter: Tank voll!"
                an -> "Entfeuchter: läuft" + if (s.optBoolean("nachtruhe")) " (leise)" else ""
                else -> "Entfeuchter: aus"
            })
            val zeit = s.optString("zeit", "")
            val uhr = if (zeit.length >= 16) zeit.substring(11, 16) else "?"
            setTextViewText(R.id.w_stand, "Stand $uhr" + if (co2 >= 1400) "  •  Bitte lüften" else "")
        }
    }
}
