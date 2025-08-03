/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.car.hal;

import static android.car.CarOccupantZoneManager.DisplayTypeEnum;
import static android.hardware.automotive.vehicle.RotaryInputType.ROTARY_INPUT_TYPE_AUDIO_VOLUME;
import static android.hardware.automotive.vehicle.RotaryInputType.ROTARY_INPUT_TYPE_SYSTEM_NAVIGATION;
import static android.hardware.automotive.vehicle.VehicleProperty.HW_CUSTOM_INPUT;
import static android.hardware.automotive.vehicle.VehicleProperty.HW_KEY_INPUT;
import static android.hardware.automotive.vehicle.VehicleProperty.HW_KEY_INPUT_V2;
import static android.hardware.automotive.vehicle.VehicleProperty.HW_MOTION_INPUT;
import static android.hardware.automotive.vehicle.VehicleProperty.HW_ROTARY_INPUT;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

import android.car.CarOccupantZoneManager;
import android.car.builtin.util.Slogf;
import android.car.input.CarInputManager;
import android.car.input.CustomInputEvent;
import android.car.input.RotaryEvent;
import android.hardware.automotive.vehicle.VehicleDisplay;
import android.hardware.automotive.vehicle.VehicleHwKeyInputAction;
import android.hardware.automotive.vehicle.VehicleHwMotionButtonStateFlag;
import android.hardware.automotive.vehicle.VehicleHwMotionInputAction;
import android.hardware.automotive.vehicle.VehicleHwMotionInputSource;
import android.hardware.automotive.vehicle.VehicleHwMotionToolType;
import android.os.SystemClock;
import android.util.Log;
import android.util.SparseArray;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.MotionEvent.PointerCoords;
import android.view.MotionEvent.PointerProperties;

import com.android.car.CarLog;
import com.android.car.CarServiceUtils;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.io.PrintWriter;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Translates HAL input events to higher-level semantic information.
 */
public class InputHalService extends HalServiceBase {

    private static final int MAX_EVENTS_TO_KEEP_AS_HISTORY = 10;
    private static final String TAG = CarLog.TAG_INPUT;
    private static final int[] SUPPORTED_PROPERTIES = new int[]{
            HW_KEY_INPUT,
            HW_KEY_INPUT_V2,
            HW_MOTION_INPUT,
            HW_ROTARY_INPUT,
            HW_CUSTOM_INPUT
    };

    private final VehicleHal mHal;

    @GuardedBy("mLock")
    private final Queue<MotionEvent> mLastFewDispatchedMotionEvents =
            new ArrayDeque<>(MAX_EVENTS_TO_KEEP_AS_HISTORY);

    @GuardedBy("mLock")
    private Queue<KeyEvent> mLastFewDispatchedV2KeyEvents = new ArrayDeque<>(
            MAX_EVENTS_TO_KEEP_AS_HISTORY);

    /**
     * A function to retrieve the current system uptime in milliseconds - replaceable for testing.
     */
    private final LongSupplier mUptimeSupplier;

    /**
     * Interface used to act upon HAL incoming key events.
     */
    public interface InputListener {
        /** Called for key event */
        void onKeyEvent(KeyEvent event, int targetDisplay);

        /** Called for key event per seat */
        void onKeyEvent(KeyEvent event, int targetDisplay, int seat);

        /** Called for motion event per seat */
        void onMotionEvent(MotionEvent event, int targetDisplay, int seat);

        /** Called for rotary event */
        void onRotaryEvent(RotaryEvent event, int targetDisplay);

        /** Called for OEM custom input event */
        void onCustomInputEvent(CustomInputEvent event);
    }

    /** The current press state of a key. */
    private static class KeyState {
        /** The timestamp (uptimeMillis) of the last ACTION_DOWN event for this key. */
        public long mLastKeyDownTimestamp = -1;
        /** The number of ACTION_DOWN events that have been sent for this keypress. */
        public int mRepeatCount = 0;
    }

    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private boolean mKeyInputSupported;

    @GuardedBy("mLock")
    private boolean mKeyInputV2Supported;

    @GuardedBy("mLock")
    private boolean mMotionInputSupported;

    @GuardedBy("mLock")
    private boolean mRotaryInputSupported;

    @GuardedBy("mLock")
    private boolean mCustomInputSupported;

    @GuardedBy("mLock")
    private InputListener mListener;

    @GuardedBy("mKeyStates")
    private final SparseArray<KeyState> mKeyStates = new SparseArray<>();

    public InputHalService(VehicleHal hal) {
        this(hal, SystemClock::uptimeMillis);
    }

    @VisibleForTesting
    InputHalService(VehicleHal hal, LongSupplier uptimeSupplier) {
        super();
        mHal = hal;
        mUptimeSupplier = uptimeSupplier;
    }

