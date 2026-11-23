package com.progresspal.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.progresspal.app.data.AppData
import com.progresspal.app.data.AppStore
import com.progresspal.app.data.Cloud
import com.progresspal.app.ui.screens.AccountCard
import com.progresspal.app.ui.screens.LoginScreen
import com.progresspal.app.data.today
import com.progresspal.app.ui.components.AppCard
import com.progresspal.app.ui.components.ScreenHeader
import com.progresspal.app.ui.components.toast
import com.progresspal.app.ui.screens.AnalyticsScreen
import com.progresspal.app.ui.screens.ChallengeHistoryScreen
import com.progresspal.app.ui.screens.ChallengeScreen
import com.progresspal.app.ui.screens.CompletedScreen
import com.progresspal.app.ui.screens.DailyTasksScreen
import com.progresspal.app.ui.screens.HomeScreen
import com.progresspal.app.ui.screens.ProjectDetailScreen
import com.progresspal.app.ui.screens.Screen
import com.progresspal.app.ui.theme.Pal
import com.progresspal.app.ui.theme.ProgressPalTheme
import java.time.LocalDate

/** A screen the app should open (e.g. from the Day widget). */
val pendingRoute = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

class MainActivity : ComponentActivity() {
    companion object { const val EXTRA_OPEN = "open" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppStore.init(applicationContext)
        com.progresspal.app.ui.components.Sounds.init(applicationContext)
        com.progresspal.app.data.SimpleTimeTracker.init(applicationContext)
        com.progresspal.app.data.Planner.init(applicationContext)
        com.progresspal.app.data.DailyOrder.init(applicationContext)
        com.progresspal.app.data.Portfolio.init(applicationContext)
        com.progresspal.app.data.Subtasks.init(applicationContext)
        com.progresspal.app.data.WatchTracker.init(applicationContext)
        handleAuth(intent)
        intent?.getStringExtra(EXTRA_OPEN)?.let { pendingRoute.value = it }
        // Needed for the Azan's Stop notification (Android 13+).
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        setContent {
            ProgressPalTheme {
                App()
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleAuth(intent)
        intent.getStringExtra(EXTRA_OPEN)?.let { pendingRoute.value = it }
    }

    private fun handleAuth(intent: android.content.Intent?) {
        if (Cloud.handleRedirect(intent?.data)) toast(this, "Signed in — syncing your data…")
    }

    override fun onResume() {
        super.onResume()
        AppStore.autoResetRecurring()
        Cloud.pull()
        ProgressWidget.refresh(this)
        com.progresspal.app.data.SimpleTimeTracker.syncSoon()
        AzanScheduler.schedule(this)
        LiveService.start(this)
    }
}

@Composable
fun App() {
    val data by AppStore.data.collectAsStateWithLifecycle()
    val onboarded by Cloud.onboarded.collectAsStateWithLifecycle()
    if (!onboarded) {
        LoginScreen()
        return
    }
    val nav = rememberNavController()
    val back: () -> Unit = { nav.popBackStack() }
    val openProject: (String) -> Unit = { nav.navigate("project/$it") }

    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
    val route by pendingRoute.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(route) {
        route?.let { nav.navigate(it) { launchSingleTop = true }; pendingRoute.value = null }
    }
    NavHost(nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                data = data,
                onOpenProject = openProject,
                onOpenDaily = { nav.navigate("daily/$it") },
                onOpenChallenge = { nav.navigate("challenge") },
                onOpenAnalytics = { nav.navigate("analytics") },
                onOpenCompleted = { nav.navigate("completed") },
                onOpenBackup = { nav.navigate("backup") },
                onOpenTimeTracker = { nav.navigate("timetracker") },
                onOpenPortfolio = { nav.navigate("portfolio") },
                onOpenWatch = { nav.navigate("watch") },
            )
        }
        composable("project/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            ProjectDetailScreen(data, entry.arguments?.getString("id") ?: "", back)
        }
        composable("daily/{date}", arguments = listOf(navArgument("date") { type = NavType.StringType })) { entry ->
            val date = runCatching { LocalDate.parse(entry.arguments?.getString("date") ?: "") }.getOrElse { today() }
            DailyTasksScreen(data, date, back, openProject)
        }
        composable("completed") { CompletedScreen(data, back, openProject) }
        composable("analytics") { AnalyticsScreen(data, back) }
        composable("challenge") { ChallengeScreen(data, back) { nav.navigate("history") } }
        composable("history") { ChallengeHistoryScreen(data, back) }
        composable("backup") { BackupScreen(data, back) }
        composable("watch") { com.progresspal.app.ui.screens.WatchTrackerScreen(back) }
        composable("portfolio") { com.progresspal.app.ui.screens.PortfolioScreen(data, back) { nav.navigate("ptasks/$it") } }
        composable("ptasks/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            com.progresspal.app.ui.screens.ProjectTasksScreen(data, entry.arguments?.getString("id") ?: "", back)
        }
        composable("timetracker") { com.progresspal.app.ui.screens.TimeTrackerScreen(data, back) }
    }
    // On every page: a small button that opens Simple Time Tracker.
    com.progresspal.app.ui.components.SttLauncherFab()
    }
}

@Composable
private fun BackupScreen(data: AppData, onBack: () -> Unit) {
    val context = LocalContext.current
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(AppStore.exportJson().toByteArray()) }
            }.isSuccess
            toast(context, if (ok) "Backup saved" else "Could not save backup")
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
            toast(context, if (text != null && AppStore.importJson(text)) "Backup restored" else "That file isn't a Progress Pal backup")
        }
    }
    Screen {
        ScreenHeader("Account & backup", null, Icons.Filled.Backup, Pal.c.primary, onBack)
        AccountCard()
        AppCard(Modifier.fillMaxWidth()) {
            Text(
                "${data.projects.size} projects · ${data.projects.sumOf { it.tasks.size }} tasks · ${data.challenges.size} challenges",
                fontSize = 14.sp, color = Pal.c.foreground,
            )
            Text(
                "Save a backup file before changing phones or uninstalling the app, then restore it on the new phone.",
                fontSize = 12.sp, color = Pal.c.mutedForeground, modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
            )
            Button(onClick = { exporter.launch("progress-pal-backup-${today()}.json") }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Download, null)
                Text("  Save backup")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { importer.launch(arrayOf("application/json", "text/plain", "*/*")) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Upload, null)
                Text("  Restore from backup (replaces current data)")
            }
        }
    }
}
