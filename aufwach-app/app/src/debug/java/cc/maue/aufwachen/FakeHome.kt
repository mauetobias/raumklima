package cc.maue.aufwachen

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.widget.LinearLayout
import android.widget.TextView

/** Nur im Test: Kacheln wie in der Google-Home-App, die nach 4 Sekunden umschalten. */
class FakeHome : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        fun kachel(name: String, zustand: String): Pair<LinearLayout, TextView> {
            val z = TextView(this).apply { text = zustand; textSize = 14f }
            val l = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 40, 40, 40)
                addView(TextView(this@FakeHome).apply { text = name; textSize = 18f }); addView(z) }
            return l to z
        }
        val (k1, z1) = kachel("Haustür", "Kamera aus")
        val (k2, z2) = kachel("Luftentfeuchter", "Aus")
        val (k3, _) = kachel("Bett", "An")
        setContentView(LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 200, 40, 40); addView(k1); addView(k2); addView(k3) })
        Handler(mainLooper).postDelayed({ z1.text = "Live"; z2.text = "An · 35 %" }, 4000)
    }
}
