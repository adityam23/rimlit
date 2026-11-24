package app.rimlit.ui.setup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.rimlit.MainViewModel
import app.rimlit.R
import app.rimlit.data.toEnumOrNull
import app.rimlit.model.Premade
import app.rimlit.model.defaultLookSwitchedTo
import app.rimlit.permissions.Permissions
import app.rimlit.ui.DeviceFit
import app.rimlit.ui.components.SettingsList
import app.rimlit.ui.title

/**
 * The guided first launch, one step at a time (see [SetupStep]); back goes a step back, and on the first leaves it.
 * Finishing marks setup done and turns glowing on, then [onFinish] gets the default look's id to open in the editor,
 * when the user asked to customise it, or `null`.
 */
@Composable
fun SetupScreen(viewModel: MainViewModel, onFinish: (customiseLookId: String?) -> Unit, modifier: Modifier = Modifier) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val statuses by viewModel.permissions.collectAsStateWithLifecycle()
    val testPostable by viewModel.testPostable.collectAsStateWithLifecycle()
    var step by rememberSaveable(stateSaver = StepSaver) { mutableStateOf(SetupStep.WELCOME) }
    // The premade picked for the first look, applied on moving on; null keeps the default look as it is.
    var picked by rememberSaveable(stateSaver = PremadeSaver) { mutableStateOf<Premade?>(null) }
    var customise by rememberSaveable { mutableStateOf(false) }
    // Set once Finish is tapped, so a second tap before leaving doesn't finish twice.
    var finished by rememberSaveable { mutableStateOf(false) }
    // "Try it" taps played in Rimlit's window (TestPlay.IN_WINDOW).
    var windowPlays by remember { mutableIntStateOf(0) }
    val play = testPlay(statuses, testPostable)
    val context = LocalContext.current
    val openGrant = rememberPermissionOpener(viewModel::openPermission, viewModel::refreshPermissions)
    // The look is named after its premade, in the user's language.
    val pickedLook = picked?.let { it to it.title() }
    val goBack: () -> Unit = { step.previous?.let { step = it } }
    BackHandler(enabled = step.previous != null, onBack = goBack)

    val onOnward = {
        if (step == SetupStep.FIRST_LOOK && pickedLook != null) {
            val (premade, name) = pickedLook
            viewModel.update { it.defaultLookSwitchedTo(premade, name) }
            picked = null
        }
        val next = step.next
        if (next != null) {
            step = next
        } else if (!finished) {
            finished = true
            viewModel.update { it.copy(enabled = true) }
            viewModel.updateUi { it.copy(setupDone = true) }
            onFinish(settings.defaultLookId.takeIf { customise })
        }
    }
    val onTest = { viewModel.tryIt(play, settings) { windowPlays++ } }
    val frame = @Composable { items: LazyListScope.() -> Unit ->
        SetupFrame(step, step.onward(statuses), goBack, onOnward, items)
    }
    Box(modifier) {
        when (step) {
            SetupStep.WELCOME -> frame { welcomeItems(settings) }

            SetupStep.DEVICE_FIT -> DeviceFit(settings, viewModel::update) { fitItems -> frame(fitItems) }

            SetupStep.FIRST_LOOK -> frame {
                firstLookItems(settings, picked, { picked = it }, customise, { customise = it })
            }

            SetupStep.TEST -> frame { testItems(play, onTest) }

            else -> frame { permissionItems(step, statuses, openGrant) { Permissions.openAppInfo(context) } }
        }
        if (step == SetupStep.TEST) WindowTestGlow(settings, windowPlays)
    }
}

/** Saves a step by name; one an update renamed or dropped restores as the welcome. */
private val StepSaver = Saver<SetupStep, String>(save = { it.name }, restore = { it.toEnumOrNull<SetupStep>() })

/** Saves the picked premade by name, none unsaved; one an update renamed or dropped restores as none. */
private val PremadeSaver = Saver<Premade?, String>(save = { it?.name }, restore = { it.toEnumOrNull<Premade>() })

/**
 * A step's page: how far along the flow it is, its [items], then back (but on the first step) and the way on, as
 * [onward] has it ([onOnward]).
 */
@Composable
private fun SetupFrame(
    step: SetupStep,
    onward: Onward,
    onBack: () -> Unit,
    onOnward: () -> Unit,
    items: LazyListScope.() -> Unit,
) {
    val progressLabel = stringResource(R.string.setup_progress)
    Scaffold(
        topBar = {
            LinearProgressIndicator(
                progress = { step.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .semantics { contentDescription = progressLabel },
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (step.previous != null) {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                } else {
                    Spacer(Modifier)
                }
                OnwardButton(onward, onOnward)
            }
        },
    ) { innerPadding ->
        // A fresh list per step, so each opens at its top.
        key(step) {
            SettingsList(innerPadding) {
                if (step != SetupStep.WELCOME) stepHeading(step)
                items()
            }
        }
    }
}

/** The way on from a step: put off (outlined), or on (filled; disabled while a required grant is missing). */
@Composable
private fun OnwardButton(onward: Onward, onClick: () -> Unit) {
    val label = stringResource(
        when (onward) {
            Onward.START -> R.string.setup_start
            Onward.NEXT, Onward.WAIT -> R.string.setup_next
            Onward.NOT_NOW -> R.string.attention_not_now
            Onward.FINISH -> R.string.setup_finish
        },
    )
    if (onward == Onward.NOT_NOW) {
        OutlinedButton(onClick = onClick) { Text(label) }
    } else {
        Button(onClick = onClick, enabled = onward != Onward.WAIT) { Text(label) }
    }
}
