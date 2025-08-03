/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.car.input;

import static android.car.CarOccupantZoneManager.DisplayTypeEnum;

import android.annotation.CallbackExecutor;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.RequiresPermission;
import android.annotation.SystemApi;
import android.car.Car;
import android.car.CarManagerBase;
import android.car.CarOccupantZoneManager;
import android.car.builtin.util.Slogf;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.SparseArray;
import android.view.KeyEvent;

import com.android.internal.annotations.GuardedBy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * This API allows capturing selected input events.
 */
public final class CarInputManager extends CarManagerBase {

    private static final String TAG = CarInputManager.class.getSimpleName();

    private static final boolean DEBUG = false;

    private static final String PERMISSION_FRAMEWORK_MONITOR_INPUT =
            "android.permission.MONITOR_INPUT";

    /**
     * Callback for capturing input events.
     * <p>
     * Events (key, rotary and custom input events) are associated with display types.
     * Display types are defined in {@link android.car.CarOccupantZoneManager}. This manager only
     * accepts the driver display types ({@link CarOccupantZoneManager#DISPLAY_TYPE_MAIN} and
     * {@link CarOccupantZoneManager#DISPLAY_TYPE_INSTRUMENT_CLUSTER}).
     *
     * @hide
     */
    @SystemApi
    public interface CarInputCaptureCallback {
        /**
         * Key events were captured.
         *
         * @param targetDisplayType the display type associated with the events passed as parameter
         * @param keyEvents the key events to process
         */
        default void onKeyEvents(@DisplayTypeEnum int targetDisplayType,
                @NonNull List<KeyEvent> keyEvents) {}

        /**
         * Rotary events were captured.
         *
         * @param targetDisplayType the display type associated with the events passed as parameter
         * @param events the rotary events to process
         */
        default void onRotaryEvents(@DisplayTypeEnum int targetDisplayType,
                @NonNull List<RotaryEvent> events) {}

        /**
         * Capture state for the display has changed due to other client making requests or
         * releasing capture. Client should check {@code activeInputTypes} for which input types
         * are currently captured.
         *
         * @param targetDisplayType the display type associated with the events passed as parameter
         * @param activeInputTypes the input types to watch
         */
        default void onCaptureStateChanged(@DisplayTypeEnum int targetDisplayType,
                @NonNull @InputTypeEnum int[] activeInputTypes) {}

        /**
         * Custom input events were captured.
         *
         * @param targetDisplayType the display type associated with the events passed as parameter
         * @param events the custom input events to process
         */
        default void onCustomInputEvents(@DisplayTypeEnum int targetDisplayType,
                @NonNull List<CustomInputEvent> events) {}
    }

    /**
     * Client will wait for grant if the request is failing due to higher priority client.
     *
     * @hide
     */
    @SystemApi
    public static final int CAPTURE_REQ_FLAGS_ALLOW_DELAYED_GRANT = 0x1;

    /**
     * Client wants to capture the keys for the whole display. This is only allowed to system
     * process.
     *
     * @hide
     */
    @SystemApi
    public static final int CAPTURE_REQ_FLAGS_TAKE_ALL_EVENTS_FOR_DISPLAY = 0x2;

