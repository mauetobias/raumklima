package cc.maue.aufwachen

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Liest nur in der Google-Home-App (und NetHome Plus) mit, was auf den Geräte-Kacheln steht,
 * z. B. „Haustür · Aus“ oder „Luftentfeuchter · An“. So kommt jedes Umschalten sofort in Widget
 * und App, auch bei Geräten, die Google nicht weitermeldet. Nichts wird gespeichert außer dem
 * erkannten Zustand, nichts verlässt das Handy.
 */
class Bildschirmleser : AccessibilityService() {
    private var zuletzt = 0L

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        val pkg = e.packageName?.toString() ?: return
        if (!pkg.startsWith("com.google.android.apps.chromecast") && !pkg.contains("midea") && !pkg.contains("nethome") &&
            !(BuildConfig.DEBUG && pkg == packageName)) return
        val jetzt = System.currentTimeMillis()
        if (jetzt - zuletzt < 400) return // nicht bei jedem Pixel neu lesen
        zuletzt = jetzt
        val wurzel = rootInActiveWindow ?: return
        var geaendert = false
        for ((art, name, muster) in GERAETE) {
            val zustand = finden(wurzel, muster) ?: continue
            val alt = Haus.stand(this).let { if (art == "kamera") it.kameraAn else it.entfeuchterAn }
            if (alt != zustand) { Haus.setzen(this, art, name, zustand); Haus.empfangen(this, "Bildschirm: $art ${if (zustand) "an" else "aus"}"); geaendert = true }
        }
        if (geaendert) cc.maue.aufwachen.widget.KlimaWidget.aktualisieren(this)
    }

    override fun onInterrupt() {}

    companion object {
        private val GERAETE = listOf(
            Triple("kamera", "Haustür", Regex("haustür", RegexOption.IGNORE_CASE)),
            Triple("entfeuchter", "Luftentfeuchter", Regex("luftentfeuchter|entfeuchter|dehumidifier", RegexOption.IGNORE_CASE)),
        )
        private val AUS = Regex("\\b(aus|off|ausgeschaltet|deaktiviert|kamera aus)\\b", RegexOption.IGNORE_CASE)
        private val AN = Regex("\\b(an|ein|on|eingeschaltet|live|läuft|aktiv|entfeuchten|trocknen)\\b", RegexOption.IGNORE_CASE)

        /** Text eines Knotens: sichtbarer Text und Beschreibung (für Bedienungshilfen). */
        private fun text(n: AccessibilityNodeInfo) = listOfNotNull(n.text?.toString(), n.contentDescription?.toString(),
            if (android.os.Build.VERSION.SDK_INT >= 30) n.stateDescription?.toString() else null)
            .joinToString(" ")

        private fun alles(n: AccessibilityNodeInfo, tiefe: Int = 0): String {
            if (tiefe > 4) return ""
            val sb = StringBuilder(text(n))
            for (i in 0 until n.childCount) n.getChild(i)?.let { sb.append(' ').append(alles(it, tiefe + 1)) }
            return sb.toString()
        }

        /** Sucht die Kachel mit dem Gerätenamen und liest daraus an oder aus. */
        fun finden(wurzel: AccessibilityNodeInfo, name: Regex): Boolean? {
            val treffer = mutableListOf<AccessibilityNodeInfo>()
            fun suchen(n: AccessibilityNodeInfo, tiefe: Int) {
                if (tiefe > 40) return
                if (name.containsMatchIn(text(n))) treffer.add(n)
                for (i in 0 until n.childCount) n.getChild(i)?.let { suchen(it, tiefe + 1) }
            }
            suchen(wurzel, 0)
            for (t in treffer) {
                // Kachel: der Knoten selbst oder bis zu drei Ebenen darüber, solange es nur um dieses Gerät geht
                var k: AccessibilityNodeInfo? = t
                repeat(4) {
                    val inhalt = k?.let { alles(it) } ?: return@repeat
                    val rest = name.replace(inhalt, " ")
                    if (inhalt.length < 200) {
                        if (AUS.containsMatchIn(rest)) return false
                        if (AN.containsMatchIn(rest)) return true
                    }
                    k = k?.parent
                }
            }
            return null
        }
    }
}
