/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.car.settings.common;

import android.car.Car;
import android.car.hardware.power.CarPowerManager;
import android.car.hardware.power.CarPowerPolicy;
import android.car.hardware.power.CarPowerPolicyFilter;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.Executor;

/**
 * Listens to power policy change and executes the registered handler.
 *
 * <p>{@link android.car.Car} instance and {@link android.car.hardware.power.CarPowerManager}
 * instances are managed internally, so the callers doesn't have to care about creating/destroying
 * them.
 */
public final class PowerPolicyListener {

    private static final Logger LOG = new Logger(PowerPolicyListener.class);

    private final Executor mExecutor;
    private final PolicyChangeHandler mPolicyChangeHandler;
    private final CarPowerManager.CarPowerPolicyListener mPolicyListener =
            new CarPowerManager.CarPowerPolicyListener() {
        @Override
        public void onPolicyChanged(@NonNull CarPowerPolicy policy) {
            mPolicyChangeHandler.handlePolicyChange(policy.isComponentEnabled(mComponent));
        }
    };
    @Nullable private Car mCar;
    @Nullable private CarPowerManager mCarPowerManager;
    private int mComponent;

    /**
     * Handlers which is called when the given component's power state is changed by power policy.
     */
    public interface PolicyChangeHandler {
        /** Called when a power policy changes around the given component */
        void handlePolicyChange(boolean isOn);
    }

    public PowerPolicyListener(Context context, int component, PolicyChangeHandler handler) {
        mComponent = component;
        mPolicyChangeHandler = handler;
        mExecutor = context.getMainExecutor();
        Car.createCar(context, null, Car.CAR_WAIT_TIMEOUT_WAIT_FOREVER,
                (car, ready) -> {
                    if (ready) {
                        LOG.d("Connected to the Car Service");
                        mCar = car;
                        CarPowerPolicyFilter filter = new CarPowerPolicyFilter.Builder()
                                .setComponents(component).build();
                        mCarPowerManager = (CarPowerManager) mCar.getCarManager(Car.POWER_SERVICE);
                        if (mCarPowerManager != null) {
                            mCarPowerManager.addPowerPolicyListener(mExecutor, filter,
                                    mPolicyListener);
                        }
                    } else {
                        LOG.d("Disconnected from the Car Service");
                        mCar = null;
                        mCarPowerManager = null;
                    }
                });
    }

    /**
     * Unregisters a power policy listener from {@link android.car.hardware.power.CarPowerManager}
     * and disconnects from {@link android.car.Car}.
     */
    public void release() {
        if (mCarPowerManager != null) {
            mCarPowerManager.removePowerPolicyListener(mPolicyListener);
        }
        try {
            if (mCar != null) {
                mCar.disconnect();
            }
        } catch (IllegalStateException e) {
            // Do nothing.
            LOG.w("release(): cannot disconnect from Car");
        }
    }

    /**
     * Executes the registered handler with the current power policy.
     */
    public void handleCurrentPolicy() {
        if (mCarPowerManager == null) {
            LOG.w("CarPowerManager is not available");
            return;
        }
        CarPowerPolicy policy = mCarPowerManager.getCurrentPowerPolicy();
        if (policy != null) {
            mPolicyChangeHandler.handlePolicyChange(policy.isComponentEnabled(mComponent));
        }
    }

    /**
     * Gets the registered {@link PolicyChangeHandler}.
     */
    public PolicyChangeHandler getPolicyChangeHandler() {
        return mPolicyChangeHandler;
    }
}