    /** @hide */
    @IntDef(flag = true, prefix = {"CAPTURE_REQ_FLAGS_"}, value = {
            CAPTURE_REQ_FLAGS_ALLOW_DELAYED_GRANT,
            CAPTURE_REQ_FLAGS_TAKE_ALL_EVENTS_FOR_DISPLAY,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface CaptureRequestFlags {}

    /**
     * This is special type to cover all INPUT_TYPE_*. This is used for clients using
     * {@link #CAPTURE_REQ_FLAGS_TAKE_ALL_EVENTS_FOR_DISPLAY} flag.
     *
     * @hide
     */
    @SystemApi
    public static final int INPUT_TYPE_ALL_INPUTS = 1;

    /**
     * This covers rotary input device for navigation.
     */
    public static final int INPUT_TYPE_ROTARY_NAVIGATION = 10;

    /**
     * Volume knob.
     */
    public static final int INPUT_TYPE_ROTARY_VOLUME = 11;

    /**
     * This is the group of keys for DPAD.
     * Included key events are: {@link KeyEvent#KEYCODE_DPAD_UP},
     * {@link KeyEvent#KEYCODE_DPAD_DOWN}, {@link KeyEvent#KEYCODE_DPAD_LEFT},
     * {@link KeyEvent#KEYCODE_DPAD_RIGHT}, {@link KeyEvent#KEYCODE_DPAD_CENTER},
     * {@link KeyEvent#KEYCODE_DPAD_DOWN_LEFT}, {@link KeyEvent#KEYCODE_DPAD_DOWN_RIGHT},
     * {@link KeyEvent#KEYCODE_DPAD_UP_LEFT}, {@link KeyEvent#KEYCODE_DPAD_UP_RIGHT}
     */
    public static final int INPUT_TYPE_DPAD_KEYS = 100;

    /**
     * This is for all {@code KeyEvent#KEYCODE_NAVIGATE_*} keys and {@link KeyEvent#KEYCODE_BACK}.
     */
    public static final int INPUT_TYPE_NAVIGATE_KEYS = 101;

    /**
     * This is for all {@code KeyEvent#KEYCODE_SYSTEM_NAVIGATE_*} keys.
     */
    public static final int INPUT_TYPE_SYSTEM_NAVIGATE_KEYS = 102;

    /**
     * This is for {@code HW_CUSTOM_INPUT} events.
     */
    public static final int INPUT_TYPE_CUSTOM_INPUT_EVENT = 200;

    /**
     * This is for touch mode input type.
     */
    public static final int INPUT_TYPE_TOUCH_SCREEN = 210;

    /**
     * This is for displays that don't support any input type
     */
    public static final int INPUT_TYPE_NONE = -1;

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = "INPUT_TYPE_", value = {
            INPUT_TYPE_ALL_INPUTS,
            INPUT_TYPE_ROTARY_NAVIGATION,
            INPUT_TYPE_ROTARY_VOLUME,
            INPUT_TYPE_DPAD_KEYS,
            INPUT_TYPE_NAVIGATE_KEYS,
            INPUT_TYPE_SYSTEM_NAVIGATE_KEYS,
            INPUT_TYPE_CUSTOM_INPUT_EVENT,
            INPUT_TYPE_TOUCH_SCREEN,
            INPUT_TYPE_NONE,
    })
    @Target({ElementType.TYPE_USE})
    public @interface InputTypeEnum {}

    /**
     * The client's request has succeeded and capture will start.
     *
     * @hide
     */
    @SystemApi
    public static final int INPUT_CAPTURE_RESPONSE_SUCCEEDED = 0;

    /**
     * The client's request has failed due to higher priority client already capturing. If priority
     * for the clients are the same, last client making request will be allowed to capture.
     *
     * @hide
     */
    @SystemApi
    public static final int INPUT_CAPTURE_RESPONSE_FAILED = 1;

    /**
     * This is used when client has set {@link #CAPTURE_REQ_FLAGS_ALLOW_DELAYED_GRANT} in
     * {@code requestFlags} and capturing is blocked due to existing higher priority client.
     * When the higher priority client stops capturing, this client can capture events after
     * getting @link CarInputCaptureCallback#onCaptureStateChanged(int, int[])} call.
     *
     * @hide
     */
    @SystemApi
    public static final int INPUT_CAPTURE_RESPONSE_DELAYED = 2;

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = "INPUT_CAPTURE_RESPONSE_", value = {
            INPUT_CAPTURE_RESPONSE_SUCCEEDED,
            INPUT_CAPTURE_RESPONSE_FAILED,
            INPUT_CAPTURE_RESPONSE_DELAYED
    })
    @Target({ElementType.TYPE_USE})
    public @interface InputCaptureResponseEnum {}

    private final ICarInput mService;
    private final ICarInputCallback mServiceCallback = new ICarInputCallbackImpl(this);

    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private final SparseArray<CallbackHolder> mCarInputCaptureCallbacks = new SparseArray<>(1);

    /**
     * @hide
     */
    public CarInputManager(Car car, IBinder service) {
        super(car);
        mService = ICarInput.Stub.asInterface(service);
    }

