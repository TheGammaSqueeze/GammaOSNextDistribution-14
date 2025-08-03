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

package com.android.car.systeminterface;

import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_SWITCHING;
import static android.view.Display.DEFAULT_DISPLAY;

import static com.android.car.CarServiceUtils.getContentResolverForUser;
import static com.android.car.CarServiceUtils.isEventOfType;
import static com.android.car.util.BrightnessUtils.GAMMA_SPACE_MAX;
import static com.android.car.util.BrightnessUtils.convertGammaToLinear;
import static com.android.car.util.BrightnessUtils.convertLinearToGamma;

import android.car.builtin.display.DisplayManagerHelper;
import android.car.builtin.os.UserManagerHelper;
import android.car.builtin.power.PowerManagerHelper;
import android.car.builtin.util.Slogf;
import android.car.user.CarUserManager.UserLifecycleListener;
import android.car.user.UserLifecycleEventFilter;
import android.content.Context;
import android.database.ContentObserver;
import android.hardware.display.DisplayManager;
import android.hardware.display.DisplayManager.DisplayListener;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings.SettingNotFoundException;
import android.provider.Settings.System;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.util.SparseIntArray;
import android.view.Display;

import com.android.car.CarLog;
import com.android.car.internal.util.IntArray;
import com.android.car.power.CarPowerManagementService;
import com.android.car.user.CarUserService;
import com.android.car.util.BrightnessUtils;
import com.android.internal.annotations.GuardedBy;

/**
 * Interface that abstracts display operations
 */
public interface DisplayInterface {

    /**
     * Sets the required services.
     *
     * @param carPowerManagementService {@link CarPowerManagementService} to listen to car power
     *                                  management changes
     * @param carUserService            {@link CarUserService} to listen to service life cycle
     *                                  changes
     */
    void init(CarPowerManagementService carPowerManagementService, CarUserService carUserService);

    /**
     * Sets display brightness.
     *
     * @param brightness Level from 0 to 100%
     */
    void setDisplayBrightness(int brightness);

    /**
     * Sets display brightness with the given displayId.
     *
     * @param displayId ID of a display.
     * @param brightness Level from 0 to 100.
     */
    void setDisplayBrightness(int displayId, int brightness);

    /**
     * Turns on or off display with the given displayId.
     *
     * @param displayId ID of a display.
     * @param on {@code true} to turn on, {@code false} to turn off.
     */
    void setDisplayState(int displayId, boolean on);

    /**
     * Turns on or off all displays.
     *
     * @param on {@code true} to turn on, {@code false} to turn off.
     */
    void setAllDisplayState(boolean on);

    /**
     * Starts monitoring the display state change.
     * <p> When there is a change, {@link CarPowerManagementService} is notified.
     */
    void startDisplayStateMonitoring();

    /**
     * Stops monitoring the display state change.
     */
    void stopDisplayStateMonitoring();

    /**
     * Gets the current on/off state of displays.
     *
     * @return {@code true}, if any display is turned on. Otherwise, {@code false}.
     */
    boolean isAnyDisplayEnabled();

    /**
     * Gets the current on/off state of display with the given displayId.
     *
     * @param displayId ID of a display.
     */
    boolean isDisplayEnabled(int displayId);

    /**
     * Refreshing display brightness. Used when user is switching and car turned on.
     */
    void refreshDisplayBrightness();

    /**
     * Refreshing display brightness with the given displayId.
     * Used when brightness change is observed.
     *
     * @param displayId ID of a display.
     */
    void refreshDisplayBrightness(int displayId);

    /**
     * Default implementation of display operations
     */
    class DefaultImpl implements DisplayInterface {
        private static final String TAG = DisplayInterface.class.getSimpleName();
        private static final boolean DEBUG = Slogf.isLoggable(TAG, Log.DEBUG);
        private static final int INVALID_DISPLAY_BRIGHTNESS = -1;

