package com.android.onboarding.flags.testing

import com.android.onboarding.flags.OnboardingFlagsProvider

/** A fake implementation of [OnboardingFlagsProvider]. */
class FakeOnboardingFlagsProvider(
  override var isContractEnabled: Boolean = false,
  override var isNodeLoggingEnabled: Boolean = false,
  override var isUiLoggingEnabled: Boolean = false,
  override var isDebug: Boolean = false,
  override val shouldVisualiseNodeTransitionsInLogcat: Boolean = false,
) : OnboardingFlagsProvider {
  @Deprecated(message = "Replaced with overrides", replaceWith = ReplaceWith("isContractEnabled"))
  var isOnboardingContractEnabledFlag: Boolean by ::isContractEnabled

  @Deprecated(
    message = "Replaced with overrides",
    replaceWith = ReplaceWith("isNodeLoggingEnabled")
  )
  var isOnboardingNodeLoggingEnabledFlag: Boolean by ::isNodeLoggingEnabled
}
