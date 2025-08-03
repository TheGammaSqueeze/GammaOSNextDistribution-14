package com.android.onboarding.contracts.provisioning

import android.app.admin.DevicePolicyManager

enum class ProvisioningTrigger(val id: Int) {
  @Suppress("UNRESOLVED_REFERENCE")
  Unspecified(DevicePolicyManager.PROVISIONING_TRIGGER_UNSPECIFIED),
  @Suppress("UNRESOLVED_REFERENCE")
  CloudEnrollment(DevicePolicyManager.PROVISIONING_TRIGGER_CLOUD_ENROLLMENT),
  @Suppress("UNRESOLVED_REFERENCE") QR(DevicePolicyManager.PROVISIONING_TRIGGER_QR_CODE),
  @Deprecated("Deprecated in DevicePolicyManager")
  @Suppress("UNRESOLVED_REFERENCE")
  PersistentDeviceOwner(DevicePolicyManager.PROVISIONING_TRIGGER_PERSISTENT_DEVICE_OWNER),
  @Suppress("UNRESOLVED_REFERENCE")
  ManagedAccount(DevicePolicyManager.PROVISIONING_TRIGGER_MANAGED_ACCOUNT),
  @Suppress("UNRESOLVED_REFERENCE") NFC(DevicePolicyManager.PROVISIONING_TRIGGER_NFC);

  companion object {
    operator fun invoke(id: Int): ProvisioningTrigger =
      when (id) {
        Unspecified.id -> Unspecified
        CloudEnrollment.id -> CloudEnrollment
        QR.id -> QR
        PersistentDeviceOwner.id -> PersistentDeviceOwner
        ManagedAccount.id -> ManagedAccount
        NFC.id -> NFC
        else -> error("Unknown ProvisioningTrigger(id=$id)")
      }
  }
}
