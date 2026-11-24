package app.rimlit.data

import android.net.Uri

/**
 * The particle image files as [LookLibrary] keeps them: [ParticleImageStore] on a device, a fake in tests. Imports
 * treat their input as untrusted; ids name stored images.
 */
interface ParticleImageFiles {
    /** Imports the picked image at [uri]; its id, or `null` when it can't be read or doesn't decode. */
    suspend fun import(uri: Uri): String?

    /** Imports the image [bytes] from a shared look file; its id, or `null` when it isn't one Rimlit exported. */
    suspend fun import(bytes: ByteArray): String?

    /** Whether the image [id] is stored. */
    suspend fun has(id: String): Boolean

    /** Deletes the stored images [ids] at once, whatever their grace: those an import added that nothing draws. */
    suspend fun delete(ids: Collection<String>)

    /** Deletes every stored image but those [keep] names, once past its grace ([UNUSED_IMAGE_GRACE_MS]). */
    suspend fun retainOnly(keep: Set<String>)
}
