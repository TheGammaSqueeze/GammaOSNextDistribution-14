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

package com.android.car.settings.units;

import android.car.Car;
import android.car.CarNotConnectedException;
import android.car.VehiclePropertyIds;
import android.car.VehicleUnit;
import android.car.feature.Flags;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.property.CarPropertyManager;
import android.content.Context;
import android.util.ArraySet;

import com.android.car.settings.common.Logger;

import java.util.ArrayList;
import java.util.List;

/** Utility to read and write {@link Unit}-related properties in {@link CarPropertyManager}. */
public class CarUnitsManager {
    private static final Logger LOG = new Logger(CarUnitsManager.class);
    private static final int AREA_ID = 0;

    private Context mContext;
    private Car mCar;
    private CarPropertyManager mCarPropertyManager;
    private OnCarServiceListener mCarServiceListener;

    public CarUnitsManager(Context context) {
        mContext = context;
        mCar = Car.createCar(mContext);
        mCarPropertyManager =
                (CarPropertyManager) mCar.getCarManager(Car.PROPERTY_SERVICE);
    }

    /**
     * Registers {@link OnCarServiceListener} as a Callback for when connection to {@link Car} has
     * been established.
     */
    public void registerCarServiceListener(OnCarServiceListener listener) {
        mCarServiceListener = listener;
        mCarServiceListener.handleServiceConnected(mCarPropertyManager);
    }

    /**
     * Unregisters {@link OnCarServiceListener} as a Callback for when connection to {@link Car} has
     * been terminated.
     */
    public void unregisterCarServiceListener() {
        mCarServiceListener = null;
    }

    protected void disconnect() {
        mCar.disconnect();
        if (mCarServiceListener != null) {
            mCarServiceListener.handleServiceDisconnected();
        }
    }

    protected boolean isPropertyAvailable(int propertyId) {
        Integer intProperty = null;

        try {
            intProperty = mCarPropertyManager.getIntProperty(propertyId, AREA_ID);
        } catch (CarNotConnectedException e) {
            LOG.e("Property is unavailable because Car is not connected.");
        }

        return intProperty != null && intProperty != VehicleUnit.SHOULD_NOT_USE;
    }

    protected Unit[] getUnitsSupportedByProperty(int propertyId) {
        ArraySet<Integer> propertyIdSet = new ArraySet<Integer>();
        propertyIdSet.add(propertyId);
        List<CarPropertyConfig> configs = mCarPropertyManager.getPropertyList(propertyIdSet);
        List<Integer> availableUnitsId = new ArrayList<Integer>();
        List<Unit> units = new ArrayList<Unit>();

        if (configs == null || configs.size() < 1 || configs.get(0) == null) {
            return null;
        }

        // Checks if the property is read-write property. Checking only one area Id because _UNITS
        // properties are global properties.
        if ((Flags.areaIdConfigAccess() ? configs.get(0).getAreaIdConfig(0).getAccess()
                : configs.get(0).getAccess())
                != CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE) {
            return null;
        }

        availableUnitsId = configs.get(0).getConfigArray();

        Unit[] result = new Unit[availableUnitsId.size()];
        for (int unitId : availableUnitsId) {
            if (UnitsMap.MAP.get(unitId) != null) {
                Unit unit = UnitsMap.MAP.get(unitId);
                units.add(unit);
            }
        }
        for (int i = 0; i < result.length; i++) {
            int unitId = availableUnitsId.get(i);
            if (UnitsMap.MAP.get(unitId) != null) {
                Unit unit = UnitsMap.MAP.get(unitId);
                result[i] = unit;
            }
        }
        return result;
    }

    protected Unit getUnitUsedByProperty(int propertyId) {
        try {
            int unitId = mCarPropertyManager.getIntProperty(propertyId, AREA_ID);
            if (UnitsMap.MAP.get(unitId) != null) {
                return UnitsMap.MAP.get(unitId);
            } else {
                return null;
            }
        } catch (CarNotConnectedException e) {
            LOG.e("CarPropertyManager cannot get property because Car is not connected.");
            return null;
        }
    }

    protected void setUnitUsedByProperty(int propertyId, int unitId) {
        try {
            mCarPropertyManager.setIntProperty(propertyId, AREA_ID, unitId);
        } catch (CarNotConnectedException e) {
            LOG.e("CarPropertyManager cannot set property because Car is not connected.");
        }
    }

    /**
     * Returns a boolean that indicates whether the unit is expressed in distance per volume (true)
     * or volume per distance (false) for fuel consumption. Note that only distance over volume
     * format is supported when Mile and Gallon (both US and UK) units are used.
     */
    protected boolean isDistanceOverVolume() {
        try {
            return mCarPropertyManager.getBooleanProperty(
                    VehiclePropertyIds.FUEL_CONSUMPTION_UNITS_DISTANCE_OVER_VOLUME, AREA_ID);
        } catch (CarNotConnectedException e) {
            return true; // Defaults to True.
        }
    }

    /** Defines callbacks that listen to {@link Car} service-related events. */
    public interface OnCarServiceListener {
        /**
         * Callback to be run when {@link Car} service is connected and {@link
         * CarPropertyManager} becomes available.
         */
        void handleServiceConnected(CarPropertyManager carPropertyManager);

        /** Callback to be run when {@link Car} service is disconnected. */
        void handleServiceDisconnected();
    }
}
