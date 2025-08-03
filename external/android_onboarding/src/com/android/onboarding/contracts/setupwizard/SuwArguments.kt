package com.android.onboarding.contracts.setupwizard

import android.os.Bundle

/**
 * Marker interface to keep track of all our contract arguments that either directly or indirectly
 * depend on [SuwArguments]
 */
interface WithSuwArguments : WithOptionalSuwArguments {
  override val suwArguments: SuwArguments
}

/**
 * Marker interface to keep track of all our contract arguments that optionally either directly or
 * indirectly depend on [SuwArguments]
 */
interface WithOptionalSuwArguments {
  val suwArguments: SuwArguments?
}

/**
 * A set of common arguments passed around to other processes during setup.
 *
 * @property isSuwSuggestedActionFlow Notifying an activity that was called from suggested action
 *   activity.
 * @property isSetupFlow Notifying an Activity that it is inside the any setup flow
 * @property preDeferredSetup Notifying an Activity that it is inside the "Pre-Deferred Setup" flow
 * @property deferredSetup Notifying an Activity that it is inside the Deferred SetupWizard flow or
 *   not
 * @property firstRun Notifying an Activity that it is inside the first SetupWizard flow or not
 * @property portalSetup Notifying an Activity that it is inside the "Portal Setup" flow
 * @property wizardBundle Parcelable extra containing the internal reference for an intent
 *   dispatched by Wizard Manager
 * @property theme Decide which theme should be used in the onboarding flow
 * @property hasMultipleUsers show additional info regarding the use of a device with multiple users
 * @property isSubactivityFirstLaunched Return true if the ActivityWrapper is Created and not start
 *   SubActivity yet
 */
interface SuwArguments {
  val isSuwSuggestedActionFlow: Boolean
  val isSetupFlow: Boolean
  val preDeferredSetup: Boolean
  val deferredSetup: Boolean
  val firstRun: Boolean
  val portalSetup: Boolean
  val wizardBundle: Bundle
  val theme: String
  val hasMultipleUsers: Boolean?
  val isSubactivityFirstLaunched: Boolean?

  companion object {
    @JvmStatic
    @JvmName("of")
    operator fun invoke(
      isSuwSuggestedActionFlow: Boolean,
      isSetupFlow: Boolean,
      preDeferredSetup: Boolean,
      deferredSetup: Boolean,
      firstRun: Boolean,
      portalSetup: Boolean,
      wizardBundle: Bundle,
      theme: String,
      hasMultipleUsers: Boolean?,
      isSubactivityFirstLaunched: Boolean?,
    ): SuwArguments =
      object : SuwArguments {
        override val isSuwSuggestedActionFlow = isSuwSuggestedActionFlow
        override val isSetupFlow = isSetupFlow
        override val preDeferredSetup = preDeferredSetup
        override val deferredSetup = deferredSetup
        override val firstRun = firstRun
        override val portalSetup = portalSetup
        override val wizardBundle = wizardBundle
        override val theme = theme
        override val hasMultipleUsers = hasMultipleUsers
        override val isSubactivityFirstLaunched = isSubactivityFirstLaunched
      }
  }
}
