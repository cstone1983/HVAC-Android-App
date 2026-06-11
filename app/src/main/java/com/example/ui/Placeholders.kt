package com.example.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.example.ui.theme.HvacThemeColors

@Composable
fun SolarDataPlaceholder(theme: HvacThemeColors) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.SolarPower, contentDescription = null, tint = theme.ecoColor, modifier = Modifier.size(48.dp))
                Text("INTEGRATION PENDING", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = theme.coolColor)
                Text("Solar Array & Inverter Telemetry", fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f))
            }
        }
        
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(100.dp)
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                 Text("CURRENT PRODUCTION", fontSize = 9.sp, color = Color.White.copy(alpha = 0.4f))
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(100.dp)
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                 Text("GRID STATUS", fontSize = 9.sp, color = Color.White.copy(alpha = 0.4f))
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("HISTORICAL TRENDS (COMING SOON)", fontSize = 10.sp, color = Color.White.copy(alpha = 0.4f))
        }
    }
}

@Composable
fun PoolDataPlaceholder(theme: HvacThemeColors) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Pool, contentDescription = null, tint = Color(0xFF0EA5E9), modifier = Modifier.size(48.dp))
                Text("INTEGRATION PENDING", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = theme.coolColor)
                Text("Pool Automation & Chemistry", fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f))
            }
        }
        
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(100.dp)
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                 Text("PH & CHLORINE", fontSize = 9.sp, color = Color.White.copy(alpha = 0.4f))
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(100.dp)
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                 Text("PUMP & HEATER STATUS", fontSize = 9.sp, color = Color.White.copy(alpha = 0.4f))
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("TEMPERATURE HISTORICAL TRENDS", fontSize = 10.sp, color = Color.White.copy(alpha = 0.4f))
        }
    }
}
