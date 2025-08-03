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

package com.android.car;

import static android.car.CarOccupantZoneManager.DisplayTypeEnum;
import static android.car.PlatformVersion.VERSION_CODES.UPSIDE_DOWN_CAKE_0;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_SWITCHING;

import static com.android.car.BuiltinPackageDependency.CAR_ACCESSIBILITY_SERVICE_CLASS;
import static com.android.car.CarServiceUtils.getCommonHandlerThread;
import static com.android.car.CarServiceUtils.getContentResolverForUser;
import static com.android.car.CarServiceUtils.isEventOfType;
import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;
import static com.android.internal.util.Preconditions.checkArgument;

import static java.util.Objects.requireNonNull;

import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.car.Car;
import android.car.CarOccupantZoneManager;
import android.car.CarOccupantZoneManager.OccupantZoneInfo;
import android.car.CarProjectionManager;
import android.car.VehicleAreaSeat;
import android.car.builtin.input.InputManagerHelper;
import android.car.builtin.util.AssistUtilsHelper;
import android.car.builtin.util.AssistUtilsHelper.VoiceInteractionSessionShowCallbackHelper;
import android.car.builtin.util.Slogf;
import android.car.builtin.view.InputEventHelper;
import android.car.builtin.view.KeyEventHelper;
import android.car.input.CarInputManager;
import android.car.input.CustomInputEvent;
import android.car.input.ICarInput;
import android.car.input.ICarInputCallback;
import android.car.input.RotaryEvent;
import android.car.user.CarUserManager.UserLifecycleListener;
import android.car.user.UserLifecycleEventFilter;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.hardware.input.InputManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.CallLog.Calls;
import android.provider.Settings;
import android.telecom.TelecomManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.util.proto.ProtoOutputStream;
import android.view.Display;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import com.android.car.bluetooth.CarBluetoothService;
import com.android.car.hal.InputHalService;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.common.UserHelperLite;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.car.power.CarPowerManagementService;
import com.android.car.systeminterface.SystemInterface;
import com.android.car.user.CarUserService;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * CarInputService monitors and handles input event through vehicle HAL.
 */
