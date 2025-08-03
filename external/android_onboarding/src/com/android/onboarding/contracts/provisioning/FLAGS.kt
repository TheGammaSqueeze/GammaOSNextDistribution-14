package com.android.onboarding.contracts.provisioning

import android.app.admin.DevicePolicyManager

/** Container for various intent flags used by provisioning */
object FLAGS {
  @get:Suppress("UNRESOLVED_REFERENCE")
  inline val FLAG_SUPPORTED_MODES_ORGANIZATION_OWNED: Int
    get() = DevicePolicyManager.FLAG_SUPPORTED_MODES_ORGANIZATION_OWNED

  @get:Suppress("UNRESOLVED_REFERENCE")
  inline val FLAG_SUPPORTED_MODES_DEVICE_OWNER: Int
    get() = DevicePolicyManager.FLAG_SUPPORTED_MODES_DEVICE_OWNER
}
