package com.android.contacts.contacts.di

import com.android.contacts.contacts.data.ContactsRepository
import com.android.contacts.contacts.data.ContactsRepositoryImpl
import com.android.contacts.contacts.data.accounts.AccountsRepository
import com.android.contacts.contacts.data.accounts.AccountsRepositoryImpl
import com.android.contacts.contacts.data.groups.GroupsRepository
import com.android.contacts.contacts.data.groups.GroupsRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ContactsModule {
    @Binds
    internal abstract fun bindContactRepository(impl: ContactsRepositoryImpl): ContactsRepository
    @Binds
    internal abstract fun bindGroupsRepository(impl: GroupsRepositoryImpl): GroupsRepository
    @Binds
    internal abstract fun bindAccountsRepository(impl: AccountsRepositoryImpl): AccountsRepository
}
