package app.rimlit.data

import app.rimlit.model.AutoColors
import app.rimlit.model.GlowPalette
import app.rimlit.model.GlowSettings.Ranges
import app.rimlit.model.Look
import app.rimlit.model.MAX_LOOKS
import app.rimlit.model.MAX_PALETTE_COLORS
import app.rimlit.model.ParticleImage
import app.rimlit.model.ParticleMotion
import app.rimlit.model.ParticleSpec
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

/**
 * The palette of the valid colors among [colors] (8-digit ARGB hex, e.g. `ff00bcd4`), the first [MAX_PALETTE_COLORS]
 * of them; `null` when there is none, so an invalid stored palette is dropped, never thrown.
 */
internal fun decodePalette(colors: Sequence<String>): GlowPalette.Colors? =
    colors.mapNotNull(PaletteJson::decodeColor).take(MAX_PALETTE_COLORS).toList().ifEmpty { null }
        ?.let(GlowPalette::Colors)

internal inline fun <reified E : Enum<E>> String?.toEnumOr(default: E): E = toEnumOrNull<E>() ?: default

/** The value of [E] named this; `null` when there is none, for a value with no fallback. */
internal inline fun <reified E : Enum<E>> String?.toEnumOrNull(): E? =
    this?.let { name -> enumValues<E>().firstOrNull { it.name == name } }

/** [parse]'s result, or `null` when the JSON is malformed: the caller falls back. */
private inline fun <T> parsedOrNull(parse: () -> T): T? = try {
    parse()
} catch (_: JSONException) {
    null
}

private fun JSONObject.string(key: String): String? = opt(key) as? String

private fun JSONObject.number(key: String): Number? = opt(key) as? Number

/** The finite number at [key] clamped to [range], or [default] when there is none. */
private fun JSONObject.float(key: String, default: Float, range: ClosedFloatingPointRange<Float>): Float =
    (number(key)?.toFloat()?.takeIf { it.isFinite() } ?: default).coerceIn(range)

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

    /** An object, absent for no particles. */
    private const val PARTICLES = "particles"

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
        .apply { look.particles?.let { put(PARTICLES, ParticleJson.encode(it)) } }

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
            particles = obj.optJSONObject(PARTICLES)?.let(ParticleJson::decode),
        )
    }
}

private object ParticleJson {
    private const val IMAGE = "image"
    private const val MOTION = "motion"
    private const val SIZE_DP = "sizeDp"
    private const val SPEED = "speed"
    private const val AMOUNT = "amount"
    private const val ROTATION = "rotationDegPerSecond"
    private const val TINT = "tint"

    fun encode(particles: ParticleSpec): JSONObject = JSONObject()
        .put(IMAGE, particles.image.name)
        .put(MOTION, particles.motion.name)
        .put(SIZE_DP, particles.sizeDp.toDouble())
        .put(SPEED, particles.speed.toDouble())
        .put(AMOUNT, particles.amount.toDouble())
        .put(ROTATION, particles.rotationDegPerSecond.toDouble())
        .put(TINT, particles.tint.name)

    /** The particles [obj] holds, or `null` without a known image and motion, which have no sensible fallback. */
    fun decode(obj: JSONObject): ParticleSpec? {
        val image = obj.string(IMAGE).toEnumOrNull<ParticleImage>() ?: return null
        val motion = obj.string(MOTION).toEnumOrNull<ParticleMotion>() ?: return null
        val defaults = ParticleSpec(image, motion)
        return defaults.copy(
            sizeDp = obj.float(SIZE_DP, defaults.sizeDp, Ranges.particleSizeDp),
            speed = obj.float(SPEED, defaults.speed, Ranges.particleSpeed),
            amount = obj.float(AMOUNT, defaults.amount, Ranges.particleAmount),
            rotationDegPerSecond = obj.float(ROTATION, defaults.rotationDegPerSecond, Ranges.particleRotation),
            tint = obj.string(TINT).toEnumOr(defaults.tint),
        )
    }
}

private object RuleJson {
    private const val LOOK = "look"
    private const val PALETTE = "palette"

    fun encode(rule: Rule): JSONObject = when (rule) {
        is Rule.UseLook -> JSONObject().put(LOOK, rule.lookId)
        is Rule.UsePalette -> JSONObject().put(PALETTE, PaletteJson.encode(rule.palette))
    }

    /** The rule [obj] holds: a look by id, or else a palette; `null` when it holds neither. */
    fun decode(obj: JSONObject): Rule? {
        obj.string(LOOK)?.takeIf { it.isNotEmpty() }?.let { return Rule.UseLook(it) }
        return obj.optJSONArray(PALETTE)?.let(PaletteJson::decode)?.let(Rule::UsePalette)
    }
}

/** A palette is a JSON array of colors as 8-digit ARGB hex. */
private object PaletteJson {
    fun encode(palette: GlowPalette.Colors) =
        JSONArray().apply { palette.argb.forEach { put("%08x".format(Locale.ROOT, it)) } }

    fun decode(array: JSONArray): GlowPalette.Colors? =
        decodePalette((0 until array.length()).asSequence().mapNotNull { array.opt(it) as? String })

    /** The color [hex] encodes, or `null` when it isn't a hex number in the ARGB range. */
    fun decodeColor(hex: String): Int? = hex.toLongOrNull(16)?.takeIf { it in 0..0xFFFFFFFFL }?.toInt()
}
