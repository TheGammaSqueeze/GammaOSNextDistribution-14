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

import android.Manifest.permission.READ_DREAM_STATE
import android.Manifest.permission.WRITE_DREAM_STATE
import android.app.WindowConfiguration
import android.content.pm.PackageManager
import android.os.ServiceManager
import android.platform.test.annotations.AppModeFull
import android.platform.test.annotations.Postsubmit
import android.server.wm.Condition
import android.server.wm.WindowManagerState
import android.server.wm.annotation.Group2
import android.service.dreams.DreamService
import android.service.dreams.IDreamManager
import android.systemui.tv.cts.Components.PIP_ACTIVITY
import android.systemui.tv.cts.Components.windowName
import android.systemui.tv.cts.PipActivity
import android.systemui.tv.cts.PipActivity.ACTION_ENTER_PIP
import androidx.annotation.CallSuper
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.compatibility.common.util.SystemUtil
import com.android.compatibility.common.util.ThrowingSupplier
import org.junit.Assume
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue

/**
 * Tests most basic picture in picture (PiP) behavior.
 *
 * Build/Install/Run:
 * atest CtsSystemUiTestCases:BasicPipTests
 */
@AppModeFull(reason = "Checks window manager state")
@Postsubmit
@Group2
@RunWith(AndroidJUnit4::class)
class BasicPipTests : TvTestBase() {
    private val isPipSupported: Boolean
        get() = packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    @CallSuper
    override fun onSetUp() {
        Assume.assumeTrue(isPipSupported)
    }

    override fun onTearDown() {
        stopPackage(PIP_ACTIVITY.packageName)
    }

    /** Open an app in pip mode and ensure it has a window but is not focused. */
    @Test
    fun openPip_launchedNotFocused() {
        launchActivity(PIP_ACTIVITY, ACTION_ENTER_PIP)
        waitForEnterPip()

        assertLaunchedNotFocused()
    }

    /** Ensure an app can be launched into pip mode from the screensaver state. */
    @Test
    @Ignore("b/163116693")
    fun openPip_afterScreenSaver() {
        runWithDreamManager { dreamManager ->
            dreamManager.dream()
            dreamManager.waitForDream()
        }

        // Launch pip activity that is supposed to wake up the device
        launchActivity(
            activity = PIP_ACTIVITY,
            action = ACTION_ENTER_PIP,
            boolExtras = mapOf(PipActivity.EXTRA_TURN_ON_SCREEN to true)
        )
        waitForEnterPip()

        assertLaunchedNotFocused()
        assertTrue("Device must be awake") {
            runWithDreamManager { dreamManager ->
                !dreamManager.isDreaming
            }
        }
    }

    /** Ensure an app in pip mode remains open throughout the device dreaming and waking. */
    @Test
    fun pipApp_remainsOpen_afterScreensaver() {
        launchActivity(PIP_ACTIVITY, ACTION_ENTER_PIP)
        waitForEnterPip()

        runWithDreamManager { dreamManager ->
            dreamManager.dream()
            dreamManager.waitForDream()
            dreamManager.awaken()
            dreamManager.waitForAwake()
        }

        assertLaunchedNotFocused()
    }

    private fun assertLaunchedNotFocused() {
        wmState.assertActivityDisplayed(PIP_ACTIVITY)
        wmState.assertNotFocusedWindow("PiP Window must not be focused!",
            PIP_ACTIVITY.windowName())
    }

    /** Run the given actions on a dream manager, acquiring appropriate permissions.  */
    private fun <T> runWithDreamManager(actions: (IDreamManager) -> T): T {
        val dreamManager: IDreamManager = IDreamManager.Stub.asInterface(
            ServiceManager.getServiceOrThrow(DreamService.DREAM_SERVICE))

        return SystemUtil.runWithShellPermissionIdentity(ThrowingSupplier {
            actions(dreamManager)
        }, READ_DREAM_STATE, WRITE_DREAM_STATE)
    }

    /** Wait for the device to enter dream state. Throw on timeout. */
    private fun IDreamManager.waitForDream() {
        val message = "Device must be dreaming!"
        Condition.waitFor(message) {
            isDreaming
        } || error(message)
    }

    /** Wait for the device to awaken. Throw on timeout. */
    private fun IDreamManager.waitForAwake() {
        val message = "Device must be awake!"
        Condition.waitFor(message) {
            !isDreaming
        } || error(message)
    }

    /** Waits until the pip animation has finished and the app is fully in pip mode. */
    private fun waitForEnterPip() {
        wmState.waitForWithAmState("checking task windowing mode") { state: WindowManagerState ->
            state.getTaskByActivity(PIP_ACTIVITY)?.let { task ->
                task.windowingMode == WindowConfiguration.WINDOWING_MODE_PINNED
            } ?: false
        } || error("Task ${PIP_ACTIVITY.flattenToShortString()} is not found or not pinned!")

        wmState.waitForWithAmState("checking activity windowing mode") {
            state: WindowManagerState ->
            state.getTaskByActivity(PIP_ACTIVITY)?.getActivity(PIP_ACTIVITY)?.let { activity ->
                activity.windowingMode == WindowConfiguration.WINDOWING_MODE_PINNED &&
                    activity.state == WindowManagerState.STATE_PAUSED
            } ?: false
        } || error("Activity ${PIP_ACTIVITY.flattenToShortString()} is not found," +
            " not pinned or not paused!")
    }
}
