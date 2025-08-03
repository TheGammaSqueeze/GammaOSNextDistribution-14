/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License") * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.app.appops.cts

import android.app.ActivityManager
import android.app.ActivityManager.PROCESS_STATE_IMPORTANT_BACKGROUND
import android.app.AppOpsManager
import android.app.AppOpsManager.HISTORICAL_MODE_ENABLED_ACTIVE
import android.app.AppOpsManager.HISTORICAL_MODE_ENABLED_PASSIVE
import android.app.AppOpsManager.HISTORY_FLAGS_ALL
import android.app.AppOpsManager.HISTORY_FLAG_AGGREGATE
import android.app.AppOpsManager.HISTORY_FLAG_DISCRETE
import android.app.AppOpsManager.HistoricalOps
import android.app.AppOpsManager.KEY_BG_STATE_SETTLE_TIME
import android.app.AppOpsManager.KEY_FG_SERVICE_STATE_SETTLE_TIME
import android.app.AppOpsManager.KEY_TOP_STATE_SETTLE_TIME
import android.app.AppOpsManager.MODE_ALLOWED
import android.app.AppOpsManager.MODE_IGNORED
import android.app.AppOpsManager.OPSTR_FINE_LOCATION
import android.app.AppOpsManager.OPSTR_RESERVED_FOR_TESTING
import android.app.AppOpsManager.OP_FLAGS_ALL
import android.app.AppOpsManager.OP_FLAG_SELF
import android.app.AppOpsManager.OP_FLAG_TRUSTED_PROXIED
import android.app.AppOpsManager.UID_STATE_CACHED
import android.app.AppOpsManager.UID_STATE_PERSISTENT
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.platform.test.annotations.AppModeFull
import android.platform.test.annotations.RequiresFlagsEnabled
import android.provider.DeviceConfig
import android.provider.DeviceConfig.NAMESPACE_PRIVACY
import android.provider.Settings
import android.util.Log
import androidx.test.filters.FlakyTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.android.compatibility.common.util.SystemUtil
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import java.util.function.Consumer
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test

private const val LOG_TAG = "DiscreteAppopsTest"
private const val PACKAGE_NAME = "android.app.appops.cts.appfordiscretetest"
private const val TIMEOUT_MILLIS = 45000L
private const val DEFAULT_TIME_QUANT_MILLIS = 60000L
private const val SHORT_TIME_QUANT_MILLIS = 2000L
private const val CALLBACK_PROPAGATION_DELAY = 5000L
private const val SAFETY_MARGIN_MILLIS = 5000L
private const val TEN_MINUTES_MILLIS = 600000L
private const val ONE_MINUTE_MILLIS = 60000L
private const val TAG1 = "tag1"
private const val TAG2 = "tag2"
private const val PROPERTY_CUTOFF = "discrete_history_cutoff_millis"
private const val PROPERTY_QUANTIZATION = "discrete_history_quantization_millis"
private const val PROPERTY_FLAGS = "discrete_history_op_flags"
private const val PROPERTY_OPS_LIST = "discrete_history_ops_cslist"
private const val INTERVAL_COMPRESSION_MULTIPLIER = 10
private const val SNAPSHOT_INTERVAL_MILLIS = 1000L

@AppModeFull(reason = "This test connects to other test app")
class DiscreteAppopsTest {
    private var previousAppOpsConstants: String? = null

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.context
    private val uid = context.packageManager.getPackageUid(PACKAGE_NAME, 0)

    private val uiAutomation get() = instrumentation.uiAutomation
    private lateinit var foregroundControlService: IAppOpsForegroundControlService
    private lateinit var serviceConnection: ServiceConnection

    private var previousDiscreteHistoryCutoffMillis: String? = null
    private var previousDiscreteHistoryQuantizationMillis: String? = null
    private var previousDiscreteHistoryOpFlags: String? = null
    private var previousDiscreteHistoryOpsCslist: String? = null

    private lateinit var appOpsManager: AppOpsManager
    private lateinit var activityManager: ActivityManager
    private val uiDevice = UiDevice.getInstance(instrumentation)

    private val testPkgAppOpMode: Int
        get() {
            return SystemUtil.callWithShellPermissionIdentity {
                appOpsManager.noteOp(OPSTR_FINE_LOCATION, uid, PACKAGE_NAME, null, null)
            }
        }

