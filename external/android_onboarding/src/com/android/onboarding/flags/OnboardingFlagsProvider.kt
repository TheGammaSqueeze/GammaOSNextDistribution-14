package com.android.onboarding.flags

/** Interface for getting enabled state of different onboarding feature flags. */
interface OnboardingFlagsProvider {
  @Deprecated(message = "Replaced with a property", replaceWith = ReplaceWith("isContractEnabled"))
  fun isOnboardingContractEnabled(): Boolean = isContractEnabled

  /**
   * Returns `true` if the onboarding contract architecture is being used across the onboarding
   * flow.
   */
  val isContractEnabled: Boolean

  @Deprecated(
    message = "Replaced with a property",
    replaceWith = ReplaceWith("isNodeLoggingEnabled")
  )
  fun isOnboardingNodeLoggingEnabled(): Boolean = isNodeLoggingEnabled

  /** Returns `true` if onboarding node logs should be uploaded remotely. */
  val isNodeLoggingEnabled: Boolean

  /** Returns `true` if onboarding UI logging should be uploaded remotely. */
  val isUiLoggingEnabled: Boolean

  /**
   * Indicates that all onboarding components should activate the flagged changes regardless of
   * other flag values.
   */
  val isDebug: Boolean

  /** Returns true if node transitions should be visualised in logcat. */
  val shouldVisualiseNodeTransitionsInLogcat: Boolean
}
