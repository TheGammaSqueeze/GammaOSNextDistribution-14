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
import android.content.ComponentName
import android.os.PersistableBundle
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.annotations.Postsubmit
import com.android.bedstead.harrier.annotations.RequireRunOnSystemUser
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasDeviceOwner
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasProfileOwner
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest
import com.android.bedstead.harrier.policies.TransferOwnership
import com.android.bedstead.harrier.policies.TransferOwnershipForDeviceOwner
import com.android.bedstead.harrier.policies.TransferOwnershipForProfileOwner
import com.android.bedstead.nene.TestApis
import com.android.compatibility.common.util.ApiTest
import com.android.eventlib.truth.EventLogsSubject
import com.android.eventlib.truth.EventLogsSubject.assertThat
import com.android.queryable.queries.BundleQuery
import com.android.queryable.queries.ReceiverQuery
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.testng.Assert
import org.testng.Assert.assertThrows

// TODO(b/298202673: Add tests for behavior for all policies)
@RunWith(BedsteadJUnit4::class)
class TransferOwnershipTest {
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#transferOwnership", "android.app.admin.DevicePolicyManager#getTransferOwnershipBundle"])
    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = [TransferOwnership::class])
    fun transferOwnership_getTransferOwnershipBundle_bundleReceivedByTargetAdmin() {
        targetDeviceAdminTestAppSupportsTransferOwnership.install().use { testApp ->
            try {
                deviceState.dpc().devicePolicyManager().transferOwnership(
                    deviceState.dpc().componentName(), targetAdmin, bundle
                )

                assertThat(
                    testApp.devicePolicyManager().transferOwnershipBundle.getBoolean(KEY)).isTrue()
            } finally {
                removeDeviceAdmin()
            }
        }
    }

    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#transferOwnership"])
    @Postsubmit(reason = "new test")
    @CannotSetPolicyTest(policy = [TransferOwnership::class], includeNonDeviceAdminStates = false)
    fun transferOwnership_cannotSet_throwsException() {
        targetDeviceAdminTestAppSupportsTransferOwnership.install().use {
            assertThrows(SecurityException::class.java) {
                deviceState.dpc().devicePolicyManager().transferOwnership(
                    deviceState.dpc().componentName(), targetAdmin, bundle
                )
            }
        }
    }

    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#transferOwnership", "android.app.admin.DevicePolicyManager#getTransferOwnershipBundle"])
    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = [TransferOwnership::class])
    fun transferOwnership_nullBundleTransferred_getTransferOwnershipBundle_emptyBundleReceivedByTargetAdmin() {
        targetDeviceAdminTestAppSupportsTransferOwnership.install().use { testApp ->
            try {
                deviceState.dpc().devicePolicyManager().transferOwnership(
                    deviceState.dpc().componentName(), targetAdmin,  /* bundle= */null
                )

                assertThat(
                    testApp.devicePolicyManager().transferOwnershipBundle.isEmpty()).isTrue()
            } finally {
                removeDeviceAdmin()
            }
        }
    }

    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#transferOwnership"])
    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = [TransferOwnershipForDeviceOwner::class])
    fun transferOwnership_deviceOwner_ownershipTransferredToTargetAdmin() {
        targetDeviceAdminTestAppSupportsTransferOwnership.install().use {
            try {
                deviceState.dpc().devicePolicyManager().transferOwnership(
                    deviceState.dpc().componentName(), targetAdmin, bundle
                )

                assertThat(TestApis.devicePolicy().getDeviceOwner()!!.componentName())
                    .isEqualTo(targetAdmin)
            } finally {
                removeDeviceAdmin()
            }
        }
    }

    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#transferOwnership"])
    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = [TransferOwnershipForProfileOwner::class])
    fun transferOwnership_profileOwner_ownershipTransferredToTargetAdmin() {
        targetDeviceAdminTestAppSupportsTransferOwnership.install().use {
            try {
                deviceState.dpc().devicePolicyManager().transferOwnership(
                    deviceState.dpc().componentName(), targetAdmin, bundle
                )

                assertThat(TestApis.devicePolicy().getProfileOwner()!!.componentName())
                    .isEqualTo(targetAdmin)
            } finally {
                removeDeviceAdmin()
            }
        }
    }

    @CanSetPolicyTest(policy = [TransferOwnership::class])
    @Postsubmit(reason = "new test")
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#getTransferOwnershipBundle"])
    fun getTransferOwnershipBundle_nonDpc_throwsException() {
            targetDeviceAdminTestAppSupportsTransferOwnership.install().use {
                try {
                    deviceState.dpc().devicePolicyManager().transferOwnership(
                        deviceState.dpc().componentName(), targetAdmin, bundle
                    )
                    assertThrows(SecurityException::class.java) {
                        localDevicePolicyManager.transferOwnershipBundle
                    }
                } finally {
                    removeDeviceAdmin()
                }
            }
        }

    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#transferOwnership"])
    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = [TransferOwnership::class])
    fun transferOwnership_invalidTarget_throwsException() {
        try {
            assertThrows(IllegalArgumentException::class.java) {
                deviceState.dpc().devicePolicyManager().transferOwnership(
                    deviceState.dpc().componentName(), invalidComponentName, bundle
                )
            }
        } finally {
            removeDeviceAdmin()
        }
    }

    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#transferOwnership"])
    @EnsureHasProfileOwner
    @Postsubmit(reason = "new test")
    @Test
    fun transferOwnership_disableCamera_policyRetainedAfterTransfer() {
        targetDeviceAdminTestAppSupportsTransferOwnership.install().use { testApp ->
            try {
                deviceState.dpc().devicePolicyManager().setCameraDisabled(
                    deviceState.dpc().componentName(), true
                )
                deviceState.dpc().devicePolicyManager().transferOwnership(
                    deviceState.dpc().componentName(), targetAdmin, bundle
                )
                assertThat(testApp.devicePolicyManager().getCameraDisabled(targetAdmin)).isTrue()
            } finally {
                removeDeviceAdmin()
            }
        }
    }

    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#transferOwnership"])
    @Postsubmit(reason = "new test")
    @EnsureHasProfileOwner
    @Test
    fun transferOwnership_profileOwner_sendsOwnerChangedBroadcast() {
        targetDeviceAdminTestAppSupportsTransferOwnership.install().use {
            try {
                deviceState.registerBroadcastReceiver(ACTION_PROFILE_OWNER_CHANGED)
                    .use { receiver ->
                        deviceState.dpc().devicePolicyManager().transferOwnership(
                            deviceState.dpc().componentName(), targetAdmin, bundle
                        )
                        assertThat(receiver.awaitForBroadcast()!!.action).isEqualTo(
                            ACTION_PROFILE_OWNER_CHANGED
                        )
                    }
            } finally {
                removeDeviceAdmin()
            }
        }
    }

    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#transferOwnership"])
    @Postsubmit(reason = "new test")
    @EnsureHasDeviceOwner
    @RequireRunOnSystemUser // Same as device owner
    @Test
    fun transferOwnership_deviceOwner_sendsOwnerChangedBroadcast() {
        targetDeviceAdminTestAppSupportsTransferOwnership.install().use {
            try {
                deviceState.registerBroadcastReceiver(ACTION_DEVICE_OWNER_CHANGED)
                    .use { receiver ->
                        deviceState.dpc().devicePolicyManager().transferOwnership(
                            deviceState.dpc().componentName(), targetAdmin, bundle
                        )

                        receiver.awaitForBroadcastOrFail()
                    }
            } finally {
                removeDeviceAdmin()
            }
        }
    }

    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#transferOwnership"])
    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = [TransferOwnership::class])
    fun transferOwnership_noMetadata_throwsException() {
        targetDeviceAdminTestAppDoesNotSupportTransferOwnership.install().use {
            assertThrows(IllegalArgumentException::class.java) {
                deviceState.dpc().devicePolicyManager().transferOwnership(
                    deviceState.dpc().componentName(), targetAdmin, emptyBundle
                )
            }
        }
    }

    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#transferOwnership"])
    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = [TransferOwnership::class])
    fun transferOwnership_sameAdmin_throwsException() {
        targetDeviceAdminTestAppSupportsTransferOwnership.install().use {
            assertThrows(IllegalArgumentException::class.java) {
                deviceState.dpc().devicePolicyManager().transferOwnership(
                    deviceState.dpc().componentName(), deviceState.dpc().componentName(),
                    emptyBundle
                )
            }
        }
    }

    /**
     * Remove whichever device admin (device owner or profile owner) the test is running for.
     */
    private fun removeDeviceAdmin() {
        TestApis.devicePolicy().getDeviceOwner()?.remove()
        TestApis.users().all().forEach {
            TestApis.devicePolicy().getProfileOwner(it)?.remove()
        }
    }

    @ApiTest(apis = ["android.app.admin.DeviceAdminReceiver#onTransferOwnershipCompleted"])
    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = [TransferOwnership::class])
    @Test
    fun transferOwnership_callsOnTransferOwnershipCompletedCallback() {
        targetDeviceAdminTestAppSupportsTransferOwnership.install().use {testApp ->
            try {
                deviceState.dpc().devicePolicyManager().transferOwnership(
                    deviceState.dpc().componentName(), targetAdmin, bundle
                )

                assertThat(testApp.events().transferOwnershipComplete()
                    .whereDeviceAdminReceiver().broadcastReceiver().receiverClass().className().isEqualTo(
                        targetAdmin.className)
                    .whereBundle().key(KEY).booleanValue().isEqualTo(bundle.getBoolean(KEY))).eventOccurred()
            } finally {
                removeDeviceAdmin()
            }
        }
    }

    // TODO: Add test of onTransferAffiliatedProfileOwnershipComplete
    companion object {
        @JvmField
        @ClassRule
        @Rule
        val deviceState = DeviceState()
        private val transferOwnershipMetadataQuery = BundleQuery.bundle().where().key(
            "supports-transfer-ownership"
        ).stringValue().isEqualTo("true")
        private val targetDeviceAdminTestAppSupportsTransferOwnership =
            deviceState.testApps().query()
                .whereIsDeviceAdmin().isTrue()
                .whereReceivers().contains(
                    ReceiverQuery.receiver().where().metadata().contains(
                        transferOwnershipMetadataQuery
                    )
                ).get()
        private val targetDeviceAdminTestAppDoesNotSupportTransferOwnership =
            deviceState.testApps().query()
                .whereIsDeviceAdmin().isTrue()
                .whereReceivers().contains(
                    ReceiverQuery.receiver().where().metadata().doesNotContain(
                        transferOwnershipMetadataQuery
                    )
                ).get()
        private const val KEY = "VALUE"
        private const val ACTION_DEVICE_OWNER_CHANGED = "android.app.action.DEVICE_OWNER_CHANGED"
        private const val ACTION_PROFILE_OWNER_CHANGED = "android.app.action.PROFILE_OWNER_CHANGED"
        private val bundle = PersistableBundle().apply { putBoolean(KEY, true) }

        private val emptyBundle = PersistableBundle()
        private val targetAdmin = ComponentName(
            targetDeviceAdminTestAppSupportsTransferOwnership.packageName(),
            targetDeviceAdminTestAppSupportsTransferOwnership.packageName()
                    + ".DeviceAdminReceiver"
        )
        private val invalidComponentName = ComponentName("invalid", "invalid")
        private val localDevicePolicyManager =
            TestApis.context().instrumentedContext().getSystemService(
                DevicePolicyManager::class.java
            )!!
    }
}
