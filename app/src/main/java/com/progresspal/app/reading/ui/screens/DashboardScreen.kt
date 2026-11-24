package com.progresspal.app.reading.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.progresspal.app.reading.data.Book
import com.progresspal.app.reading.ui.ReadingStats
import com.progresspal.app.reading.ui.components.BookCover
import com.progresspal.app.reading.ui.components.BookProgressChart
import com.progresspal.app.reading.ui.components.EmptyState
import com.progresspal.app.reading.ui.components.PagesBarChart
import com.progresspal.app.reading.ui.components.ProgressBar
import com.progresspal.app.reading.ui.components.ProgressRing
import com.progresspal.app.reading.ui.components.SectionCard
import com.progresspal.app.reading.ui.components.StatTile
import com.progresspal.app.reading.ui.theme.LocalChartColors
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

@Composable
fun DashboardScreen(stats: ReadingStats, contentPadding: PaddingValues, onOpenBook: (Long) -> Unit, onAddBook: () -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 88.dp
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { HeroCard(stats) }
        item { StatRow(stats) }

        val current = stats.currentBook
        if (current != null) {
            item { ContinueCard(current, stats, onOpenBook) }
        } else if (stats.loaded && stats.books.isEmpty()) {
            item {
                SectionCard("Welcome!") {
                    EmptyState(
                        Icons.Rounded.AutoStories,
                        "Your library is empty",
                        "Add your first PDF book to start tracking your reading progress."
                    )
                    Button(onClick = onAddBook, modifier = Modifier.fillMaxWidth()) { Text("Add a PDF book") }
                }
            }
        }

        item {
            SectionCard(
                "Pages read per day",
                subtitle = "${dateRange(stats.week)} · ${stats.weekTotal} pages · tap a bar"
            ) { PagesBarChart(stats.week, stats.dailyGoal) }
        }

        val tracked = stats.books.filter { it.status != com.progresspal.app.reading.data.BookStatus.WISHLIST }
        if (tracked.isNotEmpty()) {
            item {
                SectionCard("Book progress", subtitle = "Pages read ÷ total pages") {
                    BookProgressChart(tracked.take(6)) { onOpenBook(it.id) }
                }
            }
        }
    }
}

@Composable
private fun HeroCard(stats: ReadingStats) {
    val c = LocalChartColors.current
    val hour = LocalTime.now().hour
    val greeting = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Happy late-night reading"
    }
    val goalP = if (stats.dailyGoal > 0) stats.today.toFloat() / stats.dailyGoal else 0f
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(c.heroStart, c.heroEnd)))
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(greeting, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
                Text(
                    LocalDate.now().format(DateTimeFormatter.ofPattern("EEE, d MMM")),
                    color = Color.White, style = MaterialTheme.typography.titleLarge
                )
                Spacer(Modifier.height(14.dp))
                Text("Today", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
                Text(
                    "${stats.today} / ${stats.dailyGoal} pages",
                    color = Color.White, style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(6.dp))
                ProgressBar(goalP, height = 8.dp, color = Color.White, modifier = Modifier.width(150.dp))
                Spacer(Modifier.height(6.dp))
                Text(
                    if (stats.today >= stats.dailyGoal) "Daily goal reached 🎉"
                    else "${stats.dailyGoal - stats.today} pages to reach your goal",
                    color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall
                )
            }
            ProgressRing(
                stats.overallProgress, size = 118.dp, stroke = 11.dp,
                track = Color.White.copy(alpha = 0.22f), fill = Color.White
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${(stats.overallProgress * 100).toInt()}%", color = Color.White,
                        style = MaterialTheme.typography.headlineSmall)
                    Text("overall", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun StatRow(stats: ReadingStats) {
    val c = LocalChartColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile(Icons.AutoMirrored.Rounded.MenuBook, "${stats.reading.size}", "Ongoing", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
        StatTile(Icons.Rounded.CheckCircle, "${stats.completed.size}", "Completed", c.good, Modifier.weight(1f))
        StatTile(Icons.Rounded.LocalFireDepartment, "${stats.streak}", "Day streak", c.goal, Modifier.weight(1f))
    }
}

@Composable
private fun ContinueCard(book: Book, stats: ReadingStats, onOpenBook: (Long) -> Unit) {
    val remaining = (book.totalPages - book.pagesRead).coerceAtLeast(0)
    val pace = if (stats.activeDays >= 3) stats.totalPagesLogged.toFloat() / stats.activeDays else stats.dailyGoal.toFloat()
    val days = if (pace > 0) ceil(remaining / pace).toInt() else 0
    SectionCard("Continue reading") {
        Row(Modifier.clickable { onOpenBook(book.id) }, verticalAlignment = Alignment.CenterVertically) {
            BookCover(book, width = 72.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(book.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (book.author.isNotBlank()) Text(book.author, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgressBar(book.progress, Modifier.weight(1f), height = 8.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("${book.percent}%", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Page ${book.lastPage + 1} of ${book.totalPages}" +
                        if (remaining > 0) " · ~$days day${if (days == 1) "" else "s"} left" else "",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { onOpenBook(book.id) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors()
        ) {
            Icon(Icons.Rounded.PlayArrow, null, Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text("Resume at page ${book.lastPage + 1}")
        }
    }
}

private val rangeFmt = DateTimeFormatter.ofPattern("d MMM")

/** e.g. "18 Nov – 24 Nov" */
fun dateRange(days: List<com.progresspal.app.reading.ui.DayStat>): String =
    if (days.isEmpty()) "" else "${days.first().date.format(rangeFmt)} – ${days.last().date.format(rangeFmt)}"
