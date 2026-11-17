@file:OptIn(ExperimentalMaterial3Api::class)

package com.timetally.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timetally.app.data.AppData
import com.timetally.app.data.Store
import java.time.LocalDate

@Composable
fun SettingsScreen(data: AppData) {
    val ctx = LocalContext.current
    val s = data.settings
    var pendingImport by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runCatching {
            ctx.contentResolver.openOutputStream(uri)!!.use { it.write(Store.exportJson().toByteArray()) }
            toast(ctx, "Backup saved")
        }.onFailure { toast(ctx, "Could not save: ${it.message}") }
    }
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) runCatching {
            ctx.contentResolver.openOutputStream(uri)!!.use { it.write(Store.exportCsv().toByteArray()) }
            toast(ctx, "CSV saved")
        }.onFailure { toast(ctx, "Could not save: ${it.message}") }
    }
    val importJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            pendingImport = ctx.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
        }.onFailure { toast(ctx, "Could not read file") }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 12.dp)) {
        Text("Settings", Modifier.padding(horizontal = 16.dp), fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        Toggle("Allow multiple timers", "Off: starting an activity stops the one that is running", s.multitask) {
            Store.saveSettings(s.copy(multitask = it))
        }
        Toggle("Show untracked time", "In records and statistics", s.showUntracked) {
            Store.saveSettings(s.copy(showUntracked = it))
        }
        Toggle("Notification while a timer runs", "Live clock with a Stop button", s.showNotification) {
            Store.saveSettings(s.copy(showNotification = it))
        }
        Toggle("Week starts on Monday", "Off: week starts on Sunday", s.weekStartsMonday) {
            Store.saveSettings(s.copy(weekStartsMonday = it))
        }

        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        Section("Backup")
        Action(Icons.Filled.Download, "Save backup (.json)", "Activities, records and settings") {
            exportJson.launch("timetally-backup-${LocalDate.now()}.json")
        }
        Action(Icons.Filled.Upload, "Restore backup", "Replaces everything on this phone") {
            importJson.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
        }
        Action(Icons.Filled.TableChart, "Export records (.csv)", "Open in Excel or Google Sheets") {
            exportCsv.launch("timetally-records-${LocalDate.now()}.csv")
        }
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        Action(Icons.Filled.DeleteForever, "Delete all records", "${data.records.size} records · activities are kept") {
            confirmClear = true
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "TimeTally 1.0 · all data stays on this phone",
            Modifier.padding(horizontal = 16.dp), fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    pendingImport?.let { text ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Restore backup?") },
            text = { Text("All current activities and records will be replaced by the backup.") },
            confirmButton = {
                TextButton(onClick = {
                    toast(ctx, if (Store.importJson(text)) "Backup restored" else "That file is not a TimeTally backup")
                    pendingImport = null
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Cancel") } },
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Delete all records?") },
            text = { Text("Running timers are discarded too. This cannot be undone. Consider saving a backup first.") },
            confirmButton = {
                TextButton(onClick = { Store.clearRecords(); confirmClear = false }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title, Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun Toggle(title: String, sub: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!value) }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = value, onCheckedChange = onChange)
    }
}

@Composable
private fun Action(icon: ImageVector, title: String, sub: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, fontWeight = FontWeight.Medium)
            Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
