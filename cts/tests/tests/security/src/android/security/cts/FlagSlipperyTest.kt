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

package android.security.cts

import android.app.ActivityOptions
import android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN
import android.graphics.Rect
import android.os.SystemClock
import android.platform.test.annotations.AsbSecurityTest
import android.view.Gravity
import android.view.InputDevice
import android.view.MotionEvent
import android.view.SurfaceControlViewHost
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
import android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
import android.view.WindowManager.LayoutParams.FLAG_SLIPPERY
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.android.compatibility.common.util.PollingCheck
import com.android.sts.common.util.StsExtraBusinessLogicTestCase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.Queue

private fun getViewCenterOnScreen(v: View): Pair<Float, Float> {
    val location = IntArray(2)
    v.getLocationOnScreen(location)
    val x = location[0] + v.width / 2f
    val y = location[1] + v.height / 2f
    return Pair(x, y)
}

private fun assertAction(action: Int, event: MotionEvent?) {
    if (event == null) {
        fail("Expected ${MotionEvent.actionToString(action)}, but got a null event instead")
        return
    }
    assertEquals("Expected ${MotionEvent.actionToString(action)}, but received " +
            "${MotionEvent.actionToString(event.action)}", action, event.action)
}

private class SurfaceCreatedCallback(created: CountDownLatch) : SurfaceHolder.Callback {
    private val surfaceCreated = created
    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceCreated.countDown()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {}

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
}

@MediumTest
@RunWith(AndroidJUnit4::class)
/**
 * Non-system windows cannot use FLAG_SLIPPERY. In this test, we test that if a window specifies
 * this flag, then the flag is dropped.
 * It's not sufficient to simply check that the flag is dropped, so we test the actual behaviour of
 * the windows.
 * There are 2 windows in this test:
 * 1) The bottom activity, which is full screen. It's called 'SlipperyEnterBottomActivity' because
 * the touch will enter this activity from the slippery window
 * 2) The top window, 'topView' (or 'surfaceView'/'embeddedView'). This is the window that specifies
 * the FLAG_SLIPPERY in its layout params. We could also call it 'SlipperyExit' window, because
 * the touch will exit from this window if the slippery behaviour is enabled.
 *
 * The test does the following:
 * 1) Inject DOWN event to the slippery (top) window. When the top window receives the DOWN event,
 * it moves itself out of the way, so that the user effectively sees that the finger is over the
 * bottom activity instead.
 * 2) Inject MOVE event. If the top window were indeed slippery, this MOVE event would end up going
 * to the bottom activity, and would become a DOWN event instead (since until that point, activity
 * does not have an active touch stream). However, since we are not allowing this top window to be
 * slippery, the MOVE event should just continue to be delivered to the top window.
 * In this test, we are checking that the top window received the MOVE event, and that the bottom
 * window did not receive any events.
 *
 * There are several ways to specify FLAG_SLIPPERY on a window (or a generic entity that receives
 * touch). These are:
 * 1) WindowManagerService::addWindow
 * 2) WindowManagerService::relayoutWindow
 * 3) WindowManagerService::grantInputChannel
 *
 * So we should test all 3 of these approaches. The first 2 are similar, so they share the same
 * test code. The third approach requires adding an embedded window, and the code for that test was
 * forked to avoid excessive branching.
 */
class FlagSlipperyTest : StsExtraBusinessLogicTestCase {
    private lateinit var scenario: ActivityScenario<SlipperyEnterBottomActivity>
    private lateinit var windowManager: WindowManager

    private val nonSlipperyLayoutParams = WindowManager.LayoutParams(TYPE_APPLICATION_OVERLAY,
            FLAG_NOT_TOUCH_MODAL)
    private val slipperyLayoutParams = WindowManager.LayoutParams(TYPE_APPLICATION_OVERLAY,
            FLAG_NOT_TOUCH_MODAL or FLAG_SLIPPERY)
    private val layoutCompleted = AtomicBoolean(false)
    private val eventsForTopWindow: Queue<MotionEvent> = LinkedBlockingQueue()
    private var viewToRemove: View? = null

    @get:Rule
    val rule = ActivityScenarioRule<SlipperyEnterBottomActivity>(
            SlipperyEnterBottomActivity::class.java,
            ActivityOptions.makeBasic().apply {
                setLaunchWindowingMode(WINDOWING_MODE_FULLSCREEN)
            }.toBundle())

    constructor() : super()

