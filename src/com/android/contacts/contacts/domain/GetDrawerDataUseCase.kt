package com.android.contacts.contacts.domain

import com.android.contacts.contacts.data.accounts.AccountsRepository
import com.android.contacts.contacts.data.groups.GroupsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class GetDrawerDataUseCase @Inject constructor(
    private val groupsRepository: GroupsRepository,
    private val accountsRepository: AccountsRepository,
) {
    operator fun invoke(): Flow<DrawerData> = combine(
        groupsRepository.getGroups(),
        accountsRepository.getAccountData(),
    ) { groups, accountData ->
        DrawerData(
            groups = groups,
            accounts = accountData.accounts,
            hasGroupWritableAccounts = accountData.hasGroupWritableAccounts,
        )
    }
}
