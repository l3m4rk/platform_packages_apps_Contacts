package com.android.contacts.contacts.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import com.android.contacts.R
import com.android.contacts.ui.core.AppTheme

@Composable
internal fun ContactListItem(contact: ContactItem, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(contact.displayName) },
        leadingContent = {
            val backgroundColor = nameToColor(contact.displayName)
            AsyncImage(
                model = contact.photoUri,
                contentDescription = contact.displayName,
                placeholder = painterResource(R.drawable.quantum_ic_person_vd_theme_24),
                error = painterResource(R.drawable.ic_person_avatar),
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(backgroundColor),
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Preview
@Composable
private fun ContactListItemPreview() {
    AppTheme {
        LazyColumn {
            items(testContacts) { testName ->
                ContactListItem(
                    ContactItem(
                        id = 123L,
                        displayName = testName,
                        lookupUri = "content://contacts/1".toUri(),
                        null,
                    ),
                    onClick = {},
                )
            }
        }
    }
}
