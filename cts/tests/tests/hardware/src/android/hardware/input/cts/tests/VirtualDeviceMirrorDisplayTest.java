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

import static org.junit.Assert.assertEquals;

import android.companion.virtual.VirtualDeviceManager.VirtualDevice;
import android.companion.virtual.flags.Flags;
import android.content.Context;
import android.graphics.PointF;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.hardware.input.VirtualDpad;
import android.hardware.input.VirtualKeyEvent;
import android.hardware.input.VirtualKeyboard;
import android.hardware.input.VirtualMouse;
import android.hardware.input.VirtualMouseButtonEvent;
import android.hardware.input.VirtualMouseRelativeEvent;
import android.hardware.input.VirtualMouseScrollEvent;
import android.hardware.input.VirtualNavigationTouchpad;
import android.hardware.input.VirtualStylus;
import android.hardware.input.VirtualStylusMotionEvent;
import android.hardware.input.VirtualTouchEvent;
import android.hardware.input.VirtualTouchscreen;
import android.hardware.input.cts.virtualcreators.VirtualInputDeviceCreator;
import android.hardware.input.cts.virtualcreators.VirtualInputEventCreator;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.server.wm.WindowManagerStateHelper;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.virtualdevice.cts.common.VirtualDeviceRule;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.cts.input.DefaultPointerSpeedRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RequiresFlagsEnabled({Flags.FLAG_INTERACTIVE_SCREEN_MIRROR, Flags.FLAG_CONSISTENT_DISPLAY_FLAGS})
@SmallTest
@RunWith(AndroidJUnit4.class)
public class VirtualDeviceMirrorDisplayTest extends InputTestCase {
    private static final String DEVICE_NAME = "automirror-inputdevice";
    private static final float EPSILON = 0.001f;

    @Rule
    public final VirtualDeviceRule mRule = VirtualDeviceRule.createDefault();
    @Rule
    public final DefaultPointerSpeedRule mDefaultPointerSpeedRule = new DefaultPointerSpeedRule();

    private VirtualDevice mVirtualDevice;
    private VirtualDisplay mVirtualDisplay;
    private int mDisplayWidth;
    private int mDisplayHeight;

