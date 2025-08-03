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

import static org.junit.Assert.assertThrows;

import android.hardware.input.VirtualNavigationTouchpad;
import android.hardware.input.VirtualTouchEvent;
import android.hardware.input.cts.virtualcreators.VirtualInputDeviceCreator;
import android.hardware.input.cts.virtualcreators.VirtualInputEventCreator;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

import androidx.test.filters.FlakyTest;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class VirtualNavigationTouchpadTest extends VirtualDeviceTestCase {

    private static final String DEVICE_NAME = "CtsVirtualNavigationTouchpadTestDevice";
    private static final int TOUCHPAD_HEIGHT = 50;
    private static final int TOUCHPAD_WIDTH = 50;

    private VirtualNavigationTouchpad mVirtualNavigationTouchpad;

    @Override
    void onSetUpVirtualInputDevice() {
        mVirtualNavigationTouchpad = VirtualInputDeviceCreator.createAndPrepareNavigationTouchpad(
                mVirtualDevice, DEVICE_NAME, mVirtualDisplay.getDisplay(), TOUCHPAD_WIDTH,
                TOUCHPAD_HEIGHT).getDevice();
    }

    @Test
    public void sendTouchEvent() {
        final float inputSize = 1f;
        final float x = 30f;
        final float y = 30f;
        mVirtualNavigationTouchpad.sendTouchEvent(new VirtualTouchEvent.Builder()
                .setAction(VirtualTouchEvent.ACTION_DOWN)
                .setPointerId(1)
                .setX(x)
                .setY(y)
                .setPressure(255f)
                .setMajorAxisSize(inputSize)
                .setToolType(VirtualTouchEvent.TOOL_TYPE_FINGER)
                .build());
        mVirtualNavigationTouchpad.sendTouchEvent(new VirtualTouchEvent.Builder()
                .setAction(VirtualTouchEvent.ACTION_UP)
                .setPointerId(1)
                .setX(x)
                .setY(y)
                .setToolType(VirtualTouchEvent.TOOL_TYPE_FINGER)
                .build());
        // Convert the input axis size to its equivalent fraction of the total touchpad size.
        final float computedSize = inputSize / (TOUCHPAD_WIDTH - 1f);

        verifyEvents(Arrays.asList(
                VirtualInputEventCreator.createNavigationTouchpadMotionEvent(
                        MotionEvent.ACTION_DOWN, x, y, computedSize /* size */,
                        inputSize /* axisSize */),
                VirtualInputEventCreator.createNavigationTouchpadMotionEvent(MotionEvent.ACTION_UP,
                        x, y, computedSize /* size */, inputSize /* axisSize */)));
    }

    @Test
    public void sendTouchEvent_withoutCreateVirtualDevicePermission_throwsException() {
        final float inputSize = 1f;
        final float x = 30f;
        final float y = 30f;
        mRule.runWithoutPermissions(
                () -> assertThrows(SecurityException.class,
                        () -> mVirtualNavigationTouchpad.sendTouchEvent(
                                new VirtualTouchEvent.Builder()
                                        .setAction(VirtualTouchEvent.ACTION_DOWN)
                                        .setPointerId(1)
                                        .setX(x)
                                        .setY(y)
                                        .setPressure(255f)
                                        .setMajorAxisSize(inputSize)
                                        .setToolType(VirtualTouchEvent.TOOL_TYPE_FINGER)
                                        .build())));
    }

    @Test
    public void createVirtualNavigationTouchpad_nullArguments_throwsException() {
        assertThrows(NullPointerException.class,
                () -> mVirtualDevice.createVirtualNavigationTouchpad(null));
    }

    @Test
    public void sendTap_motionEventNotConsumed_getsConvertedToDpadCenter() {
        setConsumeGenericMotionEvents(false);

        final float x = 30f;
        final float y = 30f;
        mVirtualNavigationTouchpad.sendTouchEvent(new VirtualTouchEvent.Builder()
                .setAction(VirtualTouchEvent.ACTION_DOWN)
                .setPointerId(1)
                .setX(x)
                .setY(y)
                .setPressure(255f)
                .setToolType(VirtualTouchEvent.TOOL_TYPE_FINGER)
                .build());
        mVirtualNavigationTouchpad.sendTouchEvent(new VirtualTouchEvent.Builder()
                .setAction(VirtualTouchEvent.ACTION_UP)
                .setPointerId(1)
                .setX(x)
                .setY(y)
                .setToolType(VirtualTouchEvent.TOOL_TYPE_FINGER)
                .build());

        verifyEvents(Arrays.asList(
                createKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER),
                createKeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER)));
    }

    @FlakyTest(detail = "The test does not reliably simulate a fling action, only way to reliably"
            + "do it is when uinput supports custom timestamps for virtual input events.",
            bugId = 277040837)
    @Test
    public void sendFlingUp_motionEventNotConsumed_getsConvertedToDpadUp() {
        setConsumeGenericMotionEvents(false);

        sendFlingEvents(30f /* startX */, 30f /* startY */, -10f /* diffX */, -30f /* diffY */);

        verifyEvents(Arrays.asList(
                        createKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP),
                        createKeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_UP)));
    }

    @FlakyTest(detail = "The test does not reliably simulate a fling action, only way to reliably"
            + "do it is when uinput supports custom timestamps for virtual input events.",
            bugId = 277040837)
    @Test
    public void sendFlingDown_motionEventNotConsumed_getsConvertedToDpadDown() {
        setConsumeGenericMotionEvents(false);

        sendFlingEvents(30f /* startX */, 10f /* startY */, 10f /* diffX */, 30f /* diffY */);

        verifyEvents(Arrays.asList(
                        createKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN),
                        createKeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_DOWN)));
    }

    @FlakyTest(detail = "The test does not reliably simulate a fling action, only way to reliably"
            + "do it is when uinput supports custom timestamps for virtual input events.",
            bugId = 277040837)
    @Test
    public void sendFlingRight_motionEventNotConsumed_getsConvertedToDpadRight() {
        setConsumeGenericMotionEvents(false);

        sendFlingEvents(10f /* startX */, 30f /* startY */, 30f /* diffX */, 10f /* diffY */);

        verifyEvents(Arrays.asList(
                        createKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT),
                        createKeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT)));
    }

    @FlakyTest(detail = "The test does not reliably simulate a fling action, only way to reliably"
            + "do it is when uinput supports custom timestamps for virtual input events.",
            bugId = 277040837)
    @Test
    public void sendFlingLeft_motionEventNotConsumed_getsConvertedToDpadLeft() {
        setConsumeGenericMotionEvents(false);

        sendFlingEvents(30f /* startX */, 30f /* startY */, -30f /* diffX */, 10f /* diffY */);

        verifyEvents(Arrays.asList(
                        createKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT),
                        createKeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_LEFT)));
    }

    @Test
    public void sendLongPress_motionEventNotConsumed_getsIgnored() {
        setConsumeGenericMotionEvents(false);

        float x = 30f;
        float y = 30f;
        mVirtualNavigationTouchpad.sendTouchEvent(new VirtualTouchEvent.Builder()
                .setAction(VirtualTouchEvent.ACTION_DOWN)
                .setPointerId(1)
                .setX(x)
                .setY(y)
                .setPressure(255f)
                .setToolType(VirtualTouchEvent.TOOL_TYPE_FINGER)
                .build());
        // TODO(b/277040837): Use custom timestamps for virtual input events instead of sleep.
        SystemClock.sleep(600);
        mVirtualNavigationTouchpad.sendTouchEvent(new VirtualTouchEvent.Builder()
                .setAction(VirtualTouchEvent.ACTION_UP)
                .setPointerId(1)
                .setX(x)
                .setY(y)
                .setToolType(VirtualTouchEvent.TOOL_TYPE_FINGER)
                .build());

        verifyNoKeyEvents();
    }

    @Test
    public void sendSlowScroll_motionEventNotConsumed_getsIgnored() {
        setConsumeGenericMotionEvents(false);

        sendContinuousEvents(30f /* startX */, 30f /* startY */, 2f /* diffX */, 1f /* diffY */,
                300 /* eventTimeGapMs */);

        verifyNoKeyEvents();
    }

    private void sendFlingEvents(float startX, float startY, float diffX, float diffY) {
        sendContinuousEvents(startX, startY, diffX, diffY, 7 /* eventTimeGapMs */);
    }

    private void sendContinuousEvents(float startX, float startY, float diffX, float diffY,
            long eventTimeGapMs) {
        int eventCount = 4;
        // Starts with ACTION_DOWN.
        mVirtualNavigationTouchpad.sendTouchEvent(new VirtualTouchEvent.Builder()
                .setAction(VirtualTouchEvent.ACTION_DOWN)
                .setPointerId(1)
                .setX(startX)
                .setY(startY)
                .setPressure(255f)
                .setToolType(VirtualTouchEvent.TOOL_TYPE_FINGER)
                .build());
        SystemClock.sleep(eventTimeGapMs);

        for (int i = 1; i <= eventCount; i++) {
            mVirtualNavigationTouchpad.sendTouchEvent(new VirtualTouchEvent.Builder()
                    .setAction(VirtualTouchEvent.ACTION_MOVE)
                    .setPointerId(1)
                    .setX(startX + i * diffX / eventCount)
                    .setY(startY + i * diffY / eventCount)
                    .setPressure(255f)
                    .setToolType(VirtualTouchEvent.TOOL_TYPE_FINGER)
                    .build());
            SystemClock.sleep(eventTimeGapMs);
        }

        // Ends with ACTION_UP.
        mVirtualNavigationTouchpad.sendTouchEvent(new VirtualTouchEvent.Builder()
                .setAction(VirtualTouchEvent.ACTION_UP)
                .setPointerId(1)
                .setX(startX + diffX)
                .setY(startY + diffY)
                .setToolType(VirtualTouchEvent.TOOL_TYPE_FINGER)
                .build());
    }

    private KeyEvent createKeyEvent(int action, int code) {
        KeyEvent event = new KeyEvent(action, code);
        event.setSource(InputDevice.SOURCE_TOUCH_NAVIGATION);
        event.setDisplayId(mVirtualDisplay.getDisplay().getDisplayId());
        return event;
    }
}
