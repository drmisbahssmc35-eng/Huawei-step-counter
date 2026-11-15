package com.progresspal.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.progresspal.app.data.Cloud
import com.progresspal.app.ui.components.AppCard
import com.progresspal.app.ui.components.IconTile
import com.progresspal.app.ui.theme.Pal

/** Multi-colour Google "G". */
@Composable
fun GoogleG(size: androidx.compose.ui.unit.Dp = 20.dp) {
    Canvas(Modifier.size(size)) {
        val sw = this.size.minDimension * .22f
        val d = this.size.minDimension - sw
        val tl = Offset(sw / 2, sw / 2)
        val s = Size(d, d)
        drawArc(Color(0xFFEA4335), 200f, 115f, false, tl, s, style = Stroke(sw))
        drawArc(Color(0xFFFBBC05), 140f, 60f, false, tl, s, style = Stroke(sw))
        drawArc(Color(0xFF34A853), 45f, 95f, false, tl, s, style = Stroke(sw))
        drawArc(Color(0xFF4285F4), -10f, 55f, false, tl, s, style = Stroke(sw))
        val cy = this.size.height / 2
        drawLine(Color(0xFF4285F4), Offset(this.size.width / 2, cy), Offset(this.size.width - sw / 2, cy), sw)
    }
}

@Composable
fun GoogleButton(text: String = "Continue with Google", onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Pal.c.border),
    ) {
        GoogleG()
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Pal.c.foreground)
    }
}

@Composable
fun LoginScreen() {
    val context = LocalContext.current
    Box(Modifier.fillMaxSize().background(Pal.c.background).safeDrawingPadding().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 380.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            IconTile(Icons.Filled.BarChart, Pal.c.primary, size = 64.dp)
            Spacer(Modifier.height(16.dp))
            Text("Progress", fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Pal.c.foreground)
            Text("Track your projects and daily tasks", fontSize = 14.sp, color = Pal.c.mutedForeground)
            Spacer(Modifier.height(32.dp))
            GoogleButton { Cloud.startSignIn(context) }
            Text(
                "Opens your browser to sign in, then returns here. Your projects sync with the Progress Pal website.",
                fontSize = 12.sp, color = Pal.c.mutedForeground, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp),
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { Cloud.skipSignIn() }) {
                Text("Use without an account", color = Pal.c.mutedForeground, fontSize = 13.sp)
            }
        }
    }
}

/** Account card shown on the Backup screen and reachable from Home's ⋮ menu. */
@Composable
fun AccountCard() {
    val context = LocalContext.current
    val state by Cloud.state.collectAsStateWithLifecycle()
    AppCard(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        when (val s = state) {
            is Cloud.State.SignedOut -> {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                    Icon(Icons.Filled.CloudOff, null, tint = Pal.c.mutedForeground)
                    Spacer(Modifier.width(10.dp))
                    Text("Not signed in — data is only on this phone", fontSize = 14.sp, color = Pal.c.foreground)
                }
                GoogleButton("Sign in with Google") { Cloud.startSignIn(context) }
            }
            is Cloud.State.SignedIn -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (s.syncing) Icons.Filled.Sync else Icons.Filled.Cloud, null, tint = if (s.error != null) Pal.c.destructive else Pal.c.primary)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Signed in" + (s.email?.let { " as $it" } ?: ""), fontSize = 14.sp, color = Pal.c.foreground)
                        Text(
                            s.error ?: if (s.syncing) "Syncing…" else "Synced with the website",
                            fontSize = 12.sp, color = if (s.error != null) Pal.c.destructive else Pal.c.mutedForeground,
                        )
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { Cloud.pull() }) { Text("Sync now") }
                    TextButton(onClick = { Cloud.signOut() }) { Text("Sign out", color = Pal.c.destructive) }
                }
            }
        }
    }
}
