package com.android.onboarding.versions.annotations

/** Reasons that a change may break existing callers. */
enum class BreakingReason {

  /** The change introduces a new return value to an existing API. */
  NEW_RETURN_VALUE
}
