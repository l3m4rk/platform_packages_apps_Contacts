package com.android.contacts.contacts.ui

import android.net.Uri
import com.android.contacts.contacts.domain.GetContactsUseCase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ContactsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val getContacts: GetContactsUseCase = mockk()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = ContactsViewModel(getContacts)

    //region Initial state

    @Test
    fun `initial state has empty contacts and isLoading false`() = runTest {
        every { getContacts(any()) } returns flowOf(emptyList())
        val vm = viewModel()
        advanceTimeBy(400) // past debounce

        assertEquals(emptyList<ContactItem>(), vm.uiState.value.contacts)
        assertFalse(vm.uiState.value.isLoading)
    }

    //endregion

    //region Search open / close

    @Test
    fun `onSearchOpen sets isSearchActive true`() {
        every { getContacts(any()) } returns flowOf(emptyList())
        val vm = viewModel()

        vm.onSearchOpen()

        assertTrue(vm.uiState.value.isSearchActive)
    }

    @Test
    fun `onSearchClosed resets isSearchActive and query`() {
        every { getContacts(any()) } returns flowOf(emptyList())
        val vm = viewModel()

        vm.onSearchOpen()
        vm.onSearchQueryChanged("alex")
        vm.onSearchClosed()

        assertFalse(vm.uiState.value.isSearchActive)
        assertEquals("", vm.uiState.value.searchQuery)
    }

    @Test
    fun `onSearchQueryChanged updates searchQuery in state`() {
        every { getContacts(any()) } returns flowOf(emptyList())
        val vm = viewModel()

        vm.onSearchQueryChanged("bob")

        assertEquals("bob", vm.uiState.value.searchQuery)
    }

    //endregion

    //region Search pipeline

    @Test
    fun `contacts emitted after debounce`() = runTest {
        val contact = ContactItem(1L, "Alice", mockk<Uri>(), null)
        every { getContacts("") } returns flowOf(emptyList())
        every { getContacts("ali") } returns flowOf(listOf(contact))
        val vm = viewModel()
        advanceTimeBy(400) // settle initial state

        vm.onSearchQueryChanged("ali")
        advanceTimeBy(400) // past 300ms debounce

        assertEquals(listOf(contact), vm.uiState.value.contacts)
    }

    @Test
    fun `rapid query changes only emit for the last one (debounce)`() = runTest {
        val contact = ContactItem(1L, "Bob", mockk<Uri>(), null)
        every { getContacts("") } returns flowOf(emptyList())
        every { getContacts("b") } returns flowOf(emptyList())
        every { getContacts("bo") } returns flowOf(emptyList())
        every { getContacts("bob") } returns flowOf(listOf(contact))
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
        every { getContacts("") } returns flowOf(emptyList())
        every { getContacts("err") } returns flow { throw RuntimeException("network error") }
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
        every { getContacts("") } returns flowOf(emptyList())
        every { getContacts("err") } returns flow { throw RuntimeException("fail") }
        every { getContacts("car") } returns flowOf(listOf(contact))
        val vm = viewModel()
        advanceTimeBy(400)

        vm.onSearchQueryChanged("err")
        advanceTimeBy(400)

        vm.onSearchQueryChanged("car")
        advanceTimeBy(400)

        assertEquals(listOf(contact), vm.uiState.value.contacts)
    }

    //endregion
}
