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

package com.android.systemui.car.users;

import static android.car.CarOccupantZoneManager.DISPLAY_TYPE_MAIN;
import static android.hardware.display.DisplayManager.EVENT_FLAG_DISPLAY_BRIGHTNESS;

import static com.android.systemui.car.users.CarSystemUIUserUtil.isCurrentSystemUIDisplay;
import static com.android.systemui.car.users.CarSystemUIUserUtil.isMUMDSystemUI;

import android.car.Car;
import android.car.CarOccupantZoneManager;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.view.Display;

import androidx.annotation.GuardedBy;
import androidx.annotation.WorkerThread;

import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.settings.DisplayTracker;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.util.Assert;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Custom {@link DisplayTracker} for CarSystemUI. This class utilizes the
 * {@link CarOccupantZoneManager} to provide the relevant displays and callbacks for a particular
 * SystemUI instance running for a particular user.
 */
public class CarDisplayTrackerImpl implements DisplayTracker {
    private final Context mContext;
    private final DisplayManager mDisplayManager;
    private final UserTracker mUserTracker;
    private final Handler mHandler;
    private CarOccupantZoneManager mCarOccupantZoneManager;
    private CarOccupantZoneManager.OccupantZoneInfo mOccupantZone;
    @GuardedBy("mDisplayCallbacks")
    private final List<DisplayTrackerCallbackData> mDisplayCallbacks = new ArrayList<>();
    @GuardedBy("mBrightnessCallbacks")
    private final List<DisplayTrackerCallbackData> mBrightnessCallbacks = new ArrayList<>();

    private final CarOccupantZoneManager.OccupantZoneConfigChangeListener mConfigChangeListener =
            new CarOccupantZoneManager.OccupantZoneConfigChangeListener() {
                @Override
                public void onOccupantZoneConfigChanged(int changeFlags) {
                    mOccupantZone = mCarOccupantZoneManager.getOccupantZoneForUser(
                            mUserTracker.getUserHandle());
                }
            };

    private final DisplayManager.DisplayListener mDisplayListener =
            new DisplayManager.DisplayListener() {
                @Override
                public void onDisplayAdded(int displayId) {
                    List<DisplayTrackerCallbackData> callbacks;
                    synchronized (mDisplayCallbacks) {
                        callbacks = List.copyOf(mDisplayCallbacks);
                    }
                    CarDisplayTrackerImpl.this.onDisplayAdded(displayId, callbacks);
                }

                @Override
                public void onDisplayRemoved(int displayId) {
                    List<DisplayTrackerCallbackData> callbacks;
                    synchronized (mDisplayCallbacks) {
                        callbacks = List.copyOf(mDisplayCallbacks);
                    }
                    CarDisplayTrackerImpl.this.onDisplayRemoved(displayId, callbacks);
                }

                @Override
                public void onDisplayChanged(int displayId) {
                    List<DisplayTrackerCallbackData> callbacks;
                    synchronized (mDisplayCallbacks) {
                        callbacks = List.copyOf(mDisplayCallbacks);
                    }
                    CarDisplayTrackerImpl.this.onDisplayChanged(displayId, callbacks);
                }
            };

    private final DisplayManager.DisplayListener mBrightnessChangedListener =
            new DisplayManager.DisplayListener() {
                @Override
                public void onDisplayAdded(int displayId) {
                }

                @Override
                public void onDisplayRemoved(int displayId) {
                }

                @Override
                public void onDisplayChanged(int displayId) {
                    List<DisplayTrackerCallbackData> callbacks;
                    synchronized (mBrightnessCallbacks) {
                        callbacks = List.copyOf(mBrightnessCallbacks);
                    }
                    CarDisplayTrackerImpl.this.onDisplayChanged(displayId, callbacks);
                }
            };

    public CarDisplayTrackerImpl(Context context, UserTracker userTracker,
            CarServiceProvider carServiceProvider, Handler backgroundHandler) {
        mContext = context;
        mDisplayManager = mContext.getSystemService(DisplayManager.class);
        mUserTracker = userTracker;
        mHandler = backgroundHandler;
        carServiceProvider.addListener(mCarServiceOnConnectedListener);
    }

    @Override
    public int getDefaultDisplayId() {
        if (!isMUMDSystemUI()) {
            return Display.DEFAULT_DISPLAY;
        }
        if (mOccupantZone != null) {
            Display display = mCarOccupantZoneManager.getDisplayForOccupant(mOccupantZone,
                    DISPLAY_TYPE_MAIN);
            if (display != null) {
                return display.getDisplayId();
            }
        }
        return mContext.getDisplayId();
    }

