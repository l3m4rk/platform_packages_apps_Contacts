package com.android.contacts.contacts.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.contacts.contacts.domain.GetContactsUseCase
import com.android.contacts.group.GroupListItem
import com.android.contacts.list.ContactListFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val getContacts: GetContactsUseCase,
) : ViewModel() {

    private val searchQueryFlow = MutableStateFlow("")
    private val _uiState = MutableStateFlow(ContactsUiState())
    val uiState: StateFlow<ContactsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            searchQueryFlow.debounce(SEARCH_DEBOUNCE).flatMapLatest { query ->
                    getContacts(query).catch {
                        _uiState.update { it.copy(isLoading = false) }
                        emit(emptyList())
                    }
                }.collect { contacts ->
                    _uiState.update { it.copy(contacts = contacts, isLoading = false) }
                }
        }
    }

    fun onSearchOpen() {
        _uiState.update { it.copy(isSearchActive = true) }
    }

    fun onSearchQueryChanged(query: String) {
        searchQueryFlow.value = query
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onSearchClosed() {
        searchQueryFlow.value = ""
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

    companion object {
        private const val SEARCH_DEBOUNCE = 300L
    }
}
