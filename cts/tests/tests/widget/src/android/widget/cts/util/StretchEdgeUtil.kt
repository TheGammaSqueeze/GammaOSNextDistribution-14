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
@file:JvmName("StretchEdgeUtil")

package android.widget.cts.util

import android.app.Instrumentation
import android.app.UiAutomation
import android.content.Context
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.widget.EdgeEffect
import androidx.test.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import com.android.compatibility.common.util.UserHelper
import com.android.compatibility.common.util.WidgetTestUtils

private val userHelper: UserHelper = UserHelper()

/**
 * Flings [view] from the center by ([deltaX], [deltaY]) pixels over 16 milliseconds.
 */
fun fling(
    activityRule: ActivityTestRule<*>,
    view: View,
    deltaX: Int,
    deltaY: Int
) {
    val locationOnScreen = IntArray(2)
    activityRule.runOnUiThread {
        view.getLocationOnScreen(locationOnScreen)
    }

    val screenX = locationOnScreen[0]
    val screenY = locationOnScreen[1]
    val instrumentation = InstrumentationRegistry.getInstrumentation()

    emulateDragGesture(instrumentation, activityRule,
            screenX + (view.width / 2),
            screenY + (view.height / 2),
            deltaX,
            deltaY,
            16,
            4
    )
}

/**
 * Drags [view] from the center by [dragX], [dragY]. Returns `true` if [edgeEffect]'s distance
 * is more than 0 after the stretch.
 */
fun dragStretches(
    activityRule: ActivityTestRule<*>,
    view: View,
    edgeEffect: NoReleaseEdgeEffect,
    dragX: Int,
    dragY: Int
): Boolean {
    val locationOnScreen = IntArray(2)
    activityRule.runOnUiThread {
        view.getLocationOnScreen(locationOnScreen)
    }

    val screenX = locationOnScreen[0]
    val screenY = locationOnScreen[1]
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    emulateDragGesture(instrumentation, activityRule,
            screenX + view.width / 2,
            screenY + view.height / 2,
            dragX,
            dragY,
            160,
            20
    )
    return edgeEffect.distance > 0
}

/**
 * Drags [view] from the center by [dragX], [dragY], then taps to hold.
 * [edgeEffect] must be the [EdgeEffect] that is being stretched in [view].
 * After the drag and before the tap down, [beforeDown] is called. After the
 * tap down and before the tap up, [beforeUp] is called.
 */
fun dragAndHoldExecute(
    activityRule: ActivityTestRule<*>,
    view: View,
    edgeEffect: NoReleaseEdgeEffect,
    dragX: Int = 0,
    dragY: Int = 0,
    beforeDown: Runnable?,
    beforeUp: Runnable?
) {
    val locationOnScreen = IntArray(2)
    activityRule.runOnUiThread {
        view.getLocationOnScreen(locationOnScreen)
    }

    val screenX = locationOnScreen[0] + view.width / 2
    val screenY = locationOnScreen[1] + view.height / 2
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    emulateDragGesture(instrumentation, activityRule,
            screenX,
            screenY,
            dragX,
            dragY,
            160,
            20
    )
    edgeEffect.pauseRelease = false
    edgeEffect.onRelease()

    beforeDown?.run()

    val downTime = SystemClock.uptimeMillis()
    injectDownEvent(
            instrumentation.getUiAutomation(),
            downTime,
            screenX,
            screenY
    )

    beforeUp?.run()

    injectUpEvent(
            instrumentation.getUiAutomation(),
            downTime,
            downTime + 10,
            screenX,
            screenY
    )
}

/**
 * Drag, release, then tap-and-hold and ensure that the stretch stays after the hold.
 */
fun dragAndHoldKeepsStretch(
    activityRule: ActivityTestRule<*>,
    view: View,
    edgeEffect: NoReleaseEdgeEffect,
    dragX: Int = 0,
    dragY: Int = 0
): Boolean {
    var startDistance = 0f
    var nextFrameDistance = 0f

    dragAndHoldExecute(
            activityRule,
            view,
            edgeEffect,
            dragX,
            dragY,
            beforeDown = null,
            beforeUp = Runnable {
                activityRule.runOnUiThread {
                    startDistance = edgeEffect.distance
                }
                activityRule.runOnUiThread {
                    nextFrameDistance = edgeEffect.distance
                }
            }
    )

    return startDistance == nextFrameDistance && startDistance > 0f
}

/**
 * An [EdgeEffect] that does not release with [onRelease] unless [pauseRelease] is `false`.
 */
open class NoReleaseEdgeEffect(context: Context) : EdgeEffect(context) {
    var pauseRelease = true

    var onReleaseCalled = false

    var onPullDistanceCalled = false

    override fun onPullDistance(deltaDistance: Float, displacement: Float): Float {
        onPullDistanceCalled = true
        return super.onPullDistance(deltaDistance, displacement)
    }

