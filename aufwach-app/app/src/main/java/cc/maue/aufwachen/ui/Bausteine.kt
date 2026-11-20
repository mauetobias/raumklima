package cc.maue.aufwachen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalTime

/** Eine Karte wie im Widget: eine Stufe heller als der Grund, große Ecken. */
@Composable
fun Karte(
    icon: ImageVector,
    titel: String,
    untertitel: String? = null,
    akzent: Color = MaterialTheme.colorScheme.primary,
    rechts: (@Composable RowScope.() -> Unit)? = null,
    inhalt: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = 16.dp)) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Logo(icon, akzent)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(titel, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                    if (untertitel != null) Text(untertitel, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                rechts?.invoke(this)
            }
            Spacer(Modifier.size(8.dp))
            inhalt()
        }
    }
}

/** Logo im Kästchen, wie oben links in jedem Widget. */
@Composable
fun Logo(icon: ImageVector, farbe: Color, groesse: Int = 44) {
    Box(
        Modifier.size(groesse.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = farbe, modifier = Modifier.size((groesse * 0.55f).dp)) }
}

/** Antippbare Zeile mit Wert rechts. */
@Composable
fun Zeile(icon: ImageVector, titel: String, wert: String? = null, wertFarbe: Color? = null, beschreibung: String? = null, onClick: (() -> Unit)? = null) {
    var m = Modifier.fillMaxWidth()
    if (onClick != null) m = m.clickable(onClick = onClick)
    Row(m.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(titel, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (beschreibung != null) Text(beschreibung, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (wert != null) Text(wert, style = MaterialTheme.typography.labelLarge, color = wertFarbe ?: MaterialTheme.colorScheme.primary,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp))
        if (onClick != null) Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun SchalterZeile(icon: ImageVector, titel: String, beschreibung: String, an: Boolean, aktiv: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = aktiv) { onChange(!an) }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(titel, style = MaterialTheme.typography.bodyLarge,
                color = if (aktiv) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(beschreibung, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = an, onCheckedChange = onChange, enabled = aktiv)
    }
}

/** Kleiner Punkt mit Text: grün = gut, sonst Warnfarbe. */
@Composable
fun Status(text: String, farbe: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(farbe))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun TextDialog(titel: String, erklaerung: String, start: String, hinweis: String, geheim: Boolean = false, zahl: Boolean = false,
               onFertig: (String?) -> Unit) {
    var text by remember { mutableStateOf(start) }
    AlertDialog(
        onDismissRequest = { onFertig(null) },
        title = { Text(titel) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(erklaerung, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = text, onValueChange = { text = it }, singleLine = true, placeholder = { Text(hinweis) },
                    visualTransformation = if (geheim) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(keyboardType = when { zahl -> KeyboardType.Number; geheim -> KeyboardType.Password; else -> KeyboardType.Uri }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onFertig(text.trim()) }) { Text("Speichern") } },
        dismissButton = { TextButton(onClick = { onFertig(null) }) { Text("Abbrechen") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZeitDialog(titel: String, start: LocalTime, onFertig: (LocalTime?) -> Unit) {
    val s = rememberTimePickerState(start.hour, start.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = { onFertig(null) },
        title = { Text(titel) },
        text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state = s) } },
        confirmButton = { TextButton(onClick = { onFertig(LocalTime.of(s.hour, s.minute)) }) { Text("Übernehmen") } },
        dismissButton = { TextButton(onClick = { onFertig(null) }) { Text("Abbrechen") } },
    )
}
