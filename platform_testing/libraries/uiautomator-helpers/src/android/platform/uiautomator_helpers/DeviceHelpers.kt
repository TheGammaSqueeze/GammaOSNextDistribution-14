/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.platform.uiautomator_helpers

import android.os.SystemClock.uptimeMillis
import android.animation.TimeInterpolator
import android.app.Instrumentation
import android.content.Context
import android.graphics.PointF
import android.os.Bundle
import android.platform.uiautomator_helpers.TracingUtils.trace
import android.platform.uiautomator_helpers.WaitUtils.ensureThat
import android.platform.uiautomator_helpers.WaitUtils.waitFor
import android.platform.uiautomator_helpers.WaitUtils.waitForNullable
import android.platform.uiautomator_helpers.WaitUtils.waitForPossibleEmpty
import android.platform.uiautomator_helpers.WaitUtils.waitForValueToSettle
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import java.io.IOException
import java.time.Duration

private const val TAG = "DeviceHelpers"

object DeviceHelpers {
    private val SHORT_WAIT = Duration.ofMillis(1500)
    private val LONG_WAIT = Duration.ofSeconds(10)
    private val DOUBLE_TAP_INTERVAL = Duration.ofMillis(100)

    private val instrumentationRegistry = InstrumentationRegistry.getInstrumentation()

    @JvmStatic
    val uiDevice: UiDevice
        get() = UiDevice.getInstance(instrumentationRegistry)

    @JvmStatic
    val context: Context
        get() = instrumentationRegistry.targetContext

    /**
     * Waits for an object to be visible and returns it.
     *
     * Throws an error with message provided by [errorProvider] if the object is not found.
     */
    @Deprecated(
        "Use [DeviceHelpers.waitForObj] instead.",
        ReplaceWith("DeviceHelpers.waitForObj(selector, timeout, errorProvider)")
    )
    fun UiDevice.waitForObj(
        selector: BySelector,
        timeout: Duration = LONG_WAIT,
        errorProvider: () -> String = { "Object $selector not found" },
    ): UiObject2 = DeviceHelpers.waitForObj(selector, timeout, errorProvider)

    /**
     * Waits for an object to be visible and returns it.
     *
     * Throws an error with message provided by [errorProvider] if the object is not found.
     */
    @JvmOverloads
    @JvmStatic
    fun waitForObj(
        selector: BySelector,
        timeout: Duration = LONG_WAIT,
        errorProvider: () -> String = { "Object $selector not found" },
    ): UiObject2 =
        waitFor("$selector object", timeout, errorProvider) { uiDevice.findObject(selector) }

    /**
     * Waits for an object to be visible and returns it.
     *
     * Throws an error with message provided by [errorProvider] if the object is not found.
     */
    fun UiObject2.waitForObj(
        selector: BySelector,
        timeout: Duration = LONG_WAIT,
        errorProvider: () -> String = { "Object $selector not found" },
    ): UiObject2 = waitFor("$selector object", timeout, errorProvider) { findObject(selector) }

    /**
     * Waits for an object to be visible and returns it. Returns `null` if the object is not found.
     */
    @Deprecated(
        "Use [DeviceHelpers.waitForNullableObj] instead.",
        ReplaceWith("DeviceHelpers.waitForNullableObj(selector, timeout)")
    )
    fun UiDevice.waitForNullableObj(
        selector: BySelector,
        timeout: Duration = SHORT_WAIT,
    ): UiObject2? = DeviceHelpers.waitForNullableObj(selector, timeout)

    /**
     * Waits for an object to be visible and returns it. Returns `null` if the object is not found.
     */
    fun waitForNullableObj(
        selector: BySelector,
        timeout: Duration = SHORT_WAIT,
    ): UiObject2? =
        waitForNullable("nullable $selector objects", timeout) { uiDevice.findObject(selector) }

