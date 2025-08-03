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

package android.sensorprivacy.cts

import android.app.AppOpsManager
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources.NotFoundException
import android.hardware.SensorPrivacyManager
import android.hardware.SensorPrivacyManager.OnSensorPrivacyChangedListener
import android.hardware.SensorPrivacyManager.OnSensorPrivacyChangedListener.SensorPrivacyChangedParams
import android.hardware.SensorPrivacyManager.Sensors.CAMERA
import android.hardware.SensorPrivacyManager.Sensors.MICROPHONE
import android.hardware.SensorPrivacyManager.Sources.OTHER
import android.hardware.SensorPrivacyManager.TOGGLE_TYPE_HARDWARE
import android.hardware.SensorPrivacyManager.TOGGLE_TYPE_SOFTWARE
import android.os.PowerManager
import android.platform.test.annotations.AppModeFull
import android.platform.test.annotations.AsbSecurityTest
import android.support.test.uiautomator.By
import android.util.Log
import android.view.KeyEvent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.android.compatibility.common.util.SystemUtil
import com.android.compatibility.common.util.SystemUtil.callWithShellPermissionIdentity
import com.android.compatibility.common.util.SystemUtil.eventually
import com.android.compatibility.common.util.SystemUtil.getEventually
import com.android.compatibility.common.util.SystemUtil.runShellCommandOrThrow
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.android.compatibility.common.util.UiAutomatorUtils
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