    /**
     * Sets the input event listener.
     */
    public void setInputListener(InputListener listener) {
        boolean keyInputSupported;
        boolean keyInputV2Supported;
        boolean motionInputSupported;
        boolean rotaryInputSupported;
        boolean customInputSupported;
        synchronized (mLock) {
            if (!mKeyInputSupported && !mRotaryInputSupported && !mCustomInputSupported) {
                Slogf.w(TAG, "input listener set while rotary and key input not supported");
                return;
            }
            mListener = listener;
            keyInputSupported = mKeyInputSupported;
            keyInputV2Supported = mKeyInputV2Supported;
            motionInputSupported = mMotionInputSupported;
            rotaryInputSupported = mRotaryInputSupported;
            customInputSupported = mCustomInputSupported;
        }
        if (keyInputSupported) {
            mHal.subscribePropertySafe(this, HW_KEY_INPUT);
        }
        if (keyInputV2Supported) {
            mHal.subscribePropertySafe(this, HW_KEY_INPUT_V2);
        }
        if (motionInputSupported) {
            mHal.subscribePropertySafe(this, HW_MOTION_INPUT);
        }
        if (rotaryInputSupported) {
            mHal.subscribePropertySafe(this, HW_ROTARY_INPUT);
        }
        if (customInputSupported) {
            mHal.subscribePropertySafe(this, HW_CUSTOM_INPUT);
        }
    }

    /** Returns whether {@code HW_KEY_INPUT} is supported. */
    public boolean isKeyInputSupported() {
        synchronized (mLock) {
            return mKeyInputSupported;
        }
    }

    /** Returns whether {@code HW_KEY_INPUT_V2} is supported. */
    public boolean isKeyInputV2Supported() {
        synchronized (mLock) {
            return mKeyInputV2Supported;
        }
    }

    /** Returns whether {@code HW_MOTION_INPUT} is supported. */
    public boolean isMotionInputSupported() {
        synchronized (mLock) {
            return mMotionInputSupported;
        }
    }

    /** Returns whether {@code HW_ROTARY_INPUT} is supported. */
    public boolean isRotaryInputSupported() {
        synchronized (mLock) {
            return mRotaryInputSupported;
        }
    }

    /** Returns whether {@code HW_CUSTOM_INPUT} is supported. */
    public boolean isCustomInputSupported() {
        synchronized (mLock) {
            return mCustomInputSupported;
        }
    }

    @Override
    public void init() {
    }

    @Override
    public void release() {
        synchronized (mLock) {
            mListener = null;
            mKeyInputSupported = false;
            mKeyInputV2Supported = false;
            mMotionInputSupported = false;
            mRotaryInputSupported = false;
            mCustomInputSupported = false;
        }
    }

    @Override
    int[] getAllSupportedProperties() {
        return SUPPORTED_PROPERTIES;
    }

    @Override
    public void takeProperties(Collection<HalPropConfig> properties) {
        synchronized (mLock) {
            for (HalPropConfig property : properties) {
                switch (property.getPropId()) {
                    case HW_KEY_INPUT:
                        mKeyInputSupported = true;
                        break;
                    case HW_KEY_INPUT_V2:
                        mKeyInputV2Supported = true;
                        break;
                    case HW_MOTION_INPUT:
                        mMotionInputSupported = true;
                        break;
                    case HW_ROTARY_INPUT:
                        mRotaryInputSupported = true;
                        break;
                    case HW_CUSTOM_INPUT:
                        mCustomInputSupported = true;
                        break;
                    default:
                        break;
                }
            }
        }
    }

    @Override
    public void onHalEvents(List<HalPropValue> values) {
        InputListener listener;
        synchronized (mLock) {
            listener = mListener;
        }
        if (listener == null) {
            Slogf.w(TAG, "Input event while listener is null");
            return;
        }
        for (int i = 0; i < values.size(); i++) {
            HalPropValue value = values.get(i);
            switch (value.getPropId()) {
                case HW_KEY_INPUT:
                    dispatchKeyInput(listener, value);
                    break;
                case HW_KEY_INPUT_V2:
                    dispatchKeyInputV2(listener, value);
                    break;
                case HW_MOTION_INPUT:
                    dispatchMotionInput(listener, value);
                    break;
                case HW_ROTARY_INPUT:
                    dispatchRotaryInput(listener, value);
                    break;
                case HW_CUSTOM_INPUT:
                    dispatchCustomInput(listener, value);
                    break;
                default:
                    Slogf.e(TAG, "Wrong event dispatched, prop:0x%x", value.getPropId());
                    break;
            }
        }
    }

