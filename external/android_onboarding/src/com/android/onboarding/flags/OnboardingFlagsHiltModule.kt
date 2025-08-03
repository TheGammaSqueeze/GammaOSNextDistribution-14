package com.android.onboarding.flags

import dagger.Module
import dagger.Provides
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal object OnboardingFlagsHiltModule {

  @Provides
  @Reusable
  fun provideOnboardingFlagsProvider(): OnboardingFlagsProvider = DefaultOnboardingFlagsProvider()
}
