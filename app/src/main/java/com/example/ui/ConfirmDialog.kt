package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.themedShape

/**
 * "Are you sure?" for the few things that act on the whole house or open the house up: house
 * mode Off, the quick actions, opening a garage door, and replacing the layout.
 *
 * Added 2026-10-20 after the review found each of these fired on a single tap — on a wall panel
 * that people brush past, one stray touch could turn off every heat pump or open a door.
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    destructive: Boolean = true,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val accent = if (destructive) Color(0xFFEF4444) else Color(0xFF2196F3)
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(max = 440.dp)
                .clip(themedShape(16.dp))
                .background(Color(0xFF101A2B))
                .border(1.dp, Color.White.copy(alpha = 0.12f), themedShape(16.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(message, fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f))
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
            ) {
                Text(
                    "Cancel",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier
                        .clip(themedShape(10.dp))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 18.dp, vertical = 12.dp)
                )
                Text(
                    confirmLabel,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .clip(themedShape(10.dp))
                        .background(accent.copy(alpha = 0.35f))
                        .border(1.dp, accent.copy(alpha = 0.8f), themedShape(10.dp))
                        .clickable { onConfirm(); onDismiss() }
                        .padding(horizontal = 18.dp, vertical = 12.dp)
                )
            }
        }
    }
}