    private void dispatchKeyInput(InputListener listener, HalPropValue value) {
        int action;
        int code;
        int vehicleDisplay;
        int indentsCount;
        try {
            action = (value.getInt32Value(0) == VehicleHwKeyInputAction.ACTION_DOWN)
                    ? KeyEvent.ACTION_DOWN
                    : KeyEvent.ACTION_UP;
            code = value.getInt32Value(1);
            vehicleDisplay = value.getInt32Value(2);
            indentsCount = value.getInt32ValuesSize() < 4 ? 1 : value.getInt32Value(3);
            Slogf.d(TAG, "hal event code: %d, action: %d, display: %d, number of indents: %d",
                    code, action, vehicleDisplay, indentsCount);
        } catch (Exception e) {
            Slogf.e(TAG, "Invalid hal key input event received, int32Values: "
                    + value.dumpInt32Values(), e);
            return;
        }
        while (indentsCount > 0) {
            indentsCount--;
            dispatchKeyEvent(listener, action, code, convertDisplayType(vehicleDisplay));
        }
    }

    private void dispatchKeyInputV2(InputListener listener, HalPropValue value) {
        final int int32ValuesSize = 4;
        final int int64ValuesSize = 1;
        int seat;
        int vehicleDisplay;
        int keyCode;
        int action;
        int repeatCount;
        long elapsedDownTimeNanos;
        long elapsedEventTimeNanos;
        int convertedAction;
        int convertedVehicleDisplay;
        try {
            seat = value.getAreaId();
            if (value.getInt32ValuesSize() < int32ValuesSize) {
                Slogf.e(TAG, "Wrong int32 array size for key input v2 from vhal: %d",
                        value.getInt32ValuesSize());
                return;
            }
            vehicleDisplay = value.getInt32Value(0);
            keyCode = value.getInt32Value(1);
            action = value.getInt32Value(2);
            repeatCount = value.getInt32Value(3);

            if (value.getInt64ValuesSize() < int64ValuesSize) {
                Slogf.e(TAG, "Wrong int64 array size for key input v2 from vhal: %d",
                        value.getInt64ValuesSize());
                return;
            }
            elapsedDownTimeNanos = value.getInt64Value(0);
            if (Slogf.isLoggable(TAG, Log.DEBUG)) {
                Slogf.d(TAG, "hal event keyCode: %d, action: %d, display: %d, repeatCount: %d"
                                + ", elapsedDownTimeNanos: %d", keyCode, action, vehicleDisplay,
                        repeatCount, elapsedDownTimeNanos);
            }
            convertedAction = convertToKeyEventAction(action);
            convertedVehicleDisplay = convertDisplayType(vehicleDisplay);
        } catch (Exception e) {
            Slogf.e(TAG, "Invalid hal key input event received, int32Values: "
                    + value.dumpInt32Values() + ", int64Values: " + value.dumpInt64Values(), e);
            return;
        }

        if (action == VehicleHwKeyInputAction.ACTION_DOWN) {
            // For action down, the code should make sure that event time & down time are the same
            // to maintain the invariant as defined in KeyEvent.java.
            elapsedEventTimeNanos = elapsedDownTimeNanos;
        } else {
            elapsedEventTimeNanos = value.getTimestamp();
        }

        dispatchKeyEventV2(listener, convertedAction, keyCode, convertedVehicleDisplay,
                toUpTimeMillis(elapsedEventTimeNanos), toUpTimeMillis(elapsedDownTimeNanos),
                repeatCount, seat);
    }

