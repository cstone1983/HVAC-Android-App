package com.example.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.ui.draw.scale
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.lifecycle.LifecycleEventObserver
import com.example.model.*
import com.example.ui.theme.*
import com.example.viewmodel.HvacUiState
import com.example.viewmodel.HvacViewModel
import com.example.viewmodel.UpdateState
import kotlinx.coroutines.launch

/**
 * The dashboard's background art — glow, grid, nebula, aurora or plain — drawn behind everything.
 *
 * Lifted out of the dashboard on 2026-10-18 so the Appearance preview draws exactly the same
 * thing instead of an approximation that made the five styles look alike.
 */
fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHvacBackground(
    backgroundDesign: String,
    hvacThemeColors: HvacThemeColors,
    glowColor: Color,
    activeThemePreset: String
) {
    // 1. Core Background Styles
    when (backgroundDesign) {
        "grid" -> {
            val gridSpacing = 44.dp.toPx()
            val lineColor = hvacThemeColors.coolColor.copy(alpha = 0.05f)
            var x = 0f
            while (x < size.width) {
                drawLine(color = lineColor, start = androidx.compose.ui.geometry.Offset(x, 0f), end = androidx.compose.ui.geometry.Offset(x, size.height), strokeWidth = 1.dp.toPx())
                x += gridSpacing
            }
            var y = 0f
            while (y < size.height) {
                drawLine(color = lineColor, start = androidx.compose.ui.geometry.Offset(0f, y), end = androidx.compose.ui.geometry.Offset(size.width, y), strokeWidth = 1.dp.toPx())
                y += gridSpacing
            }
        }
        "nebula" -> {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(hvacThemeColors.heatColor.copy(alpha = 0.08f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(size.width * 0.15f, size.height * 0.25f),
                    radius = size.width * 0.7f
                ),
                radius = size.width * 0.7f,
                center = androidx.compose.ui.geometry.Offset(size.width * 0.15f, size.height * 0.25f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(hvacThemeColors.coolColor.copy(alpha = 0.1f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(size.width * 0.85f, size.height * 0.75f),
                    radius = size.width * 0.7f
                ),
                radius = size.width * 0.7f,
                center = androidx.compose.ui.geometry.Offset(size.width * 0.85f, size.height * 0.75f)
            )
        }
        "aurora" -> {
            val waveColor1 = hvacThemeColors.coolColor.copy(alpha = 0.08f)
            val waveColor2 = hvacThemeColors.ecoColor.copy(alpha = 0.08f)

            val path1 = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, size.height * 0.45f)
                cubicTo(
                    size.width * 0.25f, size.height * 0.25f,
                    size.width * 0.75f, size.height * 0.65f,
                    size.width, size.height * 0.35f
                )
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(
                path = path1,
                brush = Brush.verticalGradient(
                    colors = listOf(waveColor1, Color.Transparent),
                    startY = size.height * 0.35f,
                    endY = size.height
                )
            )

            val path2 = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, size.height * 0.25f)
                cubicTo(
                    size.width * 0.35f, size.height * 0.55f,
                    size.width * 0.65f, size.height * 0.15f,
                    size.width, size.height * 0.55f
                )
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(
                path = path2,
                brush = Brush.verticalGradient(
                    colors = listOf(waveColor2, Color.Transparent),
                    startY = size.height * 0.15f,
                    endY = size.height
                )
            )
        }
        "minimal" -> {
            // Strictly dual-tone minimal gradient, do not draw secondary items
        }
        else -> {
            // radial_glow (Default)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(glowColor, Color.Transparent),
                    center = center,
                    radius = size.width * 0.85f
                ),
                radius = size.width * 0.85f,
                center = center
            )
        }
    }

    // 2. High-Resolution Immersive Vector Art based on Active Theme Preset
    when (activeThemePreset) {
        "classic", "dynamic" -> {
            // Haven Metallic - solar crown flares & precision concentric rings
            drawCircle(
                color = hvacThemeColors.heatColor.copy(alpha = 0.04f),
                radius = size.width * 0.45f,
                center = androidx.compose.ui.geometry.Offset(size.width, 0f),
                style = Stroke(width = 1.dp.toPx())
            )
            drawCircle(
                color = hvacThemeColors.heatColor.copy(alpha = 0.02f),
                radius = size.width * 0.70f,
                center = androidx.compose.ui.geometry.Offset(size.width, 0f),
                style = Stroke(width = 2.dp.toPx())
            )
        }
        "nordic" -> {
            // Nordic Frost - deep glacier crystal vectors and coordinates
            val crystalColor = hvacThemeColors.coolColor.copy(alpha = 0.04f)
            drawCircle(
                color = crystalColor,
                radius = size.width * 0.3f,
                center = androidx.compose.ui.geometry.Offset(0f, size.height * 0.5f),
                style = Stroke(width = 1.dp.toPx())
            )
            drawLine(crystalColor, androidx.compose.ui.geometry.Offset(0f, size.height * 0.5f), androidx.compose.ui.geometry.Offset(size.width * 0.3f, size.height * 0.5f), strokeWidth = 1.dp.toPx())
            drawLine(crystalColor, androidx.compose.ui.geometry.Offset(0f, size.height * 0.2f), androidx.compose.ui.geometry.Offset(size.width * 0.15f, size.height * 0.35f), strokeWidth = 1.dp.toPx())
            drawLine(crystalColor, androidx.compose.ui.geometry.Offset(0f, size.height * 0.8f), androidx.compose.ui.geometry.Offset(size.width * 0.15f, size.height * 0.65f), strokeWidth = 1.dp.toPx())
        }
        "emerald" -> {
            // Emerald Nebula - orbital cosmic lanes and sweeping planetary systems
            val orbitColor = hvacThemeColors.ecoColor.copy(alpha = 0.04f)
            drawCircle(
                color = orbitColor,
                radius = size.width * 0.45f,
                center = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.5f),
                style = Stroke(width = 1.dp.toPx())
            )
            drawCircle(
                color = orbitColor.copy(alpha = 0.02f),
                radius = size.width * 0.65f,
                center = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.5f),
                style = Stroke(width = 1.5.dp.toPx())
            )
            drawOval(
                color = orbitColor,
                topLeft = androidx.compose.ui.geometry.Offset(-size.width * 0.1f, size.height * 0.2f),
                size = androidx.compose.ui.geometry.Size(size.width * 1.2f, size.height * 0.6f),
                style = Stroke(width = 1.3.dp.toPx())
            )
        }
        "cyber" -> {
            // Cyber Sunset - retro cyber perspective grids & wireframe horizon sun
            val gridColor = hvacThemeColors.heatColor.copy(alpha = 0.08f)
            val horizonY = size.height * 0.65f

            drawCircle(
                brush = Brush.verticalGradient(
                    colors = listOf(hvacThemeColors.heatColor.copy(alpha = 0.22f), Color.Transparent)
                ),
                radius = size.width * 0.28f,
                center = androidx.compose.ui.geometry.Offset(size.width * 0.5f, horizonY - size.width * 0.12f)
            )

            var lineY = horizonY - size.width * 0.4f
            while (lineY < horizonY) {
                drawLine(
                    color = hvacThemeColors.bgStart.copy(alpha = 0.9f),
                    start = androidx.compose.ui.geometry.Offset(0f, lineY),
                    end = androidx.compose.ui.geometry.Offset(size.width, lineY),
                    strokeWidth = 6.dp.toPx()
                )
                lineY += 16.dp.toPx()
            }

            val gridLineCount = 12
            for (i in 0..gridLineCount) {
                val startX = (size.width / gridLineCount) * i
                val vanishX = size.width / 2f
                drawLine(
                    color = gridColor.copy(alpha = 0.12f),
                    start = androidx.compose.ui.geometry.Offset(vanishX, horizonY),
                    end = androidx.compose.ui.geometry.Offset(startX, size.height),
                    strokeWidth = 1.5.dp.toPx()
                )
            }

            var hY = horizonY
            var spacing = 10.dp.toPx()
            while (hY < size.height) {
                drawLine(
                    color = gridColor.copy(alpha = 0.08f),
                    start = androidx.compose.ui.geometry.Offset(0f, hY),
                    end = androidx.compose.ui.geometry.Offset(size.width, hY),
                    strokeWidth = 1.dp.toPx()
                )
                hY += spacing
                spacing = (spacing * 1.35f).coerceAtMost(80.dp.toPx())
            }
        }
        "volcanic" -> {
            // Volcanic Obsidian - rising warm thermal sparks & dynamic magma channels
            val magmaColor = hvacThemeColors.heatColor.copy(alpha = 0.12f)
            val sparks = listOf(
                androidx.compose.ui.geometry.Offset(size.width * 0.15f, size.height * 0.4f),
                androidx.compose.ui.geometry.Offset(size.width * 0.22f, size.height * 0.75f),
                androidx.compose.ui.geometry.Offset(size.width * 0.45f, size.height * 0.25f),
                androidx.compose.ui.geometry.Offset(size.width * 0.58f, size.height * 0.85f),
                androidx.compose.ui.geometry.Offset(size.width * 0.78f, size.height * 0.35f),
                androidx.compose.ui.geometry.Offset(size.width * 0.88f, size.height * 0.7f)
            )
            sparks.forEachIndexed { idx, pos ->
                val sizeFactor = (1.5f + (idx % 3) * 1.2f)
                drawCircle(
                    color = magmaColor,
                    radius = sizeFactor.dp.toPx(),
                    center = pos
                )
                drawCircle(
                    color = magmaColor.copy(alpha = 0.025f),
                    radius = (sizeFactor * 4).dp.toPx(),
                    center = pos
                )
            }
            val path = Path().apply {
                moveTo(0f, size.height * 0.8f)
                cubicTo(size.width * 0.3f, size.height * 0.75f, size.width * 0.7f, size.height * 0.9f, size.width, size.height * 0.85f)
            }
            drawPath(path, color = magmaColor.copy(alpha = 0.03f), style = Stroke(width = 12.dp.toPx()))
        }
        "monochrome" -> {
            // Monochrome Slate - high-precision engineered aerospace layout markups
            val tickColor = Color.White.copy(alpha = 0.05f)
            val sizeL = 16.dp.toPx()
            val inset = 12.dp.toPx()

            drawLine(tickColor, androidx.compose.ui.geometry.Offset(inset, inset), androidx.compose.ui.geometry.Offset(inset + sizeL, inset), strokeWidth = 1.dp.toPx())
            drawLine(tickColor, androidx.compose.ui.geometry.Offset(inset, inset), androidx.compose.ui.geometry.Offset(inset, inset + sizeL), strokeWidth = 1.dp.toPx())
            drawLine(tickColor, androidx.compose.ui.geometry.Offset(size.width - inset, inset), androidx.compose.ui.geometry.Offset(size.width - inset - sizeL, inset), strokeWidth = 1.dp.toPx())
            drawLine(tickColor, androidx.compose.ui.geometry.Offset(size.width - inset, inset), androidx.compose.ui.geometry.Offset(size.width - inset, inset + sizeL), strokeWidth = 1.dp.toPx())

            drawCircle(
                color = tickColor,
                radius = size.width * 0.15f,
                center = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.5f),
                style = Stroke(width = 1.dp.toPx())
            )
            drawCircle(
                color = tickColor.copy(alpha = 0.02f),
                radius = size.width * 0.3f,
                center = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.5f),
                style = Stroke(width = 1.dp.toPx())
            )
        }
    }
}