    @Override
    void onSetUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        WindowManager windowManager = context.getSystemService(WindowManager.class);
        mDisplayWidth = windowManager.getCurrentWindowMetrics().getBounds().width();
        mDisplayHeight = windowManager.getCurrentWindowMetrics().getBounds().height();
        mVirtualDevice = mRule.createManagedVirtualDevice();
        mVirtualDisplay = mRule.createManagedVirtualDisplay(mVirtualDevice,
                VirtualDeviceRule.createDefaultVirtualDisplayConfigBuilder(
                        mDisplayWidth, mDisplayHeight)
                        .setFlags(DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                                | DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR)
                       .build());
        mRule.assumeActivityLaunchSupported(mVirtualDisplay.getDisplay().getDisplayId());
        // Wait for any pending transitions
        WindowManagerStateHelper windowManagerStateHelper = new WindowManagerStateHelper();
        windowManagerStateHelper.waitForAppTransitionIdleOnDisplay(mTestActivity.getDisplayId());
        mInstrumentation.getUiAutomation().syncInputTransactions();
    }

    @Override
    void onTearDown() {}

    @Test
    public void virtualMouse_scrollEvent() {
        VirtualMouse mouse = VirtualInputDeviceCreator.createAndPrepareMouse(mVirtualDevice,
                DEVICE_NAME, mVirtualDisplay.getDisplay()).getDevice();
        final PointF startPosition = mouse.getCursorPosition();
        final float moveX = 0f;
        final float moveY = 1f;
        mouse.sendScrollEvent(new VirtualMouseScrollEvent.Builder()
                .setYAxisMovement(moveY)
                .setXAxisMovement(moveX)
                .build());
        // Verify that events have been received on the activity running on default display.
        verifyEvents(Arrays.asList(
                VirtualInputEventCreator.createMouseEvent(MotionEvent.ACTION_HOVER_ENTER,
                        startPosition.x, startPosition.y, 0 /* buttonState */,
                        0f /* pressure */),
                VirtualInputEventCreator.createMouseEvent(MotionEvent.ACTION_SCROLL,
                        startPosition.x, startPosition.y, 0 /* buttonState */,
                        0f /* pressure */, 0f /* relativeX */, 0f /* relativeY */,
                        1f /* vScroll */)));
    }

    @Test
    public void virtualMouse_relativeEvent() {
        VirtualMouse mouse = VirtualInputDeviceCreator.createAndPrepareMouse(mVirtualDevice,
                DEVICE_NAME, mVirtualDisplay.getDisplay()).getDevice();
        final PointF startPosition = mouse.getCursorPosition();
        final float relativeChangeX = 25f;
        final float relativeChangeY = 35f;
        mouse.sendRelativeEvent(new VirtualMouseRelativeEvent.Builder()
                .setRelativeY(relativeChangeY)
                .setRelativeX(relativeChangeX)
                .build());
        final float firstStopPositionX = startPosition.x + relativeChangeX;
        final float firstStopPositionY = startPosition.y + relativeChangeY;
        verifyEvents(Arrays.asList(
                VirtualInputEventCreator.createMouseEvent(MotionEvent.ACTION_HOVER_ENTER,
                        firstStopPositionX, firstStopPositionY, 0 /* buttonState */,
                        0f /* pressure */, relativeChangeX, relativeChangeY,
                        0f /* vScroll */)));
        final PointF cursorPosition1 = mouse.getCursorPosition();
        assertEquals("getCursorPosition() should return the updated x position",
                firstStopPositionX, cursorPosition1.x, EPSILON);
        assertEquals("getCursorPosition() should return the updated y position",
                firstStopPositionY, cursorPosition1.y, EPSILON);

        final float secondStopPositionX = firstStopPositionX - relativeChangeX;
        final float secondStopPositionY = firstStopPositionY - relativeChangeY;
        mouse.sendRelativeEvent(new VirtualMouseRelativeEvent.Builder()
                .setRelativeY(-relativeChangeY)
                .setRelativeX(-relativeChangeX)
                .build());
        verifyEvents(Arrays.asList(
                VirtualInputEventCreator.createMouseEvent(MotionEvent.ACTION_HOVER_MOVE,
                        secondStopPositionX, secondStopPositionY, 0 /* buttonState */,
                        0f /* pressure */, -relativeChangeX, -relativeChangeY,
                        0f /* vScroll */)));
        final PointF cursorPosition2 = mouse.getCursorPosition();
        assertEquals("getCursorPosition() should return the updated x position",
                secondStopPositionX, cursorPosition2.x, EPSILON);
        assertEquals("getCursorPosition() should return the updated y position",
                secondStopPositionY, cursorPosition2.y, EPSILON);
    }

    @Test
    public void virtualMouse_buttonEvent() {
        VirtualMouse mouse = VirtualInputDeviceCreator.createAndPrepareMouse(mVirtualDevice,
                DEVICE_NAME, mVirtualDisplay.getDisplay()).getDevice();
        final PointF startPosition = mouse.getCursorPosition();
        mouse.sendButtonEvent(new VirtualMouseButtonEvent.Builder()
                .setAction(VirtualMouseButtonEvent.ACTION_BUTTON_PRESS)
                .setButtonCode(VirtualMouseButtonEvent.BUTTON_PRIMARY)
                .build());
        mouse.sendButtonEvent(new VirtualMouseButtonEvent.Builder()
                .setAction(VirtualMouseButtonEvent.ACTION_BUTTON_RELEASE)
                .setButtonCode(VirtualMouseButtonEvent.BUTTON_PRIMARY)
                .build());
        final MotionEvent buttonPressEvent = VirtualInputEventCreator.createMouseEvent(
                MotionEvent.ACTION_BUTTON_PRESS, startPosition.x, startPosition.y,
                MotionEvent.BUTTON_PRIMARY, 1f /* pressure */);
        buttonPressEvent.setActionButton(MotionEvent.BUTTON_PRIMARY);
        final MotionEvent buttonReleaseEvent = VirtualInputEventCreator.createMouseEvent(
                MotionEvent.ACTION_BUTTON_RELEASE, startPosition.x, startPosition.y,
                0 /* buttonState */, 0f /* pressure */);
        buttonReleaseEvent.setActionButton(MotionEvent.BUTTON_PRIMARY);
        verifyEvents(Arrays.asList(
                VirtualInputEventCreator.createMouseEvent(MotionEvent.ACTION_DOWN,
                        startPosition.x, startPosition.y, MotionEvent.BUTTON_PRIMARY,
                        1f /* pressure */),
                buttonPressEvent,
                buttonReleaseEvent,
                VirtualInputEventCreator.createMouseEvent(MotionEvent.ACTION_UP,
                        startPosition.x, startPosition.y, 0 /* buttonState */,
                        0f /* pressure */),
                VirtualInputEventCreator.createMouseEvent(MotionEvent.ACTION_HOVER_ENTER,
                        startPosition.x, startPosition.y, 0 /* buttonState */,
                        0f /* pressure */)));
    }

    @Test
    public void virtualTouchscreen_touchEvent() {
        VirtualTouchscreen touchscreen = VirtualInputDeviceCreator.createAndPrepareTouchscreen(
                mVirtualDevice, DEVICE_NAME, mVirtualDisplay.getDisplay()).getDevice();
        final float inputSize = 1f;
        // Convert the input axis size to its equivalent fraction of the total screen.
        final float computedSize = inputSize / (mDisplayWidth - 1f);
        final float x = mDisplayWidth / 2f;
        final float y = mDisplayHeight / 2f;

        // The number of move events that are sent between the down and up event.
        final int moveEventCount = 5;
        List<InputEvent> expectedEvents = new ArrayList<>(moveEventCount + 2);
        // The builder is used for all events in this test. So properties all events have in
        // common are set here.
        VirtualTouchEvent.Builder builder = new VirtualTouchEvent.Builder()
                .setPointerId(1)
                .setMajorAxisSize(inputSize)
                .setToolType(VirtualTouchEvent.TOOL_TYPE_FINGER);

        // Down event
        touchscreen.sendTouchEvent(builder
                .setAction(VirtualTouchEvent.ACTION_DOWN)
                .setX(x)
                .setY(y)
                .setPressure(255f)
                .build());
        expectedEvents.add(
                VirtualInputEventCreator.createTouchscreenEvent(MotionEvent.ACTION_DOWN, x, y,
                        1f /* pressure */, computedSize, inputSize));

        // We expect to get the exact coordinates in the view that were injected into the
        // touchscreen. Touch resampling could result in the generation of additional "fake"
        // touch events. To disable resampling, request unbuffered dispatch.
        mTestActivity.getWindow().getDecorView().requestUnbufferedDispatch(
                InputDevice.SOURCE_TOUCHSCREEN);

        // Next we send a bunch of ACTION_MOVE events. Each one with a different x and y
        // coordinate. If no property changes (i.e. the same VirtualTouchEvent is sent
        // multiple times) then the kernel drops the event as there is no point in delivering
        // a new event if nothing changed.
        builder.setAction(VirtualTouchEvent.ACTION_MOVE);
        for (int i = 1; i <= moveEventCount; i++) {
            builder.setX(x + i)
                    .setY(y + i)
                    .setPressure(255f);
            touchscreen.sendTouchEvent(builder.build());
            expectedEvents.add(
                    VirtualInputEventCreator.createTouchscreenEvent(MotionEvent.ACTION_MOVE,
                            x + i, y + i, 1f /* pressure */, computedSize, inputSize));
        }

        touchscreen.sendTouchEvent(builder
                .setAction(VirtualTouchEvent.ACTION_UP)
                .setX(x + moveEventCount)
                .setY(y + moveEventCount)
                .build());
        expectedEvents.add(
                VirtualInputEventCreator.createTouchscreenEvent(MotionEvent.ACTION_UP,
                        x + moveEventCount,
                        y + moveEventCount,
                        1f /* pressure */, computedSize, inputSize));

        verifyEvents(expectedEvents);
    }

    @Test
    public void virtualKeyboard_keyEvent() {
        VirtualKeyboard keyboard = VirtualInputDeviceCreator.createAndPrepareKeyboard(
                mVirtualDevice, DEVICE_NAME, mVirtualDisplay.getDisplay()).getDevice();
        keyboard.sendKeyEvent(
                new VirtualKeyEvent.Builder()
                        .setKeyCode(KeyEvent.KEYCODE_A)
                        .setAction(VirtualKeyEvent.ACTION_DOWN)
                        .build());
        keyboard.sendKeyEvent(
                new VirtualKeyEvent.Builder()
                        .setKeyCode(KeyEvent.KEYCODE_A)
                        .setAction(VirtualKeyEvent.ACTION_UP)
                        .build());

        verifyEvents(
                Arrays.asList(VirtualInputEventCreator.createKeyboardEvent(KeyEvent.ACTION_DOWN,
                                KeyEvent.KEYCODE_A),
                        VirtualInputEventCreator.createKeyboardEvent(KeyEvent.ACTION_UP,
                                KeyEvent.KEYCODE_A)));
    }

    @Test
    public void virtualDpad_keyEvent() {
        VirtualDpad dpad = VirtualInputDeviceCreator.createAndPrepareDpad(mVirtualDevice,
                DEVICE_NAME, mVirtualDisplay.getDisplay()).getDevice();
        dpad.sendKeyEvent(
                new VirtualKeyEvent.Builder()
                        .setKeyCode(KeyEvent.KEYCODE_DPAD_UP)
                        .setAction(VirtualKeyEvent.ACTION_DOWN)
                        .build());
        dpad.sendKeyEvent(
                new VirtualKeyEvent.Builder()
                        .setKeyCode(KeyEvent.KEYCODE_DPAD_UP)
                        .setAction(VirtualKeyEvent.ACTION_UP)
                        .build());
        dpad.sendKeyEvent(
                new VirtualKeyEvent.Builder()
                        .setKeyCode(KeyEvent.KEYCODE_DPAD_CENTER)
                        .setAction(VirtualKeyEvent.ACTION_DOWN)
                        .build());
        dpad.sendKeyEvent(
                new VirtualKeyEvent.Builder()
                        .setKeyCode(KeyEvent.KEYCODE_DPAD_CENTER)
                        .setAction(VirtualKeyEvent.ACTION_UP)
                        .build());

        verifyEvents(
                Arrays.asList(VirtualInputEventCreator.createDpadEvent(KeyEvent.ACTION_DOWN,
                                KeyEvent.KEYCODE_DPAD_UP),
                        VirtualInputEventCreator.createDpadEvent(KeyEvent.ACTION_UP,
                                KeyEvent.KEYCODE_DPAD_UP),
                        VirtualInputEventCreator.createDpadEvent(KeyEvent.ACTION_DOWN,
                                KeyEvent.KEYCODE_DPAD_CENTER),
                        VirtualInputEventCreator.createDpadEvent(KeyEvent.ACTION_UP,
                                KeyEvent.KEYCODE_DPAD_CENTER)));
    }

    @Test
    public void virtualNavigationTouchpad_touchEvent() {
        final int touchPadWidth = 50;
        final int touchPadHeight = 50;
        VirtualNavigationTouchpad navigationTouchpad =
                VirtualInputDeviceCreator.createAndPrepareNavigationTouchpad(mVirtualDevice,
                        DEVICE_NAME, mVirtualDisplay.getDisplay(), touchPadWidth, touchPadHeight)
                        .getDevice();
        final float inputSize = 1f;
        final float x = 30f;
        final float y = 30f;
        navigationTouchpad.sendTouchEvent(new VirtualTouchEvent.Builder()
                .setAction(VirtualTouchEvent.ACTION_DOWN)
                .setPointerId(1)
                .setX(x)
                .setY(y)
                .setPressure(255f)
                .setMajorAxisSize(inputSize)
                .setToolType(VirtualTouchEvent.TOOL_TYPE_FINGER)
                .build());
        navigationTouchpad.sendTouchEvent(new VirtualTouchEvent.Builder()
                .setAction(VirtualTouchEvent.ACTION_UP)
                .setPointerId(1)
                .setX(x)
                .setY(y)
                .setToolType(VirtualTouchEvent.TOOL_TYPE_FINGER)
                .build());
        // Convert the input axis size to its equivalent fraction of the total touchpad size.
        final float computedSize = inputSize / (touchPadWidth - 1f);
        verifyEvents(Arrays.asList(
                VirtualInputEventCreator.createNavigationTouchpadMotionEvent(
                        MotionEvent.ACTION_DOWN, x, y, computedSize /* size */,
                        inputSize /* axisSize */),
                VirtualInputEventCreator.createNavigationTouchpadMotionEvent(
                        MotionEvent.ACTION_UP, x, y, computedSize /* size */,
                        inputSize /* axisSize */)));
    }

    @RequiresFlagsEnabled({Flags.FLAG_INTERACTIVE_SCREEN_MIRROR,
            Flags.FLAG_CONSISTENT_DISPLAY_FLAGS, Flags.FLAG_VIRTUAL_STYLUS})
    @Test
    public void virtualStylus_touchEvent() {
        VirtualStylus stylus = VirtualInputDeviceCreator.createAndPrepareStylus(mVirtualDevice,
                DEVICE_NAME, mVirtualDisplay.getDisplay()).getDevice();
        // We expect to get the exact coordinates in the view that were injected using the
        // stylus. Touch resampling could result in the generation of additional "fake" touch
        // events. To disable resampling, request unbuffered dispatch.
        mTestActivity.getWindow().getDecorView().requestUnbufferedDispatch(
                InputDevice.SOURCE_STYLUS);

        final int x = mDisplayWidth / 2;
        final int y = mDisplayHeight / 2;
        final int toolType = VirtualStylusMotionEvent.TOOL_TYPE_STYLUS;
        // The number of move events that are sent between the down and up event.
        final int moveEventCount = 5;
        List<InputEvent> expectedEvents = new ArrayList<>(moveEventCount + 2);
        // The builder is used for all events in this test. So properties all events have in
        // common are set here.
        VirtualStylusMotionEvent.Builder builder = new VirtualStylusMotionEvent.Builder()
                .setToolType(toolType);

        // Down event
        stylus.sendMotionEvent(builder
                .setAction(VirtualStylusMotionEvent.ACTION_DOWN)
                .setX(x)
                .setY(y)
                .setPressure(255)
                .build());
        expectedEvents.add(
                VirtualInputEventCreator.createStylusEvent(MotionEvent.ACTION_DOWN,
                        toolType, x, y, 1f /* pressure */, 0 /* buttonState */));

        // Next we send a bunch of ACTION_MOVE events. Each one with a different x and y
        // coordinate.
        builder.setAction(VirtualStylusMotionEvent.ACTION_MOVE);
        for (int i = 1; i <= moveEventCount; i++) {
            builder.setX(x + i)
                    .setY(y + i)
                    .setPressure(255);
            stylus.sendMotionEvent(builder.build());
            expectedEvents.add(
                    VirtualInputEventCreator.createStylusEvent(MotionEvent.ACTION_MOVE,
                            toolType, x + i, y + i, 1f /* pressure */,
                            0 /* buttonState */));
        }

        // Up event
        stylus.sendMotionEvent(builder
                .setAction(VirtualStylusMotionEvent.ACTION_UP)
                .setX(x + moveEventCount)
                .setY(y + moveEventCount)
                .build());
        expectedEvents.add(
                VirtualInputEventCreator.createStylusEvent(MotionEvent.ACTION_UP,
                        toolType, x + moveEventCount, y + moveEventCount, 1f /* pressure */,
                        0 /* buttonState */));

        verifyEvents(expectedEvents);
    }
}
