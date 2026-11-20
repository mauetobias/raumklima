package cc.maue.aufwachen

import android.content.Context

/**
 * Licht und Fenster in der Wohnung. Google Home meldet jede Änderung als stillen Hinweis
 * „Raumklima-Status“ mit Text wie „licht Bett an“ oder „fenster Küche auf“. Die App merkt sich
 * den letzten Stand je Gerät.
 */
object Haus {
    data class Stand(val lichterAn: List<String>, val lichterBekannt: Int, val fensterAuf: List<String>, val fensterBekannt: Int,
                     val kameraAn: Boolean?, val entfeuchterAn: Boolean?, val roborockFaehrt: Boolean?)

    private fun p(c: Context) = c.getSharedPreferences("haus", Context.MODE_PRIVATE)

    /** Text aus dem Hinweis auswerten. true, wenn er verstanden wurde. */
    fun melden(c: Context, text: String): Boolean {
        val w = text.trim().split(Regex("\\s+"))
        if (w.size < 3) return false
        val art = w.first().lowercase()
        val name = w.subList(1, w.size - 1).joinToString(" ")
        val wert = when (w.last().lowercase()) { "an", "auf", "offen", "on", "open" -> true; "aus", "zu", "off", "closed" -> false; else -> return false }
        val schluessel = when (art) { "licht", "lampe" -> "licht"; "fenster", "tür", "tuer" -> "fenster"; "kamera" -> "kamera"; "entfeuchter" -> "entfeuchter"; "roborock" -> "roborock"; else -> return false }
        p(c).edit().putBoolean("$schluessel|$name", wert).putLong("zeit", System.currentTimeMillis()).apply()
        return true
    }

    /** Merkt sich die letzte Meldung von Google Home (für das Protokoll). */
    fun empfangen(c: Context, text: String) {
        p(c).edit().putString("meldung_text", text).putLong("meldung_zeit", System.currentTimeMillis()).apply()
    }
    fun letzteMeldung(c: Context): Pair<Long, String>? {
        val t = p(c).getLong("meldung_zeit", 0L); if (t == 0L) return null
        return t to p(c).getString("meldung_text", "").orEmpty()
    }

    /** Stand direkt setzen (z. B. von der Hue Bridge). */
    fun setzen(c: Context, art: String, name: String, wert: Boolean) {
        p(c).edit().putBoolean("$art|$name", wert).putLong("zeit", System.currentTimeMillis()).apply()
    }

    fun stand(c: Context): Stand {
        val alle = p(c).all.filterValues { it is Boolean }
        fun liste(art: String) = alle.filterKeys { it.startsWith("$art|") }.mapKeys { it.key.substringAfter('|') }
        val l = liste("licht"); val f = liste("fenster")
        val k = liste("kamera"); val e = liste("entfeuchter"); val r = liste("roborock")
        return Stand(l.filterValues { it == true }.keys.sorted(), l.size, f.filterValues { it == true }.keys.sorted(), f.size,
            if (k.isEmpty()) null else k.values.any { it == true },
            if (e.isEmpty()) null else e.values.any { it == true },
            if (r.isEmpty()) null else r.values.any { it == true })
    }
}