    private void dispatchMotionInput(InputListener listener, HalPropValue value) {
        final int firstInt32ArrayOffset = 5;
        final int int64ValuesSize = 1;
        final int numInt32Arrays = 2;
        final int numFloatArrays = 4;
        int seat;
        int vehicleDisplay;
        int inputSource;
        int action;
        int buttonStateFlag;
        int pointerCount;
        int[] pointerIds;
        int[] toolTypes;
        float[] xData;
        float[] yData;
        float[] pressureData;
        float[] sizeData;
        long elapsedDownTimeNanos;
        PointerProperties[] pointerProperties;
        PointerCoords[] pointerCoords;
        int convertedInputSource;
        int convertedAction;
        int convertedButtonStateFlag;
        try {
            seat = value.getAreaId();
            if (value.getInt32ValuesSize() < firstInt32ArrayOffset) {
                Slogf.e(TAG, "Wrong int32 array size for key input v2 from vhal: %d",
                        value.getInt32ValuesSize());
                return;
            }
            vehicleDisplay = value.getInt32Value(0);
            inputSource = value.getInt32Value(1);
            action = value.getInt32Value(2);
            buttonStateFlag = value.getInt32Value(3);
            pointerCount = value.getInt32Value(4);
            if (pointerCount < 1) {
                Slogf.e(TAG, "Wrong pointerCount for key input v2 from vhal: %d",
                        pointerCount);
                return;
            }
            pointerIds = new int[pointerCount];
            toolTypes = new int[pointerCount];
            xData = new float[pointerCount];
            yData = new float[pointerCount];
            pressureData = new float[pointerCount];
            sizeData = new float[pointerCount];
            if (value.getInt32ValuesSize() < firstInt32ArrayOffset
                    + pointerCount * numInt32Arrays) {
                Slogf.e(TAG, "Wrong int32 array size for key input v2 from vhal: %d",
                        value.getInt32ValuesSize());
                return;
            }
            if (value.getFloatValuesSize() < pointerCount * numFloatArrays) {
                Slogf.e(TAG, "Wrong int32 array size for key input v2 from vhal: %d",
                        value.getInt32ValuesSize());
                return;
            }
            for (int i = 0; i < pointerCount; i++) {
                pointerIds[i] = value.getInt32Value(firstInt32ArrayOffset + i);
                toolTypes[i] = value.getInt32Value(firstInt32ArrayOffset + pointerCount + i);
                xData[i] = value.getFloatValue(i);
                yData[i] = value.getFloatValue(pointerCount + i);
                pressureData[i] = value.getFloatValue(2 * pointerCount + i);
                sizeData[i] = value.getFloatValue(3 * pointerCount + i);
            }
            if (value.getInt64ValuesSize() < int64ValuesSize) {
                Slogf.e(TAG, "Wrong int64 array size for key input v2 from vhal: %d",
                        value.getInt64ValuesSize());
                return;
            }
            elapsedDownTimeNanos = value.getInt64Value(0);

            if (Slogf.isLoggable(TAG, Log.DEBUG)) {
                Slogf.d(TAG, "hal motion event inputSource: %d, action: %d, display: %d"
                                + ", buttonStateFlag: %d, pointerCount: %d, elapsedDownTimeNanos: "
                                + "%d", inputSource, action, vehicleDisplay, buttonStateFlag,
                        pointerCount, elapsedDownTimeNanos);
            }
            pointerProperties = createPointerPropertiesArray(pointerCount);
            pointerCoords = createPointerCoordsArray(pointerCount);
            for (int i = 0; i < pointerCount; i++) {
                pointerProperties[i].id = pointerIds[i];
                pointerProperties[i].toolType = convertToolType(toolTypes[i]);
                pointerCoords[i].x = xData[i];
                pointerCoords[i].y = yData[i];
                pointerCoords[i].pressure = pressureData[i];
                pointerCoords[i].size = sizeData[i];
            }

            convertedAction = convertMotionAction(action);
            convertedButtonStateFlag = convertButtonStateFlag(buttonStateFlag);
            convertedInputSource = convertInputSource(inputSource);
        } catch (Exception e) {
            Slogf.e(TAG, "Invalid hal key input event received, int32Values: "
                    + value.dumpInt32Values() + ", floatValues: " + value.dumpFloatValues()
                    + ", int64Values: " + value.dumpInt64Values(), e);
            return;
        }
        MotionEvent event = MotionEvent.obtain(toUpTimeMillis(elapsedDownTimeNanos),
                toUpTimeMillis(value.getTimestamp()) /* eventTime */,
                convertedAction,
                pointerCount,
                pointerProperties,
                pointerCoords,
                0 /* metaState */,
                convertedButtonStateFlag,
                0f /* xPrecision */,
                0f /* yPrecision */,
                0 /* deviceId */,
                0 /* edgeFlags */,
                convertedInputSource,
                0 /* flags */);
        listener.onMotionEvent(event, convertDisplayType(vehicleDisplay), seat);
        saveMotionEventInHistory(event);
    }

    private void saveV2KeyInputEventInHistory(KeyEvent keyEvent) {
        synchronized (mLock) {
            while (mLastFewDispatchedV2KeyEvents.size() >= MAX_EVENTS_TO_KEEP_AS_HISTORY) {
                mLastFewDispatchedV2KeyEvents.remove();
            }
            mLastFewDispatchedV2KeyEvents.add(keyEvent);
        }
    }

    private void saveMotionEventInHistory(MotionEvent motionEvent) {
        synchronized (mLock) {
            while (mLastFewDispatchedMotionEvents.size() >= MAX_EVENTS_TO_KEEP_AS_HISTORY) {
                mLastFewDispatchedMotionEvents.remove();
            }
            mLastFewDispatchedMotionEvents.add(motionEvent);
        }
    }

