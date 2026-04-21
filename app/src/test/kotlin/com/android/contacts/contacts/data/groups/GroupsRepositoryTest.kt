package com.android.contacts.contacts.data.groups

import android.content.ContentResolver
import android.content.Context
import android.database.MatrixCursor
import android.provider.ContactsContract
import androidx.compose.ui.graphics.vector.Group
import com.android.contacts.group.GroupListItem
import io.mockk.every
import io.mockk.mockk
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
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
class GroupsRepositoryTest {

    private val context = mockk<Context>()
    private val resolver = mockk<ContentResolver>()
    private val repo = GroupsRepositoryImpl(context, UnconfinedTestDispatcher())

    @Before
    fun setUp() {
        every { context.contentResolver } returns resolver
    }

    private fun makeCursor(vararg rows: Array<Any?>): MatrixCursor {
        val cursor = MatrixCursor(
            arrayOf(
                ContactsContract.Groups.ACCOUNT_NAME,
                ContactsContract.Groups.ACCOUNT_TYPE,
                ContactsContract.Groups.DATA_SET,
                ContactsContract.Groups._ID,
                ContactsContract.Groups.TITLE,
                ContactsContract.Groups.SUMMARY_COUNT,
                ContactsContract.Groups.GROUP_IS_READ_ONLY,
                ContactsContract.Groups.SYSTEM_ID,
            ),
        )
        rows.forEach { cursor.addRow(it) }
        return cursor
    }

    @Test
    fun `null cursor emits empty list`() = runTest {
        every { resolver.query(any(), any(), any(), any(), any()) } returns null
        assertEquals(emptyList<GroupListItem>(), repo.getGroups().first())
    }

    @Test
    fun `empty cursor emits empty list`() = runTest {
        every { resolver.query(any(), any(), any(), any(), any()) } returns makeCursor()
        assertEquals(emptyList<GroupListItem>(), repo.getGroups().first())
    }

    @Test
    fun `single group maps fields correctly`() = runTest {
        every { resolver.query(any(), any(), any(), any(), any()) } returns
            makeCursor(arrayOf("acctName", "com.google", null, 42L, "Friends", 5, 0, null))

        val result = repo.getGroups().first()

        assertEquals(1, result.size)
        assertEquals(42L, result[0].groupId)
        assertEquals("Friends", result[0].title)
        assertEquals(5, result[0].memberCount)
        assertTrue(result[0].isFirstGroupInAccount)
    }

    @Test
    fun `two groups same account second is not first in account`() = runTest {
        every { resolver.query(any(), any(), any(), any(), any()) } returns makeCursor(
            arrayOf("acct", "com.google", null, 1L, "A", 0, 0, null),
            arrayOf("acct", "com.google", null, 2L, "B", 0, 0, null),
        )

        val result = repo.getGroups().first()

        assertTrue(result[0].isFirstGroupInAccount)
        assertFalse(result[1].isFirstGroupInAccount)
    }

    @Test
    fun `two groups different accounts both are first in account`() = runTest {
        every { resolver.query(any(), any(), any(), any(), any()) } returns makeCursor(
            arrayOf("acct1", "com.google", null, 1L, "A", 0, 0, null),
            arrayOf("acct2", "com.exchange", null, 2L, "B", 0, 0, null),
        )
        val result = repo.getGroups().first()
        assertTrue(result[0].isFirstGroupInAccount)
        assertTrue(result[1].isFirstGroupInAccount)
    }

    //region FFC filtering

    @Test
    fun `empty FFC group is filtered out`() = runTest {
        // readOnly=1, systemId="Friends", memberCount=0 → should be removed
        every { resolver.query(any(), any(), any(), any(), any()) } returns makeCursor(
            arrayOf("acct", "com.google", null, 1L, "Friends", 0, 1, "Friends"),
        )
        assertEquals(emptyList<GroupListItem>(), repo.getGroups().first())
    }

    @Test
    fun `non-empty FFC group is kept`() = runTest {
        // readOnly=1, systemId="Friends", memberCount=5 → should be kept
        every { resolver.query(any(), any(), any(), any(), any()) } returns makeCursor(
            arrayOf("acct", "com.google", null, 1L, "Friends", 5, 1, "Friends"),
        )
        assertEquals(1, repo.getGroups().first().size)
    }

    @Test
    fun `user group with FFC-like title but no systemId is kept`() = runTest {
        // systemId=null → not an FFC group, keep it regardless of memberCount
        every { resolver.query(any(), any(), any(), any(), any()) } returns makeCursor(
            arrayOf("acct", "com.google", null, 1L, "Friends", 0, 0, null),
        )
        assertEquals(1, repo.getGroups().first().size)
    }

    //endregion
}
