package cc.maue.aufwachen

import android.content.Context

/**
 * Licht und Fenster in der Wohnung. Google Home meldet jede Änderung als stillen Hinweis
 * „Raumklima-Status“ mit Text wie „licht Bett an“ oder „fenster Küche auf“. Die App merkt sich
 * den letzten Stand je Gerät.
 */
object Haus {
    data class Stand(val lichterAn: List<String>, val lichterBekannt: Int, val fensterAuf: List<String>, val fensterBekannt: Int,
                     val kameraAn: Boolean?)

    private fun p(c: Context) = c.getSharedPreferences("haus", Context.MODE_PRIVATE)

    /** Text aus dem Hinweis auswerten. true, wenn er verstanden wurde. */
    fun melden(c: Context, text: String): Boolean {
        val w = text.trim().split(Regex("\\s+"))
        if (w.size < 3) return false
        val art = w.first().lowercase()
        val name = w.subList(1, w.size - 1).joinToString(" ")
        val wert = when (w.last().lowercase()) { "an", "auf", "offen", "on", "open" -> true; "aus", "zu", "off", "closed" -> false; else -> return false }
        val schluessel = when (art) { "licht", "lampe" -> "licht"; "fenster", "tür", "tuer" -> "fenster"; "kamera" -> "kamera"; else -> return false }
        p(c).edit().putBoolean("$schluessel|$name", wert).putLong("zeit", System.currentTimeMillis()).apply()
        return true
    }

    fun stand(c: Context): Stand {
        val alle = p(c).all
        fun liste(art: String) = alle.filterKeys { it.startsWith("$art|") }.mapKeys { it.key.substringAfter('|') }
        val l = liste("licht"); val f = liste("fenster")
        val k = liste("kamera")
        return Stand(l.filterValues { it == true }.keys.sorted(), l.size, f.filterValues { it == true }.keys.sorted(), f.size,
            if (k.isEmpty()) null else k.values.any { it == true })
    }
}
