package com.progresspal.app.ui.components

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.progresspal.app.data.DAY_LABELS
import com.progresspal.app.data.Repeat
import com.progresspal.app.data.zone
import com.progresspal.app.ui.theme.NumberStyle
import com.progresspal.app.ui.theme.Pal
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** Current time in ms, re-emitted every [periodMs]. */
@Composable
fun rememberNow(periodMs: Long = 1000L): State<Long> = produceState(System.currentTimeMillis()) {
    while (true) {
        value = System.currentTimeMillis()
        delay(periodMs)
    }
}

fun toast(context: Context, msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

object Sounds {
    private val gen: ToneGenerator? by lazy { runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 70) }.getOrNull() }
    private fun play(tone: Int, ms: Int) { runCatching { gen?.startTone(tone, ms) } }
    private var appContext: Context? = null

    fun init(context: Context) { appContext = context.applicationContext }

    private fun playRaw(context: Context?, res: Int) {
        val ctx = context?.applicationContext ?: appContext ?: return
        runCatching {
            android.media.MediaPlayer.create(ctx, res)?.apply {
                setOnCompletionListener { it.release() }
                start()
            }
        }
    }

    /** The task-done chime (res/raw/task_done.mp3) — played whenever a task is completed. */
    fun done(context: Context? = null) = playRaw(context, com.progresspal.app.R.raw.task_done)

    /** The task-started sound (res/raw/task_started.mp3) — played whenever a task / timer starts. */
    fun started(context: Context? = null) = playRaw(context, com.progresspal.app.R.raw.task_started)

    fun check() = done()
    fun uncheck() = play(ToneGenerator.TONE_PROP_NACK, 100)
    fun start() = started()
    fun won() = play(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 600)
    fun lost() = play(ToneGenerator.TONE_SUP_ERROR, 500)
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    borderColor: Color = Pal.c.border,
    background: Color = Pal.c.card,
    borderWidth: Dp = 1.dp,
    corner: Dp = 14.dp,
    onClick: (() -> Unit)? = null,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(corner)
    Column(
        modifier
            .clip(shape)
            .background(background)
            .border(BorderStroke(borderWidth, borderColor), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

@Composable
fun BarProgress(percent: Int, brush: Brush, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    val anim by animateFloatAsState((percent.coerceIn(0, 100)) / 100f, tween(800), label = "bar")
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(Pal.c.muted)
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(anim)
                .clip(CircleShape)
                .background(brush)
        )
    }
}

@Composable
fun Ring(
    fraction: Float,
    color: Color,
    size: Dp,
    stroke: Dp,
    track: Color = Pal.c.muted,
    content: @Composable () -> Unit = {},
) {
    val anim by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(700), label = "ring")
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val sw = stroke.toPx()
            val d = this.size.minDimension - sw
            val tl = Offset(sw / 2, sw / 2)
            drawArc(track, 0f, 360f, false, tl, Size(d, d), style = Stroke(sw))
            drawArc(color, -90f, 360f * anim, false, tl, Size(d, d), style = Stroke(sw, cap = StrokeCap.Round))
        }
        content()
    }
}

@Composable
fun CircularProgress(percentage: Int, color: Color, size: Dp = 160.dp, stroke: Dp = 10.dp) {
    Ring(percentage / 100f, color, size, stroke) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$percentage", style = NumberStyle, fontSize = 40.sp, color = Pal.c.foreground)
                Text("%", fontSize = 16.sp, color = Pal.c.mutedForeground, modifier = Modifier.padding(bottom = 8.dp))
            }
            Text("complete", fontSize = 11.sp, color = Pal.c.mutedForeground)
        }
    }
}

@Composable
fun IconTile(icon: ImageVector, tint: Color, size: Dp = 40.dp, background: Color = tint.copy(alpha = .12f)) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(background),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(size / 2)) }
}

@Composable
fun ScreenHeader(title: String, subtitle: String?, icon: ImageVector, tint: Color, onBack: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Pal.c.foreground) }
            Spacer(Modifier.width(4.dp))
        }
        IconTile(icon, tint)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Pal.c.foreground)
            if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = Pal.c.mutedForeground)
        }
    }
}

@Composable
fun LinkCard(icon: ImageVector, tint: Color, title: String, subtitle: String, onClick: () -> Unit) {
    AppCard(
        Modifier.fillMaxWidth(),
        borderColor = tint.copy(alpha = .25f),
        background = tint.copy(alpha = .06f),
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Pal.c.foreground)
                Text(subtitle, fontSize = 12.sp, color = Pal.c.mutedForeground)
            }
            Icon(Icons.Filled.ChevronRight, null, tint = Pal.c.mutedForeground, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun Pill(text: String, bg: Color = Pal.c.muted, fg: Color = Pal.c.mutedForeground, icon: ImageVector? = null, fontSize: TextUnit = 10.sp) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(10.dp))
            Spacer(Modifier.width(3.dp))
        }
        Text(text, fontSize = fontSize, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SectionLabel(text: String, icon: ImageVector? = null, tint: Color = Pal.c.mutedForeground) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
        if (icon != null) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text.uppercase(), fontSize = 10.sp, color = Pal.c.mutedForeground, fontWeight = FontWeight.Medium, letterSpacing = 1.sp)
    }
}

@Composable
fun StatBox(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    AppCard(modifier, padding = 12.dp) {
        Text(value, style = NumberStyle, fontSize = 18.sp, color = color, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Text(label, fontSize = 12.sp, color = Pal.c.mutedForeground, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }
}

// ───────────────────────── Pickers ─────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerModal(initial: LocalDate?, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = (initial ?: LocalDate.now(zone())).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) { DatePicker(state) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerModal(initial: LocalTime?, onDismiss: () -> Unit, onPick: (LocalTime) -> Unit) {
    val t = initial ?: LocalTime.NOON
    val state = rememberTimePickerState(t.hour, t.minute, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)); onDismiss() }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { TimePicker(state) },
    )
}

/** Small outlined chip button used for "Deadline", "12:00 PM", "Daily" etc. */
@Composable
fun ChipButton(text: String, icon: ImageVector?, active: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.height(34.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp),
        border = BorderStroke(1.dp, Pal.c.border),
        shape = RoundedCornerShape(8.dp),
    ) {
        if (icon != null) {
            Icon(icon, null, modifier = Modifier.size(14.dp), tint = if (active) Pal.c.foreground else Pal.c.mutedForeground)
            Spacer(Modifier.width(6.dp))
        }
        Text(text, fontSize = 12.sp, color = if (active) Pal.c.foreground else Pal.c.mutedForeground)
    }
}

@Composable
fun RepeatSelector(value: Repeat, options: List<Repeat>, icon: ImageVector?, onChange: (Repeat) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        ChipButton(value.label, icon) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { r ->
                DropdownMenuItem(text = { Text(r.label) }, onClick = { onChange(r); open = false })
            }
        }
    }
}

@Composable
fun DayChips(selected: List<String>, onToggle: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        DAY_LABELS.forEach { day ->
            val on = day in selected
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (on) Pal.c.primary else Pal.c.muted)
                    .clickable { onToggle(day) },
                contentAlignment = Alignment.Center,
            ) {
                Text(day, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = if (on) Color.White else Pal.c.mutedForeground)
            }
        }
    }
}
