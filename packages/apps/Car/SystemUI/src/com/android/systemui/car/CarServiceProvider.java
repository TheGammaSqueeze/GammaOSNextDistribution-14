/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.systemui.car;

import android.car.Car;
import android.content.Context;

import androidx.annotation.AnyThread;
import androidx.annotation.VisibleForTesting;

import com.android.internal.annotations.GuardedBy;
import com.android.systemui.car.dagger.CarSysUIDumpable;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

/** Provides a common connection to the car service that can be shared. */
// It needs to be @Singleton scoped because it is used by a number of components in @WMSingleton &
// @SysUISingleton scopes.
@Singleton
public class CarServiceProvider {

    private final Context mContext;
    /**
     * mListeners is guarded by itself - when requiring locks on both mListeners and mCar, always
     * obtain the car lock before the listeners.
     */
    @GuardedBy("mListeners")
    private final List<CarServiceOnConnectedListener> mListeners = new ArrayList<>();
    private final Object mCarLock = new Object();
    /**
     * mCar is guarded by mCarLock - when requiring locks on both mListeners and mCar, always
     * obtain the car lock before the listeners.
     */
    @GuardedBy("mCarLock")
    private Car mCar;

    @Inject
    public CarServiceProvider(@CarSysUIDumpable Context context) {
        mContext = context;
        mCar = Car.createCar(mContext, /* handler= */ null, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT,
                (car, ready) -> {
                    synchronized (mCarLock) {
                        synchronized (mListeners) {
                            mCar = car;
                            if (ready) {
                                for (CarServiceOnConnectedListener listener : mListeners) {
                                    listener.onConnected(mCar);
                                }
                            }
                        }
                    }
                });
    }

    @VisibleForTesting
    public CarServiceProvider(Context context, Car car) {
        mContext = context;
        mCar = car;
    }

    /**
     * Let's other components hook into the connection to the car service. If we're already
     * connected to the car service, the callback is immediately triggered.
     */
    @AnyThread
    public void addListener(CarServiceOnConnectedListener listener) {
        synchronized (mCarLock) {
            if (mCar.isConnected()) {
                listener.onConnected(mCar);
            }
        }
        synchronized (mListeners) {
            mListeners.add(listener);
        }
    }

    /**
     * Remove a car service connection listener.
     */
    @AnyThread
    public void removeListener(CarServiceOnConnectedListener listener) {
        synchronized (mListeners) {
            mListeners.remove(listener);
        }
    }

    /**
     * Listener which is triggered when Car Service is connected.
     */
    public interface CarServiceOnConnectedListener {
        /** This will be called when the car service has successfully been connected. */
        void onConnected(Car car);
    }
}
