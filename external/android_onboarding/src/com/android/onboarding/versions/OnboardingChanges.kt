package com.android.onboarding.versions

import java.lang.UnsupportedOperationException

/** Entry point to checking if processes support particular change ids. */
interface OnboardingChanges {

  /**
   * True if the current executing process supports the given change ID.
   *
   * The [changeId] must reference a long constant annotated with
   * [com.android.onboarding.versions.annotations.ChangeId]
   */
  fun currentProcessSupportsChange(changeId: Long): Boolean

  /**
   * Throws an [UnsupportedOperationException] if the current executing process does not support the
   * given change ID.
   *
   * The changeId must reference a long constant annotated with
   * [com.android.onboarding.versions.annotations.ChangeId]
   */
  fun requireCurrentProcessSupportsChange(changeId: Long)

  /**
   * True if the given component supports the given change ID.
   *
   * The changeId must reference a long constant annotated with
   * [com.android.onboarding.versions.annotations.ChangeId]
   *
   * If the component has not had [loadSupportedChanges] called previously, then it will be called
   * now. It is recommended that [loadSupportedChanges] is called ahead-of-time for all relevant
   * components to avoid arbitrary delays.
   *
   * If the component does not exist or is not valid, then it is assumed to support all released
   * changes but no unreleased changes.
   */
  fun componentSupportsChange(component: String, changeId: Long): Boolean

  /**
   * Load the supported changes for the given component.
   *
   * This call involves IPC, and should ideally be performed in the background ahead of time.
   */
  fun loadSupportedChanges(component: String)
}
