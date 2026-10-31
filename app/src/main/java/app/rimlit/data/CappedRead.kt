package app.rimlit.data

import java.io.ByteArrayOutputStream
import java.io.InputStream

private const val COPY_BUFFER_BYTES = 8 * 1024

/**
 * All of this stream's remaining bytes, read in [COPY_BUFFER_BYTES] chunks; `null` once more than [cap] bytes have
 * been read, so a caller can refuse an oversized, untrusted stream without reading all of it. Shared by
 * `LookArchive.kt` (which throws on overflow) and `ParticleImageStore.kt` (which treats it as an unreadable image).
 */
internal fun InputStream.readCapped(cap: Int): ByteArray? {
    val out = ByteArrayOutputStream()
    val chunk = ByteArray(COPY_BUFFER_BYTES)
    while (true) {
        val read = read(chunk)
        if (read < 0) return out.toByteArray()
        if (out.size() + read > cap) return null
        out.write(chunk, 0, read)
    }
}
