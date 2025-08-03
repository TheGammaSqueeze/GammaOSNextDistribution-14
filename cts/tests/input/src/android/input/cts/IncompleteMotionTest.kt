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
package android.input.cts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.MotionEvent.ACTION_CANCEL
import android.view.MotionEvent.ACTION_DOWN
import android.view.MotionEvent.ACTION_MOVE
import android.view.View
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.PollingCheck
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val OVERLAY_ACTIVITY_FOCUSED = "android.input.cts.action.OVERLAY_ACTIVITY_FOCUSED"
private val ACTIVITY_FOCUS_LOST_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(10)

private fun getViewCenterOnScreen(v: View): Pair<Float, Float> {
    val location = IntArray(2)
    v.getLocationOnScreen(location)
    val x = location[0].toFloat() + v.width / 2
    val y = location[1].toFloat() + v.height / 2
    return Pair(x, y)
}

/**
 * When OverlayActivity receives focus, it will send out the OVERLAY_ACTIVITY_FOCUSED broadcast.
 */
class OverlayFocusedBroadcastReceiver : BroadcastReceiver() {
    private val isFocused = AtomicBoolean(false)
    override fun onReceive(context: Context, intent: Intent) {
        isFocused.set(true)
    }

    fun overlayActivityIsFocused(): Boolean {
        return isFocused.get()
    }
}

/**
 * This test injects an incomplete event stream and makes sure that the app processes it correctly.
 * If it does not process it correctly, it can get ANRd.
 *
 * This test reproduces a bug where there was incorrect consumption logic in the InputEventReceiver
 * jni code. If the system has this bug, this test ANRs.
 * The bug occurs when the app consumes a focus event right after a batched MOVE event.
 * In this test, we take care to write a batched MOVE event and a focus event prior to unblocking
 * the UI thread to let the app process these events.
 */
@MediumTest
@RunWith(AndroidJUnit4::class)
class IncompleteMotionTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(IncompleteMotionActivity::class.java)
    private lateinit var activity: IncompleteMotionActivity
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Before
    fun setUp() {
        activityRule.getScenario().onActivity {
            activity = it
        }
        PollingCheck.waitFor { activity.hasWindowFocus() }
    }

    /**
     * Check that MOVE event is received by the activity, even if it's coupled with a FOCUS event.
     */
    @Test
    fun testIncompleteMotion() {
        val downTime = SystemClock.uptimeMillis()
        val (x, y) = getViewCenterOnScreen(activity.window.decorView)

        // Start a valid touch stream
        sendEvent(downTime, ACTION_DOWN, x, y, sync = true)
        val resultFuture = CompletableFuture<Void>()
        // Lock up the UI thread. This ensures that the motion event that we will write will
        // not get processed by the app right away.
        activity.runOnUiThread {
            val sendMoveAndFocus = thread(start = true) {
                try {
                    sendEvent(downTime, ACTION_MOVE, x, y + 10, sync = false)
                    // The MOVE event is sent async because the UI thread is blocked.
                    // Give dispatcher some time to send it to the app
                    SystemClock.sleep(700)

                    val handlerThread = HandlerThread("Receive broadcast from overlay activity")
                    handlerThread.start()
                    val looper: Looper = handlerThread.looper
                    val handler = Handler(looper)
                    val receiver = OverlayFocusedBroadcastReceiver()
                    val intentFilter = IntentFilter(OVERLAY_ACTIVITY_FOCUSED)
                    activity.registerReceiver(
                        receiver,
                        intentFilter,
                        null,
                        handler,
                        Context.RECEIVER_EXPORTED,
                    )

                    // Now send hasFocus=false event to the app by launching a new focusable window
                    startOverlayActivity()
                    PollingCheck.waitFor { receiver.overlayActivityIsFocused() }
                    activity.unregisterReceiver(receiver)
                    handlerThread.quit()
                    // We need to ensure that the focus event has been written to the app's socket
                    // before unblocking the UI thread. Having the overlay activity receive
                    // hasFocus=true event is a good proxy for that. However, it does not guarantee
                    // that dispatcher has written the hasFocus=false event to the current activity.
                    // For safety, add another small sleep here
                    SystemClock.sleep(300)
                    resultFuture.complete(null)
                } catch (e: Throwable) {
                    // Catch potential throwable as to not crash UI thread, rethrow and validate
                    // outside.
                    resultFuture.completeExceptionally(e)
                }
            }
            sendMoveAndFocus.join()
        }
        // The default PollingCheck is 3 seconds, but this one is monitoring multiple operations
        // that will take 1 second at the fastest; if the system is running tasks in the background,
        // this would potentially cause timeouts at this point.
        PollingCheck.waitFor(
            ACTIVITY_FOCUS_LOST_TIMEOUT_MILLIS,
            { !activity.hasWindowFocus() }
        )
        // If the platform implementation has a bug, it would consume both MOVE and FOCUS events,
        // but will only call 'finish' for the focus event.
        // The MOVE event would not be propagated to the app, because the Choreographer
        // callback never gets scheduled
        // If we wait too long here, we will cause ANR (if the platform has a bug).
        // If the MOVE event is received, however, we can stop the test.
        PollingCheck.waitFor { activity.receivedMove() }
        // Finish the gesture. No dangling injected pointers should remain
        sendEvent(downTime, ACTION_CANCEL, x, y, sync = true)
        // Before finishing the test, check that no exceptions occurred while running the
        // instructions in the 'sendMoveAndFocus' thread.
        resultFuture.get()
    }

    private fun sendEvent(downTime: Long, action: Int, x: Float, y: Float, sync: Boolean) {
        val eventTime = when (action) {
            ACTION_DOWN -> downTime
            else -> SystemClock.uptimeMillis()
        }
        val metaState = 0
        val event = MotionEvent.obtain(downTime, eventTime, action, x, y, metaState)
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        instrumentation.uiAutomation.injectInputEvent(event, sync)
    }

    /**
     * Start an activity that overlays the main activity. This is needed in order to move the focus
     * to the newly launched activity, thus causing the bottom activity to lose focus.
     * This activity is not full-screen, in order to prevent the bottom activity from receiving an
     * onStop call. In the previous platform implementation, the ANR behaviour was incorrectly
     * fixed by consuming events from the onStop event.
     * Because the bottom activity's UI thread is locked, use 'am start' to start the new activity
     */
    private fun startOverlayActivity() {
        val flags = " -W -n "
        val startCmd = "am start $flags android.input.cts/.OverlayActivity"
        instrumentation.uiAutomation.executeShellCommand(startCmd)
    }
}