        private final Context mContext;
        private final DisplayManager mDisplayManager;
        private final Object mLock = new Object();
        private final int mMaximumBacklight;
        private final int mMinimumBacklight;
        private final WakeLockInterface mWakeLockInterface;
        @GuardedBy("mLock")
        private CarPowerManagementService mCarPowerManagementService;
        @GuardedBy("mLock")
        private CarUserService mCarUserService;
        @GuardedBy("mLock")
        private final SparseBooleanArray mDisplayStateSet = new SparseBooleanArray();
        @GuardedBy("mLock")
        private final SparseIntArray mDisplayBrightnessSet = new SparseIntArray();
        private final UserManager mUserManager;

        private final ContentObserver mBrightnessObserver =
                new ContentObserver(new Handler(Looper.getMainLooper())) {
                    @Override
                    public void onChange(boolean selfChange) {
                        Slogf.i(TAG, "Brightness change from Settings: selfChange=%b", selfChange);
                        refreshDisplayBrightness();
                    }
                };

        private final DisplayManager.DisplayListener mDisplayListener = new DisplayListener() {
            @Override
            public void onDisplayAdded(int displayId) {
                synchronized (mLock) {
                    mDisplayStateSet.put(displayId, isDisplayOn(displayId));
                    mDisplayBrightnessSet.put(displayId, INVALID_DISPLAY_BRIGHTNESS);
                }
            }

            @Override
            public void onDisplayRemoved(int displayId) {
                synchronized (mLock) {
                    mDisplayStateSet.delete(displayId);
                    mDisplayBrightnessSet.delete(displayId);
                }
            }

            @Override
            public void onDisplayChanged(int displayId) {
                handleDisplayChanged(displayId);
            }
        };

        DefaultImpl(Context context, WakeLockInterface wakeLockInterface) {
            mContext = context;
            mDisplayManager = context.getSystemService(DisplayManager.class);
            mMaximumBacklight = PowerManagerHelper.getMaximumScreenBrightnessSetting(context);
            mMinimumBacklight = PowerManagerHelper.getMinimumScreenBrightnessSetting(context);
            mWakeLockInterface = wakeLockInterface;
            synchronized (mLock) {
                for (Display display : mDisplayManager.getDisplays()) {
                    int displayId = display.getDisplayId();
                    mDisplayStateSet.put(displayId, isDisplayOn(displayId));
                    mDisplayBrightnessSet.put(displayId, INVALID_DISPLAY_BRIGHTNESS);
                }
            }
            mUserManager = context.getSystemService(UserManager.class);
        }

        private final UserLifecycleListener mUserLifecycleListener = event -> {
            if (!isEventOfType(TAG, event, USER_LIFECYCLE_EVENT_TYPE_SWITCHING)) {
                return;
            }
            if (DEBUG) {
                Slogf.d(TAG, "DisplayInterface.DefaultImpl.onEvent(%s)", event);
            }

            onUsersUpdate();
        };

        @Override
        public void refreshDisplayBrightness() {
            refreshDisplayBrightness(DEFAULT_DISPLAY);
        }

        @Override
        public void refreshDisplayBrightness(int displayId) {
            CarPowerManagementService carPowerManagementService = null;
            synchronized (mLock) {
                carPowerManagementService = mCarPowerManagementService;
            }
            if (carPowerManagementService == null) {
                Slogf.e(CarLog.TAG_POWER, "Could not set brightness: "
                        + "no CarPowerManagementService");
                return;
            }
            if (UserManagerHelper.isVisibleBackgroundUsersSupported(mUserManager)) {
                refreshDisplayBrightnessFromDisplay(carPowerManagementService, displayId);
            } else {
                refreshDisplayBrigtnessFromSetting(carPowerManagementService);
            }
        }

