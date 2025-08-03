package com.android.onboarding.flags

import android.os.SystemProperties

/** A default implementation of [OnboardingFlagsProvider] using system property. */
class DefaultOnboardingFlagsProvider : OnboardingFlagsProvider {
  override val isContractEnabled: Boolean
    get() =
      SystemProperties.getBoolean("$SYSTEM_PROPERTY_NAMESPACE.$FEATURE_CONTRACT_ENABLED", false)
  override val isNodeLoggingEnabled: Boolean
    get() =
      SystemProperties.getBoolean("$SYSTEM_PROPERTY_NAMESPACE.$FEATURE_NODE_LOGGING_ENABLED", false)

  override val isUiLoggingEnabled: Boolean
    get() =
      SystemProperties.getBoolean("$SYSTEM_PROPERTY_NAMESPACE.$FEATURE_UI_LOGGING_ENABLED", false)

  override val isDebug: Boolean
    get() = SystemProperties.getBoolean("$SYSTEM_PROPERTY_NAMESPACE.$FEATURE_DEBUG_ENABLED", false)

  override val shouldVisualiseNodeTransitionsInLogcat: Boolean
    get() =
      SystemProperties.getBoolean(
        "$SYSTEM_PROPERTY_NAMESPACE.$FEATURE_VISUALISE_TRANSITIONS",
        false
      ) || isDebug

  companion object {
    // The key length has to be less that 31 characters (<18 if excluding namespace)
    const val SYSTEM_PROPERTY_NAMESPACE = "aoj.feature"
    private const val FEATURE_CONTRACT_ENABLED = "contract"
    private const val FEATURE_NODE_LOGGING_ENABLED = "node_logging"
    private const val FEATURE_DEBUG_ENABLED = "debug"
    private const val FEATURE_UI_LOGGING_ENABLED = "ui_logging"
    private const val FEATURE_VISUALISE_TRANSITIONS = "vis_transitions"
  }
}
