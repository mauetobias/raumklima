package cc.maue.aufwachen

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Direkter Draht zur Philips Hue Bridge im eigenen WLAN. Viel schneller als der Umweg über
 * Google Home, funktioniert aber nur zu Hause. Unterwegs bleiben die Google-Hinweise.
 */
object Hue {
    private fun p(c: Context) = c.getSharedPreferences("hue", Context.MODE_PRIVATE)
    fun ip(c: Context) = p(c).getString("ip", null)
    fun nutzer(c: Context) = p(c).getString("nutzer", null)
    fun gekoppelt(c: Context) = ip(c) != null && nutzer(c) != null
    fun zuletzt(c: Context) = p(c).getLong("zuletzt", 0L)
    fun lampen(c: Context) = p(c).getInt("lampen", 0)

    private fun holen(url: String, methode: String = "GET", body: String? = null, zeit: Int = 2500): String {
        val v = URL(url).openConnection() as HttpURLConnection
        v.connectTimeout = zeit; v.readTimeout = zeit; v.requestMethod = methode
        if (body != null) { v.doOutput = true; v.outputStream.use { it.write(body.toByteArray()) } }
        return try { v.inputStream.bufferedReader().readText() } finally { v.disconnect() }
    }

    /** Bridge im WLAN suchen (über den Hue-Dienst, der die interne Adresse kennt). */
    fun finden(c: Context): String? {
        val a = JSONArray(holen("https://discovery.meethue.com", zeit = 8000))
        if (a.length() == 0) return null
        val ip = a.getJSONObject(0).getString("internalipaddress")
        p(c).edit().putString("ip", ip).apply()
        return ip
    }

    /** Einmal koppeln. Liefert true, sobald der Knopf auf der Bridge gedrückt wurde. */
    fun koppeln(c: Context): Boolean {
        val ip = ip(c) ?: finden(c) ?: return false
        val a = JSONArray(holen("http://$ip/api", "POST", JSONObject().put("devicetype", "raumklima#handy").toString(), 5000))
        val ok = a.optJSONObject(0)?.optJSONObject("success")?.optString("username") ?: return false
        p(c).edit().putString("nutzer", ok).apply()
        abfragen(c)
        return true
    }

    fun trennen(c: Context) = p(c).edit().clear().apply()

    /** Alle Lampen direkt abfragen und den Stand übernehmen. Klappt nur im WLAN zu Hause. */
    fun abfragen(c: Context): Boolean {
        val ip = ip(c) ?: return false
        val n = nutzer(c) ?: return false
        return runCatching {
            val o = JSONObject(holen("http://$ip/api/$n/lights"))
            val namen = o.keys().asSequence().toList()
            namen.forEach { k ->
                val l = o.getJSONObject(k)
                val st = l.getJSONObject("state")
                val an = st.optBoolean("on") && st.optBoolean("reachable", true)
                Haus.setzen(c, "licht", l.getString("name"), an)
            }
            p(c).edit().putLong("zuletzt", System.currentTimeMillis()).putInt("lampen", namen.size).apply()
            true
        }.getOrDefault(false)
    }
}
