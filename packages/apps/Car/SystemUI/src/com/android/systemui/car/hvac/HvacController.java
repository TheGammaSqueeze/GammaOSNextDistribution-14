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

package com.android.systemui.car.hvac;

import static android.car.VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL;
import static android.car.VehicleAreaType.VEHICLE_AREA_TYPE_SEAT;
import static android.car.VehiclePropertyIds.HVAC_ACTUAL_FAN_SPEED_RPM;
import static android.car.VehiclePropertyIds.HVAC_AC_ON;
import static android.car.VehiclePropertyIds.HVAC_AUTO_ON;
import static android.car.VehiclePropertyIds.HVAC_AUTO_RECIRC_ON;
import static android.car.VehiclePropertyIds.HVAC_DEFROSTER;
import static android.car.VehiclePropertyIds.HVAC_DUAL_ON;
import static android.car.VehiclePropertyIds.HVAC_ELECTRIC_DEFROSTER_ON;
import static android.car.VehiclePropertyIds.HVAC_FAN_DIRECTION;
import static android.car.VehiclePropertyIds.HVAC_FAN_DIRECTION_AVAILABLE;
import static android.car.VehiclePropertyIds.HVAC_FAN_SPEED;
import static android.car.VehiclePropertyIds.HVAC_MAX_AC_ON;
import static android.car.VehiclePropertyIds.HVAC_MAX_DEFROST_ON;
import static android.car.VehiclePropertyIds.HVAC_POWER_ON;
import static android.car.VehiclePropertyIds.HVAC_RECIRC_ON;
import static android.car.VehiclePropertyIds.HVAC_SEAT_TEMPERATURE;
import static android.car.VehiclePropertyIds.HVAC_SEAT_VENTILATION;
import static android.car.VehiclePropertyIds.HVAC_SIDE_MIRROR_HEAT;
import static android.car.VehiclePropertyIds.HVAC_STEERING_WHEEL_HEAT;
import static android.car.VehiclePropertyIds.HVAC_TEMPERATURE_CURRENT;
import static android.car.VehiclePropertyIds.HVAC_TEMPERATURE_DISPLAY_UNITS;
import static android.car.VehiclePropertyIds.HVAC_TEMPERATURE_SET;

import android.annotation.IntDef;
import android.annotation.Nullable;
import android.car.Car;
import android.car.VehiclePropertyIds;
import android.car.VehicleUnit;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyManager;
import android.content.res.Resources;
import android.os.Build;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.GuardedBy;
import androidx.annotation.VisibleForTesting;

import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.dagger.qualifiers.UiBackground;
import com.android.systemui.statusbar.policy.ConfigurationController;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import javax.inject.Inject;

/**
 * A controller that connects to {@link CarPropertyManager} to subscribe to HVAC property change
 * events and propagate them to subscribing {@link HvacView}s by property ID and area ID.
 *
 * Grants {@link HvacView}s access to {@link HvacPropertySetter} with API's to write new values
 * for HVAC properties.
 */
