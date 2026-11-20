package cc.maue.aufwachen

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Holt die verschlüsselten Messdaten des Raumklima-Boten von GitHub und entschlüsselt sie (Fernet). */
object Daten {
    private const val BASIS = "https://raw.githubusercontent.com/mauetobias/raumklima/daten/"

    fun schluessel(c: Context) = Logik.prefs(c).getString("schluessel", "")!!.trim()

    /** Mit dem Schlüssel „demo“ zeigt die App Beispielwerte (für die Prüfung im Play Store und zum Ausprobieren). */
    fun demo(c: Context) = schluessel(c).equals("demo", ignoreCase = true)

    fun status(c: Context): JSONObject {
        if (demo(c)) return JSONObject().put("zeit", java.time.LocalDateTime.now().withSecond(0).toString())
            .put("feuchte", 54).put("co2", 742).put("temperatur", 21.4).put("laerm", 36)
            .put("entfeuchter_an", false).put("entfeuchter_tank_voll", false).put("nachtruhe", false)
            .put("roborock_akku", 100).put("roborock_zustand", "voll geladen").put("roborock_heute", false)
            .put("roborock_letzte_runde", java.time.LocalDateTime.now().minusDays(1).withHour(11).withMinute(12).withSecond(0).withNano(0).toString())
            .put("roborock_letzte_dauer", 52).put("roborock_letzte_flaeche", 38.5).put("roborock", "außerhalb des Zeitfensters")
        val s = JSONObject(laden(c, "status.enc"))
        runCatching { live(s) }
        return s
    }

    /**
     * Holt die Werte des Home Coach direkt bei Netatmo, mit dem Zugangsschlüssel, den der Bote
     * verschlüsselt im Status mitliefert. So zeigt Neuladen sofort den neuesten Messwert.
     */
    private fun live(s: JSONObject) {
        val token = s.optString("netatmo_token", "")
        if (token.isEmpty() || s.optLong("netatmo_bis", 0L) * 1000 < System.currentTimeMillis() + 60_000) return
        val v = URL("https://api.netatmo.com/api/gethomecoachsdata").openConnection() as HttpURLConnection
        v.setRequestProperty("Authorization", "Bearer $token")
        v.connectTimeout = 10000; v.readTimeout = 15000
        if (v.responseCode != 200) { v.disconnect(); return }
        val o = JSONObject(v.inputStream.bufferedReader().readText()); v.disconnect()
        val d = o.getJSONObject("body").getJSONArray("devices").getJSONObject(0).getJSONObject("dashboard_data")
        if (d.has("Humidity")) s.put("feuchte", d.getInt("Humidity"))
        if (d.has("CO2")) s.put("co2", d.getInt("CO2"))
        if (d.has("Temperature")) s.put("temperatur", d.getDouble("Temperature"))
        if (d.has("Noise")) s.put("laerm", d.getInt("Noise"))
        if (d.has("time_utc")) s.put("zeit", java.time.LocalDateTime.ofInstant(
            java.time.Instant.ofEpochSecond(d.getLong("time_utc")), java.time.ZoneId.systemDefault()).withNano(0).toString())
        s.put("live", true)
    }

