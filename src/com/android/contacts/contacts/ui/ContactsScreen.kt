package com.android.contacts.contacts.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.android.contacts.R
import com.android.contacts.list.ContactListItemView
import com.android.contacts.ui.core.AppTheme
import com.google.common.math.LongMath
import kotlinx.coroutines.launch

@Composable
fun ContactsScreen(
    viewModel: ContactsViewModel = hiltViewModel(),
    onContactClick: (Uri) -> Unit,
    onCreateContact: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ContactsDrawerContent(
                uiState = uiState,
                onAllContactsClick = {
                    viewModel.onViewSelected(ContactsView.ALL_CONTACTS)
                    scope.launch { drawerState.close() }
                },
                onGroupClick = { group ->
                    viewModel.onGroupSelected(group)
                    scope.launch { drawerState.close() }
                },
                onCreateLabelClick = {
                    // TODO: navigate to create group
                },
                onAccountClick = { filter ->
                    viewModel.onAccountSelected(filter)
                    scope.launch { drawerState.close() }
                },
                onSettingsClick = onSettingsClick,
            )
        },
    ) {
        Scaffold(
            topBar = {
                ContactsTopBar(
                    isSearchActive = uiState.isSearchActive,
                    searchQuery = uiState.searchQuery,
                    onSearchQueryChanged = viewModel::onSearchQueryChanged,
                    onSearchOpen = {},
                    onSearchClosed = viewModel::onSearchClosed,
                    onMenuClick = { scope.launch { drawerState.open() } },
                )
            },
            floatingActionButton = {
                if (uiState.isFabVisible) {
                    FloatingActionButton(onClick = onCreateContact) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = stringResource(R.string.action_menu_add_new_contact_button),
                        )
                    }
                }
            },
        ) { padding ->
            ContactsContent(
                uiState = uiState,
                onContactClick = onContactClick,
                padding = Modifier.padding(padding),
            )
        }
    }
}

@Composable
internal fun ContactsContent(
    uiState: ContactsUiState,
    onContactClick: (Uri) -> Unit,
    padding: Modifier,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.noContacts))
    }
}


data class ContactItem(
    val id: Long,
    val displayName: String,
    val lookupUri: Uri,
    val photoUri: Uri?,
)

@Composable
private fun ContactListItem(contact: ContactItem, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(contact.displayName) },
        leadingContent = {
            AsyncImage(
                model = contact.photoUri,
                contentDescription = contact.displayName,
                placeholder = painterResource(R.drawable.quantum_ic_person_vd_theme_24),
                error = painterResource(R.drawable.ic_person_avatar),
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.LightGray)
            )
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Preview
@Composable
private fun ContactListItem() {
    AppTheme {
        ContactListItem(
            ContactItem(
                id = 123L,
                displayName = "Test Contact",
                lookupUri = Uri.parse("hadfdsf"),
                null,
            ),
            onClick = {}
        )
    }
}
