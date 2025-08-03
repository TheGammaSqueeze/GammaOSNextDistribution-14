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
package android.platform.test.rule

import android.app.ActivityManager
import android.graphics.Rect
import android.platform.test.rule.Orientation.LANDSCAPE
import android.platform.test.rule.Orientation.NATURAL
import android.platform.test.rule.Orientation.PORTRAIT
import android.platform.test.rule.RotationUtils.clearOrientationOverride
import android.platform.test.rule.RotationUtils.setOrientationOverride
import android.platform.test.util.HealthTestingUtils.waitForValueToSettle
import android.platform.uiautomator_helpers.WaitUtils.ensureThat
import android.util.Log
import androidx.test.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.android.launcher3.tapl.LauncherInstrumentation
import java.time.Duration
import org.junit.runner.Description

/** Locks the orientation in Landscape before starting the test, and goes back to natural after. */
internal class LandscapeOrientationRule : BaseOrientationRule(LANDSCAPE)

/** Locks the orientation in Portrait before starting the test, and goes back to natural after. */
internal class PortraitOrientationRule : BaseOrientationRule(PORTRAIT)

class NaturalOrientationRule : BaseOrientationRule(NATURAL)

/**
 * Possible device orientations.
 *
 * See [UiDevice.orientation] for their definitions.
 */
enum class Orientation {
    LANDSCAPE,
    PORTRAIT,
    NATURAL
}

/** Returns whether the device is landscape or portrait , based on display dimensions. */
val UiDevice.orientation: Orientation
    get() =
        if (displayWidth > displayHeight) {
            LANDSCAPE
        } else {
            PORTRAIT
        }

val UiDevice.naturalOrientation: Orientation
    get() {
        if (isNaturalOrientation) {
            return stableOrientation
        }
        return when (stableOrientation) {
            LANDSCAPE -> PORTRAIT
            PORTRAIT -> LANDSCAPE
            else -> throw RuntimeException("Unexpected orientation: $stableOrientation.")
        }
    }

// This makes sure that the orientation stabilised before returning it.
private val UiDevice.stableOrientation: Orientation
    get() =
        waitForValueToSettle(
            /* errorMessage= */ { "Device orientation didn't settle" },
            /* supplier */ { orientation },
            /* minimumSettleTime= */ 1_000,
            /* timeoutMs= */ 5_000
        )

/** Uses launcher rect to decide which rotation to apply to match [expectedOrientation]. */
sealed class BaseOrientationRule constructor(private val expectedOrientation: Orientation) :
    TestWatcher() {

    override fun starting(description: Description) {
        setOrientationOverride(expectedOrientation)
    }

    override fun finished(description: Description) {
        clearOrientationOverride()
    }
}

/** Provides a way to set and clear a rotation override. */
object RotationUtils {
    private val device: UiDevice =
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    private val launcher: LauncherInstrumentation by lazy { LauncherInstrumentation() }

    /**
     * Sets device orientation to [expectedOrientation], according to [Rect.orientation] definition.
     *
     * Important: call [clearOrientationOverride] after the end of the test. If a single orientation
     * is needed for the entire test, please use the TestRule [OrientationRule] that automatically
     * takes care of cleaning the override. Those should be called only when the test needs to
     * change orientation in the middle.
     */
    fun setOrientationOverride(
        orientation: Orientation,
        timeoutDuration: Duration = Duration.ofSeconds(10)
    ) {
        val expectedOrientation =
            if (orientation == NATURAL) device.naturalOrientation else orientation
        setEnableLauncherRotation(true)
        if (device.stableOrientation == expectedOrientation) {
            return
        }

        // Change orientation might be called soon before the device is unfolded, and lose its
        // effect after unfold
        ensureThat(
            "orientation is $expectedOrientation",
            timeout = timeoutDuration,
            ignoreException = true
        ) {
            changeOrientation()
            device.stableOrientation == expectedOrientation
        }

        log("Rotation override set to ${expectedOrientation.name}")
    }

    private fun changeOrientation() {
        if (device.isNaturalOrientation) {
            device.setOrientationLeft()
        } else {
            device.setOrientationNatural()
        }
    }

    /** returns stable orientation, doesn't necessarily mean orientation needs to happen */
    fun waitForOrientationToSettle(): Orientation {
        return device.stableOrientation
    }

    fun clearOrientationOverride() {
        device.setOrientationNatural()
        setEnableLauncherRotation(false)
        device.unfreezeRotation()
        waitForOrientationToSettle()
        log("Rotation override cleared.")
    }

    private fun setEnableLauncherRotation(enable: Boolean) {
        // Launcher is only allowed to enable rotation when the device in test harness mode.
        if (ActivityManager.isRunningInTestHarness()) {
            launcher.setEnableRotation(enable)
        }
    }

    private fun log(message: String) = Log.d("RotationUtils", message)
}
