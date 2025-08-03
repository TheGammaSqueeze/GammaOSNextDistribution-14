/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.view.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Point;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.util.TypedValue;
import android.view.Display;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.cts.util.InputDeviceIterators;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Set;

/**
 * Test {@link ViewConfiguration}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class ViewConfigurationTest {
    private static Set<Integer> SUPPORTED_AXES_FOR_SCROLL_HAPTICS = Set.of(MotionEvent.AXIS_SCROLL);

    @Test
    public void testStaticValues() {
        ViewConfiguration.getScrollBarSize();
        ViewConfiguration.getFadingEdgeLength();
        ViewConfiguration.getPressedStateDuration();
        ViewConfiguration.getLongPressTimeout();
        assertTrue(ViewConfiguration.getMultiPressTimeout() > 0);
        ViewConfiguration.getTapTimeout();
        ViewConfiguration.getJumpTapTimeout();
        ViewConfiguration.getEdgeSlop();
        ViewConfiguration.getTouchSlop();
        ViewConfiguration.getWindowTouchSlop();
        ViewConfiguration.getMinimumFlingVelocity();
        ViewConfiguration.getMaximumFlingVelocity();
        ViewConfiguration.getMaximumDrawingCacheSize();
        ViewConfiguration.getZoomControlsTimeout();
        ViewConfiguration.getGlobalActionKeyTimeout();
        ViewConfiguration.getScrollFriction();
        ViewConfiguration.getScrollBarFadeDuration();
        ViewConfiguration.getScrollDefaultDelay();
        ViewConfiguration.getDoubleTapTimeout();
        ViewConfiguration.getKeyRepeatTimeout();
        ViewConfiguration.getKeyRepeatDelay();
        ViewConfiguration.getDefaultActionModeHideDuration();
    }

    @Test
    public void testConstructor() {
        new ViewConfiguration();
    }

    @Test
    public void testInstanceValues() {
        Context context = InstrumentationRegistry.getTargetContext();
        ViewConfiguration vc = ViewConfiguration.get(context);
        assertNotNull(vc);

        vc.getScaledDoubleTapSlop();
        vc.getScaledEdgeSlop();
        vc.getScaledFadingEdgeLength();
        vc.getScaledMaximumDrawingCacheSize();
        vc.getScaledMaximumFlingVelocity();
        vc.getScaledMinimumFlingVelocity();
        vc.getScaledOverflingDistance();
        vc.getScaledOverscrollDistance();
        vc.getScaledPagingTouchSlop();
        vc.getScaledScrollBarSize();
        vc.getScaledHorizontalScrollFactor();
        vc.getScaledVerticalScrollFactor();
        vc.getScaledTouchSlop();
        vc.getScaledHandwritingSlop();
        vc.getScaledWindowTouchSlop();
        vc.hasPermanentMenuKey();

        float pixelsToMmRatio = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_MM, 1,
                context.getResources().getDisplayMetrics());

        // Verify that the min scaling span size is reasonable.
        float scaledMinScalingSpanMm = vc.getScaledMinimumScalingSpan() / pixelsToMmRatio;
        assertTrue(scaledMinScalingSpanMm > 0);
        assertTrue(scaledMinScalingSpanMm < 40.5); // 1.5 times the recommended size of 27mm
    }

    @Test
    public void testExceptionsThrown() {
        ViewConfiguration vc = new ViewConfiguration();
        boolean correctExceptionThrown = false;
        try {
            vc.getScaledMinimumScalingSpan();
        } catch (IllegalStateException e) {
            if (e.getMessage().equals("Min scaling span cannot be determined when this "
                    + "method is called on a ViewConfiguration that was instantiated using a "
                    + "constructor with no Context parameter")) {
                correctExceptionThrown = true;
            }
        }
        assertTrue(correctExceptionThrown);
    }

    /**
     * The purpose of the ambiguous gesture multiplier is to potentially increase the touch slop
     * and the long press timeout to allow the gesture classifier an additional window to
     * make the classification. Therefore, this multiplier should be always greater or equal to 1.
     */
    @Test
    public void testGetAmbiguousGestureMultiplier() {
        final float staticMultiplier = ViewConfiguration.getAmbiguousGestureMultiplier();
        assertTrue(staticMultiplier >= 1);

        ViewConfiguration vc = ViewConfiguration.get(InstrumentationRegistry.getTargetContext());
        final float instanceMultiplier = vc.getAmbiguousGestureMultiplier();
        assertTrue(instanceMultiplier >= 1);
    }

    @Test
    public void testFlingThresholds_forInvalidInputDeviceIds() {
        Context context = InstrumentationRegistry.getTargetContext();
        ViewConfiguration contextBasedVc = ViewConfiguration.get(context);
        ViewConfiguration contextLessVc = new ViewConfiguration();
        for (ViewConfiguration vc : new ViewConfiguration[] {contextBasedVc, contextLessVc}) {
            InputDeviceIterators.iteratorOverInvalidDeviceIds((deviceId) -> {
                // Test with some source-axis combinations. Any source-axis combination should
                // provide the no-fling thresholds, since the device ID is known to be invalid.
                verifyNoFlingThresholds(
                        vc, deviceId, InputDevice.SOURCE_TOUCHSCREEN, MotionEvent.AXIS_X);
                verifyNoFlingThresholds(
                        vc, deviceId, InputDevice.SOURCE_TOUCHSCREEN, MotionEvent.AXIS_Y);
                verifyNoFlingThresholds(
                        vc, deviceId, InputDevice.SOURCE_ROTARY_ENCODER, MotionEvent.AXIS_SCROLL);
            });
        }
    }

    @Test
    public void testFlingThresholds_forAllAvailableDevices() {
        Context context = InstrumentationRegistry.getTargetContext();
        ViewConfiguration contextBasedVc = ViewConfiguration.get(context);
        ViewConfiguration contextLessVc = new ViewConfiguration();
        for (ViewConfiguration vc : new ViewConfiguration[] {contextBasedVc, contextLessVc}) {
            InputDeviceIterators.iteratorOverEveryInputDeviceMotionRange((deviceId, range) -> {
                int axis = range.getAxis();
                int source = range.getSource();

                int minVel = vc.getScaledMinimumFlingVelocity(deviceId, axis, source);
                int maxVel = vc.getScaledMaximumFlingVelocity(deviceId, axis, source);

                // These min/max thresholds are thresholds for a valid InputDevice ID, on a
                // source and axis applicable to the InputDevice represented by the ID. Check
                // that the provided thresholds are within the valid bounds.
                verifyFlingThresholdRange(minVel, maxVel);
            });
            InputDeviceIterators.iteratorOverEveryValidDeviceId((deviceId) -> {
                // Test with source-axis combinations that we know are not valid. Since the
                // source-axis combinations will be invalid, we expect the no-fling thresholds,
                // despite the fact that we're using a valid InputDevice ID.
                verifyNoFlingThresholds(
                        vc, deviceId, InputDevice.SOURCE_ROTARY_ENCODER, MotionEvent.AXIS_X);
                verifyNoFlingThresholds(
                        vc, deviceId, InputDevice.SOURCE_TOUCHSCREEN, MotionEvent.AXIS_WHEEL);
            });
        }
    }

    @Test
    public void testGetScaledAmbiguousGestureMultiplier() {
        ViewConfiguration vc = ViewConfiguration.get(InstrumentationRegistry.getTargetContext());
        final float instanceMultiplier = vc.getScaledAmbiguousGestureMultiplier();
        assertTrue(instanceMultiplier >= 1);
    }

    @Test
    public void testGetMaximumDrawingCacheSize() {
        Context context = InstrumentationRegistry.getTargetContext();
        ViewConfiguration vc = ViewConfiguration.get(context);
        assertNotNull(vc);

        // Should be at least the size of the screen we're supposed to draw into.
        final WindowManager win = context.getSystemService(WindowManager.class);
        final Display display = win.getDefaultDisplay();
        final Point size = new Point();
        display.getSize(size);
        assertTrue(vc.getScaledMaximumDrawingCacheSize() >= size.x * size.y * 4);

        // This deprecated value should just be what it's historically hardcoded to be.
        assertEquals(480 * 800 * 4, vc.getMaximumDrawingCacheSize());
    }

    /** Verifies whether or not the given fling thresholds are within the valid range. */
    private static void verifyFlingThresholdRange(int minVel, int maxVel) {
        if (minVel > maxVel) {
            // The only case where we expect the minimum velocity to exceed the maximum velocity is
            // for InputDevices that do not support fling, in which case the minimum and maximum
            // velocties are set to Integer's max and min values, respectively.
            verifyNoFlingThresholds(minVel, maxVel);
        } else {
            // If the minimum velocity is <= the maximum velocity, the velocities should represent
            // valid thresholds, which should not be negative values (as the thresholds are defined
            // as absolute values).
            assertTrue(minVel >= 0);
            assertTrue(maxVel >= 0);
        }
    }

    private static void verifyNoFlingThresholds(
            ViewConfiguration viewConfiguration, int deviceId, int source, int axis) {
        verifyNoFlingThresholds(
            viewConfiguration.getScaledMinimumFlingVelocity(deviceId, axis, source),
            viewConfiguration.getScaledMaximumFlingVelocity(deviceId, axis, source));
    }

    /**
     * Verifies that the given min and max fling velocities represent the values used to suppress
     * fling.
     */
    private static void verifyNoFlingThresholds(int minVel, int maxVel) {
        assertEquals(Integer.MAX_VALUE, minVel);
        assertEquals(Integer.MIN_VALUE, maxVel);
    }
}
