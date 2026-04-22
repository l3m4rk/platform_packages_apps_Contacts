package com.android.contacts.contacts.ui

import android.net.Uri
import app.cash.turbine.test
import com.android.contacts.ContactsUtils
import com.android.contacts.contacts.data.accounts.AccountDisplayItem
import com.android.contacts.contacts.domain.ContactsFilter
import com.android.contacts.contacts.domain.DrawerData
import com.android.contacts.contacts.domain.GetContactsUseCase
import com.android.contacts.contacts.domain.GetDrawerDataUseCase
import com.android.contacts.contacts.domain.GetGroupContactDataUseCase
import com.android.contacts.contacts.domain.TriggerContactsSyncUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ContactsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val getContacts: GetContactsUseCase = mockk()
    private val getDrawerData: GetDrawerDataUseCase = mockk()
    private val getGroupContactData: GetGroupContactDataUseCase = mockk()
    private val triggerContactsSync: TriggerContactsSyncUseCase = mockk()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(): ContactsViewModel {
        every { getDrawerData() } returns flowOf(DrawerData(emptyList(), emptyList(), false))
        return ContactsViewModel(getContacts, getDrawerData, getGroupContactData, triggerContactsSync)
    }

    //region Initial state

    @Test
    fun `initial state has empty contacts and isLoading false`() = runTest {
        every { getContacts(any(), any()) } returns flowOf(emptyList())
        val vm = viewModel()
        advanceTimeBy(400) // past debounce

        assertEquals(emptyList<ContactItem>(), vm.uiState.value.contacts)
        assertFalse(vm.uiState.value.isLoading)
    }

    //endregion

    //region Search open / close

    @Test
    fun `onSearchOpen sets isSearchActive true`() {
        every { getContacts(any(), any()) } returns flowOf(emptyList())
        val vm = viewModel()

        vm.onSearchOpen()

        assertTrue(vm.uiState.value.isSearchActive)
    }

    @Test
    fun `onSearchClosed resets isSearchActive and query`() {
        every { getContacts(any(), any()) } returns flowOf(emptyList())
        val vm = viewModel()

        vm.onSearchOpen()
        vm.onSearchQueryChanged("alex")
        vm.onSearchClosed()

        assertFalse(vm.uiState.value.isSearchActive)
        assertEquals("", vm.uiState.value.searchQuery)
    }

    @Test
    fun `onSearchQueryChanged updates searchQuery in state`() {
        every { getContacts(any(), any()) } returns flowOf(emptyList())
        val vm = viewModel()

        vm.onSearchQueryChanged("bob")

        assertEquals("bob", vm.uiState.value.searchQuery)
    }

    //endregion

    //region Search pipeline

    @Test
    fun `contacts emitted after debounce`() = runTest {
        val contact = ContactItem(1L, "Alice", mockk<Uri>(), null)
        every { getContacts("", any()) } returns flowOf(emptyList())
        every { getContacts("ali", any()) } returns flowOf(listOf(contact))
        val vm = viewModel()
        advanceTimeBy(400) // settle initial state

        vm.onSearchQueryChanged("ali")
        advanceTimeBy(400) // past 300ms debounce

        assertEquals(listOf(contact), vm.uiState.value.contacts)
    }

    @Test
    fun `rapid query changes only emit for the last one (debounce)`() = runTest {
        val contact = ContactItem(1L, "Bob", mockk<Uri>(), null)
        every { getContacts("", any()) } returns flowOf(emptyList())
        every { getContacts("b", any()) } returns flowOf(emptyList())
        every { getContacts("bo", any()) } returns flowOf(emptyList())
        every { getContacts("bob", any()) } returns flowOf(listOf(contact))
        val vm = viewModel()
        advanceTimeBy(400)

        vm.onSearchQueryChanged("b")
        vm.onSearchQueryChanged("bo")
        vm.onSearchQueryChanged("bob")
        advanceTimeBy(400)

        assertEquals(listOf(contact), vm.uiState.value.contacts)
    }

    @Test
    fun `error in getContacts emits emptyList and sets isLoading false`() = runTest {
        every { getContacts("", any()) } returns flowOf(emptyList())
        every { getContacts("err", any()) } returns flow { throw RuntimeException("network error") }
        val vm = viewModel()
        advanceTimeBy(400)

        vm.onSearchQueryChanged("err")
        advanceTimeBy(400)

        assertEquals(emptyList<ContactItem>(), vm.uiState.value.contacts)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `pipeline survives error and emits results for next query`() = runTest {
        val contact = ContactItem(1L, "Carol", mockk<Uri>(), null)
        every { getContacts("", any()) } returns flowOf(emptyList())
        every { getContacts("err", any()) } returns flow { throw RuntimeException("fail") }
        every { getContacts("car", any()) } returns flowOf(listOf(contact))
        val vm = viewModel()
        advanceTimeBy(400)

        vm.onSearchQueryChanged("err")
        advanceTimeBy(400)

        vm.onSearchQueryChanged("car")
        advanceTimeBy(400)

        assertEquals(listOf(contact), vm.uiState.value.contacts)
    }

    //endregion

    //region Filter dispatch

    @Test
    fun `onGroupSelected updates selectedGroupId and currentView`() = runTest {
        every { getContacts(any(), any()) } returns flowOf(emptyList())
        val vm = viewModel()
        advanceTimeBy(400)

        vm.onGroupSelected(group)

        assertEquals(ContactsView.GROUP_VIEW, vm.uiState.value.currentView)
        assertEquals(group.groupId, vm.uiState.value.selectedGroupId)
    }

    @Test
    fun `onGroupSelected triggers getContacts with ByGroup filter`() = runTest {
        every { getContacts(any(), any()) } returns flowOf(emptyList())
        val vm = viewModel()
        advanceTimeBy(400)

        vm.onGroupSelected(group)
        advanceTimeBy(100)

        verify { getContacts("", ContactsFilter.ByGroup(group.groupId)) }
    }

    @Test
    fun `onAccountSelected updates selectedAccount and currentView`() = runTest {
        every { getContacts(any(), any()) } returns flowOf(emptyList())
        val vm = viewModel()
        val account = AccountDisplayItem(mockk(), "Test Account", null)
        advanceTimeBy(400)

        vm.onAccountSelected(account)

        assertEquals(ContactsView.ACCOUNT_VIEW, vm.uiState.value.currentView)
        assertEquals(account, vm.uiState.value.selectedAccount)
    }

    @Test
    fun `onAccountSelected triggers getContacts with ByAccount filter`() = runTest {
        every { getContacts(any(), any()) } returns flowOf(emptyList())
        val vm = viewModel()
        val filter = mockk<com.android.contacts.list.ContactListFilter>()
        val account = AccountDisplayItem(filter, "Test Account", null)
        advanceTimeBy(400)

        vm.onAccountSelected(account)
        advanceTimeBy(100)

        verify { getContacts("", ContactsFilter.ByAccount(filter)) }
    }

    @Test
    fun `onViewSelected ALL_CONTACTS resets filter and state`() = runTest {
        every { getContacts(any(), any()) } returns flowOf(emptyList())
        val vm = viewModel()
        advanceTimeBy(400)

        vm.onGroupSelected(group)
        advanceTimeBy(100)
        vm.onViewSelected(ContactsView.ALL_CONTACTS)
        advanceTimeBy(100)

        assertEquals(ContactsView.ALL_CONTACTS, vm.uiState.value.currentView)
        assertEquals(-1L, vm.uiState.value.selectedGroupId)
        assertNull(vm.uiState.value.selectedAccount)
        verify { getContacts("", ContactsFilter.AllContacts) }
    }

    @Test
    fun `search ignores active group filter`() = runTest {
        every { getContacts(any(), any()) } returns flowOf(emptyList())
        val vm = viewModel()
        advanceTimeBy(400)

        vm.onGroupSelected(group)
        advanceTimeBy(100)
        vm.onSearchOpen()
        vm.onSearchQueryChanged("ali")
        advanceTimeBy(400)

        verify { getContacts("ali", ContactsFilter.AllContacts) }
    }

    //endregion

    //region onSendToGroup events

    @Test
    fun `onSendToGroup emits nothing when contacts list is empty`() = runTest {
        every { getContacts(any(), any()) } returns flowOf(emptyList())
        val vm = viewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.onSendToGroup(ContactsUtils.SCHEME_MAILTO)
            advanceUntilIdle()
            expectNoEvents()
        }
    }

    @Test
    fun `onSendToGroup emits ShowNoContactDataToast when no emails found`() = runTest {
        every { getContacts(any(), any()) } returns flowOf(listOf(contact))
        coEvery { getGroupContactData(any(), any()) } returns noDataResult
        val vm = viewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.onSendToGroup(ContactsUtils.SCHEME_MAILTO)
            advanceUntilIdle()
            assertEquals(ContactsEvent.ShowNoContactDataToast(ContactsUtils.SCHEME_MAILTO), awaitItem())
        }
    }

    @Test
    fun `onSendToGroup emits SendToGroup when all contacts have defaults`() = runTest {
        every { getContacts(any(), any()) } returns flowOf(listOf(contact))
        coEvery { getGroupContactData(any(), any()) } returns allDefaultsResult
        val vm = viewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.onSendToGroup(ContactsUtils.SCHEME_MAILTO)
            advanceUntilIdle()
            assertEquals(
                ContactsEvent.SendToGroup("alice@example.com", ContactsUtils.SCHEME_MAILTO),
                awaitItem(),
            )
        }
    }

    @Test
    fun `onSendToGroup emits toast then SendToGroup when some contacts have no data`() = runTest {
        every { getContacts(any(), any()) } returns flowOf(listOf(contact))
        coEvery { getGroupContactData(any(), any()) } returns allDefaultsMissingResult
        val vm = viewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.onSendToGroup(ContactsUtils.SCHEME_MAILTO)
            advanceUntilIdle()
            assertEquals(ContactsEvent.ShowNoContactDataToast(ContactsUtils.SCHEME_MAILTO), awaitItem())
            assertEquals(
                ContactsEvent.SendToGroup("alice@example.com", ContactsUtils.SCHEME_MAILTO),
                awaitItem(),
            )
        }
    }

    @Test
    fun `onSendToGroup emits OpenGroupPicker when contacts need disambiguation`() = runTest {
        every { getContacts(any(), any()) } returns flowOf(listOf(contact))
        coEvery { getGroupContactData(any(), any()) } returns needsPickerResult
        val vm = viewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.onSendToGroup(ContactsUtils.SCHEME_MAILTO)
            advanceUntilIdle()
            val event = awaitItem() as ContactsEvent.OpenGroupPicker
            assertEquals(ContactsUtils.SCHEME_MAILTO, event.scheme)
        }
    }

    //endregion

    //region onRefresh

    @Test
    fun `onRefresh initial state has isRefreshing false`() = runTest {
        every { getContacts(any(), any()) } returns flowOf(emptyList())
        val vm = viewModel()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isRefreshing)
    }

    @Test
    fun `onRefresh no network emits ShowConnectionError and clears isRefreshing`() = runTest {
        every { getContacts(any(), any()) } returns flowOf(emptyList())
        coEvery { triggerContactsSync() } returns TriggerContactsSyncUseCase.Result.NoNetwork
        val vm = viewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.onRefresh()
            advanceUntilIdle()

            assertEquals(ContactsEvent.ShowConnectionError, awaitItem())
            assertFalse(vm.uiState.value.isRefreshing)
        }
    }

    @Test
    fun `onRefresh sets isRefreshing true before sync completes`() = runTest {
        every { getContacts(any(), any()) } returns flowOf(emptyList())
        // Flow never emits false — simulates long-running sync
        val syncFlow = MutableSharedFlow<Boolean>()
        coEvery { triggerContactsSync() } returns TriggerContactsSyncUseCase.Result.SyncStarted(syncFlow)
        val vm = viewModel()
        advanceUntilIdle()

        vm.onRefresh()
        advanceTimeBy(100) // before grace period

        assertTrue(vm.uiState.value.isRefreshing)
    }

    @Test
    fun `onRefresh clears isRefreshing when syncActive emits false after grace period`() = runTest {
        every { getContacts(any(), any()) } returns flowOf(emptyList())
        coEvery { triggerContactsSync() } returns TriggerContactsSyncUseCase.Result.SyncStarted(
            flowOf(false),
        )
        val vm = viewModel()
        advanceUntilIdle()

        vm.onRefresh()
        advanceTimeBy(600) // past 500ms grace period

        assertFalse(vm.uiState.value.isRefreshing)
    }

    @Test
    fun `onRefresh stays refreshing while sync is active then clears when done`() = runTest {
        every { getContacts(any(), any()) } returns flowOf(emptyList())
        val syncFlow = flow {
            emit(true)  // sync active
            delay(800)
            emit(false) // sync done
        }
        coEvery { triggerContactsSync() } returns TriggerContactsSyncUseCase.Result.SyncStarted(syncFlow)
        val vm = viewModel()
        advanceUntilIdle()

        vm.onRefresh()
        advanceTimeBy(600) // past grace, sync is active → still refreshing
        assertTrue(vm.uiState.value.isRefreshing)

        advanceTimeBy(900) // sync done
        assertFalse(vm.uiState.value.isRefreshing)
    }

    @Test
    fun `onRefresh emits no event when sync succeeds`() = runTest {
        every { getContacts(any(), any()) } returns flowOf(emptyList())
        coEvery { triggerContactsSync() } returns TriggerContactsSyncUseCase.Result.SyncStarted(
            flowOf(false),
        )
        val vm = viewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.onRefresh()
            advanceTimeBy(600)
            expectNoEvents()
        }
    }

    @Test
    fun `onRefresh clears isRefreshing after timeout when sync never finishes`() = runTest {
        every { getContacts(any(), any()) } returns flowOf(emptyList())
        val syncFlow = MutableSharedFlow<Boolean>() // never emits
        coEvery { triggerContactsSync() } returns TriggerContactsSyncUseCase.Result.SyncStarted(syncFlow)
        val vm = viewModel()
        advanceUntilIdle()

        vm.onRefresh()
        advanceTimeBy(2_600) // grace (500) + timeout (2000) + buffer

        assertFalse(vm.uiState.value.isRefreshing)
    }

    //endregion

    //region Group edit mode

    @Test
    fun `onEnterGroupEditMode sets isGroupEditMode true`() {
        every { getContacts(any(), any()) } returns flowOf(emptyList())
        val vm = viewModel()

        vm.onEnterGroupEditMode()

        assertTrue(vm.uiState.value.isGroupEditMode)
    }

    @Test
    fun `onContactLongClick in ALL_CONTACTS enters selection mode and selects contact`() {
        every { getContacts(any(), any()) } returns flowOf(emptyList())
        val vm = viewModel()
        // default view is ALL_CONTACTS

        vm.onContactLongClick(42L)

        assertTrue(vm.uiState.value.isSelectionMode)
        assertFalse(vm.uiState.value.isGroupEditMode)
        assertEquals(setOf(42L), vm.uiState.value.selectedContactIds)
    }

    @Test
    fun `onContactLongClick in GROUP_VIEW enters group edit mode and selects contact`() = runTest {
        every { getContacts(any(), any()) } returns flowOf(emptyList())
        val vm = viewModel()
        vm.onGroupSelected(group)
        advanceUntilIdle()

        vm.onContactLongClick(42L)

        assertTrue(vm.uiState.value.isGroupEditMode)
        assertFalse(vm.uiState.value.isSelectionMode)
        assertEquals(setOf(42L), vm.uiState.value.selectedContactIds)
    }

    @Test
    fun `onExitGroupEditMode clears isGroupEditMode and selectedContactIds`() {
        every { getContacts(any(), any()) } returns flowOf(emptyList())
        val vm = viewModel()

        vm.onEnterGroupEditMode()
        vm.onToggleContactSelection(1L)
        vm.onExitGroupEditMode()

        assertFalse(vm.uiState.value.isGroupEditMode)
        assertEquals(emptySet<Long>(), vm.uiState.value.selectedContactIds)
    }

    @Test
    fun `onToggleContactSelection adds contact to selection`() {
        every { getContacts(any(), any()) } returns flowOf(emptyList())
        val vm = viewModel()

        vm.onToggleContactSelection(42L)

        assertEquals(setOf(42L), vm.uiState.value.selectedContactIds)
    }

    @Test
    fun `onToggleContactSelection removes already selected contact`() {
        every { getContacts(any(), any()) } returns flowOf(emptyList())
        val vm = viewModel()

        vm.onToggleContactSelection(42L)
        vm.onToggleContactSelection(42L)

        assertEquals(emptySet<Long>(), vm.uiState.value.selectedContactIds)
    }

    @Test
    fun `onToggleContactSelection handles multiple contacts independently`() {
        every { getContacts(any(), any()) } returns flowOf(emptyList())
        val vm = viewModel()

        vm.onToggleContactSelection(1L)
        vm.onToggleContactSelection(2L)
        vm.onToggleContactSelection(1L) // deselect

        assertEquals(setOf(2L), vm.uiState.value.selectedContactIds)
    }

    @Test
    fun `onRemoveSelectedFromGroup emits RemoveFromGroup with correct data`() = runTest {
        every { getContacts(any(), any()) } returns flowOf(emptyList())
        val vm = viewModel()

        vm.onToggleContactSelection(10L)
        vm.onToggleContactSelection(20L)

        vm.events.test {
            vm.onRemoveSelectedFromGroup(group)
            advanceUntilIdle()

            val event = awaitItem() as ContactsEvent.RemoveFromGroup
            assertEquals(group.groupId, event.groupId)
            assertEquals(group.accountName, event.accountName)
            assertEquals(group.accountType, event.accountType)
            assertEquals(group.dataSet, event.dataSet)
            assertTrue(event.contactIds.toSet() == setOf(10L, 20L))
        }
    }

    @Test
    fun `onRemoveSelectedFromGroup clears edit mode and selection after emitting event`() = runTest {
        every { getContacts(any(), any()) } returns flowOf(emptyList())
        val vm = viewModel()

        vm.onEnterGroupEditMode()
        vm.onToggleContactSelection(10L)

        vm.events.test {
            vm.onRemoveSelectedFromGroup(group)
            advanceUntilIdle()
            awaitItem() // consume the event
        }

        assertFalse(vm.uiState.value.isGroupEditMode)
        assertEquals(emptySet<Long>(), vm.uiState.value.selectedContactIds)
    }

    @Test
    fun `onRemoveSelectedFromGroup emits nothing when selection is empty`() = runTest {
        every { getContacts(any(), any()) } returns flowOf(emptyList())
        val vm = viewModel()

        vm.events.test {
            vm.onRemoveSelectedFromGroup(group)
            advanceUntilIdle()
            expectNoEvents()
        }
    }

    //endregion

    //region Selection mode

    @Test
    fun `onExitSelectionMode clears isSelectionMode and selectedContactIds`() {
        every { getContacts(any(), any()) } returns flowOf(emptyList())
        val vm = viewModel()

        vm.onContactLongClick(1L)
        vm.onExitSelectionMode()

        assertFalse(vm.uiState.value.isSelectionMode)
        assertEquals(emptySet<Long>(), vm.uiState.value.selectedContactIds)
    }

    @Test
    fun `onExitSelectionMode clears showDeleteConfirmation`() {
        every { getContacts(any(), any()) } returns flowOf(emptyList())
        val vm = viewModel()

        vm.onContactLongClick(1L)
        vm.onDeleteSelected()
        assertTrue(vm.uiState.value.showDeleteConfirmation)

        vm.onExitSelectionMode()

        assertFalse(vm.uiState.value.showDeleteConfirmation)
    }

    @Test
    fun `deselecting last contact exits selection mode automatically`() {
        every { getContacts(any(), any()) } returns flowOf(emptyList())
        val vm = viewModel()

        vm.onContactLongClick(42L)
        assertTrue(vm.uiState.value.isSelectionMode)

        vm.onToggleContactSelection(42L)

        assertFalse(vm.uiState.value.isSelectionMode)
        assertEquals(emptySet<Long>(), vm.uiState.value.selectedContactIds)
    }

    @Test
    fun `deselecting in group edit mode does not exit edit mode`() {
        every { getContacts(any(), any()) } returns flowOf(emptyList())
        val vm = viewModel()

        vm.onEnterGroupEditMode()
        vm.onToggleContactSelection(42L)
        vm.onToggleContactSelection(42L)

        assertTrue(vm.uiState.value.isGroupEditMode)
    }

    @Test
    fun `onDeleteSelected sets showDeleteConfirmation true`() {
        every { getContacts(any(), any()) } returns flowOf(emptyList())
        val vm = viewModel()

        vm.onContactLongClick(1L)
        vm.onDeleteSelected()

        assertTrue(vm.uiState.value.showDeleteConfirmation)
    }

    @Test
    fun `onDeleteSelected does nothing when selection is empty`() {
        every { getContacts(any(), any()) } returns flowOf(emptyList())
        val vm = viewModel()

        vm.onDeleteSelected()

        assertFalse(vm.uiState.value.showDeleteConfirmation)
    }

    @Test
    fun `onDeleteDismissed clears showDeleteConfirmation and keeps selection`() {
        every { getContacts(any(), any()) } returns flowOf(emptyList())
        val vm = viewModel()

        vm.onContactLongClick(1L)
        vm.onDeleteSelected()
        vm.onDeleteDismissed()

        assertFalse(vm.uiState.value.showDeleteConfirmation)
        assertTrue(vm.uiState.value.isSelectionMode)
        assertEquals(setOf(1L), vm.uiState.value.selectedContactIds)
    }

    @Test
    fun `onDeleteConfirmed emits DeleteContacts with correct ids and names`() = runTest {
        val alice = ContactItem(1L, "Alice", mockk<Uri>(), null)
        val bob = ContactItem(2L, "Bob", mockk<Uri>(), null)
        every { getContacts(any(), any()) } returns flowOf(listOf(alice, bob))
        val vm = viewModel()
        advanceUntilIdle()

        vm.onToggleContactSelection(1L)
        vm.onToggleContactSelection(2L)
        vm.onDeleteSelected()

        vm.events.test {
            vm.onDeleteConfirmed()
            advanceUntilIdle()

            val event = awaitItem() as ContactsEvent.DeleteContacts
            assertEquals(setOf(1L, 2L), event.contactIds.toSet())
            assertEquals(setOf("Alice", "Bob"), event.displayNames.toSet())
        }
    }

    @Test
    fun `onDeleteConfirmed clears selection mode and showDeleteConfirmation`() = runTest {
        val alice = ContactItem(1L, "Alice", mockk<Uri>(), null)
        every { getContacts(any(), any()) } returns flowOf(listOf(alice))
        val vm = viewModel()
        advanceUntilIdle()

        vm.onToggleContactSelection(1L)
        vm.onDeleteSelected()

        vm.events.test {
            vm.onDeleteConfirmed()
            advanceUntilIdle()
            awaitItem()
        }

        assertFalse(vm.uiState.value.isSelectionMode)
        assertFalse(vm.uiState.value.showDeleteConfirmation)
        assertEquals(emptySet<Long>(), vm.uiState.value.selectedContactIds)
    }

    @Test
    fun `onShareSelected emits ShareContacts with correct lookup URIs`() = runTest {
        val uri1 = mockk<Uri>()
        val uri2 = mockk<Uri>()
        val alice = ContactItem(1L, "Alice", uri1, null)
        val bob = ContactItem(2L, "Bob", uri2, null)
        every { getContacts(any(), any()) } returns flowOf(listOf(alice, bob))
        val vm = viewModel()
        advanceUntilIdle()

        vm.onToggleContactSelection(1L)
        vm.onToggleContactSelection(2L)

        vm.events.test {
            vm.onShareSelected()
            advanceUntilIdle()

            val event = awaitItem() as ContactsEvent.ShareContacts
            assertEquals(setOf(uri1, uri2), event.lookupUris.toSet())
        }
    }

    @Test
    fun `onShareSelected clears selection mode`() = runTest {
        val alice = ContactItem(1L, "Alice", mockk<Uri>(), null)
        every { getContacts(any(), any()) } returns flowOf(listOf(alice))
        val vm = viewModel()
        advanceUntilIdle()

        vm.onToggleContactSelection(1L)

        vm.events.test {
            vm.onShareSelected()
            advanceUntilIdle()
            awaitItem()
        }

        assertFalse(vm.uiState.value.isSelectionMode)
        assertEquals(emptySet<Long>(), vm.uiState.value.selectedContactIds)
    }

    @Test
    fun `onShareSelected emits nothing when selection is empty`() = runTest {
        every { getContacts(any(), any()) } returns flowOf(emptyList())
        val vm = viewModel()

        vm.events.test {
            vm.onShareSelected()
            advanceUntilIdle()
            expectNoEvents()
        }
    }

    @Test
    fun `onLinkSelected emits LinkContacts with correct ids`() = runTest {
        every { getContacts(any(), any()) } returns flowOf(emptyList())
        val vm = viewModel()

        vm.onToggleContactSelection(10L)
        vm.onToggleContactSelection(20L)

        vm.events.test {
            vm.onLinkSelected()
            advanceUntilIdle()

            val event = awaitItem() as ContactsEvent.LinkContacts
            assertEquals(setOf(10L, 20L), event.contactIds.toSet())
        }
    }

    @Test
    fun `onLinkSelected clears selection mode`() = runTest {
        every { getContacts(any(), any()) } returns flowOf(emptyList())
        val vm = viewModel()

        vm.onToggleContactSelection(10L)
        vm.onToggleContactSelection(20L)

        vm.events.test {
            vm.onLinkSelected()
            advanceUntilIdle()
            awaitItem()
        }

        assertFalse(vm.uiState.value.isSelectionMode)
        assertEquals(emptySet<Long>(), vm.uiState.value.selectedContactIds)
    }

    @Test
    fun `onLinkSelected emits nothing when fewer than 2 contacts selected`() = runTest {
        every { getContacts(any(), any()) } returns flowOf(emptyList())
        val vm = viewModel()

        vm.onToggleContactSelection(10L)

        vm.events.test {
            vm.onLinkSelected()
            advanceUntilIdle()
            expectNoEvents()
        }
    }

    @Test
    fun `onRefresh clears selection mode`() = runTest {
        every { getContacts(any(), any()) } returns flowOf(emptyList())
        val syncFlow = flowOf(false)
        coEvery { triggerContactsSync() } returns TriggerContactsSyncUseCase.Result.SyncStarted(syncFlow)
        val vm = viewModel()

        vm.onContactLongClick(1L)
        assertTrue(vm.uiState.value.isSelectionMode)

        vm.onRefresh()
        advanceTimeBy(600)

        assertFalse(vm.uiState.value.isSelectionMode)
        assertEquals(emptySet<Long>(), vm.uiState.value.selectedContactIds)
    }

    //endregion
}

private val contact = ContactItem(1L, "Alice", mockk<Uri>(), null)
private val group = com.android.contacts.group.GroupListItem(
    "acctName", "com.google", null, 42L, "Friends", true, 5, false, null,
)

private val noDataResult = GetGroupContactDataUseCase.Result(
    itemList = emptyList(),
    defaultSelectionIds = LongArray(0),
    allHaveDefaults = false,
    hasMissingContacts = true,
)
private val allDefaultsResult = GetGroupContactDataUseCase.Result(
    itemList = listOf("alice@example.com"),
    defaultSelectionIds = longArrayOf(10L),
    allHaveDefaults = true,
    hasMissingContacts = false,
)
private val allDefaultsMissingResult = GetGroupContactDataUseCase.Result(
    itemList = listOf("alice@example.com"),
    defaultSelectionIds = longArrayOf(10L),
    allHaveDefaults = true,
    hasMissingContacts = true,
)
private val needsPickerResult = GetGroupContactDataUseCase.Result(
    itemList = listOf("alice@example.com", "alice2@example.com"),
    defaultSelectionIds = LongArray(0),
    allHaveDefaults = false,
    hasMissingContacts = false,
)
