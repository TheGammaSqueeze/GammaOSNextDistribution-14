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

package android.permissionmultidevice.cts

import android.Manifest
import android.app.ActivityOptions
import android.app.Instrumentation
import android.companion.virtual.VirtualDeviceManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.permission.flags.Flags
import android.permissionmultidevice.cts.PackageManagementUtils.installPackage
import android.permissionmultidevice.cts.PackageManagementUtils.uninstallPackage
import android.permissionmultidevice.cts.PermissionUtils.getHostDeviceName
import android.permissionmultidevice.cts.UiAutomatorUtils.click
import android.permissionmultidevice.cts.UiAutomatorUtils.findTextForView
import android.permissionmultidevice.cts.UiAutomatorUtils.waitFindObject
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.view.Display
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import com.android.compatibility.common.util.AdoptShellPermissionsRule
import com.android.compatibility.common.util.SystemUtil
import com.google.common.truth.Truth
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM, codeName = "VanillaIceCream")
class DeviceAwarePermissionGrantTest {
    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val defaultDeviceContext = instrumentation.targetContext

    @get:Rule(order = 0)
    val mAdoptShellPermissionsRule =
        AdoptShellPermissionsRule(
            instrumentation.uiAutomation,
            Manifest.permission.CREATE_VIRTUAL_DEVICE
        )

    @get:Rule(order = 1) var mFakeVirtualDeviceRule = FakeVirtualDeviceRule()

    @Rule @JvmField val mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @Before
    fun setup() {
        installPackage(APP_APK_PATH_STREAMING)
    }

    @After
    fun cleanup() {
        uninstallPackage(APP_PACKAGE_NAME, requireSuccess = false)
    }

    @RequiresFlagsEnabled(
        Flags.FLAG_DEVICE_AWARE_PERMISSION_APIS_ENABLED,
        Flags.FLAG_DEVICE_AWARE_PERMISSIONS_ENABLED
    )
    @Test
    fun onHostDevice_requestPermissionForHostDevice_shouldGrantPermission() {
        testGrantPermissionForDevice(
            Display.DEFAULT_DISPLAY,
            DEVICE_ID_DEFAULT,
            false,
            "",
            expectPermissionGrantedOnDefaultDevice = true,
            expectPermissionGrantedOnRemoteDevice = false
        )
    }

    @RequiresFlagsEnabled(
        Flags.FLAG_DEVICE_AWARE_PERMISSION_APIS_ENABLED,
        Flags.FLAG_DEVICE_AWARE_PERMISSIONS_ENABLED
    )
    @Test
    fun onHostDevice_requestPermissionForRemoteDevice_shouldGrantPermission() {
        testGrantPermissionForDevice(
            Display.DEFAULT_DISPLAY,
            mFakeVirtualDeviceRule.virtualDevice.deviceId,
            true,
            mFakeVirtualDeviceRule.deviceDisplayName,
            expectPermissionGrantedOnDefaultDevice = false,
            expectPermissionGrantedOnRemoteDevice = true
        )
    }

    @RequiresFlagsEnabled(
        Flags.FLAG_DEVICE_AWARE_PERMISSION_APIS_ENABLED,
        Flags.FLAG_DEVICE_AWARE_PERMISSIONS_ENABLED
    )
    @Test
    fun onRemoteDevice_requestPermissionForHostDevice_shouldGrantPermission() {
        testGrantPermissionForDevice(
            mFakeVirtualDeviceRule.virtualDisplayId,
            DEVICE_ID_DEFAULT,
            true,
            getHostDeviceName(defaultDeviceContext),
            expectPermissionGrantedOnDefaultDevice = true,
            expectPermissionGrantedOnRemoteDevice = false
        )
    }

