package app.rimlit.ui.glow

/**
 * Holds the last value [build] made and rebuilds it only when its key changes, so frames and edits that don't
 * change the key reuse it (layouts, pre-blurred halos, rasterised sprites). Main thread only.
 */
internal class BuildCache<K : Any, V : Any>(private val build: (K) -> V) {
    private var key: K? = null
    private var value: V? = null

    fun get(key: K): V = value?.takeIf { this.key == key } ?: build(key).also {
        this.key = key
        value = it
    }
}
