package app.rimlit.data

import app.rimlit.model.AutoColors
import app.rimlit.model.GlowSettings.Ranges
import app.rimlit.model.Look
import app.rimlit.model.MAX_LOOKS
import app.rimlit.model.MAX_PALETTE_COLORS
import app.rimlit.model.Rule
import app.rimlit.model.isValidLookName
import app.rimlit.model.lookNameKey
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.Locale

// The settings stored as JSON (one string per collection), with the platform's org.json. Decoding is tolerant, since
// the stored string may come from an older or newer version: unknown values fall back to the defaults, numbers are
// clamped to [Ranges], and anything malformed is skipped, or the whole collection decodes as `null`.

internal fun encodeLooks(looks: List<Look>): String = JSONArray().apply {
    for (look in looks) put(LookJson.encode(look))
}.toString()

/**
 * The looks in [json], skipping malformed ones and those repeating an earlier id or name, up to [MAX_LOOKS]; `null`
 * when it is absent, malformed or holds no valid look.
 */
internal fun decodeLooks(json: String?): List<Look>? {
    val array = json?.let { parsedOrNull { JSONArray(it) } } ?: return null
    val looks = mutableListOf<Look>()
    for (i in 0 until array.length()) {
        val look = array.optJSONObject(i)?.let(LookJson::decode)
        val repeats = look != null && looks.any { it.id == look.id || lookNameKey(it.name) == lookNameKey(look.name) }
        if (look != null && !repeats && looks.size < MAX_LOOKS) looks += look
    }
    return looks.ifEmpty { null }
}

/** Rules are a JSON object by package name. */
internal fun encodeRules(rules: Map<String, Rule>): String = JSONObject().apply {
    for ((key, rule) in rules) put(key, RuleJson.encode(rule))
}.toString()

/** The rules in [json], skipping malformed ones; `null` when it is absent or malformed. */
internal fun decodeRules(json: String?): Map<String, Rule>? {
    val obj = json?.let { parsedOrNull { JSONObject(it) } } ?: return null
    return obj.keys().asSequence()
        .filter { it.isNotEmpty() }
        .mapNotNull { key -> obj.optJSONObject(key)?.let(RuleJson::decode)?.let { key to it } }
        .toMap()
}

/** The valid colors among [colors] (8-digit ARGB hex, e.g. `ff00bcd4`), at most [MAX_PALETTE_COLORS], or `null`. */
internal fun decodePalette(colors: Sequence<String>): List<Int>? =
    colors.mapNotNull(PaletteJson::decodeColor).take(MAX_PALETTE_COLORS).toList().ifEmpty { null }

internal inline fun <reified E : Enum<E>> String?.toEnumOr(default: E): E =
    this?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default

/** [parse]'s result, or `null` when the JSON is malformed: the caller falls back. */
private inline fun <T> parsedOrNull(parse: () -> T): T? = try {
    parse()
} catch (_: JSONException) {
    null
}

private fun JSONObject.string(key: String): String? = opt(key) as? String

private fun JSONObject.number(key: String): Number? = opt(key) as? Number

private object LookJson {
    private const val ID = "id"
    private const val NAME = "name"
    private const val STYLE = "style"
    private const val COLOR_MODE = "colorMode"
    private const val AUTO_COLOR_SOURCE = "autoColorSource"
    private const val AUTO_COLOR_COUNT = "autoColorCount"
    private const val CUSTOM_COLORS = "customColors"
    private const val REGION = "region"
    private const val THICKNESS_DP = "thicknessDp"
    private const val GLOW_WIDTH_DP = "glowWidthDp"
    private const val SPEED = "speed"
    private const val BRIGHTNESS = "brightness"

    private val defaults = Look()

    fun encode(look: Look): JSONObject = JSONObject()
        .put(ID, look.id)
        .put(NAME, look.name)
        .put(STYLE, look.style.name)
        .put(COLOR_MODE, look.colorMode.name)
        .put(AUTO_COLOR_SOURCE, look.autoColors.source.name)
        .put(AUTO_COLOR_COUNT, look.autoColors.count)
        .put(CUSTOM_COLORS, PaletteJson.encode(look.customColors))
        .put(REGION, look.region.name)
        .put(THICKNESS_DP, look.thicknessDp.toDouble())
        .put(GLOW_WIDTH_DP, look.glowWidthDp.toDouble())
        .put(SPEED, look.speed.toDouble())
        .put(BRIGHTNESS, look.brightness.toDouble())

    /** The look [obj] holds, or `null` without an id or a valid name (see [isValidLookName]). */
    fun decode(obj: JSONObject): Look? {
        val id = obj.string(ID)?.takeIf { it.isNotEmpty() } ?: return null
        val name = obj.string(NAME)?.trim()?.takeIf(::isValidLookName) ?: return null
        return Look(
            id = id,
            name = name,
            style = obj.string(STYLE).toEnumOr(defaults.style),
            colorMode = obj.string(COLOR_MODE).toEnumOr(defaults.colorMode),
            autoColors = AutoColors(
                source = obj.string(AUTO_COLOR_SOURCE).toEnumOr(defaults.autoColors.source),
                count = (obj.number(AUTO_COLOR_COUNT)?.toInt() ?: defaults.autoColors.count)
                    .coerceIn(Ranges.autoColorCount),
            ),
            customColors = obj.optJSONArray(CUSTOM_COLORS)?.let(PaletteJson::decode) ?: defaults.customColors,
            region = obj.string(REGION).toEnumOr(defaults.region),
            thicknessDp = obj.float(THICKNESS_DP, defaults.thicknessDp, Ranges.thicknessDp),
            glowWidthDp = obj.float(GLOW_WIDTH_DP, defaults.glowWidthDp, Ranges.glowWidthDp),
            speed = obj.float(SPEED, defaults.speed, Ranges.speed),
            brightness = obj.float(BRIGHTNESS, defaults.brightness, Ranges.brightness),
        )
    }

    /** The finite number at [key] clamped to [range], or [default] when there is none. */
    private fun JSONObject.float(key: String, default: Float, range: ClosedFloatingPointRange<Float>): Float =
        (number(key)?.toFloat()?.takeIf { it.isFinite() } ?: default).coerceIn(range)
}

private object RuleJson {
    private const val LOOK = "look"
    private const val PALETTE = "palette"

    fun encode(rule: Rule): JSONObject = when (rule) {
        is Rule.UseLook -> JSONObject().put(LOOK, rule.lookId)
        is Rule.UsePalette -> JSONObject().put(PALETTE, PaletteJson.encode(rule.colors))
    }

    /** The rule [obj] holds: a look by id, or else a palette; `null` when it holds neither. */
    fun decode(obj: JSONObject): Rule? {
        obj.string(LOOK)?.takeIf { it.isNotEmpty() }?.let { return Rule.UseLook(it) }
        return obj.optJSONArray(PALETTE)?.let(PaletteJson::decode)?.let(Rule::UsePalette)
    }
}

/** A palette is a JSON array of colors as 8-digit ARGB hex. */
private object PaletteJson {
    fun encode(colors: List<Int>) = JSONArray().apply { colors.forEach { put("%08x".format(Locale.ROOT, it)) } }

    fun decode(array: JSONArray): List<Int>? =
        decodePalette((0 until array.length()).asSequence().mapNotNull { array.opt(it) as? String })

    /** The color [hex] encodes, or `null` when it isn't a hex number in the ARGB range. */
    fun decodeColor(hex: String): Int? = hex.toLongOrNull(16)?.takeIf { it in 0..0xFFFFFFFFL }?.toInt()
}
