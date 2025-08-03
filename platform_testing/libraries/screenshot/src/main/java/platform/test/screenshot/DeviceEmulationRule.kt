/*
 * Copyright 2022 The Android Open Source Project
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

package platform.test.screenshot

import android.app.UiAutomation
import android.app.UiModeManager
import android.content.Context
import android.os.Build
import android.os.UserHandle
import android.view.Display
import android.view.WindowManagerGlobal
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Rule to be added to a screenshot test to simulate a device with the given [spec].
 *
 * This rule takes care of setting up the environment by:
 * - emulating a display size and density, taking the device orientation into account.
 * - setting the test app in dark/light mode.
 *
 * Important: This rule should usually be the first rule in your test, so that all the display and
 * app reconfiguration happens *before* your test starts doing any work, like launching an Activity.
 *
 * @see DeviceEmulationSpec
 */
class DeviceEmulationRule(private val spec: DeviceEmulationSpec) : TestRule {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val uiAutomation = instrumentation.uiAutomation
    private val isRoblectric = Build.FINGERPRINT.contains("robolectric")

    companion object {
        var prevDensity: Int? = -1
        var prevWidth: Int? = -1
        var prevHeight: Int? = -1
        var prevNightMode: Int? = UiModeManager.MODE_NIGHT_AUTO
        var initialized: Boolean = false
    }

    override fun apply(base: Statement, description: Description): Statement {
        // The statement which calls beforeTest() before running the test.
        return object : Statement() {
            override fun evaluate() {
                beforeTest()
                base.evaluate()
            }
        }
    }

    private fun beforeTest() {
        // Emulate the display size and density.
        val display = spec.display
        val density = display.densityDpi
        val (width, height) = getEmulatedDisplaySize()

        if (isRoblectric) {
            // For Robolectric tests use RuntimeEnvironment.setQualifiers until wm is shadowed
            // b/275751037  to address this issue.
            val runtimeEnvironment = Class.forName("org.robolectric.RuntimeEnvironment")
            val setQualifiers =
                runtimeEnvironment.getDeclaredMethod("setQualifiers", String::class.java)
            val scaledWidth = width * 160 / density
            val scaledHeight = height * 160 / density
            val qualifier = "w${scaledWidth}dp-h${scaledHeight}dp-${density}dpi"
            setQualifiers.invoke(null, qualifier)
        } else {
            val curNightMode =
                if (spec.isDarkTheme) {
                    UiModeManager.MODE_NIGHT_YES
                } else {
                    UiModeManager.MODE_NIGHT_NO
                }

            if (initialized) {
                if (prevDensity != density) {
                    setDisplayDensity(density)
                }
                if (prevWidth != width || prevHeight != height) {
                    setDisplaySize(width, height)
                }
                if (prevNightMode != curNightMode) {
                    setNightMode(curNightMode)
                }
            } else {
                // Make sure that we are in natural orientation (rotation 0) before we set the
                // screen size.
                uiAutomation.setRotation(UiAutomation.ROTATION_FREEZE_0)

                setDisplayDensity(density)
                setDisplaySize(width, height)

                // Force the dark/light theme.
                setNightMode(curNightMode)

                // Make sure that all devices are in touch mode to avoid screenshot differences
                // in focused elements when in keyboard mode.
                instrumentation.setInTouchMode(true)

                // Set the initialization fact.
                initialized = true
            }
        }
    }

    /** Get the emulated display size for [spec]. */
    private fun getEmulatedDisplaySize(): Pair<Int, Int> {
        val display = spec.display
        val isPortraitNaturalPosition = display.width < display.height
        return if (spec.isLandscape == isPortraitNaturalPosition) {
            display.height to display.width
        } else {
            display.width to display.height
        }
    }

    private fun setDisplayDensity(density: Int) {
        val wm =
            WindowManagerGlobal.getWindowManagerService()
                ?: error("Unable to acquire WindowManager")
        wm.setForcedDisplayDensityForUser(Display.DEFAULT_DISPLAY, density, UserHandle.myUserId())
        prevDensity = density
    }

    private fun setDisplaySize(width: Int, height: Int) {
        val wm =
            WindowManagerGlobal.getWindowManagerService()
                ?: error("Unable to acquire WindowManager")
        wm.setForcedDisplaySize(Display.DEFAULT_DISPLAY, width, height)
        prevWidth = width
        prevHeight = height
    }

    private fun setNightMode(nightMode: Int) {
        val uiModeManager =
            InstrumentationRegistry.getInstrumentation()
                .targetContext
                .getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        uiModeManager.setApplicationNightMode(nightMode)
        prevNightMode = nightMode
    }
}

/** The specification of a device display to be used in a screenshot test. */
data class DisplaySpec(
    val name: String,
    val width: Int,
    val height: Int,
    val densityDpi: Int,
)

/** The specification of a device emulation. */
data class DeviceEmulationSpec(
    val display: DisplaySpec,
    val isDarkTheme: Boolean = false,
    val isLandscape: Boolean = false,
) {
    companion object {
        /**
         * Return a list of [DeviceEmulationSpec] for each of the [displays].
         *
         * If [isDarkTheme] is null, this will create a spec for both light and dark themes, for
         * each of the orientation.
         *
         * If [isLandscape] is null, this will create a spec for both portrait and landscape, for
         * each of the light/dark themes.
         */
        fun forDisplays(
            vararg displays: DisplaySpec,
            isDarkTheme: Boolean? = null,
            isLandscape: Boolean? = null,
        ): List<DeviceEmulationSpec> {
            return displays.flatMap { display ->
                buildList {
                    fun addDisplay(isLandscape: Boolean) {
                        if (isDarkTheme != true) {
                            add(DeviceEmulationSpec(display, isDarkTheme = false, isLandscape))
                        }

                        if (isDarkTheme != false) {
                            add(DeviceEmulationSpec(display, isDarkTheme = true, isLandscape))
                        }
                    }

                    if (isLandscape != true) {
                        addDisplay(isLandscape = false)
                    }

                    if (isLandscape != false) {
                        addDisplay(isLandscape = true)
                    }
                }
            }
        }
    }

    override fun toString(): String = buildString {
        // This string is appended to PNGs stored in the device, so let's keep it simple.
        append(display.name)
        if (isDarkTheme) append("_dark")
        if (isLandscape) append("_landscape")
    }
}
