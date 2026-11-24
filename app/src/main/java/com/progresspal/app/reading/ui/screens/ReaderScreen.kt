package com.progresspal.app.reading.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EditCalendar
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.progresspal.app.reading.data.BookStatus
import com.progresspal.app.reading.ui.ReaderEvent
import com.progresspal.app.reading.ui.ReaderViewModel
import com.progresspal.app.reading.util.PdfDocument
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/** How long a page must stay on screen before it counts as "read" for the daily log. */
private const val READ_DWELL_MS = 2500L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(vm: ReaderViewModel, onBack: () -> Unit, onMarkComplete: () -> Unit) {
    val book by vm.book.collectAsState()
    val doc by vm.document.collectAsState()
    val version by vm.version.collectAsState()
    val initial by vm.initialPage.collectAsState()
    val error by vm.error.collectAsState()
    val stamping by vm.stamping.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // keep the screen awake while reading
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            when (e) {
                is ReaderEvent.Message -> snackbar.showSnackbar(e.text)
                is ReaderEvent.Stamped -> {
                    val r = snackbar.showSnackbar("Stamped: ${e.text}", actionLabel = "Undo", duration = SnackbarDuration.Short)
                    if (r == SnackbarResult.ActionPerformed) vm.undoStamp()
                }
            }
        }
    }

    val b = book
    val document = doc
    val start = initial
    Box(Modifier.fillMaxSize().background(Color(0xFF2B2E36))) {
        when {
            error != null -> Text(error!!, color = Color.White, modifier = Modifier.align(Alignment.Center).padding(24.dp))
            b == null || document == null || start == null ->
                CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            else -> {
                val pager = rememberPagerState(initialPage = start) { document.pageCount }
                var chrome by remember { mutableStateOf(true) }
                var zoomed by remember { mutableStateOf(false) }
                var goTo by remember { mutableStateOf(false) }
                var menu by remember { mutableStateOf(false) }

                LaunchedEffect(pager.settledPage) {
                    val p = pager.settledPage
                    vm.onPageShown(p)
                    delay(READ_DWELL_MS)
                    vm.onPageRead(p)
                }

                HorizontalPager(
                    state = pager,
                    userScrollEnabled = !zoomed,
                    beyondViewportPageCount = 1,
                    modifier = Modifier.fillMaxSize(),
                    key = { it },
                ) { index ->
                    PdfPage(
                        doc = document,
                        index = index,
                        version = version,
                        onTap = { chrome = !chrome },
                        onZoomChange = { if (index == pager.currentPage) zoomed = it },
                        onLongPress = { fx, fy -> vm.stamp(index, fx, fy) },
                    )
                }

                // ---- top bar ----
                AnimatedVisibility(chrome, enter = fadeIn() + slideInVertically { -it }, exit = fadeOut() + slideOutVertically { -it },
                    modifier = Modifier.align(Alignment.TopCenter)) {
                    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f), shadowElevation = 4.dp) {
                        Row(
                            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                            Column(Modifier.weight(1f)) {
                                Text(b.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${b.percent}% read · ${b.stampCount} stamp${if (b.stampCount == 1) "" else "s"}",
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Box {
                                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Share PDF (with stamps)") },
                                        leadingIcon = { Icon(Icons.Rounded.Share, null) },
                                        onClick = {
                                            menu = false
                                            val uri = FileProvider.getUriForFile(context, context.packageName + ".readingfiles", File(b.filePath))
                                            val send = Intent(Intent.ACTION_SEND).apply {
                                                type = "application/pdf"
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                putExtra(Intent.EXTRA_SUBJECT, b.title)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(send, "Share PDF"))
                                        }
                                    )
                                    if (b.status != BookStatus.COMPLETED) {
                                        DropdownMenuItem(
                                            text = { Text("Mark as completed") },
                                            leadingIcon = { Icon(Icons.Rounded.CheckCircle, null) },
                                            onClick = { menu = false; onMarkComplete() }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ---- bottom bar ----
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                    SnackbarHost(snackbar, Modifier.fillMaxWidth())
                    AnimatedVisibility(chrome, enter = fadeIn() + slideInVertically { it }, exit = fadeOut() + slideOutVertically { it }) {
                        Column {
                            Row(Modifier.fillMaxWidth().padding(end = 16.dp, bottom = 12.dp), horizontalArrangement = Arrangement.End) {
                                ExtendedFloatingActionButton(
                                    onClick = { if (!stamping) vm.stamp(pager.currentPage) },
                                    icon = {
                                        if (stamping) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                        else Icon(Icons.Rounded.EditCalendar, null)
                                    },
                                    text = { Text("Stamp date & time") },
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                            Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f), shadowElevation = 8.dp) {
                                Column(Modifier.navigationBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            enabled = pager.currentPage > 0,
                                            onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }
                                        ) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Previous page") }
                                        var dragging by remember { mutableStateOf<Float?>(null) }
                                        Slider(
                                            value = dragging ?: pager.currentPage.toFloat(),
                                            onValueChange = { dragging = it },
                                            onValueChangeFinished = {
                                                dragging?.let { v -> scope.launch { pager.scrollToPage(v.toInt()) } }
                                                dragging = null
                                            },
                                            valueRange = 0f..(document.pageCount - 1).coerceAtLeast(1).toFloat(),
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            enabled = pager.currentPage < document.pageCount - 1,
                                            onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }
                                        ) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Next page") }
                                    }
                                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "Page ${pager.currentPage + 1} of ${document.pageCount}",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { goTo = true }.padding(6.dp)
                                        )
                                        Spacer(Modifier.weight(1f))
                                        Icon(Icons.Rounded.TouchApp, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            "Long-press page to stamp there",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (goTo) {
                    var text by remember { mutableStateOf("${pager.currentPage + 1}") }
                    AlertDialog(
                        onDismissRequest = { goTo = false },
                        title = { Text("Go to page") },
                        text = {
                            OutlinedTextField(
                                text, { text = it.filter(Char::isDigit).take(6) },
                                singleLine = true, label = { Text("1 – ${document.pageCount}") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                text.toIntOrNull()?.let { p ->
                                    scope.launch { pager.scrollToPage((p - 1).coerceIn(0, document.pageCount - 1)) }
                                }
                                goTo = false
                            }) { Text("Go") }
                        },
                        dismissButton = { TextButton(onClick = { goTo = false }) { Text("Cancel") } }
                    )
                }
            }
        }
    }
}

@Composable
private fun PdfPage(
    doc: PdfDocument,
    index: Int,
    version: Int,
    onTap: () -> Unit,
    onZoomChange: (Boolean) -> Unit,
    onLongPress: (Float, Float) -> Unit,
) {
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val tap by rememberUpdatedState(onTap)
    val longPress by rememberUpdatedState(onLongPress)
    val zoomChange by rememberUpdatedState(onZoomChange)

    val aspect by produceState(0.707f, doc, index) { value = doc.aspectRatio(index).takeIf { it > 0f } ?: 0.707f }
    val bitmap: ImageBitmap? by produceState<ImageBitmap?>(null, doc, version, index, boxSize.width) {
        if (boxSize.width > 0) {
            value = doc.render(index, (boxSize.width * 1.6f).toInt())?.asImageBitmap()
        }
    }

    fun clamp(o: Offset, s: Float): Offset {
        val maxX = boxSize.width * (s - 1f) / 2f
        val maxY = boxSize.height * (s - 1f) / 2f
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    fun setScale(s: Float, o: Offset) {
        val was = scale > 1.01f
        scale = s
        offset = if (s <= 1f) Offset.Zero else clamp(o, s)
        val now = scale > 1.01f
        if (was != now) zoomChange(now)
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { boxSize = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { tap() },
                    onDoubleTap = { p ->
                        if (scale > 1.01f) setScale(1f, Offset.Zero)
                        else {
                            val s = 2.5f
                            val c = Offset(size.width / 2f, size.height / 2f)
                            setScale(s, (c - p) * (s - 1f))
                        }
                    },
                    onLongPress = { p ->
                        // undo the zoom/pan transform, then map into the fitted page rectangle
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val c = Offset(w / 2, h / 2)
                        val q = c + (p - c - offset) / scale
                        val imgW: Float
                        val imgH: Float
                        if (w / h > aspect) { imgH = h; imgW = h * aspect } else { imgW = w; imgH = w / aspect }
                        val left = (w - imgW) / 2
                        val top = (h - imgH) / 2
                        val fx = (q.x - left) / imgW
                        val fy = (q.y - top) / imgH
                        if (fx in 0f..1f && fy in 0f..1f) longPress(fx, fy)
                    }
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val fingers = event.changes.count { it.pressed }
                        if (fingers >= 2 || scale > 1.01f) {
                            val z = event.calculateZoom()
                            val pan = event.calculatePan()
                            setScale((scale * z).coerceIn(1f, 5f), offset + pan)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .graphicsLayer {
                    scaleX = scale; scaleY = scale
                    translationX = offset.x; translationY = offset.y
                }
                .padding(0.dp)
                .aspectRatio(aspect, matchHeightConstraintsFirst = boxSize.width.toFloat() / boxSize.height.coerceAtLeast(1) > aspect)
                .shadow(6.dp)
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            val bmp = bitmap
            if (bmp != null) {
                Image(bmp, contentDescription = "Page ${index + 1}", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            } else {
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
            }
        }
    }
}

