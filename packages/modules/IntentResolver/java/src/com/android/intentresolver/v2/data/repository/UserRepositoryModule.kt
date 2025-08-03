package com.android.intentresolver.v2.data.repository

import android.content.Context
import android.os.UserHandle
import android.os.UserManager
import com.android.intentresolver.inject.ApplicationUser
import com.android.intentresolver.inject.ProfileParent
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface UserRepositoryModule {
    companion object {
        @Provides
        @Singleton
        @ApplicationUser
        fun applicationUser(@ApplicationContext context: Context): UserHandle = context.user

        @Provides
        @Singleton
        @ProfileParent
        fun profileParent(
            @ApplicationContext context: Context,
            userManager: UserManager
        ): UserHandle {
            return userManager.getProfileParent(context.user) ?: context.user
        }
    }

    @Binds @Singleton fun userRepository(impl: UserRepositoryImpl): UserRepository
}
