package cc.maue.aufwachen

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDate
import java.time.LocalTime
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.concurrent.thread

/**
 * Meldet dem Raumklima-Boten, wann du aufgestanden bist (der Roborock startet erst eine Stunde danach).
 * Weg: verschlüsselt (Fernet, derselbe Schlüssel wie die Messdaten) über ntfy.sh auf einem geheimen Kanal,
 * der Bote holt es dort alle 5 Minuten ab. Ohne Konto, ohne Token; ohne den Schlüssel kann niemand etwas damit anfangen.
 */
object Signal {
    /** Geheimer Kanal, abgeleitet aus dem Schlüssel (gleiche Rechnung wie aufstehen.py). */
    fun kanal(schluessel: String): String {
        val h = MessageDigest.getInstance("SHA-256").digest(("aufstehen:" + schluessel.trim()).toByteArray())
        return "raumklima-" + h.take(12).joinToString("") { "%02x".format(it) }
    }

    fun aufgestanden(c: Context, grund: String) {
        val key = Daten.schluessel(c)
        if (key.isEmpty() || Daten.demo(c)) return
        val jetzt = LocalTime.now()
        val text = JSONObject().put("tag", LocalDate.now().toString()).put("zeit", "%02d:%02d".format(jetzt.hour, jetzt.minute)).put("grund", grund).toString()
        thread {
            val ergebnis = runCatching {
                val v = URL("https://ntfy.sh/" + kanal(key)).openConnection() as HttpURLConnection
                v.requestMethod = "POST"; v.doOutput = true
                v.connectTimeout = 15000; v.readTimeout = 15000
                v.setRequestProperty("Cache", "yes")
                v.outputStream.use { it.write(verschluesseln(key, text.toByteArray()).toByteArray()) }
                val code = v.responseCode; v.disconnect()
                if (code in 200..299) "Aufstehzeit an den Boten gemeldet" else "Aufstehzeit melden: Antwort $code"
            }.getOrElse { "Aufstehzeit melden: ${it.javaClass.simpleName}" }
            Logik.protokoll(c.applicationContext, ergebnis)
        }
    }

    /** Fernet-Token: 0x80 | Zeit | IV | AES-128-CBC | HMAC-SHA256, Base64 (URL-sicher). */
    fun verschluesseln(schluessel: String, daten: ByteArray): String {
        val key = Base64.decode(schluessel, Base64.URL_SAFE)
        require(key.size == 32) { "Schlüssel ungültig" }
        val iv = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key.copyOfRange(16, 32), "AES"), IvParameterSpec(iv))
        val inhalt = cipher.doFinal(daten)
        val kopf = ByteBuffer.allocate(9 + 16 + inhalt.size).put(0x80.toByte()).putLong(System.currentTimeMillis() / 1000).put(iv).put(inhalt).array()
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key.copyOfRange(0, 16), "HmacSHA256")) }.doFinal(kopf)
        return Base64.encodeToString(kopf + mac, Base64.URL_SAFE or Base64.NO_WRAP)
    }
}
