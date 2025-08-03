package com.android.intentresolver.inject

import android.content.Context
import com.android.intentresolver.logging.EventLogImpl
import dagger.Module
import dagger.Provides
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
object SingletonModule {
    @Provides @Singleton fun instanceIdSequence() = EventLogImpl.newIdSequence()

    @Provides
    @Reusable
    @ApplicationOwned
    fun resources(@ApplicationContext context: Context) = context.resources
}
