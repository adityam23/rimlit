package app.rimlit.data

import app.rimlit.model.particleImageIds
import java.io.IOException

/**
 * Deletes the particle image files no stored look draws, through [retainOnly] ([ParticleImageStore.retainOnly]). The
 * only way image files are cleaned up: the looks come from [SettingsRepository.stored], which fails rather than fall
 * back to defaults, so settings that couldn't be read never read as "no look draws any image".
 *
 * @throws IOException when the settings can't be read; nothing is deleted then.
 */
internal suspend fun deleteUnusedParticleImages(
    repository: SettingsRepository,
    retainOnly: suspend (keep: Set<String>) -> Unit,
) {
    retainOnly(repository.stored().particleImageIds)
}
