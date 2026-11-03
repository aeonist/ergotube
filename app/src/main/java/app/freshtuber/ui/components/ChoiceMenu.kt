package app.freshtuber.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import app.freshtuber.R

/**
 * A text button showing the current choice that opens a menu of the others.
 * Temporary home for filters and sort orders until the radial menu takes them (stage 5).
 */
@Composable
fun ChoiceMenu(titles: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    if (titles.size < 2) return
    var open by remember { mutableStateOf(false) }
    val current = selected.coerceIn(titles.indices)
    Box {
        TextButton(onClick = { open = true }) {
            Text(titles[current], maxLines = 1)
            Icon(Icons.Rounded.ArrowDropDown, contentDescription = stringResource(R.string.filter_choose))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            titles.forEachIndexed { index, title ->
                DropdownMenuItem(
                    text = { Text(title) },
                    trailingIcon = if (index == current) {
                        { Icon(Icons.Rounded.Check, contentDescription = null) }
                    } else {
                        null
                    },
                    onClick = {
                        open = false
                        if (index != current) onSelect(index)
                    },
                )
            }
        }
    }
}
