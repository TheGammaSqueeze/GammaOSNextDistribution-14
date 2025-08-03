/*
 * Copyright 2024 The Android Open Source Project
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

package android.view.cts.input;

import static com.android.input.flags.Flags.FLAG_INPUT_DEVICE_VIEW_BEHAVIOR_API;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.app.Instrumentation;
import android.hardware.input.InputManager;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.cts.util.InputDeviceIterators;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test {@link android.view.InputDevice.ViewBehavior} functionality.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class InputDeviceViewBehaviorTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private InputManager mInputManager;

    @Before
    public void setup() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        mInputManager = instrumentation.getTargetContext().getSystemService(InputManager.class);
        assertNotNull(mInputManager);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_INPUT_DEVICE_VIEW_BEHAVIOR_API)
    public void testShouldSmoothScroll_onValidAxisSourceCombo_throwsNoException() {
        InputDeviceIterators.iteratorOverEveryInputDeviceMotionRange((deviceId, motionRange) -> {
            InputDevice.ViewBehavior viewBehavior =
                    mInputManager.getInputDeviceViewBehavior(deviceId);
            assertNotNull(viewBehavior);

            viewBehavior.shouldSmoothScroll(motionRange.getAxis(), motionRange.getSource());
        });
    }

    @Test
    @RequiresFlagsEnabled(FLAG_INPUT_DEVICE_VIEW_BEHAVIOR_API)
    public void testShouldSmoothScroll_onInvalidAxisSourceCombo_returnsFalse() {
        InputDeviceIterators.iteratorOverEveryValidDeviceId((deviceId) -> {
            InputDevice.ViewBehavior viewBehavior =
                    mInputManager.getInputDeviceViewBehavior(deviceId);
            // Test with source-axis combinations that we know are not valid. Since the
            // source-axis combinations will be invalid, we expect false return values,
            // as per the API's documentation.
            assertFalse(viewBehavior.shouldSmoothScroll(
                    MotionEvent.AXIS_X, InputDevice.SOURCE_ROTARY_ENCODER));
            assertFalse(
                    viewBehavior.shouldSmoothScroll(MotionEvent.AXIS_SCROLL,
                    InputDevice.SOURCE_TOUCHSCREEN));
        });
    }

    @Test
    @RequiresFlagsEnabled(FLAG_INPUT_DEVICE_VIEW_BEHAVIOR_API)
    public void testGetViewBehavior_forValidDeviceId_returnsNonNull() {
        InputDeviceIterators.iteratorOverEveryValidDeviceId((deviceId) -> {
            assertNotNull(mInputManager.getInputDeviceViewBehavior(deviceId));
        });
    }

    @Test
    @RequiresFlagsEnabled(FLAG_INPUT_DEVICE_VIEW_BEHAVIOR_API)
    public void testGetViewBehavior_forInvalidDeviceId_returnsNull() {
        InputDeviceIterators.iteratorOverInvalidDeviceIds((deviceId) -> {
            assertNull(mInputManager.getInputDeviceViewBehavior(deviceId));
        });
    }
}