    @Before
    fun setup() {
        scenario = rule.getScenario()
        windowManager = getInstrumentation().getTargetContext().getSystemService<WindowManager>(
                WindowManager::class.java)!!
        setDimensionsToQuarterScreen()

        waitForWindowFocusOnBottomActivity()
    }

    @After
    fun tearDown() {
        eventsForTopWindow.clear()
        if (viewToRemove != null) {
            scenario.onActivity {
                windowManager.removeViewImmediate(viewToRemove)
            }
            viewToRemove = null
        }
    }

    // ========================== Regular window tests =============================================

    private fun addWindow(slipperyWhenAdded: Boolean): View {
        val view = View(getInstrumentation().targetContext)
        scenario.onActivity {
            view.setOnTouchListener(OnTouchListener(view))
            view.setBackgroundColor(android.graphics.Color.RED)
            layoutCompleted.set(false)
            view.viewTreeObserver.addOnGlobalLayoutListener {
                layoutCompleted.set(true)
            }

            if (slipperyWhenAdded) {
                windowManager.addView(view, slipperyLayoutParams)
            } else {
                // Add the window with non-slippery params, and make it slippery via updateLayout
                windowManager.addView(view, nonSlipperyLayoutParams)
            }
        }
        waitForLayoutToComplete()
        if (!slipperyWhenAdded) {
            scenario.onActivity {
                layoutCompleted.set(false)
                windowManager.updateViewLayout(view, slipperyLayoutParams)
            }
        }
        waitForLayoutToComplete()
        PollingCheck.waitFor {
            view.hasWindowFocus()
        }
        return view
    }

    private fun testWindowIsNotSlippery(slipperyWhenAdded: Boolean) {
        // Start overlay (attacker) activity
        // Attacker: create a window that is slippery and will capture the initial DOWN touch event,
        // then will move itself out of the way, forcing the next MOVE event to go to the bottom
        // window
        val topView = addWindow(slipperyWhenAdded)
        viewToRemove = topView
        // Inject motion DOWN into the attacking activity. It will cause the activity to move to
        // bottom right, which will make the next touch slip into the current window
        assertBottomWindowDoesNotReceiveSlipperyTouch(topView)
    }

    /**
     * Test a top window that tries to set FLAG_SLIPPERY when it is added to WindowManager
     */
    @Test
    @AsbSecurityTest(cveBugId = [157929241])
    fun testWindowIsNotSlipperyWhenAdded() {
        testWindowIsNotSlippery(true /* slipperyWhenAdded */)
    }

    /**
     * Test a top window that tries to set FLAG_SLIPPERY during relayout
     */
    @Test
    @AsbSecurityTest(cveBugId = [157929241])
    fun testWindowIsNotSlipperyAfterRelayout() {
        testWindowIsNotSlippery(false /* slipperyWhenAdded */)
    }

    // ========================== Embedded window tests ============================================
    private lateinit var mVr: SurfaceControlViewHost

    private fun addEmbeddedHostWindow(): SurfaceView {
        val surfaceView = SurfaceView(getInstrumentation().targetContext)
        val surfaceCreated = CountDownLatch(1)
        scenario.onActivity {
            surfaceView.setZOrderOnTop(true)
            // The color green should not be visible, but helps debug if there are any layout issues
            // with the embedded view that will be positioned on top
            surfaceView.setBackgroundColor(android.graphics.Color.GREEN)
            surfaceView.viewTreeObserver.addOnGlobalLayoutListener {
                layoutCompleted.set(true)
            }
            surfaceView.getHolder().addCallback(SurfaceCreatedCallback(surfaceCreated))
            windowManager.addView(surfaceView, slipperyLayoutParams)
        }
        waitForLayoutToComplete()
        surfaceCreated.await()
        PollingCheck.waitFor {
            surfaceView.hasWindowFocus()
        }
        return surfaceView
    }

    private fun addEmbeddedView(surfaceView: SurfaceView): View {
        val embeddedViewDrawn = CountDownLatch(1)
        val viewDrawnCallback = Runnable {
            embeddedViewDrawn.countDown()
        }
        layoutCompleted.set(false)
        val embeddedView = View(getInstrumentation().targetContext)
        scenario.onActivity {
            embeddedView.setOnTouchListener(OnTouchListener(surfaceView))
            embeddedView.setBackgroundColor(android.graphics.Color.RED)
            embeddedView.viewTreeObserver.addOnGlobalLayoutListener {
                layoutCompleted.set(true)
            }

            embeddedView.viewTreeObserver.registerFrameCommitCallback(viewDrawnCallback)
            mVr = SurfaceControlViewHost(it, it.getDisplay()!!, surfaceView.getHostToken())
            mVr.setView(embeddedView, slipperyLayoutParams)
            surfaceView.setChildSurfacePackage(mVr.getSurfacePackage()!!)
            embeddedView.invalidate()
        }
        embeddedViewDrawn.await()
        embeddedView.viewTreeObserver.unregisterFrameCommitCallback(viewDrawnCallback)
        waitForLayoutToComplete()
        return embeddedView
    }

