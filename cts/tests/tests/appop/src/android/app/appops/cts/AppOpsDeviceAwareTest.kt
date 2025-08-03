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

package android.app.appops.cts

import android.Manifest
import android.app.AppOpsManager
import android.companion.virtual.VirtualDeviceManager
import android.companion.virtual.VirtualDeviceParams
import android.content.AttributionSource
import android.os.Process
import android.permission.flags.Flags
import android.platform.test.annotations.AppModeFull
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.virtualdevice.cts.common.FakeAssociationRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.AdoptShellPermissionsRule
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@AppModeFull(reason = "Instant apps cannot hold GET_APP_OPS_STATS")
class AppOpsDeviceAwareTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.context
    private val appOpsManager = context.getSystemService(AppOpsManager::class.java)!!
    private val virtualDeviceManager = context.getSystemService(VirtualDeviceManager::class.java)!!
    private lateinit var virtualDevice: VirtualDeviceManager.VirtualDevice

    @get:Rule val mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()
    @get:Rule var mFakeAssociationRule = FakeAssociationRule()
    @get:Rule
    val mAdoptShellPermissionsRule =
        AdoptShellPermissionsRule(
            instrumentation.uiAutomation,
            Manifest.permission.CREATE_VIRTUAL_DEVICE,
            Manifest.permission.GET_APP_OPS_STATS
        )

    @Before
    fun setUp() {
        virtualDevice =
            virtualDeviceManager.createVirtualDevice(
                mFakeAssociationRule.associationInfo.id,
                VirtualDeviceParams.Builder().setName("virtual_device").build()
            )

        // Reset app ops state for this test package to the system default.
        reset(context.opPackageName)
    }

    @After
    fun cleanup() {
        virtualDevice.close()
    }

    @RequiresFlagsEnabled(Flags.FLAG_DEVICE_AWARE_PERMISSION_APIS_ENABLED)
    @Test
    fun getPackagesForOps_isDeviceAware() {
        // noteOp for an external device
        val attributionSource =
            AttributionSource.Builder(Process.myUid())
                .setPackageName(context.opPackageName)
                .setAttributionTag(context.attributionTag)
                .setDeviceId(virtualDevice.deviceId)
                .build()

        val startTimeMillis = System.currentTimeMillis()
        val mode =
            appOpsManager.noteOpNoThrow(AppOpsManager.OP_CAMERA, attributionSource, "message")
        val endTimeMillis = System.currentTimeMillis()
        assertThat(AppOpsManager.MODE_ALLOWED).isEqualTo(mode)

        // Expect noteOp doesn't create attributedOpEntry for default device
        val packagesOpsForDefaultDevice =
            appOpsManager.getPackagesForOps(arrayOf(AppOpsManager.OPSTR_CAMERA))
        val packageOpsForDefaultDevice =
            packagesOpsForDefaultDevice.find { it.packageName == context.opPackageName }
        val opEntryForDefaultDevice = packageOpsForDefaultDevice!!.ops[0]
        assertThat(opEntryForDefaultDevice.opStr).isEqualTo(AppOpsManager.OPSTR_CAMERA)
        assertThat(opEntryForDefaultDevice.attributedOpEntries).isEmpty()

        // Expect op is noted for the external device
        val packagesOpsForExternalDevice =
            appOpsManager.getPackagesForOps(
                arrayOf(AppOpsManager.OPSTR_CAMERA),
                virtualDevice.persistentDeviceId!!
            )

        val packageOps =
            packagesOpsForExternalDevice.find { it.packageName == context.opPackageName }
        assertThat(packageOps).isNotNull()

        val opEntries = packageOps!!.ops
        assertThat(opEntries.size).isEqualTo(1)

        val opEntry = opEntries[0]
        assertThat(opEntry.opStr).isEqualTo(AppOpsManager.OPSTR_CAMERA)
        assertThat(opEntry.mode).isEqualTo(AppOpsManager.MODE_ALLOWED)

        val attributedOpEntry = opEntry.attributedOpEntries[null]!!
        val lastAccessTime = attributedOpEntry.getLastAccessTime(AppOpsManager.OP_FLAG_SELF)
        assertThat(lastAccessTime).isAtLeast(startTimeMillis)
        assertThat(lastAccessTime).isAtMost(endTimeMillis)
    }

    @RequiresFlagsEnabled(Flags.FLAG_DEVICE_AWARE_PERMISSION_APIS_ENABLED)
    @Test
    fun getPermissionGroupUsageForPrivacyIndicator_isDeviceAware() {
        // noteOp for an external device
        val attributionSource =
            AttributionSource.Builder(Process.myUid())
                .setPackageName(context.opPackageName)
                .setAttributionTag(context.attributionTag)
                .setDeviceId(virtualDevice.deviceId)
                .build()

        val startTimeMillis = System.currentTimeMillis()
        val mode =
            appOpsManager.noteOpNoThrow(AppOpsManager.OP_CAMERA, attributionSource, "message")
        val endTimeMillis = System.currentTimeMillis()
        assertThat(AppOpsManager.MODE_ALLOWED).isEqualTo(mode)

        val groupUsage = appOpsManager.getPermissionGroupUsageForPrivacyIndicator(false)
        assertThat(groupUsage.size).isEqualTo(1)

        val permGroupUsage = groupUsage[0]
        assertThat(permGroupUsage.persistentDeviceId).isEqualTo(virtualDevice.persistentDeviceId)
        assertThat(permGroupUsage.packageName).isEqualTo(context.opPackageName)
        assertThat(permGroupUsage.permissionGroupName).isEqualTo(Manifest.permission_group.CAMERA)
        assertThat(permGroupUsage.lastAccessTimeMillis).isAtLeast(startTimeMillis)
        assertThat(permGroupUsage.lastAccessTimeMillis).isAtMost(endTimeMillis)
    }
}
