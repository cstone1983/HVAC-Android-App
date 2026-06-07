package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Shape
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape

data class HvacThemePreset(
    val id: String,
    val name: String,
    val description: String,
    val accentColorHex: String,
    val coolColorHex: String,
    val offColorHex: String,
    val bgStartColorHex: String,
    val bgEndColorHex: String,
    val glowColorHex: String,
    val glowAlpha: Float
)

data class HvacThemeColors(
    val heatColor: Color,
    val coolColor: Color,
    val offColor: Color,
    val ecoColor: Color = Color(0xFF10B981),
    val boostColor: Color = Color(0xFFEF4444),
    val bgStart: Color,
    val bgEnd: Color,
    val glowColor: Color,
    val glowAlpha: Float,
    val cardCornerStyle: String = "rounded", // "sharp", "rounded", "ultra_rounded"
    val cardOpacity: Float = 0.03f
)

val LocalHvacTheme = staticCompositionLocalOf {
    HvacThemeColors(
        heatColor = Color(0xFFF59E0B),
        coolColor = Color(0xFF2196F3),
        offColor = Color(0xFF64748B),
        bgStart = Color(0xFF0F172A),
        bgEnd = Color(0xFF1E293B),
        glowColor = Color(0xFF2196F3),
        glowAlpha = 0.12f,
        cardCornerStyle = "rounded",
        cardOpacity = 0.03f
    )
}

@Composable
fun hvacCardShape(defaultDp: Int = 12): Shape {
    val theme = LocalHvacTheme.current
    return when (theme.cardCornerStyle) {
        "sharp" -> RoundedCornerShape(0.dp)
        "ultra_rounded" -> RoundedCornerShape((defaultDp * 2.2).toInt().dp)
        else -> RoundedCornerShape(defaultDp.dp)
    }
}

@Composable
fun hvacCardBgColor(baseAlpha: Float = 0.03f): Color {
    val theme = LocalHvacTheme.current
    return Color.White.copy(alpha = theme.cardOpacity)
}

@Composable
fun hvacActiveCardBgColor(baseColor: Color): Color {
    val theme = LocalHvacTheme.current
    val alpha = (theme.cardOpacity * 3f).coerceIn(0.04f, 0.40f)
    return baseColor.copy(alpha = alpha)
}

@Composable
fun hvacBorderAlphaColor(): Color {
    val theme = LocalHvacTheme.current
    val alpha = (theme.cardOpacity * 1.5f).coerceIn(0.04f, 0.40f)
    return Color.White.copy(alpha = alpha)
}

@Composable
fun hvacActiveBorderAlphaColor(baseColor: Color): Color {
    val theme = LocalHvacTheme.current
    val alpha = (theme.cardOpacity * 5.0f).coerceIn(0.15f, 0.85f)
    return baseColor.copy(alpha = alpha)
}

val HvacThemePresetsList = listOf(
    HvacThemePreset(
        id = "dynamic",
        name = "Device Native (Dynamic)",
        description = "Driven dynamically by your cloud and layout config spec",
        accentColorHex = "#F59E0B",
        coolColorHex = "#2196F3",
        offColorHex = "#64748B",
        bgStartColorHex = "#0F172A",
        bgEndColorHex = "#1E293B",
        glowColorHex = "#2196F3",
        glowAlpha = 0.12f
    ),
    HvacThemePreset(
        id = "classic",
        name = "Haven Metallic",
        description = "Comforting dark slate with bright gold and classic blue highlights",
        accentColorHex = "#F59E0B",
        coolColorHex = "#2196F3",
        offColorHex = "#64748B",
        bgStartColorHex = "#0F172A",
        bgEndColorHex = "#1E293B",
        glowColorHex = "#2196F3",
        glowAlpha = 0.12f
    ),
    HvacThemePreset(
        id = "nordic",
        name = "Nordic Frost",
        description = "Arctic glacier metallic silver and deep polar blue details",
        accentColorHex = "#E2E8F0",
        coolColorHex = "#3B82F6",
        offColorHex = "#475569",
        bgStartColorHex = "#0B131E",
        bgEndColorHex = "#182F45",
        glowColorHex = "#3B82F6",
        glowAlpha = 0.14f
    ),
    HvacThemePreset(
        id = "emerald",
        name = "Emerald Nebula",
        description = "Lush obsidian green and glowing teal-mint visual accents",
        accentColorHex = "#10B981",
        coolColorHex = "#0EA5E9",
        offColorHex = "#4B5563",
        bgStartColorHex = "#03170E",
        bgEndColorHex = "#0C3220",
        glowColorHex = "#10B981",
        glowAlpha = 0.15f
    ),
    HvacThemePreset(
        id = "cyber",
        name = "Cyber Sunset",
        description = "Striking midnight violet landscape and neon magenta power nodes",
        accentColorHex = "#EC4899",
        coolColorHex = "#06B6D4",
        offColorHex = "#6B7280",
        bgStartColorHex = "#08020C",
        bgEndColorHex = "#170220",
        glowColorHex = "#EC4899",
        glowAlpha = 0.16f
    ),
    HvacThemePreset(
        id = "volcanic",
        name = "Volcanic Obsidian",
        description = "Vibrant magma crimson sparks on an obsidian deep black plate",
        accentColorHex = "#EF4444",
        coolColorHex = "#F97316",
        offColorHex = "#4B5563",
        bgStartColorHex = "#0C0204",
        bgEndColorHex = "#1E040A",
        glowColorHex = "#EF4444",
        glowAlpha = 0.15f
    ),
    HvacThemePreset(
        id = "monochrome",
        name = "Monochrome Slate",
        description = "Ultra-premium minimalist carbon back drop with silver-gray cues",
        accentColorHex = "#F3F4F6",
        coolColorHex = "#9CA3AF",
        offColorHex = "#374151",
        bgStartColorHex = "#0A0A0C",
        bgEndColorHex = "#1C1C1F",
        glowColorHex = "#FFFFFF",
        glowAlpha = 0.08f
    )
)