abstract class SensorPrivacyBaseTest(
    val sensor: Int,
    vararg val extras: String
) {

    companion object {
        val TAG = this::class.simpleName
        const val MIC_CAM_ACTIVITY_ACTION =
                "android.sensorprivacy.cts.usemiccamera.action.USE_MIC_CAM"
        const val MIC_CAM_OVERLAY_ACTIVITY_ACTION =
                "android.sensorprivacy.cts.usemiccamera.overlay.action.USE_MIC_CAM"
        const val SHOW_OVERLAY_ACTION =
                "android.sensorprivacy.cts.usemiccamera.action.SHOW_OVERLAY_ACTION"
        const val FINISH_MIC_CAM_ACTIVITY_ACTION =
                "android.sensorprivacy.cts.usemiccamera.action.FINISH_USE_MIC_CAM"
        const val USE_MIC_EXTRA =
                "android.sensorprivacy.cts.usemiccamera.extra.USE_MICROPHONE"
        const val USE_CAM_EXTRA =
                "android.sensorprivacy.cts.usemiccamera.extra.USE_CAMERA"
        const val DELAYED_ACTIVITY_EXTRA =
                "android.sensorprivacy.cts.usemiccamera.extra.DELAYED_ACTIVITY"
        const val DELAYED_ACTIVITY_NEW_TASK_EXTRA =
                "android.sensorprivacy.cts.usemiccamera.extra.DELAYED_ACTIVITY_NEW_TASK"
        const val RETRY_CAM_EXTRA =
                "android.sensorprivacy.cts.usemiccamera.extra.RETRY_CAM_EXTRA"
        const val PKG_NAME = "android.sensorprivacy.cts.usemiccamera"
        const val RECORDING_FILE_NAME = "${PKG_NAME}_record.mp4"
        const val ACTIVITY_TITLE_SNIP = "CtsUseMic"
        const val SENSOR_USE_TIME_MS = 5L
        const val NEW_WINDOW_TIMEOUT_MILLIS = 5_000L
    }

    protected val instrumentation = InstrumentationRegistry.getInstrumentation()!!
    protected val uiAutomation = instrumentation.uiAutomation!!
    protected val uiDevice = UiDevice.getInstance(instrumentation)!!
    protected val context = instrumentation.targetContext!!
    protected val spm = context.getSystemService(SensorPrivacyManager::class.java)!!
    protected val packageManager = context.packageManager!!
    protected val op = getOpForSensor(sensor)

    var oldState: Boolean = false

    @Before
    open fun init() {
        oldState = isSensorPrivacyEnabled()
        setSensor(false)
        uiDevice.wakeUp()
        runShellCommandOrThrow("wm dismiss-keyguard")
        uiDevice.waitForIdle()
        SystemUtil.waitForBroadcastDispatch(FINISH_MIC_CAM_ACTIVITY_ACTION)
    }

    @After
    fun tearDown() {
        finishTestApp()
        Thread.sleep(3000)
        setSensor(oldState)
    }

    @Test
    fun testSetSensor() {
        assumeSensorToggleSupport()
        setSensor(true)
        assertTrue(isSensorPrivacyEnabled())

        setSensor(false)
        assertFalse(isSensorPrivacyEnabled())
    }

    @Test
    fun testSensorPrivacy_softwareToggle() {
        assumeSensorToggleSupport()
        setSensor(true)
        assertTrue(isToggleSensorPrivacyEnabled(TOGGLE_TYPE_SOFTWARE))

        setSensor(false)
        assertFalse(isToggleSensorPrivacyEnabled(TOGGLE_TYPE_SOFTWARE))
    }

    @Test
    fun testSensorPrivacy_hardwareToggle() {
        assumeSensorToggleSupport()
        // Default value should be false weather HW toggles
        // are supported or not
        assertFalse(isToggleSensorPrivacyEnabled(TOGGLE_TYPE_HARDWARE))
    }

    @Test
    fun testSensorPrivacy_comboToggle() {
        assumeSensorToggleSupport()
        setSensor(sensor, true)
        assertTrue(isCombinedSensorPrivacyEnabled())

        setSensor(sensor, false)
        assertFalse(isCombinedSensorPrivacyEnabled())
    }

    @Test
    fun testDialog() {
        assumeSensorToggleSupport()
        testDialog(delayedActivity = false, delayedActivityNewTask = false)
    }

    @Test
    fun testDialog_remainsOnTop() {
        assumeSensorToggleSupport()
        testDialog(delayedActivity = true, delayedActivityNewTask = false)
    }

    @Test
    fun testDialog_remainsOnTop_newTask() {
        assumeSensorToggleSupport()
        testDialog(delayedActivity = true, delayedActivityNewTask = true)
    }

    fun testDialog(delayedActivity: Boolean = false, delayedActivityNewTask: Boolean = false) {
        try {
            setSensor(true)
            val intent = Intent(MIC_CAM_ACTIVITY_ACTION)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    .addFlags(Intent.FLAG_ACTIVITY_MATCH_EXTERNAL)
            for (extra in extras) {
                intent.putExtra(extra, true)
            }
            intent.putExtra(DELAYED_ACTIVITY_EXTRA, delayedActivity)
            intent.putExtra(DELAYED_ACTIVITY_NEW_TASK_EXTRA, delayedActivityNewTask)
            doAndWaitForWindowTransition {
                context.startActivity(intent)
            }
            Thread.sleep(3000)
            unblockSensorWithDialogAndAssert()
        } finally {
            broadcastAndWait(FINISH_MIC_CAM_ACTIVITY_ACTION)
        }
    }

    @Test
    fun testListener() {
        assumeSensorToggleSupport()
        val executor = Executors.newSingleThreadExecutor()
        setSensor(false)
        val latchEnabled = CountDownLatch(1)
        var listener =
                OnSensorPrivacyChangedListener { _, enabled: Boolean ->
                    if (enabled) {
                        latchEnabled.countDown()
                    }
                }
        runWithShellPermissionIdentity {
            spm.addSensorPrivacyListener(sensor, executor, listener)
        }
        setSensor(true)
        latchEnabled.await(100, TimeUnit.MILLISECONDS)
        runWithShellPermissionIdentity {
            spm.removeSensorPrivacyListener(sensor, listener)
        }

        val latchDisabled = CountDownLatch(1)
        listener = OnSensorPrivacyChangedListener { _, enabled: Boolean ->
            if (!enabled) {
                latchDisabled.countDown()
            }
        }
        runWithShellPermissionIdentity {
            spm.addSensorPrivacyListener(sensor, executor, listener)
        }
        setSensor(false)
        latchEnabled.await(100, TimeUnit.MILLISECONDS)
        runWithShellPermissionIdentity {
            spm.removeSensorPrivacyListener(sensor, listener)
        }
    }

    @Test
    fun testToggleListener() {
        assumeSensorToggleSupport()
        val executor = Executors.newSingleThreadExecutor()
        setSensor(false)
        val latchEnabled = CountDownLatch(1)
        val listenerSensorEnabled = object : OnSensorPrivacyChangedListener {
            override fun onSensorPrivacyChanged(params: SensorPrivacyChangedParams) {
                if (params.isEnabled &&
                        params.sensor == sensor &&
                        params.toggleType == TOGGLE_TYPE_SOFTWARE) {
                    latchEnabled.countDown()
                }
            }

            override fun onSensorPrivacyChanged(sensor: Int, enabled: Boolean) {
            }
        }
        runWithShellPermissionIdentity {
            spm.addSensorPrivacyListener(executor, listenerSensorEnabled)
        }
        setSensor(true)
        latchEnabled.await(100, TimeUnit.MILLISECONDS)
        runWithShellPermissionIdentity {
            spm.removeSensorPrivacyListener(listenerSensorEnabled)
        }

        val latchDisabled = CountDownLatch(1)
        val listenerSensorDisabled = object : OnSensorPrivacyChangedListener {
            override fun onSensorPrivacyChanged(params: SensorPrivacyChangedParams) {
                if (!params.isEnabled &&
                        params.sensor == sensor &&
                        params.toggleType == TOGGLE_TYPE_SOFTWARE) {
                    latchDisabled.countDown()
                }
            }

            override fun onSensorPrivacyChanged(sensor: Int, enabled: Boolean) {
            }
        }
        runWithShellPermissionIdentity {
            spm.addSensorPrivacyListener(executor, listenerSensorDisabled)
        }
        setSensor(false)
        latchEnabled.await(100, TimeUnit.MILLISECONDS)
        runWithShellPermissionIdentity {
            spm.removeSensorPrivacyListener(listenerSensorDisabled)
        }
    }

    @Test
    fun testToggleListener_defaultExecutor() {
        assumeSensorToggleSupport()
        setSensor(false)
        val latchEnabled = CountDownLatch(1)
        var listenerSensorEnabled = object : OnSensorPrivacyChangedListener {
            override fun onSensorPrivacyChanged(params: SensorPrivacyChangedParams) {
                if (params.isEnabled && params.sensor == sensor) {
                    latchEnabled.countDown()
                }
            }

            override fun onSensorPrivacyChanged(sensor: Int, enabled: Boolean) {
            }
        }
        runWithShellPermissionIdentity {
            spm.addSensorPrivacyListener(listenerSensorEnabled)
        }
        setSensor(true)
        latchEnabled.await(100, TimeUnit.MILLISECONDS)
        runWithShellPermissionIdentity {
            spm.removeSensorPrivacyListener(listenerSensorEnabled)
        }

        val latchDisabled = CountDownLatch(1)
        val listenerSensorDisabled = object : OnSensorPrivacyChangedListener {
            override fun onSensorPrivacyChanged(params: SensorPrivacyChangedParams) {
                if (!params.isEnabled && params.sensor == sensor) {
                    latchDisabled.countDown()
                }
            }

            override fun onSensorPrivacyChanged(sensor: Int, enabled: Boolean) {
            }
        }
        runWithShellPermissionIdentity {
            spm.addSensorPrivacyListener(listenerSensorDisabled)
        }
        setSensor(false)
        latchEnabled.await(100, TimeUnit.MILLISECONDS)
        runWithShellPermissionIdentity {
            spm.removeSensorPrivacyListener(listenerSensorDisabled)
        }
    }

    @Test
    @AppModeFull(reason = "Instant apps can't manage keyguard")
    fun testCantChangeWhenLocked() {
        assumeSensorToggleSupport()
        assumeTrue(packageManager.hasSystemFeature(PackageManager.FEATURE_SECURE_LOCK_SCREEN))

//      TODO use actual test api when it can be added
//      assumeTrue(callWithShellPermissionIdentity { spm.requiresAuthentication() })
        val packageContext: Context = context.createPackageContext("android", 0)
        try {
            assumeTrue(packageContext.resources.getBoolean(packageContext.resources
                    .getIdentifier("config_sensorPrivacyRequiresAuthentication", "bool", "android"))
            )
        } catch (e: NotFoundException) {
        // Since by default we want authentication to be required we
        // continue the test if the OEM has removed this resource.
        }

        setSensor(false)
        assertFalse(isSensorPrivacyEnabled())
        runWhileLocked {
            setSensor(true)
            assertFalse("State was changed while device is locked",
                    isSensorPrivacyEnabled())
        }

        setSensor(true)
        assertTrue(isSensorPrivacyEnabled())
        runWhileLocked {
            setSensor(false)
            assertTrue("State was changed while device is locked",
                    isSensorPrivacyEnabled())
        }
    }

    fun unblockSensorWithDialogAndAssert() {
        val buttonResId = getDialogPositiveButtonId()
        UiAutomatorUtils.waitFindObject(By.res(buttonResId)).click()
        eventually {
            assertFalse(isSensorPrivacyEnabled())
        }
    }

    private fun getDialogPositiveButtonId() =
            if (packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
                "com.android.systemui:id/bottom_sheet_positive_button"
            } else {
                "android:id/button1"
            }

    @Test
    @AppModeFull(reason = "Uses secondary app, instant apps have no visibility")
    fun testOpNotRunningWhileSensorPrivacyEnabled() {
        assumeSensorToggleSupport()
        setSensor(false)
        val before = System.currentTimeMillis()
        startTestApp()
        eventually {
            assertOpRunning(true)
        }
        Thread.sleep(SENSOR_USE_TIME_MS)
        setSensor(true)
        eventually {
            val after = System.currentTimeMillis()
            assertOpRunning(false)
            assertLastAccessTimeAndDuration(before, after)
        }
    }

    @Test
    @AppModeFull(reason = "Uses secondary app, instant apps have no visibility")
    fun testOpStartsRunningAfterStartedWithSensoryPrivacyEnabled() {
        assumeSensorToggleSupport()
        setSensor(true)
        // Retry camera connection because external cameras are disconnected
        // if sensor privacy is enabled (b/182204067)
        startTestApp(true)
        UiAutomatorUtils.waitFindObject(By.text(
                Pattern.compile("Cancel", Pattern.CASE_INSENSITIVE))).click()
        assertOpRunning(false)
        setSensor(false)
        eventually {
            assertOpRunning(true)
        }
    }

    @Test
    @AppModeFull(reason = "Uses secondary app, instant apps have no visibility")
    fun testOpGetsRecordedAfterStartedWithSensorPrivacyEnabled() {
        assumeSensorToggleSupport()
        setSensor(true)
        // Retry camera connection because external cameras are disconnected
        // if sensor privacy is enabled (b/182204067)
        startTestApp(true)
        UiAutomatorUtils.waitFindObject(By.text(
                Pattern.compile("Cancel", Pattern.CASE_INSENSITIVE))).click()
        val before = System.currentTimeMillis()
        setSensor(false)
        eventually {
            assertOpRunning(true)
        }
        setSensor(true)
        eventually {
            val after = System.currentTimeMillis()
            assertLastAccessTimeAndDuration(before, after)
        }
    }

    @Test
    @AppModeFull(reason = "Uses secondary app, instant apps have no visibility")
    fun testOpLastAccessUpdatesAfterToggleSensorPrivacy() {
        assumeSensorToggleSupport()
        setSensor(false)
        val before = System.currentTimeMillis()
        startTestApp()
        eventually {
            assertOpRunning(true)
        }
        Thread.sleep(SENSOR_USE_TIME_MS)
        setSensor(true)
        eventually {
            val after = System.currentTimeMillis()
            assertOpRunning(false)
            assertLastAccessTimeAndDuration(before, after)
        }

        val before2 = System.currentTimeMillis()
        setSensor(false)
        eventually {
            assertOpRunning(true)
        }
        Thread.sleep(SENSOR_USE_TIME_MS)
        setSensor(true)
        eventually {
            val after = System.currentTimeMillis()
            assertOpRunning(false)
            assertLastAccessTimeAndDuration(before2, after)
        }
    }

    @Test
    @AppModeFull(reason = "Uses secondary app, instant apps have no visibility")
    fun testOpFinishedWhileToggleOn() {
        assumeSensorToggleSupport()
        setSensor(false)
        startTestApp()
        eventually {
            assertOpRunning(true)
        }
        setSensor(true)
        Thread.sleep(5000)
        eventually {
            assertOpRunning(false)
        }
        finishTestApp()
        Thread.sleep(1000)
        setSensor(false)
        Thread.sleep(1000)
        assertOpRunning(false)
    }

    @Test
    @AsbSecurityTest(cveBugId = [199550934])
    fun testTapjacking() {
        assumeSensorToggleSupport()
        setSensor(true)
        startTestOverlayApp(false)
        assertNotNull("Dialog never showed",
                UiAutomatorUtils.waitFindObject(By.res(getDialogPositiveButtonId())))
        val view = UiAutomatorUtils.waitFindObjectOrNull(By.text("This Should Be Hidden"), 10_000)
        assertNull("Overlay should not have shown.", view)
    }

    @Test
    @AppModeFull(reason = "Uses secondary app, instant apps have no visibility")
    fun testCantEnablePrivacyIfNotSupported() {
        assumeFalse(spm.supportsSensorToggle(sensor))
        assumeFalse(spm.supportsSensorToggle(TOGGLE_TYPE_SOFTWARE, sensor))
        setSensor(true)
        assertFalse(isSensorPrivacyEnabled())
    }

    private fun assumeSensorToggleSupport() {
        assumeTrue(spm.supportsSensorToggle(sensor))
        assumeTrue(spm.supportsSensorToggle(TOGGLE_TYPE_SOFTWARE, sensor))
    }

    private fun startTestApp() {
        startTestApp(false)
    }

    private fun startTestApp(retryCameraOnError: Boolean) {
        val intent = Intent(MIC_CAM_ACTIVITY_ACTION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_ACTIVITY_MATCH_EXTERNAL)
        for (extra in extras) {
            intent.putExtra(extra, true)
        }
        intent.putExtra(RETRY_CAM_EXTRA, retryCameraOnError)
        context.startActivity(intent)
        // Wait for app to open
        if (!isWear()) {
            UiAutomatorUtils.waitFindObject(By.textContains(ACTIVITY_TITLE_SNIP))
        }
    }

    private fun startTestOverlayApp(retryCameraOnError: Boolean) {
        val intent = Intent(MIC_CAM_OVERLAY_ACTIVITY_ACTION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_ACTIVITY_MATCH_EXTERNAL)
        for (extra in extras) {
            intent.putExtra(extra, true)
        }
        intent.putExtra(RETRY_CAM_EXTRA, retryCameraOnError)
        context.startActivity(intent)
        // Wait for app to open
        if (!isWear()) {
            eventually {
                UiAutomatorUtils.waitFindObject(By.textContains(ACTIVITY_TITLE_SNIP))
            }
        }

        context.sendBroadcast(Intent(SHOW_OVERLAY_ACTION))
    }

    private fun finishTestApp() {
        // instant apps can't broadcast to other instant apps; use the shell
        broadcastAndWait(FINISH_MIC_CAM_ACTIVITY_ACTION)
    }

    private fun broadcastAndWait(action: String) {
        Log.i(TAG, "Broadcasting action '$action'")
        runShellCommandOrThrow("am broadcast" +
                " --user ${context.userId}" +
                " -a $action" +
                " -f ${Intent.FLAG_RECEIVER_VISIBLE_TO_INSTANT_APPS}")
        SystemUtil.waitForBroadcastDispatch(FINISH_MIC_CAM_ACTIVITY_ACTION)
        Log.i(TAG, "Finished broadcasting action '$action'")
    }

    protected fun setSensor(enable: Boolean) {
        runWithShellPermissionIdentity {
            spm.setSensorPrivacy(OTHER, sensor, enable)
        }
    }

    protected fun setSensor(sensor: Int, enable: Boolean) {
        runWithShellPermissionIdentity {
            spm.setSensorPrivacy(sensor, enable)
        }
    }

    private fun isSensorPrivacyEnabled(): Boolean {
        return callWithShellPermissionIdentity {
            spm.isSensorPrivacyEnabled(sensor)
        }
    }

    private fun isToggleSensorPrivacyEnabled(toggleType: Int): Boolean {
        return callWithShellPermissionIdentity {
            spm.isSensorPrivacyEnabled(toggleType, sensor)
        }
    }

    private fun isCombinedSensorPrivacyEnabled(): Boolean {
        return callWithShellPermissionIdentity {
            spm.areAnySensorPrivacyTogglesEnabled(sensor)
        }
    }

    private fun getOpForSensor(sensor: Int): String? {
        return when (sensor) {
            CAMERA -> AppOpsManager.OPSTR_CAMERA
            MICROPHONE -> AppOpsManager.OPSTR_RECORD_AUDIO
            else -> null
        }
    }

    private fun getOpForPackage(): AppOpsManager.PackageOps {
        return callWithShellPermissionIdentity {
            val uid = try {
                packageManager.getPackageUid(PKG_NAME, 0)
            } catch (e: PackageManager.NameNotFoundException) {
                // fail test
                assertNull(e)
                -1
            }
            val appOpsManager: AppOpsManager =
                    context.getSystemService(AppOpsManager::class.java)!!
            val pkgOps = appOpsManager.getOpsForPackage(uid, PKG_NAME, op)
            assertFalse("expected non empty app op list", pkgOps.isEmpty())
            pkgOps[0]
        }
    }

    private fun assertOpRunning(isRunning: Boolean) {
        val pkgOp = getOpForPackage()
        for (op in pkgOp.ops) {
            for ((_, attrOp) in op.attributedOpEntries) {
                assertEquals("Unexpected op running state", isRunning, attrOp.isRunning)
            }
        }
    }

    private fun isWear(): Boolean = packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)

    private fun assertLastAccessTimeAndDuration(before: Long, after: Long) {
        val pkgOp = getOpForPackage()
        for (op in pkgOp.ops) {
            for ((_, attrOp) in op.attributedOpEntries) {
                val lastAccess = attrOp.getLastAccessTime(AppOpsManager.OP_FLAGS_ALL_TRUSTED)
                val lastDuration = attrOp.getLastDuration(AppOpsManager.OP_FLAGS_ALL_TRUSTED)
                assertTrue("lastAccess was $lastAccess, not between $before and $after",
                        lastAccess in before..after)
                assertTrue("lastAccess had duration $lastDuration, greater than ${after - before}",
                lastDuration <= (after - before))
            }
        }
    }

    fun runWhileLocked(r: () -> Unit) {
        val km = context.getSystemService(KeyguardManager::class.java)!!
        val pm = context.getSystemService(PowerManager::class.java)!!
        val pin = "1234".toByteArray(StandardCharsets.UTF_8)
        try {
            runWithShellPermissionIdentity {
                assumeTrue("Could not set lock.",
                        km.setLock(KeyguardManager.PIN, pin, KeyguardManager.PIN, null))
            }
            getEventually {
                uiDevice.pressKeyCode(KeyEvent.KEYCODE_SLEEP)
                assumeFalse("Device never slept.", pm.isInteractive)
            }
            getEventually {
                uiDevice.pressKeyCode(KeyEvent.KEYCODE_WAKEUP)
                assumeTrue("Device never woke up.", pm.isInteractive)
            }
            getEventually {
                assumeTrue("Device isn't locked", km.isDeviceLocked)
            }

            r.invoke()
        } finally {
            runWithShellPermissionIdentity {
                assumeTrue("Could not remove lock.",
                        km.setLock(KeyguardManager.PIN, null, KeyguardManager.PIN, pin))
            }

            // Recycle the screen power in case the keyguard is stuck open
            getEventually {
                uiDevice.pressKeyCode(KeyEvent.KEYCODE_SLEEP)
                assumeFalse("Device never slept.", pm.isInteractive)
            }
            getEventually {
                uiDevice.pressKeyCode(KeyEvent.KEYCODE_WAKEUP)
                assumeTrue("Device never woke up.", pm.isInteractive)
            }

            getEventually {
                assumeFalse("Device isn't unlocked", km.isDeviceLocked)
            }
        }
    }

    /**
     * Perform the requested action, then wait both for the action to complete, and for at least
     * one window transition to occur since the moment the action begins executing.
     */
    private inline fun doAndWaitForWindowTransition(
            crossinline block: () -> Unit
    ) {
        val timeoutOccurred = !uiDevice.performActionAndWait({
            block()
        }, Until.newWindow(), NEW_WINDOW_TIMEOUT_MILLIS)

        if (timeoutOccurred) {
            throw RuntimeException("Timed out waiting for window transition.")
        }
    }

}
