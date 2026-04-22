package com.android.contacts.contacts.ui

import com.android.contacts.contacts.data.accounts.AccountDisplayItem
import com.android.contacts.group.GroupListItem

data class ContactsUiState(
    val currentView: ContactsView = ContactsView.ALL_CONTACTS,
    val isSearchActive: Boolean = false,
    val searchQuery: String = "",
    val groups: List<GroupListItem> = emptyList(),
    val accounts: List<AccountDisplayItem> = emptyList(),
    val providerStatus: Int? = null,
    val hasGroupWritableAccounts: Boolean = false,
    val selectedGroupId: Long = -1L,
    val selectedAccount: AccountDisplayItem? = null,
    val contacts: List<ContactItem> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isGroupEditMode: Boolean = false,
    val isSelectionMode: Boolean = false,
    val selectedContactIds: Set<Long> = emptySet(),
    val showDeleteConfirmation: Boolean = false,
)

val ContactsUiState.isAnySelectionModeActive: Boolean
    get() = isGroupEditMode || isSelectionMode

val ContactsUiState.isFabVisible: Boolean
    get() = currentView != ContactsView.GROUP_VIEW && !isSearchActive && !isSelectionMode

fun ContactsUiState.showLoadingUi() = contacts.isEmpty() && isLoading

enum class ContactsView { ALL_CONTACTS, GROUP_VIEW, ACCOUNT_VIEW }
