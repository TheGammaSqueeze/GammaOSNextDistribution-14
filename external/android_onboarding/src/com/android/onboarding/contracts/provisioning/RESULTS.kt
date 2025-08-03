package com.android.onboarding.contracts.provisioning

import android.app.admin.DevicePolicyManager

object RESULTS {
  @get:Suppress("UNRESOLVED_REFERENCE")
  inline val RESULT_UPDATE_ROLE_HOLDER: Int
    get() = DevicePolicyManager.RESULT_UPDATE_ROLE_HOLDER
}
