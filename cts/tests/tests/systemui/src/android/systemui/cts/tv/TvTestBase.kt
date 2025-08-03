/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.systemui.cts.tv

import android.Manifest.permission.FORCE_STOP_PACKAGES
import android.app.ActivityManager
import android.app.IActivityManager
import android.app.IProcessObserver
import android.app.Instrumentation
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PackageManager.FEATURE_LEANBACK
import android.content.pm.PackageManager.FEATURE_LEANBACK_ONLY
import android.os.SystemClock
import android.server.wm.UiDeviceUtils
import android.server.wm.WindowManagerStateHelper
import android.systemui.tv.cts.ResourceNames.SYSTEM_UI_PACKAGE
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.android.compatibility.common.util.SystemUtil
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import java.io.IOException

abstract class TvTestBase {
    companion object {
        private const val TAG = "TvTestBase"
        private const val AFTER_TEST_PROCESS_CHECK_DELAY = 1_000L // 1 sec
    }

    protected val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    protected val uiDevice: UiDevice = UiDevice.getInstance(instrumentation)
    protected val context: Context = instrumentation.context
    protected val packageManager: PackageManager = context.packageManager
            ?: error("Could not get a PackageManager")
    protected val activityManager: ActivityManager =
            context.getSystemService(ActivityManager::class.java)
                    ?: error("Could not get a ActivityManager")
    protected val wmState: WindowManagerStateHelper = WindowManagerStateHelper()
    private val isTelevision: Boolean
        get() = packageManager.run {
            hasSystemFeature(FEATURE_LEANBACK) || hasSystemFeature(FEATURE_LEANBACK_ONLY)
        }
    private var systemUiProcessObserver : SystemUiProcessObserver? = null

    @Before
    fun setUp() {
        assumeTrue(isTelevision)

        systemUiProcessObserver = SystemUiProcessObserver().apply { start() }

        UiDeviceUtils.pressWakeupButton()
        UiDeviceUtils.pressUnlockButton()

        onSetUp()
    }

    @After
    fun tearDown() {
        if (!isTelevision) return

        onTearDown()

        SystemClock.sleep(AFTER_TEST_PROCESS_CHECK_DELAY)
        systemUiProcessObserver?.stop()
        assertFalse(
            "SystemUI has died during test execution", systemUiProcessObserver?.hasDied ?: true)
    }

    abstract fun onSetUp()

    abstract fun onTearDown()

    protected fun launchActivity(
        activity: ComponentName? = null,
        action: String? = null,
        flags: Set<Int> = setOf(),
        boolExtras: Map<String, Boolean> = mapOf(),
        intExtras: Map<String, Int> = mapOf(),
        stringExtras: Map<String, String> = mapOf()
    ) {
        require(activity != null || !action.isNullOrBlank()) {
            "Cannot launch an activity with neither activity name nor action!"
        }
        val command = composeAmShellCommand(
                "start", activity, action, flags, boolExtras, intExtras, stringExtras)
        executeShellCommand(command)
    }

    protected fun startForegroundService(
        service: ComponentName,
        action: String? = null
    ) {
        val command = composeAmShellCommand("start-foreground-service", service, action)
        executeShellCommand(command)
    }

    protected fun sendBroadcast(
        action: String,
        flags: Set<Int> = setOf(),
        boolExtras: Map<String, Boolean> = mapOf(),
        intExtras: Map<String, Int> = mapOf(),
        stringExtras: Map<String, String> = mapOf()
    ) {
        val command = composeAmShellCommand(
                "broadcast", null, action, flags, boolExtras, intExtras, stringExtras)
        executeShellCommand(command)
    }

    protected fun stopPackage(packageName: String) {
        SystemUtil.runWithShellPermissionIdentity({
            activityManager.forceStopPackage(packageName)
        }, FORCE_STOP_PACKAGES)
    }

    private fun composeAmShellCommand(
        command: String,
        component: ComponentName?,
        action: String? = null,
        flags: Set<Int> = setOf(),
        boolExtras: Map<String, Boolean> = mapOf(),
        intExtras: Map<String, Int> = mapOf(),
        stringExtras: Map<String, String> = mapOf()
    ): String = buildString {
        append("am ")
        append(command)
        component?.let {
            append(" -n ")
            append(it.flattenToShortString())
        }
        action?.let {
            append(" -a ")
            append(it)
        }
        flags.forEach {
            append(" -f ")
            append(it)
        }
        boolExtras.forEach {
            append(it.withFlag("ez"))
        }
        intExtras.forEach {
            append(it.withFlag("ei"))
        }
        stringExtras.forEach {
            append(it.withFlag("es"))
        }
    }

    private fun Map.Entry<String, *>.withFlag(flag: String): String = " --$flag $key $value"

    protected fun executeShellCommand(cmd: String): String {
        try {
            return SystemUtil.runShellCommand(instrumentation, cmd)
        } catch (e: IOException) {
            Log.e(TAG, "Error running shell command: $cmd")
            throw e
        }
    }

    inner class SystemUiProcessObserver : IProcessObserver.Stub() {
        private val activityManager: IActivityManager = ActivityManager.getService()
        private val uiAutomation = instrumentation.uiAutomation
        private val systemUiUid = packageManager.getPackageUid(SYSTEM_UI_PACKAGE, 0)
        var hasDied: Boolean = false

        fun start() {
            hasDied = false
            uiAutomation.adoptShellPermissionIdentity(
                    android.Manifest.permission.SET_ACTIVITY_WATCHER)
            activityManager.registerProcessObserver(this)
        }

        fun stop() {
            activityManager.unregisterProcessObserver(this)
            uiAutomation.dropShellPermissionIdentity()
        }

        override fun onProcessStarted(
            pid: Int,
            processUid: Int,
            packageUid: Int,
            packageName: String,
            processName: String
        ) {}

        override fun onForegroundActivitiesChanged(pid: Int, uid: Int, foreground: Boolean) {}

        override fun onForegroundServicesChanged(pid: Int, uid: Int, serviceTypes: Int) {}

        override fun onProcessDied(pid: Int, uid: Int) {
            if (uid == systemUiUid) hasDied = true
        }
    }
}
