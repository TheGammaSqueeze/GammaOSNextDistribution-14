package com.android.onboarding.flags.testing

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module(includes = [TestingDaggerModule::class])
@InstallIn(SingletonComponent::class)
internal interface TestingHiltModule
