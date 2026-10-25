package com.example.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import com.example.model.PendingSetpoint
import com.example.model.SetpointStatus

/**
 * Temperature changes in flight, by zone key, provided at the dashboard root so every zone card
 * can show its line without each one being handed the view model.
 */
val LocalPendingSetpoints = staticCompositionLocalOf<Map<String, PendingSetpoint>> { emptyMap() }

private val ConfirmedGreen = Color(0xFF10B981)
private val FailedRed = Color(0xFFEF4444)

/**
 * The line above SET while a temperature change is on its way (Chris, 2026-10-25):
 * "Setting to 72°" until the head(s) report it, "Updating 72°" when the zone is off and only the
 * schedule is changing, then green "Set confirmed" for a few seconds, or red "Update failed" if
 * nothing confirmed within a minute. Draws nothing otherwise.
 */
@Composable
fun SetpointStatusLine(zoneKey: String, fontSize: TextUnit, modifier: Modifier = Modifier) {
    val pending = LocalPendingSetpoints.current[zoneKey] ?: return
    val degrees = if (pending.target % 1.0 == 0.0) "${pending.target.toInt()}°" else "${pending.target}°"
    val (text, color) = when (pending.status) {
        SetpointStatus.SETTING -> "Setting to $degrees" to Color.White
        SetpointStatus.UPDATING -> "Updating $degrees" to Color.White
        SetpointStatus.CONFIRMED -> "Set confirmed" to ConfirmedGreen
        SetpointStatus.FAILED -> "Update failed" to FailedRed
    }
    Text(
        text = text,
        fontSize = fontSize,
        fontWeight = FontWeight.SemiBold,
        color = color,
        maxLines = 1,
        softWrap = false,
        modifier = modifier.testTag("setpoint_status_$zoneKey")
    )
}
