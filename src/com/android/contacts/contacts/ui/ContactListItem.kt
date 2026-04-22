package com.android.contacts.contacts.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.android.contacts.ui.core.AppTheme
import com.android.contacts.util.SearchUtil

@Composable
internal fun ContactListItem(
    contact: ContactItem,
    onClick: () -> Unit,
    searchQuery: String = "",
    isSelected: Boolean = false,
    onSelectionToggle: (() -> Unit)? = null,
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
        modifier = Modifier.clickable(onClick = onSelectionToggle ?: onClick),
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
        val backgroundColor =
            remember(contact.displayName) { nameToColor(contact.displayName) }
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
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = Color.White,
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