    private static long toUpTimeMillis(long elapsedEventTimeNanos) {
        final byte maxTries = 5;
        long timeSpentInSleep1 = 0;
        long timeSpentInSleep2 = 0;
        long smallestTimeSpentInSleep = Integer.MAX_VALUE;
        int tryNum;
        for (tryNum = 0; tryNum < maxTries; tryNum++) {
            timeSpentInSleep1 = SystemClock.elapsedRealtime() - SystemClock.uptimeMillis();
            timeSpentInSleep2 = SystemClock.elapsedRealtime() - SystemClock.uptimeMillis();
            if (timeSpentInSleep1 < smallestTimeSpentInSleep) {
                smallestTimeSpentInSleep = timeSpentInSleep1;
            }
            if (timeSpentInSleep2 < smallestTimeSpentInSleep) {
                smallestTimeSpentInSleep = timeSpentInSleep2;
            }
            if (timeSpentInSleep1 == timeSpentInSleep2) {
                break;
            }
        }
        // If maxTries was reached, use the smallest of all calculated timeSpentInSleep.
        long eventUpTimeMillis;
        if (tryNum == maxTries) {
            // Assuming no sleep after elapsedEventTimeNanos.
            eventUpTimeMillis = NANOSECONDS.toMillis(elapsedEventTimeNanos)
                    - smallestTimeSpentInSleep;
        } else {
            // Assuming no sleep after elapsedEventTimeNanos.
            eventUpTimeMillis = NANOSECONDS.toMillis(elapsedEventTimeNanos) - timeSpentInSleep1;
        }
        return eventUpTimeMillis;
    }

    private static PointerProperties[] createPointerPropertiesArray(int size) {
        PointerProperties[] array = new PointerProperties[size];
        for (int i = 0; i < size; i++) {
            array[i] = new PointerProperties();
        }
        return array;
    }

    private static PointerCoords[] createPointerCoordsArray(int size) {
        PointerCoords[] array = new PointerCoords[size];
        for (int i = 0; i < size; i++) {
            array[i] = new PointerCoords();
        }
        return array;
    }

    private int convertToKeyEventAction(int vehicleHwKeyAction) {
        switch (vehicleHwKeyAction) {
            case VehicleHwKeyInputAction.ACTION_DOWN:
                return KeyEvent.ACTION_DOWN;
            case VehicleHwKeyInputAction.ACTION_UP:
                return KeyEvent.ACTION_UP;
            default:
                throw new IllegalArgumentException("Unexpected key event action: "
                        + vehicleHwKeyAction);
        }
    }

    private int convertInputSource(int vehicleInputSource) {
        switch (vehicleInputSource) {
            case VehicleHwMotionInputSource.SOURCE_KEYBOARD:
                return InputDevice.SOURCE_KEYBOARD;
            case VehicleHwMotionInputSource.SOURCE_DPAD:
                return InputDevice.SOURCE_DPAD;
            case VehicleHwMotionInputSource.SOURCE_GAMEPAD:
                return InputDevice.SOURCE_GAMEPAD;
            case VehicleHwMotionInputSource.SOURCE_TOUCHSCREEN:
                return InputDevice.SOURCE_TOUCHSCREEN;
            case VehicleHwMotionInputSource.SOURCE_MOUSE:
                return InputDevice.SOURCE_MOUSE;
            case VehicleHwMotionInputSource.SOURCE_STYLUS:
                return InputDevice.SOURCE_STYLUS;
            case VehicleHwMotionInputSource.SOURCE_BLUETOOTH_STYLUS:
                return InputDevice.SOURCE_BLUETOOTH_STYLUS;
            case VehicleHwMotionInputSource.SOURCE_TRACKBALL:
                return InputDevice.SOURCE_TRACKBALL;
            case VehicleHwMotionInputSource.SOURCE_MOUSE_RELATIVE:
                return InputDevice.SOURCE_MOUSE_RELATIVE;
            case VehicleHwMotionInputSource.SOURCE_TOUCHPAD:
                return InputDevice.SOURCE_TOUCHPAD;
            case VehicleHwMotionInputSource.SOURCE_TOUCH_NAVIGATION:
                return InputDevice.SOURCE_TOUCH_NAVIGATION;
            case VehicleHwMotionInputSource.SOURCE_ROTARY_ENCODER:
                return InputDevice.SOURCE_ROTARY_ENCODER;
            case VehicleHwMotionInputSource.SOURCE_JOYSTICK:
                return InputDevice.SOURCE_JOYSTICK;
            case VehicleHwMotionInputSource.SOURCE_HDMI:
                return InputDevice.SOURCE_HDMI;
            case VehicleHwMotionInputSource.SOURCE_SENSOR:
                return InputDevice.SOURCE_SENSOR;
            default:
                return InputDevice.SOURCE_UNKNOWN;
        }
    }

