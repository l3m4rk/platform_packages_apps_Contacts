package com.android.contacts.contacts.di

import com.android.contacts.contacts.data.ContactsRepository
import com.android.contacts.contacts.data.ContactsRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface ContactsModule {
    @Binds
    fun bindContactRepository(impl: ContactsRepositoryImpl): ContactsRepository
}
