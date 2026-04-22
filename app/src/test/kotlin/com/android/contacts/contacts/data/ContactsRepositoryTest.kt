package com.android.contacts.contacts.data

import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.database.MatrixCursor
import android.net.Uri
import android.provider.ContactsContract
import app.cash.turbine.test
import com.android.contacts.contacts.domain.ContactsFilter
import com.android.contacts.list.ContactListFilter
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ContactsRepositoryTest {

    private val context = mockk<Context>()
    private val resolver = mockk<ContentResolver>()
    private val repo = ContactsRepositoryImpl(context, UnconfinedTestDispatcher())

    @Before
    fun setUp() {
        every { context.contentResolver } returns resolver
        every { resolver.registerContentObserver(any(), any(), any()) } just runs
        every { resolver.unregisterContentObserver(any()) } just runs
    }

    private fun makeContactsCursor(vararg rows: Array<Any?>): MatrixCursor {
        return MatrixCursor(
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.CONTACT_PRESENCE,
                ContactsContract.Contacts.CONTACT_STATUS,
                ContactsContract.Contacts.PHOTO_ID,
                ContactsContract.Contacts.PHOTO_THUMBNAIL_URI,
                ContactsContract.Contacts.LOOKUP_KEY,
                ContactsContract.Contacts.PHONETIC_NAME,
                ContactsContract.Contacts.STARRED,
            )
        ).also { rows.forEach(it::addRow) }
    }

    //region Group filtering

    @Test
    fun `group filter with no members returns empty list without second query`() = runTest {
        every { resolver.query(any(), any(), any(), any(), any()) } returns
            MatrixCursor(arrayOf(ContactsContract.Data.CONTACT_ID))

        val result = repo.getContacts(filter = ContactsFilter.ByGroup(1L)).first()

        assertEquals(emptyList<Any>(), result)
    }

    @Test
    fun `group filter null data cursor returns empty list`() = runTest {
        every { resolver.query(any(), any(), any(), any(), any()) } returns null

        val result = repo.getContacts(filter = ContactsFilter.ByGroup(1L)).first()

        assertEquals(emptyList<Any>(), result)
    }

    @Test
    fun `group filter maps contact fields correctly`() = runTest {
        every { resolver.query(any(), any(), any(), any(), any()) } answers {
            val uri = firstArg<Uri>()
            if (uri == ContactsContract.Data.CONTENT_URI) {
                MatrixCursor(arrayOf(ContactsContract.Data.CONTACT_ID)).apply { addRow(arrayOf(42L)) }
            } else {
                makeContactsCursor(arrayOf(42L, "Alice", null, null, null, null, "abc123", null, null))
            }
        }

        val result = repo.getContacts(filter = ContactsFilter.ByGroup(1L)).first()

        assertEquals(1, result.size)
        assertEquals(42L, result[0].id)
        assertEquals("Alice", result[0].displayName)
    }

    @Test
    fun `group filter uses correct group row id in query`() = runTest {
        val selectionArgsSlot = slot<Array<String>>()
        every { resolver.query(eq(ContactsContract.Data.CONTENT_URI), any(), any(), capture(selectionArgsSlot), any()) } returns
            MatrixCursor(arrayOf(ContactsContract.Data.CONTACT_ID))

        repo.getContacts(filter = ContactsFilter.ByGroup(99L)).first()

        assertEquals("99", selectionArgsSlot.captured[1])
    }

    //endregion

    //region Account filtering

    @Test
    fun `account filter appends account name and type to URI`() = runTest {
        val uriSlot = slot<Uri>()
        every { resolver.query(capture(uriSlot), any(), any(), any(), any()) } returns makeContactsCursor()
        val filter = ContactListFilter.createAccountFilter("com.google", "user@gmail.com", null, null)

        repo.getContacts(filter = ContactsFilter.ByAccount(filter)).first()

        val capturedUri = uriSlot.captured
        assertEquals("user@gmail.com", capturedUri.getQueryParameter(ContactsContract.RawContacts.ACCOUNT_NAME))
        assertEquals("com.google", capturedUri.getQueryParameter(ContactsContract.RawContacts.ACCOUNT_TYPE))
    }

    @Test
    fun `account filter returns contacts from cursor`() = runTest {
        every { resolver.query(any(), any(), any(), any(), any()) } returns
            makeContactsCursor(arrayOf(7L, "Bob", null, null, null, null, "xyz", null, null))
        val filter = ContactListFilter.createAccountFilter("com.google", "user@gmail.com", null, null)

        val result = repo.getContacts(filter = ContactsFilter.ByAccount(filter)).first()

        assertEquals(1, result.size)
        assertEquals("Bob", result[0].displayName)
    }

    //endregion

    //region AllContacts (smoke test)

    @Test
    fun `all contacts filter uses plain Contacts URI`() = runTest {
        val uriSlot = slot<Uri>()
        every { resolver.query(capture(uriSlot), any(), any(), any(), any()) } returns makeContactsCursor()

        repo.getContacts(filter = ContactsFilter.AllContacts).first()

        assertEquals(ContactsContract.Contacts.CONTENT_URI, uriSlot.captured)
    }

    //endregion

    //region Reactive updates

    @Test
    fun `content change triggers re-query and emits updated list`() = runTest {
        val observerSlot = slot<ContentObserver>()
        every { resolver.registerContentObserver(any(), any(), capture(observerSlot)) } just runs

        val firstCursor = makeContactsCursor(arrayOf(1L, "Alice", null, null, null, null, "k1", null, null))
        val secondCursor = makeContactsCursor(
            arrayOf(1L, "Alice", null, null, null, null, "k1", null, null),
            arrayOf(2L, "Bob", null, null, null, null, "k2", null, null),
        )
        var callCount = 0
        every { resolver.query(any(), any(), any(), any(), any()) } answers {
            if (callCount++ == 0) firstCursor else secondCursor
        }

        repo.getContacts(filter = ContactsFilter.AllContacts).test {
            assertEquals(1, awaitItem().size) // initial emission

            observerSlot.captured.onChange(false) // simulate DB change

            assertEquals(2, awaitItem().size) // re-query result
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `group view observes Data URI for reactive updates`() = runTest {
        val observedUriSlot = slot<Uri>()
        every { resolver.registerContentObserver(capture(observedUriSlot), any(), any()) } just runs
        every { resolver.query(any(), any(), any(), any(), any()) } returns
            MatrixCursor(arrayOf(ContactsContract.Data.CONTACT_ID))

        repo.getContacts(filter = ContactsFilter.ByGroup(1L)).first()

        assertEquals(ContactsContract.Data.CONTENT_URI, observedUriSlot.captured)
    }

    @Test
    fun `non-group view observes Contacts URI for reactive updates`() = runTest {
        val observedUriSlot = slot<Uri>()
        every { resolver.registerContentObserver(capture(observedUriSlot), any(), any()) } just runs
        every { resolver.query(any(), any(), any(), any(), any()) } returns makeContactsCursor()

        repo.getContacts(filter = ContactsFilter.AllContacts).first()

        assertEquals(ContactsContract.Contacts.CONTENT_URI, observedUriSlot.captured)
    }

    //endregion
}
