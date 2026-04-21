package com.android.contacts.contacts.data.accounts

import android.content.Context
import android.graphics.drawable.Drawable
import com.android.contacts.list.ContactListFilter
import com.android.contacts.model.AccountTypeManager
import com.android.contacts.model.account.AccountInfo
import com.android.contacts.model.account.AccountType
import com.android.contacts.model.account.AccountWithDataSet
import com.google.common.util.concurrent.Futures
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AccountsRepositoryTest {

    private val context = mockk<Context>(relaxed = true)
    private val accountTypeManager = mockk<AccountTypeManager>()
    private val dispatcher = UnconfinedTestDispatcher()
    private val repo = AccountsRepositoryImpl(accountTypeManager, context, dispatcher)

    private fun makeAccountInfo(
        name: String,
        type: String,
        dataSet: String? = null,
        isGroupMembershipEditable: Boolean = false,
        displayName: String = name,
    ): AccountInfo {
        val accountType = mockk<AccountType> {
            every { getDisplayIcon(context) } returns mockk<Drawable>()
            every { isGroupMembershipEditable() } returns isGroupMembershipEditable
        }
        val account = AccountWithDataSet(name, type, dataSet)
        return mockk<AccountInfo> {
            every { getAccount() } returns account
            every { getType() } returns accountType
            every { getNameLabel() } returns displayName
            every { getIcon() } returns null
        }
    }

    @Before
    fun setUp() {
        every {
            accountTypeManager.filterAccountsAsync(any())
        } returns Futures.immediateFuture(emptyList())
    }

    //region accounts mapping

    @Test
    fun `empty accounts list emits empty AccountData`() = runTest {
        val result = repo.getAccountData().first()
        assertTrue(result.accounts.isEmpty())
        assertFalse(result.hasGroupWritableAccounts)
    }

    @Test
    fun `single account maps filter fields correctly`() = runTest {
        val info = makeAccountInfo("john@gmail.com", "com.google")
        every { accountTypeManager.filterAccountsAsync(any()) } returns
            Futures.immediateFuture(listOf(info))

        val result = repo.getAccountData().first()

        assertEquals(1, result.accounts.size)
        assertEquals("john@gmail.com", result.accounts[0].filter.accountName)
        assertEquals("com.google", result.accounts[0].filter.accountType)
        assertEquals(ContactListFilter.FILTER_TYPE_ACCOUNT, result.accounts[0].filter.filterType)
    }

    @Test
    fun `single account display name comes from AccountInfo getNameLabel`() = runTest {
        val info = makeAccountInfo("john@gmail.com", "com.google", displayName = "John's Google")
        every { accountTypeManager.filterAccountsAsync(any()) } returns
            Futures.immediateFuture(listOf(info))

        val result = repo.getAccountData().first()

        assertEquals("John's Google", result.accounts[0].displayName)
    }

    @Test
    fun `dataSet is mapped correctly`() = runTest {
        val info = makeAccountInfo("john@gmail.com", "com.google", dataSet = "myDataSet")
        every { accountTypeManager.filterAccountsAsync(any()) } returns
            Futures.immediateFuture(listOf(info))

        val result = repo.getAccountData().first()

        assertEquals("myDataSet", result.accounts[0].filter.dataSet)
    }

    @Test
    fun `multiple accounts all mapped`() = runTest {
        val infos = listOf(
            makeAccountInfo("a@gmail.com", "com.google"),
            makeAccountInfo("b@exchange.com", "com.microsoft.exchange"),
        )
        every { accountTypeManager.filterAccountsAsync(any()) } returns
            Futures.immediateFuture(infos)

        val result = repo.getAccountData().first()

        assertEquals(2, result.accounts.size)
        assertEquals("a@gmail.com", result.accounts[0].filter.accountName)
        assertEquals("b@exchange.com", result.accounts[1].filter.accountName)
    }

    //endregion

    //region hasGroupWritableAccounts

    @Test
    fun `hasGroupWritableAccounts false when no accounts are group writable`() = runTest {
        val infos = listOf(
            makeAccountInfo("a@gmail.com", "com.google", isGroupMembershipEditable = false),
        )
        every { accountTypeManager.filterAccountsAsync(any()) } returns
            Futures.immediateFuture(infos)

        assertFalse(repo.getAccountData().first().hasGroupWritableAccounts)
    }

    @Test
    fun `hasGroupWritableAccounts true when at least one account is group writable`() = runTest {
        val infos = listOf(
            makeAccountInfo("a@gmail.com", "com.google", isGroupMembershipEditable = false),
            makeAccountInfo("b@gmail.com", "com.google", isGroupMembershipEditable = true),
        )
        every { accountTypeManager.filterAccountsAsync(any()) } returns
            Futures.immediateFuture(infos)

        assertTrue(repo.getAccountData().first().hasGroupWritableAccounts)
    }

    //endregion
}
