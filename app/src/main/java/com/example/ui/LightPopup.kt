package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.LocalHvacTheme
import com.example.ui.theme.themedShape
import com.example.viewmodel.HvacViewModel
import kotlin.math.roundToInt

/**
 * The pop-up behind holding a light or switch tile, rebuilt 2026-10-21 to match the heat pump
 * pop-up. It used to say "POWER STATE · ACTIVE" in 8–9 sp capitals and show brightness as
 * "128 / 255", Home Assistant's internal scale. Now it is On/Off as two large buttons and, for a
 * dimmable light, brightness in percent with one-tap steps.
 */
@Composable
fun LightingControlPopup(
    entityId: String,
    name: String,
    isOn: Boolean,
    brightness: Int?,
    isLight: Boolean,
    onDismiss: () -> Unit,
    viewModel: HvacViewModel,
    onInteraction: () -> Unit = {}
) {
    val theme = LocalHvacTheme.current
    val onColor = theme.heatColor
    val activeColor = if (isOn) onColor else Color(0xFF64748B)
    // Home Assistant brightness is 0–255; people think in percent.
    val livePct = if (isOn) brightness?.let { (it * 100f / 255f).roundToInt().coerceIn(1, 100) } else 0
    val setOn: (Boolean) -> Unit = { wantOn ->
        if (wantOn != isOn) {
            if (isLight) viewModel.toggleLight(entityId, isOn, name) else viewModel.toggleSwitch(entityId, isOn, name)
        }
    }
    val setPct: (Int) -> Unit = { pct ->
        viewModel.setLightBrightness(entityId, (pct * 255f / 100f).roundToInt(), name)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .dialogInteractionReporter(onInteraction)
                .testTag("lighting_control_popup_$entityId"),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            shape = themedShape(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(44.dp).background(activeColor.copy(alpha = 0.15f), themedShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (isLight) Icons.Default.Lightbulb else Icons.Default.PowerSettingsNew,
                            contentDescription = null,
                            tint = activeColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        Text(
                            when {
                                !isOn -> "Off"
                                isLight && livePct != null -> "On · $livePct%"
                                else -> "On"
                            },
                            fontSize = 14.sp,
                            color = if (isOn) onColor else Color.White.copy(alpha = 0.55f)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(themedShape(12.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(22.dp))
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OnOffButton("On", isOn, onColor, Modifier.weight(1f).testTag("lighting_popup_on")) { setOn(true) }
                    OnOffButton("Off", !isOn, Color(0xFF94A3B8), Modifier.weight(1f).testTag("lighting_popup_off")) { setOn(false) }
                }

                if (isLight) {
                    var sliderPct by remember(livePct) { mutableStateOf((livePct ?: 0).toFloat()) }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(themedShape(14.dp))
                            .background(Color.White.copy(alpha = 0.03f))
                            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)), themedShape(14.dp))
                            .padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("Brightness", fontSize = 13.sp, color = Color.White.copy(alpha = 0.55f), modifier = Modifier.weight(1f))
                            Text("${sliderPct.roundToInt()}%", fontSize = 28.sp, fontWeight = FontWeight.Medium, color = if (isOn) onColor else Color.White)
                        }
                        Slider(
                            value = sliderPct,
                            onValueChange = { sliderPct = it },
                            onValueChangeFinished = { setPct(sliderPct.roundToInt()) },
                            valueRange = 0f..100f,
                            colors = SliderDefaults.colors(
                                thumbColor = onColor,
                                activeTrackColor = onColor,
                                inactiveTrackColor = Color.White.copy(alpha = 0.1f)
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("lighting_popup_brightness_slider")
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(25, 50, 75, 100).forEach { step ->
                                val selected = isOn && livePct == step
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .clip(themedShape(10.dp))
                                        .background(if (selected) onColor.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f))
                                        .border(
                                            BorderStroke(1.dp, if (selected) onColor.copy(alpha = 0.8f) else Color.Transparent),
                                            themedShape(10.dp)
                                        )
                                        .clickable { sliderPct = step.toFloat(); setPct(step) }
                                        .testTag("lighting_popup_step_$step"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("$step%", fontSize = 15.sp, color = if (selected) Color.White else Color.White.copy(alpha = 0.75f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnOffButton(label: String, selected: Boolean, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(themedShape(12.dp))
            .background(if (selected) color.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.04f))
            .border(
                BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) color.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.08f)),
                themedShape(12.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 16.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.7f)
        )
    }
}
