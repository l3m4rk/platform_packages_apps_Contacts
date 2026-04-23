package com.android.contacts.contacts.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import androidx.compose.ui.res.stringResource
import com.android.contacts.R
import com.android.contacts.ui.core.AppTheme
import com.android.contacts.util.SearchUtil

// MD3 tonal palette tone-80 (light) and tone-20 (dark) for 5 hues matching Google Contacts style
private val avatarPaletteLight = listOf(
    Color(0xFFF48FB1), // pink
    Color(0xFFFFD54F), // amber
    Color(0xFFCE93D8), // purple
    Color(0xFF81C784), // green
    Color(0xFF80DEEA), // cyan
)
private val avatarPaletteDark = listOf(
    Color(0xFF880E4F), // pink
    Color(0xFF4E3900), // amber
    Color(0xFF4A0072), // purple
    Color(0xFF1B5E20), // green
    Color(0xFF006064), // cyan
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ContactListItem(
    contact: ContactItem,
    onClick: () -> Unit,
    searchQuery: String = "",
    isSelected: Boolean = false,
    onSelectionToggle: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val highlightColor = MaterialTheme.colorScheme.primary
    val headlineText = remember(contact.displayName, searchQuery) {
        buildAnnotatedString {
            val name = contact.displayName
            val query = searchQuery.trim()
            if (query.isBlank()) {
                append(name)
            } else {
                val matchStart = SearchUtil.contains(name, query)
                if (matchStart < 0) {
                    append(name)
                } else {
                    val matchEnd = matchEndIndex(name, matchStart, query)
                    append(name.substring(0, matchStart))
                    withStyle(SpanStyle(color = highlightColor, fontWeight = FontWeight.Bold)) {
                        append(name.substring(matchStart, matchEnd))
                    }
                    append(name.substring(matchEnd))
                }
            }
        }
    }

    ListItem(
        headlineContent = { Text(headlineText) },
        supportingContent = contact.snippet?.let { raw ->
            {
                Text(
                    text = parseSnippet(raw, highlightColor),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        leadingContent = { ContactAvatar(contact) },
        trailingContent = if (onSelectionToggle != null) {
            { Checkbox(checked = isSelected, onCheckedChange = { onSelectionToggle() }) }
        } else null,
        modifier = Modifier.combinedClickable(
            onClick = onSelectionToggle ?: onClick,
            onLongClickLabel = if (onLongClick != null) stringResource(R.string.multi_picker_select_action) else null,
            onLongClick = onLongClick,
        ),
    )
}

@Composable
private fun ContactAvatar(contact: ContactItem) {
    val context = LocalContext.current
    if (contact.photoUri != null) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(contact.photoUri)
                .crossfade(true)
                .build(),
            contentDescription = contact.displayName,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape),
        )
    } else {
        val isDark = isSystemInDarkTheme()
        val backgroundColor = remember(contact.displayName, isDark) {
            val palette = if (isDark) avatarPaletteDark else avatarPaletteLight
            palette[nameToColorIndex(contact.displayName, palette.size)]
        }
        val contentColor = if (backgroundColor.luminance() > 0.3f) Color(0xFF1C1B1F) else Color(0xFFECE6F0)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(backgroundColor),
        ) {
            val initial = contact.displayName.trimStart().firstOrNull()
            if (initial != null && initial.isLetter()) {
                Text(
                    text = initial.uppercaseChar().toString(),
                    color = contentColor,
                    style = MaterialTheme.typography.titleMedium,
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Preview
@Composable
private fun ContactListItemPreview() {
    AppTheme {
        LazyColumn {
            items(testContacts) { name ->
                ContactListItem(
                    ContactItem(
                        id = 123L,
                        displayName = name,
                        lookupUri = "content://contacts/1".toUri(),
                        photoUri = null,
                    ),
                    onClick = {},
                )
            }
        }
    }
}
