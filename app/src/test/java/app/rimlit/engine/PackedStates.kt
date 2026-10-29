package app.rimlit.engine

import app.rimlit.model.GlowSettings
import java.lang.reflect.Modifier
import java.util.EnumSet
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReferenceArray

/**
 * Packs a [GlowState] into a `Long` and back, exactly: each field is interned to an index by equality, so two states
 * share a code if and only if they are equal. A field that outgrows its bits, or a [GlowState] field this codec
 * doesn't encode, fails loudly instead of silently merging distinct states. Thread-safe; a code decodes on any thread
 * that received it through a happens-before edge from the one that encoded it.
 */
internal class StateCodec {

    private val settings = Interner<GlowSettings>(bits = 4)
    private val environments = Interner<Environment>(bits = 5)
    private val keySets = Interner<Set<String>>(bits = 4)

    /** Every armed-timer set by its bit mask, so decoding shares them. */
    private val timerSets = List(1 shl Timer.entries.size) { mask ->
        Timer.entries.filterTo(EnumSet.noneOf(Timer::class.java)) { mask and (1 shl it.ordinal) != 0 }
    }
    private val pendings = Interner<Pending>(bits = 8)
    private val glows = Interner<Glow>(bits = 20)

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
        put(if (state.wakeScreenDeniedReported) 1 else 0, 1)
        return code
    }

    fun decode(code: Long): GlowState {
        var rest = code
        fun take(bits: Int): Int = (rest and ((1L shl bits) - 1)).toInt().also { rest = rest ushr bits }
        val settings = settings.value(take(settings.bits))
        val environment = environments.value(take(environments.bits))
        val activeKeys = keySets.value(take(keySets.bits))
        val ringingKeys = keySets.value(take(keySets.bits))
        val pending = pendings.valueOrNull(take(pendings.bits))
        val glow = glows.valueOrNull(take(glows.bits))
        val armed = timerSets[take(Timer.entries.size)]
        val accessibilityOverlay = take(1) == 1
        val wakeScreenDeniedReported = take(1) == 1
        return GlowState(
            settings = settings,
            environment = environment,
            accessibilityOverlay = accessibilityOverlay,
            activeKeys = activeKeys,
            ringingKeys = ringingKeys,
            pending = pending,
            glow = glow,
            armed = armed,
            wakeScreenDeniedReported = wakeScreenDeniedReported,
        )
    }

    /** Numbers distinct values from 1 in first-seen order, and back; `null` is 0. */
    private class Interner<T : Any>(val bits: Int) {
        private val ids = ConcurrentHashMap<T, Int>()
        private val values = AtomicReferenceArray<T>(1 shl bits)
        private val last = AtomicInteger()

        fun id(value: T?): Int {
            value ?: return 0
            return ids[value] ?: ids.computeIfAbsent(value) {
                last.incrementAndGet().also { id ->
                    check(id < 1 shl bits) { "more than ${(1 shl bits) - 1} distinct values: $value" }
                    values.set(id, value)
                }
            }
        }

        fun valueOrNull(id: Int): T? = if (id == 0) null else checkNotNull(values.get(id)) { "unknown id $id" }

        fun value(id: Int): T = checkNotNull(valueOrNull(id)) { "null where a value is required" }
    }

    private companion object {
        /**
         * settings, environment, accessibilityOverlay, activeKeys, ringingKeys, pending, glow, armed,
         * wakeScreenDeniedReported.
         */
        const val ENCODED_FIELDS = 9
    }
}

/**
 * A thread-safe set of non-negative longs: [LongSet] shards, each behind its own lock, picked by the key's high hash
 * bits (the shards' own slots use the low ones).
 */
internal class ConcurrentLongSet(private val shardBits: Int = 8) {

    private val shards = Array(1 shl shardBits) { LongSet(initialCapacity = 1 shl 10) }

    val size: Int get() = shards.sumOf { synchronized(it) { it.size } }

    /** Adds [key]; `false` if it was already present. */
    fun add(key: Long): Boolean {
        val shard = shards[((key * SHARD_MIX) ushr (Long.SIZE_BITS - shardBits)).toInt()]
        return synchronized(shard) { shard.add(key) }
    }

    private companion object {
        const val SHARD_MIX = 0x2545F4914F6CDD1DL
    }
}

/**
 * An open-addressing set of non-negative longs, filled up to three quarters: 11–21 bytes per element, where a boxed
 * `HashSet` takes 50+. Linear probing stays short at that load, and most lookups find a state already visited.
 */
internal class LongSet(initialCapacity: Int) {

    init {
        require(initialCapacity > 0 && initialCapacity and (initialCapacity - 1) == 0) { "a power of two" }
    }

    private var keys = LongArray(initialCapacity).apply { fill(EMPTY) }
    var size = 0
        private set

    /** Adds [key]; `false` if it was already present. */
    fun add(key: Long): Boolean {
        require(key >= 0) { "only non-negative keys: $key" }
        if (size * 4 >= keys.size * 3) grow()
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
    }
}

/**
 * A growable list of longs, without boxing. It doubles up to [BLOCK] elements, then grows a block at a time, so a
 * list of tens of millions is never copied whole and wastes at most one block.
 */
internal class LongList(initialCapacity: Int = 16) {

    /** Full blocks of [BLOCK] elements, then [last], which holds the rest. */
    private val blocks = ArrayList<LongArray>()
    private var last = LongArray(initialCapacity.coerceIn(1, BLOCK))
    private var lastSize = 0

    val size: Int get() = blocks.size * BLOCK + lastSize

    operator fun get(index: Int): Long {
        require(index in 0 until size) { "index $index out of $size" }
        val block = index / BLOCK
        return (if (block < blocks.size) blocks[block] else last)[index % BLOCK]
    }

    fun add(value: Long) {
        if (lastSize == last.size) {
            if (last.size < BLOCK) {
                last = last.copyOf(minOf(last.size * 2, BLOCK))
            } else {
                blocks += last
                last = LongArray(BLOCK)
                lastSize = 0
            }
        }
        last[lastSize++] = value
    }

    fun toArray(): LongArray {
        val array = LongArray(size)
        blocks.forEachIndexed { i, block -> block.copyInto(array, destinationOffset = i * BLOCK) }
        last.copyInto(array, destinationOffset = blocks.size * BLOCK, endIndex = lastSize)
        return array
    }

    private companion object {
        /** 8 MB: small enough to waste, large enough that the block list stays short. */
        const val BLOCK = 1 shl 20
    }
}
