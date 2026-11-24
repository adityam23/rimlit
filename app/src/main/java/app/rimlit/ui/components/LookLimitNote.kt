package app.rimlit.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import app.rimlit.R
import app.rimlit.model.MAX_LOOKS

/** That no more looks fit: [MAX_LOOKS] at most. Shown wherever a new look is offered but can't be added. */
@Composable
fun LookLimitNote(modifier: Modifier = Modifier) {
    Text(
        text = pluralStringResource(R.plurals.look_limit, MAX_LOOKS, MAX_LOOKS),
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