    override fun onRelease() {
        onReleaseCalled = true
        if (!pauseRelease) {
            super.onRelease()
        }
    }
}

/**
 * Emulates a linear drag gesture between 2 points across the screen.
 *
 * @param instrumentation the instrumentation used to run the test
 * @param dragStartX Start X of the emulated drag gesture
 * @param dragStartY Start Y of the emulated drag gesture
 * @param dragAmountX X amount of the emulated drag gesture
 * @param dragAmountY Y amount of the emulated drag gesture
 * @param dragDurationMs The time in milliseconds over which the drag occurs
 * @param moveEventCount The number of events that produce the movement
 */
private fun emulateDragGesture(
    instrumentation: Instrumentation,
    activityTestRule: ActivityTestRule<*>?,
    dragStartX: Int,
    dragStartY: Int,
    dragAmountX: Int,
    dragAmountY: Int,
    dragDurationMs: Int,
    moveEventCount: Int
) {
    // We are using the UiAutomation object to inject events so that drag works
    // across view / window boundaries (such as for the emulated drag and drop
    // sequences)
    val uiAutomation = instrumentation.uiAutomation
    val downTime = SystemClock.uptimeMillis()
    injectDownEvent(uiAutomation, downTime, dragStartX, dragStartY)

    val dragEndX = dragStartX + dragAmountX
    val dragEndY = dragStartY + dragAmountY
    // Inject a sequence of MOVE events that emulate the "move" part of the gesture
    injectMoveEventsForDrag(uiAutomation, downTime, downTime, dragStartX, dragStartY,
            dragEndX, dragEndY, moveEventCount, dragDurationMs)
    injectUpEvent(uiAutomation, downTime, downTime + dragDurationMs, dragEndX, dragEndY)

    // Wait for the system to process all events in the queue
    if (activityTestRule != null) {
        WidgetTestUtils.runOnMainAndDrawSync(activityTestRule,
                activityTestRule.activity.getWindow().getDecorView(), null)
    } else {
        instrumentation.waitForIdleSync()
    }
}

fun injectMoveEventsForDrag(
    uiAutomation: UiAutomation,
    downTime: Long,
    dragStartTime: Long = downTime,
    dragStartX: Int,
    dragStartY: Int,
    dragEndX: Int,
    dragEndY: Int,
    moveEventCount: Int,
    dragDurationMs: Int
) {
    val dragAmountX = dragEndX - dragStartX
    val dragAmountY = dragEndY - dragStartY

    for (i in 0 until moveEventCount) {
        // Note that the first MOVE event is generated "away" from the coordinates
        // of the start / DOWN event, and the last MOVE event is generated
        // at the same coordinates as the subsequent UP event.
        val moveX = dragStartX + (dragAmountX * i / moveEventCount)
        val moveY = dragStartY + (dragAmountY * i / moveEventCount)
        val eventTime = dragStartTime + (dragDurationMs * i / moveEventCount)
        injectEvent(uiAutomation, MotionEvent.ACTION_MOVE, downTime, eventTime, moveX, moveY)
    }
}

/**
 * Injects an [MotionEvent.ACTION_UP] event at the given coordinates.
 *
 * @param downTime The time of the event, usually from [SystemClock.uptimeMillis]
 * @param xOnScreen The x screen coordinate to press on
 * @param yOnScreen The y screen coordinate to press on
 * sent.
 */
fun injectUpEvent(
    uiAutomation: UiAutomation,
    downTime: Long,
    upTime: Long,
    xOnScreen: Int,
    yOnScreen: Int
) = injectEvent(uiAutomation, MotionEvent.ACTION_UP, downTime, upTime, xOnScreen, yOnScreen)

/**
 * Injects an [MotionEvent.ACTION_DOWN] event at the given coordinates.
 *
 * @param downTime The time of the event, usually from [SystemClock.uptimeMillis]
 * @param xOnScreen The x screen coordinate to press on
 * @param yOnScreen The y screen coordinate to press on
 * sent.
 */
fun injectDownEvent(
    uiAutomation: UiAutomation,
    downTime: Long,
    xOnScreen: Int,
    yOnScreen: Int
) = injectEvent(uiAutomation, MotionEvent.ACTION_DOWN, downTime, downTime, xOnScreen, yOnScreen)

private fun injectEvent(
    uiAutomation: UiAutomation,
    action: Int,
    downTime: Long,
    eventTime: Long,
    xOnScreen: Int,
    yOnScreen: Int
) {
    val eventUp = MotionEvent.obtain(
            downTime, eventTime, action, xOnScreen.toFloat(), yOnScreen.toFloat(), 1)
    userHelper.injectDisplayIdIfNeeded(eventUp)
    eventUp.source = InputDevice.SOURCE_TOUCHSCREEN
    uiAutomation.injectInputEvent(eventUp, true)
    eventUp.recycle()
}