    /**
     * Waits for an object to be visible and returns it. Returns `null` if the object is not found.
     */
    fun UiObject2.waitForNullableObj(
        selector: BySelector,
        timeout: Duration = SHORT_WAIT,
    ): UiObject2? = waitForNullable("nullable $selector objects", timeout) { findObject(selector) }

    /**
     * Waits for objects matched by [selector] to be visible and returns them. Returns `null` if no
     * objects are found
     */
    @Deprecated(
        "Use DeviceHelpers.waitForPossibleEmpty",
        ReplaceWith(
            "waitForPossibleEmpty(selector, timeout)",
            "android.platform.uiautomator_helpers.DeviceHelpers.waitForPossibleEmpty"
        )
    )
    fun waitForNullableObjects(
        selector: BySelector,
        timeout: Duration = SHORT_WAIT,
    ): List<UiObject2>? = waitForPossibleEmpty(selector, timeout)

    /**
     * Waits for objects matched by selector to be visible. Returns an empty list when none is
     * visible.
     */
    fun waitForPossibleEmpty(
        selector: BySelector,
        timeout: Duration = SHORT_WAIT,
    ): List<UiObject2> =
        waitForPossibleEmpty("$selector objects", timeout) { uiDevice.findObjects(selector) }

    /**
     * Waits for objects matched by [selector] to be visible and returns them. Returns `null` if no
     * objects are found
     */
    @Deprecated(
        "Use DeviceHelpers.waitForNullableObjects",
        ReplaceWith("DeviceHelpers.waitForNullableObjects(selector, timeout)")
    )
    fun UiDevice.waitForNullableObjects(
        selector: BySelector,
        timeout: Duration = SHORT_WAIT,
    ): List<UiObject2>? = DeviceHelpers.waitForNullableObjects(selector, timeout)

    /** Returns [true] when the [selector] is visible. */
    fun hasObject(
        selector: BySelector,
    ): Boolean = trace("Checking if device has $selector") { uiDevice.hasObject(selector) }

    /** Finds an object with this selector and clicks on it. */
    fun BySelector.click() {
        trace("Clicking $this") { waitForObj(this).click() }
    }

    /**
     * Asserts visibility of a [selector], waiting for [timeout] until visibility matches the
     * expected.
     *
     * If [container] is provided, the object is searched only inside of it.
     */
    @JvmOverloads
    @JvmStatic
    @Deprecated(
        "Use DeviceHelpers.assertVisibility directly",
        ReplaceWith("DeviceHelpers.assertVisibility(selector, visible, timeout, errorProvider)")
    )
    fun UiDevice.assertVisibility(
        selector: BySelector,
        visible: Boolean = true,
        timeout: Duration = LONG_WAIT,
        errorProvider: (() -> String)? = null,
    ) {
        DeviceHelpers.assertVisibility(selector, visible, timeout, errorProvider)
    }

    /**
     * Asserts visibility of a [selector], waiting for [timeout] until visibility matches the
     * expected.
     *
     * If [container] is provided, the object is searched only inside of it.
     */
    @JvmOverloads
    @JvmStatic
    fun assertVisibility(
        selector: BySelector,
        visible: Boolean = true,
        timeout: Duration = LONG_WAIT,
        errorProvider: (() -> String)? = null,
    ) {
        ensureThat("$selector is ${visible.asVisibilityBoolean()}", timeout, errorProvider) {
            uiDevice.hasObject(selector) == visible
        }
    }

    private fun Boolean.asVisibilityBoolean(): String =
        when (this) {
            true -> "visible"
            false -> "invisible"
        }

    /**
     * Asserts visibility of a [selector] inside this [UiObject2], waiting for [timeout] until
     * visibility matches the expected.
     */
    fun UiObject2.assertVisibility(
        selector: BySelector,
        visible: Boolean,
        timeout: Duration = LONG_WAIT,
        errorProvider: (() -> String)? = null,
    ) {
        ensureThat(
            "$selector is ${visible.asVisibilityBoolean()} inside $this",
            timeout,
            errorProvider
        ) {
            hasObject(selector) == visible
        }
    }

