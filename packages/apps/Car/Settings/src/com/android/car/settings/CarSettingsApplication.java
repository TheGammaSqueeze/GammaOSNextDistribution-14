/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.car.settings;

import static android.car.CarOccupantZoneManager.DISPLAY_TYPE_MAIN;

import android.annotation.Nullable;
import android.app.Application;
import android.car.Car;
import android.car.Car.CarServiceLifecycleListener;
import android.car.CarOccupantZoneManager;
import android.car.CarOccupantZoneManager.OccupantZoneConfigChangeListener;
import android.car.CarOccupantZoneManager.OccupantZoneInfo;
import android.car.media.CarAudioManager;
import android.view.Display;

import androidx.annotation.GuardedBy;

/**
 * Application class for CarSettings.
 */
public class CarSettingsApplication extends Application {

    private CarOccupantZoneManager mCarOccupantZoneManager;

    private final Object mInfoLock = new Object();
    private final Object mCarAudioManagerLock = new Object();

    @GuardedBy("mInfoLock")
    private int mOccupantZoneDisplayId = Display.DEFAULT_DISPLAY;
    @GuardedBy("mInfoLock")
    private int mAudioZoneId = CarAudioManager.INVALID_AUDIO_ZONE;
    @GuardedBy("mInfoLock")
    private int mOccupantZoneType = CarOccupantZoneManager.OCCUPANT_TYPE_INVALID;
    @GuardedBy("mCarAudioManagerLock")
    private CarAudioManager mCarAudioManager = null;

    /**
     * Listener to monitor any Occupant Zone configuration change.
     */
    private final OccupantZoneConfigChangeListener mConfigChangeListener = flags -> {
        synchronized (mInfoLock) {
            updateZoneInfoLocked();
        }
    };

    /**
     * Listener to monitor the Lifecycle of car service.
     */
    private final CarServiceLifecycleListener mCarServiceLifecycleListener = (car, ready) -> {
        if (!ready) {
            mCarOccupantZoneManager = null;
            synchronized (mCarAudioManagerLock) {
                mCarAudioManager = null;
            }
            return;
        }
        mCarOccupantZoneManager = (CarOccupantZoneManager) car.getCarManager(
                Car.CAR_OCCUPANT_ZONE_SERVICE);
        if (mCarOccupantZoneManager != null) {
            mCarOccupantZoneManager.registerOccupantZoneConfigChangeListener(
                    mConfigChangeListener);
        }
        synchronized (mCarAudioManagerLock) {
            mCarAudioManager = (CarAudioManager) car.getCarManager(Car.AUDIO_SERVICE);
        }
        synchronized (mInfoLock) {
            updateZoneInfoLocked();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();

        Car.createCar(this, /* handler= */ null , Car.CAR_WAIT_TIMEOUT_WAIT_FOREVER,
                mCarServiceLifecycleListener);
    }

    /**
     * Returns zone type assigned for the current user.
     * The zone type is used to determine whether the settings preferences
     * should be available or not.
     */
    public final int getMyOccupantZoneType() {
        synchronized (mInfoLock) {
            return mOccupantZoneType;
        }
    }

    /**
     * Returns displayId assigned for the current user.
     */
    public final int getMyOccupantZoneDisplayId() {
        synchronized (mInfoLock) {
            return mOccupantZoneDisplayId;
        }
    }

    /**
     * Returns audio zone id assigned for the current user.
     */
    public final int getMyAudioZoneId() {
        synchronized (mInfoLock) {
            return mAudioZoneId;
        }
    }

    /**
     * Returns CarAudioManager instance.
     */
    @Nullable
    public final CarAudioManager getCarAudioManager() {
        synchronized (mCarAudioManagerLock) {
            return mCarAudioManager;
        }
    }

    @GuardedBy("mInfoLock")
    private void updateZoneInfoLocked() {
        if (mCarOccupantZoneManager == null) {
            return;
        }
        OccupantZoneInfo info = mCarOccupantZoneManager.getMyOccupantZone();
        if (info != null) {
            mOccupantZoneType = info.occupantType;
            mAudioZoneId = mCarOccupantZoneManager.getAudioZoneIdForOccupant(info);
            Display display = mCarOccupantZoneManager
                    .getDisplayForOccupant(info, DISPLAY_TYPE_MAIN);
            if (display != null) {
                mOccupantZoneDisplayId = display.getDisplayId();
            }
        }
    }
}
