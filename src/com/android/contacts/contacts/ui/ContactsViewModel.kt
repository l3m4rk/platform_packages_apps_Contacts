package com.android.contacts.contacts.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.contacts.contacts.domain.GetContactsUseCase
import com.android.contacts.group.GroupListItem
import com.android.contacts.list.ContactListFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.ticker
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val getContacts: GetContactsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ContactsUiState())
    val uiState: StateFlow<ContactsUiState> = _uiState.asStateFlow()

    init {
        loadContacts()
    }

    private fun loadContacts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            getContacts()
                .catch { _uiState.update { it.copy(isLoading = false) } }
                .collect { contacts ->
                    _uiState.update { it.copy(contacts = contacts, isLoading = false) }
                }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query, isSearchActive = query.isNotEmpty()) }
        viewModelScope.launch {
            getContacts(query)
                .collect { contacts -> _uiState.update { it.copy(contacts = contacts) } }
        }
    }

    fun onSearchClosed() {
        _uiState.update { it.copy(isSearchActive = false, searchQuery = "") }
    }

    fun onViewSelected(view: ContactsView) {
        _uiState.update { it.copy(currentView = view) }
    }

    fun onGroupSelected(group: GroupListItem) {
        _uiState.update {
            it.copy(
                currentView = ContactsView.GROUP_VIEW,
                selectedGroupId = group.groupId,
            )
        }
    }

    fun onAccountSelected(filter: ContactListFilter) {
        _uiState.update {
            it.copy(
                currentView = ContactsView.ACCOUNT_VIEW,
                selectedAccount = filter,
            )
        }
    }
}