    private int convertMotionAction(int vehicleAction) {
        switch (vehicleAction) {
            case VehicleHwMotionInputAction.ACTION_DOWN:
                return MotionEvent.ACTION_DOWN;
            case VehicleHwMotionInputAction.ACTION_UP:
                return MotionEvent.ACTION_UP;
            case VehicleHwMotionInputAction.ACTION_MOVE:
                return MotionEvent.ACTION_MOVE;
            case VehicleHwMotionInputAction.ACTION_CANCEL:
                return MotionEvent.ACTION_CANCEL;
            case VehicleHwMotionInputAction.ACTION_POINTER_DOWN:
                return MotionEvent.ACTION_POINTER_DOWN;
            case VehicleHwMotionInputAction.ACTION_POINTER_UP:
                return MotionEvent.ACTION_POINTER_UP;
            case VehicleHwMotionInputAction.ACTION_HOVER_MOVE:
                return MotionEvent.ACTION_HOVER_MOVE;
            case VehicleHwMotionInputAction.ACTION_SCROLL:
                return MotionEvent.ACTION_SCROLL;
            case VehicleHwMotionInputAction.ACTION_HOVER_ENTER:
                return MotionEvent.ACTION_HOVER_ENTER;
            case VehicleHwMotionInputAction.ACTION_HOVER_EXIT:
                return MotionEvent.ACTION_HOVER_EXIT;
            case VehicleHwMotionInputAction.ACTION_BUTTON_PRESS:
                return MotionEvent.ACTION_BUTTON_PRESS;
            case VehicleHwMotionInputAction.ACTION_BUTTON_RELEASE:
                return MotionEvent.ACTION_BUTTON_RELEASE;
            default:
                throw new IllegalArgumentException("Unexpected motion action: " + vehicleAction);
        }
    }

    private int convertButtonStateFlag(int buttonStateFlag) {
        switch (buttonStateFlag) {
            case VehicleHwMotionButtonStateFlag.BUTTON_PRIMARY:
                return MotionEvent.BUTTON_PRIMARY;
            case VehicleHwMotionButtonStateFlag.BUTTON_SECONDARY:
                return MotionEvent.BUTTON_SECONDARY;
            case VehicleHwMotionButtonStateFlag.BUTTON_TERTIARY:
                return MotionEvent.BUTTON_TERTIARY;
            case VehicleHwMotionButtonStateFlag.BUTTON_FORWARD:
                return MotionEvent.BUTTON_FORWARD;
            case VehicleHwMotionButtonStateFlag.BUTTON_BACK:
                return MotionEvent.BUTTON_BACK;
            case VehicleHwMotionButtonStateFlag.BUTTON_STYLUS_PRIMARY:
                return MotionEvent.BUTTON_STYLUS_PRIMARY;
            case VehicleHwMotionButtonStateFlag.BUTTON_STYLUS_SECONDARY:
                return MotionEvent.BUTTON_STYLUS_SECONDARY;
            default:
                return 0; // No flag set.
        }
    }

    private int convertToolType(int toolType) {
        switch (toolType) {
            case VehicleHwMotionToolType.TOOL_TYPE_FINGER:
                return MotionEvent.TOOL_TYPE_FINGER;
            case VehicleHwMotionToolType.TOOL_TYPE_STYLUS:
                return MotionEvent.TOOL_TYPE_STYLUS;
            case VehicleHwMotionToolType.TOOL_TYPE_MOUSE:
                return MotionEvent.TOOL_TYPE_MOUSE;
            case VehicleHwMotionToolType.TOOL_TYPE_ERASER:
                return MotionEvent.TOOL_TYPE_ERASER;
            default:
                return MotionEvent.TOOL_TYPE_UNKNOWN;
        }
    }

