/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.permissionui.cts

import android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.Manifest.permission.BLUETOOTH_SCAN
import android.app.AppOpsManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.cts.BTAdapterUtils
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PackageManager.FLAG_PERMISSION_REVOKED_COMPAT
import android.location.LocationManager
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.util.Log
import androidx.test.InstrumentationRegistry
import androidx.test.filters.FlakyTest
import androidx.test.filters.SdkSuppress
import com.android.compatibility.common.util.SystemUtil.runShellCommandOrThrow
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertTrue
import junit.framework.AssertionFailedError
import org.junit.After
import org.junit.Assert.assertNotEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

private const val LOG_TAG = "PermissionTest30WithBluetooth"

/** Runtime Bluetooth-permission behavior of apps targeting API 30 */
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.S, codeName = "S")
@FlakyTest
class PermissionTest30WithBluetooth : BaseUsePermissionTest() {

    private val TEST_APP_AUTHORITY =
        "android.permissionui.cts.usepermission.AccessBluetoothOnCommand"
    private val TEST_APP_PKG = "android.permissionui.cts.usepermission"
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var bluetoothAdapterWasEnabled: Boolean = false
    private val locationManager = context.getSystemService(LocationManager::class.java)!!
    private var locationWasEnabled: Boolean = false

    private enum class BluetoothScanResult {
        UNKNOWN,
        ERROR,
        EXCEPTION,
        EMPTY,
        FILTERED,
        FULL
    }

    @Before
    fun installApp() {
        installPackage(APP_APK_PATH_30_WITH_BLUETOOTH)
    }

    private fun reinstallApp() {
        installPackage(APP_APK_PATH_30_WITH_BLUETOOTH, reinstall = true)
    }

    @Before
    fun enableBluetooth() {
        assumeTrue(supportsBluetooth())
        bluetoothAdapter = context.getSystemService(BluetoothManager::class.java).adapter
        bluetoothAdapterWasEnabled = bluetoothAdapter.isEnabled()
        runWithShellPermissionIdentity {
            assertTrue(BTAdapterUtils.enableAdapter(bluetoothAdapter, context))
        }
        enableTestMode()
    }

    @Before
    fun enableLocation() {
        val userHandle: UserHandle = Process.myUserHandle()
        locationWasEnabled = locationManager.isLocationEnabledForUser(userHandle)
        if (!locationWasEnabled) {
            runWithShellPermissionIdentity {
                locationManager.setLocationEnabledForUser(true, userHandle)
            }
        }
    }

    @After
    fun disableLocation() {
        val userHandle: UserHandle = Process.myUserHandle()

        if (!locationWasEnabled) {
            runWithShellPermissionIdentity {
                locationManager.setLocationEnabledForUser(false, userHandle)
            }
        }
    }

    @After
    fun disableBluetooth() {
        assumeTrue(supportsBluetooth())
        disableTestMode()
        if (!bluetoothAdapterWasEnabled) {
            runWithShellPermissionIdentity {
                assertTrue(BTAdapterUtils.disableAdapter(bluetoothAdapter, context))
            }
        }
    }

    // TODO:(b/220030722) Remove verbose logging (after test is stabilized)
    @Test
    fun testGivenBluetoothIsDeniedWhenScanIsAttemptedThenThenGetEmptyScanResult() {
        assumeTrue(supportsBluetoothLe())

        assertTrue(
            "Please enable location to run this test. Bluetooth scanning " +
                "requires location to be enabled.",
            locationManager.isLocationEnabled()
        )
        assertBluetoothRevokedCompatState(revoked = false)

        Log.v(
            LOG_TAG,
            "Testing for: Given {BLUETOOTH_SCAN, !BLUETOOTH_SCAN.COMPAT_REVOKE, " +
                "!ACCESS_*_LOCATION}, expect EMPTY"
        )
        assertEquals(BluetoothScanResult.EMPTY, scanForBluetoothDevices())

        Log.v(
            LOG_TAG,
            "Testing for: Given {BLUETOOTH_SCAN, !BLUETOOTH_SCAN.COMPAT_REVOKE, " +
                "ACCESS_*_LOCATION}, expect FULL"
        )
        uiAutomation.grantRuntimePermission(TEST_APP_PKG, ACCESS_FINE_LOCATION)
        uiAutomation.grantRuntimePermission(TEST_APP_PKG, ACCESS_BACKGROUND_LOCATION)
        setAppOp(context.packageName, AppOpsManager.OPSTR_FINE_LOCATION, AppOpsManager.MODE_ALLOWED)
        assertEquals(BluetoothScanResult.FULL, scanForBluetoothDevices())

        Log.v(
            LOG_TAG,
            "Testing for: Given {BLUETOOTH_SCAN, BLUETOOTH_SCAN.COMPAT_REVOKE, " +
                "ACCESS_*_LOCATION}, expect ERROR"
        )
        revokeAppPermissionsByUi(BLUETOOTH_SCAN, isLegacyApp = true)
        assertBluetoothRevokedCompatState(revoked = true)
        val res = scanForBluetoothDevices()
        if (res != BluetoothScanResult.ERROR && res != BluetoothScanResult.EMPTY) {
            throw AssertionFailedError("Expected to be EMPTY or ERROR, but was $res")
        }
    }

    private fun setAppOp(packageName: String, appOp: String, appOpMode: Int) {
        runWithShellPermissionIdentity {
            context
                .getSystemService(AppOpsManager::class.java)!!
                .setUidMode(appOp, packageManager.getPackageUid(packageName, 0), appOpMode)
        }
    }

    @Test
    fun testRevokedCompatPersistsOnReinstall() {
        assertBluetoothRevokedCompatState(revoked = false)
        revokeAppPermissionsByUi(BLUETOOTH_SCAN, isLegacyApp = true)
        assertBluetoothRevokedCompatState(revoked = true)
        reinstallApp()
        assertBluetoothRevokedCompatState(revoked = true)
        installApp()
        assertBluetoothRevokedCompatState(revoked = true)
    }

    private fun assertBluetoothRevokedCompatState(revoked: Boolean = true) {
        runWithShellPermissionIdentity {
            val flag =
                context.packageManager.getPermissionFlags(
                    BLUETOOTH_SCAN,
                    TEST_APP_PKG,
                    Process.myUserHandle()
                ) and FLAG_PERMISSION_REVOKED_COMPAT
            if (revoked) {
                assertNotEquals(0, flag)
            } else {
                assertEquals(0, flag)
            }
        }
    }

    private fun scanForBluetoothDevices(): BluetoothScanResult {
        val resolver = InstrumentationRegistry.getTargetContext().getContentResolver()
        val result = resolver.call(TEST_APP_AUTHORITY, "", null, null)
        return BluetoothScanResult.values()[result!!.getInt(Intent.EXTRA_INDEX)]
    }

    private fun supportsBluetooth(): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)

    private fun supportsBluetoothLe(): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)

    private fun enableTestMode() =
        runShellCommandOrThrow(
            "dumpsys activity service" +
                " com.android.bluetooth.btservice.AdapterService set-test-mode enabled"
        )

    private fun disableTestMode() =
        runShellCommandOrThrow(
            "dumpsys activity service" +
                " com.android.bluetooth.btservice.AdapterService set-test-mode disabled"
        )
}
