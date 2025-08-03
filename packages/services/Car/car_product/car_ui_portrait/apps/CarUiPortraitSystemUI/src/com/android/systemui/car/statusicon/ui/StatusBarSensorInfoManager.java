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

package com.android.systemui.car.statusicon.ui;

import static android.car.VehicleAreaSeat.SEAT_UNKNOWN;
import static android.car.VehiclePropertyIds.ENV_OUTSIDE_TEMPERATURE;
import static android.car.VehiclePropertyIds.HVAC_TEMPERATURE_DISPLAY_UNITS;

import android.car.Car;
import android.car.VehicleUnit;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyManager;
import android.content.res.Resources;
import android.util.Log;

import androidx.lifecycle.MutableLiveData;

import com.android.systemui.R;
import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.dagger.qualifiers.UiBackground;

import java.util.concurrent.Executor;

/**
 * This class manages the sensor info that displays on the top status bar, using sensor properties
 * from {@link CarPropertyManager}
 */
public class StatusBarSensorInfoManager {
    private static final String TAG = StatusBarSensorInfoManager.class.getSimpleName();
    private static final int[] SENSOR_PROPERTIES = {
            ENV_OUTSIDE_TEMPERATURE,
            HVAC_TEMPERATURE_DISPLAY_UNITS
    };
    private static final boolean DEBUG = false;

    private final String mTemperatureFormatCelsius;
    private final String mTemperatureFormatFahrenheit;
    private final MutableLiveData<String> mSensorStringLiveData;
    private final CarServiceProvider mCarServiceProvider;
    private Executor mExecutor;
    private CarPropertyManager mCarPropertyManager;
    private MutableLiveData<Boolean> mSensorAvailabilityData;
    private float mTemperatureValueInCelsius;
    private int mTemperatureUnit;

    private final CarPropertyManager.CarPropertyEventCallback mPropertyEventCallback =
            new CarPropertyManager.CarPropertyEventCallback() {
                @Override
                public void onChangeEvent(CarPropertyValue value) {
                    mExecutor.execute(
                            () -> handleTemperaturePropertyChange(value.getPropertyId(), value));
                }

                @Override
                public void onErrorEvent(int propId, int zone) {
                    Log.w(TAG, "Could not handle " + propId + " change event in zone " + zone);
                }
            };

    @UiBackground
    private final CarServiceProvider.CarServiceOnConnectedListener mCarServiceLifecycleListener =
            car -> {
                try {
                    mExecutor.execute(() -> {
                        mSensorAvailabilityData.postValue(true);
                        mCarPropertyManager =
                                (CarPropertyManager) car.getCarManager(Car.PROPERTY_SERVICE);
                        initializeHvacProperties();
                        registerHvacPropertyEventListeners();
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Failed to connect to Vhal", e);
                    mSensorAvailabilityData.postValue(false);
                }
            };

    StatusBarSensorInfoManager(
            Resources resources,
            CarServiceProvider carServiceProvider,
            @UiBackground Executor executor,
            MutableLiveData<String> sensorStringLiveData,
            MutableLiveData<Boolean> sensorAvailabilityData) {
        mSensorStringLiveData = sensorStringLiveData;
        mSensorAvailabilityData = sensorAvailabilityData;
        mExecutor = executor;
        mCarServiceProvider = carServiceProvider;
        mTemperatureFormatCelsius = resources.getString(
                R.string.statusbar_temperature_format_celsius);
        mTemperatureFormatFahrenheit = resources.getString(
                R.string.statusbar_temperature_format_fahrenheit);
    }

    void onAttached() {
        mCarServiceProvider.addListener(mCarServiceLifecycleListener);
    }

    void onDetached() {
        mCarServiceProvider.removeListener(mCarServiceLifecycleListener);
        unregisterHvacPropertyEventListeners();
    }

    private void initializeHvacProperties() {
        int temperatureAreaId =
                mCarPropertyManager.getAreaId(ENV_OUTSIDE_TEMPERATURE, SEAT_UNKNOWN);
        mTemperatureValueInCelsius =
                mCarPropertyManager.getFloatProperty(ENV_OUTSIDE_TEMPERATURE, temperatureAreaId);
        int unitAreaId =
                mCarPropertyManager.getAreaId(HVAC_TEMPERATURE_DISPLAY_UNITS, SEAT_UNKNOWN);
        mTemperatureUnit =
                mCarPropertyManager.getIntProperty(HVAC_TEMPERATURE_DISPLAY_UNITS, unitAreaId);
        updateHvacProperties();
    }

    private void registerHvacPropertyEventListeners() {
        for (Integer propertyId : SENSOR_PROPERTIES) {
            mCarPropertyManager.registerCallback(mPropertyEventCallback, propertyId,
                    CarPropertyManager.SENSOR_RATE_ONCHANGE);
        }
    }

    private void unregisterHvacPropertyEventListeners() {
        for (Integer propertyId : SENSOR_PROPERTIES) {
            mCarPropertyManager.unregisterCallback(mPropertyEventCallback, propertyId);
        }
    }

    private void handleTemperaturePropertyChange(int propertyId, CarPropertyValue value) {
        switch (propertyId) {
            case ENV_OUTSIDE_TEMPERATURE: {
                mTemperatureValueInCelsius = (float) value.getValue();
                break;
            }
            case HVAC_TEMPERATURE_DISPLAY_UNITS: {
                mTemperatureUnit = (Integer) value.getValue();
                break;
            }
            default: {
                logIfDebug("Unknown property" + value);
                return;
            }
        }
        updateHvacProperties();
    }

    private void updateHvacProperties() {
        boolean displayInInFahrenheit = mTemperatureUnit == VehicleUnit.FAHRENHEIT;
        float tempToDisplay = displayInInFahrenheit ? celsiusToFahrenheit(
                mTemperatureValueInCelsius)
                : mTemperatureValueInCelsius;
        mSensorStringLiveData.postValue(
                String.format(
                        displayInInFahrenheit
                                ? mTemperatureFormatFahrenheit
                                : mTemperatureFormatCelsius,
                        tempToDisplay
                )
        );

        logIfDebug("New sensor string is " + mSensorStringLiveData.getValue());
    }

    private void logIfDebug(String msg) {
        if (DEBUG) {
            Log.d(TAG, msg);
        }
    }

    private float celsiusToFahrenheit(float tempC) {
        return (tempC * 9f / 5f) + 32;
    }
}
