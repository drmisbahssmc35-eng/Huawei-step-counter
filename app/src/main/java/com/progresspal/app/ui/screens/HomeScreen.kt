package com.progresspal.app.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.SportsMartialArts
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.progresspal.app.data.AppData
import com.progresspal.app.data.activeProjects
import com.progresspal.app.data.dailyTasksFor
import com.progresspal.app.data.overallProgress
import com.progresspal.app.data.today
import com.progresspal.app.ui.components.AddProjectDialog
import com.progresspal.app.ui.components.DailyTaskCard
import com.progresspal.app.ui.components.IconTile
import com.progresspal.app.ui.components.LinkCard
import com.progresspal.app.ui.components.ProjectCard
import com.progresspal.app.ui.components.SalatCard
import com.progresspal.app.ui.components.WeekStrip
import com.progresspal.app.ui.theme.Brand
import com.progresspal.app.ui.theme.Pal
import java.time.LocalDate

@Composable
fun HomeScreen(
    data: AppData,
    onOpenProject: (String) -> Unit,
    onOpenDaily: (LocalDate) -> Unit,
    onOpenChallenge: () -> Unit,
    onOpenAnalytics: () -> Unit,
    onOpenCompleted: () -> Unit,
    onOpenBackup: () -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf(today()) }
    var showAdd by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    val projects = data.projects
    val (done, total, overall) = data.overallProgress()
    val active = activeProjects(projects)
    val doneCount = projects.count { it.completedAt != null }

    Screen {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 20.dp)) {
            IconTile(Icons.Filled.BarChart, Pal.c.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Progress", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Pal.c.foreground)
                Text("$done/$total tasks · $overall% complete", fontSize = 12.sp, color = Pal.c.mutedForeground)
            }
            Button(onClick = { showAdd = true }, shape = RoundedCornerShape(10.dp)) {
                Icon(Icons.Filled.Add, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("New Project")
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More", tint = Pal.c.mutedForeground) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Account & backup") }, onClick = { menu = false; onOpenBackup() })
                }
            }
        }

        WeekStrip(selected) { selected = it }
        DailyTaskCard(selected, dailyTasksFor(data, selected)) { onOpenDaily(selected) }
        SalatCard(data, selected)

        if (projects.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                IconTile(Icons.Filled.BarChart, Pal.c.mutedForeground, size = 64.dp, background = Pal.c.muted)
                Spacer(Modifier.height(16.dp))
                Text("No projects yet", fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = Pal.c.foreground)
                Text(
                    "Create your first project to start tracking progress", fontSize = 14.sp,
                    color = Pal.c.mutedForeground, textAlign = TextAlign.Center,
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                active.forEach { p -> ProjectCard(p) { onOpenProject(p.id) } }
            }
        }

        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LinkCard(Icons.Filled.SportsMartialArts, Brand.coral, "Time vs Task Challenge", "Beat the clock, complete your tasks!", onOpenChallenge)
            LinkCard(Icons.AutoMirrored.Filled.TrendingUp, Brand.purple, "Analytics", "View your productivity insights", onOpenAnalytics)
            if (doneCount > 0) {
                Spacer(Modifier.height(12.dp))
                LinkCard(
                    Icons.Filled.EmojiEvents, Pal.c.primary, "Completed Projects",
                    "$doneCount project${if (doneCount > 1) "s" else ""} finished", onOpenCompleted,
                )
            }
        }
    }
    if (showAdd) AddProjectDialog { showAdd = false }
}
