package cc.maue.aufwachen

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*
import java.time.LocalTime
import cc.maue.aufwachen.widget.KlimaWidget

class MainActivity : Activity() {
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val p = Logik.prefs(this)
        val dp = resources.displayMetrics.density
        fun abstand(v: android.view.View, oben: Int = 16) = v.apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = (oben * dp).toInt() }
        }
        fun titel(t: String) = TextView(this).apply { text = t; textSize = 15f; setTypeface(null, Typeface.BOLD) }

        val spalte = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * dp).toInt(), (40 * dp).toInt(), (20 * dp).toInt(), (20 * dp).toInt())
        }
        spalte.addView(TextView(this).apply { text = "🏠 Raumklima"; textSize = 26f; setTypeface(null, Typeface.BOLD) })
        spalte.addView(TextView(this).apply { text = "Version ${Updater.installiertName(this@MainActivity)}"; setTextColor(Color.GRAY); textSize = 12f })
        val updateKnopf = Button(this).apply { visibility = android.view.View.GONE }
        spalte.addView(updateKnopf)
        kotlin.concurrent.thread {
            val info = Updater.pruefen(this)
            if (info != null) runOnUiThread {
                updateKnopf.text = "⬆ Update auf Version ${info.version} laden"
                updateKnopf.visibility = android.view.View.VISIBLE
                updateKnopf.setOnClickListener { Updater.oeffnen(this, info) }
            }
        }

        // Raumklima: Schlüssel, Widget, Export
        spalte.addView(abstand(titel("Schlüssel für die Messdaten")))
        val schluessel = EditText(this).apply {
            setText(p.getString("schluessel", "")); hint = "TOKEN_SCHLUESSEL aus dem Chat"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        }
        spalte.addView(schluessel)
        spalte.addView(Button(this).apply {
            text = "Schlüssel speichern und Widget aktualisieren"
            setOnClickListener {
                p.edit().putString("schluessel", schluessel.text.toString().trim()).apply()
                KlimaWidget.planen(this@MainActivity); KlimaWidget.aktualisieren(this@MainActivity)
                Toast.makeText(this@MainActivity, "Gespeichert. Widget lang drücken auf dem Homescreen → Widgets → Raumklima", Toast.LENGTH_LONG).show()
            }
        })
        spalte.addView(Button(this).apply {
            text = "Verlauf als CSV exportieren"
            setOnClickListener {
                startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("text/csv").putExtra(Intent.EXTRA_TITLE, "raumklima-${java.time.LocalDate.now()}.csv"), 42)
            }
        })

        // Willkommen zu Hause
        spalte.addView(abstand(TextView(this).apply { text = "🏡 Willkommen zu Hause"; textSize = 22f; setTypeface(null, Typeface.BOLD) }, 28))
        spalte.addView(abstand(TextView(this).apply {
            text = "Ist dein Handy in der Nähe der Wohnung UND die Kamera an der Haustür sieht jemanden reinkommen, und es wird schon dunkel: Licht an Bett und Schreibtisch, Alexa begrüßt dich. Im Flur stehen bleiben löst nichts aus."
            setTextColor(Color.DKGRAY)
        }, 4))
        spalte.addView(abstand(titel("Link der Alexa-Routine „Willkommen“")))
        val wUrl = EditText(this).apply { setText(p.getString("willkommen_url", "")); hint = "https://www.virtualsmarthome.xyz/url_routine_trigger/…"; inputType = InputType.TYPE_TEXT_VARIATION_URI }
        spalte.addView(wUrl)
        val wAktiv = CheckBox(this).apply { text = "Begrüßung einschalten"; isChecked = p.getBoolean("willkommen_aktiv", false) }
        val wAbends = CheckBox(this).apply { text = "Nur wenn die Sonne schon untergeht"; isChecked = p.getBoolean("willkommen_abends", true) }
        spalte.addView(wAktiv); spalte.addView(wAbends)
        val wStatus = TextView(this).apply {
            textSize = 13f; setTextColor(Color.DKGRAY)
            text = Willkommen.zuhause(this@MainActivity)?.let { (la, lo) -> "Zuhause gesetzt · Sonnenuntergang heute ${Willkommen.sonnenuntergang(la, lo)}" } ?: "Zuhause noch nicht gesetzt"
        }
        spalte.addView(Button(this).apply {
            text = "Ich bin gerade zu Hause: Standort merken"
            setOnClickListener {
                if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 7)
                    Toast.makeText(this@MainActivity, "Standort erlauben, dann nochmal tippen", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                Willkommen.standortSetzen(this@MainActivity) { t -> runOnUiThread { wStatus.text = t; Toast.makeText(this@MainActivity, t, Toast.LENGTH_LONG).show() } }
            }
        })
        spalte.addView(wStatus)
        spalte.addView(Button(this).apply {
            text = "Kamera-Hinweise erlauben (Benachrichtigungszugriff)"
            setOnClickListener { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        })
        spalte.addView(abstand(Button(this).apply {
            text = "Begrüßung speichern"
            setOnClickListener {
                p.edit().putString("willkommen_url", wUrl.text.toString().trim()).putBoolean("willkommen_aktiv", wAktiv.isChecked)
                    .putBoolean("willkommen_abends", wAbends.isChecked).apply()
                if (wAktiv.isChecked && Build.VERSION.SDK_INT >= 29 &&
                    checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this@MainActivity, "Bitte bei Standort „Immer zulassen“ wählen, sonst merkt die App das Ankommen nicht", Toast.LENGTH_LONG).show()
                    requestPermissions(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), 8)
                }
                Willkommen.registrieren(this@MainActivity)
                Toast.makeText(this@MainActivity, "Gespeichert", Toast.LENGTH_SHORT).show()
            }
        }, 8))
        spalte.addView(Button(this).apply {
            text = "Begrüßung jetzt testen"
            setOnClickListener { Willkommen.ausloesen(this@MainActivity, "Test"); Toast.makeText(this@MainActivity, "Gesendet", Toast.LENGTH_SHORT).show() }
        })
        spalte.addView(Button(this).apply {
            text = "Ablauf testen: so tun, als käme ich gerade an"
            setOnClickListener {
                p.edit().putLong("scharf_bis", System.currentTimeMillis() + 20 * 60_000L).apply()
                Toast.makeText(this@MainActivity, "Scharf für 20 Min. Jetzt einmal vor der Kamera durch die Tür gehen.", Toast.LENGTH_LONG).show()
            }
        })

        spalte.addView(abstand(TextView(this).apply { text = "☀️ Aufwachen"; textSize = 22f; setTypeface(null, Typeface.BOLD) }, 28))
        spalte.addView(abstand(TextView(this).apply {
            text = "Startet morgens einmal deine Alexa-Routine: Licht am Bett und Schreibtisch, Musik auf den Echos."
            setTextColor(Color.DKGRAY)
        }, 4))

        spalte.addView(abstand(titel("Link der Alexa-Routine (URL Routine Trigger)")))
        val url = EditText(this).apply {
            setText(p.getString("url", "")); hint = "https://www.virtualsmarthome.xyz/url_routine_trigger/…"
            inputType = InputType.TYPE_TEXT_VARIATION_URI
        }
        spalte.addView(url)

        spalte.addView(abstand(titel("Zeitfenster am Morgen")))
        val zeiten = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val ab = EditText(this).apply { setText(p.getString("ab", "06:00")); width = (90 * dp).toInt() }
        val bis = EditText(this).apply { setText(p.getString("bis", "11:00")); width = (90 * dp).toInt() }
        zeiten.addView(TextView(this).apply { text = "von  " }); zeiten.addView(ab)
        zeiten.addView(TextView(this).apply { text = "   bis  " }); zeiten.addView(bis)
        spalte.addView(zeiten)

        val wecker = CheckBox(this).apply { text = "Starten, wenn ich den Wecker ausschalte"; isChecked = p.getBoolean("wecker", true) }
        val entsperren = CheckBox(this).apply { text = "Ohne Wecker: beim ersten Entsperren im Zeitfenster"; isChecked = p.getBoolean("entsperren", true) }
        spalte.addView(abstand(wecker)); spalte.addView(entsperren)

        spalte.addView(abstand(Button(this).apply {
            text = "Speichern"
            setOnClickListener {
                val a = runCatching { LocalTime.parse(ab.text.toString().trim()) }.getOrNull()
                val b = runCatching { LocalTime.parse(bis.text.toString().trim()) }.getOrNull()
                if (a == null || b == null || !a.isBefore(b)) {
                    Toast.makeText(this@MainActivity, "Zeiten bitte als HH:MM, z. B. 06:00 und 11:00", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                p.edit().putString("url", url.text.toString().trim()).putString("ab", a.toString()).putString("bis", b.toString())
                    .putBoolean("wecker", wecker.isChecked).putBoolean("entsperren", entsperren.isChecked).apply()
                Logik.planen(this@MainActivity)
                Toast.makeText(this@MainActivity, "Gespeichert", Toast.LENGTH_SHORT).show()
                zeigen()
            }
        }, 20))
        spalte.addView(Button(this).apply {
            text = "Jetzt testen (Licht und Musik)"
            setOnClickListener { Logik.aufwachen(this@MainActivity, "Test") { runOnUiThread { zeigen() } } }
        })

        status = TextView(this).apply { textSize = 13f; setTextColor(Color.DKGRAY) }
        spalte.addView(abstand(status, 24))
        setContentView(ScrollView(this).apply { addView(spalte) })

        berechtigungen()
        Logik.planen(this)
        Updater.planen(this)
        KlimaWidget.planen(this)
    }

    @Deprecated("einfach gehalten")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val ziel = data?.data
        if (requestCode != 42 || resultCode != RESULT_OK || ziel == null) return
        Toast.makeText(this, "Export läuft …", Toast.LENGTH_SHORT).show()
        kotlin.concurrent.thread {
            val text = try {
                val csv = Daten.exportCsv(this)
                contentResolver.openOutputStream(ziel)!!.use { it.write(csv.toByteArray()) }
                "Export fertig: ${csv.lines().size - 2} Messungen"
            } catch (e: Exception) { "Export fehlgeschlagen: ${e.message ?: e.javaClass.simpleName}" }
            runOnUiThread { Toast.makeText(this, text, Toast.LENGTH_LONG).show() }
        }
    }

    override fun onResume() { super.onResume(); zeigen() }

    private fun zeigen() {
        val p = Logik.prefs(this)
        val naechster = getSystemService(AlarmManager::class.java).nextAlarmClock?.triggerTime
        status.text = buildString {
            append(if (Logik.heuteSchon(this@MainActivity)) "Heute schon ausgelöst ✓\n" else "Heute noch nicht ausgelöst\n")
            append("Nächster Wecker: ")
            append(naechster?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime().toString().replace('T', ' ') } ?: "keiner")
            append("\n\nProtokoll:\n")
            append(p.getString("protokoll", "").orEmpty().ifEmpty { "noch leer" })
        }
    }

    private fun berechtigungen() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        val pm = getSystemService(PowerManager::class.java)
        if (!pm.isIgnoringBatteryOptimizations(packageName))
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
    }
}
