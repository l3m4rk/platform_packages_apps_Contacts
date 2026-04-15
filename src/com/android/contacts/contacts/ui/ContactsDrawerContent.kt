package com.android.contacts.contacts.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.contacts.group.GroupListItem
import com.android.contacts.list.ContactListFilter
import com.android.contacts.R
import com.android.contacts.ui.core.AppTheme

@Composable
fun ContactsDrawerContent(
    uiState: ContactsUiState,
    onAllContactsClick: () -> Unit,
    onGroupClick: (GroupListItem) -> Unit,
    onCreateLabelClick: () -> Unit,
    onAccountClick: (ContactListFilter) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalDrawerSheet(modifier = Modifier) {
        LazyColumn {
            item {
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.AccountCircle, contentDescription = null) },
                    label = { Text(stringResource(R.string.contactsList)) },
                    selected = uiState.currentView == ContactsView.ALL_CONTACTS,
                    onClick = onAllContactsClick,
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
            }

            // Groups section (labels in ui)
            if (uiState.groups.isNotEmpty() || uiState.hasGroupWritableAccounts) {
                item { DrawerSectionHeader(text = stringResource(R.string.menu_title_groups)) }
                items(uiState.groups, key = { it.groupId }) { group ->
                    NavigationDrawerItem(
                        icon = { Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null) },
                        label = { Text(group.title) },
                        badge = if (group.memberCount > 0) {
                            { Text(group.memberCount.toString()) }
                        } else null,
                        selected = uiState.currentView == ContactsView.GROUP_VIEW && uiState.selectedGroupId == group.groupId,
                        onClick = { onGroupClick(group) },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    )
                }
            }

            // Accounts section
            if (uiState.accounts.isNotEmpty()) {
                item { DrawerSectionHeader(text = stringResource(R.string.menu_title_filters)) }
                items(uiState.accounts, key = { it.id }) { filter ->
                    NavigationDrawerItem(
                        icon = {
                            Icon(Icons.Default.AccountBox, contentDescription = filter.accountName)
                        },
                        label = {},
                        selected = uiState.currentView == ContactsView.ACCOUNT_VIEW &&
                            uiState.selectedAccount == filter,
                        onClick = { onAccountClick(filter) },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    )
                }
            }

            // Divider + Settings
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item {
                NavigationDrawerItem(
                    icon = {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = stringResource(R.string.menu_settings),
                        )
                    },
                    label = { Text(stringResource(R.string.menu_settings)) },
                    selected = false,
                    onClick = onSettingsClick,
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
            }
        }
    }
}

@Composable
private fun DrawerSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 28.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Preview
@Composable
private fun DrawerSectionHeaderPreview() {
    AppTheme {
        Surface {
            DrawerSectionHeader("Hello, tree")
        }
    }
}
