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

package android.input.cts

import android.graphics.Point
import android.graphics.PointF
import android.hardware.input.InputManager
import android.input.cts.VirtualDisplayActivityScenarioRule.Companion.HEIGHT
import android.input.cts.VirtualDisplayActivityScenarioRule.Companion.ORIENTATION_0
import android.input.cts.VirtualDisplayActivityScenarioRule.Companion.ORIENTATION_180
import android.input.cts.VirtualDisplayActivityScenarioRule.Companion.ORIENTATION_270
import android.input.cts.VirtualDisplayActivityScenarioRule.Companion.ORIENTATION_90
import android.input.cts.VirtualDisplayActivityScenarioRule.Companion.WIDTH
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.cts.input.DebugInputRule
import com.android.cts.input.UinputTouchDevice
import com.android.cts.input.inputeventmatchers.withCoords
import com.android.cts.input.inputeventmatchers.withFlags
import com.android.cts.input.inputeventmatchers.withMotionAction
import org.hamcrest.Description
import org.hamcrest.Matchers.allOf
import org.hamcrest.TypeSafeMatcher
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith

@MediumTest
@RunWith(AndroidJUnit4::class)
class TouchScreenTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var touchScreen: UinputTouchDevice
    private lateinit var verifier: EventVerifier

    @get:Rule
    val debugInputRule = DebugInputRule()
    @get:Rule
    val testName = TestName()
    @get:Rule
    val virtualDisplayRule = VirtualDisplayActivityScenarioRule<CaptureEventActivity>(testName)

    @Before
    fun setUp() {
        touchScreen = UinputTouchDevice(
                instrumentation,
                virtualDisplayRule.virtualDisplay.display,
                R.raw.test_touchscreen_register,
                InputDevice.SOURCE_TOUCHSCREEN,
                useDisplaySize = true,
        )
        verifier = EventVerifier(virtualDisplayRule.activity::getInputEvent)
    }

    @After
    fun tearDown() {
        if (this::touchScreen.isInitialized) {
            touchScreen.close()
        }
    }

    @Test
    fun testHostUsiVersionIsNull() {
        assertNull(
            instrumentation.targetContext.getSystemService(InputManager::class.java)
                .getHostUsiVersion(virtualDisplayRule.virtualDisplay.display)
        )
    }

    @DebugInputRule.DebugInput(bug = 288321659)
    @Test
    fun testSingleTouch() {
        val pointer = Point(100, 100)
        val pointerId = 0

        // ACTION_DOWN
        touchScreen.sendBtnTouch(true)
        touchScreen.sendDown(pointerId, pointer)
        touchScreen.sync()
        verifier.assertReceivedMotion(withMotionAction(MotionEvent.ACTION_DOWN))

        // ACTION_MOVE
        pointer.offset(1, 1)
        touchScreen.sendMove(pointerId, pointer)
        touchScreen.sync()
        verifier.assertReceivedMotion(withMotionAction(MotionEvent.ACTION_MOVE))

        // ACTION_UP
        touchScreen.sendBtnTouch(false)
        touchScreen.sendUp(pointerId)
        touchScreen.sync()
        verifier.assertReceivedMotion(withMotionAction(MotionEvent.ACTION_UP))
    }

    @DebugInputRule.DebugInput(bug = 288321659)
    @Test
    fun testMultiTouch() {
        val pointer0 = Point(100, 100)
        val pointer1 = Point(150, 150)
        val pointerId0 = 0
        val pointerId1 = 1

        // ACTION_DOWN
        touchScreen.sendBtnTouch(true)
        touchScreen.sendDown(pointerId0, pointer0)
        touchScreen.sync()
        verifier.assertReceivedMotion(withMotionAction(MotionEvent.ACTION_DOWN))

        // ACTION_POINTER_DOWN
        touchScreen.sendDown(pointerId1, pointer1)
        touchScreen.sync()
        verifier.assertReceivedMotion(withMotionAction(MotionEvent.ACTION_POINTER_DOWN, 1))

        // ACTION_MOVE
        pointer1.offset(1, 1)
        touchScreen.sendMove(pointerId1, pointer1)
        touchScreen.sync()
        verifier.assertReceivedMotion(withMotionAction(MotionEvent.ACTION_MOVE))

        // ACTION_POINTER_UP
        touchScreen.sendUp(pointerId0)
        touchScreen.sync()
        verifier.assertReceivedMotion(withMotionAction(MotionEvent.ACTION_POINTER_UP, 0))

        // ACTION_UP
        touchScreen.sendBtnTouch(false)
        touchScreen.sendUp(pointerId1)
        touchScreen.sync()
        verifier.assertReceivedMotion(withMotionAction(MotionEvent.ACTION_UP))
    }

    @DebugInputRule.DebugInput(bug = 288321659)
    @Test
    fun testDeviceCancel() {
        val pointer = Point(100, 100)
        val pointerId = 0

        // ACTION_DOWN
        touchScreen.sendBtnTouch(true)
        touchScreen.sendDown(pointerId, pointer)
        touchScreen.sync()
        verifier.assertReceivedMotion(withMotionAction(MotionEvent.ACTION_DOWN))

        // ACTION_MOVE
        pointer.offset(1, 1)
        touchScreen.sendMove(pointerId, pointer)
        touchScreen.sync()
        verifier.assertReceivedMotion(withMotionAction(MotionEvent.ACTION_MOVE))

        // ACTION_CANCEL
        touchScreen.sendToolType(pointerId, UinputTouchDevice.MT_TOOL_PALM)
        touchScreen.sync()
        verifier.assertReceivedMotion(withMotionAction(MotionEvent.ACTION_CANCEL))

        // No event
        touchScreen.sendBtnTouch(false)
        touchScreen.sendUp(pointerId)
        touchScreen.sync()
        virtualDisplayRule.activity.assertNoEvents()
    }

    /**
     * Check that pointer cancel is received by the activity via uinput device.
     */
    @DebugInputRule.DebugInput(bug = 288321659)
    @Test
    fun testDevicePointerCancel() {
        val pointer0 = Point(100, 100)
        val pointer1 = Point(150, 150)
        val pointerId0 = 0
        val pointerId1 = 1

        // ACTION_DOWN
        touchScreen.sendBtnTouch(true)
        touchScreen.sendDown(pointerId0, pointer0)
        touchScreen.sync()
        verifier.assertReceivedMotion(withMotionAction(MotionEvent.ACTION_DOWN))

        // ACTION_MOVE
        pointer0.offset(1, 1)
        touchScreen.sendMove(pointerId0, pointer0)
        touchScreen.sync()
        verifier.assertReceivedMotion(withMotionAction(MotionEvent.ACTION_MOVE))

        // ACTION_POINTER_DOWN(1)
        touchScreen.sendDown(pointerId1, pointer1)
        touchScreen.sync()
        verifier.assertReceivedMotion(withMotionAction(MotionEvent.ACTION_POINTER_DOWN, 1))

        // ACTION_POINTER_UP(1) with cancel flag
        touchScreen.sendToolType(pointerId1, UinputTouchDevice.MT_TOOL_PALM)
        touchScreen.sync()
        verifier.assertReceivedMotion(
            allOf(
                withMotionAction(MotionEvent.ACTION_POINTER_UP, 1),
                withFlags(MotionEvent.FLAG_CANCELED)
            )
        )

        // ACTION_UP
        touchScreen.sendBtnTouch(false)
        touchScreen.sendUp(pointerId0)
        touchScreen.sync()
        verifier.assertReceivedMotion(withMotionAction(MotionEvent.ACTION_UP))
    }

    @Test
    fun testTouchScreenPrecisionOrientation0() {
        virtualDisplayRule.runInDisplayOrientation(ORIENTATION_0) {
            verifyTapsOnFourCorners(
                arrayOf(
                    PointF(0f, 0f),
                    PointF(WIDTH - 1f, 0f),
                    PointF(WIDTH - 1f, HEIGHT - 1f),
                    PointF(0f, HEIGHT - 1f),
                )
            )
        }
    }

    @Test
    fun testTouchScreenPrecisionOrientation90() {
        virtualDisplayRule.runInDisplayOrientation(ORIENTATION_90) {
            verifyTapsOnFourCorners(
                arrayOf(
                    PointF(0f, WIDTH - 1f),
                    PointF(0f, 0f),
                    PointF(HEIGHT - 1f, 0f),
                    PointF(HEIGHT - 1f, WIDTH - 1f),
                )
            )
        }
    }

    @Test
    fun testTouchScreenPrecisionOrientation180() {
        virtualDisplayRule.runInDisplayOrientation(ORIENTATION_180) {
            verifyTapsOnFourCorners(
                arrayOf(
                    PointF(WIDTH - 1f, HEIGHT - 1f),
                    PointF(0f, HEIGHT - 1f),
                    PointF(0f, 0f),
                    PointF(WIDTH - 1f, 0f),
                )
            )
        }
    }

    @Test
    fun testTouchScreenPrecisionOrientation270() {
        virtualDisplayRule.runInDisplayOrientation(ORIENTATION_270) {
            verifyTapsOnFourCorners(
                arrayOf(
                    PointF(HEIGHT - 1f, 0f),
                    PointF(HEIGHT - 1f, WIDTH - 1f),
                    PointF(0f, WIDTH - 1f),
                    PointF(0f, 0f),
                )
            )
        }
    }

    @Test
    fun testEventTime() {
        val pointer = Point(100, 100)
        val pointerId = 0

        val withConsistentEventTime = object : TypeSafeMatcher<MotionEvent>() {
            override fun describeTo(description: Description) {
                description.appendText("getEventTimeNanos() is consistent with getEventTime()")
            }

            override fun matchesSafely(event: MotionEvent): Boolean {
                return event.getEventTimeNanos() / 1_000_000 == event.getEventTime()
            }
        }

       // ACTION_DOWN
        touchScreen.sendBtnTouch(true)
        touchScreen.sendDown(pointerId, pointer)
        touchScreen.sync()
        verifier.assertReceivedMotion(
            allOf(withMotionAction(MotionEvent.ACTION_DOWN), withConsistentEventTime)
        )

        // ACTION_MOVE
        pointer.offset(1, 1)
        touchScreen.sendMove(pointerId, pointer)
        touchScreen.sync()
        verifier.assertReceivedMotion(
            allOf(withMotionAction(MotionEvent.ACTION_MOVE), withConsistentEventTime)
        )

        // ACTION_UP
        touchScreen.sendBtnTouch(false)
        touchScreen.sendUp(pointerId)
        touchScreen.sync()
        verifier.assertReceivedMotion(
            allOf(withMotionAction(MotionEvent.ACTION_UP), withConsistentEventTime)
        )
    }

    // Verifies that each of the four corners of the touch screen (lt, rt, rb, lb) map to the
    // given four points by tapping on the corners in order and asserting the location of the
    // received events match the provided values.
    private fun verifyTapsOnFourCorners(expectedPoints: Array<PointF>) {
        val pointerId = 0
        for (i in 0 until 4) {
            touchScreen.sendBtnTouch(true)
            touchScreen.sendDown(pointerId, CORNERS[i])
            touchScreen.sync()
            verifier.assertReceivedMotion(
                allOf(withMotionAction(MotionEvent.ACTION_DOWN), withCoords(expectedPoints[i]))
            )

            touchScreen.sendBtnTouch(false)
            touchScreen.sendUp(pointerId)
            touchScreen.sync()
            verifier.assertReceivedMotion(withMotionAction(MotionEvent.ACTION_UP))
        }
    }

    companion object {
        // The four corners of the touchscreen: lt, rt, rb, lb
        val CORNERS = arrayOf(
            Point(0, 0),
            Point(WIDTH - 1, 0),
            Point(WIDTH - 1, HEIGHT - 1),
            Point(0, HEIGHT - 1),
        )
    }
}
