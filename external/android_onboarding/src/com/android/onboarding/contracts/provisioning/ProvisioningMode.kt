package com.android.onboarding.contracts.provisioning

import android.app.admin.DevicePolicyManager
import android.os.Build
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.Q)
enum class ProvisioningMode(val id: Int) {
  FullyManagedDevice(DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE),

  ManagedProfile(DevicePolicyManager.PROVISIONING_MODE_MANAGED_PROFILE),

  ManagedProfileOnPersonalDevice(
    DevicePolicyManager.PROVISIONING_MODE_MANAGED_PROFILE_ON_PERSONAL_DEVICE
  );

  companion object {
    operator fun invoke(id: Int): ProvisioningMode =
      when (id) {
        FullyManagedDevice.id -> FullyManagedDevice
        ManagedProfile.id -> ManagedProfile
        ManagedProfileOnPersonalDevice.id -> ManagedProfileOnPersonalDevice
        else -> error("Unknown ProvisioningMode(id=$id)")
      }
  }
}
