package com.android.contacts.contacts.domain

import com.android.contacts.contacts.data.accounts.AccountData
import com.android.contacts.contacts.data.accounts.AccountsRepository
import com.android.contacts.contacts.data.groups.GroupsRepository
import com.android.contacts.group.GroupListItem
import com.android.contacts.list.ContactListFilter
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GetDrawerDataUseCaseTest {

    private val groupsRepo = mockk<GroupsRepository>()
    private val accountsRepo = mockk<AccountsRepository>()
    private val useCase = GetDrawerDataUseCase(groupsRepo, accountsRepo)

    private val group = GroupListItem(
        "acctName",
        "com.google",
        null,
        1L,
        "Friends",
        true,
        5,
        false,
        null,
    )
    private val filter = mockk<ContactListFilter>()

    //region combines groups and accounts

    @Test
    fun `groups are passed through to DrawerData`() = runTest {
        every { groupsRepo.getGroups() } returns flowOf(listOf(group))
        every { accountsRepo.getAccountData() } returns flowOf(
            AccountData(emptyList(), hasGroupWritableAccounts = false)
        )

        assertEquals(listOf(group), useCase().first().groups)
    }

    @Test
    fun `accounts are passed through to DrawerData`() = runTest {
        every { groupsRepo.getGroups() } returns flowOf(emptyList())
        every { accountsRepo.getAccountData() } returns flowOf(
            AccountData(listOf(filter), hasGroupWritableAccounts = false)
        )

        assertEquals(listOf(filter), useCase().first().accounts)
    }

    @Test
    fun `empty groups and accounts produces empty DrawerData`() = runTest {
        every { groupsRepo.getGroups() } returns flowOf(emptyList())
        every { accountsRepo.getAccountData() } returns flowOf(
            AccountData(emptyList(), hasGroupWritableAccounts = false)
        )

        val result = useCase().first()

        assertTrue(result.groups.isEmpty())
        assertTrue(result.accounts.isEmpty())
        assertFalse(result.hasGroupWritableAccounts)
    }

    @Test
    fun `hasGroupWritableAccounts true is propagated`() = runTest {
        every { groupsRepo.getGroups() } returns flowOf(emptyList())
        every { accountsRepo.getAccountData() } returns flowOf(
            AccountData(emptyList(), hasGroupWritableAccounts = true)
        )

        assertTrue(useCase().first().hasGroupWritableAccounts)
    }

    @Test
    fun `hasGroupWritableAccounts false is propagated`() = runTest {
        every { groupsRepo.getGroups() } returns flowOf(emptyList())
        every { accountsRepo.getAccountData() } returns flowOf(
            AccountData(emptyList(), hasGroupWritableAccounts = false)
        )

        assertFalse(useCase().first().hasGroupWritableAccounts)
    }

    //endregion
}
