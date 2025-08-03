package com.android.intentresolver.v2.platform

import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface SecureSettingsModule {

    @Binds @Reusable fun secureSettings(settings: PlatformSecureSettings): SecureSettings
}
