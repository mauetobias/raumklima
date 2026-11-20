package cc.maue.aufwachen

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.graphics.Color
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.LinearLayout
import cc.maue.aufwachen.widget.KlimaWidget
import cc.maue.aufwachen.widget.KlimaWidgetEmpfaenger

/** Nur im Test: zeigt das echte Widget in mehreren Größen (für Bildschirmfotos im Emulator). */
class WidgetVorschau : Activity() {
    private lateinit var host: AppWidgetHost
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        host = AppWidgetHost(this, 4711)
        val am = AppWidgetManager.getInstance(this)
        val info = am.installedProviders.first { it.provider == ComponentName(this, KlimaWidgetEmpfaenger::class.java) }
        val d = resources.displayMetrics.density
        val wurzel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(60, 70, 60)); setPadding(30, 120, 30, 30) }
        val reihe1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val reihe2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun widget(w: Int, h: Int, ziel: LinearLayout) {
            val id = host.allocateAppWidgetId()
            am.bindAppWidgetIdIfAllowed(id, info.provider)
            val opt = Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, w); putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, w)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, h); putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, h)
            }
            am.updateAppWidgetOptions(id, opt)
            val v = host.createView(this, id, info)
            ziel.addView(FrameLayout(this).apply { addView(v) }, LinearLayout.LayoutParams((w * d).toInt(), (h * d).toInt()).apply { setMargins(12, 12, 12, 12) })
        }
        widget(170, 270, reihe1)   // wie bei dir: 2 breit, 2 hoch
        widget(80, 270, reihe1)    // eine Kachel schmal, hoch
        widget(80, 130, reihe1)    // eine Kachel
        widget(360, 120, reihe2)   // breit, flach
        wurzel.addView(reihe1); wurzel.addView(reihe2)
        widget(360, 200, wurzel)   // breit, mittel
        setContentView(wurzel)
        KlimaWidget.aktualisieren(this)
    }
    override fun onStart() { super.onStart(); host.startListening() }
    override fun onStop() { host.stopListening(); super.onStop() }
}
