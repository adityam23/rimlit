package app.rimlit.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.rimlit.data.AppInfo

/**
 * An app as a list row: its icon, its name with [supporting] under it, then [trailing] (a switch, a checkbox). The one
 * face of an app wherever apps are listed: the Apps screen, the editor's Apps tab. [modifier] makes the row act (a
 * click, a toggle); [trailing] then only shows the state.
 */
@Composable
internal fun AppRow(
    app: AppInfo,
    modifier: Modifier = Modifier,
    supporting: (@Composable () -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
        ) {
            Text(text = app.label, style = MaterialTheme.typography.bodyLarge)
            supporting?.invoke()
        }
        trailing()
    }
}
