package com.progresspal.app.reading.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.progresspal.app.reading.data.BookStatus
import com.progresspal.app.reading.ui.ReadingStats
import com.progresspal.app.reading.ui.components.BookProgressChart
import com.progresspal.app.reading.ui.components.EmptyState
import com.progresspal.app.reading.ui.components.PagesBarChart
import com.progresspal.app.reading.ui.components.SectionCard
import com.progresspal.app.reading.ui.components.StatTile
import com.progresspal.app.reading.ui.theme.LocalChartColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.progresspal.app.reading.ui.DayLog
import com.progresspal.app.reading.ui.components.ProgressBar

@Composable
fun StatsScreen(stats: ReadingStats, contentPadding: PaddingValues, onGoal: (Int) -> Unit, onOpenBook: (Long) -> Unit) {
    var range by rememberSaveable { mutableStateOf(0) }
    var editGoal by remember { mutableStateOf(false) }
    var showAllLog by rememberSaveable { mutableStateOf(false) }
    val c = LocalChartColors.current
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, contentPadding.calculateTopPadding() + 8.dp, 16.dp, contentPadding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(Icons.Rounded.Insights, "${stats.totalPagesLogged}", "Pages logged", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                StatTile(Icons.Rounded.Speed, String.format("%.1f", if (stats.activeDays > 0) stats.totalPagesLogged.toFloat() / stats.activeDays else 0f), "Avg / reading day", MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(Icons.Rounded.LocalFireDepartment, "${stats.streak}", "Current streak", c.goal, Modifier.weight(1f))
                StatTile(Icons.Rounded.EmojiEvents, "${stats.bestStreak}", "Best streak", c.goal, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(Icons.Rounded.CalendarMonth, "${stats.activeDays}", "Reading days", c.good, Modifier.weight(1f))
                StatTile(
                    Icons.Rounded.Flag, "${stats.dailyGoal}", "Daily goal (tap)", MaterialTheme.colorScheme.tertiary,
                    Modifier.weight(1f).clickable { editGoal = true }
                )
            }
        }
        item {
            val data = if (range == 0) stats.week else stats.month
            SectionCard(
                "Pages read per day",
                subtitle = "${dateRange(data)} · ${data.sumOf { it.pages }} pages" +
                    (stats.bestDay?.let { " · best ${it.pages} on ${it.date.format(DateTimeFormatter.ofPattern("d MMM"))}" } ?: "")
            ) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf("7 days", "30 days").forEachIndexed { i, label ->
                        SegmentedButton(
                            selected = range == i, onClick = { range = i },
                            shape = SegmentedButtonDefaults.itemShape(i, 2)
                        ) { Text(label) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                PagesBarChart(data, stats.dailyGoal, height = 220.dp)
            }
        }
        item {
            SectionCard(
                "Date-wise reading log",
                subtitle = if (stats.history.isEmpty()) "Pages you read on each date" else
                    "${stats.history.size} reading date${if (stats.history.size == 1) "" else "s"} · newest first"
            ) {
                if (stats.history.isEmpty()) {
                    EmptyState(Icons.Rounded.CalendarMonth, "No reading logged yet", "Each date you read will appear here with its page count.")
                } else {
                    val shown = if (showAllLog) stats.history else stats.history.take(10)
                    shown.forEachIndexed { i, log ->
                        if (i > 0) HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        DateLogRow(log, stats.dailyGoal)
                    }
                    if (stats.history.size > 10) {
                        TextButton(onClick = { showAllLog = !showAllLog }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (showAllLog) "Show less" else "Show all ${stats.history.size} dates")
                        }
                    }
                }
            }
        }
        item {
            val tracked = stats.books.filter { it.status != BookStatus.WISHLIST }
            SectionCard(
                "Progress by book",
                subtitle = "${stats.pagesRead} of ${stats.pagesTotal} pages · ${(stats.overallProgress * 100).toInt()}% overall"
            ) {
                if (tracked.isEmpty()) EmptyState(Icons.Rounded.Insights, "No data yet", "Start reading a book to see progress here.")
                else BookProgressChart(tracked.sortedByDescending { it.progress }) { onOpenBook(it.id) }
            }
        }
    }

    if (editGoal) {
        var text by remember { mutableStateOf(stats.dailyGoal.toString()) }
        AlertDialog(
            onDismissRequest = { editGoal = false },
            title = { Text("Daily reading goal") },
            text = {
                Column {
                    Text("How many pages do you want to read each day?")
                    OutlinedTextField(
                        text, { text = it.filter(Char::isDigit).take(4) },
                        singleLine = true, label = { Text("Pages per day") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { text.toIntOrNull()?.let(onGoal); editGoal = false }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editGoal = false }) { Text("Cancel") } }
        )
    }
}


@Composable
private fun DateLogRow(log: DayLog, goal: Int) {
    val c = LocalChartColors.current
    val isToday = log.date == LocalDate.now()
    Row(verticalAlignment = Alignment.CenterVertically) {
        // calendar-style date block
        Column(
            Modifier
                .width(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer)
                .padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val fg = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer
            Text(log.date.format(DateTimeFormatter.ofPattern("MMM")).uppercase(), style = MaterialTheme.typography.labelSmall, color = fg)
            Text("${log.date.dayOfMonth}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = fg)
            Text("${log.date.year}", style = MaterialTheme.typography.labelSmall, color = fg)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    log.date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + if (isToday) " · Today" else "",
                    style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f)
                )
                if (log.pages >= goal) {
                    Icon(Icons.Rounded.CheckCircle, "Goal met", tint = c.good, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text("${log.pages} pages", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
            ProgressBar(log.pages.toFloat() / goal.coerceAtLeast(1), height = 6.dp, color = if (log.pages >= goal) c.good else c.bar)
            Spacer(Modifier.height(6.dp))
            log.books.forEach { (title, pages) ->
                Row {
                    Text(
                        title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                    )
                    Text("$pages pages", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
