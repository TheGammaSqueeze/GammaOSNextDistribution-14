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
package android.input.cts

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.PointF
import android.server.wm.WindowManagerStateHelper
import android.view.Display.DEFAULT_DISPLAY
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager.LayoutParams.FLAG_SPLIT_TOUCH
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.PollingCheck
import com.android.cts.input.inputeventmatchers.withCoords
import com.android.cts.input.inputeventmatchers.withCoordsForPointerIndex
import com.android.cts.input.inputeventmatchers.withMotionAction
import org.hamcrest.Matchers.allOf
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@MediumTest
@RunWith(Parameterized::class)
class MultiTouchTest {
    private lateinit var activity: CaptureEventActivity
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var verifier: EventVerifier
    private val touchInjector = TouchInjector(instrumentation)
    private val activityName =
        ComponentName(instrumentation.targetContext, CaptureEventActivity::class.java)

    @JvmField
    @Parameterized.Parameter(0)
    var orientation = 0

    @JvmField
    @Parameterized.Parameter(1)
    var flags = 0

    @Parameterized.Parameter(2)
    lateinit var testName: String

    @Before
    fun setUp() {
        val bundle = ActivityOptions.makeBasic().setLaunchDisplayId(0).toBundle()
        val intent = Intent(Intent.ACTION_VIEW)
                .setComponent(activityName)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra(CaptureEventActivity.EXTRA_FIXED_ORIENTATION, orientation)
                .putExtra(CaptureEventActivity.EXTRA_WINDOW_FLAGS, flags)

        activity = instrumentation.startActivitySync(intent, bundle) as CaptureEventActivity

        PollingCheck.waitFor { activity.hasWindowFocus() }
        verifier = EventVerifier(activity::getInputEvent)

        val wmState = WindowManagerStateHelper()
        wmState.waitForAppTransitionIdleOnDisplay(DEFAULT_DISPLAY)
        wmState.waitForActivityOrientation(activityName, orientation)

        instrumentation.uiAutomation.syncInputTransactions()
        instrumentation.waitForIdleSync()
    }

    /**
     * Check that pointer cancel is received by the activity via injectInputEvent.
     */
    @Test
    fun testMultiTouch() {
        val testPointer = getPositionInView(activity.window.decorView)
        val firstPointerInScreen = getViewPointerOnScreen(activity.window.decorView, testPointer)
        val secondPointer = PointF(testPointer.x + 10, testPointer.y + 10)
        val secondPointerInScreen = getViewPointerOnScreen(activity.window.decorView, secondPointer)

        touchInjector.sendMultiTouchEvent(arrayOf(firstPointerInScreen, secondPointerInScreen))

        verifier.assertReceivedMotion(
            allOf(withMotionAction(MotionEvent.ACTION_DOWN), withCoords(testPointer))
        )
        verifier.assertReceivedMotion(
            allOf(withMotionAction(MotionEvent.ACTION_MOVE), withCoords(testPointer))
        )
        verifier.assertReceivedMotion(
            allOf(
                withMotionAction(MotionEvent.ACTION_POINTER_DOWN, 1),
                withCoordsForPointerIndex(1, secondPointer)
            )
        )
        verifier.assertReceivedMotion(
            allOf(
                withMotionAction(MotionEvent.ACTION_POINTER_UP, 1),
                withCoordsForPointerIndex(1, secondPointer)
            )
        )
        verifier.assertReceivedMotion(
            allOf(withMotionAction(MotionEvent.ACTION_UP), withCoords(testPointer))
        )
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{2}")
        fun parameters() = arrayOf(
            arrayOf(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, 0, "PORTRAIT"),
            arrayOf(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, FLAG_SPLIT_TOUCH, "PORTRAIT_SPLIT"),
            arrayOf(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, 0, "LANDSCAPE"),
            arrayOf(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, FLAG_SPLIT_TOUCH, "LANDSCAPE_SPLIT"),
            arrayOf(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE, 0, "REVERSE_LANDSCAPE"),
            arrayOf(
                ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
                FLAG_SPLIT_TOUCH,
                "REVERSE_LANDSCAPE_SPLIT",
            )
        )

        @JvmStatic
        fun getPositionInView(v: View): PointF {
            val x = v.width / 4f
            val y = v.height / 4f
            return PointF(x, y)
        }

        @JvmStatic
        fun getViewPointerOnScreen(v: View, pt: PointF): PointF {
            val location = IntArray(2)
            v.getLocationOnScreen(location)
            val x = location[0].toFloat() + pt.x
            val y = location[1].toFloat() + pt.y
            return PointF(x, y)
        }
    }
}
