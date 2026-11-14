package dev.pdfcrate.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** The app's stroke icon set (res/drawable/ic_*.xml). */
@Composable
fun Glyph(@DrawableRes icon: Int, description: String?, modifier: Modifier = Modifier,
          tint: Color = LocalContentColor.current) {
    Icon(painterResource(icon), description, modifier.size(22.dp), tint = tint)
}

/** Icon above a short label, for tool palettes. The label doubles as the accessible name. */
@Composable
fun ToolButton(@DrawableRes icon: Int, label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
               enabled: Boolean = true, selected: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val content = when {
        !enabled -> colors.onSurface.copy(alpha = .38f)
        selected -> colors.onSecondaryContainer
        else -> colors.onSurfaceVariant
    }
    Surface(onClick = onClick, enabled = enabled, shape = MaterialTheme.shapes.medium,
        color = if (selected) colors.secondaryContainer else Color.Transparent, contentColor = content,
        modifier = modifier.sizeIn(minWidth = 60.dp, minHeight = 56.dp)
            .semantics { this.selected = selected; role = Role.Button }) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Glyph(icon, null)
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center)
        }
    }
}

/**
 * Copies plain text. Returns true when the app should confirm the copy itself: Android 13+
 * already shows a system confirmation.
 */
fun copyText(context: Context, label: String, text: String): Boolean {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
        .setPrimaryClip(ClipData.newPlainText(label, text))
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
}