public class CarInputService extends ICarInput.Stub
        implements CarServiceBase, InputHalService.InputListener {
    public static final String ENABLED_ACCESSIBILITY_SERVICES_SEPARATOR = ":";

    private static final int MAX_RETRIES_FOR_ENABLING_ACCESSIBILITY_SERVICES = 5;

    @VisibleForTesting
    static final String TAG = CarLog.TAG_INPUT;

    private static final boolean DBG = Slogf.isLoggable(TAG, Log.DEBUG);

    @VisibleForTesting
    static final String LONG_PRESS_TIMEOUT = "long_press_timeout";

    /** An interface to receive {@link KeyEvent}s as they occur. */
    public interface KeyEventListener {
        /** Called when a key event occurs. */
        // TODO(b/247170915): This method is no needed anymore, please remove and use
        // onKeyEvent(KeyEvent event, intDisplayType, int seat)
        default void onKeyEvent(KeyEvent event) {
        }

        /**
         * Called when a key event occurs with seat.
         *
         * @param event       the key event that occurred
         * @param displayType target display the event is associated with should be one of
         *                    {@link CarOccupantZoneManager#DISPLAY_TYPE_MAIN},
         *                    {@link CarOccupantZoneManager#DISPLAY_TYPE_INSTRUMENT_CLUSTER},
         *                    {@link CarOccupantZoneManager#DISPLAY_TYPE_HUD},
         *                    {@link CarOccupantZoneManager#DISPLAY_TYPE_INPUT},
         *                    {@link CarOccupantZoneManager#DISPLAY_TYPE_AUXILIARY},
         * @param seat        the area id this event is occurring from
         */
        default void onKeyEvent(KeyEvent event, @DisplayTypeEnum int displayType,
                @VehicleAreaSeat.Enum int seat) {
            // No op
        }
    }

    /** An interface to receive {@link MotionEvent}s as they occur. */
    public interface MotionEventListener {
        /** Called when a motion event occurs. */
        void onMotionEvent(MotionEvent event);
    }

    private final class KeyPressTimer {
        private final Runnable mLongPressRunnable;
        private final Runnable mCallback = this::onTimerExpired;
        private final IntSupplier mLongPressDelaySupplier;

        @GuardedBy("CarInputService.this.mLock")
        private final Handler mHandler;
        @GuardedBy("CarInputService.this.mLock")
        private boolean mDown;
        @GuardedBy("CarInputService.this.mLock")
        private boolean mLongPress = false;

        KeyPressTimer(
                Handler handler, IntSupplier longPressDelaySupplier, Runnable longPressRunnable) {
            mHandler = handler;
            mLongPressRunnable = longPressRunnable;
            mLongPressDelaySupplier = longPressDelaySupplier;
        }

        /** Marks that a key was pressed, and starts the long-press timer. */
        void keyDown() {
            synchronized (mLock) {
                mDown = true;
                mLongPress = false;
                mHandler.removeCallbacks(mCallback);
                mHandler.postDelayed(mCallback, mLongPressDelaySupplier.getAsInt());
            }
        }

        /**
         * Marks that a key was released, and stops the long-press timer.
         * <p>
         * Returns true if the press was a long-press.
         */
        boolean keyUp() {
            synchronized (mLock) {
                mHandler.removeCallbacks(mCallback);
                mDown = false;
                return mLongPress;
            }
        }

        private void onTimerExpired() {
            synchronized (mLock) {
                // If the timer expires after key-up, don't retroactively make the press long.
                if (!mDown) {
                    return;
                }
                mLongPress = true;
            }
            mLongPressRunnable.run();
        }
    }

    private final VoiceInteractionSessionShowCallbackHelper mShowCallback;
    static final VoiceInteractionSessionShowCallbackHelper sDefaultShowCallback =
            new VoiceInteractionSessionShowCallbackHelper() {
                @Override
                public void onFailed() {
                    Slogf.w(TAG, "Failed to show VoiceInteractionSession");
                }

                @Override
                public void onShown() {
                    Slogf.d(TAG, "VoiceInteractionSessionShowCallbackHelper onShown()");
                }
            };

    private final Context mContext;
    private final InputHalService mInputHalService;
    private final CarUserService mUserService;
    private final CarOccupantZoneService mCarOccupantZoneService;
    private final CarBluetoothService mCarBluetoothService;
    private final CarPowerManagementService mCarPowerService;
    private final TelecomManager mTelecomManager;
    private final SystemInterface mSystemInterface;

    // The default handler for main-display key events. By default, injects the events into
    // the input queue via InputManager, but can be overridden for testing.
    private final KeyEventListener mDefaultKeyHandler;
    // The default handler for main-display motion events. By default, injects the events into
    // the input queue via InputManager, but can be overridden for testing.
    private final MotionEventListener mDefaultMotionHandler;
    // The supplier for the last-called number. By default, gets the number from the call log.
    // May be overridden for testing.
    private final Supplier<String> mLastCalledNumberSupplier;
    // The supplier for the system long-press delay, in milliseconds. By default, gets the value
    // from Settings.Secure for the current user, falling back to the system-wide default
    // long-press delay defined in ViewConfiguration. May be overridden for testing.
    private final IntSupplier mLongPressDelaySupplier;
    // ComponentName of the RotaryService.
    private final String mRotaryServiceComponentName;

    private final BooleanSupplier mShouldCallButtonEndOngoingCallSupplier;

    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private CarProjectionManager.ProjectionKeyEventHandler mProjectionKeyEventHandler;

    @GuardedBy("mLock")
    private final BitSet mProjectionKeyEventsSubscribed = new BitSet();

    private final KeyPressTimer mVoiceKeyTimer;
    private final KeyPressTimer mCallKeyTimer;

    @GuardedBy("mLock")
    private KeyEventListener mInstrumentClusterKeyListener;

    @GuardedBy("mLock")
    private final SparseArray<KeyEventListener> mListeners = new SparseArray<>();

    private final InputCaptureClientController mCaptureController;

    private int mDriverSeat = VehicleAreaSeat.SEAT_UNKNOWN;

    private boolean mHasDriver;

    // key: seat, value: power key handled by ACTION_DOWN.
    // {@code true} if the screen was turned on with the power key ACTION_DOWN. In this case,
    // we need to block the power key's ACTION_UP to prevent the device from going back to sleep.
    // When ACTION_UP, it is released with {@code false}.
    private SparseBooleanArray mPowerKeyHandled = new SparseBooleanArray();

    // The default handler for special keys. The behavior of the keys is implemented in this
    // service. It can be overridden by {@link #registerKeyEventListener}.
    private final KeyEventListener mDefaultSpecialKeyHandler = new KeyEventListener() {
        @Override
        public void onKeyEvent(KeyEvent event, @DisplayTypeEnum int displayType,
                @VehicleAreaSeat.Enum int seat) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_HOME:
                    handleHomeKey(event, displayType, seat);
                    break;
                case KeyEvent.KEYCODE_POWER:
                    handlePowerKey(event, displayType, seat);
                    break;
                default:
                    Slogf.e(TAG, "Key event %s is not supported by special key handler",
                            KeyEvent.keyCodeToString(event.getKeyCode()));
                    break;
            }
        }
    };

    private final UserLifecycleListener mUserLifecycleListener = event -> {
        if (!isEventOfType(TAG, event, USER_LIFECYCLE_EVENT_TYPE_SWITCHING)) {
            return;
        }
        Slogf.d(TAG, "CarInputService.onEvent(%s)", event);

        updateCarAccessibilityServicesSettings(event.getUserId());
    };

    private static int getViewLongPressDelay(Context context) {
        return Settings.Secure.getInt(getContentResolverForUser(context,
                UserHandle.CURRENT.getIdentifier()), LONG_PRESS_TIMEOUT,
                ViewConfiguration.getLongPressTimeout());
    }

    public CarInputService(Context context, InputHalService inputHalService,
            CarUserService userService, CarOccupantZoneService occupantZoneService,
            CarBluetoothService bluetoothService, CarPowerManagementService carPowerService,
            SystemInterface systemInterface) {
        this(context, inputHalService, userService, occupantZoneService, bluetoothService,
                carPowerService, systemInterface,
                new Handler(getCommonHandlerThread().getLooper()),
                context.getSystemService(TelecomManager.class),
                new KeyEventListener() {
                    @Override
                    public void onKeyEvent(KeyEvent event, @DisplayTypeEnum int displayType,
                            @VehicleAreaSeat.Enum int seat) {
                        InputManagerHelper.injectInputEvent(
                                context.getSystemService(InputManager.class), event);
                    }
                },
                /* defaultMotionHandler= */ event -> InputManagerHelper.injectInputEvent(
                        context.getSystemService(InputManager.class), event),
                /* lastCalledNumberSupplier= */ () -> Calls.getLastOutgoingCall(context),
                /* longPressDelaySupplier= */ () -> getViewLongPressDelay(context),
                /* shouldCallButtonEndOngoingCallSupplier= */ () -> context.getResources()
                        .getBoolean(R.bool.config_callButtonEndsOngoingCall),
                new InputCaptureClientController(context), sDefaultShowCallback);
    }

    @VisibleForTesting
    CarInputService(Context context, InputHalService inputHalService, CarUserService userService,
            CarOccupantZoneService occupantZoneService, CarBluetoothService bluetoothService,
            CarPowerManagementService carPowerService, SystemInterface systemInterface,
            Handler handler, TelecomManager telecomManager,
            KeyEventListener defaultKeyHandler, MotionEventListener defaultMotionHandler,
            Supplier<String> lastCalledNumberSupplier, IntSupplier longPressDelaySupplier,
            BooleanSupplier shouldCallButtonEndOngoingCallSupplier,
            InputCaptureClientController captureController,
            VoiceInteractionSessionShowCallbackHelper showCallback) {
        super();
        mContext = context;
        mCaptureController = captureController;
        mInputHalService = inputHalService;
        mUserService = userService;
        mCarOccupantZoneService = occupantZoneService;
        mCarBluetoothService = bluetoothService;
        mCarPowerService = carPowerService;
        mSystemInterface = systemInterface;
        mTelecomManager = telecomManager;
        mDefaultKeyHandler = defaultKeyHandler;
        mDefaultMotionHandler = defaultMotionHandler;
        mLastCalledNumberSupplier = lastCalledNumberSupplier;
        mLongPressDelaySupplier = longPressDelaySupplier;
        mShowCallback = showCallback;

        mVoiceKeyTimer = new KeyPressTimer(
                handler, longPressDelaySupplier, this::handleVoiceAssistLongPress);
        mCallKeyTimer = new KeyPressTimer(handler, longPressDelaySupplier,
                this::handleCallLongPress);

        mRotaryServiceComponentName = mContext.getString(R.string.rotaryService);
        mShouldCallButtonEndOngoingCallSupplier = shouldCallButtonEndOngoingCallSupplier;

        registerKeyEventListener(mDefaultSpecialKeyHandler,
                Arrays.asList(KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_POWER));
    }

    /**
     * Set projection key event listener. If null, unregister listener.
     */
    public void setProjectionKeyEventHandler(
            @Nullable CarProjectionManager.ProjectionKeyEventHandler listener,
            @Nullable BitSet events) {
        synchronized (mLock) {
            mProjectionKeyEventHandler = listener;
            mProjectionKeyEventsSubscribed.clear();
            if (events != null) {
                mProjectionKeyEventsSubscribed.or(events);
            }
        }
    }

    /**
     * This method registers a keyEventListener to listen on key events that it is interested in.
     *
     * @param listener           the listener to be registered
     * @param keyCodesOfInterest the events of interest that the listener is interested in
     * @throws IllegalArgumentException when an event is already registered to another listener
     */
    public void registerKeyEventListener(KeyEventListener listener,
            List<Integer> keyCodesOfInterest) {
        requireNonNull(listener, "Key event listener can not be null");
        requireNonNull(keyCodesOfInterest, "Key events of interest can not be null");
        checkArgument(!keyCodesOfInterest.isEmpty(),
                "Key events of interest can not be empty");
        synchronized (mLock) {
            // Check for invalid key codes
            for (int i = 0; i < keyCodesOfInterest.size(); i++) {
                if (mListeners.contains(keyCodesOfInterest.get(i))
                        && mListeners.get(keyCodesOfInterest.get(i)) != mDefaultSpecialKeyHandler) {
                    throw new IllegalArgumentException("Event "
                            + KeyEvent.keyCodeToString(keyCodesOfInterest.get(i))
                            + " already registered to another listener");
                }
            }
            for (int i = 0; i < keyCodesOfInterest.size(); i++) {
                mListeners.put(keyCodesOfInterest.get(i), listener);
            }
        }
    }

    /**
     * Unregisters the key event listener for all the keys it currently listen to
     *
     * @param listener the listener to be unregistered
     */
    public boolean unregisterKeyEventListener(KeyEventListener listener) {
        requireNonNull(listener, "Key event listener can not be null");
        synchronized (mLock) {
            var keysToRemove = new ArrayList<Integer>();
            for (int c = 0; c < mListeners.size(); c++) {
                if (!mListeners.valueAt(c).equals(listener)) {
                    continue;
                }
                keysToRemove.add(mListeners.keyAt(c));
            }
            if (keysToRemove.isEmpty()) {
                Slogf.w(TAG, "Failed to unregister listener ({%s} was not registered)",
                        listener);
                return false;
            }
            for (int c = 0; c < keysToRemove.size(); c++) {
                mListeners.delete(keysToRemove.get(c));
            }
        }
        return true;
    }

    /**
     * Sets the instrument cluster key event listener.
     */
    public void setInstrumentClusterKeyListener(KeyEventListener listener) {
        synchronized (mLock) {
            mInstrumentClusterKeyListener = listener;
        }
    }

    @Override
    public void init() {
        if (!mInputHalService.isKeyInputSupported()) {
            Slogf.w(TAG, "Hal does not support key input.");
            return;
        }
        Slogf.d(TAG, "Hal supports key input.");
        mInputHalService.setInputListener(this);
        UserLifecycleEventFilter userSwitchingEventFilter = new UserLifecycleEventFilter.Builder()
                .addEventType(USER_LIFECYCLE_EVENT_TYPE_SWITCHING).build();
        mUserService.addUserLifecycleListener(userSwitchingEventFilter, mUserLifecycleListener);
        mDriverSeat = mCarOccupantZoneService.getDriverSeat();
        mHasDriver = (mDriverSeat != VehicleAreaSeat.SEAT_UNKNOWN);
    }

    @Override
    public void release() {
        synchronized (mLock) {
            mProjectionKeyEventHandler = null;
            mProjectionKeyEventsSubscribed.clear();
            mInstrumentClusterKeyListener = null;
            mListeners.clear();
        }
        mUserService.removeUserLifecycleListener(mUserLifecycleListener);
    }

    @Override
    public void onKeyEvent(KeyEvent event, @DisplayTypeEnum int targetDisplayType) {
        onKeyEvent(event, targetDisplayType, mDriverSeat);
    }

    /**
     * Called for key event
     *
     * @throws IllegalArgumentException if the passed seat is an unknown seat and the driver seat is
     *                                  not an unknown seat
     */
    @Override
    public void onKeyEvent(KeyEvent event, @DisplayTypeEnum int targetDisplayType,
            @VehicleAreaSeat.Enum int seat) {
        if (mHasDriver && seat == VehicleAreaSeat.SEAT_UNKNOWN) {
            // To support {@link #onKeyEvent(KeyEvent, int)}, we need to check whether the driver
            // exists or not.
            // For example, for a passenger-only system, the driver seat might be SEAT_UNKNOWN.
            // In this case, no exception should be occurred.
            throw new IllegalArgumentException("Unknown seat");
        }

        // Update user activity information to car power management service.
        notifyUserActivity(event, targetDisplayType, seat);

        // Driver key events are handled the same as HW_KEY_INPUT.
        if (seat == mDriverSeat) {
            dispatchKeyEventForDriver(event, targetDisplayType);
            return;
        }

        // Notifies the listeners of the key event.
        notifyKeyEventListener(event, targetDisplayType, seat);
    }

    private void dispatchKeyEventForDriver(KeyEvent event, @DisplayTypeEnum int targetDisplayType) {
        // Special case key code that have special "long press" handling for automotive
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_VOICE_ASSIST:
                // TODO: b/288107028 - Pass target display type to handleVoiceAssistKey()
                // when passenger displays support voice assist keys
                handleVoiceAssistKey(event, targetDisplayType);
                return;
            case KeyEvent.KEYCODE_CALL:
                handleCallKey(event);
                return;
            default:
                break;
        }

        assignDisplayId(event, targetDisplayType);

        // Allow specifically targeted keys to be routed to the cluster
        if (targetDisplayType == CarOccupantZoneManager.DISPLAY_TYPE_INSTRUMENT_CLUSTER
                && handleInstrumentClusterKey(event)) {
            return;
        }
        if (mCaptureController.onKeyEvent(targetDisplayType, event)) {
            return;
        }
        mDefaultKeyHandler.onKeyEvent(event, targetDisplayType, mDriverSeat);
    }

    /**
     * Called for motion event
     */
    @Override
    public void onMotionEvent(MotionEvent event, @DisplayTypeEnum int targetDisplayType,
            @VehicleAreaSeat.Enum int seat) {
        if (!Car.getPlatformVersion().isAtLeast(UPSIDE_DOWN_CAKE_0)) {
            Slogf.e(TAG, "Motion event for passenger is only supported from %s",
                    UPSIDE_DOWN_CAKE_0);
            return;
        }

        if (seat == VehicleAreaSeat.SEAT_UNKNOWN) {
            throw new IllegalArgumentException("Unknown seat");
        }

        notifyUserActivity(event, targetDisplayType, seat);
        assignDisplayIdForSeat(event, targetDisplayType, seat);
        mDefaultMotionHandler.onMotionEvent(event);
    }

    private void notifyKeyEventListener(KeyEvent event, int targetDisplay, int seat) {
        KeyEventListener keyEventListener;
        synchronized (mLock) {
            keyEventListener = mListeners.get(event.getKeyCode());
        }
        if (keyEventListener == null) {
            if (DBG) {
                Slogf.d(TAG, "Key event listener not found for event %s",
                        KeyEvent.keyCodeToString(event.getKeyCode()));
            }
            // If there is no listener for the key event, it is injected into the core system.
            keyEventListener = mDefaultKeyHandler;
        }
        assignDisplayIdForSeat(event, targetDisplay, seat);
        keyEventListener.onKeyEvent(event, targetDisplay, seat);
    }

    private void assignDisplayId(KeyEvent event, @DisplayTypeEnum int targetDisplayType) {
        // Setting display id for driver user id (currently MAIN and CLUSTER display types are
        // linked to driver user only)
        int newDisplayId = mCarOccupantZoneService.getDisplayIdForDriver(targetDisplayType);

        // Display id is overridden even if already set.
        KeyEventHelper.setDisplayId(event, newDisplayId);
    }

    private void assignDisplayIdForSeat(InputEvent event, @DisplayTypeEnum int targetDisplayType,
            @VehicleAreaSeat.Enum int seat) {
        int newDisplayId = getDisplayIdForSeat(targetDisplayType, seat);

        InputEventHelper.setDisplayId(event, newDisplayId);
    }

    private int getDisplayIdForSeat(@DisplayTypeEnum int targetDisplayType,
            @VehicleAreaSeat.Enum int seat) {
        int zoneId = mCarOccupantZoneService.getOccupantZoneIdForSeat(seat);
        return mCarOccupantZoneService.getDisplayForOccupant(zoneId, targetDisplayType);
    }

    /**
     * Notifies the car power manager that user activity happened.
     */
    private void notifyUserActivity(InputEvent event, @DisplayTypeEnum int targetDisplayType,
            @VehicleAreaSeat.Enum int seat) {
        int displayId = getDisplayIdForSeat(targetDisplayType, seat);
        if (displayId == Display.INVALID_DISPLAY) {
            return;
        }
        mCarPowerService.notifyUserActivity(displayId, event.getEventTime());
    }

    @Override
    public void onRotaryEvent(RotaryEvent event, @DisplayTypeEnum int targetDisplay) {
        if (!mCaptureController.onRotaryEvent(targetDisplay, event)) {
            List<KeyEvent> keyEvents = rotaryEventToKeyEvents(event);
            for (KeyEvent keyEvent : keyEvents) {
                onKeyEvent(keyEvent, targetDisplay);
            }
        }
    }

    @Override
    public void onCustomInputEvent(CustomInputEvent event) {
        if (!mCaptureController.onCustomInputEvent(event)) {
            Slogf.w(TAG, "Failed to propagate (%s)", event);
            return;
        }
        Slogf.d(TAG, "Succeed injecting (%s)", event);
    }

    private static List<KeyEvent> rotaryEventToKeyEvents(RotaryEvent event) {
        int numClicks = event.getNumberOfClicks();
        int numEvents = numClicks * 2; // up / down per each click
        boolean clockwise = event.isClockwise();
        int keyCode;
        switch (event.getInputType()) {
            case CarInputManager.INPUT_TYPE_ROTARY_NAVIGATION:
                keyCode = clockwise
                        ? KeyEvent.KEYCODE_NAVIGATE_NEXT
                        : KeyEvent.KEYCODE_NAVIGATE_PREVIOUS;
                break;
            case CarInputManager.INPUT_TYPE_ROTARY_VOLUME:
                keyCode = clockwise
                        ? KeyEvent.KEYCODE_VOLUME_UP
                        : KeyEvent.KEYCODE_VOLUME_DOWN;
                break;
            default:
                Slogf.e(TAG, "Unknown rotary input type: %d", event.getInputType());
                return Collections.EMPTY_LIST;
        }
        ArrayList<KeyEvent> keyEvents = new ArrayList<>(numEvents);
        for (int i = 0; i < numClicks; i++) {
            long uptime = event.getUptimeMillisForClick(i);
            KeyEvent downEvent = createKeyEvent(/* down= */ true, uptime, uptime, keyCode);
            KeyEvent upEvent = createKeyEvent(/* down= */ false, uptime, uptime, keyCode);
            keyEvents.add(downEvent);
            keyEvents.add(upEvent);
        }
        return keyEvents;
    }

    private static KeyEvent createKeyEvent(boolean down, long downTime, long eventTime,
            int keyCode) {
        return new KeyEvent(
                downTime,
                eventTime,
                /* action= */ down ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP,
                keyCode,
                /* repeat= */ 0,
                /* metaState= */ 0,
                /* deviceId= */ 0,
                /* scancode= */ 0,
                /* flags= */ 0,
                InputDevice.SOURCE_CLASS_BUTTON);
    }

    @Override
    public int requestInputEventCapture(ICarInputCallback callback,
            @DisplayTypeEnum int targetDisplayType,
            int[] inputTypes, int requestFlags) {
        return mCaptureController.requestInputEventCapture(callback, targetDisplayType, inputTypes,
                requestFlags);
    }

    @Override
    public void releaseInputEventCapture(ICarInputCallback callback,
            @DisplayTypeEnum int targetDisplayType) {
        mCaptureController.releaseInputEventCapture(callback, targetDisplayType);
    }

    /**
     * Injects the {@link KeyEvent} passed as parameter against Car Input API.
     * <p>
     * The event's display id will be overwritten accordingly to the display type (it will be
     * retrieved from {@link CarOccupantZoneService}).
     *
     * @param event             the event to inject
     * @param targetDisplayType the display type associated with the event
     * @throws SecurityException when caller doesn't have INJECT_EVENTS permission granted
     */
    @Override
    public void injectKeyEvent(KeyEvent event, @DisplayTypeEnum int targetDisplayType) {
        // Permission check
        if (PackageManager.PERMISSION_GRANTED != mContext.checkCallingOrSelfPermission(
                android.Manifest.permission.INJECT_EVENTS)) {
            throw new SecurityException("Injecting KeyEvent requires INJECT_EVENTS permission");
        }

        long token = Binder.clearCallingIdentity();
        try {
            // Redirect event to onKeyEvent
            onKeyEvent(event, targetDisplayType);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    /**
     * Injects the {@link KeyEvent} passed as parameter against Car Input API.
     * <p>
     * The event's display id will be overwritten accordingly to the display type (it will be
     * retrieved from {@link CarOccupantZoneService}).
     *
     * @param event             the event to inject
     * @param targetDisplayType the display type associated with the event
     * @param seat              the seat associated with the event
     * @throws SecurityException when caller doesn't have INJECT_EVENTS permission granted
     */
    public void injectKeyEventForSeat(KeyEvent event, @DisplayTypeEnum int targetDisplayType,
            @VehicleAreaSeat.Enum int seat) {
        // Permission check
        if (PackageManager.PERMISSION_GRANTED != mContext.checkCallingOrSelfPermission(
                android.Manifest.permission.INJECT_EVENTS)) {
            throw new SecurityException("Injecting KeyEvent requires INJECT_EVENTS permission");
        }

        long token = Binder.clearCallingIdentity();
        try {
            // Redirect event to onKeyEvent
            onKeyEvent(event, targetDisplayType, seat);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    /**
     * Injects the {@link MotionEvent} passed as parameter against Car Input API.
     * <p>
     * The event's display id will be overwritten accordingly to the display type (it will be
     * retrieved from {@link CarOccupantZoneService}).
     *
     * @param event             the event to inject
     * @param targetDisplayType the display type associated with the event
     * @param seat              the seat associated with the event
     * @throws SecurityException when caller doesn't have INJECT_EVENTS permission granted
     */
    public void injectMotionEventForSeat(MotionEvent event, @DisplayTypeEnum int targetDisplayType,
            @VehicleAreaSeat.Enum int seat) {
        // Permission check
        if (PackageManager.PERMISSION_GRANTED != mContext.checkCallingOrSelfPermission(
                android.Manifest.permission.INJECT_EVENTS)) {
            throw new SecurityException("Injecting MotionEvent requires INJECT_EVENTS permission");
        }

        long token = Binder.clearCallingIdentity();
        try {
            // Redirect event to onMotionEvent
            onMotionEvent(event, targetDisplayType, seat);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    private void handleVoiceAssistKey(KeyEvent event, @DisplayTypeEnum int targetDisplayType) {
        int action = event.getAction();
        if (action == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            mVoiceKeyTimer.keyDown();
            dispatchProjectionKeyEvent(CarProjectionManager.KEY_EVENT_VOICE_SEARCH_KEY_DOWN);
        } else if (action == KeyEvent.ACTION_UP) {
            if (mVoiceKeyTimer.keyUp()) {
                // Long press already handled by handleVoiceAssistLongPress(), nothing more to do.
                // Hand it off to projection, if it's interested, otherwise we're done.
                dispatchProjectionKeyEvent(
                        CarProjectionManager.KEY_EVENT_VOICE_SEARCH_LONG_PRESS_KEY_UP);
                return;
            }

            if (dispatchProjectionKeyEvent(
                    CarProjectionManager.KEY_EVENT_VOICE_SEARCH_SHORT_PRESS_KEY_UP)) {
                return;
            }

            // TODO: b/288107028 - Pass the actual target display type to onKeyEvent
            // when passenger displays support voice assist keys
            if (mCaptureController.onKeyEvent(targetDisplayType, event)) {
                return;
            }

            launchDefaultVoiceAssistantHandler();
        }
    }

    private void handleVoiceAssistLongPress() {
        // If projection wants this event, let it take it.
        if (dispatchProjectionKeyEvent(
                CarProjectionManager.KEY_EVENT_VOICE_SEARCH_LONG_PRESS_KEY_DOWN)) {
            return;
        }
        // Otherwise, try to launch voice recognition on a BT device.
        if (launchBluetoothVoiceRecognition()) {
            return;
        }
        // Finally, fallback to the default voice assist handling.
        launchDefaultVoiceAssistantHandler();
    }

    private void handleCallKey(KeyEvent event) {
        int action = event.getAction();
        if (action == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            mCallKeyTimer.keyDown();
            dispatchProjectionKeyEvent(CarProjectionManager.KEY_EVENT_CALL_KEY_DOWN);
        } else if (action == KeyEvent.ACTION_UP) {
            if (mCallKeyTimer.keyUp()) {
                // Long press already handled by handleCallLongPress(), nothing more to do.
                // Hand it off to projection, if it's interested, otherwise we're done.
                dispatchProjectionKeyEvent(CarProjectionManager.KEY_EVENT_CALL_LONG_PRESS_KEY_UP);
                return;
            }

            if (acceptCallIfRinging()) {
                // Ringing call answered, nothing more to do.
                return;
            }

            if (mShouldCallButtonEndOngoingCallSupplier.getAsBoolean() && endCall()) {
                // On-going call ended, nothing more to do.
                return;
            }

            if (dispatchProjectionKeyEvent(
                    CarProjectionManager.KEY_EVENT_CALL_SHORT_PRESS_KEY_UP)) {
                return;
            }

            launchDialerHandler();
        }
    }

    private void handleCallLongPress() {
        // Long-press answers call if ringing, same as short-press.
        if (acceptCallIfRinging()) {
            return;
        }

        if (mShouldCallButtonEndOngoingCallSupplier.getAsBoolean() && endCall()) {
            return;
        }

        if (dispatchProjectionKeyEvent(CarProjectionManager.KEY_EVENT_CALL_LONG_PRESS_KEY_DOWN)) {
            return;
        }

        dialLastCallHandler();
    }

    private void handlePowerKey(KeyEvent event, @DisplayTypeEnum int targetDisplayType,
            @VehicleAreaSeat.Enum int seat) {
        if (DBG) {
            Slogf.d(TAG, "called handlePowerKey: DisplayType=%d, VehicleAreaSeat=%d",
                    targetDisplayType, seat);
        }

        int displayId = getDisplayIdForSeat(targetDisplayType, seat);
        if (displayId == Display.INVALID_DISPLAY) {
            Slogf.e(TAG, "Failed to set display power state : Invalid display type=%d, seat=%d",
                    targetDisplayType, seat);
            return;
        }

        boolean isOn = mSystemInterface.isDisplayEnabled(displayId);

        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            if (!isOn) {
                mCarPowerService.setDisplayPowerState(displayId, /* enable= */ true);
                setPowerKeyHandled(seat, /* handled= */ true);
            }
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
            if (isOn && !isPowerKeyHandled(seat)) {
                mCarPowerService.setDisplayPowerState(displayId, /* enable= */ false);
            }
            setPowerKeyHandled(seat, /* handled= */ false);
        }
    }

    private boolean isPowerKeyHandled(@VehicleAreaSeat.Enum int seat) {
        return mPowerKeyHandled.get(seat);
    }

    private void setPowerKeyHandled(@VehicleAreaSeat.Enum int seat, boolean handled) {
        mPowerKeyHandled.put(seat, handled);
    }

    private void handleHomeKey(KeyEvent event, @DisplayTypeEnum int targetDisplayType,
            @VehicleAreaSeat.Enum int seat) {
        if (DBG) {
            Slogf.d(TAG, "called handleHomeKey: DisplayType=%d, VehicleAreaSeat=%d",
                    targetDisplayType, seat);
        }
        if (event.getAction() == KeyEvent.ACTION_UP) {
            int zoneId = mCarOccupantZoneService.getOccupantZoneIdForSeat(seat);
            if (zoneId == OccupantZoneInfo.INVALID_ZONE_ID) {
                Slogf.w(TAG, "Failed to get occupant zone id : Invalid seat=%d", seat);
                return;
            }

            int userId = mCarOccupantZoneService.getUserForOccupant(zoneId);
            int displayId = mCarOccupantZoneService.getDisplayForOccupant(zoneId,
                    targetDisplayType);
            CarServiceUtils.startHomeForUserAndDisplay(mContext, userId, displayId);
        }
    }

    private boolean dispatchProjectionKeyEvent(@CarProjectionManager.KeyEventNum int event) {
        CarProjectionManager.ProjectionKeyEventHandler projectionKeyEventHandler;
        synchronized (mLock) {
            projectionKeyEventHandler = mProjectionKeyEventHandler;
            if (projectionKeyEventHandler == null || !mProjectionKeyEventsSubscribed.get(event)) {
                // No event handler, or event handler doesn't want this event - we're done.
                return false;
            }
        }

        projectionKeyEventHandler.onKeyEvent(event);
        return true;
    }

    private void launchDialerHandler() {
        Slogf.i(TAG, "call key, launch dialer intent");
        Intent dialerIntent = new Intent(Intent.ACTION_DIAL);
        mContext.startActivityAsUser(dialerIntent, UserHandle.CURRENT);
    }

    private void dialLastCallHandler() {
        Slogf.i(TAG, "call key, dialing last call");

        String lastNumber = mLastCalledNumberSupplier.get();
        if (!TextUtils.isEmpty(lastNumber)) {
            Intent callLastNumberIntent = new Intent(Intent.ACTION_CALL)
                    .setData(Uri.fromParts("tel", lastNumber, /* fragment= */ null))
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivityAsUser(callLastNumberIntent, UserHandle.CURRENT);
        }
    }

    private boolean acceptCallIfRinging() {
        if (mTelecomManager != null && mTelecomManager.isRinging()) {
            Slogf.i(TAG, "call key while ringing. Answer the call!");
            mTelecomManager.acceptRingingCall();
            return true;
        }
        return false;
    }

    private boolean endCall() {
        if (mTelecomManager != null && mTelecomManager.isInCall()) {
            Slogf.i(TAG, "End the call!");
            mTelecomManager.endCall();
            return true;
        }
        return false;
    }

    private boolean isBluetoothVoiceRecognitionEnabled() {
        Resources res = mContext.getResources();
        return res.getBoolean(R.bool.enableLongPressBluetoothVoiceRecognition);
    }

    private boolean launchBluetoothVoiceRecognition() {
        if (isBluetoothVoiceRecognitionEnabled()) {
            Slogf.d(TAG, "Attempting to start Bluetooth Voice Recognition.");
            return mCarBluetoothService.startBluetoothVoiceRecognition();
        }
        Slogf.d(TAG, "Unable to start Bluetooth Voice Recognition, it is not enabled.");
        return false;
    }

    private void launchDefaultVoiceAssistantHandler() {
        Slogf.d(TAG, "voice key, invoke AssistUtilsHelper");

        if (!AssistUtilsHelper.showPushToTalkSessionForActiveService(mContext, mShowCallback)) {
            Slogf.w(TAG, "Unable to retrieve assist component for current user");
        }
    }

    /**
     * @return false if the KeyEvent isn't consumed because there is no
     * InstrumentClusterKeyListener.
     */
    private boolean handleInstrumentClusterKey(KeyEvent event) {
        KeyEventListener listener;
        synchronized (mLock) {
            listener = mInstrumentClusterKeyListener;
        }
        if (listener == null) {
            return false;
        }
        listener.onKeyEvent(event);
        return true;
    }

    private List<String> getAccessibilityServicesToBeEnabled() {
        String carSafetyAccessibilityServiceComponentName = BuiltinPackageDependency
                .getComponentName(CAR_ACCESSIBILITY_SERVICE_CLASS);
        ArrayList<String> accessibilityServicesToBeEnabled = new ArrayList<>();
        accessibilityServicesToBeEnabled.add(carSafetyAccessibilityServiceComponentName);
        if (!TextUtils.isEmpty(mRotaryServiceComponentName)) {
            accessibilityServicesToBeEnabled.add(mRotaryServiceComponentName);
        }
        return accessibilityServicesToBeEnabled;
    }

    private static List<String> createServiceListFromSettingsString(
            String accessibilityServicesString) {
        return TextUtils.isEmpty(accessibilityServicesString)
                ? new ArrayList<>()
                : Arrays.asList(accessibilityServicesString.split(
                        ENABLED_ACCESSIBILITY_SERVICES_SEPARATOR));
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dump(IndentingPrintWriter writer) {
        writer.println("*Input Service*");
        writer.println("Long-press delay: " + mLongPressDelaySupplier.getAsInt() + "ms");
        writer.println("Call button ends ongoing call: "
                + mShouldCallButtonEndOngoingCallSupplier.getAsBoolean());
        mCaptureController.dump(writer);
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dumpProto(ProtoOutputStream proto) {
        // No op
    }

    private void updateCarAccessibilityServicesSettings(@UserIdInt int userId) {
        if (UserHelperLite.isHeadlessSystemUser(userId)) {
            return;
        }
        List<String> accessibilityServicesToBeEnabled = getAccessibilityServicesToBeEnabled();
        ContentResolver contentResolverForUser = getContentResolverForUser(mContext, userId);
        List<String> alreadyEnabledServices = createServiceListFromSettingsString(
                Settings.Secure.getString(contentResolverForUser,
                        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES));

        int retry = 0;
        while (!alreadyEnabledServices.containsAll(accessibilityServicesToBeEnabled)
                && retry <= MAX_RETRIES_FOR_ENABLING_ACCESSIBILITY_SERVICES) {
            ArrayList<String> enabledServicesList = new ArrayList<>(alreadyEnabledServices);
            int numAccessibilityServicesToBeEnabled = accessibilityServicesToBeEnabled.size();
            for (int i = 0; i < numAccessibilityServicesToBeEnabled; i++) {
                String serviceToBeEnabled = accessibilityServicesToBeEnabled.get(i);
                if (!enabledServicesList.contains(serviceToBeEnabled)) {
                    enabledServicesList.add(serviceToBeEnabled);
                }
            }
            Settings.Secure.putString(contentResolverForUser,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    String.join(ENABLED_ACCESSIBILITY_SERVICES_SEPARATOR, enabledServicesList));
            // Read again to account for any race condition with other parts of the code that might
            // be enabling other accessibility services.
            alreadyEnabledServices = createServiceListFromSettingsString(
                    Settings.Secure.getString(contentResolverForUser,
                            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES));
            retry++;
        }
        if (!alreadyEnabledServices.containsAll(accessibilityServicesToBeEnabled)) {
            Slogf.e(TAG, "Failed to enable accessibility services");
        }

        Settings.Secure.putString(contentResolverForUser, Settings.Secure.ACCESSIBILITY_ENABLED,
                "1");
    }
}
