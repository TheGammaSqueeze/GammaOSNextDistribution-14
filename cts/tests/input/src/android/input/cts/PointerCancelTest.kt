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
package android.input.cts

import android.graphics.PointF
import android.server.wm.WindowManagerStateHelper
import android.view.Display.DEFAULT_DISPLAY
import android.view.Gravity
import android.view.InputEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.PollingCheck
import com.android.cts.input.inputeventmatchers.withFlags
import com.android.cts.input.inputeventmatchers.withMotionAction
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import org.hamcrest.Matchers.allOf
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private fun getViewCenterOnScreen(v: View): PointF {
    val location = IntArray(2)
    v.getLocationOnScreen(location)
    val x = location[0].toFloat() + v.width / 2
    val y = location[1].toFloat() + v.height / 2
    return PointF(x, y)
}

@MediumTest
@RunWith(AndroidJUnit4::class)
class PointerCancelTest {
    @get:Rule
    val activityRule = ActivityScenarioRule<CaptureEventActivity>(CaptureEventActivity::class.java)
    private lateinit var activity: CaptureEventActivity
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var verifier: EventVerifier
    private val touchInjector = TouchInjector(instrumentation)

    @Before
    fun setUp() {
        activityRule.getScenario().onActivity {
            activity = it
        }
        PollingCheck.waitFor { activity.hasWindowFocus() }
        verifier = EventVerifier(activity::getInputEvent)

        WindowManagerStateHelper().waitForAppTransitionIdleOnDisplay(DEFAULT_DISPLAY)
        instrumentation.uiAutomation.syncInputTransactions()
    }

    /**
     * Check that pointer cancel is received by the activity via injectInputEvent.
     */
    @Test
    fun testPointerCancelMotion() {
        val pointerInDecorView = getViewCenterOnScreen(activity.window.decorView)
        val secondPointer = PointF(pointerInDecorView.x + 1, pointerInDecorView.y + 1)

        // Start a valid touch stream
        touchInjector.sendMultiTouchEvent(arrayOf(pointerInDecorView, secondPointer), true)
        verifier.assertReceivedMotion(withMotionAction(MotionEvent.ACTION_DOWN))
        verifier.assertReceivedMotion(withMotionAction(MotionEvent.ACTION_MOVE))
        verifier.assertReceivedMotion(withMotionAction(MotionEvent.ACTION_POINTER_DOWN, 1))
        verifier.assertReceivedMotion(
            allOf(
                withMotionAction(MotionEvent.ACTION_POINTER_UP, 1),
                withFlags(MotionEvent.FLAG_CANCELED)
            )
        )
        verifier.assertReceivedMotion(withMotionAction(MotionEvent.ACTION_UP))
    }

    @Test
    fun testPointerCancelForSplitTouch() {
        val view = addFloatingWindow()
        val pointerInFloating = getViewCenterOnScreen(view)
        val pointerOutsideFloating = PointF(
            pointerInFloating.x + view.width / 2 + 1,
            pointerInFloating.y + view.height / 2 + 1
        )

        val eventsInFloating = LinkedBlockingQueue<InputEvent>()
        view.setOnTouchListener { _, event ->
            eventsInFloating.add(MotionEvent.obtain(event))
        }
        val verifierForFloating = EventVerifier { eventsInFloating.poll(5, TimeUnit.SECONDS) }

        touchInjector.sendMultiTouchEvent(arrayOf(pointerInFloating, pointerOutsideFloating), true)

        // First finger down (floating window)
        verifierForFloating.assertReceivedMotion(withMotionAction(MotionEvent.ACTION_DOWN))

        // First finger move (floating window)
        verifierForFloating.assertReceivedMotion(withMotionAction(MotionEvent.ACTION_MOVE))

        // Second finger down (activity window)
        verifier.assertReceivedMotion(withMotionAction(MotionEvent.ACTION_DOWN))
        verifierForFloating.assertReceivedMotion(withMotionAction(MotionEvent.ACTION_MOVE))

        // ACTION_CANCEL with cancel flag (activity window)
        verifier.assertReceivedMotion(withMotionAction(MotionEvent.ACTION_CANCEL))
        verifierForFloating.assertReceivedMotion(withMotionAction(MotionEvent.ACTION_MOVE))

        // First finger up (floating window)
        verifierForFloating.assertReceivedMotion(withMotionAction(MotionEvent.ACTION_UP))
    }

    private fun addFloatingWindow(): View {
        val view = View(instrumentation.targetContext)
        val layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.TYPE_APPLICATION,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        or WindowManager.LayoutParams.FLAG_SPLIT_TOUCH
        )
        layoutParams.x = 0
        layoutParams.y = 0
        layoutParams.width = 100
        layoutParams.height = 100
        layoutParams.gravity = Gravity.LEFT or Gravity.CENTER_VERTICAL

        activity.runOnUiThread {
            view.setBackgroundColor(android.graphics.Color.RED)
            activity.windowManager.addView(view, layoutParams)
        }

        PollingCheck.waitFor {
            view.hasWindowFocus()
        }
        instrumentation.uiAutomation.syncInputTransactions()
        return view
    }
}
