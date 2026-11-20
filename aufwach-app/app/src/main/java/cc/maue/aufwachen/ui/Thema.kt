package cc.maue.aufwachen.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Gleiche Handschrift wie die Widgets: Farben aus dem Hintergrundbild (Material You).
 * Ohne Material You (vor Android 12) die erdigen Töne der Widget-Familie.
 */
private val Dunkel = darkColorScheme(
    primary = Color(0xFFC9D6A3), onPrimary = Color(0xFF2B3410),
    primaryContainer = Color(0xFF474B41), onPrimaryContainer = Color(0xFFF2F1E6),
    secondaryContainer = Color(0xFF474B41), onSecondaryContainer = Color(0xFFF2F1E6),
    background = Color(0xFF1B1C18), surface = Color(0xFF1B1C18),
    surfaceContainer = Color(0xFF30332B), surfaceContainerHigh = Color(0xFF3A3D35), surfaceContainerHighest = Color(0xFF474B41),
    onSurface = Color(0xFFF2F1E6), onSurfaceVariant = Color(0xFFC5C8B9), outlineVariant = Color(0xFF5E6357),
)
private val Hell = lightColorScheme(
    primary = Color(0xFF55642B), onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E7AF), onPrimaryContainer = Color(0xFF181E00),
    background = Color(0xFFFBFAF0), surface = Color(0xFFFBFAF0),
)

object Ampel {
    val gruen = Color(0xFF66BB6A)
    val gelb = Color(0xFFFFD54F)
    val orange = Color(0xFFFFA726)
    val rot = Color(0xFFEF5350)
    val sonne = Color(0xFFFFB300)
}

@Composable
fun RaumklimaThema(inhalt: @Composable () -> Unit) {
    val dunkel = isSystemInDarkTheme()
    val c = LocalContext.current
    val farben = when {
        Build.VERSION.SDK_INT >= 31 && dunkel -> dynamicDarkColorScheme(c)
        Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(c)
        dunkel -> Dunkel
        else -> Hell
    }
    val t = Typography()
    MaterialTheme(
        colorScheme = farben,
        typography = t.copy(
            displayLarge = t.displayLarge.copy(fontWeight = FontWeight.Bold, fontSize = 64.sp, letterSpacing = (-1.5).sp),
            headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold),
            titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
        ),
        content = inhalt,
    )
}
