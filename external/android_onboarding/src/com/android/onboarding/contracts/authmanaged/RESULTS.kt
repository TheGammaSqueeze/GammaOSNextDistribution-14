package com.android.onboarding.contracts.authmanaged

import android.app.Activity

/** Container for extras auth_managed module */
object RESULTS {

  // Results returned by EmmChimeraActivity
  // LINT.IfChange
  const val RESULT_LAUNCH_APP_FAILED: Int = Activity.RESULT_FIRST_USER + 1
  const val RESULT_FETCH_APP_INFO_FAILED: Int = Activity.RESULT_FIRST_USER + 2
  const val RESULT_DOWNLOAD_INSTALL_FAILED: Int = Activity.RESULT_FIRST_USER + 3
  const val RESULT_DM_NOT_SUPPORTED: Int = Activity.RESULT_FIRST_USER + 4
  const val RESULT_DOWNLOAD_INSTALL_CANCELED: Int = Activity.RESULT_FIRST_USER + 5
  const val RESULT_SHOULD_SKIP_DM: Int = Activity.RESULT_FIRST_USER + 6
  const val RESULT_SETUP_SUPPRESSED_BY_CALLER: Int = Activity.RESULT_FIRST_USER + 7
  const val RESULT_DISALLOW_ADDING_MANAGED_ACCOUNT: Int = Activity.RESULT_FIRST_USER + 8
  const val RESULT_CANCELLED_FORCE_REMOVE_ACCOUNT: Int = Activity.RESULT_FIRST_USER + 9
  // LINT.ThenChange(//depot/google3/java/com/google/android/gmscore/integ/libs/common_auth/src/com/google/android/gms/common/auth/ui/ManagedAccountUtil.java)
}
