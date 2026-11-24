package app.rimlit.data

import org.json.JSONArray
import org.json.JSONObject

// Tolerant reads of stored JSON values (see `SettingsJson.kt`): a value of the wrong type reads as absent.

internal fun JSONObject.string(key: String): String? = opt(key) as? String

internal fun JSONObject.number(key: String): Number? = opt(key) as? Number

/** The strings in this array, in order, skipping what isn't one. */
internal fun JSONArray.strings(): Sequence<String> = (0 until length()).asSequence().mapNotNull { opt(it) as? String }

/** The finite number at [key] clamped to [range], or [default] when there is none. */
internal fun JSONObject.float(key: String, default: Float, range: ClosedFloatingPointRange<Float>): Float =
    (number(key)?.toFloat()?.takeIf { it.isFinite() } ?: default).coerceIn(range)
