package com.android.contacts.contacts.domain

import android.accounts.Account
import android.content.ContentResolver
import android.content.Context
import android.os.Bundle
import android.provider.ContactsContract
import com.android.contacts.di.core.IoDispatcher
import com.android.contacts.model.AccountTypeManager
import com.android.contacts.util.SyncUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject

class TriggerContactsSyncUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val accountTypeManager: AccountTypeManager,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    sealed interface Result {
        data object NoNetwork : Result
        /** Sync was requested. Collect [syncActive] to know when it finishes. */
        data class SyncStarted(val syncActive: Flow<Boolean>) : Result
    }

    suspend operator fun invoke(): Result = withContext(ioDispatcher) {
        if (!SyncUtil.isNetworkConnected(context)) return@withContext Result.NoNetwork

        val bundle = Bundle().apply {
            putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
            putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
        }

        val googleAccounts = accountTypeManager.writableGoogleAccounts
            .mapNotNull { it.account.getAccountOrNull() }

        googleAccounts
            .filter { account ->
                !SyncUtil.isSyncStatusPendingOrActive(account) ||
                    SyncUtil.isUnsyncableGoogleAccount(account)
            }
            .forEach { account: Account ->
                ContentResolver.requestSync(account, ContactsContract.AUTHORITY, bundle)
            }

        Result.SyncStarted(observeSyncActive(googleAccounts))
    }

    private fun observeSyncActive(accounts: List<Account>): Flow<Boolean> =
        callbackFlow {
            fun isAnySyncing() = accounts.any { account ->
                SyncUtil.isSyncStatusPendingOrActive(account)
            }

            val handle = ContentResolver.addStatusChangeListener(
                ContentResolver.SYNC_OBSERVER_TYPE_ACTIVE or
                    ContentResolver.SYNC_OBSERVER_TYPE_PENDING,
            ) {
                trySend(isAnySyncing())
            }

            trySend(isAnySyncing())

            awaitClose { ContentResolver.removeStatusChangeListener(handle) }
        }.flowOn(ioDispatcher)
}
