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

package android.tools.helpers

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Point
import android.graphics.Rect
import android.os.RemoteException
import android.os.SystemClock
import android.tools.Logger
import android.tools.Rotation
import android.tools.helpers.WindowUtils.displayBounds
import android.tools.helpers.WindowUtils.estimateNavigationBarPosition
import android.tools.traces.ConditionsFactory
import android.tools.traces.component.ComponentNameMatcher
import android.tools.traces.executeShellCommand
import android.tools.traces.parsers.WindowManagerStateHelper
import android.tools.traces.parsers.toAndroidRect
import android.util.Rational
import android.view.View
import android.view.ViewConfiguration
import androidx.annotation.VisibleForTesting
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert
import org.junit.Assert.assertNotNull

const val FIND_TIMEOUT: Long = 10000
const val FAST_WAIT_TIMEOUT: Long = 0
val DOCKED_STACK_DIVIDER = ComponentNameMatcher("", "DockedStackDivider")
const val IME_PACKAGE = "com.google.android.inputmethod.latin"

@VisibleForTesting const val SYSTEMUI_PACKAGE = "com.android.systemui"
private val LONG_PRESS_TIMEOUT = ViewConfiguration.getLongPressTimeout() * 2L
private const val TAG = "FLICKER"

/**
 * Sets [android.app.UiAutomation.waitForIdle] global timeout to 0 causing the
 * [android.app.UiAutomation.waitForIdle] function to timeout instantly. This removes some delays
 * when using the UIAutomator library required to create fast UI transitions.
 */
fun setFastWait() {
    Configurator.getInstance().waitForIdleTimeout = FAST_WAIT_TIMEOUT
}

/** Reverts [android.app.UiAutomation.waitForIdle] to default behavior. */
fun setDefaultWait() {
    Configurator.getInstance().waitForIdleTimeout = FIND_TIMEOUT
}

/** Checks if the device is running on gestural or 2-button navigation modes */
fun UiDevice.isQuickstepEnabled(): Boolean {
    val enabled = this.findObject(By.res(SYSTEMUI_PACKAGE, "recent_apps")) == null
    Logger.d(TAG, "Quickstep enabled: $enabled")
    return enabled
}

/** Checks if the display is rotated or not */
fun UiDevice.isRotated(): Boolean {
    return Rotation.getByValue(this.displayRotation).isRotated()
}

/**
 * Shows quickstep
 *
 * @throws AssertionError When quickstep does not appear
 */
fun UiDevice.openQuickstep(wmHelper: WindowManagerStateHelper) {
    if (this.isQuickstepEnabled()) {
        val navBar = this.findObject(By.res(SYSTEMUI_PACKAGE, "navigation_bar_frame"))

        // TODO(vishnun) investigate why this object cannot be found.
        val navBarVisibleBounds: Rect =
            if (navBar != null) {
                navBar.visibleBounds
            } else {
                Logger.e(TAG, "Could not find nav bar, infer location")
                estimateNavigationBarPosition(Rotation.ROTATION_0).bounds.toAndroidRect()
            }

        val startX = navBarVisibleBounds.centerX()
        val startY = navBarVisibleBounds.centerY()
        val endX: Int
        val endY: Int
        val height: Int
        val steps: Int
        if (this.isRotated()) {
            height = this.displayWidth
            endX = height * 2 / 3
            endY = navBarVisibleBounds.centerY()
            steps = (endX - startX) / 100 // 100 px/step
        } else {
            height = this.displayHeight
            endX = navBarVisibleBounds.centerX()
            endY = height * 2 / 3
            steps = (startY - endY) / 100 // 100 px/step
        }
        // Swipe from nav bar to 2/3rd down the screen.
        this.swipe(startX, startY, endX, endY, steps)
    }

    // use a long timeout to wait until recents populated
    val recentsSysUISelector = By.res(this.launcherPackageName, "overview_panel")
    var recents = this.wait(Until.findObject(recentsSysUISelector), FIND_TIMEOUT)

    // Quickstep detection is flaky on AOSP, UIDevice doesn't always find SysUI elements
    // If it couldn't find, try pressing 'recent items' button
    if (recents == null) {
        try {
            this.pressRecentApps()
        } catch (e: RemoteException) {
            throw RuntimeException(e)
        }
        recents = this.wait(Until.findObject(recentsSysUISelector), FIND_TIMEOUT)
    }
    assertNotNull("Recent items didn't appear", recents)
    wmHelper
        .StateSyncBuilder()
        .withNavOrTaskBarVisible()
        .withStatusBarVisible()
        .withAppTransitionIdle()
        .waitForAndVerify()
}

