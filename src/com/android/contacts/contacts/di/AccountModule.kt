package com.android.contacts.contacts.di

import android.content.Context
import com.android.contacts.model.AccountTypeManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AccountModule {

    @Singleton
    @Provides
    fun provideAccountTypeManager(@ApplicationContext context: Context): AccountTypeManager =
        AccountTypeManager.getInstance(context)
}
