package net.kravos.jezik.ui.components

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.kravos.jezik.ui.theme.K
import net.kravos.jezik.ui.theme.LocalPalette

/** True when the system runs with animations off (emulator smoke test): render animations static. */
@Composable
fun animationsOff(): Boolean {
    val ctx = LocalContext.current
    return remember { runCatching { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false) }
}

/** A "3D" button/card: a darker slab under the face; the face sinks when pressed. */
@Composable
fun Chunky(
    modifier: Modifier = Modifier,
    color: Color,
    shadow: Color = K.darker(color),
    border: Color? = null,
    shape: Shape = RoundedCornerShape(16.dp),
    depth: Dp = 4.dp,
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val off = if (pressed && enabled) depth else 0.dp
    Box(modifier.padding(bottom = depth), propagateMinConstraints = true) {
        Box(Modifier.matchParentSize().offset(y = depth).background(shadow, shape))
        Box(
            Modifier.offset(y = off).clip(shape).background(color, shape)
                .then(if (border != null) Modifier.border(2.dp, border, shape) else Modifier)
                .clickable(src, null, enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}

/** Primary full-width action button ("PROVERI", "DALJE"). */
@Composable
fun BigButton(text: String, modifier: Modifier = Modifier, color: Color = K.Green, enabled: Boolean = true, onClick: () -> Unit) {
    val pal = LocalPalette.current
    val c = if (enabled) color else pal.locked
    Chunky(
        modifier.fillMaxWidth(), color = c, shadow = if (enabled) K.darker(color) else pal.locked,
        enabled = enabled, onClick = onClick,
    ) {
        Text(
            text.uppercase(), Modifier.padding(vertical = 14.dp), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, letterSpacing = 0.8.sp,
            color = if (enabled) Color.White else pal.dim,
        )
    }
}

/** Outlined option card used by multiple-choice exercises. */
@Composable
fun OptionCard(text: String, state: OptState, modifier: Modifier = Modifier, enabled: Boolean = true, fontSize: Int = 17, onClick: () -> Unit) {
    val pal = LocalPalette.current
    val (bg, br, fg) = when (state) {
        OptState.NORMAL -> Triple(MaterialTheme.colorScheme.background, pal.border, MaterialTheme.colorScheme.onBackground)
        OptState.SELECTED -> Triple(MaterialTheme.colorScheme.secondaryContainer, K.Blue, K.Blue)
        OptState.RIGHT -> Triple(pal.okBg, K.Green, K.Green)
        OptState.WRONG -> Triple(pal.badBg, K.Red, K.Red)
        OptState.DONE -> Triple(MaterialTheme.colorScheme.background, pal.border, pal.border)
    }
    Chunky(modifier, color = bg, shadow = br, border = br, depth = 3.dp, enabled = enabled && state != OptState.DONE, onClick = onClick) {
        Text(text, Modifier.padding(horizontal = 14.dp, vertical = 14.dp), color = fg, fontSize = fontSize.sp, fontWeight = FontWeight.SemiBold)
    }
}

enum class OptState { NORMAL, SELECTED, RIGHT, WRONG, DONE }

/** Word tile for sentence building. */
@Composable
fun WordTile(text: String, used: Boolean, onClick: () -> Unit) {
    val pal = LocalPalette.current
    if (used) {
        Box(
            Modifier.padding(bottom = 3.dp).clip(RoundedCornerShape(12.dp)).background(pal.border)
                .padding(horizontal = 12.dp, vertical = 9.dp),
        ) { Text(text, color = Color.Transparent, fontSize = 17.sp) }
        return
    }
    Chunky(color = MaterialTheme.colorScheme.background, shadow = pal.border, border = pal.border, depth = 3.dp, shape = RoundedCornerShape(12.dp), onClick = onClick) {
        Text(text, Modifier.padding(horizontal = 12.dp, vertical = 9.dp), fontSize = 17.sp, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
fun SpeakerButton(size: Dp = 56.dp, color: Color = K.Blue, onClick: () -> Unit) {
    Chunky(Modifier.size(size), color = color, shape = RoundedCornerShape(14.dp), onClick = onClick) {
        Icon(Icons.AutoMirrored.Filled.VolumeUp, "Slušaj", tint = Color.White, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun ProgressPill(progress: Float, modifier: Modifier = Modifier, color: Color = K.Green) {
    val pal = LocalPalette.current
    Box(modifier.height(16.dp).clip(CircleShape).background(pal.border)) {
        Box(
            Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(16.dp).clip(CircleShape).background(color),
        ) {
            Box(Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp).fillMaxWidth().height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f)))
        }
    }
}

@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val pal = LocalPalette.current
    Column(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(16.dp))
            .border(2.dp, pal.border, RoundedCornerShape(16.dp)).padding(16.dp),
        content = content,
    )
}

@Composable
fun SectionTitle(text: String) {
    Text(text, Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 4.dp), style = MaterialTheme.typography.titleMedium)
}

@Composable
fun TopTitle(text: String, onBack: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            Text("✕", Modifier.clip(CircleShape).clickable(onClick = onBack).padding(12.dp), fontSize = 20.sp, color = LocalPalette.current.dim)
        } else Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.titleLarge)
    }
}
