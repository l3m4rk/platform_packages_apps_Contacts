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
        leadingContent = {
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
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/**
 * Walks code points to find the char index in [name] that corresponds to the end of the match
 * for [query] starting at [matchStart]. Mirrors SearchUtil.contains() traversal so surrogate
 * pairs are counted correctly.
 */
private fun matchEndIndex(name: String, matchStart: Int, query: String): Int {
    var nameIdx = matchStart
    var queryIdx = 0
    while (queryIdx < query.length && nameIdx < name.length) {
        val qcp = Character.codePointAt(query, queryIdx)
        nameIdx += Character.charCount(Character.codePointAt(name, nameIdx))
        queryIdx += Character.charCount(qcp)
    }
    return nameIdx
}

/**
 * Parses a provider snippet string with `[match]` delimiters into a highlighted AnnotatedString.
 * Example input: "+380 [50] 974 6758" → "+380 **50** 974 6758" (bold+colored)
 */
private fun parseSnippet(raw: String, highlightColor: Color) = buildAnnotatedString {
    var i = 0
    while (i < raw.length) {
        when (raw[i]) {
            '[' -> {
                val end = raw.indexOf(']', i)
                if (end == -1) { append(raw.substring(i)); break }
                withStyle(SpanStyle(color = highlightColor, fontWeight = FontWeight.Bold)) {
                    append(raw.substring(i + 1, end))
                }
                i = end + 1
            }
            ']' -> i++
            else -> {
                val next = raw.indexOf('[', i).takeIf { it != -1 } ?: raw.length
                append(raw.substring(i, next))
                i = next
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