private fun getLauncherOverviewSelector(device: UiDevice): BySelector {
    return By.res(device.launcherPackageName, "overview_panel")
}

private fun longPressRecents(device: UiDevice) {
    val recentsSelector = By.res(SYSTEMUI_PACKAGE, "recent_apps")
    val recentsButton = device.wait(Until.findObject(recentsSelector), FIND_TIMEOUT)
    assertNotNull("Unable to find 'recent items' button", recentsButton)
    recentsButton.click(LONG_PRESS_TIMEOUT)
}

/** Wait for any IME view to appear */
fun UiDevice.waitForIME(): Boolean {
    val ime = this.wait(Until.findObject(By.pkg(IME_PACKAGE)), FIND_TIMEOUT)
    return ime != null
}

private fun openQuickStepAndLongPressOverviewIcon(
    device: UiDevice,
    wmHelper: WindowManagerStateHelper
) {
    if (device.isQuickstepEnabled()) {
        device.openQuickstep(wmHelper)
    } else {
        try {
            device.pressRecentApps()
        } catch (e: RemoteException) {
            Logger.e(TAG, "launchSplitScreen", e)
        }
    }
    val overviewIconSelector = By.res(device.launcherPackageName, "icon").clazz(View::class.java)
    val overviewIcon = device.wait(Until.findObject(overviewIconSelector), FIND_TIMEOUT)
    assertNotNull("Unable to find app icon in Overview", overviewIcon)
    overviewIcon.click()
}

fun UiDevice.openQuickStepAndClearRecentAppsFromOverview(wmHelper: WindowManagerStateHelper) {
    if (this.isQuickstepEnabled()) {
        this.openQuickstep(wmHelper)
    } else {
        try {
            this.pressRecentApps()
        } catch (e: RemoteException) {
            Logger.e(TAG, "launchSplitScreen", e)
        }
    }
    for (i in 0..9) {
        this.swipe(
            this.displayWidth / 2,
            this.displayHeight / 2,
            this.displayWidth,
            this.displayHeight / 2,
            5
        )
        // If "Clear all"  button appears, use it
        val clearAllSelector = By.res(this.launcherPackageName, "clear_all")
        wait(Until.findObject(clearAllSelector), FAST_WAIT_TIMEOUT)?.click()
    }
    this.pressHome()
}

/**
 * Opens quick step and puts the first app from the list of recently used apps into split-screen
 *
 * @throws AssertionError when unable to open the list of recently used apps, or when it does not
 *   contain a button to enter split screen mode
 */
fun UiDevice.launchSplitScreen(wmHelper: WindowManagerStateHelper) {
    openQuickStepAndLongPressOverviewIcon(this, wmHelper)
    val splitScreenButtonSelector = By.text("Split screen")
    val splitScreenButton = this.wait(Until.findObject(splitScreenButtonSelector), FIND_TIMEOUT)
    assertNotNull("Unable to find Split screen button in Overview", splitScreenButton)
    splitScreenButton.click()

    // Wait for animation to complete.
    this.wait(Until.findObject(splitScreenDividerSelector), FIND_TIMEOUT)
    wmHelper
        .StateSyncBuilder()
        .add(ConditionsFactory.isLayerVisible(DOCKED_STACK_DIVIDER))
        .withAppTransitionIdle()
        .waitForAndVerify()

    if (!this.isInSplitScreen()) {
        Assert.fail("Unable to find Split screen divider")
    }
}

/** Checks if the recent application is able to split screen(resizeable) */
fun UiDevice.canSplitScreen(wmHelper: WindowManagerStateHelper): Boolean {
    openQuickStepAndLongPressOverviewIcon(this, wmHelper)
    val splitScreenButtonSelector = By.text("Split screen")
    val canSplitScreen =
        this.wait(Until.findObject(splitScreenButtonSelector), FIND_TIMEOUT) != null
    this.pressHome()
    return canSplitScreen
}

