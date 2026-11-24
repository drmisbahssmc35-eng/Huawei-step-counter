package com.progresspal.app.reading.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.progresspal.app.reading.data.Book
import com.progresspal.app.reading.ui.DayStat
import com.progresspal.app.reading.ui.theme.LocalChartColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Pages-read-per-day bar chart. Single series, so no legend: the card title names it.
 * Tap a bar to see its exact value; the dashed line is the daily goal.
 */
@Composable
fun PagesBarChart(
    data: List<DayStat>,
    goal: Int,
    modifier: Modifier = Modifier,
    height: Dp = 190.dp,
) {
    val colors = LocalChartColors.current
    val ink = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val measurer = rememberTextMeasurer()
    val today = LocalDate.now()
    var selected by remember(data.size) { mutableStateOf(data.indexOfLast { it.date == today }.takeIf { data.size <= 7 } ?: -1) }
    val anim = remember { Animatable(0f) }
    LaunchedEffect(data) {
        anim.snapTo(0f)
        anim.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
    }
    val dense = data.size > 10
    val dayFmt = remember { DateTimeFormatter.ofPattern("d\nMMM", Locale.getDefault()) }
    val dateFmt = remember { DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()) }
    val labelStyle = TextStyle(fontSize = 11.sp, color = muted)
    val valueStyle = TextStyle(fontSize = 11.sp, color = ink, fontWeight = FontWeight.SemiBold)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(data) {
                detectTapGestures { pos ->
                    if (data.isEmpty()) return@detectTapGestures
                    val slot = size.width / data.size
                    val i = (pos.x / slot).toInt().coerceIn(0, data.lastIndex)
                    selected = if (selected == i) -1 else i
                }
            }
    ) {
        if (data.isEmpty()) return@Canvas
        val bottomPad = (if (dense) 22 else 36).dp.toPx()
        val topPad = 20.dp.toPx()
        val chartH = size.height - bottomPad - topPad
        val maxV = maxOf(data.maxOf { it.pages }, goal, 1) * 1.1f
        val slot = size.width / data.size
        val barW = (slot * if (dense) 0.62f else 0.42f).coerceAtMost(28.dp.toPx())
        val baseY = topPad + chartH

        // recessive grid: baseline + midline
        drawLine(colors.grid, Offset(0f, baseY), Offset(size.width, baseY), 1.dp.toPx())
        drawLine(colors.grid, Offset(0f, topPad + chartH / 2), Offset(size.width, topPad + chartH / 2), 1.dp.toPx())

        data.forEachIndexed { i, d ->
            val cx = slot * i + slot / 2
            val h = chartH * (d.pages / maxV) * anim.value
            val isSel = i == selected
            val color = when {
                selected >= 0 && !isSel -> colors.barMuted
                else -> colors.bar
            }
            if (d.pages > 0) {
                val r = 4.dp.toPx().coerceAtMost(barW / 2)
                val top = baseY - h.coerceAtLeast(r)
                // rounded data-end on top, square at the baseline
                val path = Path().apply {
                    addRoundRect(
                        androidx.compose.ui.geometry.RoundRect(
                            left = cx - barW / 2, top = top, right = cx + barW / 2, bottom = baseY,
                            topLeftCornerRadius = CornerRadius(r), topRightCornerRadius = CornerRadius(r),
                            bottomLeftCornerRadius = CornerRadius.Zero, bottomRightCornerRadius = CornerRadius.Zero,
                        )
                    )
                }
                drawPath(path, color)
            }
            // value label: every bar in the 7-day view, only the selected bar when dense
            if ((!dense && d.pages > 0) || isSel) {
                val t = measurer.measure(d.pages.toString(), valueStyle)
                drawText(t, topLeft = Offset(cx - t.size.width / 2, baseY - h - t.size.height - 2.dp.toPx()))
            }
            // x labels
            val showX = if (dense) (data.lastIndex - i) % 6 == 0 else true
            if (showX) {
                // calendar dates: "24 Nov" (stacked as 24 / Nov in the 7-day view so it fits)
                val label = if (dense) d.date.format(dateFmt) else d.date.format(dayFmt)
                val base = labelStyle.copy(textAlign = TextAlign.Center)
                val style = if (d.date == today) base.copy(color = ink, fontWeight = FontWeight.Bold) else base
                val t = measurer.measure(label, style)
                val x = (cx - t.size.width / 2).coerceIn(0f, size.width - t.size.width)
                drawText(t, topLeft = Offset(x, baseY + 5.dp.toPx()))
            }
        }

        // goal line
        val gy = baseY - chartH * (goal / maxV) * anim.value
        drawLine(
            colors.goal, Offset(0f, gy), Offset(size.width, gy), 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 5.dp.toPx()))
        )
        val gt = measurer.measure("Goal $goal", labelStyle.copy(color = ink, fontWeight = FontWeight.Medium))
        drawText(gt, topLeft = Offset(size.width - gt.size.width, (gy - gt.size.height - 2.dp.toPx()).coerceAtLeast(0f)))

        if (selected in data.indices && dense) {
            val d = data[selected]
            val t = measurer.measure("${d.date.format(dateFmt)}: ${d.pages} pages", valueStyle)
            drawText(t, topLeft = Offset(0f, 0f))
        }
    }
}

/** Circular progress ring used in the dashboard hero. */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    stroke: Dp = 12.dp,
    track: Color,
    fill: Color,
    content: @Composable () -> Unit,
) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(900, easing = FastOutSlowInEasing), label = "ring")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val s = stroke.toPx()
            val arcSize = Size(this.size.width - s, this.size.height - s)
            val tl = Offset(s / 2, s / 2)
            drawArc(track, -90f, 360f, false, tl, arcSize, style = Stroke(s, cap = StrokeCap.Round))
            if (p > 0f) drawArc(fill, -90f, 360f * p, false, tl, arcSize, style = Stroke(s, cap = StrokeCap.Round))
        }
        content()
    }
}

/** Animated horizontal progress bar. */
@Composable
fun ProgressBar(progress: Float, modifier: Modifier = Modifier, height: Dp = 10.dp, color: Color = LocalChartColors.current.bar) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(800, easing = FastOutSlowInEasing), label = "bar")
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            Modifier
                .fillMaxWidth(p)
                .height(height)
                .clip(RoundedCornerShape(height / 2))
                .background(color)
        )
    }
}

/** Per-book progress: percentage = pages read / total pages. */
@Composable
fun BookProgressChart(books: List<Book>, modifier: Modifier = Modifier, onClick: (Book) -> Unit = {}) {
    val colors = LocalChartColors.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        books.forEach { b ->
            Column(Modifier.fillMaxWidth().pointerInput(b.id) { detectTapGestures { onClick(b) } }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        b.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("${b.percent}%", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(6.dp))
                ProgressBar(b.progress, color = if (b.percent >= 100) colors.good else colors.bar)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${b.pagesRead} / ${b.totalPages} pages",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
