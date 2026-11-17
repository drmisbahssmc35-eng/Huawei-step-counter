package com.timetally.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.timetally.app.data.Store
import com.timetally.app.ui.RecordsScreen
import com.timetally.app.ui.SettingsScreen
import com.timetally.app.ui.StatsScreen
import com.timetally.app.ui.TallyTheme
import com.timetally.app.ui.TimersScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Store.init(applicationContext)
        setContent { TallyTheme { App() } }
    }
}

private data class Tab(val label: String, val icon: ImageVector)

private val Tabs = listOf(
    Tab("Timers", Icons.Filled.Timer),
    Tab("Records", Icons.AutoMirrored.Filled.FormatListBulleted),
    Tab("Statistics", Icons.Filled.BarChart),
    Tab("Settings", Icons.Filled.Settings),
)

@Composable
private fun App() {
    val data by Store.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    // Ask for notification permission (Android 13+) so running timers can show in the shade.
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        Store.refreshNotification()
    }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
        Store.refreshNotification()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar {
                Tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(t.icon, t.label) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when (tab) {
                0 -> TimersScreen(data)
                1 -> RecordsScreen(data)
                2 -> StatsScreen(data)
                else -> SettingsScreen(data)
            }
        }
    }
}
