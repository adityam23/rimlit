package app.rimlit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import app.rimlit.R
import app.rimlit.model.MAX_PALETTE_COLORS
import app.rimlit.model.hueOf
import app.rimlit.model.vividColor

private val PresetColors = listOf(
    0xFF00BCD4, 0xFF536DFE, 0xFFE040FB, 0xFFFF4081, 0xFFD50000, 0xFFFF6D00,
    0xFFFFD600, 0xFF00C853, 0xFF1DE9B6, 0xFF2979FF, 0xFFFFFFFF, 0xFFB0BEC5,
).map { it.toInt() }

/**
 * Edits a 1–[MAX_PALETTE_COLORS] color palette: one color = solid, more = gradient.
 * Tap a slot to select it, then pick a preset or drag the hue slider to recolor it.
 *
 * [onPreview] fires for every change (in-memory, instant); [onCommit] once per finished edit — taps
 * commit immediately, the hue slider commits on release.
 */
@Composable
fun PaletteEditor(
    colors: List<Int>,
    onPreview: (List<Int>) -> Unit,
    onCommit: (List<Int>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by remember { mutableIntStateOf(0) }
    val slot = selected.coerceIn(colors.indices)

    fun edited(argb: Int) = colors.toMutableList().also { it[slot] = argb }

    fun commit(newColors: List<Int>) {
        onPreview(newColors)
        onCommit(newColors)
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GradientBar(colors)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            colors.forEachIndexed { index, argb ->
                Swatch(argb, selected = index == slot, onClick = { selected = index })
            }
            if (colors.size < MAX_PALETTE_COLORS) {
                IconButton(onClick = {
                    commit(colors + shiftHue(colors.last(), 40f))
                    selected = colors.size
                }) {
                    Icon(Icons.Filled.Add, stringResource(R.string.palette_add))
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            if (colors.size > 1) {
                IconButton(onClick = {
                    commit(colors.filterIndexed { index, _ -> index != slot })
                    selected = (slot - 1).coerceAtLeast(0)
                }) {
                    Icon(Icons.Filled.Delete, stringResource(R.string.palette_remove))
                }
            }
        }
        PresetColors.chunked(6).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                row.forEach { argb -> Swatch(argb, selected = false, onClick = { commit(edited(argb)) }) }
            }
        }
        Text(
            stringResource(R.string.palette_hue),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CommitSlider(
            value = hueOf(colors[slot]),
            valueRange = 0f..359f,
            onPreview = { onPreview(edited(vividColor(it))) },
            onCommit = { onCommit(edited(vividColor(it))) },
        )
    }
}

/** Compact read-only rendering of a palette (solid fill or gradient). */
@Composable
fun GradientBar(colors: List<Int>, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(20.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(paletteBrush(colors)),
    )
}

@Composable
private fun Swatch(argb: Int, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color(argb))
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
    )
}

/**
 * [colors] as a brush: a left-to-right gradient, or with [sweep] a sweep that closes back to the first
 * color so the seam is invisible. A single color is a solid fill.
 */
fun paletteBrush(colors: List<Int>, sweep: Boolean = false): Brush {
    val stops = colors.map(::Color)
    return when {
        stops.size == 1 -> SolidColor(stops[0])
        sweep -> Brush.sweepGradient(stops + stops.first())
        else -> Brush.horizontalGradient(stops)
    }
}

private fun shiftHue(argb: Int, degrees: Float): Int {
    val hsl = FloatArray(3).also { ColorUtils.colorToHSL(argb, it) }
    // Greys have no hue to shift; give them a vivid one so the new slot is visibly distinct.
    if (hsl[1] < 0.2f) return vividColor(hsl[0] + degrees)
    hsl[0] = (hsl[0] + degrees) % 360f
    return ColorUtils.HSLToColor(hsl)
}
