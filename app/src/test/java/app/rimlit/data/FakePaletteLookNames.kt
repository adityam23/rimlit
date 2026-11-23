package app.rimlit.data

/**
 * App labels from [labels] (else the package name), counting the lookups in [labelLookups], and look names as the
 * app's English strings make them. A lookup for a package in [throwing] throws its exception, as a dying system does;
 * the failures reported are kept in [failures].
 */
internal class FakePaletteLookNames(
    var labels: Map<String, String> = emptyMap(),
    private val throwing: Map<String, Throwable> = emptyMap(),
) : PaletteLookNames {
    var labelLookups = 0
        private set
    val failures = mutableListOf<Pair<String, Exception>>()

    override fun label(packageName: String): String {
        labelLookups++
        throwing[packageName]?.let { throw it }
        return labels[packageName] ?: packageName
    }

    override fun labelFailed(packageName: String, failure: Exception) {
        failures += packageName to failure
    }

    override fun lookName(label: String, others: Int): String =
        if (others == 0) "$label colors" else "$label + $others colors"
}
