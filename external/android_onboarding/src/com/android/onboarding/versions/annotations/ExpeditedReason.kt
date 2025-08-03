package com.android.onboarding.versions.annotations

/** The reason why this change can follow the expedited release process. */
enum class ExpeditedReason {
  /** The change will not follow the expedited release process. */
  NOT_EXPEDITED,

  /** DO NOT USE - only available for use by tests until we have a real non-NOT_EXPEDITED reason. */
  TMP
}
