package cc.maue.aufwachen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import cc.maue.aufwachen.ui.RaumklimaThema
import cc.maue.aufwachen.ui.Startseite
import cc.maue.aufwachen.widget.KlimaWidget

class MainActivity : ComponentActivity() {
    /** Zählt hoch, wenn die App wieder sichtbar wird: dann Messwerte und Einrichtung neu prüfen. */
    private val tick = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            RaumklimaThema {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { Startseite(tick.intValue) }
            }
        }
        Logik.planen(this)
        if (!BuildConfig.STORE) Updater.planen(this)
        KlimaWidget.planen(this)
    }

    override fun onResume() { super.onResume(); tick.intValue++ }
}
