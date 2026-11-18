package com.progresspal.app.ui.components

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import com.progresspal.app.data.SimpleTimeTracker

/** Loads Simple Time Tracker's own icon drawables (e.g. "ic_wb_sunny_24px") from its installed app. */
object SttIcons {
    private val cache = HashMap<String, ImageBitmap?>()

    fun load(context: Context, name: String): ImageBitmap? = synchronized(cache) {
        cache.getOrPut(name) {
            runCatching {
                val res = context.packageManager.getResourcesForApplication(SimpleTimeTracker.PKG)
                val id = res.getIdentifier(name, "drawable", SimpleTimeTracker.PKG)
                if (id == 0) null
                else ResourcesCompat.getDrawable(res, id, null)?.toBitmap(96, 96)?.asImageBitmap()
            }.getOrNull()
        }
    }
}

/**
 * An activity icon as Simple Time Tracker shows it: its drawable when it is an image icon,
 * the emoji/text when it is a text icon, else the first letter of the name (without "17.").
 */
@Composable
fun SttIcon(icon: String?, name: String?, tint: Color, size: Dp = 32.dp) {
    val ctx = LocalContext.current
    val bmp = remember(icon) { if (icon != null && icon.startsWith("ic_")) SttIcons.load(ctx, icon) else null }
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        if (bmp != null) {
            Image(bmp, null, Modifier.size(size), colorFilter = ColorFilter.tint(tint))
        } else if (!icon.isNullOrBlank() && !icon.startsWith("ic_")) {
            Text(icon, fontSize = (size.value * 0.62f).sp, color = tint)
        } else {
            val letter = (name ?: "?").replace(Regex("^\\d+\\s*[.)\\-:]?\\s*"), "").ifBlank { name ?: "?" }.take(1).uppercase()
            Text(letter, fontSize = (size.value * 0.62f).sp, color = tint, fontWeight = FontWeight.Bold)
        }
    }
}
