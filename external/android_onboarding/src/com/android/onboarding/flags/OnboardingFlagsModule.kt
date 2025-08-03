package com.android.onboarding.flags

import dagger.Module
import dagger.Provides
import dagger.Reusable

@Module
class OnboardingFlagsModule {

  @Provides
  @Reusable
  fun provideOnboardingFlagsProvider(): OnboardingFlagsProvider = DefaultOnboardingFlagsProvider()
}
