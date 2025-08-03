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

package android.hardware.input.cts.virtualcreators;

import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

/**
 * Static utilities for creating input events to verify the functionality of virtual input devices.
 */
public class VirtualInputEventCreator {

    public static MotionEvent createMouseEvent(int action, float x, float y, int buttonState,
            float pressure, float relativeX, float relativeY, float vScroll) {
        final MotionEvent.PointerProperties pointerProperties = new MotionEvent.PointerProperties();
        pointerProperties.toolType = MotionEvent.TOOL_TYPE_MOUSE;
        final MotionEvent.PointerCoords pointerCoords = new MotionEvent.PointerCoords();
        pointerCoords.setAxisValue(MotionEvent.AXIS_X, x);
        pointerCoords.setAxisValue(MotionEvent.AXIS_Y, y);
        pointerCoords.setAxisValue(MotionEvent.AXIS_RELATIVE_X, relativeX);
        pointerCoords.setAxisValue(MotionEvent.AXIS_RELATIVE_Y, relativeY);
        pointerCoords.setAxisValue(MotionEvent.AXIS_PRESSURE, pressure);
        pointerCoords.setAxisValue(MotionEvent.AXIS_VSCROLL, vScroll);
        pointerCoords.setAxisValue(MotionEvent.AXIS_HSCROLL, 0 /* value */);
        return MotionEvent.obtain(
                0 /* downTime */,
                0 /* eventTime */,
                action,
                1 /* pointerCount */,
                new MotionEvent.PointerProperties[]{pointerProperties},
                new MotionEvent.PointerCoords[]{pointerCoords},
                0 /* metaState */,
                buttonState,
                1f /* xPrecision */,
                1f /* yPrecision */,
                0 /* deviceId */,
                0 /* edgeFlags */,
                InputDevice.SOURCE_MOUSE,
                0 /* flags */);
    }

    public static MotionEvent createMouseEvent(int action, float x, float y, int buttonState,
            float pressure) {
        return createMouseEvent(action, x, y, buttonState, pressure, 0 /* relativeX */,
                0 /* relativeY */, 0 /* vScroll */);
    }

    public static MotionEvent createTouchscreenEvent(int action, float x, float y, float pressure,
            float size, float axisSize) {
        final MotionEvent.PointerProperties pointerProperties = new MotionEvent.PointerProperties();
        pointerProperties.toolType = MotionEvent.TOOL_TYPE_FINGER;
        final MotionEvent.PointerCoords pointerCoords = new MotionEvent.PointerCoords();
        pointerCoords.setAxisValue(MotionEvent.AXIS_X, x);
        pointerCoords.setAxisValue(MotionEvent.AXIS_Y, y);
        pointerCoords.setAxisValue(MotionEvent.AXIS_PRESSURE, pressure);
        pointerCoords.setAxisValue(MotionEvent.AXIS_SIZE, size);
        pointerCoords.setAxisValue(MotionEvent.AXIS_TOUCH_MAJOR, axisSize);
        pointerCoords.setAxisValue(MotionEvent.AXIS_TOUCH_MINOR, axisSize);
        pointerCoords.setAxisValue(MotionEvent.AXIS_TOOL_MAJOR, axisSize);
        pointerCoords.setAxisValue(MotionEvent.AXIS_TOOL_MINOR, axisSize);
        return MotionEvent.obtain(
                0 /* downTime */,
                0 /* eventTime */,
                action,
                1 /* pointerCount */,
                new MotionEvent.PointerProperties[]{pointerProperties},
                new MotionEvent.PointerCoords[]{pointerCoords},
                0 /* metaState */,
                0 /* buttonState */,
                1f /* xPrecision */,
                1f /* yPrecision */,
                0 /* deviceId */,
                0 /* edgeFlags */,
                InputDevice.SOURCE_TOUCHSCREEN,
                0 /* flags */);
    }