    @RequiresFlagsEnabled(
        Flags.FLAG_DEVICE_AWARE_PERMISSION_APIS_ENABLED,
        Flags.FLAG_DEVICE_AWARE_PERMISSIONS_ENABLED
    )
    @Test
    fun onRemoteDevice_requestPermissionForRemoteDevice_shouldGrantPermission() {
        testGrantPermissionForDevice(
            mFakeVirtualDeviceRule.virtualDisplayId,
            mFakeVirtualDeviceRule.virtualDevice.deviceId,
            true,
            mFakeVirtualDeviceRule.deviceDisplayName,
            expectPermissionGrantedOnDefaultDevice = false,
            expectPermissionGrantedOnRemoteDevice = true
        )
    }

    private fun testGrantPermissionForDevice(
        displayId: Int,
        targetDeviceId: Int,
        showDeviceName: Boolean,
        expectedDeviceNameInDialog: String,
        expectPermissionGrantedOnDefaultDevice: Boolean,
        expectPermissionGrantedOnRemoteDevice: Boolean
    ) {
        assertAppHasPermissionForDevice(defaultDeviceContext, DEVICE_ID_DEFAULT, false)

        assertAppHasPermissionForDevice(
            defaultDeviceContext,
            mFakeVirtualDeviceRule.virtualDevice.deviceId,
            false
        )

        requestPermissionOnDevice(displayId, targetDeviceId)

        if (showDeviceName) {
            assertPermissionMessageContainsDeviceName(displayId, expectedDeviceNameInDialog)
        }

        SystemUtil.eventually { click(By.displayId(displayId).res(ALLOW_BUTTON)) }

        assertAppHasPermissionForDevice(
            defaultDeviceContext,
            DEVICE_ID_DEFAULT,
            expectPermissionGrantedOnDefaultDevice
        )

        assertAppHasPermissionForDevice(
            defaultDeviceContext,
            mFakeVirtualDeviceRule.virtualDevice.deviceId,
            expectPermissionGrantedOnRemoteDevice
        )
    }

    private fun requestPermissionOnDevice(displayId: Int, targetDeviceId: Int) {
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(displayId).toBundle()

        val intent =
            Intent()
                .setComponent(
                    ComponentName(APP_PACKAGE_NAME, "$APP_PACKAGE_NAME.RequestPermissionActivity")
                )
                .putExtra(PackageManager.EXTRA_REQUEST_PERMISSIONS_DEVICE_ID, targetDeviceId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        defaultDeviceContext.startActivity(intent, options)
    }

    private fun assertPermissionMessageContainsDeviceName(displayId: Int, deviceName: String) {
        waitFindObject(By.displayId(displayId).res(PERMISSION_MESSAGE_ID))
        val text = findTextForView(By.displayId(displayId).res(PERMISSION_MESSAGE_ID))
        Truth.assertThat(text).contains(deviceName)
    }

    private fun assertAppHasPermissionForDevice(
        context: Context,
        deviceId: Int,
        expectPermissionGranted: Boolean
    ) {
        val checkPermissionResult =
            context
                .createDeviceContext(deviceId)
                .packageManager
                .checkPermission(Manifest.permission.CAMERA, APP_PACKAGE_NAME)

        if (expectPermissionGranted) {
            Assert.assertEquals(PackageManager.PERMISSION_GRANTED, checkPermissionResult)
        } else {
            Assert.assertEquals(PackageManager.PERMISSION_DENIED, checkPermissionResult)
        }
    }

    companion object {
        const val APK_DIRECTORY = "/data/local/tmp/cts-permissionmultidevice"
        const val APP_APK_PATH_STREAMING = "${APK_DIRECTORY}/CtsAccessRemoteDeviceCamera.apk"
        const val APP_PACKAGE_NAME = "android.permissionmultidevice.cts.accessremotedevicecamera"
        const val PERMISSION_MESSAGE_ID = "com.android.permissioncontroller:id/permission_message"
        const val ALLOW_BUTTON =
            "com.android.permissioncontroller:id/permission_allow_foreground_only_button"
        const val DEVICE_ID_DEFAULT = 0
        const val PERSISTENT_DEVICE_ID_DEFAULT = VirtualDeviceManager.PERSISTENT_DEVICE_ID_DEFAULT
    }
}
