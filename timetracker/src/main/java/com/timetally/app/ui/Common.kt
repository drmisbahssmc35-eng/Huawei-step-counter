@file:OptIn(ExperimentalMaterial3Api::class)

package com.timetally.app.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timetally.app.data.toLocalDate
import com.timetally.app.data.zone
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

private val Accent = Color(0xFF3D5AFE)

@Composable
fun TallyTheme(content: @Composable () -> Unit) {
    val scheme = if (isSystemInDarkTheme()) darkColorScheme(
        primary = Color(0xFF8C9EFF),
        onPrimary = Color(0xFF0B1240),
        secondary = Color(0xFFFFD54F),
        background = Color(0xFF121418),
        surface = Color(0xFF121418),
        surfaceVariant = Color(0xFF22252C),
        surfaceContainer = Color(0xFF1A1D23),
    ) else lightColorScheme(
        primary = Accent,
        secondary = Color(0xFFFFB300),
        background = Color(0xFFF6F7FB),
        surface = Color(0xFFF6F7FB),
        surfaceVariant = Color(0xFFE7E9F1),
        surfaceContainer = Color(0xFFFFFFFF),
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

/** Current time, updated every second while on screen. */
@Composable
fun rememberNow(): Long {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1000 - value % 1000)
        }
    }
    return now
}

fun Long.asColor() = Color(this.toInt())

/** Black or white, whichever reads better on [bg]. */
fun contentOn(bg: Color): Color {
    val l = 0.299 * bg.red + 0.587 * bg.green + 0.114 * bg.blue
    return if (l > 0.62) Color(0xFF111111) else Color.White
}

@Composable
fun EmojiBadge(emoji: String, color: Color, size: Dp = 40.dp) {
    Box(
        Modifier.size(size).background(color, CircleShape),
        contentAlignment = Alignment.Center,
    ) { Text(emoji, fontSize = (size.value * 0.5f).sp) }
}

@Composable
fun ColorDot(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(4.dp)
            .size(36.dp)
            .background(color, CircleShape)
            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
            .clickable(onClick = onClick)
    )
}

/** "‹  Today  ›" header used on Records and Statistics. */
@Composable
fun PeriodSwitcher(title: String, onPrev: () -> Unit, onNext: () -> Unit, onTitle: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onPrev) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous") }
        TextButton(onClick = { onTitle?.invoke() }, enabled = onTitle != null, modifier = Modifier.weight(1f)) {
            Text(
                title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        IconButton(onClick = onNext) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next") }
    }
}

fun toast(context: Context, msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

fun pickDate(context: Context, initial: LocalDate, onPicked: (LocalDate) -> Unit) {
    DatePickerDialog(
        context, { _, y, m, d -> onPicked(LocalDate.of(y, m + 1, d)) },
        initial.year, initial.monthValue - 1, initial.dayOfMonth,
    ).show()
}

/** Pick a date, then a time, and return epoch millis. */
fun pickDateTime(context: Context, initial: Long, onPicked: (Long) -> Unit) {
    val dt = Instant.ofEpochMilli(initial).atZone(zone)
    pickDate(context, initial.toLocalDate()) { date ->
        TimePickerDialog(
            context, { _, h, min ->
                onPicked(date.atTime(LocalTime.of(h, min)).atZone(zone).toInstant().toEpochMilli())
            },
            dt.hour, dt.minute, android.text.format.DateFormat.is24HourFormat(context),
        ).show()
    }
}
