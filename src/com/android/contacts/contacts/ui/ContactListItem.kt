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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import com.android.contacts.ui.core.AppTheme

@Composable
internal fun ContactListItem(contact: ContactItem, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(contact.displayName) },
        leadingContent = {
            if (contact.photoUri != null) {
                AsyncImage(
                    model = contact.photoUri,
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
                        null,
                    ),
                    onClick = {},
                )
            }
        }
    }
}