    /**
     * Requests capturing of input event for the specified display for all requested input types.
     *
     * <p>The request can fail if a high priority client is holding it. The client can set
     * {@link #CAPTURE_REQ_FLAGS_ALLOW_DELAYED_GRANT} in {@code requestFlags} to wait for the
     * current high priority client to release it.
     *
     * <p>If only some of the input types specified are available, the request will either:
     * <ul>
     * <li>fail, returning {@link #INPUT_CAPTURE_RESPONSE_FAILED}, or
     * <li>be deferred, returning {@link #INPUT_CAPTURE_RESPONSE_DELAYED}, if the
     * {@link #CAPTURE_REQ_FLAGS_ALLOW_DELAYED_GRANT} flag is used.
     * </ul>
     *
     * <p> After {@link #INPUT_CAPTURE_RESPONSE_DELAYED} is returned, no input types are captured
     * until the client receives a {@link CarInputCaptureCallback#onCaptureStateChanged(int, int[])}
     * call with valid input types.
     *
     * <p> The targetDisplayType parameter must only contain driver display types (which are
     * {@link CarOccupantZoneManager#DISPLAY_TYPE_MAIN} and
     * {@link CarOccupantZoneManager#DISPLAY_TYPE_INSTRUMENT_CLUSTER}.
     *
     * <p>Callbacks are grouped and stacked per display types. Only the most recently
     * registered callback will receive the incoming events for the associated display and input
     * types. For instance, if two callbacks are registered against the same display type, on the
     * same {@link CarInputManager} instance, then only the last registered callback will receive
     * events, even if they were registered for different input event types.
     *
     * @throws SecurityException if caller doesn't have one of the following permissions granted:
     *                           {@code android.car.permission.CAR_MONITOR_INPUT} nor
     *                           {@code android.Manifest.permission.MONITOR_INPUT}
     * @throws IllegalArgumentException if targetDisplayType parameter correspond to a non supported
     *                                  display type
     * @throws IllegalArgumentException if inputTypes parameter contains invalid or non supported
     *                                  values
     * @param targetDisplayType the display type to register callback for
     * @param inputTypes the input type to register callback for
     * @param requestFlags the capture request flag
     * @param callback the callback to receive the input events
     * @return the input capture response indicating if registration succeed, failed or delayed
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission(anyOf = {PERMISSION_FRAMEWORK_MONITOR_INPUT,
            Car.PERMISSION_CAR_MONITOR_INPUT})
    @InputCaptureResponseEnum
    public int requestInputEventCapture(@DisplayTypeEnum int targetDisplayType,
            @NonNull @InputTypeEnum int[] inputTypes,
            @CaptureRequestFlags int requestFlags,
            @NonNull CarInputCaptureCallback callback) {
        Handler handler = getEventHandler();
        return requestInputEventCapture(targetDisplayType, inputTypes, requestFlags, handler::post,
                callback);
    }

    /**
     * Works just like {@link CarInputManager#requestInputEventCapture(int, int[], int,
     * CarInputCaptureCallback)} except that callbacks are invoked using
     * the executor passed as parameter.
     *
     * @throws SecurityException if caller doesn't have one of the following permissions granted:
     *                           {@code android.car.permission.CAR_MONITOR_INPUT} nor
     *                           {@code android.Manifest.permission.MONITOR_INPUT}
     * @param targetDisplayType the display type to register callback against
     * @param inputTypes the input type to register callback against
     * @param requestFlags the capture request flag
     * @param executor {@link Executor} to handle the callbacks
     * @param callback the callback to receive the input events
     * @return the input capture response indicating if registration succeed, failed or delayed
     * @see CarInputManager#requestInputEventCapture(int, int[], int, CarInputCaptureCallback)
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission(anyOf = {PERMISSION_FRAMEWORK_MONITOR_INPUT,
            Car.PERMISSION_CAR_MONITOR_INPUT})
    @InputCaptureResponseEnum
    public int requestInputEventCapture(@DisplayTypeEnum int targetDisplayType,
            @NonNull @InputTypeEnum int[] inputTypes,
            @CaptureRequestFlags int requestFlags,
            @NonNull @CallbackExecutor Executor executor,
            @NonNull CarInputCaptureCallback callback) {
        Objects.requireNonNull(executor);
        Objects.requireNonNull(callback);

        synchronized (mLock) {
            mCarInputCaptureCallbacks.put(targetDisplayType,
                    new CallbackHolder(callback, executor));
        }
        try {
            return mService.requestInputEventCapture(mServiceCallback, targetDisplayType,
                    inputTypes, requestFlags);
        } catch (RemoteException e) {
            return handleRemoteExceptionFromCarService(e, INPUT_CAPTURE_RESPONSE_FAILED);
        }
    }

    /**
     * Stops capturing of given display.
     *
     * @hide
     */
    @SystemApi
    public void releaseInputEventCapture(@DisplayTypeEnum int targetDisplayType) {
        CallbackHolder callbackHolder;
        synchronized (mLock) {
            callbackHolder = mCarInputCaptureCallbacks.get(targetDisplayType);
            mCarInputCaptureCallbacks.delete(targetDisplayType);
        }
        if (callbackHolder == null) {
            return;
        }
        try {
            mService.releaseInputEventCapture(mServiceCallback, targetDisplayType);
        } catch (RemoteException e) {
            // ignore
        }
    }

