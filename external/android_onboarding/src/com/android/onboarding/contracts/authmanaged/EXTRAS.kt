package com.android.onboarding.contracts.authmanaged

/** Container for extras auth_managed module */
object EXTRAS {

  // EmmArgument extras
  // LINT.IfChange
  const val EXTRA_ACCOUNT: String = "account"
  const val EXTRA_IS_SETUP_WIZARD: String = "is_setup_wizard"
  const val EXTRA_SUPPRESS_DEVICE_MANAGEMENT: String = "suppress_account_provisioning"
  const val EXTRA_CALLING_PACKAGE: String = "calling_package"
  const val EXTRA_IS_USER_OWNER: String = "is_user_owner"
  const val EXTRA_DM_STATUS: String = "dm_status"
  const val EXTRA_UNMANAGED_WORK_PROFILE_MODE: String = "unmanaged_work_profile_mode"
  const val EXTRA_IS_UNICORN_ACCOUNT: String = "is_unicorn_account"
  const val EXTRA_FLOW: String = "flow"
  const val EXTRA_OPTIONS: String = "options"
  // LINT.ThenChange(//depot/google3/java/com/google/android/gmscore/integ/libs/common_auth/src/com/google/android/gms/common/auth/ui/ManagedAccountUtil.java)

}