/** Checks if the device is in split screen by searching for the split screen divider */
fun UiDevice.isInSplitScreen(): Boolean {
    return this.wait(Until.findObject(splitScreenDividerSelector), FIND_TIMEOUT) != null
}

fun waitSplitScreenGone(wmHelper: WindowManagerStateHelper) {
    return wmHelper
        .StateSyncBuilder()
        .add(ConditionsFactory.isLayerVisible(DOCKED_STACK_DIVIDER).negate())
        .withAppTransitionIdle()
        .waitForAndVerify()
}

private val splitScreenDividerSelector: BySelector
    get() = By.res(SYSTEMUI_PACKAGE, "docked_divider_handle")

/**
 * Drags the split screen divider to the top of the screen to close it
 *
 * @throws AssertionError when unable to find the split screen divider
 */
fun UiDevice.exitSplitScreen() {
    // Quickstep enabled
    val divider = this.wait(Until.findObject(splitScreenDividerSelector), FIND_TIMEOUT)
    assertNotNull("Unable to find Split screen divider", divider)

    // Drag the split screen divider to the top of the screen
    val dstPoint =
        if (this.isRotated()) {
            Point(0, this.displayWidth / 2)
        } else {
            Point(this.displayWidth / 2, 0)
        }
    divider.drag(dstPoint, 400)
    // Wait for animation to complete.
    SystemClock.sleep(2000)
}

/**
 * Drags the split screen divider to the bottom of the screen to close it
 *
 * @throws AssertionError when unable to find the split screen divider
 */
fun UiDevice.exitSplitScreenFromBottom(wmHelper: WindowManagerStateHelper) {
    // Quickstep enabled
    val divider = this.wait(Until.findObject(splitScreenDividerSelector), FIND_TIMEOUT)
    assertNotNull("Unable to find Split screen divider", divider)

    // Drag the split screen divider to the bottom of the screen
    val dstPoint =
        if (this.isRotated()) {
            Point(this.displayWidth, this.displayWidth / 2)
        } else {
            Point(this.displayWidth / 2, this.displayHeight)
        }
    divider.drag(dstPoint, 400)
    waitSplitScreenGone(wmHelper)
}

/**
 * Drags the split screen divider to resize the windows in split screen
 *
 * @throws AssertionError when unable to find the split screen divider
 */
fun UiDevice.resizeSplitScreen(windowHeightRatio: Rational) {
    val dividerSelector = splitScreenDividerSelector
    val divider = this.wait(Until.findObject(dividerSelector), FIND_TIMEOUT)
    assertNotNull("Unable to find Split screen divider", divider)
    val destHeight = (displayBounds.height() * windowHeightRatio.toFloat()).toInt()

    // Drag the split screen divider to so that the ratio of top window height and bottom
    // window height is windowHeightRatio
    this.drag(
        divider.visibleBounds.centerX(),
        divider.visibleBounds.centerY(),
        this.displayWidth / 2,
        destHeight,
        10
    )
    this.wait(Until.findObject(dividerSelector), FIND_TIMEOUT)
    // Wait for animation to complete.
    SystemClock.sleep(2000)
}

/** Checks if the device has a window with the package name */
fun UiDevice.hasWindow(packageName: String): Boolean {
    return this.wait(Until.findObject(By.pkg(packageName)), FIND_TIMEOUT) != null
}

/** Waits until the package with that name is gone */
fun UiDevice.waitUntilGone(packageName: String): Boolean {
    return this.wait(Until.gone(By.pkg(packageName)), FIND_TIMEOUT) != null
}

fun stopPackage(context: Context, packageName: String) {
    executeShellCommand("am force-stop $packageName")
    val packageUid =
        try {
            context.packageManager.getPackageUid(packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            return
        }
    while (targetPackageIsRunning(packageUid)) {
        try {
            Thread.sleep(100)
        } catch (e: InterruptedException) { // ignore
        }
    }
}

private fun targetPackageIsRunning(uid: Int): Boolean {
    val result = String(executeShellCommand("cmd activity get-uid-state $uid"))
    return !result.contains("(NONEXISTENT)")
}

/** Turns on the device display and presses the home button to reach the launcher screen */
fun UiDevice.wakeUpAndGoToHomeScreen() {
    try {
        this.wakeUp()
    } catch (e: RemoteException) {
        throw RuntimeException(e)
    }
    this.pressHome()
}