    /**
     * Injects the {@link KeyEvent} passed as parameter against Car Input API.
     * <p>
     * The event parameter display id will be overridden accordingly to the display type also passed
     * as parameter.
     *
     * @param event the key event to inject
     * @param targetDisplayType the display type associated with the key event
     * @throws RemoteException in case of failure when invoking car input service
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission(android.Manifest.permission.INJECT_EVENTS)
    public void injectKeyEvent(@NonNull KeyEvent event, @DisplayTypeEnum int targetDisplayType) {
        try {
            mService.injectKeyEvent(event, targetDisplayType);
        } catch (RemoteException e) {
            e.rethrowFromSystemServer();
        }
    }

    /** @hide */
    @Override
    protected void onCarDisconnected() {
        synchronized (mLock) {
            mCarInputCaptureCallbacks.clear();
        }
    }

    private CallbackHolder getCallback(@DisplayTypeEnum int targetDisplayType) {
        synchronized (mLock) {
            return mCarInputCaptureCallbacks.get(targetDisplayType);
        }
    }

    private void dispatchKeyEvents(@DisplayTypeEnum int targetDisplayType,
            List<KeyEvent> keyEvents) {
        CallbackHolder callbackHolder = getCallback(targetDisplayType);
        if (callbackHolder == null) {
            return;
        }
        callbackHolder.mExecutor.execute(() -> {
            callbackHolder.mCallback.onKeyEvents(targetDisplayType, keyEvents);
        });
    }

    private void dispatchRotaryEvents(@DisplayTypeEnum int targetDisplayType,
            List<RotaryEvent> events) {
        CallbackHolder callbackHolder = getCallback(targetDisplayType);
        if (callbackHolder == null) {
            return;
        }
        callbackHolder.mExecutor.execute(() -> {
            callbackHolder.mCallback.onRotaryEvents(targetDisplayType, events);
        });
    }

    private void dispatchOnCaptureStateChanged(@DisplayTypeEnum int targetDisplayType,
            int[] activeInputTypes) {
        CallbackHolder callbackHolder = getCallback(targetDisplayType);
        if (callbackHolder == null) {
            return;
        }
        callbackHolder.mExecutor.execute(() -> {
            callbackHolder.mCallback.onCaptureStateChanged(targetDisplayType, activeInputTypes);
        });
    }

    private void dispatchCustomInputEvents(@DisplayTypeEnum int targetDisplayType,
            List<CustomInputEvent> events) {
        CallbackHolder callbackHolder = getCallback(targetDisplayType);
        if (callbackHolder == null) {
            return;
        }
        callbackHolder.mExecutor.execute(() -> {
            if (DEBUG) {
                Slogf.d(TAG, "Firing events " + events + " on callback "
                        + callbackHolder.mCallback);
            }
            callbackHolder.mCallback.onCustomInputEvents(targetDisplayType, events);
        });
    }

    private static final class ICarInputCallbackImpl extends ICarInputCallback.Stub {

        private final WeakReference<CarInputManager> mManager;

        private ICarInputCallbackImpl(CarInputManager manager) {
            mManager = new WeakReference<>(manager);
        }

        @Override
        public void onKeyEvents(@DisplayTypeEnum int targetDisplayType,
                @NonNull List<KeyEvent> keyEvents) {
            CarInputManager manager = mManager.get();
            if (manager == null) {
                return;
            }
            manager.dispatchKeyEvents(targetDisplayType, keyEvents);
        }

        @Override
        public void onRotaryEvents(@DisplayTypeEnum int targetDisplayType,
                @NonNull List<RotaryEvent> events) {
            CarInputManager manager = mManager.get();
            if (manager == null) {
                return;
            }
            manager.dispatchRotaryEvents(targetDisplayType, events);
        }

        @Override
        public void onCaptureStateChanged(@DisplayTypeEnum int targetDisplayType,
                @NonNull @InputTypeEnum int[] activeInputTypes) {
            CarInputManager manager = mManager.get();
            if (manager == null) {
                return;
            }
            manager.dispatchOnCaptureStateChanged(targetDisplayType, activeInputTypes);
        }

        @Override
        public void onCustomInputEvents(@DisplayTypeEnum int targetDisplayType,
                @NonNull List<CustomInputEvent> events) {
            CarInputManager manager = mManager.get();
            if (manager == null) {
                return;
            }
            manager.dispatchCustomInputEvents(targetDisplayType, events);
        }
    }

    /**
     * Class used to bind {@link CarInputCaptureCallback} and their associated {@link Executor}.
     */
    private static final class CallbackHolder {

        final CarInputCaptureCallback mCallback;

        final Executor mExecutor;

        CallbackHolder(CarInputCaptureCallback callback, Executor executor) {
            mCallback = callback;
            mExecutor = executor;
        }
    }
}
