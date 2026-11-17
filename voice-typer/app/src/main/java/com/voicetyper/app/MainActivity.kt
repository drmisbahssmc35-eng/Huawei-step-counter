package com.voicetyper.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.voicetyper.app.Prefs.Companion.ENGINE_CLOUD
import com.voicetyper.app.Prefs.Companion.ENGINE_SYSTEM

class MainActivity : ComponentActivity() {
    private val vm: DictationViewModel by viewModels()
    private var startOnResume = false
    private var startAfterPermission = false

    private val micPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted && startAfterPermission) vm.start()
            if (!granted) vm.status = "Microphone permission is needed to hear you"
            startAfterPermission = false
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VoiceTyperTheme {
                MainScreen(vm, onMic = { onMicTapped() })
            }
        }
        if (savedInstanceState == null) handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        if (i?.action == ACTION_DICTATE) startOnResume = true
    }

    override fun onResume() {
        super.onResume()
        if (startOnResume) {
            startOnResume = false
            if (!vm.listening) startWithPermission()
        }
    }

    // The mic only works while the app is on screen, so wrap up (and copy) when leaving it.
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) vm.stop()
    }

    private fun onMicTapped() {
        if (vm.listening) vm.stop() else startWithPermission()
    }

    private fun startWithPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            vm.start()
        } else {
            startAfterPermission = true
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    companion object {
        const val ACTION_DICTATE = "com.voicetyper.app.DICTATE"
    }
}

private val LANGUAGES = listOf(
    "" to "Auto / phone default",
    "en-US" to "English (US)",
    "en-GB" to "English (UK)",
    "en-IN" to "English (India)",
    "bn-BD" to "বাংলা (Bangladesh)",
    "bn-IN" to "বাংলা (India)",
    "hi-IN" to "हिन्दी (Hindi)",
    "ur-PK" to "اردو (Urdu)",
    "ar-SA" to "العربية (Arabic)",
)

@Composable
fun VoiceTyperTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: DictationViewModel, onMic: () -> Unit) {
    var showSettings by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val locked = vm.listening || vm.transcribing

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Voice Typer") },
                actions = {
                    IconButton(onClick = { showHistory = true }) { Icon(Icons.Default.History, "History") }
                    IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, "Settings") }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = vm.engine == ENGINE_SYSTEM,
                    onClick = { vm.updateEngine(ENGINE_SYSTEM) },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                    enabled = !locked,
                ) { Text("Google · live") }
                SegmentedButton(
                    selected = vm.engine == ENGINE_CLOUD,
                    onClick = { vm.updateEngine(ENGINE_CLOUD) },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    enabled = !locked,
                ) { Text("Whisper AI · best") }
            }

            Spacer(Modifier.size(8.dp))
            LanguagePicker(vm.language, enabled = !locked, onPick = vm::updateLanguage)
            Spacer(Modifier.size(8.dp))

            OutlinedTextField(
                value = vm.text,
                onValueChange = { vm.text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                placeholder = { Text("Your words will appear here…") },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 19.sp, lineHeight = 27.sp),
            )
            if (vm.partial.isNotBlank()) {
                Text(
                    vm.partial,
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                TextButton(onClick = { vm.copy() }, enabled = vm.text.isNotBlank()) {
                    Icon(Icons.Default.ContentCopy, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Copy")
                }
                TextButton(
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, vm.text.trim())
                        ctx.startActivity(Intent.createChooser(send, "Share text"))
                    },
                    enabled = vm.text.isNotBlank(),
                ) {
                    Icon(Icons.Default.Share, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Share")
                }
                TextButton(onClick = { vm.text = "" }, enabled = vm.text.isNotEmpty() && !locked) {
                    Icon(Icons.Default.Delete, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Clear")
                }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                MicButton(vm.listening, vm.transcribing, vm.level, onMic)
            }
            Text(
                vm.status,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showSettings) SettingsDialog(vm.prefs, onDismiss = { showSettings = false })
    if (showHistory) HistoryDialog(vm, onDismiss = { showHistory = false })
}

@Composable
fun LanguagePicker(current: String, enabled: Boolean, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Language, null)
            Spacer(Modifier.width(8.dp))
            Text(LANGUAGES.firstOrNull { it.first == current }?.second ?: current)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            LANGUAGES.forEach { (tag, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = {
                    onPick(tag)
                    open = false
                })
            }
        }
    }
}

@Composable
fun MicButton(listening: Boolean, busy: Boolean, level: Float, onClick: () -> Unit) {
    val animLevel by animateFloatAsState(if (listening) level else 0f, label = "level")
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulseScale",
    )
    val color = if (listening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
        if (listening) {
            Box(
                Modifier
                    .size(96.dp)
                    .graphicsLayer {
                        val s = pulse + animLevel * 0.45f
                        scaleX = s
                        scaleY = s
                    }
                    .background(color.copy(alpha = 0.22f), CircleShape),
            )
        }
        Surface(
            onClick = onClick,
            enabled = !busy,
            shape = CircleShape,
            color = color,
            shadowElevation = 6.dp,
            modifier = Modifier.size(96.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (busy) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(40.dp))
                } else {
                    Icon(
                        if (listening) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = if (listening) "Stop" else "Start dictation",
                        tint = if (listening) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(46.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryDialog(vm: DictationViewModel, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Recent transcripts") },
        text = {
            if (vm.history.isEmpty()) {
                Text("Nothing yet.")
            } else {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(vm.history, key = { it }) { t ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    vm.copy(t)
                                    onDismiss()
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(t, Modifier.weight(1f), maxLines = 3, overflow = TextOverflow.Ellipsis)
                            IconButton(onClick = { vm.deleteHistory(t) }) { Icon(Icons.Default.Delete, "Delete") }
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = {
            if (vm.history.isNotEmpty()) TextButton(onClick = { vm.clearHistory() }) { Text("Clear all") }
        },
    )
}

@Composable
fun SettingsDialog(prefs: Prefs, onDismiss: () -> Unit) {
    var autoCopy by remember { mutableStateOf(prefs.autoCopy) }
    var append by remember { mutableStateOf(prefs.appendMode) }
    var base by remember { mutableStateOf(prefs.apiBase) }
    var model by remember { mutableStateOf(prefs.model) }
    var key by remember { mutableStateOf(prefs.apiKey) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SwitchRow("Copy to clipboard automatically", autoCopy) { autoCopy = it }
                SwitchRow("Add new speech to the previous text", append) { append = it }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("Whisper AI (cloud)", style = MaterialTheme.typography.titleSmall)
                Text(
                    "The most accurate option, similar to Claude's voice typing. Handles mixed Bangla and English well. " +
                        "Needs an API key: Groq has a free tier (console.groq.com → API Keys).",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = {
                        base = Prefs.GROQ_BASE
                        model = Prefs.GROQ_MODEL
                    }, label = { Text("Groq (free)") })
                    AssistChip(onClick = {
                        base = Prefs.OPENAI_BASE
                        model = Prefs.OPENAI_MODEL
                    }, label = { Text("OpenAI") })
                }
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("API key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = base,
                    onValueChange = { base = it },
                    label = { Text("API address") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("Model") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Tip: add the Voice Typer tile to Quick Settings (pull down twice → ✎) " +
                        "to start dictating from any app.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                prefs.autoCopy = autoCopy
                prefs.appendMode = append
                prefs.apiBase = base
                prefs.model = model
                prefs.apiKey = key
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