    /** Asserts that a this selector is visible. Throws otherwise. */
    fun BySelector.assertVisible(
        timeout: Duration = LONG_WAIT,
        errorProvider: (() -> String)? = null
    ) {
        uiDevice.assertVisibility(
            selector = this,
            visible = true,
            timeout = timeout,
            errorProvider = errorProvider
        )
    }
    /** Asserts that a this selector is invisible. Throws otherwise. */
    fun BySelector.assertInvisible(
        timeout: Duration = LONG_WAIT,
        errorProvider: (() -> String)? = null
    ) {
        uiDevice.assertVisibility(
            selector = this,
            visible = false,
            timeout = timeout,
            errorProvider = errorProvider
        )
    }

    /**
     * Executes a shell command on the device.
     *
     * Adds some logging. Throws [RuntimeException] In case of failures.
     */
    @Deprecated("Use [DeviceHelpers.shell] directly", ReplaceWith("DeviceHelpers.shell(command)"))
    @JvmStatic
    fun UiDevice.shell(command: String): String = DeviceHelpers.shell(command)

    /**
     * Executes a shell command on the device, and return its output one it finishes.
     *
     * Adds some logging to [UiDevice.executeShellCommand]. Throws [RuntimeException] In case of
     * failures. Blocks until the command returns.
     *
     * @param command Shell command to execute
     * @return Standard output of the command.
     */
    @JvmStatic
    fun shell(command: String): String {
        trace("Executing shell command: $command") {
            Log.d(TAG, "Executing Shell Command: $command at ${uptimeMillis()}ms")
            return try {
                uiDevice.executeShellCommand(command)
            } catch (e: IOException) {
                Log.e(TAG, "IOException Occurred.", e)
                throw RuntimeException(e)
            }
        }
    }

    /** Perform double tap at specified x and y position */
    @JvmStatic
    fun UiDevice.doubleTapAt(x: Int, y: Int) {
        click(x, y)
        Thread.sleep(DOUBLE_TAP_INTERVAL.toMillis())
        click(x, y)
    }

    /**
     * Aims at replacing [UiDevice.swipe].
     *
     * This should be used instead of [UiDevice.swipe] as it causes less flakiness. See
     * [BetterSwipe].
     */
    @JvmStatic
    @Deprecated(
        "Use DeviceHelpers.betterSwipe directly",
        ReplaceWith("DeviceHelpers.betterSwipe(startX, startY, endX, endY, interpolator)")
    )
    fun UiDevice.betterSwipe(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        interpolator: TimeInterpolator = FLING_GESTURE_INTERPOLATOR
    ) {
        DeviceHelpers.betterSwipe(startX, startY, endX, endY, interpolator)
    }

    /**
     * Aims at replacing [UiDevice.swipe].
     *
     * This should be used instead of [UiDevice.swipe] as it causes less flakiness. See
     * [BetterSwipe].
     */
    @JvmStatic
    fun betterSwipe(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        interpolator: TimeInterpolator = FLING_GESTURE_INTERPOLATOR
    ) {
        trace("Swiping ($startX,$startY) -> ($endX,$endY)") {
            BetterSwipe.from(PointF(startX.toFloat(), startY.toFloat()))
                .to(PointF(endX.toFloat(), endY.toFloat()), interpolator = interpolator)
                .release()
        }
    }

    /** [message] will be visible to the terminal when using `am instrument`. */
    fun printInstrumentationStatus(tag: String, message: String) {
        val result =
            Bundle().apply {
                putString(Instrumentation.REPORT_KEY_STREAMRESULT, "[$tag]: $message")
            }
        instrumentationRegistry.sendStatus(/* resultCode= */ 0, result)
    }

    /**
     * Returns whether the screen is on.
     *
     * As this uses [waitForValueToSettle], it is resilient to fast screen on/off happening.
     */
    @JvmStatic
    val UiDevice.isScreenOnSettled: Boolean
        get() = waitForValueToSettle("Screen on") { isScreenOn }
}
