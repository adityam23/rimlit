package app.rimlit.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.rimlit.MainViewModel
import app.rimlit.R
import app.rimlit.data.ParticleImageStore
import app.rimlit.model.Look
import app.rimlit.model.MAX_PARTICLE_IMAGES
import app.rimlit.model.ParticlePicture

/**
 * Picks images to add to [look]'s particles, as many as still fit, and has [viewModel] import them; a toast says how
 * many couldn't be added. The persisted look then switches to them, and the screen's draft follows it.
 */
@Composable
internal fun rememberAddParticleImages(viewModel: MainViewModel, look: Look): () -> Unit {
    // The application's: the import may outlive this screen.
    val context = LocalContext.current.applicationContext
    val own = (look.particles?.picture as? ParticlePicture.Custom)?.ids?.size ?: 0
    return rememberParticleImagePicker(MAX_PARTICLE_IMAGES - own) { uris ->
        viewModel.addParticleImages(look.id, uris) { rejected ->
            if (rejected == 0) return@addParticleImages
            val text = context.resources.getQuantityString(R.plurals.particle_images_rejected, rejected, rejected)
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        }
    }
}

/**
 * Opens the system photo picker for up to [remaining] still images (no storage permission: the picker grants only
 * what the user picks), handing them to [onPick]; the picker shows only when at least one more fits.
 */
@Composable
private fun rememberParticleImagePicker(remaining: Int, onPick: (List<Uri>) -> Unit): () -> Unit {
    // Picking several needs room for two; the last free place picks one.
    val several = remember(remaining) { PickMultipleVisualMedia(remaining.coerceAtLeast(2)) }
    val pickSeveral = rememberLauncherForActivityResult(several) { if (it.isNotEmpty()) onPick(it) }
    val pickOne = rememberLauncherForActivityResult(PickVisualMedia()) { it?.let { uri -> onPick(listOf(uri)) } }
    val images = PickVisualMediaRequest(PickVisualMedia.ImageOnly)
    return {
        when {
            remaining > 1 -> pickSeveral.launch(images)
            remaining == 1 -> pickOne.launch(images)
        }
    }
}

/**
 * A look's own particle images, [ids], each removable while another is left ([onRemove]), and a button to add more
 * while they fit ([onAdd], `null` when they don't).
 */
@Composable
internal fun ParticleImagesCard(
    ids: List<String>,
    onRemove: (String) -> Unit,
    onAdd: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val store = remember(context) { ParticleImageStore(context) }
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ids.forEachIndexed { index, id ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // A stored image is a small PNG, as the renderer reads it.
                        val image = remember(id) { store.load(id)?.asImageBitmap() }
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            image?.let {
                                Image(
                                    bitmap = it,
                                    contentDescription = stringResource(R.string.particle_image_description, index + 1),
                                    modifier = Modifier.size(56.dp),
                                )
                            }
                        }
                        IconButton(onClick = { onRemove(id) }, enabled = ids.size > 1) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.particle_image_remove, index + 1),
                            )
                        }
                    }
                }
            }
            if (onAdd != null) {
                OutlinedButton(onClick = onAdd) {
                    Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                    Text(text = stringResource(R.string.particle_images_add), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}
