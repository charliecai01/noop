package com.noop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.noop.R

/**
 * The search bar at the top of the More page and Settings: a pill field with a magnifying glass and a
 * clear button. Twin of the iOS `NoopLiquidGlassSearchField` as those two screens use it.
 */
@Composable
internal fun NoopSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        leadingIcon = {
            Icon(Icons.Filled.Search, contentDescription = null, tint = Palette.textSecondary,
                 modifier = Modifier.size(18.dp))
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close,
                         contentDescription = uiString(R.string.l10n_workouts_screen_clear_search_67300d0f),
                         tint = Palette.textTertiary, modifier = Modifier.size(16.dp))
                }
            }
        },
        placeholder = {
            Text(uiString(R.string.search_placeholder), style = NoopType.body, color = Palette.textTertiary)
        },
        singleLine = true,
        textStyle = NoopType.body,
        shape = RoundedCornerShape(50),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Palette.textPrimary,
            unfocusedTextColor = Palette.textPrimary,
            cursorColor = Palette.accent,
            focusedBorderColor = Palette.accent,
            unfocusedBorderColor = Palette.hairline,
            focusedContainerColor = Palette.surfaceInset,
            unfocusedContainerColor = Palette.surfaceInset,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

/** What a searched screen shows in place of its content when nothing matches (More, Settings). */
@Composable
internal fun SearchNoResults(query: String) {
    NoopCard(padding = 20.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = Palette.textTertiary,
                 modifier = Modifier.size(18.dp))
            Text(uiString(R.string.search_no_results, query.trim()), style = NoopType.subhead,
                 color = Palette.textSecondary)
        }
    }
}
