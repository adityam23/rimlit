package app.rimlit.data

/** App labels from [labels] (else the package name), and look names as the app's English strings make them. */
internal class FakePaletteLookNames(private val labels: Map<String, String> = emptyMap()) : PaletteLookNames {
    override fun label(packageName: String): String = labels[packageName] ?: packageName

    override fun lookName(labels: List<String>): String =
        if (labels.size == 1) "${labels.first()} colors" else "${labels.first()} + ${labels.size - 1} colors"
}
