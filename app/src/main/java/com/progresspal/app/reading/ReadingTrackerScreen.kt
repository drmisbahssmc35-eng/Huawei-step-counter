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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.unit.dp

/** Reading Tracker: read PDF books, stamp the date & time, and track pages read. */
@Composable
fun ReadingTrackerScreen(onBack: () -> Unit) {
    ReadingTheme {
        val pending by ReadingGraph.incoming.collectAsStateWithLifecycle()
        App(pending, { ReadingGraph.incoming.value = null }, onBack)
    }
}

/** Which Simple Time Tracker activity each book is recorded as (book id → activity id). */
private object BookActivities {
    private fun prefs(ctx: android.content.Context) = ctx.getSharedPreferences("reading_stt", android.content.Context.MODE_PRIVATE)
    fun get(ctx: android.content.Context, bookId: Long): Long? = prefs(ctx).getLong("book_$bookId", -1L).takeIf { it >= 0 }
    fun set(ctx: android.content.Context, bookId: Long, activityId: Long) = prefs(ctx).edit().putLong("book_$bookId", activityId).apply()
}

/** Default activity for a book: hadith books (Riyad-us-Saliheen, Bukhari, Muslim…) → the Hadith activity; else a reading one. */
private fun defaultActivity(title: String, acts: List<com.progresspal.app.data.SttActivity>): com.progresspal.app.data.SttActivity? {
    val t = title.lowercase()
    val hadithBook = listOf("riyad", "riyaz", "saliheen", "salihin", "hadith", "hadis", "bukhari", "muslim", "tirmidhi", "tirmizi", "abu dawud", "nasai", "ibn majah", "mishkat")
        .any { it in t }
    if (hadithBook) acts.firstOrNull { it.name.contains("hadith", true) || it.name.contains("hadis", true) }?.let { return it }
    return listOf("reading tracker", "pdf book", "reading", "book", "pdf", "read")
        .firstNotNullOfOrNull { w -> acts.firstOrNull { it.name.contains(w, ignoreCase = true) } }
}

/**
 * While a book is open in the reader, runs its Simple Time Tracker activity (chosen per book with the
 * chip at the top; Riyad-us-Saliheen and other hadith books default to the Hadith activity) with the
 * book title as comment, and stops it when the reader closes. A timer it didn't start is left alone.
 */
@Composable
private fun ReadingStt(bookId: Long, bookTitle: String?) {
    if (bookTitle == null) return
    val ctx = LocalContext.current
    val acts = remember { SimpleTimeTracker.cache.value.activities }
    var act by remember(bookId) {
        mutableStateOf(BookActivities.get(ctx, bookId)?.let { id -> acts.firstOrNull { it.id == id } } ?: defaultActivity(bookTitle, acts))
    }
    var startedId by remember { mutableStateOf<Long?>(null) }
    var picking by remember { mutableStateOf(false) }
    LaunchedEffect(act?.id) {
        val a = act ?: return@LaunchedEffect
        // Switching activity: stop the one this reader started.
        startedId?.takeIf { it != a.id }?.let { old -> acts.firstOrNull { it.id == old }?.let { if (SimpleTimeTracker.isRunning(it.id)) SimpleTimeTracker.toggle(it) } }
        startedId = null
        if (!SimpleTimeTracker.isRunning(a.id)) { SimpleTimeTracker.start(a, bookTitle); startedId = a.id }
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            startedId?.let { id -> acts.firstOrNull { it.id == id }?.let { if (SimpleTimeTracker.isRunning(it.id)) SimpleTimeTracker.toggle(it) } }
        }
    }
    androidx.compose.foundation.layout.Box(
        androidx.compose.ui.Modifier.fillMaxSize().windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.statusBars).padding(top = 66.dp),
        contentAlignment = androidx.compose.ui.Alignment.TopCenter,
    ) {
        androidx.compose.material3.Surface(
            onClick = { picking = true },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .92f),
        ) {
            Text(
                "⏱ " + (act?.name ?: "Record in STT: choose activity") + "  ▾",
                androidx.compose.ui.Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1,
            )
        }
    }
    if (picking) com.progresspal.app.ui.screens.ActivityPicker(
        acts, act?.id,
        onPick = { a -> BookActivities.set(ctx, bookId, a.id); act = a; picking = false },
        onDismiss = { picking = false },
        title = "Record this book as",
    )
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
                androidx.compose.foundation.layout.Box {
                    ReaderScreen(
                        readerVm,
                        onBack = { nav.popBackStack() },
                        onMarkComplete = { readerVm.book.value?.let { vm.setStatus(it, BookStatus.COMPLETED) } },
                    )
                    ReadingStt(id, readingBook?.title)
                }
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
