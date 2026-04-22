package com.android.contacts.contacts.domain

import android.content.ContentResolver
import android.database.MatrixCursor
import android.provider.ContactsContract
import com.android.contacts.ContactsUtils
import com.android.contacts.group.GroupMembersFragment
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class GetGroupContactDataUseCaseTest {

    private val resolver = mockk<ContentResolver>()
    private val useCase = GetGroupContactDataUseCase(resolver, UnconfinedTestDispatcher())

    private fun makeEmailCursor(vararg rows: Array<Any?>): MatrixCursor =
        MatrixCursor(GroupMembersFragment.Query.EMAIL_PROJECTION).also { rows.forEach(it::addRow) }

    private fun makePhoneCursor(vararg rows: Array<Any?>): MatrixCursor =
        MatrixCursor(GroupMembersFragment.Query.PHONE_PROJECTION).also { rows.forEach(it::addRow) }

    // row: contactId, itemId, isPrimary (0/1), data
    private fun emailRow(contactId: String, itemId: Long, isPrimary: Int, email: String): Array<Any?> =
        arrayOf(contactId, itemId, isPrimary, email)

    private fun phoneRow(contactId: String, itemId: Long, isPrimary: Int, phone: String): Array<Any?> =
        arrayOf(contactId, itemId, isPrimary, phone)

    //region null / empty cursor

    @Test
    fun `null cursor returns empty result`() = runTest {
        every { resolver.query(any(), any(), any(), any(), any()) } returns null

        val result = useCase(longArrayOf(1L), ContactsUtils.SCHEME_MAILTO)

        assertTrue(result.itemList.isEmpty())
        assertTrue(result.defaultSelectionIds.isEmpty())
        assertTrue(result.hasMissingContacts)
    }

    @Test
    fun `empty cursor returns empty result with all contacts missing`() = runTest {
        every { resolver.query(any(), any(), any(), any(), any()) } returns makeEmailCursor()

        val result = useCase(longArrayOf(1L, 2L), ContactsUtils.SCHEME_MAILTO)

        assertTrue(result.itemList.isEmpty())
        assertTrue(result.hasMissingContacts)
    }

    //endregion

    //region allHaveDefaults

    @Test
    fun `single item per contact counts as having a default`() = runTest {
        every { resolver.query(any(), any(), any(), any(), any()) } returns makeEmailCursor(
            emailRow("1", 10L, 0, "alice@example.com"),
            emailRow("2", 20L, 0, "bob@example.com"),
        )

        val result = useCase(longArrayOf(1L, 2L), ContactsUtils.SCHEME_MAILTO)

        assertTrue(result.allHaveDefaults)
    }

    @Test
    fun `primary item marked counts as having a default`() = runTest {
        every { resolver.query(any(), any(), any(), any(), any()) } returns makeEmailCursor(
            emailRow("1", 10L, 1, "alice@example.com"),
            emailRow("1", 11L, 0, "alice2@example.com"),
        )

        val result = useCase(longArrayOf(1L), ContactsUtils.SCHEME_MAILTO)

        assertTrue(result.allHaveDefaults)
    }

    @Test
    fun `multiple items with no primary means no default`() = runTest {
        every { resolver.query(any(), any(), any(), any(), any()) } returns makeEmailCursor(
            emailRow("1", 10L, 0, "alice@example.com"),
            emailRow("1", 11L, 0, "alice2@example.com"),
        )

        val result = useCase(longArrayOf(1L), ContactsUtils.SCHEME_MAILTO)

        assertFalse(result.allHaveDefaults)
    }

    @Test
    fun `one contact missing default makes allHaveDefaults false`() = runTest {
        every { resolver.query(any(), any(), any(), any(), any()) } returns makeEmailCursor(
            emailRow("1", 10L, 1, "alice@example.com"), // has primary
            emailRow("2", 20L, 0, "bob@example.com"),   // single item — counts as default
            emailRow("3", 30L, 0, "carol@example.com"), // multiple, no primary
            emailRow("3", 31L, 0, "carol2@example.com"),
        )

        val result = useCase(longArrayOf(1L, 2L, 3L), ContactsUtils.SCHEME_MAILTO)

        assertFalse(result.allHaveDefaults)
    }

    //endregion

    //region defaultSelectionIds

    @Test
    fun `primary item id is used as default selection`() = runTest {
        every { resolver.query(any(), any(), any(), any(), any()) } returns makeEmailCursor(
            emailRow("1", 10L, 0, "alice@example.com"),
            emailRow("1", 11L, 1, "alice2@example.com"), // primary
        )

        val result = useCase(longArrayOf(1L), ContactsUtils.SCHEME_MAILTO)

        assertTrue(result.defaultSelectionIds.contains(11L))
        assertFalse(result.defaultSelectionIds.contains(10L))
    }

    @Test
    fun `first item id used as default when no primary`() = runTest {
        every { resolver.query(any(), any(), any(), any(), any()) } returns makeEmailCursor(
            emailRow("1", 10L, 0, "alice@example.com"),
            emailRow("1", 11L, 0, "alice2@example.com"),
        )

        val result = useCase(longArrayOf(1L), ContactsUtils.SCHEME_MAILTO)

        assertTrue(result.defaultSelectionIds.contains(10L))
    }

    //endregion

    //region hasMissingContacts

    @Test
    fun `hasMissingContacts false when all contact ids have data`() = runTest {
        every { resolver.query(any(), any(), any(), any(), any()) } returns makeEmailCursor(
            emailRow("1", 10L, 1, "alice@example.com"),
            emailRow("2", 20L, 1, "bob@example.com"),
        )

        val result = useCase(longArrayOf(1L, 2L), ContactsUtils.SCHEME_MAILTO)

        assertFalse(result.hasMissingContacts)
    }

    @Test
    fun `hasMissingContacts true when a contact id has no data`() = runTest {
        every { resolver.query(any(), any(), any(), any(), any()) } returns makeEmailCursor(
            emailRow("1", 10L, 1, "alice@example.com"),
            // contact 2 has no email
        )

        val result = useCase(longArrayOf(1L, 2L), ContactsUtils.SCHEME_MAILTO)

        assertTrue(result.hasMissingContacts)
    }

    //endregion

    //region itemList

    @Test
    fun `itemList contains all non-empty data values`() = runTest {
        every { resolver.query(any(), any(), any(), any(), any()) } returns makeEmailCursor(
            emailRow("1", 10L, 1, "alice@example.com"),
            emailRow("2", 20L, 1, "bob@example.com"),
        )

        val result = useCase(longArrayOf(1L, 2L), ContactsUtils.SCHEME_MAILTO)

        assertEquals(listOf("alice@example.com", "bob@example.com"), result.itemList)
    }

    @Test
    fun `empty data string is not added to itemList`() = runTest {
        every { resolver.query(any(), any(), any(), any(), any()) } returns makeEmailCursor(
            emailRow("1", 10L, 1, ""),
            emailRow("2", 20L, 1, "bob@example.com"),
        )

        val result = useCase(longArrayOf(1L, 2L), ContactsUtils.SCHEME_MAILTO)

        assertEquals(listOf("bob@example.com"), result.itemList)
    }

    //endregion

    //region phone scheme

    @Test
    fun `phone scheme queries phone projection`() = runTest {
        every { resolver.query(any(), any(), any(), any(), any()) } returns makePhoneCursor(
            phoneRow("1", 10L, 1, "+1234567890"),
        )

        val result = useCase(longArrayOf(1L), ContactsUtils.SCHEME_SMSTO)

        assertEquals(listOf("+1234567890"), result.itemList)
    }

    //endregion
}