    public static MotionEvent createNavigationTouchpadMotionEvent(int action, float x, float y,
            float size, float axisSize) {
        final MotionEvent.PointerProperties pointerProperties = new MotionEvent.PointerProperties();
        pointerProperties.toolType = MotionEvent.TOOL_TYPE_FINGER;
        final MotionEvent.PointerCoords pointerCoords = new MotionEvent.PointerCoords();
        pointerCoords.setAxisValue(MotionEvent.AXIS_X, x);
        pointerCoords.setAxisValue(MotionEvent.AXIS_Y, y);
        pointerCoords.setAxisValue(MotionEvent.AXIS_PRESSURE, 1f /* value */);
        pointerCoords.setAxisValue(MotionEvent.AXIS_SIZE, size);
        pointerCoords.setAxisValue(MotionEvent.AXIS_TOUCH_MAJOR, axisSize);
        pointerCoords.setAxisValue(MotionEvent.AXIS_TOUCH_MINOR, axisSize);
        pointerCoords.setAxisValue(MotionEvent.AXIS_TOOL_MAJOR, axisSize);
        pointerCoords.setAxisValue(MotionEvent.AXIS_TOOL_MINOR, axisSize);
        return MotionEvent.obtain(
                0 /* downTime */,
                0 /* eventTime */,
                action,
                1 /* pointerCount */,
                new MotionEvent.PointerProperties[]{pointerProperties},
                new MotionEvent.PointerCoords[]{pointerCoords},
                0 /* metaState */,
                0 /* buttonState */,
                1f /* xPrecision */,
                1f /* yPrecision */,
                0 /* deviceId */,
                0 /* edgeFlags */,
                InputDevice.SOURCE_TOUCH_NAVIGATION,
                0 /* flags */);
    }

    public static MotionEvent createStylusHoverMotionEvent(int action, float x, float y,
            int toolType) {
        return createStylusHoverMotionEvent(action, x, y, toolType, 0 /* buttonState */);
    }

    public static MotionEvent createStylusHoverMotionEvent(int action, float x, float y,
            int toolType, int buttonState) {
        return createStylusEvent(action, toolType, x, y, 0f /* pressure */, buttonState);
    }

    public static MotionEvent createStylusTouchMotionEvent(int action, float x, float y,
            int toolType) {
        return createStylusTouchMotionEvent(action, x, y, toolType, 0 /* buttonState */);
    }

    public static MotionEvent createStylusTouchMotionEvent(int action, float x, float y,
            int toolType, float tilt, float orientation) {
        return createStylusEvent(action, toolType, x, y, 1f /* pressure */, 0 /* buttonState */,
                tilt, orientation);
    }

    public static MotionEvent createStylusTouchMotionEvent(int action, float x, float y,
            int toolType, int buttonState) {
        return createStylusEvent(action, toolType, x, y, 1f /* pressure */, buttonState);
    }

    public static MotionEvent createStylusEvent(int action, int toolType, float x, float y,
            float pressure, int buttonState) {
        return createStylusEvent(action, toolType, x, y, pressure, buttonState, 0f /* tilt */,
                0f /* orientation */);
    }

    private static MotionEvent createStylusEvent(int action, int toolType, float x, float y,
            float pressure, int buttonState, float tilt, float orientation) {
        final MotionEvent.PointerProperties pointerProperties = new MotionEvent.PointerProperties();
        pointerProperties.toolType = toolType;
        final MotionEvent.PointerCoords pointerCoords = new MotionEvent.PointerCoords();
        pointerCoords.setAxisValue(MotionEvent.AXIS_X, x);
        pointerCoords.setAxisValue(MotionEvent.AXIS_Y, y);
        pointerCoords.setAxisValue(MotionEvent.AXIS_PRESSURE, pressure);
        pointerCoords.setAxisValue(MotionEvent.AXIS_TILT, tilt);
        pointerCoords.setAxisValue(MotionEvent.AXIS_ORIENTATION, orientation);
        return MotionEvent.obtain(
                0 /* downTime */,
                0 /* eventTime */,
                action,
                1 /* pointerCount */,
                new MotionEvent.PointerProperties[]{pointerProperties},
                new MotionEvent.PointerCoords[]{pointerCoords},
                0 /* metaState */,
                buttonState,
                1f /* xPrecision */,
                1f /* yPrecision */,
                0 /* deviceId */,
                0 /* edgeFlags */,
                InputDevice.SOURCE_STYLUS | InputDevice.SOURCE_KEYBOARD
                        | InputDevice.SOURCE_TOUCHSCREEN,
                0 /* flags */);
    }

    public static KeyEvent createKeyboardEvent(int action, int code) {
        return createKeyEvent(action, code, InputDevice.SOURCE_KEYBOARD);
    }

    public static KeyEvent createDpadEvent(int action, int code) {
        return createKeyEvent(action, code, InputDevice.SOURCE_KEYBOARD | InputDevice.SOURCE_DPAD);
    }

    private static KeyEvent createKeyEvent(int action, int code, int source) {
        return new KeyEvent(
                0 /* downTime */,
                0 /* eventTime */,
                action,
                code,
                0 /* repeat */,
                0 /* metaState */,
                0 /* deviceId */,
                0 /* scancode */,
                0 /* flags */,
                source);
    }

    private VirtualInputEventCreator() {
    }
}