    @Before
    fun setUpTest() {
        appOpsManager = context.getSystemService(AppOpsManager::class.java)!!
        activityManager = context.getSystemService(ActivityManager::class.java)!!
        runWithShellPermissionIdentity {
            previousDiscreteHistoryCutoffMillis = DeviceConfig.getString(
                    NAMESPACE_PRIVACY, PROPERTY_CUTOFF, null)
            previousDiscreteHistoryQuantizationMillis = DeviceConfig.getString(
                    NAMESPACE_PRIVACY, PROPERTY_QUANTIZATION, null)
            previousDiscreteHistoryOpFlags = DeviceConfig.getString(
                    NAMESPACE_PRIVACY, PROPERTY_FLAGS, null)
            previousDiscreteHistoryOpsCslist = DeviceConfig.getString(
                    NAMESPACE_PRIVACY, PROPERTY_OPS_LIST, null)

            appOpsManager.clearHistory()
            appOpsManager.resetHistoryParameters()

            previousAppOpsConstants = Settings.Global.getString(context.contentResolver,
                    Settings.Global.APP_OPS_CONSTANTS)

            // Speed up app-ops service proc state transitions
            Settings.Global.putString(context.contentResolver, Settings.Global.APP_OPS_CONSTANTS,
                    "$KEY_TOP_STATE_SETTLE_TIME=300,$KEY_FG_SERVICE_STATE_SETTLE_TIME=100," +
                            "$KEY_BG_STATE_SETTLE_TIME=10")
        }

        enableDiscreteRegistryDebugMode()

        val serviceIntent = Intent().setComponent(ComponentName(PACKAGE_NAME,
                "$PACKAGE_NAME.AppOpsForegroundControlService"))

        val newService = CompletableFuture<IAppOpsForegroundControlService>()
        serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                newService.complete(IAppOpsForegroundControlService.Stub.asInterface(service))
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                Assert.fail("foreground control service disconnected")
            }
        }

        context.bindService(serviceIntent, serviceConnection,
                Context.BIND_AUTO_CREATE or Context.BIND_NOT_FOREGROUND)
        foregroundControlService = newService.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
    }

    @Before
    fun wakeScreenUp() {
        uiDevice.wakeUp()
        uiDevice.executeShellCommand("wm dismiss-keyguard")
        uiDevice.executeShellCommand("input keyevent KEYCODE_HOME")

        instrumentation.uiAutomation.waitForIdle(1000, 10000)
    }

    @After
    fun tearDownTest() {
        runWithShellPermissionIdentity {
            appOpsManager.clearHistory()
            appOpsManager.resetHistoryParameters()
        }
        foregroundControlService.cleanup()
        context.unbindService(serviceConnection)

        runWithShellPermissionIdentity {
            Settings.Global.putString(context.contentResolver, Settings.Global.APP_OPS_CONSTANTS,
                    previousAppOpsConstants)
        }
        uiDevice.executeShellCommand("device_config put " + NAMESPACE_PRIVACY + " " +
                PROPERTY_CUTOFF + " " + previousDiscreteHistoryCutoffMillis)
        uiDevice.executeShellCommand("device_config put " + NAMESPACE_PRIVACY + " " +
                PROPERTY_QUANTIZATION + " " + previousDiscreteHistoryQuantizationMillis)
        uiDevice.executeShellCommand("device_config put " + NAMESPACE_PRIVACY + " " +
                PROPERTY_FLAGS + " " + previousDiscreteHistoryOpFlags)
        uiDevice.executeShellCommand("device_config put " + NAMESPACE_PRIVACY + " " +
                PROPERTY_OPS_LIST + " " + previousDiscreteHistoryOpsCslist)
    }

    @Test
    @RequiresFlagsEnabled
    fun testRecordAndCheckAppOp() {
        waitUntilSafelyInTimeQuant(DEFAULT_TIME_QUANT_MILLIS, SAFETY_MARGIN_MILLIS)
        noteOp(OPSTR_RESERVED_FOR_TESTING, uid, PACKAGE_NAME, null, null)
        val timeStamp = System.currentTimeMillis() /
                DEFAULT_TIME_QUANT_MILLIS * DEFAULT_TIME_QUANT_MILLIS
        Thread.sleep(250)

        // Get all ops for the package
        var allOps = getHistoricalOps(HISTORY_FLAGS_ALL)

        assertThat(allOps).isNotNull()
        assertThat(allOps!!.uidCount).isEqualTo(1)

        var uidOps = allOps.getUidOpsAt(0)
        assertThat(uidOps).isNotNull()
        assertThat(uidOps.uid).isEqualTo(uid)
        assertThat(uidOps.packageCount).isEqualTo(1)

        var packageOps = uidOps.getPackageOpsAt(0)
        assertThat(packageOps).isNotNull()
        assertThat(packageOps.packageName).isEqualTo(PACKAGE_NAME)
        assertThat(packageOps.opCount).isEqualTo(1)

        var op = packageOps.getOpAt(0)
        assertThat(op).isNotNull()
        assertThat(op.opName).isEqualTo(OPSTR_RESERVED_FOR_TESTING)
        assertThat(op.discreteAccessCount).isEqualTo(1)
        var discrete = op.getDiscreteAccessAt(0)

        assertThat(discrete.getLastDuration(OP_FLAGS_ALL)).isEqualTo(-1)
        assertThat(discrete.getLastAccessTime(OP_FLAGS_ALL)).isEqualTo(timeStamp)
        // Get only discrete accesses
        allOps = getHistoricalOps(HISTORY_FLAG_DISCRETE)

        assertThat(allOps).isNotNull()
        assertThat(allOps!!.uidCount).isEqualTo(1)

        uidOps = allOps.getUidOpsAt(0)
        assertThat(uidOps).isNotNull()
        assertThat(uidOps.uid).isEqualTo(uid)
        assertThat(uidOps.packageCount).isEqualTo(1)

        packageOps = uidOps.getPackageOpsAt(0)
        assertThat(packageOps).isNotNull()
        assertThat(packageOps.packageName).isEqualTo(PACKAGE_NAME)
        assertThat(packageOps.opCount).isEqualTo(1)

        op = packageOps.getOpAt(0)
        assertThat(op).isNotNull()
        assertThat(op.opName).isEqualTo(OPSTR_RESERVED_FOR_TESTING)
        assertThat(op.discreteAccessCount).isEqualTo(1)
        discrete = op.getDiscreteAccessAt(0)

        assertThat(discrete.getLastDuration(OP_FLAGS_ALL)).isEqualTo(-1)
        assertThat(discrete.getLastAccessTime(OP_FLAGS_ALL)).isEqualTo(timeStamp)
        // Get only aggregate accesses
        allOps = getHistoricalOps(HISTORY_FLAG_AGGREGATE)

        assertThat(allOps).isNotNull()
        assertThat(allOps!!.uidCount).isEqualTo(1)

        uidOps = allOps.getUidOpsAt(0)
        assertThat(uidOps).isNotNull()
        assertThat(uidOps.uid).isEqualTo(uid)
        assertThat(uidOps.packageCount).isEqualTo(1)

        packageOps = uidOps.getPackageOpsAt(0)
        assertThat(packageOps).isNotNull()
        assertThat(packageOps.packageName).isEqualTo(PACKAGE_NAME)
        assertThat(packageOps.opCount).isEqualTo(1)

        op = packageOps.getOpAt(0)
        assertThat(op).isNotNull()
        assertThat(op.opName).isEqualTo(OPSTR_RESERVED_FOR_TESTING)
        assertThat(op.discreteAccessCount).isEqualTo(0)
    }

    @Test
    fun testNotedTwiceRecordedOnce() {
        waitUntilSafelyInTimeQuant(DEFAULT_TIME_QUANT_MILLIS, SAFETY_MARGIN_MILLIS)
        noteOp(OPSTR_RESERVED_FOR_TESTING, uid, PACKAGE_NAME, null, null)
        noteOp(OPSTR_RESERVED_FOR_TESTING, uid, PACKAGE_NAME, null, null)

        val timeStamp = System.currentTimeMillis() /
                DEFAULT_TIME_QUANT_MILLIS * DEFAULT_TIME_QUANT_MILLIS
        var allOps = getHistoricalOps()

        assertThat(allOps).isNotNull()
        assertThat(allOps!!.uidCount).isEqualTo(1)

        var uidOps = allOps.getUidOpsAt(0)
        assertThat(uidOps).isNotNull()
        assertThat(uidOps.uid).isEqualTo(uid)
        assertThat(uidOps.packageCount).isEqualTo(1)

        var packageOps = uidOps.getPackageOpsAt(0)
        assertThat(packageOps).isNotNull()
        assertThat(packageOps.packageName).isEqualTo(PACKAGE_NAME)
        assertThat(packageOps.opCount).isEqualTo(1)

        var op = packageOps.getOpAt(0)
        assertThat(op).isNotNull()
        assertThat(op.opName).isEqualTo(OPSTR_RESERVED_FOR_TESTING)
        assertThat(op.discreteAccessCount).isEqualTo(1)
        var discrete = op.getDiscreteAccessAt(0)

        assertThat(discrete.getLastDuration(OP_FLAGS_ALL)).isEqualTo(-1)
        assertThat(discrete.getLastAccessTime(OP_FLAGS_ALL)).isEqualTo(timeStamp)
    }

    @Test
    fun testAdjustedQuantization() {
        setQuantization(SHORT_TIME_QUANT_MILLIS)
        waitUntilNextQuantStarts(SHORT_TIME_QUANT_MILLIS)
        val timeStamp = System.currentTimeMillis() /
                SHORT_TIME_QUANT_MILLIS * SHORT_TIME_QUANT_MILLIS
        noteOp(OPSTR_RESERVED_FOR_TESTING, uid, PACKAGE_NAME)
        Thread.sleep(100)
        noteOp(OPSTR_RESERVED_FOR_TESTING, uid, PACKAGE_NAME)
        waitUntilNextQuantStarts(SHORT_TIME_QUANT_MILLIS)
        noteOp(OPSTR_RESERVED_FOR_TESTING, uid, PACKAGE_NAME)
        Thread.sleep(100)
        noteOp(OPSTR_RESERVED_FOR_TESTING, uid, PACKAGE_NAME)
        var allOps = getHistoricalOps()
        assertThat(allOps).isNotNull()
        assertThat(allOps!!.uidCount).isEqualTo(1)

        var uidOps = allOps.getUidOpsAt(0)
        assertThat(uidOps).isNotNull()
        assertThat(uidOps.uid).isEqualTo(uid)
        assertThat(uidOps.packageCount).isEqualTo(1)

        var packageOps = uidOps.getPackageOpsAt(0)
        assertThat(packageOps).isNotNull()
        assertThat(packageOps.packageName).isEqualTo(PACKAGE_NAME)
        assertThat(packageOps.opCount).isEqualTo(1)

        var op = packageOps.getOpAt(0)
        assertThat(op).isNotNull()
        assertThat(op.opName).isEqualTo(OPSTR_RESERVED_FOR_TESTING)
        assertThat(op.discreteAccessCount).isEqualTo(2)
        var discrete = op.getDiscreteAccessAt(0)

        assertThat(discrete.getLastDuration(OP_FLAGS_ALL)).isEqualTo(-1)
        assertThat(discrete.getLastAccessTime(OP_FLAGS_ALL)).isEqualTo(timeStamp)

        discrete = op.getDiscreteAccessAt(1)

        assertThat(discrete.getLastDuration(OP_FLAGS_ALL)).isEqualTo(-1)
        assertThat(discrete.getLastAccessTime(OP_FLAGS_ALL))
                .isEqualTo(timeStamp + SHORT_TIME_QUANT_MILLIS)
    }

    @Test
    fun testTimeTravel() {
        waitUntilSafelyInTimeQuant(DEFAULT_TIME_QUANT_MILLIS, SAFETY_MARGIN_MILLIS)
        val timeStamp = System.currentTimeMillis() /
                DEFAULT_TIME_QUANT_MILLIS * DEFAULT_TIME_QUANT_MILLIS
        noteOp(OPSTR_RESERVED_FOR_TESTING, uid, PACKAGE_NAME, null, null)

        var allOps = getHistoricalOps()

        assertThat(allOps).isNotNull()
        assertThat(allOps!!.uidCount).isEqualTo(1)

        var uidOps = allOps.getUidOpsAt(0)
        assertThat(uidOps).isNotNull()
        assertThat(uidOps.uid).isEqualTo(uid)
        assertThat(uidOps.packageCount).isEqualTo(1)

        var packageOps = uidOps.getPackageOpsAt(0)
        assertThat(packageOps).isNotNull()
        assertThat(packageOps.packageName).isEqualTo(PACKAGE_NAME)
        assertThat(packageOps.opCount).isEqualTo(1)

        var op = packageOps.getOpAt(0)
        assertThat(op).isNotNull()
        assertThat(op.opName).isEqualTo(OPSTR_RESERVED_FOR_TESTING)
        assertThat(op.discreteAccessCount).isEqualTo(1)
        var discrete = op.getDiscreteAccessAt(0)

        assertThat(discrete.getLastDuration(OP_FLAGS_ALL)).isEqualTo(-1)
        assertThat(discrete.getLastAccessTime(OP_FLAGS_ALL)).isEqualTo(timeStamp)
        runWithShellPermissionIdentity {
            appOpsManager.offsetHistory(DEFAULT_TIME_QUANT_MILLIS)
        }

        allOps = getHistoricalOps()
        assertThat(allOps).isNotNull()
        assertThat(allOps!!.uidCount).isEqualTo(1)

        uidOps = allOps.getUidOpsAt(0)
        assertThat(uidOps).isNotNull()
        assertThat(uidOps.uid).isEqualTo(uid)
        assertThat(uidOps.packageCount).isEqualTo(1)

        packageOps = uidOps.getPackageOpsAt(0)
        assertThat(packageOps).isNotNull()
        assertThat(packageOps.packageName).isEqualTo(PACKAGE_NAME)
        assertThat(packageOps.opCount).isEqualTo(1)

        op = packageOps.getOpAt(0)
        assertThat(op).isNotNull()
        assertThat(op.opName).isEqualTo(OPSTR_RESERVED_FOR_TESTING)
        assertThat(op.discreteAccessCount).isEqualTo(1)
        discrete = op.getDiscreteAccessAt(0)

        assertThat(discrete.getLastDuration(OP_FLAGS_ALL)).isEqualTo(-1)
        assertThat(discrete.getLastAccessTime(OP_FLAGS_ALL))
                .isEqualTo(timeStamp - DEFAULT_TIME_QUANT_MILLIS)
    }

    @Test
    fun testHistoryTimeParameter() {
        waitUntilSafelyInTimeQuant(DEFAULT_TIME_QUANT_MILLIS, SAFETY_MARGIN_MILLIS)
        var timeStamp = System.currentTimeMillis() /
                DEFAULT_TIME_QUANT_MILLIS * DEFAULT_TIME_QUANT_MILLIS - TEN_MINUTES_MILLIS
        noteOp(OPSTR_RESERVED_FOR_TESTING, uid, PACKAGE_NAME)

        runWithShellPermissionIdentity {
            appOpsManager.offsetHistory(TEN_MINUTES_MILLIS)
        }

        var allOps = getHistoricalOps()

        assertThat(allOps).isNotNull()
        assertThat(allOps!!.uidCount).isEqualTo(1)

        var uidOps = allOps.getUidOpsAt(0)
        assertThat(uidOps).isNotNull()
        assertThat(uidOps.uid).isEqualTo(uid)
        assertThat(uidOps.packageCount).isEqualTo(1)

        var packageOps = uidOps.getPackageOpsAt(0)
        assertThat(packageOps).isNotNull()
        assertThat(packageOps.packageName).isEqualTo(PACKAGE_NAME)
        assertThat(packageOps.opCount).isEqualTo(1)

        var op = packageOps.getOpAt(0)
        assertThat(op).isNotNull()
        assertThat(op.opName).isEqualTo(OPSTR_RESERVED_FOR_TESTING)
        assertThat(op.discreteAccessCount).isEqualTo(1)
        var discrete = op.getDiscreteAccessAt(0)

        assertThat(discrete.getLastDuration(OP_FLAGS_ALL)).isEqualTo(-1)
        assertThat(discrete.getLastAccessTime(OP_FLAGS_ALL))
                .isEqualTo(timeStamp)

        allOps = getHistoricalOps(HISTORY_FLAGS_ALL, null, OP_FLAGS_ALL,
                System.currentTimeMillis() - ONE_MINUTE_MILLIS, Long.MAX_VALUE)
        assertThat(allOps).isNotNull()
        assertThat(allOps!!.uidCount).isEqualTo(0)
    }

    @Test
    @FlakyTest
    fun testDeduplicationUidState() {
        makeTop() // pre-warm application uid state change to make it faster during test run
        makeBackground()
        setQuantization(SHORT_TIME_QUANT_MILLIS)
        waitUntilNextQuantStarts(SHORT_TIME_QUANT_MILLIS)
        val timestamp = System.currentTimeMillis() /
            SHORT_TIME_QUANT_MILLIS * SHORT_TIME_QUANT_MILLIS

        noteOp(OPSTR_RESERVED_FOR_TESTING, uid, PACKAGE_NAME)

        makeTop()

        noteOp(OPSTR_RESERVED_FOR_TESTING, uid, PACKAGE_NAME)

        makeBackground()

        waitUntilNextQuantStarts(SHORT_TIME_QUANT_MILLIS)
        noteOp(OPSTR_RESERVED_FOR_TESTING, uid, PACKAGE_NAME)

        var allOps = getHistoricalOps()
        assertThat(allOps).isNotNull()
        assertThat(allOps!!.uidCount).isEqualTo(1)

        var uidOps = allOps.getUidOpsAt(0)
        assertThat(uidOps).isNotNull()
        assertThat(uidOps.uid).isEqualTo(uid)
        assertThat(uidOps.packageCount).isEqualTo(1)

        var packageOps = uidOps.getPackageOpsAt(0)
        assertThat(packageOps).isNotNull()
        assertThat(packageOps.packageName).isEqualTo(PACKAGE_NAME)

        var op = packageOps.getOp(OPSTR_RESERVED_FOR_TESTING)
        assertThat(op).isNotNull()
        assertThat(op!!.discreteAccessCount).isEqualTo(2)
        var discrete = op!!.getDiscreteAccessAt(0)

        assertThat(discrete.getLastDuration(OP_FLAGS_ALL)).isEqualTo(-1)
        assertThat(discrete.getLastAccessBackgroundTime(OP_FLAGS_ALL)).isEqualTo(timestamp)
        assertThat(discrete.getLastAccessForegroundTime(OP_FLAGS_ALL)).isEqualTo(timestamp)
        discrete = op!!.getDiscreteAccessAt(1)

        assertThat(discrete.getLastDuration(OP_FLAGS_ALL)).isEqualTo(-1)
        assertThat(discrete.getLastAccessBackgroundTime(OP_FLAGS_ALL))
                .isEqualTo(timestamp + SHORT_TIME_QUANT_MILLIS)
        assertThat(discrete.getLastAccessForegroundTime(OP_FLAGS_ALL)).isEqualTo(-1)
    }

    @Test
    @FlakyTest
    fun testDeduplicationAttributions() {
        setQuantization(SHORT_TIME_QUANT_MILLIS)
        waitUntilNextQuantStarts(SHORT_TIME_QUANT_MILLIS)
        val timestamp = System.currentTimeMillis() /
                SHORT_TIME_QUANT_MILLIS * SHORT_TIME_QUANT_MILLIS

        noteOp(OPSTR_RESERVED_FOR_TESTING, uid, PACKAGE_NAME, TAG1)
        noteOp(OPSTR_RESERVED_FOR_TESTING, uid, PACKAGE_NAME, TAG2)
        Thread.sleep(SHORT_TIME_QUANT_MILLIS)
        noteOp(OPSTR_RESERVED_FOR_TESTING, uid, PACKAGE_NAME, TAG1)
        var allOps = getHistoricalOps(HISTORY_FLAG_DISCRETE)
        assertThat(allOps).isNotNull()
        assertThat(allOps!!.uidCount).isEqualTo(1)

        var uidOps = allOps.getUidOpsAt(0)
        assertThat(uidOps).isNotNull()
        assertThat(uidOps.uid).isEqualTo(uid)
        assertThat(uidOps.packageCount).isEqualTo(1)

        var packageOps = uidOps.getPackageOpsAt(0)
        assertThat(packageOps).isNotNull()
        assertThat(packageOps.packageName).isEqualTo(PACKAGE_NAME)
        assertThat(packageOps.opCount).isEqualTo(1)

        var op = packageOps.getOpAt(0)
        assertThat(op).isNotNull()
        assertThat(op.opName).isEqualTo(OPSTR_RESERVED_FOR_TESTING)
        assertThat(op.discreteAccessCount).isEqualTo(2)
        var discrete = op.getDiscreteAccessAt(0)
        assertThat(discrete.getLastDuration(OP_FLAGS_ALL)).isEqualTo(-1)
        assertThat(discrete.getLastAccessTime(OP_FLAGS_ALL)).isEqualTo(timestamp)

        discrete = op.getDiscreteAccessAt(1)
        assertThat(discrete.getLastDuration(OP_FLAGS_ALL)).isEqualTo(-1)
        assertThat(discrete.getLastAccessTime(OP_FLAGS_ALL))
                .isEqualTo(timestamp + SHORT_TIME_QUANT_MILLIS)

        assertThat(packageOps.attributedOpsCount).isEqualTo(2)
        var attribution = packageOps.getAttributedOps(TAG1)
        assertThat(attribution).isNotNull()
        assertThat(attribution!!.opCount).isEqualTo(1)
        op = attribution.getOpAt(0)
        assertThat(op).isNotNull()
        assertThat(op.opName).isEqualTo(OPSTR_RESERVED_FOR_TESTING)
        assertThat(op.discreteAccessCount).isEqualTo(2)
        discrete = op.getDiscreteAccessAt(0)
        assertThat(discrete.getLastDuration(OP_FLAGS_ALL)).isEqualTo(-1)
        assertThat(discrete.getLastAccessTime(OP_FLAGS_ALL)).isEqualTo(timestamp)

        discrete = op.getDiscreteAccessAt(1)
        assertThat(discrete.getLastDuration(OP_FLAGS_ALL)).isEqualTo(-1)
        assertThat(discrete.getLastAccessTime(OP_FLAGS_ALL))
                .isEqualTo(timestamp + SHORT_TIME_QUANT_MILLIS)

        attribution = packageOps.getAttributedOps(TAG2)
        assertThat(attribution).isNotNull()
        assertThat(attribution!!.opCount).isEqualTo(1)
        op = attribution.getOpAt(0)
        assertThat(op).isNotNull()
        assertThat(op.opName).isEqualTo(OPSTR_RESERVED_FOR_TESTING)
        assertThat(op.discreteAccessCount).isEqualTo(1)
        discrete = op.getDiscreteAccessAt(0)
        assertThat(discrete.getLastDuration(OP_FLAGS_ALL)).isEqualTo(-1)
        assertThat(discrete.getLastAccessTime(OP_FLAGS_ALL)).isEqualTo(timestamp)
    }

    @Test
    @FlakyTest
    fun testCutoffTime() {
        runWithShellPermissionIdentity {
            DeviceConfig.setProperty(NAMESPACE_PRIVACY, PROPERTY_CUTOFF, 120000L.toString(), false)
        }
        // Pause to give the AppOpsService (DiscreteRegistry) time to pick up the new value.
        Thread.sleep(1000)

        waitUntilSafelyInTimeQuant(DEFAULT_TIME_QUANT_MILLIS, SAFETY_MARGIN_MILLIS)
        val timeStamp = System.currentTimeMillis() /
                DEFAULT_TIME_QUANT_MILLIS * DEFAULT_TIME_QUANT_MILLIS

        noteOp(OPSTR_RESERVED_FOR_TESTING, uid, PACKAGE_NAME)
        var allOps = getHistoricalOps()
        assertThat(allOps).isNotNull()
        assertThat(allOps!!.uidCount).isEqualTo(1)

        var uidOps = allOps.getUidOpsAt(0)
        assertThat(uidOps).isNotNull()
        assertThat(uidOps.uid).isEqualTo(uid)
        assertThat(uidOps.packageCount).isEqualTo(1)

        var packageOps = uidOps.getPackageOpsAt(0)
        assertThat(packageOps).isNotNull()
        assertThat(packageOps.packageName).isEqualTo(PACKAGE_NAME)
        assertThat(packageOps.opCount).isEqualTo(1)

        var op = packageOps.getOpAt(0)
        assertThat(op).isNotNull()
        assertThat(op.opName).isEqualTo(OPSTR_RESERVED_FOR_TESTING)
        assertThat(op.discreteAccessCount).isEqualTo(1)
        var discrete = op.getDiscreteAccessAt(0)

        assertThat(discrete.getLastDuration(OP_FLAGS_ALL)).isEqualTo(-1)
        assertThat(discrete.getLastAccessTime(OP_FLAGS_ALL)).isEqualTo(timeStamp)

        runWithShellPermissionIdentity {
            appOpsManager.offsetHistory(180000)
        }

        allOps = getHistoricalOps()
        assertThat(allOps).isNotNull()
        assertThat(allOps!!.uidCount).isEqualTo(0)
    }

    @Test
    @FlakyTest
    fun testMixedDeduplication() {
        setQuantization(SHORT_TIME_QUANT_MILLIS)
        waitUntilNextQuantStarts(SHORT_TIME_QUANT_MILLIS)
        makeTop() // pre-warm application uid state change to make it faster during test run
        makeBackground()
        val timestamp = System.currentTimeMillis() /
                SHORT_TIME_QUANT_MILLIS * SHORT_TIME_QUANT_MILLIS
        val timestamp2 = timestamp + SHORT_TIME_QUANT_MILLIS
        val timestamp3 = timestamp2 + SHORT_TIME_QUANT_MILLIS
        val timestamp4 = timestamp3 + SHORT_TIME_QUANT_MILLIS
        // first quant - foreground access in tag1, background in tag2
        noteOp(OPSTR_RESERVED_FOR_TESTING, uid, PACKAGE_NAME, TAG2)
        makeTop()
        noteOp(OPSTR_RESERVED_FOR_TESTING, uid, PACKAGE_NAME, TAG1)
        waitUntilNextQuantStarts(SHORT_TIME_QUANT_MILLIS)

        // second quant - background access in tag1, foreground in tag2
        noteOp(OPSTR_RESERVED_FOR_TESTING, uid, PACKAGE_NAME, TAG2, null)
        makeBackground()
        noteOp(OPSTR_RESERVED_FOR_TESTING, uid, PACKAGE_NAME, TAG1, null)
        makeTop()
        waitUntilNextQuantStarts(SHORT_TIME_QUANT_MILLIS)

        // third quant - single foreground access in tag1, nothing in tag2
        noteOp(OPSTR_RESERVED_FOR_TESTING, uid, PACKAGE_NAME, TAG1, null)
        noteOp(OPSTR_RESERVED_FOR_TESTING, uid, PACKAGE_NAME, TAG1, null)
        noteOp(OPSTR_RESERVED_FOR_TESTING, uid, PACKAGE_NAME, TAG1, null)
        noteOp(OPSTR_RESERVED_FOR_TESTING, uid, PACKAGE_NAME, TAG1, null)
        makeBackground()
        waitUntilNextQuantStarts(SHORT_TIME_QUANT_MILLIS)

        // fourth quant - single background access in tag2, nothing in tag1
        noteOp(OPSTR_RESERVED_FOR_TESTING, uid, PACKAGE_NAME, TAG2, null)
        noteOp(OPSTR_RESERVED_FOR_TESTING, uid, PACKAGE_NAME, TAG2, null)
        var allOps = getHistoricalOps(HISTORY_FLAG_DISCRETE)
        assertThat(allOps).isNotNull()
        assertThat(allOps!!.uidCount).isEqualTo(1)

        var uidOps = allOps.getUidOpsAt(0)
        assertThat(uidOps).isNotNull()
        assertThat(uidOps.uid).isEqualTo(uid)
        assertThat(uidOps.packageCount).isEqualTo(1)

        var packageOps = uidOps.getPackageOpsAt(0)
        assertThat(packageOps).isNotNull()
        assertThat(packageOps.packageName).isEqualTo(PACKAGE_NAME)

        var op = packageOps.getOp(OPSTR_RESERVED_FOR_TESTING)
        assertThat(op).isNotNull()
        assertThat(op!!.discreteAccessCount).isEqualTo(4)
        var discrete = op!!.getDiscreteAccessAt(0)
        assertThat(discrete.getLastAccessForegroundTime(OP_FLAGS_ALL)).isEqualTo(timestamp)
        assertThat(discrete.getLastAccessBackgroundTime(OP_FLAGS_ALL)).isEqualTo(timestamp)

        discrete = op!!.getDiscreteAccessAt(1)
        assertThat(discrete.getLastAccessForegroundTime(OP_FLAGS_ALL)).isEqualTo(timestamp2)
        assertThat(discrete.getLastAccessBackgroundTime(OP_FLAGS_ALL)).isEqualTo(timestamp2)

        discrete = op!!.getDiscreteAccessAt(2)
        assertThat(discrete.getLastAccessForegroundTime(OP_FLAGS_ALL)).isEqualTo(timestamp3)
        assertThat(discrete.getLastAccessBackgroundTime(OP_FLAGS_ALL)).isEqualTo(-1)

        discrete = op!!.getDiscreteAccessAt(3)
        assertThat(discrete.getLastAccessForegroundTime(OP_FLAGS_ALL)).isEqualTo(-1)
        assertThat(discrete.getLastAccessBackgroundTime(OP_FLAGS_ALL)).isEqualTo(timestamp4)

        var attribution = packageOps.getAttributedOps(TAG1)
        assertThat(attribution).isNotNull()
        assertThat(attribution!!.opCount).isEqualTo(1)
        op = attribution.getOpAt(0)
        assertThat(op).isNotNull()
        assertThat(op.opName).isEqualTo(OPSTR_RESERVED_FOR_TESTING)
        assertThat(op.discreteAccessCount).isEqualTo(3)
        discrete = op.getDiscreteAccessAt(0)
        assertThat(discrete.getLastAccessForegroundTime(OP_FLAGS_ALL)).isEqualTo(timestamp)
        assertThat(discrete.getLastAccessBackgroundTime(OP_FLAGS_ALL)).isEqualTo(-1)

        discrete = op.getDiscreteAccessAt(1)
        assertThat(discrete.getLastAccessForegroundTime(OP_FLAGS_ALL)).isEqualTo(-1)
        assertThat(discrete.getLastAccessBackgroundTime(OP_FLAGS_ALL)).isEqualTo(timestamp2)

        discrete = op.getDiscreteAccessAt(2)
        assertThat(discrete.getLastAccessForegroundTime(OP_FLAGS_ALL)).isEqualTo(timestamp3)
        assertThat(discrete.getLastAccessBackgroundTime(OP_FLAGS_ALL)).isEqualTo(-1)

        attribution = packageOps.getAttributedOps(TAG2)
        assertThat(attribution).isNotNull()
        assertThat(attribution!!.opCount).isEqualTo(1)
        op = attribution.getOpAt(0)
        assertThat(op).isNotNull()
        assertThat(op.opName).isEqualTo(OPSTR_RESERVED_FOR_TESTING)
        assertThat(op.discreteAccessCount).isEqualTo(3)
        discrete = op.getDiscreteAccessAt(0)
        assertThat(discrete.getLastAccessForegroundTime(OP_FLAGS_ALL)).isEqualTo(-1)
        assertThat(discrete.getLastAccessBackgroundTime(OP_FLAGS_ALL)).isEqualTo(timestamp)

        discrete = op.getDiscreteAccessAt(1)
        assertThat(discrete.getLastAccessForegroundTime(OP_FLAGS_ALL)).isEqualTo(timestamp2)
        assertThat(discrete.getLastAccessBackgroundTime(OP_FLAGS_ALL)).isEqualTo(-1)

        discrete = op.getDiscreteAccessAt(2)
        assertThat(discrete.getLastAccessForegroundTime(OP_FLAGS_ALL)).isEqualTo(-1)
        assertThat(discrete.getLastAccessBackgroundTime(OP_FLAGS_ALL)).isEqualTo(timestamp4)

        // Test the same result using alternative accessors.
        var accesses = op.getBackgroundDiscreteAccesses(OP_FLAGS_ALL)
        assertThat(accesses.size).isEqualTo(2)
        assertThat(accesses[0].getLastAccessTime(OP_FLAGS_ALL)).isEqualTo(timestamp)
        assertThat(accesses[1].getLastAccessTime(OP_FLAGS_ALL)).isEqualTo(timestamp4)

        accesses = op.getForegroundDiscreteAccesses(OP_FLAGS_ALL)
        assertThat(accesses.size).isEqualTo(1)
        assertThat(accesses[0].getLastAccessTime(OP_FLAGS_ALL)).isEqualTo(timestamp2)

        accesses = op.getDiscreteAccesses(UID_STATE_PERSISTENT, UID_STATE_CACHED, OP_FLAGS_ALL)
        assertThat(accesses.size).isEqualTo(3)
        assertThat(accesses[0].getLastAccessTime(OP_FLAGS_ALL)).isEqualTo(timestamp)
        assertThat(accesses[1].getLastAccessTime(OP_FLAGS_ALL)).isEqualTo(timestamp2)
        assertThat(accesses[2].getLastAccessTime(OP_FLAGS_ALL)).isEqualTo(timestamp4)
    }

    @Test
    @FlakyTest
    fun testOperationWithDuration() {
        setQuantization(SHORT_TIME_QUANT_MILLIS)
        waitUntilNextQuantStarts(SHORT_TIME_QUANT_MILLIS)
        val timestamp = System.currentTimeMillis() /
                SHORT_TIME_QUANT_MILLIS * SHORT_TIME_QUANT_MILLIS

        runWithShellPermissionIdentity {
            appOpsManager.startOp(OPSTR_RESERVED_FOR_TESTING, uid, PACKAGE_NAME, null, null)
        }

        // First test that unfinished op was added without duration
        var allOps = getHistoricalOps()
        assertThat(allOps).isNotNull()
        assertThat(allOps!!.uidCount).isEqualTo(1)

        var uidOps = allOps.getUidOpsAt(0)
        assertThat(uidOps).isNotNull()
        assertThat(uidOps.uid).isEqualTo(uid)
        assertThat(uidOps.packageCount).isEqualTo(1)

        var packageOps = uidOps.getPackageOpsAt(0)
        assertThat(packageOps).isNotNull()
        assertThat(packageOps.packageName).isEqualTo(PACKAGE_NAME)
        assertThat(packageOps.opCount).isEqualTo(1)

        var op = packageOps.getOpAt(0)
        assertThat(op).isNotNull()
        assertThat(op.opName).isEqualTo(OPSTR_RESERVED_FOR_TESTING)
        assertThat(op.discreteAccessCount).isEqualTo(1)
        var discrete = op.getDiscreteAccessAt(0)

        assertThat(discrete.getLastDuration(OP_FLAGS_ALL)).isEqualTo(-1)
        assertThat(discrete.getLastAccessTime(OP_FLAGS_ALL)).isEqualTo(timestamp)
        Thread.sleep(SHORT_TIME_QUANT_MILLIS)
        runWithShellPermissionIdentity {
            appOpsManager.finishOp(OPSTR_RESERVED_FOR_TESTING, uid, PACKAGE_NAME, null)
        }

        allOps = getHistoricalOps()
        assertThat(allOps).isNotNull()
        assertThat(allOps!!.uidCount).isEqualTo(1)

        uidOps = allOps.getUidOpsAt(0)
        assertThat(uidOps).isNotNull()
        assertThat(uidOps.uid).isEqualTo(uid)
        assertThat(uidOps.packageCount).isEqualTo(1)

        packageOps = uidOps.getPackageOpsAt(0)
        assertThat(packageOps).isNotNull()
        assertThat(packageOps.packageName).isEqualTo(PACKAGE_NAME)
        assertThat(packageOps.opCount).isEqualTo(1)

        op = packageOps.getOpAt(0)
        assertThat(op).isNotNull()
        assertThat(op.opName).isEqualTo(OPSTR_RESERVED_FOR_TESTING)
        assertThat(op.discreteAccessCount).isEqualTo(1)
        discrete = op.getDiscreteAccessAt(0)

        assertThat(discrete.getLastDuration(OP_FLAGS_ALL)).isEqualTo(2 * SHORT_TIME_QUANT_MILLIS)
        assertThat(discrete.getLastAccessTime(OP_FLAGS_ALL)).isEqualTo(timestamp)
    }

    @Test
    fun testOperationWithDurationForcedSavedDuringTransaction() {
        setQuantization(SHORT_TIME_QUANT_MILLIS)
        waitUntilNextQuantStarts(SHORT_TIME_QUANT_MILLIS)
        val timestamp = System.currentTimeMillis() /
                SHORT_TIME_QUANT_MILLIS * SHORT_TIME_QUANT_MILLIS

        runWithShellPermissionIdentity {
            appOpsManager.startOp(OPSTR_RESERVED_FOR_TESTING, uid, PACKAGE_NAME, null, null)
        }

        // Force persistence
        runWithShellPermissionIdentity {
            appOpsManager.offsetHistory(0)
        }

        // First test that unfinished op was added without duration
        var allOps = getHistoricalOps()
        assertThat(allOps).isNotNull()
        assertThat(allOps!!.uidCount).isEqualTo(1)

        var uidOps = allOps.getUidOpsAt(0)
        assertThat(uidOps).isNotNull()
        assertThat(uidOps.uid).isEqualTo(uid)
        assertThat(uidOps.packageCount).isEqualTo(1)

        var packageOps = uidOps.getPackageOpsAt(0)
        assertThat(packageOps).isNotNull()
        assertThat(packageOps.packageName).isEqualTo(PACKAGE_NAME)
        assertThat(packageOps.opCount).isEqualTo(1)

        var op = packageOps.getOpAt(0)
        assertThat(op).isNotNull()
        assertThat(op.opName).isEqualTo(OPSTR_RESERVED_FOR_TESTING)
        assertThat(op.discreteAccessCount).isEqualTo(1)
        var discrete = op.getDiscreteAccessAt(0)

        assertThat(discrete.getLastDuration(OP_FLAGS_ALL)).isEqualTo(-1)
        assertThat(discrete.getLastAccessTime(OP_FLAGS_ALL)).isEqualTo(timestamp)
        Thread.sleep(SHORT_TIME_QUANT_MILLIS)
        runWithShellPermissionIdentity {
            appOpsManager.finishOp(OPSTR_RESERVED_FOR_TESTING, uid, PACKAGE_NAME, null)
        }

        allOps = getHistoricalOps()
        assertThat(allOps).isNotNull()
        assertThat(allOps!!.uidCount).isEqualTo(1)

        uidOps = allOps.getUidOpsAt(0)
        assertThat(uidOps).isNotNull()
        assertThat(uidOps.uid).isEqualTo(uid)
        assertThat(uidOps.packageCount).isEqualTo(1)

        packageOps = uidOps.getPackageOpsAt(0)
        assertThat(packageOps).isNotNull()
        assertThat(packageOps.packageName).isEqualTo(PACKAGE_NAME)
        assertThat(packageOps.opCount).isEqualTo(1)

        op = packageOps.getOpAt(0)
        assertThat(op).isNotNull()
        assertThat(op.opName).isEqualTo(OPSTR_RESERVED_FOR_TESTING)
        assertThat(op.discreteAccessCount).isEqualTo(1)
        discrete = op.getDiscreteAccessAt(0)

        assertThat(discrete.getLastDuration(OP_FLAGS_ALL)).isEqualTo(2 * SHORT_TIME_QUANT_MILLIS)
        assertThat(discrete.getLastAccessTime(OP_FLAGS_ALL)).isEqualTo(timestamp)
    }

    @Test
    fun testOpFlagsAndDeduplication() {
        waitUntilSafelyInTimeQuant(DEFAULT_TIME_QUANT_MILLIS, SAFETY_MARGIN_MILLIS)
        runWithShellPermissionIdentity {
            appOpsManager.noteProxyOp(OPSTR_RESERVED_FOR_TESTING, PACKAGE_NAME, uid, null, null)
            appOpsManager.noteOp(OPSTR_RESERVED_FOR_TESTING, uid, PACKAGE_NAME, null, null)
        }

        val timeStamp = System.currentTimeMillis() /
                DEFAULT_TIME_QUANT_MILLIS * DEFAULT_TIME_QUANT_MILLIS
        Thread.sleep(500)
        var allOps = getHistoricalOps(HISTORY_FLAG_DISCRETE)

        assertThat(allOps).isNotNull()
        assertThat(allOps!!.uidCount).isEqualTo(1)

        var uidOps = allOps.getUidOpsAt(0)
        assertThat(uidOps).isNotNull()
        assertThat(uidOps.uid).isEqualTo(uid)
        assertThat(uidOps.packageCount).isEqualTo(1)

        var packageOps = uidOps.getPackageOpsAt(0)
        assertThat(packageOps).isNotNull()
        assertThat(packageOps.packageName).isEqualTo(PACKAGE_NAME)
        assertThat(packageOps.opCount).isEqualTo(1)

        var op = packageOps.getOpAt(0)
        assertThat(op).isNotNull()
        assertThat(op.opName).isEqualTo(OPSTR_RESERVED_FOR_TESTING)
        assertThat(op.discreteAccessCount).isEqualTo(1)
        var discrete = op.getDiscreteAccessAt(0)

        assertThat(discrete.getLastDuration(OP_FLAGS_ALL)).isEqualTo(-1)
        assertThat(discrete.getLastAccessTime(OP_FLAGS_ALL)).isEqualTo(timeStamp)
        assertThat(discrete.getLastAccessTime(OP_FLAG_TRUSTED_PROXIED)).isEqualTo(timeStamp)
        assertThat(discrete.getLastAccessTime(OP_FLAG_SELF)).isEqualTo(timeStamp)

        allOps = getHistoricalOps(HISTORY_FLAG_DISCRETE, null, OP_FLAG_TRUSTED_PROXIED)

        assertThat(allOps).isNotNull()
        assertThat(allOps!!.uidCount).isEqualTo(1)

        uidOps = allOps.getUidOpsAt(0)
        assertThat(uidOps).isNotNull()
        assertThat(uidOps.uid).isEqualTo(uid)
        assertThat(uidOps.packageCount).isEqualTo(1)

        packageOps = uidOps.getPackageOpsAt(0)
        assertThat(packageOps).isNotNull()
        assertThat(packageOps.packageName).isEqualTo(PACKAGE_NAME)
        assertThat(packageOps.opCount).isEqualTo(1)

        op = packageOps.getOpAt(0)
        assertThat(op).isNotNull()
        assertThat(op.opName).isEqualTo(OPSTR_RESERVED_FOR_TESTING)
        assertThat(op.discreteAccessCount).isEqualTo(1)
        discrete = op.getDiscreteAccessAt(0)

        assertThat(discrete.getLastDuration(OP_FLAGS_ALL)).isEqualTo(-1)
        assertThat(discrete.getLastAccessTime(OP_FLAGS_ALL)).isEqualTo(timeStamp)
        assertThat(discrete.getLastAccessTime(OP_FLAG_TRUSTED_PROXIED)).isEqualTo(timeStamp)
        assertThat(discrete.getLastAccessTime(OP_FLAG_SELF)).isEqualTo(-1)

        // test alternative accessor
        var accesses = op.getDiscreteAccesses(UID_STATE_CACHED, UID_STATE_PERSISTENT, OP_FLAG_SELF)
        assertThat(accesses.size).isEqualTo(0)
        accesses = op.getDiscreteAccesses(
                UID_STATE_PERSISTENT, UID_STATE_CACHED, OP_FLAG_TRUSTED_PROXIED)
        assertThat(accesses.size).isEqualTo(1)
    }

    @Test
    fun testOpFlagsParameter() {
        // Collect only OP_FLAG_TRUSTED_PROXIED (8)
        runWithShellPermissionIdentity {
            DeviceConfig.setProperty(NAMESPACE_PRIVACY, PROPERTY_FLAGS, 8.toString(), false)
        }

        var allOps: HistoricalOps? = null

        for (i in 1..3) {
            waitUntilSafelyInTimeQuant(DEFAULT_TIME_QUANT_MILLIS, SAFETY_MARGIN_MILLIS)
            runWithShellPermissionIdentity {
                appOpsManager.noteProxyOp(OPSTR_RESERVED_FOR_TESTING, PACKAGE_NAME, uid, null, null)
                appOpsManager.noteOp(OPSTR_RESERVED_FOR_TESTING, uid, PACKAGE_NAME, null, null)
            }
            allOps = getHistoricalOps(HISTORY_FLAG_DISCRETE)
            if (allOps!!.getUidOpsAt(0).getPackageOpsAt(0).getOpAt(0).getDiscreteAccessAt(0)
                            .getLastAccessTime(OP_FLAG_SELF) == -1L) {
                break
            }
            runWithShellPermissionIdentity {
                appOpsManager.clearHistory()
            }
            // maybe device_config callback didn't propagate yet, give it some more time
            Thread.sleep(CALLBACK_PROPAGATION_DELAY)
        }

        val timeStamp = System.currentTimeMillis() /
                DEFAULT_TIME_QUANT_MILLIS * DEFAULT_TIME_QUANT_MILLIS

        assertThat(allOps).isNotNull()
        assertThat(allOps!!.uidCount).isEqualTo(1)

        var uidOps = allOps.getUidOpsAt(0)
        assertThat(uidOps).isNotNull()
        assertThat(uidOps.uid).isEqualTo(uid)
        assertThat(uidOps.packageCount).isEqualTo(1)

        var packageOps = uidOps.getPackageOpsAt(0)
        assertThat(packageOps).isNotNull()
        assertThat(packageOps.packageName).isEqualTo(PACKAGE_NAME)
        assertThat(packageOps.opCount).isEqualTo(1)

        var op = packageOps.getOpAt(0)
        assertThat(op).isNotNull()
        assertThat(op.opName).isEqualTo(OPSTR_RESERVED_FOR_TESTING)
        assertThat(op.discreteAccessCount).isEqualTo(1)
        var discrete = op.getDiscreteAccessAt(0)

        assertThat(discrete.getLastDuration(OP_FLAGS_ALL)).isEqualTo(-1)
        assertThat(discrete.getLastAccessTime(OP_FLAGS_ALL)).isEqualTo(timeStamp)
        assertThat(discrete.getLastAccessTime(OP_FLAG_TRUSTED_PROXIED)).isEqualTo(timeStamp)
        assertThat(discrete.getLastAccessTime(OP_FLAG_SELF)).isEqualTo(-1)
    }

    @Test
    fun testOpsListParameter() {
        // collect only OP_FINE_LOCATION = 1
        makeTop()
        runWithShellPermissionIdentity {
            DeviceConfig.setProperty(NAMESPACE_PRIVACY, PROPERTY_OPS_LIST, "1", false)
        }
        var allOps: HistoricalOps? = null
        for (i in 1..3) {
            waitUntilSafelyInTimeQuant(DEFAULT_TIME_QUANT_MILLIS, SAFETY_MARGIN_MILLIS)
            noteOp(OPSTR_FINE_LOCATION, uid, PACKAGE_NAME, null, null)
            noteOp(OPSTR_RESERVED_FOR_TESTING, uid, PACKAGE_NAME, null, null)
            allOps = getHistoricalOps(HISTORY_FLAG_DISCRETE)
            if (allOps!!.getUidOpsAt(0).getPackageOpsAt(0).opCount == 1) break
            runWithShellPermissionIdentity {
                appOpsManager.clearHistory()
            }
            // maybe device_config callback didn't propagate yet, give it some more time
            Thread.sleep(CALLBACK_PROPAGATION_DELAY)
        }

        val timeStamp = System.currentTimeMillis() /
                DEFAULT_TIME_QUANT_MILLIS * DEFAULT_TIME_QUANT_MILLIS

        assertThat(allOps).isNotNull()
        assertThat(allOps!!.uidCount).isEqualTo(1)

        var uidOps = allOps.getUidOpsAt(0)
        assertThat(uidOps).isNotNull()
        assertThat(uidOps.uid).isEqualTo(uid)
        assertThat(uidOps.packageCount).isEqualTo(1)

        var packageOps = uidOps.getPackageOpsAt(0)
        assertThat(packageOps).isNotNull()
        assertThat(packageOps.packageName).isEqualTo(PACKAGE_NAME)
        assertThat(packageOps.opCount).isEqualTo(1)

        var op = packageOps.getOpAt(0)
        assertThat(op).isNotNull()
        assertThat(op.opName).isEqualTo(OPSTR_FINE_LOCATION)
        assertThat(op.discreteAccessCount).isEqualTo(1)
        var discrete = op.getDiscreteAccessAt(0)

        assertThat(discrete.getLastDuration(OP_FLAGS_ALL)).isEqualTo(-1)
        assertThat(discrete.getLastAccessTime(OP_FLAGS_ALL)).isEqualTo(timeStamp)
    }

    /** Provides guarantee that there is at least requiredSafetyMarginMillis milliseconds until next
     *  time quant starts.
     */
    private fun waitUntilSafelyInTimeQuant(
        quantSizeMillis: Long,
        requiredSafetyMarginMillis: Long
    ) {
        while (System.currentTimeMillis() / quantSizeMillis * quantSizeMillis
                != (System.currentTimeMillis() + requiredSafetyMarginMillis) /
                quantSizeMillis * quantSizeMillis) {
            Thread.sleep(1)
        }
    }

    private fun waitUntilNextQuantStarts(quantSizeMillis: Long) {
        val currentTimeQuant = System.currentTimeMillis() / quantSizeMillis * quantSizeMillis
        while (System.currentTimeMillis() / quantSizeMillis * quantSizeMillis
                == currentTimeQuant) {
            Thread.sleep(1)
        }
    }

    private fun enableDiscreteRegistryDebugMode() {
        runWithShellPermissionIdentity {
            appOpsManager.setHistoryParameters(HISTORICAL_MODE_ENABLED_PASSIVE,
                    SNAPSHOT_INTERVAL_MILLIS, INTERVAL_COMPRESSION_MULTIPLIER)
            appOpsManager.setHistoryParameters(HISTORICAL_MODE_ENABLED_ACTIVE,
                    SNAPSHOT_INTERVAL_MILLIS, INTERVAL_COMPRESSION_MULTIPLIER)
        }
    }

    private fun setQuantization(timeQuantMillis: Long) {
        runWithShellPermissionIdentity {
            DeviceConfig.setProperty(NAMESPACE_PRIVACY, PROPERTY_QUANTIZATION,
                    timeQuantMillis.toString(), false)
        }
    }

    private fun noteOp(
        op: String,
        uid: Int,
        packageName: String,
        tag: String? = null,
        message: String? = null
    ) {
        runWithShellPermissionIdentity {
            appOpsManager.noteOp(op, uid, packageName, tag, message)
        }
    }

    private fun getHistoricalOps(
        historyFlags: Int = HISTORY_FLAGS_ALL,
        opNames: List<String>? = null,
        flags: Int = OP_FLAGS_ALL,
        beginTimeMillis: Long = 0,
        endTimeMillis: Long = Long.MAX_VALUE
    ): HistoricalOps? {
        uiAutomation.adoptShellPermissionIdentity()
        val array = arrayOfNulls<HistoricalOps>(1)
        val lock = ReentrantLock()
        val condition = lock.newCondition()
        try {
            lock.lock()
            val request = AppOpsManager.HistoricalOpsRequest.Builder(
                    beginTimeMillis, endTimeMillis)
                    .setUid(uid)
                    .setPackageName(PACKAGE_NAME)
                    .setOpNames(opNames?.toList())
                    .setHistoryFlags(historyFlags)
                    .setFlags(flags)
                    .build()
            appOpsManager.getHistoricalOps(request, context.mainExecutor, Consumer { ops ->
                array[0] = ops
                try {
                    lock.lock()
                    condition.signalAll()
                } finally {
                    lock.unlock()
                }
            })
            condition.await(5, TimeUnit.SECONDS)
            return array[0]
        } finally {
            lock.unlock()
            uiAutomation.dropShellPermissionIdentity()
        }
    }

    private fun makeTop() {
        while (true) {
            context.startActivity(Intent().setComponent(
                    ComponentName(PACKAGE_NAME,
                            "$PACKAGE_NAME.AppOpsForegroundControlActivity"))
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            // b/294950507: Make sure the UidState is also foreground to avoid the race condition
            // between AppOpsService#noteOperationUnchecked and
            // ActivityManagerService#noteUidProcessState that caused flakiness
            if (testPkgAppOpMode == MODE_ALLOWED) {
                if (activityManager.getUidProcessState(uid) < PROCESS_STATE_IMPORTANT_BACKGROUND) {
                    break
                } else {
                    Log.i(LOG_TAG, "AppOpMode is MODE_ALLOWED. However, the UidProcessState" +
                        "is still not in foreground, waiting until the UidProcessState is in" +
                        "foreground.")
                }
            }
            Thread.sleep(100)
        }
    }

    private fun makeBackground() {
        while (true) {
            foregroundControlService.finishActivity()
            foregroundControlService.stopForegroundService()
            foregroundControlService.stopLocationForegroundService()
            if (testPkgAppOpMode == MODE_IGNORED) {
                break
            }
            Thread.sleep(100)
        }
    }
}