    private void dispatchRotaryInput(InputListener listener, HalPropValue value) {
        int timeValuesIndex = 3;  // remaining values are time deltas in nanoseconds
        if (value.getInt32ValuesSize() < timeValuesIndex) {
            Slogf.e(TAG, "Wrong int32 array size for RotaryInput from vhal: %d",
                    value.getInt32ValuesSize());
            return;
        }
        int rotaryInputType = value.getInt32Value(0);
        int detentCount = value.getInt32Value(1);
        int vehicleDisplay = value.getInt32Value(2);
        long timestamp = value.getTimestamp();  // for first detent, uptime nanoseconds
        Slogf.d(TAG, "hal rotary input type: %d, number of detents: %d, display: %d",
                rotaryInputType, detentCount, vehicleDisplay);
        boolean clockwise = detentCount > 0;
        detentCount = Math.abs(detentCount);
        if (detentCount == 0) { // at least there should be one event
            Slogf.e(TAG, "Zero detentCount from vhal, ignore the event");
            return;
        }
        // If count is Integer.MIN_VALUE, Math.abs(count) < 0.
        if (detentCount < 0 || detentCount > Integer.MAX_VALUE - detentCount + 1) {
            Slogf.e(TAG, "Invalid detentCount from vhal: %d, ignore the event", detentCount);
        }
        if (vehicleDisplay != VehicleDisplay.MAIN
                && vehicleDisplay != VehicleDisplay.INSTRUMENT_CLUSTER) {
            Slogf.e(TAG, "Wrong display type for RotaryInput from vhal: %d",
                    vehicleDisplay);
            return;
        }
        if (value.getInt32ValuesSize() != (timeValuesIndex + detentCount - 1)) {
            Slogf.e(TAG, "Wrong int32 array size for RotaryInput from vhal: %d",
                    value.getInt32ValuesSize());
            return;
        }
        int carInputManagerType;
        switch (rotaryInputType) {
            case ROTARY_INPUT_TYPE_SYSTEM_NAVIGATION:
                carInputManagerType = CarInputManager.INPUT_TYPE_ROTARY_NAVIGATION;
                break;
            case ROTARY_INPUT_TYPE_AUDIO_VOLUME:
                carInputManagerType = CarInputManager.INPUT_TYPE_ROTARY_VOLUME;
                break;
            default:
                Slogf.e(TAG, "Unknown rotary input type: %d", rotaryInputType);
                return;
        }

        long[] timestamps = new long[detentCount];
        // vhal returns elapsed time while rotary event is using uptime to be in line with KeyEvent.
        long uptimeToElapsedTimeDelta = CarServiceUtils.getUptimeToElapsedTimeDeltaInMillis();
        long startUptime = TimeUnit.NANOSECONDS.toMillis(timestamp) - uptimeToElapsedTimeDelta;
        timestamps[0] = startUptime;
        for (int i = 0; i < timestamps.length - 1; i++) {
            timestamps[i + 1] = timestamps[i] + TimeUnit.NANOSECONDS.toMillis(
                    value.getInt32Value(timeValuesIndex + i));
        }
        RotaryEvent event = new RotaryEvent(carInputManagerType, clockwise, timestamps);
        listener.onRotaryEvent(event, convertDisplayType(vehicleDisplay));
    }

    /**
     * Dispatches a KeyEvent using {@link #mUptimeSupplier} for the event time.
     *
     * @param listener listener to dispatch the event to
     * @param action action for the KeyEvent
     * @param code keycode for the KeyEvent
     * @param display target display the event is associated with
     */
    private void dispatchKeyEvent(InputListener listener, int action, int code,
            @DisplayTypeEnum int display) {
        dispatchKeyEvent(listener, action, code, display, mUptimeSupplier.getAsLong());
    }

    /**
     * Dispatches a KeyEvent.
     *
     * @param listener listener to dispatch the event to
     * @param action action for the KeyEvent
     * @param code keycode for the KeyEvent
     * @param display target display the event is associated with
     * @param eventTime uptime in milliseconds when the event occurred
     */
    private void dispatchKeyEvent(InputListener listener, int action, int code,
            @DisplayTypeEnum int display, long eventTime) {
        long downTime;
        int repeat;

        synchronized (mKeyStates) {
            KeyState state = mKeyStates.get(code);
            if (state == null) {
                state = new KeyState();
                mKeyStates.put(code, state);
            }

            if (action == KeyEvent.ACTION_DOWN) {
                downTime = eventTime;
                repeat = state.mRepeatCount++;
                state.mLastKeyDownTimestamp = eventTime;
            } else {
                // Handle key up events without any matching down event by setting the down time to
                // the event time. This shouldn't happen in practice - keys should be pressed
                // before they can be released! - but this protects us against HAL weirdness.
                downTime =
                        (state.mLastKeyDownTimestamp == -1)
                                ? eventTime
                                : state.mLastKeyDownTimestamp;
                repeat = 0;
                state.mRepeatCount = 0;
            }
        }

        KeyEvent event = new KeyEvent(
                downTime,
                eventTime,
                action,
                code,
                repeat,
                0 /* deviceId */,
                0 /* scancode */,
                0 /* flags */,
                InputDevice.SOURCE_CLASS_BUTTON);

        // event.displayId will be set in CarInputService#onKeyEvent
        listener.onKeyEvent(event, display);
    }

