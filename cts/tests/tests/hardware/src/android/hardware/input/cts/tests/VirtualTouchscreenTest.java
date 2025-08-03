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

package android.hardware.input.cts.tests;

import static android.view.InputDevice.SOURCE_TOUCHSCREEN;

import static org.junit.Assert.assertThrows;

import android.hardware.input.VirtualTouchEvent;
import android.hardware.input.VirtualTouchscreen;
import android.hardware.input.cts.virtualcreators.VirtualInputDeviceCreator;
import android.hardware.input.cts.virtualcreators.VirtualInputEventCreator;
import android.view.InputEvent;
import android.view.MotionEvent;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class VirtualTouchscreenTest extends VirtualDeviceTestCase {

    private static final String DEVICE_NAME = "CtsVirtualTouchscreenTestDevice";

    private VirtualTouchscreen mVirtualTouchscreen;

    @Override
    void onSetUpVirtualInputDevice() {
        mVirtualTouchscreen = VirtualInputDeviceCreator.createAndPrepareTouchscreen(
                mVirtualDevice, DEVICE_NAME, mVirtualDisplay.getDisplay()).getDevice();
    }
    @Test
    public void sendTouchEvent() {
        final float inputSize = 1f;
        // Convert the input axis size to its equivalent fraction of the total screen.
        final float computedSize = inputSize
                / (mVirtualDisplay.getDisplay().getMode().getPhysicalWidth() - 1f);
        final float x = 50f;
        final float y = 50f;

        // The number of move events that are sent between the down and up event.
        int moveEventCount = 5;
        List<InputEvent> expectedEvents = new ArrayList<>(moveEventCount + 2);
        // The builder is used for all events in this test. So properties all events have in common
        // are set here.
        VirtualTouchEvent.Builder builder = new VirtualTouchEvent.Builder()
                .setPointerId(1)
                .setMajorAxisSize(inputSize)
                .setToolType(VirtualTouchEvent.TOOL_TYPE_FINGER);

        // Down event
        mVirtualTouchscreen.sendTouchEvent(builder
                .setAction(VirtualTouchEvent.ACTION_DOWN)
                .setX(x)
                .setY(y)
                .setPressure(255f)
                .build());
        expectedEvents.add(
                VirtualInputEventCreator.createTouchscreenEvent(MotionEvent.ACTION_DOWN, x, y,
                        1f /* pressure */, computedSize, inputSize));

        // We expect to get the exact coordinates in the view that were injected into the
        // touchscreen. Touch resampling could result in the generation of additional "fake" touch
        // events. To disable resampling, request unbuffered dispatch.
        mTestActivity.getWindow().getDecorView().requestUnbufferedDispatch(SOURCE_TOUCHSCREEN);

        // Next we send a bunch of ACTION_MOVE events. Each one with a different x and y coordinate.
        // If no property changes (i.e. the same VirtualTouchEvent is sent multiple times) then the
        // kernel drops the event as there is no point in delivering a new event if nothing changed.
        builder.setAction(VirtualTouchEvent.ACTION_MOVE);
        for (int i = 1; i <= moveEventCount; i++) {
            builder.setX(x + i)
                    .setY(y + i)
                    .setPressure(255f);
            mVirtualTouchscreen.sendTouchEvent(builder.build());
            expectedEvents.add(
                    VirtualInputEventCreator.createTouchscreenEvent(MotionEvent.ACTION_MOVE,
                            x + i, y + i, 1f /* pressure */, computedSize, inputSize));
        }

        mVirtualTouchscreen.sendTouchEvent(builder
                .setAction(VirtualTouchEvent.ACTION_UP)
                .setX(x + moveEventCount)
                .setY(y + moveEventCount)
                .build());
        expectedEvents.add(
                VirtualInputEventCreator.createTouchscreenEvent(MotionEvent.ACTION_UP,
                        x + moveEventCount, y + moveEventCount, 1f /* pressure */,
                        computedSize, inputSize));

        verifyEvents(expectedEvents);
    }

    @Test
    public void sendHoverEvents() {
        final float x0 = 50f, y0 = 50f;
        final float x1 = 60f, y1 = 60f;

        sendHoverEvent(VirtualTouchEvent.ACTION_DOWN, x0, y0);
        sendHoverEvent(VirtualTouchEvent.ACTION_MOVE, x0, y1);
        sendHoverEvent(VirtualTouchEvent.ACTION_MOVE, x1, y1);
        sendHoverEvent(VirtualTouchEvent.ACTION_UP, x1, y1);

        verifyEvents(Arrays.asList(
                createMotionEvent(MotionEvent.ACTION_HOVER_ENTER, x0, y0),
                createMotionEvent(MotionEvent.ACTION_HOVER_MOVE, x0, y0),
                createMotionEvent(MotionEvent.ACTION_HOVER_MOVE, x0, y1),
                createMotionEvent(MotionEvent.ACTION_HOVER_MOVE, x1, y1),
                createMotionEvent(MotionEvent.ACTION_HOVER_EXIT, x1, y1)));
    }

    @Test
    public void sendTouchEvent_withoutCreateVirtualDevicePermission_throwsException() {
        final float inputSize = 1f;
        final float x = 50f;
        final float y = 50f;
        mRule.runWithoutPermissions(
                () -> assertThrows(SecurityException.class,
                        () -> mVirtualTouchscreen.sendTouchEvent(new VirtualTouchEvent.Builder()
                                .setAction(VirtualTouchEvent.ACTION_DOWN)
                                .setPointerId(1)
                                .setX(x)
                                .setY(y)
                                .setPressure(255f)
                                .setMajorAxisSize(inputSize)
                                .setToolType(VirtualTouchEvent.TOOL_TYPE_FINGER)
                                .build())));
    }

    private void sendHoverEvent(int action, float x, float y) {
        mVirtualTouchscreen.sendTouchEvent(new VirtualTouchEvent.Builder()
                .setAction(action)
                .setPointerId(1)
                .setX(x)
                .setY(y)
                .setPressure(0f)
                .setToolType(VirtualTouchEvent.TOOL_TYPE_FINGER)
                .build());
    }

    private MotionEvent createMotionEvent(int action, float x, float y) {
        return VirtualInputEventCreator.createTouchscreenEvent(action, x, y, 0f /* pressure */,
                0f /* size */, 0f /* axisSize */);
    }

    @Test
    public void createVirtualTouchscreen_nullArguments_throwsException() {
        assertThrows(NullPointerException.class,
                () -> mVirtualDevice.createVirtualTouchscreen(null));
    }
}
