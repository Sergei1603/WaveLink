package ru.wavelink.app.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import ru.wavelink.app.ui.components.WlIconButton
import ru.wavelink.app.ui.components.WlStackedButton
import ru.wavelink.app.ui.components.fadingRule
import ru.wavelink.app.ui.theme.Wl
import ru.wavelink.app.ui.theme.WlType

/**
 * What the library has picked out — track ids in the Треки view, artist folder keys in the
 * Артисты one. Kept as screen state rather than in a ViewModel because it is the *screen's*
 * mode: leaving the screen ends it, and a rotation must not.
 */
@Stable
class SelectionState(initial: Set<String> = emptySet()) {
    var ids: Set<String> by mutableStateOf(initial)
        private set

    val active: Boolean get() = ids.isNotEmpty()
    val count: Int get() = ids.size

    fun contains(id: String): Boolean = id in ids

    fun toggle(id: String) {
        ids = if (id in ids) ids - id else ids + id
    }

    fun clear() { ids = emptySet() }

    /** Keeps only what still exists — a deleted or renamed row must not linger in the count. */
    fun retainAll(present: Collection<String>) {
        if (!active) return
        val kept = ids intersect present.toSet()
        if (kept.size != ids.size) ids = kept
    }

    companion object {
        val Saver = listSaver<SelectionState, String>(
            save = { it.ids.toList() },
            restore = { SelectionState(it.toSet()) }
        )
    }
}

@Composable
fun rememberSelection(): SelectionState =
    rememberSaveable(saver = SelectionState.Saver) { SelectionState() }

/** One button in [SelectionBar]. `enabled = false` greys it out rather than removing it. */
data class SelectionAction(
    val label: String,
    val icon: ImageVector,
    val enabled: Boolean = true,
    val onClick: () -> Unit
)

/**
 * The bar that replaces nothing and covers nothing: it sits between the list and the tab bar
 * while a selection is live, and the ✕ on the left is the only way out besides acting.
 */
@Composable
fun SelectionBar(
    count: Int,
    onClear: () -> Unit,
    actions: List<SelectionAction>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .fadingRule(top = true, inset = 0.dp)
            .background(Wl.Surface)
            .padding(start = 8.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        WlIconButton(onClick = onClear, size = 40.dp) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Снять выделение",
                tint = Wl.Accent,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            count.toString(),
            style = WlType.NumericSm,
            color = Wl.Text,
            modifier = Modifier.padding(end = 6.dp)
        )
        actions.forEach { action ->
            WlStackedButton(
                label = action.label,
                color = if (action.enabled) Wl.text(70) else Wl.text(30),
                onClick = { if (action.enabled) action.onClick() },
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    action.icon,
                    contentDescription = null,
                    tint = LocalContentColor.current,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}

/** The same shape as the collections screen's delete prompt, so destructive asks look alike. */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String = "Удалить",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Wl.Surface,
        title = { Text(title, style = WlType.Heading, color = Wl.Text) },
        text = { Text(message, style = WlType.BodySm, color = Wl.text(65)) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirmLabel, color = Wl.Accent300)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена", color = Wl.text(60)) }
        }
    )
}