    @Override
    public Display[] getAllDisplays() {
        if (!isMUMDSystemUI()) {
            return mDisplayManager.getDisplays();
        }
        if (mOccupantZone != null) {
            return mCarOccupantZoneManager.getAllDisplaysForOccupant(mOccupantZone)
                    .toArray(Display[]::new);
        }
        return new Display[]{mDisplayManager.getDisplay(mContext.getDisplayId())};
    }

    @Override
    public Display getDisplay(int displayId) {
        return mDisplayManager.getDisplay(displayId);
    }

    @Override
    public void addDisplayChangeCallback(Callback callback, Executor executor) {
        synchronized (mDisplayCallbacks) {
            if (mDisplayCallbacks.isEmpty()) {
                mDisplayManager.registerDisplayListener(mDisplayListener, mHandler);
            }
            mDisplayCallbacks.add(new DisplayTrackerCallbackData(callback, executor));
        }
    }

    @Override
    public void addBrightnessChangeCallback(Callback callback, Executor executor) {
        synchronized (mBrightnessCallbacks) {
            if (mBrightnessCallbacks.isEmpty()) {
                mDisplayManager.registerDisplayListener(mBrightnessChangedListener, mHandler,
                        EVENT_FLAG_DISPLAY_BRIGHTNESS);
            }
            mBrightnessCallbacks.add(new DisplayTrackerCallbackData(callback, executor));
        }
    }

    @Override
    public void removeCallback(Callback callback) {
        synchronized (mDisplayCallbacks) {
            boolean changed = mDisplayCallbacks.removeIf(it -> it.sameOrEmpty(callback));
            if (changed && mDisplayCallbacks.isEmpty()) {
                mDisplayManager.unregisterDisplayListener(mDisplayListener);
            }
        }

        synchronized (mBrightnessCallbacks) {
            boolean changed = mBrightnessCallbacks.removeIf(it -> it.sameOrEmpty(callback));
            if (changed && mBrightnessCallbacks.isEmpty()) {
                mDisplayManager.unregisterDisplayListener(mBrightnessChangedListener);
            }
        }
    }

    @WorkerThread
    private void onDisplayAdded(int displayId, List<DisplayTrackerCallbackData> callbacks) {
        Assert.isNotMainThread();
        if (!shouldExecuteDisplayCallback(displayId)) {
            return;
        }

        callbacks.forEach(it -> {
            DisplayTracker.Callback callback = it.mCallback.get();
            if (callback != null) {
                it.mExecutor.execute(() -> callback.onDisplayAdded(displayId));
            }
        });
    }

    @WorkerThread
    private void onDisplayRemoved(int displayId, List<DisplayTrackerCallbackData> callbacks) {
        Assert.isNotMainThread();
        if (!shouldExecuteDisplayCallback(displayId)) {
            return;
        }

        callbacks.forEach(it -> {
            DisplayTracker.Callback callback = it.mCallback.get();
            if (callback != null) {
                it.mExecutor.execute(() -> callback.onDisplayRemoved(displayId));
            }
        });
    }

    @WorkerThread
    private void onDisplayChanged(int displayId, List<DisplayTrackerCallbackData> callbacks) {
        Assert.isNotMainThread();
        if (!shouldExecuteDisplayCallback(displayId)) {
            return;
        }

        callbacks.forEach(it -> {
            DisplayTracker.Callback callback = it.mCallback.get();
            if (callback != null) {
                it.mExecutor.execute(() -> callback.onDisplayChanged(displayId));
            }
        });
    }

    private boolean shouldExecuteDisplayCallback(int displayId) {
        if (!isMUMDSystemUI()) {
            return true;
        }
        return mOccupantZone != null && isCurrentSystemUIDisplay(mCarOccupantZoneManager,
                mOccupantZone, displayId);
    }

    private final CarServiceProvider.CarServiceOnConnectedListener mCarServiceOnConnectedListener =
            new CarServiceProvider.CarServiceOnConnectedListener() {
                @Override
                public void onConnected(Car car) {
                    mCarOccupantZoneManager =
                            (CarOccupantZoneManager) car.getCarManager(
                                    Car.CAR_OCCUPANT_ZONE_SERVICE);
                    if (mCarOccupantZoneManager != null) {
                        mOccupantZone = mCarOccupantZoneManager.getOccupantZoneForUser(
                                mUserTracker.getUserHandle());
                        mCarOccupantZoneManager.registerOccupantZoneConfigChangeListener(
                                mConfigChangeListener);
                    }
                }
            };

    private static class DisplayTrackerCallbackData {
        final WeakReference<Callback> mCallback;
        final Executor mExecutor;

        DisplayTrackerCallbackData(Callback callback, Executor executor) {
            mCallback = new WeakReference<>(callback);
            mExecutor = executor;
        }

        boolean sameOrEmpty(DisplayTracker.Callback other) {
            DisplayTracker.Callback callback = mCallback.get();
            if (callback == null) {
                return true;
            }
            return callback.equals(other);
        }
    }
}
