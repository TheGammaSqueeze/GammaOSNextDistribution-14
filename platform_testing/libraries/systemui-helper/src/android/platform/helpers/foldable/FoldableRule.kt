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

package android.platform.helpers.foldable

import android.os.SystemClock
import android.platform.helpers.foldable.FoldableState.FOLDED
import android.platform.helpers.foldable.FoldableState.HALF_FOLDED
import android.platform.helpers.foldable.FoldableState.REAR_DISPLAY
import android.platform.helpers.foldable.FoldableState.UNFOLDED
import android.platform.test.rule.TestWatcher
import android.platform.uiautomator_helpers.TracingUtils.trace
import android.platform.uiautomator_helpers.WaitUtils.ensureThat
import androidx.annotation.FloatRange
import java.util.concurrent.TimeUnit
import org.junit.rules.TestRule
import org.junit.runner.Description

/**
 * Provides an interface to use foldable specific features.
 *
 * Should be used as [org.junit.ClassRule]. To start a test folded or unfolded, use
 * [foldBeforeTestRule], [unfoldBeforeTestRule], [halfFoldBeforeTestRule], and
 * [rearDisplayBeforeTestRule].
 *
 * Example:
 * ```
 *  companion object {
 *      @get:ClassRule val foldable = FoldableRule()
 *  }
 *  @get:Rule val foldRule = foldable.foldBeforeTestRule
 * ```
 */
class FoldableRule(private val ensureScreenOn: Boolean = false) : TestWatcher() {

    private val controller = FoldableDeviceController()
    private var initialized = false

    override fun starting(description: Description?) {
        controller.init()
        initialized = true
    }

    override fun finished(description: Description?) {
        if (initialized) {
            controller.uninit()
            initialized = false
        }
    }

    @JvmOverloads
    fun fold(turnOffDisplayAfterFold: Boolean = true) {
        trace("FoldableRule#fold") {
            check(!controller.isFolded) { "Trying to fold when already folded" }
            if (ensureScreenOn) {
                ensureThat("screen is on before folding") { screenOn }
            }
            val initialScreenSurface = displaySurface
            val initialState = currentState

            controller.fold()

            if (turnOffDisplayAfterFold) {
                uiDevice.sleep()
                SystemClock.sleep(ANIMATION_TIMEOUT) // Wait for fold + screen off animations
                ensureThat("screen is off after folding") { !screenOn }
            } else {
                SystemClock.sleep(ANIMATION_TIMEOUT) // Wait for fold animations
            }

            if (initialState == UNFOLDED || initialState == HALF_FOLDED) {
                ensureThat("screen surface decreases after folding") {
                    displaySurface < initialScreenSurface
                }
            } else {
                ensureThat("screen surface remains after folding") {
                    displaySurface == initialScreenSurface
                }
            }
        }
    }

    fun halfFold() {
        trace("FoldableRule#halfFold") {
            check(!controller.isHalfFolded) { "Trying to half-fold when already half-folded" }
            if (ensureScreenOn) {
                ensureThat("screen is on before half-folding") { screenOn }
            }
            val initialScreenSurface = displaySurface
            val initialState = currentState

            controller.halfFold()
            SystemClock.sleep(ANIMATION_TIMEOUT)

            if (initialState == FOLDED || initialState == REAR_DISPLAY) {
                ensureThat("screen surface increases after half-folding") {
                    displaySurface > initialScreenSurface
                }
            } else {
                ensureThat("screen surface remains after half-folding") {
                    displaySurface == initialScreenSurface
                }
            }
        }
    }

    fun unfold() {
        trace("FoldableRule#unfold") {
            check(!controller.isUnfolded) { "Trying to unfold when already unfolded" }
            if (ensureScreenOn) {
                ensureThat("screen is on before unfolding") { screenOn }
            }
            val initialScreenSurface = displaySurface
            val initialState = currentState

            controller.unfold()
            SystemClock.sleep(ANIMATION_TIMEOUT) // Let's wait for the unfold animation to finish.

            ensureThat("screen is on after unfolding") { screenOn }
            if (initialState == FOLDED || initialState == REAR_DISPLAY) {
                ensureThat("screen surface increases after unfolding") {
                    displaySurface > initialScreenSurface
                }
            } else {
                ensureThat("screen surface remains after unfolding") {
                    displaySurface == initialScreenSurface
                }
            }
        }
    }

    fun rearDisplay() {
        trace("FoldableRule#rearDisplay") {
            check(!controller.isOnRearDisplay) { "Trying to go to rear display when already there" }
            val initialScreenSurface = displaySurface
            val initialState = currentState

            controller.rearDisplay()
            SystemClock.sleep(ANIMATION_TIMEOUT)

            if (initialState == HALF_FOLDED || initialState == UNFOLDED) {
                ensureThat("screen surface decreases after going to rear display") {
                    displaySurface < initialScreenSurface
                }
            } else {
                ensureThat("screen surface remains after going to rear display") {
                    displaySurface == initialScreenSurface
                }
            }
        }
    }

    fun setHingeAngle(@FloatRange(from = 0.0, to = 180.0) angle: Float) {
        trace("Setting hinge angle to $angle") { controller.setHingeAngle(angle) }
    }

    val foldBeforeTestRule: TestRule = FoldControlRule(FOLDED)
    val unfoldBeforeTestRule: TestRule = FoldControlRule(UNFOLDED)
    val halfFoldBeforeTestRule: TestRule = FoldControlRule(HALF_FOLDED)
    val rearDisplayBeforeTestRule: TestRule = FoldControlRule(REAR_DISPLAY)

    private inner class FoldControlRule(private val startState: FoldableState) : TestWatcher() {
        override fun starting(description: Description?) {
            trace("FoldControlRule#starting") {
                check(initialized) { "Initialize of FoldableRule needed before this." }
                if (currentState == startState) {
                    return
                }
                when (startState) {
                    FOLDED -> fold()
                    UNFOLDED -> unfold()
                    HALF_FOLDED -> halfFold()
                    REAR_DISPLAY -> rearDisplay()
                }
            }
        }
    }

    private val currentState: FoldableState
        get() =
            if (controller.isFolded) FOLDED
            else if (controller.isHalfFolded) HALF_FOLDED
            else if (controller.isUnfolded) UNFOLDED
            else if (controller.isOnRearDisplay) REAR_DISPLAY else throw IllegalStateException()

    private val screenOn: Boolean
        get() = uiDevice.isScreenOn

    private val displaySurface: Int
        get() = uiDevice.displayWidth * uiDevice.displayHeight
}

private val ANIMATION_TIMEOUT = TimeUnit.SECONDS.toMillis(3)

private enum class FoldableState {
    FOLDED,
    UNFOLDED,
    HALF_FOLDED,
    REAR_DISPLAY,
}
