package com.progresspal.app.ui.components

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import com.progresspal.app.data.SttActivity
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


/** One activity as a full-width bar in its own colour, exactly like Simple Time Tracker's list. */
@Composable
fun SttActivityRow(
    a: SttActivity,
    selected: Boolean = false,
    onClick: () -> Unit,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(shape)
            .background(Color(a.color.toInt()))
            .then(if (selected) Modifier.border(3.dp, Color.White, shape) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SttIcon(a.icon, a.name, Color.White, 34.dp)
        Spacer(Modifier.width(18.dp))
        Text(
            a.name, Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        trailing()
    }
}
