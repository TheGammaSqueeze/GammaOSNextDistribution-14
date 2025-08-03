package com.android.managedprovisioning

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(ActivityComponent::class, SingletonComponent::class)
class Module {
    @Provides
    fun defaultScreenManager(): ScreenManager =
        ScreenManager(ScreenManager.DEFAULT_SCREEN_TO_ACTIVITY_MAP)
}