package app.rimlit.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.rimlit.MainViewModel
import app.rimlit.R
import app.rimlit.data.LOOK_DOCUMENT_MIME
import app.rimlit.data.LOOK_FILE_MIME
import app.rimlit.data.LOOK_FILE_NAME
import app.rimlit.data.LookArchive
import app.rimlit.data.LookArchiveException.Problem
import app.rimlit.data.LookFileResult
import app.rimlit.model.GlowSettings
import app.rimlit.model.Look
import app.rimlit.model.MAX_LOOKS
import app.rimlit.ui.components.CheckboxCard

/**
 * The look picker's export, share and import, from [rememberLookSharing]; [LookSharingDialogs] shows what they ask.
 * Export and share first ask which looks to send; import opens a look file and, when its looks don't all fit, asks
 * which to add ([pending]). A toast says how each ended.
 */
internal class LookSharing(
    private val asking: MutableState<Sending?>,
    /** A look file read for import whose looks don't all fit, while the user picks which to add. */
    val pending: LookArchive?,
    private val onSend: (Sending, Set<String>) -> Unit,
    private val onOpen: () -> Unit,
    val onImportPicked: (Set<String>) -> Unit,
    val onCancelImport: () -> Unit,
) {
    /** Where the looks picked go: a file saved where the user says, or the share sheet. */
    enum class Sending { EXPORT, SHARE }

    /** Where the looks being picked go, while the dialog asking which is up. */
    val sending: Sending? get() = asking.value

    fun export() {
        asking.value = Sending.EXPORT
    }

    fun share() {
        asking.value = Sending.SHARE
    }

    /** Opens a look file to import, through the system file picker. */
    fun import() = onOpen()

    /** Sends the looks [ids] where [sending] says. */
    fun send(ids: Set<String>) {
        asking.value?.let { onSend(it, ids) }
        asking.value = null
    }

    fun dismiss() {
        asking.value = null
    }
}

/** The look picker's export, share and import, carried out by [viewModel]. */
@Composable
internal fun rememberLookSharing(viewModel: MainViewModel): LookSharing {
    val context = LocalContext.current
    // The application's for the toast: the result may arrive after this screen is gone.
    val report = { result: LookFileResult -> context.applicationContext.report(result) }
    // The looks picked for export while the system asks where to save them; saved, since the activity may be
    // recreated meanwhile, and the document then already exists, waiting to be written.
    var exporting by rememberSaveable { mutableStateOf(emptySet<String>()) }
    val createDocument = rememberLauncherForActivityResult(CreateDocument(LOOK_DOCUMENT_MIME)) { uri ->
        if (uri != null && exporting.isNotEmpty()) viewModel.exportLooks(exporting, uri, report)
    }
    val openDocument = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        uri?.let { viewModel.importLooks(it, report) }
    }
    val pending by viewModel.pendingImport.collectAsStateWithLifecycle()
    return LookSharing(
        asking = rememberSaveable { mutableStateOf(null) },
        pending = pending,
        onSend = { sending, ids ->
            when (sending) {
                LookSharing.Sending.EXPORT -> {
                    exporting = ids
                    createDocument.launch(LOOK_FILE_NAME)
                }

                LookSharing.Sending.SHARE -> viewModel.shareLooks(ids) { result ->
                    // The application's, as for the toast: the file may be ready after this activity is gone.
                    if (result is LookFileResult.Shareable) {
                        context.applicationContext.share(result.uri)
                    } else {
                        report(result)
                    }
                }
            }
        },
        onOpen = { openDocument.launch(arrayOf(LOOK_FILE_MIME, LOOK_DOCUMENT_MIME)) },
        onImportPicked = { ids -> viewModel.importPicked(ids, report) },
        onCancelImport = viewModel::cancelImport,
    )
}

/** The dialogs [sharing] asks with, for the looks in [settings]: which to send, or which of an import to add. */
@Composable
internal fun LookSharingDialogs(sharing: LookSharing, settings: GlowSettings) {
    sharing.sending?.let { sending ->
        val export = sending == LookSharing.Sending.EXPORT
        LookChecklistDialog(
            title = stringResource(if (export) R.string.looks_export_title else R.string.looks_share_title),
            looks = settings.looks,
            max = settings.looks.size,
            confirmLabel = stringResource(if (export) R.string.looks_export else R.string.looks_share),
            onConfirm = sharing::send,
            onDismiss = sharing::dismiss,
        )
    }
    sharing.pending?.let { archive ->
        val room = (MAX_LOOKS - settings.looks.size).coerceAtLeast(0)
        LookChecklistDialog(
            title = stringResource(R.string.looks_import_title),
            text = pluralStringResource(R.plurals.looks_import_pick, room, room),
            looks = archive.looks,
            max = room,
            confirmLabel = stringResource(R.string.looks_import),
            onConfirm = sharing.onImportPicked,
            onDismiss = sharing.onCancelImport,
        )
    }
}

/** Picks up to [max] of [looks], the first [max] to start with; [onConfirm] gets the ids picked, at least one. */
@Composable
private fun LookChecklistDialog(
    title: String,
    looks: List<Look>,
    max: Int,
    confirmLabel: String,
    onConfirm: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
    text: String? = null,
) {
    var picked by remember(looks) { mutableStateOf(looks.take(max).map { it.id }.toSet()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                text?.let { Text(it) }
                for (look in looks) {
                    val checked = look.id in picked
                    CheckboxCard(
                        title = look.name,
                        checked = checked,
                        onCheckedChange = { picked = if (it) picked + look.id else picked - look.id },
                        enabled = checked || picked.size < max,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(picked) }, enabled = picked.isNotEmpty()) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

/**
 * Opens the share sheet for the look file at [uri], granting the app it is sent to read access to it alone. The URI
 * is also the intent's clip data, which is what carries the grant and lets the sheet show the file. From any context:
 * the sheet opens in its own task.
 */
private fun Context.share(uri: Uri) {
    val send = Intent(Intent.ACTION_SEND)
        .setType(LOOK_FILE_MIME)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    send.clipData = ClipData.newRawUri(null, uri)
    val chooser = Intent.createChooser(send, getString(R.string.looks_share_title))
    startActivity(chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Says how [result] ended, in a toast; nothing for a file ready to share, which opens the share sheet instead. */
private fun Context.report(result: LookFileResult) {
    val text = when (result) {
        is LookFileResult.Exported -> resources.getQuantityString(R.plurals.looks_exported, result.looks, result.looks)
        is LookFileResult.Imported -> resources.getQuantityString(R.plurals.looks_imported, result.looks, result.looks)
        is LookFileResult.Shareable -> return
        LookFileResult.NoRoom -> resources.getQuantityString(R.plurals.looks_import_no_room, MAX_LOOKS, MAX_LOOKS)
        is LookFileResult.Failed -> getString(result.problem.messageRes)
    }
    Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
}

/** Why a look file was refused, or for `null`, that it couldn't be read or written. */
private val Problem?.messageRes
    get() = when (this) {
        Problem.INVALID -> R.string.looks_file_invalid
        Problem.TOO_LARGE -> R.string.looks_file_too_large
        Problem.NEWER_VERSION -> R.string.looks_file_newer
        null -> R.string.looks_file_failed
    }
