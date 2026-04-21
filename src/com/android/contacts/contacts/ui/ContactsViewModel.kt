package com.android.contacts.contacts.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.contacts.contacts.domain.ContactsFilter
import com.android.contacts.contacts.domain.GetContactsUseCase
import com.android.contacts.contacts.domain.GetDrawerDataUseCase
import com.android.contacts.group.GroupListItem
import com.android.contacts.list.ContactListFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val getContacts: GetContactsUseCase,
    private val getDrawerData: GetDrawerDataUseCase,
) : ViewModel() {

    private val searchQueryFlow = MutableStateFlow("")
    private val contactsFilterFlow = MutableStateFlow<ContactsFilter>(ContactsFilter.AllContacts)
    private val _uiState = MutableStateFlow(ContactsUiState())
    val uiState: StateFlow<ContactsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(searchQueryFlow.debounce(SEARCH_DEBOUNCE), contactsFilterFlow) { query, filter ->
                query to filter
            }.flatMapLatest { (query, filter) ->
                // When searching, ignore the group/account filter — search across all contacts
                val effectiveFilter = if (query.isNotBlank()) ContactsFilter.AllContacts else filter
                getContacts(query, effectiveFilter).catch {
                    _uiState.update { it.copy(isLoading = false) }
                    emit(emptyList())
                }
            }.collect { contacts ->
                _uiState.update { it.copy(contacts = contacts, isLoading = false) }
            }
        }
        viewModelScope.launch {
            getDrawerData().catch { }.collect { data ->
                _uiState.update {
                    it.copy(
                        groups = data.groups,
                        accounts = data.accounts,
                        hasGroupWritableAccounts = data.hasGroupWritableAccounts,
                    )
                }
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
        contactsFilterFlow.value = ContactsFilter.AllContacts
        _uiState.update { it.copy(currentView = view, selectedGroupId = -1L, selectedAccount = null) }
    }

    fun onGroupSelected(group: GroupListItem) {
        contactsFilterFlow.value = ContactsFilter.ByGroup(group.groupId)
        _uiState.update {
            it.copy(
                currentView = ContactsView.GROUP_VIEW,
                selectedGroupId = group.groupId,
                selectedAccount = null,
            )
        }
    }

    fun onAccountSelected(filter: ContactListFilter) {
        contactsFilterFlow.value = ContactsFilter.ByAccount(filter)
        _uiState.update {
            it.copy(
                currentView = ContactsView.ACCOUNT_VIEW,
                selectedAccount = filter,
                selectedGroupId = -1L,
            )
        }
    }

    companion object {
        private const val SEARCH_DEBOUNCE = 300L
    }
}
