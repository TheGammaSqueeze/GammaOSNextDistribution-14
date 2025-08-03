/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.car.carlauncher.calmmode;

import android.app.Application;
import android.car.Car;
import android.car.VehicleAreaSeat;
import android.car.VehiclePropertyIds;
import android.car.VehicleUnit;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyManager;
import android.icu.util.MeasureUnit;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;



public class TemperatureViewModel extends AndroidViewModel
        implements CarPropertyManager.CarPropertyEventCallback {
    private static final boolean DEBUG = Build.isDebuggable();
    private static final String TAG = TemperatureViewModel.class.getSimpleName();
    private Car mCar;
    private CarPropertyManager mPropertyManager;
    private MutableLiveData<TemperatureData> mTemperatureData = new MutableLiveData<>();

    private boolean mIsTemperatureSet;
    private float mValue;
    private MeasureUnit mUnit;
    public TemperatureViewModel(@NonNull Application application) {
        super(application);
        mCar = Car.createCar(application);
        mPropertyManager = mCar.getCarManager(CarPropertyManager.class);
        // Listen for changes
        mPropertyManager.registerCallback(this, VehiclePropertyIds.HVAC_TEMPERATURE_DISPLAY_UNITS,
                CarPropertyManager.SENSOR_RATE_ONCHANGE);
        mPropertyManager.registerCallback(this, VehiclePropertyIds.ENV_OUTSIDE_TEMPERATURE,
                CarPropertyManager.SENSOR_RATE_ONCHANGE);

    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (DEBUG) {
            Log.v(TAG, "onCleared()");
        }
        if (mCar != null && mCar.isConnected()) {
            mCar.disconnect();
            mCar = null;
        }
        if (mPropertyManager != null) {
            mPropertyManager.unregisterCallback(this,
                    VehiclePropertyIds.HVAC_TEMPERATURE_DISPLAY_UNITS);
            mPropertyManager.unregisterCallback(this,
                    VehiclePropertyIds.ENV_OUTSIDE_TEMPERATURE);
            mPropertyManager = null;
        }
    }

    /**
     * @return CarPropertyValue for VehicleAreaSeat.SEAT_UNKNOWN or null if CarPropertyManager is
     *     not initialized
     */
    @VisibleForTesting
    @Nullable
    CarPropertyValue getCarPropertyValue(int propertyId) {
        if (mPropertyManager == null) {
            return null;
        }
        return mPropertyManager.getProperty(propertyId, mPropertyManager.getAreaId(
                propertyId, VehicleAreaSeat.SEAT_UNKNOWN));
    }

    @Override
    public void onChangeEvent(CarPropertyValue carPropertyValue) {
        if (DEBUG) {
            Log.v(TAG, "onChangeEvent(carPropertyValue=" + carPropertyValue + ")");
        }
        if (carPropertyValue == null) {
            return;
        }
        if (carPropertyValue.getPropertyId() == VehiclePropertyIds.HVAC_TEMPERATURE_DISPLAY_UNITS) {
            handleUnitChange(carPropertyValue);
        } else if (carPropertyValue.getPropertyId() == VehiclePropertyIds.ENV_OUTSIDE_TEMPERATURE) {
            handleValueChange(carPropertyValue);
        }
    }

    @VisibleForTesting
    void handleUnitChange(CarPropertyValue<Integer> newVehicleUnit) {
        if (newVehicleUnit.getValue() != VehicleUnit.FAHRENHEIT
                && newVehicleUnit.getValue() != VehicleUnit.CELSIUS) {
            if (DEBUG) {
                Log.d(TAG, "handleUnitChange: Invalid temperature unit received");
            }
            return;
        }
        MeasureUnit newMeasureUnit =
                newVehicleUnit.getValue() == VehicleUnit.FAHRENHEIT
                        ? MeasureUnit.FAHRENHEIT : MeasureUnit.CELSIUS;
        if (mUnit == newMeasureUnit) {
            return;
        }
        // If temperature value has been set previously and unit has changed,
        // convert value and post update
        if (mIsTemperatureSet) {
            TemperatureData temperatureData;
            if (mUnit == MeasureUnit.FAHRENHEIT) {
                temperatureData = new TemperatureData.Builder().setValueFahrenheit(mValue).build();
                temperatureData.convertToCelsius();
            } else {
                temperatureData = new TemperatureData.Builder().setValueCelsius(mValue).build();
                temperatureData.convertToFahrenheit();
            }
            mTemperatureData.setValue(temperatureData);
        }
        mUnit = newMeasureUnit;
    }

    @VisibleForTesting
    void handleValueChange(CarPropertyValue<Float> newValue) {
        if (mIsTemperatureSet && mValue == newValue.getValue()) {
            return;
        }
        // Outside temperature is always received in celsius, assign default
        if (mUnit == null) {
            mUnit = MeasureUnit.CELSIUS;
        }
        TemperatureData temperatureData =
                new TemperatureData.Builder().setValueCelsius(newValue.getValue()).build();
        if (mUnit != MeasureUnit.CELSIUS) {
            temperatureData.convertToFahrenheit();
        }
        mTemperatureData.setValue(temperatureData);
        mValue = newValue.getValue();
        if (!mIsTemperatureSet) {
            mIsTemperatureSet = true;
        }
    }

    @Override
    public void onErrorEvent(int propertyId, int areaId) {
        if (DEBUG) {
            Log.w(TAG, "onErrorEvent(propertyId=" + propertyId + ", areaId=" + areaId);
        }
        mTemperatureData.setValue(null);
    }

    public LiveData<TemperatureData> getTemperatureData() {
        return mTemperatureData;
    }
}