    /** Alle Monate als eine CSV (Kopfzeile nur einmal). */
    fun exportCsv(c: Context): String {
        if (demo(c)) return "zeit,feuchte,co2,temperatur\n" + (0 until 48).joinToString("\n") {
            "${java.time.LocalDateTime.now().minusMinutes(30L * it).withSecond(0).withNano(0)},${52 + it % 5},${650 + it * 7 % 300},21.${it % 9}"
        } + "\n"
        val monate = status(c).optJSONArray("monate") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until monate.length()) {
            val zeilen = laden(c, "verlauf-${monate.getString(i)}.csv.enc").trimEnd().lines()
            if (sb.isEmpty()) sb.appendLine(zeilen.first())
            zeilen.drop(1).forEach { sb.appendLine(it) }
        }
        return sb.toString()
    }

    private fun laden(c: Context, name: String): String {
        val v = URL(BASIS + name + "?t=" + System.currentTimeMillis() / 60000).openConnection() as HttpURLConnection
        v.connectTimeout = 15000; v.readTimeout = 30000
        val token = v.inputStream.use { it.readBytes() }
        v.disconnect()
        return String(fernet(schluessel(c), String(token).trim()), Charsets.UTF_8)
    }

    /** Fernet: Version | Zeit | IV | Daten | HMAC, Schlüssel = 16 Byte Signatur + 16 Byte AES. */
    fun fernet(schluessel: String, token: String): ByteArray {
        val key = Base64.decode(schluessel, Base64.URL_SAFE)
        require(key.size == 32) { "Schlüssel ungültig" }
        val daten = Base64.decode(token, Base64.URL_SAFE)
        val ohneMac = daten.copyOfRange(0, daten.size - 32)
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key.copyOfRange(0, 16), "HmacSHA256")) }
        require(MessageDigest.isEqual(mac.doFinal(ohneMac), daten.copyOfRange(daten.size - 32, daten.size))) { "Schlüssel passt nicht" }
        val iv = daten.copyOfRange(9, 25)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key.copyOfRange(16, 32), "AES"), IvParameterSpec(iv))
        return cipher.doFinal(ohneMac.copyOfRange(25, ohneMac.size))
    }

    /** Eine Zeile aus dem Verlauf des Boten. */
    data class Messung(
        val zeit: java.time.LocalDateTime, val feuchte: Int?, val co2: Int?, val temperatur: Double?, val laerm: Int?,
        val entfeuchter: Boolean, val aktion: String, val roborock: String,
    )

    @Volatile private var zwischen: Pair<Long, List<Messung>>? = null

    /** Messungen der letzten [tage] Tage (aktueller und voriger Monat), älteste zuerst. */
    fun verlauf(c: Context, tage: Int): List<Messung> {
        val ab = java.time.LocalDateTime.now().minusDays(tage.toLong())
        zwischen?.let { (t, l) -> if (System.currentTimeMillis() - t < 4 * 60_000 && l.isNotEmpty()) return l.filter { it.zeit.isAfter(ab) } }
        val alle = if (demo(c)) demoVerlauf() else {
            val monate = (JSONObject(laden(c, "status.enc")).optJSONArray("monate") ?: org.json.JSONArray())
            val liste = (0 until monate.length()).map { monate.getString(it) }.takeLast(2)
            liste.flatMap { csvLesen(laden(c, "verlauf-$it.csv.enc")) }
        }.sortedBy { it.zeit }
        zwischen = System.currentTimeMillis() to alle
        return alle.filter { it.zeit.isAfter(ab) }
    }

    private fun csvLesen(text: String): List<Messung> {
        val zeilen = text.trim().lines()
        if (zeilen.size < 2) return emptyList()
        val kopf = zeilen.first().split(',')
        fun idx(n: String) = kopf.indexOf(n)
        val iZ = idx("zeit"); val iF = idx("feuchte"); val iC = idx("co2"); val iT = idx("temperatur"); val iL = idx("laerm")
        val iE = idx("entfeuchter_an"); val iA = idx("entfeuchter_aktion"); val iR = idx("roborock")
        return zeilen.drop(1).mapNotNull { z ->
            val w = felder(z)
            fun f(i: Int) = if (i in w.indices) w[i].trim() else ""
            val zeit = runCatching { java.time.OffsetDateTime.parse(f(iZ)).atZoneSameInstant(java.time.ZoneId.systemDefault()).toLocalDateTime() }
                .recoverCatching { java.time.LocalDateTime.parse(f(iZ)) }.getOrNull() ?: return@mapNotNull null
            Messung(zeit, f(iF).toDoubleOrNull()?.toInt(), f(iC).toDoubleOrNull()?.toInt(), f(iT).toDoubleOrNull(), f(iL).toDoubleOrNull()?.toInt(),
                f(iE) == "1" || f(iE).equals("true", true), f(iA), f(iR))
        }
    }

    /** CSV-Zeile in Felder, Anführungszeichen wie beim Python-csv-Modul. */
    private fun felder(z: String): List<String> {
        val aus = mutableListOf<String>(); val sb = StringBuilder(); var inQ = false; var i = 0
        while (i < z.length) {
            val ch = z[i]
            when {
                inQ && ch == '"' && i + 1 < z.length && z[i + 1] == '"' -> { sb.append('"'); i++ }
                ch == '"' -> inQ = !inQ
                ch == ',' && !inQ -> { aus.add(sb.toString()); sb.clear() }
                else -> sb.append(ch)
            }
            i++
        }
        aus.add(sb.toString()); return aus
    }

    private fun demoVerlauf(): List<Messung> {
        val jetzt = java.time.LocalDateTime.now().withSecond(0).withNano(0)
        return (0 until 7 * 288).map { i ->
            val t = jetzt.minusMinutes(5L * i)
            val h = t.hour + t.minute / 60.0
            val feuchte = 53 + 4 * kotlin.math.sin((h - 6) / 24 * 2 * Math.PI) + (if (h in 7.0..7.5) 6 else 0) + (i % 7) / 3.0
            val co2 = 600 + 350 * kotlin.math.max(0.0, kotlin.math.sin((h - 2) / 24 * 2 * Math.PI)) + (i % 11) * 4
            Messung(t, feuchte.toInt(), co2.toInt(), 20.5 + 1.2 * kotlin.math.sin((h - 9) / 24 * 2 * Math.PI), 35 + (i % 9),
                feuchte > 57, if (feuchte > 57) "läuft" else "aus", "")
        }
    }
}
