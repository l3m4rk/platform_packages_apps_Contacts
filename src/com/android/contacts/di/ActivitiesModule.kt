package com.android.contacts.di

import android.content.Context
import com.android.contacts.list.ProviderStatusWatcher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object ActivitiesModule {

    @Provides
    fun provideProviderStatusWatcher(@ApplicationContext ctx: Context): ProviderStatusWatcher =
        ProviderStatusWatcher.getInstance(ctx)
}
