/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package android.devicepolicy.cts

import android.app.admin.DevicePolicyManager
import android.app.admin.UnsafeStateException
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.annotations.EnsurePolicyOperationUnsafe
import com.android.bedstead.harrier.annotations.Postsubmit
import com.android.bedstead.harrier.annotations.RequireFeature
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest
import com.android.bedstead.harrier.policies.LockNow
import com.android.bedstead.nene.TestApis
import com.android.bedstead.nene.devicepolicy.CommonDevicePolicy
import com.android.bedstead.nene.devicepolicy.CommonDevicePolicy.DevicePolicyOperation.OPERATION_LOCK_NOW
import com.android.bedstead.nene.devicepolicy.CommonDevicePolicy.OperationSafetyReason.OPERATION_SAFETY_REASON_DRIVING_DISTRACTION
import com.android.bedstead.nene.packages.CommonPackages
import com.android.compatibility.common.util.ApiTest
import com.google.common.truth.Truth
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.testng.Assert
import org.testng.Assert.assertThrows

@RunWith(BedsteadJUnit4::class)
class DevicePolicySafetyCheckerTest {
    @RequireFeature(CommonPackages.FEATURE_AUTOMOTIVE)
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#lockNow"])
    @EnsurePolicyOperationUnsafe(
        operation = OPERATION_LOCK_NOW,
        reason = CommonDevicePolicy.OperationSafetyReason.OPERATION_SAFETY_REASON_DRIVING_DISTRACTION
    )
    @CanSetPolicyTest(policy = [LockNow::class])
    @Postsubmit(reason = "new test")
    fun lockNow_isUnsafe_throwsUnsafeStateException() {
        assertThrows(UnsafeStateException::class.java) {
            deviceState.dpc().devicePolicyManager().lockNow()
        }
    }

    @Postsubmit(reason = "new test")
    @Test
    @EnsurePolicyOperationUnsafe(
        operation = OPERATION_LOCK_NOW,
        reason = CommonDevicePolicy.OperationSafetyReason.OPERATION_SAFETY_REASON_DRIVING_DISTRACTION
    )
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#isSafeOperation"])
    fun isSafeOperation_isNotSafe_returnsFalse() {
        Truth.assertThat(
            localDevicePolicyManager.isSafeOperation(
                OPERATION_SAFETY_REASON_DRIVING_DISTRACTION
            )
        ).isFalse()
    }

    @Postsubmit(reason = "new test")
    @Test
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#isSafeOperation"])
    fun isSafeOperation_isSafe_returnsTrue() {
        Truth.assertThat(
            localDevicePolicyManager.isSafeOperation(
                OPERATION_SAFETY_REASON_DRIVING_DISTRACTION
            )
        ).isTrue()
    }

    // TODO: Add test for // onOperationSafetyStateChanged

    companion object {
        @JvmField
        @ClassRule
        @Rule
        val deviceState = DeviceState()

        private val localDevicePolicyManager =
            TestApis.context().instrumentedContext().getSystemService(
                DevicePolicyManager::class.java
            )!!
        private val OPERATION_SAFETY_REASON_DRIVING_DISTRACTION =
            CommonDevicePolicy.OperationSafetyReason.OPERATION_SAFETY_REASON_DRIVING_DISTRACTION.value
    }
}
