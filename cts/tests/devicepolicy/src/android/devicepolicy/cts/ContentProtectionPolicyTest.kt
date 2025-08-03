/*
 * Copyright (C) 2024 The Android Open Source Project
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

import android.app.admin.DevicePolicyManager.CONTENT_PROTECTION_DISABLED
import android.app.admin.DevicePolicyManager.CONTENT_PROTECTION_ENABLED
import android.view.contentprotection.flags.Flags
import com.android.bedstead.flags.annotations.RequireFlagsEnabled
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.annotations.Postsubmit
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest
import com.android.bedstead.harrier.policies.ContentProtectionPolicy
import com.android.compatibility.common.util.ApiTest
import com.google.common.truth.Truth.assertThat
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.testng.Assert.assertThrows

/** CTS tests for content protection device policy management.  */
@RunWith(BedsteadJUnit4::class)
@RequireFlagsEnabled(Flags.FLAG_MANAGE_DEVICE_POLICY_ENABLED)
class ContentProtectionPolicyTest {

    @Test
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#getContentProtectionPolicy"])
    @CanSetPolicyTest(policy = [ContentProtectionPolicy::class])
    @Postsubmit(reason = "new test")
    fun getContentProtectionPolicy_defaultValue() {
        val componentName = deviceState.dpc().componentName()
        val remoteDpm = deviceState.dpc().devicePolicyManager()

        val actualPolicy = remoteDpm.getContentProtectionPolicy(componentName)

        assertThat(actualPolicy).isEqualTo(CONTENT_PROTECTION_DISABLED)
    }

    @Test
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#getContentProtectionPolicy"])
    @CannotSetPolicyTest(policy = [ContentProtectionPolicy::class])
    @Postsubmit(reason = "new test")
    fun getContentProtectionPolicy_notAllowed_throwsException() {
        val componentName = deviceState.dpc().componentName()
        val remoteDpm = deviceState.dpc().devicePolicyManager()

        assertThrows(SecurityException::class.java) {
            remoteDpm.getContentProtectionPolicy(componentName)
        }
    }

    @Test
    @ApiTest(
            apis = [
                "android.app.admin.DevicePolicyManager#setContentProtectionPolicy",
                "android.app.admin.DevicePolicyManager#getContentProtectionPolicy",
            ]
    )
    @PolicyAppliesTest(policy = [ContentProtectionPolicy::class])
    @Postsubmit(reason = "new test")
    fun setContentProtectionPolicy_enabled_policySet() {
        val componentName = deviceState.dpc().componentName()
        val remoteDpm = deviceState.dpc().devicePolicyManager()
        val originalPolicy = remoteDpm.getContentProtectionPolicy(componentName)

        try {
            remoteDpm.setContentProtectionPolicy(componentName, CONTENT_PROTECTION_ENABLED)

            val actualPolicy = remoteDpm.getContentProtectionPolicy(componentName)

            assertThat(actualPolicy).isEqualTo(CONTENT_PROTECTION_ENABLED)
        } finally {
            remoteDpm.setContentProtectionPolicy(componentName, originalPolicy)
        }
    }

    @Test
    @ApiTest(
            apis = [
                "android.app.admin.DevicePolicyManager#setContentProtectionPolicy",
                "android.app.admin.DevicePolicyManager#getContentProtectionPolicy",
            ]
    )
    @PolicyAppliesTest(policy = [ContentProtectionPolicy::class])
    @Postsubmit(reason = "new test")
    fun setContentProtectionPolicy_disabled_policySet() {
        val componentName = deviceState.dpc().componentName()
        val remoteDpm = deviceState.dpc().devicePolicyManager()
        val originalPolicy = remoteDpm.getContentProtectionPolicy(componentName)

        try {
            remoteDpm.setContentProtectionPolicy(componentName, CONTENT_PROTECTION_DISABLED)

            val actualPolicy = remoteDpm.getContentProtectionPolicy(componentName)

            assertThat(actualPolicy).isEqualTo(CONTENT_PROTECTION_DISABLED)
        } finally {
            remoteDpm.setContentProtectionPolicy(componentName, originalPolicy)
        }
    }

    @Test
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#setContentProtectionPolicy"])
    @CannotSetPolicyTest(policy = [ContentProtectionPolicy::class])
    @Postsubmit(reason = "new test")
    fun setContentProtectionPolicy_notAllowed_throwsException() {
        val componentName = deviceState.dpc().componentName()
        val remoteDpm = deviceState.dpc().devicePolicyManager()

        assertThrows(SecurityException::class.java) {
            remoteDpm.setContentProtectionPolicy(componentName, CONTENT_PROTECTION_ENABLED)
        }
    }

    companion object {

        @Rule
        @ClassRule
        @JvmField
        val deviceState = DeviceState()
    }
}