    /**
     * Dispatches a {@link KeyEvent} to the given {@code seat}.
     *
     * @param listener listener to dispatch the event to
     * @param action action for the key event
     * @param code keycode for the key event
     * @param display target display the event is associated with
     * @param eventTime uptime in milliseconds when the event occurred
     * @param downTime time in milliseconds at which the key down was originally sent
     * @param repeat A repeat count for down events For key down events, this is the repeat count
     *               with the first down starting at 0 and counting up from there. For key up
     *               events, this is always equal to 0.
     * @param seat the area id this event is occurring from
     */
    private void dispatchKeyEventV2(InputListener listener, int action, int code,
            @DisplayTypeEnum int display, long eventTime, long downTime, int repeat, int seat) {
        KeyEvent event = new KeyEvent(
                downTime,
                eventTime,
                action,
                code,
                repeat,
                0 /* metaState */,
                0 /* deviceId */,
                0 /* scancode */,
                0 /* flags */,
                InputDevice.SOURCE_CLASS_BUTTON);

        // event.displayId will be set in CarInputService#onKeyEvent
        listener.onKeyEvent(event, display, seat);
        saveV2KeyInputEventInHistory(event);
    }

    private void dispatchCustomInput(InputListener listener, HalPropValue value) {
        Slogf.d(TAG, "Dispatching CustomInputEvent for listener: %s and value: %s",
                listener, value);
        int inputCode;
        int targetDisplayType;
        int repeatCounter;
        try {
            inputCode = value.getInt32Value(0);
            targetDisplayType = convertDisplayType(value.getInt32Value(1));
            repeatCounter = value.getInt32Value(2);
        } catch (Exception e) {
            Slogf.e(TAG, "Invalid hal custom input event received", e);
            return;
        }
        CustomInputEvent event = new CustomInputEvent(inputCode, targetDisplayType, repeatCounter);
        listener.onCustomInputEvent(event);
    }

    /**
     * Converts the vehicle display type ({@link VehicleDisplay#MAIN} and
     * {@link VehicleDisplay#INSTRUMENT_CLUSTER}) to their corresponding types in
     * {@link CarOccupantZoneManager} ({@link CarOccupantZoneManager#DISPLAY_TYPE_MAIN} and
     * {@link CarOccupantZoneManager#DISPLAY_TYPE_INSTRUMENT_CLUSTER}).
     *
     * @param vehicleDisplayType the vehicle display type
     * @return the corresponding display type (defined in {@link CarOccupantZoneManager}) or
     * {@link CarOccupantZoneManager#DISPLAY_TYPE_UNKNOWN} if the value passed as parameter doesn't
     * correspond to a driver's display type
     * @hide
     */
    @DisplayTypeEnum
    public static int convertDisplayType(int vehicleDisplayType) {
        switch (vehicleDisplayType) {
            case VehicleDisplay.MAIN:
                return CarOccupantZoneManager.DISPLAY_TYPE_MAIN;
            case VehicleDisplay.INSTRUMENT_CLUSTER:
                return CarOccupantZoneManager.DISPLAY_TYPE_INSTRUMENT_CLUSTER;
            case VehicleDisplay.HUD:
                return CarOccupantZoneManager.DISPLAY_TYPE_HUD;
            case VehicleDisplay.INPUT:
                return CarOccupantZoneManager.DISPLAY_TYPE_INPUT;
            case VehicleDisplay.AUXILIARY:
                return CarOccupantZoneManager.DISPLAY_TYPE_AUXILIARY;
            default:
                return CarOccupantZoneManager.DISPLAY_TYPE_UNKNOWN;
        }
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dump(PrintWriter writer) {
        synchronized (mLock) {
            writer.println("*Input HAL*");
            writer.println("mKeyInputSupported:" + mKeyInputSupported);
            writer.println("mRotaryInputSupported:" + mRotaryInputSupported);
            writer.println("mKeyInputV2Supported:" + mKeyInputV2Supported);
            writer.println("mMotionInputSupported:" + mMotionInputSupported);

            writer.println("mLastFewDispatchedV2KeyEvents:");
            KeyEvent[] keyEvents = new KeyEvent[mLastFewDispatchedV2KeyEvents.size()];
            mLastFewDispatchedV2KeyEvents.toArray(keyEvents);
            for (int i = 0; i < keyEvents.length; i++) {
                writer.println("Event [" + i + "]: " + keyEvents[i].toString());
            }

            writer.println("mLastFewDispatchedMotionEvents:");
            MotionEvent[] motionEvents = new MotionEvent[mLastFewDispatchedMotionEvents.size()];
            mLastFewDispatchedMotionEvents.toArray(motionEvents);
            for (int i = 0; i < motionEvents.length; i++) {
                writer.println("Event [" + i + "]: " + motionEvents[i].toString());
            }
        }
    }
}
