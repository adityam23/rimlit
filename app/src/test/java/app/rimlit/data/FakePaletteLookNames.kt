package app.rimlit.data

/**
 * App labels from [labels] (else the package name), counting the lookups in [labelLookups], and look names as the
 * app's English strings make them.
 */
internal class FakePaletteLookNames(var labels: Map<String, String> = emptyMap()) : PaletteLookNames {
    var labelLookups = 0
        private set

    override fun label(packageName: String): String {
        labelLookups++
        return labels[packageName] ?: packageName
    }

    override fun lookName(label: String, others: Int): String =
        if (others == 0) "$label colors" else "$label + $others colors"
}
