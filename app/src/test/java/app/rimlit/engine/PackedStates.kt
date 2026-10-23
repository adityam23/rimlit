package app.rimlit.engine

import app.rimlit.model.GlowSettings
import java.lang.reflect.Modifier

/**
 * Packs a [GlowState] into a `Long`, exactly: each field is interned to an index by equality, so two states share a
 * code if and only if they are equal. A field that outgrows its bits, or a [GlowState] field this codec doesn't
 * encode, fails loudly instead of silently merging distinct states.
 */
internal class StateCodec {

    private val settings = Interner<GlowSettings>(bits = 4)
    private val environments = Interner<Environment>(bits = 4)
    private val keySets = Interner<Set<String>>(bits = 4)
    private val pendings = Interner<Pending?>(bits = 8)
    private val glows = Interner<Glow?>(bits = 20)

    init {
        val fields = GlowState::class.java.declaredFields.count { !Modifier.isStatic(it.modifiers) }
        check(fields == ENCODED_FIELDS) { "GlowState has $fields fields but the codec encodes $ENCODED_FIELDS" }
        val bits = settings.bits + environments.bits + 2 * keySets.bits + pendings.bits + glows.bits +
            Timer.entries.size + 2
        check(bits < Long.SIZE_BITS) { "$bits bits don't fit a non-negative Long" }
    }

    fun encode(state: GlowState): Long {
        var code = 0L
        var shift = 0
        fun put(value: Int, bits: Int) {
            code = code or (value.toLong() shl shift)
            shift += bits
        }
        put(settings.id(state.settings), settings.bits)
        put(environments.id(state.environment), environments.bits)
        put(keySets.id(state.activeKeys), keySets.bits)
        put(keySets.id(state.ringingKeys), keySets.bits)
        put(pendings.id(state.pending), pendings.bits)
        put(glows.id(state.glow), glows.bits)
        put(state.armed.fold(0) { mask, timer -> mask or (1 shl timer.ordinal) }, Timer.entries.size)
        put(if (state.accessibilityOverlay) 1 else 0, 1)
        put(if (state.fullScreenIntentReported) 1 else 0, 1)
        return code
    }

    private class Interner<T>(val bits: Int) {
        private val ids = HashMap<T, Int>()

        fun id(value: T): Int = ids.getOrPut(value) { ids.size }
            .also { check(it < 1 shl bits) { "more than ${1 shl bits} distinct values: $value" } }
    }

    private companion object {
        /**
         * settings, environment, accessibilityOverlay, activeKeys, ringingKeys, pending, glow, armed,
         * fullScreenIntentReported.
         */
        const val ENCODED_FIELDS = 9
    }
}

/** An open-addressing set of non-negative longs: about 16 bytes per element, where a boxed `HashSet` takes 50+. */
internal class LongSet {

    private var keys = LongArray(INITIAL_CAPACITY).apply { fill(EMPTY) }
    var size = 0
        private set

    /** Adds [key]; `false` if it was already present. */
    fun add(key: Long): Boolean {
        require(key >= 0) { "only non-negative keys: $key" }
        if (size * 2 >= keys.size) grow()
        return insert(keys, key).also { if (it) size++ }
    }

    private fun insert(table: LongArray, key: Long): Boolean {
        val mask = table.size - 1
        var slot = mix(key) and mask
        while (true) {
            when (table[slot]) {
                EMPTY -> {
                    table[slot] = key
                    return true
                }

                key -> return false

                else -> slot = (slot + 1) and mask
            }
        }
    }

    private fun grow() {
        val bigger = LongArray(keys.size * 2).apply { fill(EMPTY) }
        for (key in keys) if (key != EMPTY) insert(bigger, key)
        keys = bigger
    }

    private fun mix(key: Long): Int {
        val h = key * -0x61c8864680b583ebL
        return (h xor (h ushr 32)).toInt()
    }

    private companion object {
        const val EMPTY = -1L
        const val INITIAL_CAPACITY = 1 shl 16
    }
}

/** A growable list of longs, without boxing. */
internal class LongList {

    private var values = LongArray(INITIAL_CAPACITY)
    var size = 0
        private set

    operator fun get(index: Int): Long {
        require(index < size) { "index $index out of $size" }
        return values[index]
    }

    fun add(value: Long) {
        if (size == values.size) values = values.copyOf(size * 2)
        values[size++] = value
    }

    private companion object {
        const val INITIAL_CAPACITY = 1 shl 16
    }
}
