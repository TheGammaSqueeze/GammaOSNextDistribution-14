package com.android.onboarding.flags.testing

import com.android.onboarding.flags.OnboardingFlagsProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
interface TestingDaggerModule {

  companion object {
    @Provides
    @Singleton
    fun provideFakeOnboardingFlagsProvider(): FakeOnboardingFlagsProvider =
      FakeOnboardingFlagsProvider()
  }

  @Binds
  @Singleton
  fun bindOnboardingFlagsProvider(impl: FakeOnboardingFlagsProvider): OnboardingFlagsProvider
}
