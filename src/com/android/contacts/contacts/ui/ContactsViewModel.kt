package com.android.contacts.contacts.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.contacts.contacts.data.accounts.AccountDisplayItem
import com.android.contacts.contacts.domain.ContactsFilter
import com.android.contacts.contacts.domain.GetContactsUseCase
import com.android.contacts.contacts.domain.GetDrawerDataUseCase
import com.android.contacts.contacts.domain.GetGroupContactDataUseCase
import com.android.contacts.contacts.domain.TriggerContactsSyncUseCase
import com.android.contacts.contacts.domain.TriggerContactsSyncUseCase.Result.NoNetwork
import com.android.contacts.contacts.domain.TriggerContactsSyncUseCase.Result.SyncStarted
import com.android.contacts.group.GroupListItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val getContacts: GetContactsUseCase,
    private val getDrawerData: GetDrawerDataUseCase,
    private val getGroupContactData: GetGroupContactDataUseCase,
    private val triggerContactsSync: TriggerContactsSyncUseCase,
) : ViewModel() {

    private val searchQueryFlow = MutableStateFlow("")
    private val contactsFilterFlow = MutableStateFlow<ContactsFilter>(ContactsFilter.AllContacts)
    private val _uiState = MutableStateFlow(ContactsUiState())
    val uiState: StateFlow<ContactsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ContactsEvent>(extraBufferCapacity = 2)
    val events: SharedFlow<ContactsEvent> = _events.asSharedFlow()

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

    fun onAccountSelected(item: AccountDisplayItem) {
        contactsFilterFlow.value = ContactsFilter.ByAccount(item.filter)
        _uiState.update {
            it.copy(
                currentView = ContactsView.ACCOUNT_VIEW,
                selectedAccount = item,
                selectedGroupId = -1L,
            )
        }
    }

    fun onSendToGroup(scheme: String) {
        viewModelScope.launch {
            val contactIds = _uiState.value.contacts.map { it.id }.toLongArray()
            if (contactIds.isEmpty()) return@launch
            val result = getGroupContactData(contactIds, scheme)
            when {
                result.itemList.isEmpty() -> {
                    _events.emit(ContactsEvent.ShowNoContactDataToast(scheme))
                }
                !result.allHaveDefaults -> {
                    // Picker is opened before any missing-data toast (matches old flow)
                    _events.emit(ContactsEvent.OpenGroupPicker(contactIds, result.defaultSelectionIds, scheme))
                }
                else -> {
                    // All contacts with data have a default — send directly.
                    // Show a warning first if some contacts had no data at all.
                    if (result.hasMissingContacts) {
                        _events.emit(ContactsEvent.ShowNoContactDataToast(scheme))
                    }
                    _events.emit(ContactsEvent.SendToGroup(result.itemList.joinToString(","), scheme))
                }
            }
        }
    }

    fun onEnterGroupEditMode() {
        _uiState.update { it.copy(isGroupEditMode = true) }
    }

    fun onExitGroupEditMode() {
        _uiState.update { it.copy(isGroupEditMode = false, selectedContactIds = emptySet()) }
    }

    fun onToggleContactSelection(contactId: Long) {
        _uiState.update { state ->
            val updated = if (contactId in state.selectedContactIds)
                state.selectedContactIds - contactId
            else
                state.selectedContactIds + contactId
            state.copy(selectedContactIds = updated)
        }
    }

    fun onRemoveSelectedFromGroup(group: GroupListItem) {
        viewModelScope.launch {
            val ids = _uiState.value.selectedContactIds.toLongArray()
            if (ids.isEmpty()) return@launch
            _events.emit(
                ContactsEvent.RemoveFromGroup(
                    contactIds = ids,
                    groupId = group.groupId,
                    accountName = group.accountName,
                    accountType = group.accountType,
                    dataSet = group.dataSet,
                ),
            )
            _uiState.update { it.copy(isGroupEditMode = false, selectedContactIds = emptySet()) }
        }
    }

    fun onRefresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            when (val result = triggerContactsSync()) {
                NoNetwork -> {
                    _events.emit(ContactsEvent.ShowConnectionError)
                    _uiState.update { it.copy(isRefreshing = false) }
                }
                is SyncStarted -> {
                    // Wait for sync to start (grace period) then watch for completion.
                    // Without the delay, the initial syncActive emission can be false
                    // before ContentResolver has registered the sync as active.
                    delay(SYNC_START_GRACE_MS)
                    withTimeoutOrNull(REFRESH_TIMEOUT_MS) {
                        result.syncActive.first { !it }
                    }
                    _uiState.update { it.copy(isRefreshing = false) }
                }
            }
        }
    }

    companion object {
        private const val SEARCH_DEBOUNCE = 300L
        private const val SYNC_START_GRACE_MS = 500L
        private const val REFRESH_TIMEOUT_MS = 2_000L
    }
}
