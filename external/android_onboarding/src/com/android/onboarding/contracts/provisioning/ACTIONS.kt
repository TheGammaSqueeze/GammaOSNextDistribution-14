package com.android.onboarding.contracts.provisioning

import android.app.admin.DevicePolicyManager
import android.nfc.NfcAdapter
import android.os.Build
import androidx.annotation.RequiresApi

object ACTIONS {
  inline val ACTION_MANAGED_PROFILE_PROVISIONED: String
    get() = DevicePolicyManager.ACTION_MANAGED_PROFILE_PROVISIONED

  inline val ACTION_PROVISIONING_SUCCESSFUL: String
    get() = DevicePolicyManager.ACTION_PROVISIONING_SUCCESSFUL

  inline val ACTION_ADD_DEVICE_ADMIN: String
    get() = DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN

  @get:Suppress("UNRESOLVED_REFERENCE")
  inline val ACTION_SET_PROFILE_OWNER: String
    get() = DevicePolicyManager.ACTION_SET_PROFILE_OWNER

  inline val ACTION_DEVICE_OWNER_CHANGED: String
    get() = DevicePolicyManager.ACTION_DEVICE_OWNER_CHANGED

  @get:RequiresApi(Build.VERSION_CODES.Q)
  inline val ACTION_GET_PROVISIONING_MODE: String
    get() = DevicePolicyManager.ACTION_GET_PROVISIONING_MODE

  @get:RequiresApi(Build.VERSION_CODES.Q)
  inline val ACTION_ADMIN_POLICY_COMPLIANCE: String
    get() = DevicePolicyManager.ACTION_ADMIN_POLICY_COMPLIANCE

  @get:Suppress("UNRESOLVED_REFERENCE")
  inline val ACTION_UPDATE_DEVICE_POLICY_MANAGEMENT_ROLE_HOLDER: String
    get() = DevicePolicyManager.ACTION_UPDATE_DEVICE_POLICY_MANAGEMENT_ROLE_HOLDER

  @get:Suppress("UNRESOLVED_REFERENCE")
  inline val ACTION_ESTABLISH_NETWORK_CONNECTION: String
    get() = DevicePolicyManager.ACTION_ESTABLISH_NETWORK_CONNECTION

  @get:RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
  inline val ACTION_DEVICE_FINANCING_STATE_CHANGED: String
    get() = DevicePolicyManager.ACTION_DEVICE_FINANCING_STATE_CHANGED

  /**
   * Cannot link against it directly because it's marked as `@hidden` without `@SystemApi`
   *
   * @see DevicePolicyManager.ACTION_PROVISIONING_COMPLETED
   */
  inline val ACTION_PROVISIONING_COMPLETED: String
    get() = "android.app.action.PROVISIONING_COMPLETED"

  @get:Suppress("UNRESOLVED_REFERENCE")
  inline val ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE: String
    get() = DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE

  @get:Suppress("UNRESOLVED_REFERENCE")
  inline val ACTION_ROLE_HOLDER_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE: String
    get() = DevicePolicyManager.ACTION_ROLE_HOLDER_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE

  @get:Suppress("UNRESOLVED_REFERENCE")
  inline val ACTION_PROVISION_FINANCED_DEVICE: String
    get() = DevicePolicyManager.ACTION_PROVISION_FINANCED_DEVICE

  @get:Suppress("UNRESOLVED_REFERENCE")
  inline val ACTION_PROVISION_FINALIZATION: String
    get() = DevicePolicyManager.ACTION_PROVISION_FINALIZATION

  @Deprecated("Deprecated in DevicePolicyManager")
  inline val ACTION_PROVISION_MANAGED_DEVICE: String
    get() = DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE

  /**
   * Cannot link against it directly because it's marked as `@hidden` without `@SystemApi`
   *
   * @see DevicePolicyManager.ACTION_PROVISION_MANAGED_USER
   */
  inline val ACTION_PROVISION_MANAGED_USER: String
    get() = "android.app.action.PROVISION_MANAGED_USER"

  inline val ACTION_PROVISION_MANAGED_PROFILE: String
    get() = DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE

  @get:Suppress("UNRESOLVED_REFERENCE")
  inline val ACTION_ROLE_HOLDER_PROVISION_MANAGED_PROFILE: String
    get() = DevicePolicyManager.ACTION_ROLE_HOLDER_PROVISION_MANAGED_PROFILE

  @get:Suppress("UNRESOLVED_REFERENCE")
  inline val ACTION_ROLE_HOLDER_PROVISION_FINALIZATION: String
    get() = DevicePolicyManager.ACTION_ROLE_HOLDER_PROVISION_FINALIZATION

  /** @see com.android.managedprovisioning.common.Globals.ACTION_RESUME_PROVISIONING */
  inline val ACTION_RESUME_PROVISIONING: String
    get() = "com.android.managedprovisioning.action.RESUME_PROVISIONING"

  /**
   * @see com.android.managedprovisioning.common.Globals.ACTION_PROVISION_MANAGED_DEVICE_SILENTLY
   */
  inline val ACTION_PROVISION_MANAGED_DEVICE_SILENTLY: String
    get() = "com.android.managedprovisioning.action.PROVISION_MANAGED_DEVICE_SILENTLY"

  inline val ACTION_NDEF_DISCOVERED: String
    get() = NfcAdapter.ACTION_NDEF_DISCOVERED
}
