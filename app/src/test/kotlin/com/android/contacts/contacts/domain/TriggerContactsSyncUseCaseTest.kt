package com.android.contacts.contacts.domain

import android.accounts.Account
import android.content.ContentResolver
import android.content.Context
import android.content.SyncStatusObserver
import android.provider.ContactsContract
import app.cash.turbine.test
import com.android.contacts.model.AccountTypeManager
import com.android.contacts.model.account.AccountInfo
import com.android.contacts.model.account.AccountWithDataSet
import com.android.contacts.util.SyncUtil
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TriggerContactsSyncUseCaseTest {

    private val context = mockk<Context>()
    private val accountTypeManager = mockk<AccountTypeManager>()
    private val useCase = TriggerContactsSyncUseCase(
        context, accountTypeManager, UnconfinedTestDispatcher(),
    )

    private val googleAccount = Account("alice@gmail.com", "com.google")

    @Before
    fun setUp() {
        mockkStatic(SyncUtil::class)
        mockkStatic(ContentResolver::class)
        // default: static methods are no-ops unless overridden in each test
        every { ContentResolver.requestSync(any(), any(), any()) } just runs
        every { ContentResolver.addStatusChangeListener(any(), any()) } returns mockk()
        every { ContentResolver.removeStatusChangeListener(any()) } just runs
        every { SyncUtil.isSyncStatusPendingOrActive(any()) } returns false
        every { SyncUtil.isUnsyncableGoogleAccount(any()) } returns false
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // region network check

    @Test
    fun `no network returns NoNetwork`() = runTest {
        every { SyncUtil.isNetworkConnected(context) } returns false

        val result = useCase()

        assertEquals(TriggerContactsSyncUseCase.Result.NoNetwork, result)
    }

    @Test
    fun `network available returns SyncStarted`() = runTest {
        every { SyncUtil.isNetworkConnected(context) } returns true
        stubAccounts(googleAccount)

        val result = useCase()

        assertTrue(result is TriggerContactsSyncUseCase.Result.SyncStarted)
    }

    // endregion

    // region requestSync

    @Test
    fun `syncable account triggers requestSync`() = runTest {
        every { SyncUtil.isNetworkConnected(context) } returns true
        every { SyncUtil.isSyncStatusPendingOrActive(googleAccount) } returns false
        stubAccounts(googleAccount)

        useCase()

        verify { ContentResolver.requestSync(googleAccount, ContactsContract.AUTHORITY, any()) }
    }

    @Test
    fun `account already syncing skips requestSync`() = runTest {
        every { SyncUtil.isNetworkConnected(context) } returns true
        every { SyncUtil.isSyncStatusPendingOrActive(googleAccount) } returns true
        every { SyncUtil.isUnsyncableGoogleAccount(googleAccount) } returns false
        stubAccounts(googleAccount)

        useCase()

        verify(exactly = 0) { ContentResolver.requestSync(any(), any(), any()) }
    }

    @Test
    fun `unsyncable google account triggers requestSync even when pending`() = runTest {
        every { SyncUtil.isNetworkConnected(context) } returns true
        every { SyncUtil.isSyncStatusPendingOrActive(googleAccount) } returns true
        every { SyncUtil.isUnsyncableGoogleAccount(googleAccount) } returns true
        stubAccounts(googleAccount)

        useCase()

        verify { ContentResolver.requestSync(googleAccount, ContactsContract.AUTHORITY, any()) }
    }

    @Test
    fun `no google accounts does not call requestSync`() = runTest {
        every { SyncUtil.isNetworkConnected(context) } returns true
        stubAccounts() // empty

        useCase()

        verify(exactly = 0) { ContentResolver.requestSync(any(), any(), any()) }
    }

    @Test
    fun `only non-syncing accounts get requestSync when multiple accounts present`() = runTest {
        val syncingAccount = Account("syncing@gmail.com", "com.google")
        val idleAccount = Account("idle@gmail.com", "com.google")
        every { SyncUtil.isNetworkConnected(context) } returns true
        every { SyncUtil.isSyncStatusPendingOrActive(syncingAccount) } returns true
        every { SyncUtil.isUnsyncableGoogleAccount(syncingAccount) } returns false
        every { SyncUtil.isSyncStatusPendingOrActive(idleAccount) } returns false
        stubAccounts(syncingAccount, idleAccount)

        useCase()

        verify(exactly = 0) { ContentResolver.requestSync(syncingAccount, any(), any()) }
        verify(exactly = 1) { ContentResolver.requestSync(idleAccount, ContactsContract.AUTHORITY, any()) }
    }

    @Test
    fun `requestSync bundle has EXPEDITED and MANUAL extras`() = runTest {
        every { SyncUtil.isNetworkConnected(context) } returns true
        stubAccounts(googleAccount)

        val bundleSlot = slot<android.os.Bundle>()
        every { ContentResolver.requestSync(any(), any(), capture(bundleSlot)) } just runs

        useCase()

        assertTrue(bundleSlot.captured.getBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED))
        assertTrue(bundleSlot.captured.getBoolean(ContentResolver.SYNC_EXTRAS_MANUAL))
    }

    // endregion

    // region syncActive flow

    @Test
    fun `syncActive emits false when no account is syncing`() = runTest {
        every { SyncUtil.isNetworkConnected(context) } returns true
        every { SyncUtil.isSyncStatusPendingOrActive(googleAccount) } returns false
        stubAccounts(googleAccount)

        val result = useCase() as TriggerContactsSyncUseCase.Result.SyncStarted

        result.syncActive.test {
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `syncActive emits true when account is syncing`() = runTest {
        every { SyncUtil.isNetworkConnected(context) } returns true
        every { SyncUtil.isSyncStatusPendingOrActive(googleAccount) } returns true
        stubAccounts(googleAccount)

        val result = useCase() as TriggerContactsSyncUseCase.Result.SyncStarted

        result.syncActive.test {
            assertTrue(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `syncActive emits updated value when observer fires`() = runTest {
        every { SyncUtil.isNetworkConnected(context) } returns true
        every { SyncUtil.isSyncStatusPendingOrActive(googleAccount) } returns false
        stubAccounts(googleAccount)

        val observerSlot = slot<SyncStatusObserver>()
        every { ContentResolver.addStatusChangeListener(any(), capture(observerSlot)) } returns mockk()

        val result = useCase() as TriggerContactsSyncUseCase.Result.SyncStarted

        result.syncActive.test {
            assertFalse(awaitItem()) // initial: not syncing

            // Sync starts — observer fires
            every { SyncUtil.isSyncStatusPendingOrActive(googleAccount) } returns true
            observerSlot.captured.onStatusChanged(0)
            assertTrue(awaitItem())

            // Sync finishes — observer fires again
            every { SyncUtil.isSyncStatusPendingOrActive(googleAccount) } returns false
            observerSlot.captured.onStatusChanged(0)
            assertFalse(awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    // endregion

    // region helpers

    private fun stubAccounts(vararg accounts: Account) {
        val infos = accounts.map { account ->
            mockk<AccountInfo> {
                every { getAccount() } returns AccountWithDataSet(account.name, account.type, null)
            }
        }
        every { accountTypeManager.writableGoogleAccounts } returns infos
    }

    // endregion
}
