package com.android.contacts.contacts.data.accounts

import android.content.Context
import com.android.contacts.di.core.IoDispatcher
import com.android.contacts.list.ContactListFilter
import com.android.contacts.model.AccountTypeManager
import com.google.common.util.concurrent.Futures
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

interface AccountsRepository {
    fun getAccountData(): Flow<AccountData>
}

internal class AccountsRepositoryImpl @Inject constructor(
    private val accountTypeManager: AccountTypeManager,
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AccountsRepository {


    override fun getAccountData(): Flow<AccountData> = flow {
        val accounts = Futures.getUnchecked(
            accountTypeManager
                .filterAccountsAsync(AccountTypeManager.drawerDisplayableFilter()),
        )
        emit(
            AccountData(
                accounts = accounts.map { accountInfo ->
                    val account = accountInfo.account
                    ContactListFilter.createAccountFilter(
                        account.type,
                        account.name,
                        account.dataSet,
                        accountInfo.type.getDisplayIcon(context),
                    )
                },
                hasGroupWritableAccounts = accounts.any { it.type.isGroupMembershipEditable },
            ),
        )
    }.flowOn(ioDispatcher)
}
