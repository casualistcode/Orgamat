package de.tastenhalter.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Großer, ruhiger Knopf wie „Zurück“/„Weiter“ in Stimmlicht. */
@Composable
fun SoftButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    emphasized: Boolean = false,
    leading: ImageVector? = null,
    trailing: ImageVector? = null,
    height: Dp = 64.dp,
    tint: Color = Palette.Text,
) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier
            .height(height)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(shape)
            .background(if (emphasized) Palette.SurfaceStrong else Palette.Surface)
            .border(1.dp, if (emphasized) Palette.LineStrong else Palette.Line, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Icon(leading, null, tint = tint.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium, color = tint, maxLines = 1)
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            Icon(trailing, null, tint = tint.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
        }
    }
}

/** Runder Knopf mit Symbol; [filled] = kräftig in [color]. */
@Composable
fun RoundButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    filled: Boolean = false,
    color: Color = Palette.Text,
    enabled: Boolean = true,
) {
    Box(
        modifier
            .size(size)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(CircleShape)
            .background(if (filled) color else Palette.Surface)
            .border(1.dp, if (filled) color else Palette.Line, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription,
            tint = if (filled) Palette.Ground else color,
            modifier = Modifier.size(size * 0.45f),
        )
    }
}

/** Kleine Auswahl-Pille. */
@Composable
fun Pill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Palette.Orange,
) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .heightIn(min = 38.dp)
            .clip(shape)
            .background(if (selected) color.copy(alpha = 0.16f) else Color.Transparent)
            .border(1.dp, if (selected) color.copy(alpha = 0.6f) else Palette.Line, shape)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) Palette.Text else Palette.Muted,
            maxLines = 1,
        )
    }
}

/** Kleine Überschrift in Versalien, wie „SCHRITT 3/12“ in Stimmlicht. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = Palette.Muted) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = modifier,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Ruhige Fläche mit dünnem Rand. */
@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Palette.Surface)
            .border(1.dp, Palette.Line, shape),
        content = content,
    )
}

/** Eine Zeile im Einstellungsmenü. */
@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Palette.Text)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
            }
        }
        trailing()
    }
}

@Composable
fun RowDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 18.dp)
            .height(1.dp)
            .background(Palette.Line)
    )
}

/** Kompakter Wertewähler: − Wert + */
@Composable
fun MiniStepper(
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    minusEnabled: Boolean = true,
    plusEnabled: Boolean = true,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RoundButton(Icons.Filled.Remove, "weniger", onMinus, size = 40.dp, enabled = minusEnabled)
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = Palette.Text,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.widthIn(min = 92.dp).padding(horizontal = 6.dp),
        )
        RoundButton(Icons.Filled.Add, "mehr", onPlus, size = 40.dp, enabled = plusEnabled)
    }
}

/** Schlanker Schieberegler: dünne Linie, runder Griff. [value] liegt zwischen 0 und 1. */
@Composable
fun ThinSlider(
    value: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onFinished: () -> Unit = {},
    color: Color = Palette.Orange,
) {
    val change by rememberUpdatedState(onChange)
    val finished by rememberUpdatedState(onFinished)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(32.dp)
            .pointerInput(Unit) {
                fun fraction(x: Float) = ((x - 8.dp.toPx()) / (size.width - 16.dp.toPx())).coerceIn(0f, 1f)
                awaitEachGesture {
                    val down = awaitFirstDown()
                    change(fraction(down.position.x))
                    drag(down.id) { c ->
                        change(fraction(c.position.x))
                        c.consume()
                    }
                    finished()
                }
            }
    ) {
        val pad = 8.dp.toPx()
        val y = size.height / 2
        val x = pad + (size.width - 2 * pad) * value.coerceIn(0f, 1f)
        drawLine(Palette.LineStrong, Offset(pad, y), Offset(size.width - pad, y), 3.dp.toPx(), StrokeCap.Round)
        drawLine(color, Offset(pad, y), Offset(x, y), 3.dp.toPx(), StrokeCap.Round)
        drawCircle(Palette.Text, 7.dp.toPx(), Offset(x, y))
    }
}

@Composable
fun StatusDot(color: Color, size: Dp = 8.dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
    )
}

/** Hinweisleiste. */
@Composable
fun Banner(text: String, color: Color, modifier: Modifier = Modifier, onDismiss: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.45f), shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Palette.Text, modifier = Modifier.weight(1f))
        if (onDismiss != null) {
            Text(
                "OK",
                style = MaterialTheme.typography.labelLarge,
                color = color,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}
