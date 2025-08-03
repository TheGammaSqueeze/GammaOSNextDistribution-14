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

import android.graphics.Point
import android.graphics.PointF
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
import com.android.cts.input.UinputTouchDevice
import com.android.cts.input.inputeventmatchers.withCoords
import com.android.cts.input.inputeventmatchers.withMotionAction
import com.android.cts.input.inputeventmatchers.withSource
import com.android.cts.input.inputeventmatchers.withToolType
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith

@MediumTest
@RunWith(AndroidJUnit4::class)
class DrawingTabletTest {
    private lateinit var drawingTablet: UinputTouchDevice
    private lateinit var verifier: EventVerifier

    @get:Rule
    val testName = TestName()
    @get:Rule
    val virtualDisplayRule = VirtualDisplayActivityScenarioRule<CaptureEventActivity>(testName)

    @Before
    fun setUp() {
        drawingTablet =
            UinputTouchDevice(
                InstrumentationRegistry.getInstrumentation(),
                virtualDisplayRule.virtualDisplay.display,
                R.raw.test_drawing_tablet_register,
                InputDevice.SOURCE_MOUSE or InputDevice.SOURCE_STYLUS,
                useDisplaySize = true,
            )
        verifier = EventVerifier(virtualDisplayRule.activity::getInputEvent)
    }

    @After
    fun tearDown() {
        if (this::drawingTablet.isInitialized) {
            drawingTablet.close()
        }
    }

    @Test
    fun testCoordinateMappingOrientation0() {
        virtualDisplayRule.runInDisplayOrientation(ORIENTATION_0) {
            verifyTaps(EXPECTED_POINTS_UNROTATED, ::transformForUnrotatedDrawingTablet)
        }
    }

    @Test
    fun testCoordinateMappingOrientation90() {
        virtualDisplayRule.runInDisplayOrientation(ORIENTATION_90) {
            verifyTaps(EXPECTED_POINTS_ROTATED, ::transformForRotatedDrawingTablet)
        }
    }

    @Test
    fun testCoordinateMappingOrientation180() {
        virtualDisplayRule.runInDisplayOrientation(ORIENTATION_180) {
            verifyTaps(EXPECTED_POINTS_UNROTATED, ::transformForUnrotatedDrawingTablet)
        }
    }

    @Test
    fun testCoordinateMappingOrientation270() {
        virtualDisplayRule.runInDisplayOrientation(ORIENTATION_270) {
            verifyTaps(EXPECTED_POINTS_ROTATED, ::transformForRotatedDrawingTablet)
        }
    }

    /**
     * Taps at each point in [INJECTION_POINTS] and ensures that the event is received at the
     * corresponding point in [expectedPoints].
     */
    private fun verifyTaps(
        expectedPoints: Array<PointF?>,
        transformToExpectedPoint: (Point) -> PointF?
    ) {
        for (i in INJECTION_POINTS.indices) {
            val pointerId = 0
            drawingTablet.sendBtnTouch(true)
            drawingTablet.sendDown(pointerId, INJECTION_POINTS[i], UinputTouchDevice.MT_TOOL_PEN)
            drawingTablet.sync()

            drawingTablet.sendBtnTouch(false)
            drawingTablet.sendUp(pointerId)
            drawingTablet.sync()

            val expected = expectedPoints[i]
            // Ensure the hard-coded expected points and the transformation function agree for the
            // injected point.
            assertEquals(transformToExpectedPoint(INJECTION_POINTS[i]), expected)

            if (expected != null) {
                verifier.assertReceivedMotion(allOf(
                    withMotionAction(MotionEvent.ACTION_DOWN),
                    withSource(InputDevice.SOURCE_STYLUS or InputDevice.SOURCE_MOUSE),
                    withToolType(MotionEvent.TOOL_TYPE_STYLUS),
                    withCoords(expected)
                ))

                verifier.assertReceivedMotion(
                    allOf(withMotionAction(MotionEvent.ACTION_MOVE), withCoords(expected))
                )
                verifier.assertReceivedMotion(
                    allOf(withMotionAction(MotionEvent.ACTION_UP), withCoords(expected))
                )
            }
        }
        virtualDisplayRule.activity.assertNoEvents()
    }

    private companion object {
        const val EPSILON = 0.001f

        /** Coordinates in the drawing tablet injected in [verifyTaps]. */
        val INJECTION_POINTS = arrayOf(
            Point(0, 0), // top-left corner of drawing tablet
            Point(WIDTH - 1, 0), // top-right corner of drawing tablet
            Point(WIDTH - 1, HEIGHT - 1), // bottom-right corner of drawing tablet
            Point(0, HEIGHT - 1), // bottom-left corner of drawing tablet
            Point(200, 200), // point inside drawing tablet
            Point(200, 600), // point inside drawing tablet
        )

        /**
         * The points that each of the [INJECTION_POINTS] are expected to map to when the display
         * is rotated to 0 or 180 degrees.
         */
        val EXPECTED_POINTS_UNROTATED = arrayOf<PointF?>(
            PointF(INJECTION_POINTS[0]),
            PointF(INJECTION_POINTS[1]),
            PointF(INJECTION_POINTS[2]),
            PointF(INJECTION_POINTS[3]),
            PointF(INJECTION_POINTS[4]),
            PointF(INJECTION_POINTS[5]),
        )

        fun transformForUnrotatedDrawingTablet(p: Point): PointF? {
            return PointF(p)
        }

        /**
         * The points that each of the [INJECTION_POINTS] are expected to map to when the display
         * is rotated to 90 or 270 degrees.
         */
        val EXPECTED_POINTS_ROTATED = arrayOf<PointF?>(
            PointF(0f, 0f),
            PointF(798.3333f, 0f),
            null,
            null,
            PointF(333.3333f, 333.3333f),
            null,
        )

        fun transformForRotatedDrawingTablet(p: Point): PointF? {
            val rotatedScale = HEIGHT.toFloat() / WIDTH.toFloat()
            val scaled = PointF(p.x * rotatedScale, p.y * rotatedScale)
            if (scaled.x < 0 || scaled.x >= HEIGHT.toFloat() ||
                scaled.y < 0 || scaled.y >= WIDTH.toFloat()) {
                return null
            }
            return scaled
        }
    }
}