    /**
     * Create an embedded slippery window and ensure it continues to receive touch after it moves
     * away from the touched position.
     */
    @Test
    @AsbSecurityTest(cveBugId = [157929241])
    fun testWindowlessWindowIsNotSlippery() {
        val surfaceView = addEmbeddedHostWindow()
        viewToRemove = surfaceView
        // 'embeddedView' variable is used to retain the reference through the end of the test
        @Suppress("UNUSED_VARIABLE") val embeddedView = addEmbeddedView(surfaceView)

        assertBottomWindowDoesNotReceiveSlipperyTouch(surfaceView)
    }

    // ========================== Shared utility functions =========================================

    private inner class OnTouchListener(relayoutView: View) : View.OnTouchListener {
        val relayoutView = relayoutView
        override fun onTouch(v: View, e: MotionEvent): Boolean {
            if (e.action == MotionEvent.ACTION_DOWN) {
                // Move the window out of the way by changing the gravity to bottom right
                val wmlp = WindowManager.LayoutParams()
                wmlp.copyFrom(slipperyLayoutParams)
                wmlp.gravity = Gravity.BOTTOM or Gravity.RIGHT
                layoutCompleted.set(false)
                // Cannot always call 'updateViewLayout' for the incoming view, because in the
                // embedded case, the provided embedded view is not attached to the window manager
                // (and will therefore crash). Just use the view provided in the constructor.
                windowManager.updateViewLayout(relayoutView, wmlp)
                return true
            }
            eventsForTopWindow.add(MotionEvent.obtain(e))
            return true
        }
    }

    private fun assertBottomWindowDoesNotReceiveSlipperyTouch(topView: View) {
        // Inject motion DOWN into the top view / window. It will cause the window to move to
        // bottom right, which will make the next touch slip into the current window if the top
        // window is actually slippery
        val (x, y) = getViewCenterOnScreen(topView)
        val downTime = SystemClock.uptimeMillis()
        sendEvent(downTime, MotionEvent.ACTION_DOWN, x, y)
        waitForLayoutToComplete()
        sendEvent(downTime, MotionEvent.ACTION_MOVE, x + 1, y + 1)
        scenario.onActivity {
            // Bottom activity should not get any events
            assertNull(it.getEvent())
            // Top window should continue getting events.
            assertAction(MotionEvent.ACTION_MOVE, eventsForTopWindow.poll())
            assertNull(eventsForTopWindow.poll())
        }
    }

    /**
     * Wait until the bottom activity has window focus
     */
    private fun waitForWindowFocusOnBottomActivity() {
        PollingCheck.waitFor {
            var activityHasWindowFocus = AtomicBoolean(false)
            scenario.onActivity { activity -> run {
                    activityHasWindowFocus.set(activity.hasWindowFocus())
                }
            }
            activityHasWindowFocus.get()
        }
    }

    private fun waitForLayoutToComplete() {
        PollingCheck.waitFor {
            layoutCompleted.get()
        }
        getInstrumentation().uiAutomation.syncInputTransactions(true /*waitAnimations*/)
    }

    private fun setDimensionsToQuarterScreen() {
        val bounds: Rect = windowManager.currentWindowMetrics.bounds
        val width = (bounds.right - bounds.left) / 4
        val height = (bounds.bottom - bounds.top) / 4
        slipperyLayoutParams.width = width
        slipperyLayoutParams.height = height
        nonSlipperyLayoutParams.width = width
        nonSlipperyLayoutParams.height = height
    }

    private fun sendEvent(downTime: Long, action: Int, x: Float, y: Float) {
        val eventTime = when (action) {
            MotionEvent.ACTION_DOWN -> downTime
            else -> SystemClock.uptimeMillis()
        }
        val event = MotionEvent.obtain(downTime, eventTime, action, x, y, 0 /*metaState*/)
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        getInstrumentation().uiAutomation.injectInputEvent(event, true /*sync*/)
    }

    companion object {
        private val TAG = "FlagSlipperyTest"
    }
}
