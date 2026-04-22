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
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
        return ContactsViewModel(getContacts, getDrawerData, getGroupContactData)
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