        private void refreshDisplayBrightnessFromDisplay(
                CarPowerManagementService carPowerManagementService, int displayId) {
            int linear = BrightnessUtils.brightnessFloatToInt(
                    DisplayManagerHelper.getBrightness(mContext, displayId));
            int gamma = convertLinearToGamma(linear, mMinimumBacklight, mMaximumBacklight);
            int percentBright = convertGammaToPercentBright(gamma);
            Slogf.i(TAG, "Refreshing percent brightness(from display %d) to %d", displayId,
                    percentBright);
            carPowerManagementService.sendDisplayBrightness(displayId, percentBright);
        }

        private void refreshDisplayBrigtnessFromSetting(
                CarPowerManagementService carPowerManagementService) {
            int gamma = GAMMA_SPACE_MAX;
            try {
                int linear = System.getInt(getContentResolverForUser(mContext,
                        UserHandle.CURRENT.getIdentifier()), System.SCREEN_BRIGHTNESS);
                gamma = convertLinearToGamma(linear, mMinimumBacklight, mMaximumBacklight);
            } catch (SettingNotFoundException e) {
                Slogf.e(CarLog.TAG_POWER, "Could not get SCREEN_BRIGHTNESS: ", e);
            }
            int percentBright = convertGammaToPercentBright(gamma);
            Slogf.i(TAG, "Refreshing percent brightness(from Setting) to %d", percentBright);
            carPowerManagementService.sendDisplayBrightness(percentBright);
        }

        private static int convertGammaToPercentBright(int gamma) {
            return (gamma * 100 + ((GAMMA_SPACE_MAX + 1) / 2)) / GAMMA_SPACE_MAX;
        }

        private void handleDisplayChanged(int displayId) {
            refreshDisplayBrightness(displayId);
            boolean isOn = isDisplayOn(displayId);
            CarPowerManagementService service;
            synchronized (mLock) {
                boolean state = mDisplayStateSet.get(displayId, false);
                if (state == isOn) { // same as what is set
                    return;
                }
                service = mCarPowerManagementService;
            }
            service.handleDisplayChanged(displayId, isOn);
        }

        private boolean isDisplayOn(int displayId) {
            Display disp = mDisplayManager.getDisplay(displayId);
            if (disp == null) {
                return false;
            }
            return disp.getState() == Display.STATE_ON;
        }

        @Override
        public void setDisplayBrightness(int percentBright) {
            setDisplayBrightness(DEFAULT_DISPLAY, percentBright);
        }

        @Override
        public void setDisplayBrightness(int displayId, int percentBright) {
            synchronized (mLock) {
                if (percentBright == mDisplayBrightnessSet.get(displayId)) {
                    // We have already set the value last time. Skipping
                    return;
                }
                mDisplayBrightnessSet.put(displayId, percentBright);
            }
            int gamma = (percentBright * GAMMA_SPACE_MAX + 50) / 100;
            int linear = convertGammaToLinear(gamma, mMinimumBacklight, mMaximumBacklight);
            if (UserManagerHelper.isVisibleBackgroundUsersSupported(mUserManager)) {
                DisplayManagerHelper.setBrightness(mContext, displayId,
                        BrightnessUtils.brightnessIntToFloat(linear));
            } else {
                System.putInt(
                        getContentResolverForUser(mContext, UserHandle.CURRENT.getIdentifier()),
                        System.SCREEN_BRIGHTNESS,
                        linear);
            }
        }

        @Override
        public void init(CarPowerManagementService carPowerManagementService,
                CarUserService carUserService) {
            synchronized (mLock) {
                mCarPowerManagementService = carPowerManagementService;
                mCarUserService = carUserService;
            }
        }