public class HvacController implements HvacPropertySetter,
        ConfigurationController.ConfigurationListener {
    private static final String TAG = HvacController.class.getSimpleName();
    private static final boolean DEBUG = Build.IS_ENG || Build.IS_USERDEBUG;
    private static final int[] HVAC_PROPERTIES =
            {HVAC_FAN_SPEED, HVAC_FAN_DIRECTION, HVAC_TEMPERATURE_CURRENT, HVAC_TEMPERATURE_SET,
                    HVAC_DEFROSTER, HVAC_AC_ON, HVAC_MAX_AC_ON, HVAC_MAX_DEFROST_ON, HVAC_RECIRC_ON,
                    HVAC_DUAL_ON, HVAC_AUTO_ON, HVAC_SEAT_TEMPERATURE, HVAC_SIDE_MIRROR_HEAT,
                    HVAC_STEERING_WHEEL_HEAT, HVAC_TEMPERATURE_DISPLAY_UNITS,
                    HVAC_ACTUAL_FAN_SPEED_RPM, HVAC_POWER_ON, HVAC_FAN_DIRECTION_AVAILABLE,
                    HVAC_AUTO_RECIRC_ON, HVAC_SEAT_VENTILATION, HVAC_ELECTRIC_DEFROSTER_ON};
    private static final int[] HVAC_PROPERTIES_TO_GET_ON_INIT =
            {HVAC_POWER_ON, HVAC_AUTO_ON, HVAC_FAN_DIRECTION_AVAILABLE};
    private static final int GLOBAL_AREA_ID = 0;

    @IntDef(value = {HVAC_FAN_SPEED, HVAC_FAN_DIRECTION, HVAC_TEMPERATURE_CURRENT,
            HVAC_TEMPERATURE_SET, HVAC_DEFROSTER, HVAC_AC_ON, HVAC_MAX_AC_ON, HVAC_MAX_DEFROST_ON,
            HVAC_RECIRC_ON, HVAC_DUAL_ON, HVAC_AUTO_ON, HVAC_SEAT_TEMPERATURE,
            HVAC_SIDE_MIRROR_HEAT, HVAC_STEERING_WHEEL_HEAT, HVAC_TEMPERATURE_DISPLAY_UNITS,
            HVAC_ACTUAL_FAN_SPEED_RPM, HVAC_POWER_ON, HVAC_FAN_DIRECTION_AVAILABLE,
            HVAC_AUTO_RECIRC_ON, HVAC_SEAT_VENTILATION, HVAC_ELECTRIC_DEFROSTER_ON})
    @Target({ElementType.TYPE_PARAMETER, ElementType.TYPE_USE})
    public @interface HvacProperty {
    }

    @Target({ElementType.TYPE_PARAMETER, ElementType.TYPE_USE})
    public @interface AreaId {
    }

    private Executor mExecutor;
    private CarPropertyManager mCarPropertyManager;
    private boolean mIsConnectedToCar;
    private List<Integer> mHvacPowerDependentProperties;

    private final Object mLock = new Object();
    @GuardedBy("mLock")
    private final SparseBooleanArray mAreaIdToIsHvacPowerOn = new SparseBooleanArray();

    /**
     * Contains views to init until car service is connected.
     * This must be accessed via {@link #mExecutor} to ensure thread safety.
     */
    private final ArrayList<View> mViewsToInit = new ArrayList<>();
    private final Map<@HvacProperty Integer, Map<@AreaId Integer, List<HvacView>>>
            mHvacPropertyViewMap = new HashMap<>();

    private final CarPropertyManager.CarPropertyEventCallback mPropertyEventCallback =
            new CarPropertyManager.CarPropertyEventCallback() {
                @Override
                public void onChangeEvent(CarPropertyValue value) {
                    mExecutor.execute(() -> {
                        handleHvacPropertyChange(value.getPropertyId(), value);
                    });
                }

                @Override
                public void onErrorEvent(int propId, int zone) {
                    Log.w(TAG, "Could not handle " + propId + " change event in zone " + zone);
                }
            };

    @UiBackground
    @VisibleForTesting
    final CarServiceProvider.CarServiceOnConnectedListener mCarServiceLifecycleListener =
            car -> {
                try {
                    mExecutor.execute(() -> {
                        mIsConnectedToCar = true;
                        mCarPropertyManager =
                                (CarPropertyManager) car.getCarManager(Car.PROPERTY_SERVICE);
                        CarPropertyConfig hvacPowerOnConfig =
                                mCarPropertyManager.getCarPropertyConfig(HVAC_POWER_ON);
                        mHvacPowerDependentProperties = hvacPowerOnConfig != null
                                ? hvacPowerOnConfig.getConfigArray() : new ArrayList<>();
                        registerHvacPropertyEventListeners();
                        mViewsToInit.forEach(this::registerHvacViews);
                        mViewsToInit.clear();
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Failed to connect to HVAC", e);
                    mIsConnectedToCar = false;
                }
            };

    @Inject
    public HvacController(CarServiceProvider carServiceProvider,
            @UiBackground Executor executor,
            @Main Resources resources,
            ConfigurationController configurationController) {
        mExecutor = executor;
        if (!mIsConnectedToCar) {
            carServiceProvider.addListener(mCarServiceLifecycleListener);
        }
        configurationController.addCallback(this);
    }

    private int[] getSupportedAreaIds(int propertyId) {
        CarPropertyConfig config = mCarPropertyManager.getCarPropertyConfig(propertyId);
        if (config == null) {
            // This property isn't supported/exposed by the CarPropertyManager. So an empty array is
            // returned here to signify that no areaIds with this propertyId are going to be
            // registered or updated.
            return new int[] {};
        }
        return config.getAreaIds();
    }

    private ArrayList<Integer> getAreaIdsFromTargetAreaId(int propertyId, int targetAreaId) {
        ArrayList<Integer> areaIdsFromTargetAreaId = new ArrayList<Integer>();
        int[] supportedAreaIds = getSupportedAreaIds(propertyId);

        for (int supportedAreaId : supportedAreaIds) {
            if (targetAreaId == GLOBAL_AREA_ID || (targetAreaId & supportedAreaId) != 0) {
                areaIdsFromTargetAreaId.add(supportedAreaId);
            }
        }

        return areaIdsFromTargetAreaId;
    }

    @Override
    public void setHvacProperty(@HvacProperty Integer propertyId, int targetAreaId,
            int val) {
        mExecutor.execute(() -> {
            if (isHvacPowerDependentPropAndNotAvailable(propertyId.intValue(), targetAreaId)) {
                Log.w(TAG, "setHvacProperty - HVAC_POWER_ON is false so skipping setting HVAC"
                        + " propertyId: " + VehiclePropertyIds.toString(propertyId) + ", areaId: "
                        + Integer.toHexString(targetAreaId) + ", val: " + val);
                return;
            }
            try {
                ArrayList<Integer> supportedAreaIds = getAreaIdsFromTargetAreaId(
                        propertyId.intValue(), targetAreaId);
                for (int areaId : supportedAreaIds) {
                    mCarPropertyManager.setIntProperty(propertyId, areaId, val);
                }
            } catch (RuntimeException e) {
                Log.w(TAG, "setHvacProperty - Error while setting HVAC propertyId: "
                        + VehiclePropertyIds.toString(propertyId) + ", areaId: "
                        + Integer.toHexString(targetAreaId) + ", val: " + val, e);
            }
        });
    }

    @Override
    public void setHvacProperty(@HvacProperty Integer propertyId, int targetAreaId,
            float val) {
        mExecutor.execute(() -> {
            if (isHvacPowerDependentPropAndNotAvailable(propertyId.intValue(), targetAreaId)) {
                Log.w(TAG, "setHvacProperty - HVAC_POWER_ON is false so skipping setting HVAC"
                        + " propertyId: " + VehiclePropertyIds.toString(propertyId) + ", areaId: "
                        + Integer.toHexString(targetAreaId) + ", val: " + val);
                return;
            }
            try {
                ArrayList<Integer> supportedAreaIds = getAreaIdsFromTargetAreaId(
                        propertyId.intValue(), targetAreaId);
                for (int areaId : supportedAreaIds) {
                    mCarPropertyManager.setFloatProperty(propertyId, areaId, val);
                }
            } catch (RuntimeException e) {
                Log.w(TAG, "setHvacProperty - Error while setting HVAC propertyId: "
                        + VehiclePropertyIds.toString(propertyId) + ", areaId: "
                        + Integer.toHexString(targetAreaId) + ", val: " + val, e);
            }
        });
    }

    @Override
    public void setHvacProperty(@HvacProperty Integer propertyId, int targetAreaId,
            boolean val) {
        mExecutor.execute(() -> {
            if (isHvacPowerDependentPropAndNotAvailable(propertyId.intValue(), targetAreaId)) {
                Log.w(TAG, "setHvacProperty - HVAC_POWER_ON is false so skipping setting HVAC"
                        + " propertyId: " + VehiclePropertyIds.toString(propertyId) + ", areaId: "
                        + Integer.toHexString(targetAreaId) + ", val: " + val);
                return;
            }
            try {
                ArrayList<Integer> supportedAreaIds = getAreaIdsFromTargetAreaId(
                        propertyId.intValue(), targetAreaId);
                for (int areaId : supportedAreaIds) {
                    mCarPropertyManager.setBooleanProperty(propertyId, areaId, val);
                }
            } catch (RuntimeException e) {
                Log.w(TAG, "setHvacProperty - Error while setting HVAC propertyId: "
                        + VehiclePropertyIds.toString(propertyId) + ", areaId: "
                        + Integer.toHexString(targetAreaId) + ", val: " + val, e);
            }
        });
    }

    /**
     * Registers all {@link HvacView}s in the {@code rootView} and its descendents.
     */
    @UiBackground
    public void registerHvacViews(View rootView) {
        if (!mIsConnectedToCar) {
            mExecutor.execute(() -> mViewsToInit.add(rootView));
            return;
        }

        if (rootView instanceof HvacView) {
            try {
                HvacView hvacView = (HvacView) rootView;
                @HvacProperty Integer propId = hvacView.getHvacPropertyToView();
                @AreaId Integer targetAreaId = hvacView.getAreaId();

                CarPropertyConfig carPropertyConfig =
                        mCarPropertyManager.getCarPropertyConfig(propId);
                if (carPropertyConfig == null) {
                    throw new IllegalArgumentException(
                            "Cannot register hvac view for property: "
                            + VehiclePropertyIds.toString(propId)
                            + " because property is not implemented.");
                }

                hvacView.setHvacPropertySetter(this);
                hvacView.setConfigInfo(carPropertyConfig);
                hvacView.setDisableViewIfPowerOff(mHvacPowerDependentProperties.contains(propId));

                ArrayList<Integer> supportedAreaIds = getAreaIdsFromTargetAreaId(propId.intValue(),
                        targetAreaId.intValue());
                for (Integer areaId : supportedAreaIds) {
                    addHvacViewToMap(propId.intValue(), areaId.intValue(), hvacView);
                }

                if (mCarPropertyManager != null) {
                    CarPropertyValue<Integer> hvacTemperatureDisplayUnitsValue =
                            (CarPropertyValue<Integer>) getPropertyValueOrNull(
                                    HVAC_TEMPERATURE_DISPLAY_UNITS, GLOBAL_AREA_ID);
                    for (Integer areaId : supportedAreaIds) {
                        CarPropertyValue initValueOrNull = getPropertyValueOrNull(propId, areaId);

                        // Initialize the view with the initial value.
                        if (initValueOrNull != null) {
                            hvacView.onPropertyChanged(initValueOrNull);
                        }
                        if (hvacTemperatureDisplayUnitsValue != null) {
                            boolean usesFahrenheit = hvacTemperatureDisplayUnitsValue.getValue()
                                    == VehicleUnit.FAHRENHEIT;
                            hvacView.onHvacTemperatureUnitChanged(usesFahrenheit);
                        }

                        if (carPropertyConfig.getAreaType() != VEHICLE_AREA_TYPE_SEAT) {
                            continue;
                        }

                        for (int propToGetOnInitId : HVAC_PROPERTIES_TO_GET_ON_INIT) {
                            int[] propToGetOnInitSupportedAreaIds = getSupportedAreaIds(
                                    propToGetOnInitId);

                            int areaIdToFind = areaId.intValue();

                            for (int supportedAreaId : propToGetOnInitSupportedAreaIds) {
                                if ((supportedAreaId & areaIdToFind) == areaIdToFind) {
                                    CarPropertyValue propToGetOnInitValueOrNull =
                                            getPropertyValueOrNull(propToGetOnInitId,
                                                    supportedAreaId);
                                    if (propToGetOnInitValueOrNull != null) {
                                        hvacView.onPropertyChanged(propToGetOnInitValueOrNull);
                                    }
                                    break;
                                }
                            }
                        }
                    }
                }
            } catch (IllegalArgumentException ex) {
                Log.e(TAG, "Can't register HVAC view", ex);
            }
        }

        if (rootView instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) rootView;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                registerHvacViews(viewGroup.getChildAt(i));
            }
        }
    }

    /**
     * Unregisters all {@link HvacView}s in the {@code rootView} and its descendents.
     */
    public void unregisterViews(View rootView) {
        if (rootView instanceof HvacView) {
            HvacView hvacView = (HvacView) rootView;
            @HvacProperty Integer propId = hvacView.getHvacPropertyToView();
            @AreaId Integer targetAreaId = hvacView.getAreaId();

            ArrayList<Integer> supportedAreaIds = getAreaIdsFromTargetAreaId(propId.intValue(),
                    targetAreaId.intValue());
            for (Integer areaId : supportedAreaIds) {
                removeHvacViewFromMap(propId.intValue(), areaId.intValue(), hvacView);
            }
        }

        if (rootView instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) rootView;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                unregisterViews(viewGroup.getChildAt(i));
            }
        }
    }

    @VisibleForTesting
    void handleHvacPropertyChange(@HvacProperty int propertyId, CarPropertyValue value) {
        if (DEBUG) {
            Log.d(TAG, "handleHvacPropertyChange - propertyId: "
                    + VehiclePropertyIds.toString(propertyId) + " value: " + value);
        }
        if (value.getPropertyId() == HVAC_POWER_ON) {
            handleHvacPowerOn(value);
        }
        if (value.getPropertyId() == HVAC_TEMPERATURE_DISPLAY_UNITS) {
            mHvacPropertyViewMap.forEach((propId, areaIds) -> {
                areaIds.forEach((areaId, views) -> {
                    views.forEach(v -> v.onHvacTemperatureUnitChanged(
                            (Integer) value.getValue() == VehicleUnit.FAHRENHEIT));
                });
            });
            return;
        }

        int valueAreaType = mCarPropertyManager.getCarPropertyConfig(value.getPropertyId())
                .getAreaType();
        if (valueAreaType == VEHICLE_AREA_TYPE_GLOBAL) {
            mHvacPropertyViewMap.forEach((propId, areaIds) -> {
                areaIds.forEach((areaId, views) -> {
                    views.forEach(v -> v.onPropertyChanged(value));
                });
            });
        } else {
            mHvacPropertyViewMap.forEach((propId, areaIds) -> {
                if (valueAreaType
                        == mCarPropertyManager.getCarPropertyConfig(propId).getAreaType()) {
                    areaIds.forEach((areaId, views) -> {
                        if ((value.getAreaId() & areaId) == areaId) {
                            views.forEach(v -> v.onPropertyChanged(value));
                        }
                    });
                }
            });
        }
    }

    @VisibleForTesting
    Map<@HvacProperty Integer, Map<@AreaId Integer, List<HvacView>>> getHvacPropertyViewMap() {
        return mHvacPropertyViewMap;
    }

    @Override
    public void onLocaleListChanged() {
        // Call {@link HvacView#onLocaleListChanged} on all {@link HvacView} instances.
        for (Map<@AreaId Integer, List<HvacView>> subMap : mHvacPropertyViewMap.values()) {
            for (List<HvacView> views : subMap.values()) {
                for (HvacView view : views) {
                    view.onLocaleListChanged();
                }
            }
        }
    }

    private void handleHvacPowerOn(CarPropertyValue hvacPowerOnValue) {
        Boolean isPowerOn = (Boolean) hvacPowerOnValue.getValue();
        synchronized (mLock) {
            mAreaIdToIsHvacPowerOn.put(hvacPowerOnValue.getAreaId(), isPowerOn);
        }
        if (!isPowerOn) {
            return;
        }

        for (int propertyId: mHvacPowerDependentProperties) {
            mExecutor.execute(() -> {
                ArrayList<Integer> areaIds = getAreaIdsFromTargetAreaId(propertyId,
                        hvacPowerOnValue.getAreaId());
                for (int areaId: areaIds) {
                    CarPropertyValue valueOrNull = getPropertyValueOrNull(propertyId, areaId);
                    if (valueOrNull != null) {
                        handleHvacPropertyChange(propertyId, valueOrNull);
                    }
                }
            });
        }
    }

    @Nullable
    private CarPropertyValue<?> getPropertyValueOrNull(int propertyId, int areaId) {
        if (isHvacPowerDependentPropAndNotAvailable(propertyId, areaId)) {
            return null;
        }
        try {
            return mCarPropertyManager.getProperty(propertyId, areaId);
        } catch (Exception e) {
            Log.e(TAG, "getPropertyValueOrNull - Error while getting HVAC propertyId: "
                    + VehiclePropertyIds.toString(propertyId) + ", areaId: "
                    + Integer.toHexString(areaId) + ": ", e);
        }
        return null;
    }

    private boolean isHvacPowerDependentPropAndNotAvailable(int propertyId, int areaId) {
        if (!mHvacPowerDependentProperties.contains(propertyId)) {
            return false;
        }
        ArrayList<Integer> powerDependentAreaIds = getAreaIdsFromTargetAreaId(propertyId, areaId);
        synchronized (mLock) {
            for (int powerDependentAreaId: powerDependentAreaIds) {
                for (int i  = 0; i < mAreaIdToIsHvacPowerOn.size(); ++i) {
                    if ((mAreaIdToIsHvacPowerOn.keyAt(i) & powerDependentAreaId)
                            == powerDependentAreaId) {
                        return !mAreaIdToIsHvacPowerOn.valueAt(i);
                    }
                }
            }
        }
        Log.w(TAG, "isHvacPowerDependentPropAndNotAvailable - For propertyId: + "
                + VehiclePropertyIds.toString(propertyId) + ", areaId: "
                + Integer.toHexString(areaId) + ", no matching area ID found for HVAC_POWER_ON.");
        return false;
    }

    private void registerHvacPropertyEventListeners() {
        for (int i = 0; i < HVAC_PROPERTIES.length; i++) {
            @HvacProperty Integer propertyId = HVAC_PROPERTIES[i];
            if (mCarPropertyManager.getCarPropertyConfig(propertyId) == null) {
                Log.w(TAG, "registerHvacPropertyEventListeners - propertyId: + "
                        + VehiclePropertyIds.toString(propertyId) + " is not implemented."
                        + " Skipping registering callback.");
                continue;
            }
            mCarPropertyManager.registerCallback(mPropertyEventCallback, propertyId,
                    CarPropertyManager.SENSOR_RATE_ONCHANGE);
        }
    }

    private void addHvacViewToMap(@HvacProperty int propId, @AreaId int areaId,
            HvacView v) {
        mHvacPropertyViewMap.computeIfAbsent(propId, k -> new HashMap<>())
                .computeIfAbsent(areaId, k -> new ArrayList<>())
                .add(v);
    }

    private void removeHvacViewFromMap(@HvacProperty int propId, @AreaId int areaId, HvacView v) {
        Map<Integer, List<HvacView>> viewsRegisteredForProp = mHvacPropertyViewMap.get(propId);
        if (viewsRegisteredForProp != null) {
            List<HvacView> registeredViews = viewsRegisteredForProp.get(areaId);
            if (registeredViews != null) {
                registeredViews.remove(v);
                if (registeredViews.isEmpty()) {
                    viewsRegisteredForProp.remove(areaId);
                    if (viewsRegisteredForProp.isEmpty()) {
                        mHvacPropertyViewMap.remove(propId);
                    }
                }
            }
        }
    }
}