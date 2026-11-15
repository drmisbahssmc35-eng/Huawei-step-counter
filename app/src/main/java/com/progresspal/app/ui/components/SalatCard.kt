package com.progresspal.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Brightness3
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.progresspal.app.data.AppData
import com.progresspal.app.data.AppStore
import com.progresspal.app.data.format
import com.progresspal.app.data.formatTime12
import com.progresspal.app.data.salatOn
import com.progresspal.app.data.salatStats
import com.progresspal.app.data.salatTasks
import com.progresspal.app.data.today
import com.progresspal.app.ui.theme.Brand
import com.progresspal.app.ui.theme.NumberStyle
import com.progresspal.app.ui.theme.Pal
import java.time.LocalDate

/** Five prayer check-circles for [date] plus the current streak. */
@Composable
fun SalatCard(data: AppData, date: LocalDate) {
    val tasks = data.salatTasks()
    if (tasks.isEmpty()) {
        AppCard(Modifier.fillMaxWidth().padding(bottom = 16.dp), borderColor = Brand.green.copy(alpha = .25f), background = Brand.green.copy(alpha = .05f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Filled.Brightness3, Brand.green, size = 36.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Salat tracker", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Pal.c.foreground)
                    Text("Adds Fajr–Isha to Daily Tasks so you can build a streak", fontSize = 12.sp, color = Pal.c.mutedForeground)
                }
                TextButton(onClick = { AppStore.enableSalat() }) { Text("Start") }
            }
        }
        return
    }
    val stats = data.salatStats()
    val isToday = date == today()
    val entries = data.salatOn(date)
    val count = entries.count { it.completed }
    val future = date.isAfter(today())

    AppCard(Modifier.fillMaxWidth().padding(bottom = 16.dp), borderColor = Brand.green.copy(alpha = .3f)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
            IconTile(Icons.Filled.Brightness3, Brand.green, size = 32.dp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("Salat", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Pal.c.foreground)
                Text(
                    (if (isToday) "Today" else date.format("EEE, MMM d")) + " · $count/${entries.size} prayed",
                    fontSize = 11.sp, color = Pal.c.mutedForeground,
                )
            }
            Row(
                Modifier.clip(RoundedCornerShape(10.dp)).background(Brand.coral.copy(alpha = .12f)).padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.LocalFireDepartment, null, tint = Brand.coral, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("${stats.current}", style = NumberStyle, fontSize = 16.sp, color = Brand.coral)
                Text(if (stats.current == 1) " day" else " days", fontSize = 11.sp, color = Brand.coral)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (entries.isEmpty()) {
                Text("No prayers scheduled on this day", fontSize = 12.sp, color = Pal.c.mutedForeground)
            }
            entries.forEach { e ->
                val t = e.task
                val done = e.completed
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (done) Brand.green else Color.Transparent)
                            .border(2.dp, if (done) Brand.green else Pal.c.border, CircleShape)
                            .clickable(enabled = !future) {
                                if (done) Sounds.uncheck() else Sounds.check()
                                if (e.isRecurring) AppStore.toggleDateTask(t.id, date) else AppStore.toggleTask(e.project.id, t.id)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (done) Icon(Icons.Filled.Check, "Prayed", tint = Color.White, modifier = Modifier.size(20.dp))
                        else Text(t.title.take(1), fontWeight = FontWeight.Bold, color = Pal.c.mutedForeground)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(t.title, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = if (done) Pal.c.foreground else Pal.c.mutedForeground)
                    t.dueTime?.let { Text(formatTime12(it), fontSize = 9.sp, color = Pal.c.mutedForeground) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        BarProgress(if (entries.isEmpty()) 0 else count * 100 / entries.size, androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Brand.green, Brand.blue)))
        stats.longest?.let {
            Text(
                "Longest streak: ${it.length} days (${it.start.format("MMM d")} – ${it.end.format("MMM d, yyyy")})",
                fontSize = 11.sp, color = Pal.c.mutedForeground, modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
