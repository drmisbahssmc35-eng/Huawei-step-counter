package com.progresspal.app.reading

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.progresspal.app.reading.data.Book
import com.progresspal.app.reading.data.BookStatus
import com.progresspal.app.reading.ui.MainViewModel
import com.progresspal.app.reading.ui.ReaderViewModel
import com.progresspal.app.reading.ui.components.BookDetailsDialog
import com.progresspal.app.reading.ui.components.ConfirmDeleteDialog
import com.progresspal.app.reading.ui.screens.DashboardScreen
import com.progresspal.app.reading.ui.screens.LibraryScreen
import com.progresspal.app.reading.ui.screens.ReaderScreen
import com.progresspal.app.reading.ui.screens.StatsScreen
import com.progresspal.app.reading.ui.theme.ReadingTheme

import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import com.progresspal.app.data.SimpleTimeTracker

/** Reading Tracker: read PDF books, stamp the date & time, and track pages read. */
@Composable
fun ReadingTrackerScreen(onBack: () -> Unit) {
    ReadingTheme {
        val pending by ReadingGraph.incoming.collectAsStateWithLifecycle()
        App(pending, { ReadingGraph.incoming.value = null }, onBack)
    }
}

/**
 * While a book is open in the reader, runs the Simple Time Tracker activity for reading (name with
 * "Reading", "Book", "PDF" or "Read"); stops it when the reader closes. A timer it didn't start is left alone.
 */
@Composable
private fun ReadingStt(bookTitle: String?) {
    val act = remember {
        val acts = SimpleTimeTracker.cache.value.activities
        listOf("reading tracker", "pdf book", "reading", "book", "pdf", "read")
            .firstNotNullOfOrNull { w -> acts.firstOrNull { it.name.contains(w, ignoreCase = true) } }
    }
    var startedByUs by remember { mutableStateOf(false) }
    LaunchedEffect(bookTitle) {
        if (bookTitle != null && act != null && !SimpleTimeTracker.isRunning(act.id)) {
            SimpleTimeTracker.start(act, bookTitle); startedByUs = true
        }
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { if (startedByUs && act != null && SimpleTimeTracker.isRunning(act.id)) SimpleTimeTracker.toggle(act) }
    }
}

private enum class Tab(val route: String, val label: String, val icon: ImageVector, val title: String) {
    Dashboard("dashboard", "Dashboard", Icons.Rounded.Dashboard, "Reading Tracker"),
    Library("library", "Library", Icons.AutoMirrored.Rounded.LibraryBooks, "My Library"),
    Stats("stats", "Statistics", Icons.Rounded.BarChart, "Statistics"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App(incoming: Uri?, consumeIncoming: () -> Unit, onExit: () -> Unit) {
    val vm: MainViewModel = viewModel()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val pendingImport by vm.pendingImport.collectAsStateWithLifecycle()
    val importing by vm.importing.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val snackbar = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<Book?>(null) }
    var deleting by remember { mutableStateOf<Book?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.onPdfPicked(uri)
    }
    val addBook = { picker.launch(arrayOf("application/pdf")) }

    LaunchedEffect(incoming) {
        incoming?.let { vm.onPdfPicked(it); consumeIncoming() }
    }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    val openBook: (Long) -> Unit = { id -> nav.navigate("reader/$id") }
    val currentTab = Tab.entries.firstOrNull { it.route == route }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (currentTab != null) {
                CenterAlignedTopAppBar(
                    title = { Text(currentTab.title, style = MaterialTheme.typography.titleLarge) },
                    navigationIcon = { IconButton(onClick = onExit) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                    actions = {
                        if (currentTab != Tab.Library) {
                            IconButton(onClick = addBook) { Icon(Icons.Rounded.Add, "Add book") }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            }
        },
        bottomBar = {
            if (currentTab != null) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = t == currentTab,
                            onClick = {
                                nav.navigate(t.route) {
                                    popUpTo(Tab.Dashboard.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(t.icon, null) },
                            label = { Text(t.label) }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            AnimatedVisibility(currentTab == Tab.Library, enter = scaleIn(), exit = scaleOut()) {
                ExtendedFloatingActionButton(
                    onClick = addBook,
                    icon = { Icon(Icons.Rounded.Add, null) },
                    text = { Text("Add book") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Tab.Dashboard.route) {
            composable(Tab.Dashboard.route) {
                DashboardScreen(stats, padding, openBook, addBook)
            }
            composable(Tab.Library.route) {
                LibraryScreen(
                    stats, padding, openBook,
                    onStatus = { b, s -> vm.setStatus(b, s) },
                    onEdit = { editing = it },
                    onDelete = { deleting = it },
                )
            }
            composable(Tab.Stats.route) {
                StatsScreen(stats, padding, onGoal = vm::setDailyGoal, onOpenBook = openBook)
            }
            composable(
                "reader/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { entry ->
                val id = entry.arguments?.getLong("id") ?: 0L
                val app = LocalContext.current.applicationContext as android.app.Application
                val readerVm: ReaderViewModel = viewModel(
                    key = "reader-$id",
                    factory = viewModelFactory { initializer { ReaderViewModel(app, id) } }
                )
                val readingBook by readerVm.book.collectAsStateWithLifecycle()
                ReadingStt(readingBook?.title)
                ReaderScreen(
                    readerVm,
                    onBack = { nav.popBackStack() },
                    onMarkComplete = { readerVm.book.value?.let { vm.setStatus(it, BookStatus.COMPLETED) } },
                )
            }
        }
    }

    pendingImport?.let { p ->
        BookDetailsDialog(
            title = "Add new book",
            initialTitle = p.suggestedTitle,
            initialAuthor = "",
            initialStatus = BookStatus.READING,
            busy = importing,
            confirmLabel = "Add",
            onDismiss = vm::cancelImport,
            onConfirm = { t, a, s ->
                vm.confirmImport(t, a, s ?: BookStatus.READING) { id ->
                    if (s == BookStatus.READING) openBook(id)
                }
            },
        )
    }
    editing?.let { b ->
        BookDetailsDialog(
            title = "Edit book",
            initialTitle = b.title,
            initialAuthor = b.author,
            initialStatus = null,
            busy = false,
            confirmLabel = "Save",
            onDismiss = { editing = null },
            onConfirm = { t, a, _ -> vm.rename(b, t, a); editing = null },
        )
    }
    deleting?.let { b ->
        ConfirmDeleteDialog(b, onDismiss = { deleting = null }) { vm.delete(b); deleting = null }
    }
}
