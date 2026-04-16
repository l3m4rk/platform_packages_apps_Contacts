package com.android.contacts.contacts.ui

import com.android.contacts.group.GroupListItem
import com.android.contacts.list.ContactListFilter

data class ContactsUiState(
    val currentView: ContactsView = ContactsView.ALL_CONTACTS,
    val isSearchActive: Boolean = false,
    val searchQuery: String = "",
    val isFabVisible: Boolean = true,
    val groups: List<GroupListItem> = emptyList(),
    val accounts: List<ContactListFilter> = emptyList(),
    val providerStatus: Int? = null,
    val hasGroupWritableAccounts: Boolean = false,
    val selectedGroupId: Long = -1L,
    val selectedAccount: ContactListFilter? = null,
    val contacts: List<ContactItem> = emptyList(),
    val isLoading: Boolean = false,
)

fun ContactsUiState.showLoadingUi() = contacts.isEmpty() && isLoading

enum class ContactsView { ALL_CONTACTS, GROUP_VIEW, ACCOUNT_VIEW }
