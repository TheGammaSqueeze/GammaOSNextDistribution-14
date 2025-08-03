/*
 * Copyright 2023 The Android Open Source Project
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

import android.companion.virtual.flags.Flags;
import android.hardware.input.VirtualStylus;
import android.hardware.input.VirtualStylusButtonEvent;
import android.hardware.input.VirtualStylusMotionEvent;
import android.hardware.input.cts.virtualcreators.VirtualInputDeviceCreator;
import android.hardware.input.cts.virtualcreators.VirtualInputEventCreator;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.MotionEvent;

import androidx.test.filters.SmallTest;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RequiresFlagsEnabled(Flags.FLAG_VIRTUAL_STYLUS)
@SmallTest
@RunWith(JUnitParamsRunner.class)
public class VirtualStylusTest extends VirtualDeviceTestCase {

    private static final String DEVICE_NAME = "CtsVirtualStylusTestDevice";

    private VirtualStylus mVirtualStylus;

    @Override
    void onSetUpVirtualInputDevice() {
        mVirtualStylus = VirtualInputDeviceCreator.createAndPrepareStylus(mVirtualDevice,
                DEVICE_NAME, mVirtualDisplay.getDisplay()).getDevice();
        // We expect to get the exact coordinates in the view that were injected using the
        // stylus. Touch resampling could result in the generation of additional "fake" touch
        // events. To disable resampling, request unbuffered dispatch.
        mTestActivity.getWindow().getDecorView().requestUnbufferedDispatch(
                InputDevice.SOURCE_STYLUS);
    }

    @Test
    public void createVirtualStylus_nullArguments_throwsException() {
        assertThrows(NullPointerException.class,
                () -> mVirtualDevice.createVirtualStylus(null));
    }

    @Parameters(method = "getAllToolTypes")
    @Test
    public void sendTouchEvents(int toolType) {
        final int x = 50;
        final int y = 50;
        // The number of move events that are sent between the down and up event.
        final int moveEventCount = 5;
        List<InputEvent> expectedEvents = new ArrayList<>(moveEventCount + 2);
        // The builder is used for all events in this test. So properties all events have in common
        // are set here.
        VirtualStylusMotionEvent.Builder builder = new VirtualStylusMotionEvent.Builder()
                .setToolType(toolType);

        // Down event
        mVirtualStylus.sendMotionEvent(builder
                .setAction(VirtualStylusMotionEvent.ACTION_DOWN)
                .setX(x)
                .setY(y)
                .setPressure(255)
                .build());
        expectedEvents.add(VirtualInputEventCreator.createStylusTouchMotionEvent(
                MotionEvent.ACTION_DOWN, x, y, toolType));

        // Next we send a bunch of ACTION_MOVE events. Each one with a different x and y coordinate.
        builder.setAction(VirtualStylusMotionEvent.ACTION_MOVE);
        for (int i = 1; i <= moveEventCount; i++) {
            builder.setX(x + i)
                    .setY(y + i)
                    .setPressure(255);
            mVirtualStylus.sendMotionEvent(builder.build());
            expectedEvents.add(VirtualInputEventCreator.createStylusTouchMotionEvent(
                    MotionEvent.ACTION_MOVE, x + i, y + i, toolType));
        }

        // Up event
        mVirtualStylus.sendMotionEvent(builder
                .setAction(VirtualStylusMotionEvent.ACTION_UP)
                .setX(x + moveEventCount)
                .setY(y + moveEventCount)
                .build());
        expectedEvents.add(VirtualInputEventCreator.createStylusTouchMotionEvent(
                MotionEvent.ACTION_UP, x + moveEventCount, y + moveEventCount, toolType));

        verifyEvents(expectedEvents);
    }

    @Parameters(method = "getAllButtonCodes")
    @Test
    public void sendTouchEvents_withButtonPressed(int buttonCode) {
        final int startX = 50, startY = 50;
        final int endX = 60, endY = 60;
        final int toolType = VirtualStylusMotionEvent.TOOL_TYPE_STYLUS;
        moveStylusWithButtonPressed(startX, startY, endX, endY, 255 /* pressure */, buttonCode,
                toolType);

        verifyEvents(Arrays.asList(
                VirtualInputEventCreator.createStylusTouchMotionEvent(
                        MotionEvent.ACTION_DOWN, startX, startY, toolType, buttonCode),
                VirtualInputEventCreator.createStylusTouchMotionEvent(
                        MotionEvent.ACTION_BUTTON_PRESS, startX, startY, toolType, buttonCode),
                VirtualInputEventCreator.createStylusTouchMotionEvent(
                        MotionEvent.ACTION_MOVE, startX, endY, toolType, buttonCode),
                VirtualInputEventCreator.createStylusTouchMotionEvent(
                        MotionEvent.ACTION_MOVE, endX, endY, toolType, buttonCode),
                VirtualInputEventCreator.createStylusTouchMotionEvent(
                        MotionEvent.ACTION_BUTTON_RELEASE, endX, endY, toolType),
                VirtualInputEventCreator.createStylusTouchMotionEvent(
                        MotionEvent.ACTION_UP, endX, endY, toolType)));
    }

    @Test
    public void sendTouchEvents_withTilt() {
        verifyStylusTouchWithTilt(0 /* tiltXDegrees */, 0 /* tiltYDegrees */,
                0 /* expectedTiltDegrees */, 0 /* expectedOrientationDegrees */);
        verifyStylusTouchWithTilt(90 /* tiltXDegrees */, 0 /* tiltYDegrees */,
                90 /* expectedTiltDegrees */, -90 /* expectedOrientationDegrees */);
        verifyStylusTouchWithTilt(-90 /* tiltXDegrees */, 0 /* tiltYDegrees */,
                90 /* expectedTiltDegrees */, 90 /* expectedOrientationDegrees */);
        verifyStylusTouchWithTilt(0 /* tiltXDegrees */, 90 /* tiltYDegrees */,
                90 /* expectedTiltDegrees */, 0 /* expectedOrientationDegrees */);
        verifyStylusTouchWithTilt(0 /* tiltXDegrees */, -90 /* tiltYDegrees */,
                90 /* expectedTiltDegrees */, -180 /* expectedOrientationDegrees */);
        verifyStylusTouchWithTilt(90 /* tiltXDegrees */, -90 /* tiltYDegrees */,
                90 /* expectedTiltDegrees */, -135 /* expectedOrientationDegrees */);
        verifyStylusTouchWithTilt(90 /* tiltXDegrees */, 90 /* tiltYDegrees */,
                90 /* expectedTiltDegrees */, -45 /* expectedOrientationDegrees */);
        verifyStylusTouchWithTilt(-90 /* tiltXDegrees */, 90 /* tiltYDegrees */,
                90 /* expectedTiltDegrees */, 45 /* expectedOrientationDegrees */);
        verifyStylusTouchWithTilt(-90 /* tiltXDegrees */, -90 /* tiltYDegrees */,
                90 /* expectedTiltDegrees */, 135 /* expectedOrientationDegrees */);
    }

    @Parameters(method = "getAllToolTypes")
    @Test
    public void sendHoverEvents(int toolType) {
        final int x0 = 50, y0 = 50;
        final int x1 = 60, y1 = 60;
        final int pressure = 0;

        sendMotionEvent(VirtualStylusMotionEvent.ACTION_DOWN, x0, y0, pressure, toolType);
        sendMotionEvent(VirtualStylusMotionEvent.ACTION_MOVE, x0, y1, pressure, toolType);
        sendMotionEvent(VirtualStylusMotionEvent.ACTION_MOVE, x1, y1, pressure, toolType);
        sendMotionEvent(VirtualStylusMotionEvent.ACTION_UP, x1, y1, pressure, toolType);

        verifyEvents(Arrays.asList(
                VirtualInputEventCreator.createStylusHoverMotionEvent(
                        MotionEvent.ACTION_HOVER_ENTER, x0, y0, toolType),
                VirtualInputEventCreator.createStylusHoverMotionEvent(
                        MotionEvent.ACTION_HOVER_MOVE, x0, y0, toolType),
                VirtualInputEventCreator.createStylusHoverMotionEvent(
                        MotionEvent.ACTION_HOVER_MOVE, x0, y1, toolType),
                VirtualInputEventCreator.createStylusHoverMotionEvent(
                        MotionEvent.ACTION_HOVER_MOVE, x1, y1, toolType),
                VirtualInputEventCreator.createStylusHoverMotionEvent(
                        MotionEvent.ACTION_HOVER_EXIT, x1, y1, toolType)));
    }

    @Parameters(method = "getAllButtonCodes")
    @Test
    public void sendHoverEvents_withButtonAlwaysPressed(int buttonCode) {
        final int startX = 60, startY = 60;
        final int endX = 50, endY = 50;
        final int toolType = VirtualStylusMotionEvent.TOOL_TYPE_STYLUS;
        moveStylusWithButtonPressed(startX, startY, endX, endY, 0 /* pressure */, buttonCode,
                toolType);

        verifyEvents(Arrays.asList(
                VirtualInputEventCreator.createStylusHoverMotionEvent(
                        MotionEvent.ACTION_HOVER_ENTER, startX, startY, toolType, buttonCode),
                VirtualInputEventCreator.createStylusHoverMotionEvent(
                        MotionEvent.ACTION_HOVER_MOVE, startX, startY, toolType, buttonCode),
                VirtualInputEventCreator.createStylusHoverMotionEvent(
                        MotionEvent.ACTION_HOVER_MOVE, startX, endY, toolType, buttonCode),
                VirtualInputEventCreator.createStylusHoverMotionEvent(
                        MotionEvent.ACTION_HOVER_MOVE, endX, endY, toolType, buttonCode),
                VirtualInputEventCreator.createStylusHoverMotionEvent(
                        MotionEvent.ACTION_HOVER_EXIT, endX, endY, toolType, buttonCode)));
    }

    @Parameters(method = "getAllButtonCodes")
    @Test
    public void stylusButtonPressRelease_withoutHoverOrTouch(int buttonCode) {
        mVirtualStylus.sendButtonEvent(new VirtualStylusButtonEvent.Builder()
                .setAction(VirtualStylusButtonEvent.ACTION_BUTTON_PRESS)
                .setButtonCode(buttonCode)
                .build());
        mVirtualStylus.sendButtonEvent(new VirtualStylusButtonEvent.Builder()
                .setAction(VirtualStylusButtonEvent.ACTION_BUTTON_RELEASE)
                .setButtonCode(buttonCode)
                .build());

        assertNoMoreEvents();
    }

    @Test
    public void sendTouchEvent_withoutCreateVirtualDevicePermission_throwsException() {
        final int x = 50;
        final int y = 50;
        mRule.runWithoutPermissions(
                () -> assertThrows(SecurityException.class,
                        () -> mVirtualStylus.sendMotionEvent(new VirtualStylusMotionEvent.Builder()
                                .setAction(VirtualStylusMotionEvent.ACTION_DOWN)
                                .setX(x)
                                .setY(y)
                                .setPressure(255)
                                .setToolType(VirtualStylusMotionEvent.TOOL_TYPE_STYLUS)
                                .build())));
    }

    private void verifyStylusTouchWithTilt(int tiltXDegrees, int tiltYDegrees,
            int expectedTiltDegrees, int expectedOrientationDegrees) {
        final int x0 = 60, y0 = 60;
        final int x1 = 50, y1 = 50;
        final int pressure = 255;
        final int toolType = VirtualStylusMotionEvent.TOOL_TYPE_STYLUS;

        sendMotionEvent(VirtualStylusMotionEvent.ACTION_DOWN, x0, y0, pressure, toolType,
                tiltXDegrees, tiltYDegrees);
        sendMotionEvent(VirtualStylusMotionEvent.ACTION_MOVE, x0, y1, pressure, toolType,
                tiltXDegrees, tiltYDegrees);
        sendMotionEvent(VirtualStylusMotionEvent.ACTION_MOVE, x1, y1, pressure, toolType,
                tiltXDegrees, tiltYDegrees);
        sendMotionEvent(VirtualStylusMotionEvent.ACTION_UP, x1, y1, pressure, toolType,
                tiltXDegrees, tiltYDegrees);

        final float expectedTiltRadians = (float) Math.toRadians(expectedTiltDegrees);
        final float expectedOrientationRadians = (float) Math.toRadians(expectedOrientationDegrees);
        verifyEvents(Arrays.asList(
                VirtualInputEventCreator.createStylusTouchMotionEvent(MotionEvent.ACTION_DOWN,
                        x0, y0, toolType, expectedTiltRadians, expectedOrientationRadians),
                VirtualInputEventCreator.createStylusTouchMotionEvent(MotionEvent.ACTION_MOVE,
                        x0, y1, toolType, expectedTiltRadians, expectedOrientationRadians),
                VirtualInputEventCreator.createStylusTouchMotionEvent(MotionEvent.ACTION_MOVE,
                        x1, y1, toolType, expectedTiltRadians, expectedOrientationRadians),
                VirtualInputEventCreator.createStylusTouchMotionEvent(MotionEvent.ACTION_UP,
                        x1, y1, toolType, expectedTiltRadians, expectedOrientationRadians)));
    }

    private void moveStylusWithButtonPressed(int startX, int startY, int endX, int endY,
            int pressure, int buttonCode, int toolType) {
        mVirtualStylus.sendButtonEvent(new VirtualStylusButtonEvent.Builder()
                .setAction(VirtualStylusButtonEvent.ACTION_BUTTON_PRESS)
                .setButtonCode(buttonCode)
                .build());
        sendMotionEvent(VirtualStylusMotionEvent.ACTION_DOWN, startX, startY, pressure, toolType);
        sendMotionEvent(VirtualStylusMotionEvent.ACTION_MOVE, startX, endY, pressure, toolType);
        sendMotionEvent(VirtualStylusMotionEvent.ACTION_MOVE, endX, endY, pressure, toolType);
        sendMotionEvent(VirtualStylusMotionEvent.ACTION_UP, endX, endY, pressure, toolType);
        mVirtualStylus.sendButtonEvent(new VirtualStylusButtonEvent.Builder()
                .setAction(VirtualStylusButtonEvent.ACTION_BUTTON_RELEASE)
                .setButtonCode(buttonCode)
                .build());
    }

    private void sendMotionEvent(int action, int x, int y, int pressure, int toolType,
            int tiltX, int tiltY) {
        mVirtualStylus.sendMotionEvent(new VirtualStylusMotionEvent.Builder()
                .setAction(action)
                .setToolType(toolType)
                .setX(x)
                .setY(y)
                .setTiltX(tiltX)
                .setTiltY(tiltY)
                .setPressure(pressure)
                .build());
    }

    private void sendMotionEvent(int action, int x, int y, int pressure, int toolType) {
        sendMotionEvent(action, x, y, pressure, toolType, 0 /* tiltX */, 0 /* tiltY */);
    }

    private static Integer[] getAllButtonCodes() {
        return new Integer[] {
                VirtualStylusButtonEvent.BUTTON_PRIMARY,
                VirtualStylusButtonEvent.BUTTON_SECONDARY,
        };
    }

    private static Integer[] getAllToolTypes() {
        return new Integer[] {
                VirtualStylusMotionEvent.TOOL_TYPE_STYLUS,
                VirtualStylusMotionEvent.TOOL_TYPE_ERASER,
        };
    }
}