        @Override
        public void startDisplayStateMonitoring() {
            CarPowerManagementService carPowerManagementService;
            CarUserService carUserService;
            synchronized (mLock) {
                carPowerManagementService = mCarPowerManagementService;
                carUserService = mCarUserService;
            }
            UserLifecycleEventFilter userSwitchingEventFilter =
                    new UserLifecycleEventFilter.Builder()
                            .addEventType(USER_LIFECYCLE_EVENT_TYPE_SWITCHING).build();
            carUserService.addUserLifecycleListener(userSwitchingEventFilter,
                    mUserLifecycleListener);
            if (UserManagerHelper.isVisibleBackgroundUsersSupported(mUserManager)) {
                DisplayManagerHelper.registerDisplayListener(mContext, mDisplayListener,
                        carPowerManagementService.getHandler(),
                        DisplayManagerHelper.EVENT_FLAG_DISPLAY_ADDED
                                | DisplayManagerHelper.EVENT_FLAG_DISPLAY_REMOVED
                                | DisplayManagerHelper.EVENT_FLAG_DISPLAY_CHANGED
                                | DisplayManagerHelper.EVENT_FLAG_DISPLAY_BRIGHTNESS);
            } else {
                getContentResolverForUser(mContext, UserHandle.ALL.getIdentifier())
                        .registerContentObserver(System.getUriFor(System.SCREEN_BRIGHTNESS),
                                false,
                                mBrightnessObserver);
            }

            for (Display display : mDisplayManager.getDisplays()) {
                int displayId = display.getDisplayId();
                refreshDisplayBrightness(displayId);
            }
        }

        @Override
        public void stopDisplayStateMonitoring() {
            CarUserService carUserService;
            synchronized (mLock) {
                carUserService = mCarUserService;
            }
            carUserService.removeUserLifecycleListener(mUserLifecycleListener);
            if (UserManagerHelper.isVisibleBackgroundUsersSupported(mUserManager)) {
                mDisplayManager.unregisterDisplayListener(mDisplayListener);
            } else {
                getContentResolverForUser(mContext, UserHandle.ALL.getIdentifier())
                        .unregisterContentObserver(mBrightnessObserver);
            }
        }

        @Override
        public void setDisplayState(int displayId, boolean on) {
            CarPowerManagementService carPowerManagementService;
            synchronized (mLock) {
                carPowerManagementService = mCarPowerManagementService;
                if (on && carPowerManagementService != null
                        && !carPowerManagementService.canTurnOnDisplay(displayId)) {
                    Slogf.i(CarLog.TAG_POWER, "ignore turning on display %d because "
                            + "CarPowerManagementService doesn't support it", displayId);
                    return;
                }
                mDisplayStateSet.put(displayId, on);
            }
            if (on) {
                mWakeLockInterface.switchToFullWakeLock(displayId);
                Slogf.i(CarLog.TAG_POWER, "on display %d", displayId);
            } else {
                mWakeLockInterface.switchToPartialWakeLock(displayId);
                Slogf.i(CarLog.TAG_POWER, "off display %d", displayId);
                PowerManagerHelper.goToSleep(mContext, displayId, SystemClock.uptimeMillis());
            }
            if (carPowerManagementService != null) {
                carPowerManagementService.handleDisplayChanged(displayId, on);
            }
        }

        @Override
        public void setAllDisplayState(boolean on) {
            IntArray displayIds = new IntArray();
            synchronized (mLock) {
                for (int i = 0; i < mDisplayStateSet.size(); i++) {
                    displayIds.add(mDisplayStateSet.keyAt(i));
                }
            }
            // setDisplayState has a binder call to system_server. Should not wrap setDisplayState
            // with a lock.
            for (int i = 0; i < displayIds.size(); i++) {
                setDisplayState(displayIds.get(i), on);
            }
        }

        @Override
        public boolean isAnyDisplayEnabled() {
            synchronized (mLock) {
                for (int i = 0; i < mDisplayStateSet.size(); i++) {
                    if (isDisplayEnabled(mDisplayStateSet.keyAt(i))) {
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        public boolean isDisplayEnabled(int displayId) {
            return isDisplayOn(displayId);
        }

        private void onUsersUpdate() {
            synchronized (mLock) {
                if (mCarPowerManagementService == null) {
                    // CarPowerManagementService is not connected yet
                    return;
                }
                // We need to reset last value
                mDisplayBrightnessSet.put(DEFAULT_DISPLAY, INVALID_DISPLAY_BRIGHTNESS);
            }
            refreshDisplayBrightness();
        }
    }
}
