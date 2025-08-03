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

package com.google.android.car.kitchensink.sensor;

import android.Manifest;
import android.annotation.Nullable;
import android.car.Car;
import android.car.VehiclePropertyIds;
import android.car.VehiclePropertyType;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.util.ArraySet;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.google.android.car.kitchensink.KitchenSinkHelper;
import com.google.android.car.kitchensink.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SensorsTestFragment extends Fragment {
    private static final String TAG = "CAR.SENSOR.KS";
    private static final boolean DBG = true;
    private static final boolean DBG_VERBOSE = true;
    private static final int KS_PERMISSIONS_REQUEST = 1;

    private final static String[] REQUIRED_PERMISSIONS = new String[]{
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Car.PERMISSION_MILEAGE,
        Car.PERMISSION_ENERGY,
        Car.PERMISSION_SPEED,
        Car.PERMISSION_CAR_DYNAMICS_STATE
    };
    private static final ArraySet<Integer> SENSORS_SET = new ArraySet<>(Arrays.asList(
            VehiclePropertyIds.PERF_VEHICLE_SPEED,
            VehiclePropertyIds.ENGINE_RPM,
            VehiclePropertyIds.PERF_ODOMETER,
            VehiclePropertyIds.FUEL_LEVEL,
            VehiclePropertyIds.FUEL_DOOR_OPEN,
            VehiclePropertyIds.IGNITION_STATE,
            VehiclePropertyIds.PARKING_BRAKE_ON,
            VehiclePropertyIds.GEAR_SELECTION,
            VehiclePropertyIds.NIGHT_MODE,
            VehiclePropertyIds.ENV_OUTSIDE_TEMPERATURE,
            VehiclePropertyIds.WHEEL_TICK,
            VehiclePropertyIds.ABS_ACTIVE,
            VehiclePropertyIds.TRACTION_CONTROL_ACTIVE,
            VehiclePropertyIds.EV_BATTERY_LEVEL,
            VehiclePropertyIds.EV_CHARGE_PORT_OPEN,
            VehiclePropertyIds.EV_CHARGE_PORT_CONNECTED,
            VehiclePropertyIds.EV_BATTERY_INSTANTANEOUS_CHARGE_RATE,
            VehiclePropertyIds.ENGINE_OIL_LEVEL
    ));
    private static final SparseIntArray PROPERTY_TO_RESOURCE =
            new SparseIntArray() {{
                put(VehiclePropertyIds.PERF_VEHICLE_SPEED, R.string.sensor_speed);
                put(VehiclePropertyIds.ENGINE_RPM, R.string.sensor_rpm);
                put(VehiclePropertyIds.PERF_ODOMETER, R.string.sensor_odometer);
                put(VehiclePropertyIds.FUEL_LEVEL, R.string.sensor_fuel_level);
                put(VehiclePropertyIds.FUEL_DOOR_OPEN, R.string.sensor_fuel_door_open);
                put(VehiclePropertyIds.IGNITION_STATE, R.string.sensor_ignition_status);
                put(VehiclePropertyIds.PARKING_BRAKE_ON, R.string.sensor_parking_brake);
                put(VehiclePropertyIds.GEAR_SELECTION, R.string.sensor_gear);
                put(VehiclePropertyIds.NIGHT_MODE, R.string.sensor_night);
                put(VehiclePropertyIds.ENV_OUTSIDE_TEMPERATURE, R.string.sensor_environment);
                put(VehiclePropertyIds.WHEEL_TICK, R.string.sensor_wheel_ticks);
                put(VehiclePropertyIds.ABS_ACTIVE, R.string.sensor_abs_is_active);
                put(VehiclePropertyIds.TRACTION_CONTROL_ACTIVE,
                        R.string.sensor_traction_control_is_active);
                put(VehiclePropertyIds.EV_BATTERY_LEVEL, R.string.sensor_ev_battery_level);
                put(VehiclePropertyIds.EV_CHARGE_PORT_OPEN, R.string.sensor_ev_charge_port_is_open);
                put(VehiclePropertyIds.EV_CHARGE_PORT_CONNECTED,
                        R.string.sensor_ev_charge_port_is_connected);
                put(VehiclePropertyIds.EV_BATTERY_INSTANTANEOUS_CHARGE_RATE,
                        R.string.sensor_ev_charge_rate);
                put(VehiclePropertyIds.ENGINE_OIL_LEVEL, R.string.sensor_engine_oil_level);
            }};

    private final CarPropertyManager.CarPropertyEventCallback mCarPropertyEventCallback =
            new CarPropertyManager.CarPropertyEventCallback() {
                @Override
                public void onChangeEvent(CarPropertyValue value) {
                    if (DBG_VERBOSE) {
                        Log.v(TAG, "New car property value: " + value);
                    }
                    mValueMap.put(value.getPropertyId(), value);
                    refreshCarSensorInfoText();
                }
                @Override
                public void onErrorEvent(int propId, int zone) {
                    Log.e(TAG, "propId: " + propId + " zone: " + zone);
                }
            };

    private final Handler mHandler = new Handler();
    private final Map<Integer, CarPropertyValue> mValueMap = new ConcurrentHashMap<>();
    private Context mContext;
    private KitchenSinkHelper mKitchenSinkHelper;
    private CarPropertyManager mCarPropertyManager;
    private LocationListeners mLocationListener;
    private String mNaString;
    private List<CarPropertyConfig> mCarPropertyConfigs;

    private TextView mCarSensorInfo;
    private TextView mLocationInfo;
    private TextView mAccelInfo;
    private TextView mGyroInfo;
    private TextView mMagInfo;
    private TextView mAccelUncalInfo;
    private TextView mGyroUncalInfo;
    private TextView mAccelLimitedAxesInfo;
    private TextView mGyroLimitedAxesInfo;
    private TextView mAccelLimitedAxesUncalInfo;
    private TextView mGyroLimitedAxesUncalInfo;
    private TextView mHeadingInfo;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        if (DBG) {
            Log.i(TAG, "onCreateView");
        }

        View view = inflater.inflate(R.layout.sensors, container, false);
        mContext = getActivity();
        if (!(mContext instanceof KitchenSinkHelper)) {
            throw new IllegalStateException(
                    "context does not implement " + KitchenSinkHelper.class.getSimpleName());
        }
        mKitchenSinkHelper = (KitchenSinkHelper) mContext;
        mCarSensorInfo = (TextView) view.findViewById(R.id.car_sensor_info);
        mCarSensorInfo.setMovementMethod(new ScrollingMovementMethod());
        mLocationInfo = (TextView) view.findViewById(R.id.location_info);
        mLocationInfo.setMovementMethod(new ScrollingMovementMethod());
        mAccelInfo = (TextView) view.findViewById(R.id.accel_info);
        mGyroInfo = (TextView) view.findViewById(R.id.gyro_info);
        mMagInfo = (TextView) view.findViewById(R.id.mag_info);
        mAccelUncalInfo = (TextView) view.findViewById(R.id.accel_uncal_info);
        mGyroUncalInfo = (TextView) view.findViewById(R.id.gyro_uncal_info);
        mAccelLimitedAxesInfo = (TextView) view.findViewById(R.id.accel_limited_axes_info);
        mGyroLimitedAxesInfo = (TextView) view.findViewById(R.id.gyro_limited_axes_info);
        mAccelLimitedAxesUncalInfo =
                (TextView) view.findViewById(R.id.accel_limited_axes_uncal_info);
        mGyroLimitedAxesUncalInfo = (TextView) view.findViewById(R.id.gyro_limited_axes_uncal_info);
        mHeadingInfo = (TextView) view.findViewById(R.id.heading_info);

        mNaString = getContext().getString(R.string.sensor_na);
        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        initPermissions();
    }

    @Override
    public void onResume() {
        super.onResume();
        Set<String> missingPermissions = checkExistingPermissions();
        if (!missingPermissions.isEmpty()) {
            Log.e(TAG, "Permissions not granted. Cannot initialize sensors. " + missingPermissions);
            return;
        }

        mKitchenSinkHelper.requestRefreshManager(
                this::initSensors, new Handler(getContext().getMainLooper()));
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mCarPropertyManager != null) {
            mCarPropertyManager.unregisterCallback(mCarPropertyEventCallback);
        }
        if (mLocationListener != null) {
            mLocationListener.stopListening();
        }
    }

    private void initSensors() {
        try {
            initCarSensor();
            initLocationSensor();
        } catch (Exception e) {
            Log.e(TAG, "initSensors() exception caught ", e);
        }

    }

    private void initCarSensor() {
        if (mCarPropertyManager == null) {
            mCarPropertyManager = mKitchenSinkHelper.getPropertyManager();
        }
        mCarPropertyConfigs = mCarPropertyManager.getPropertyList(SENSORS_SET);

        for (CarPropertyConfig property : mCarPropertyConfigs) {
            float rate = CarPropertyManager.SENSOR_RATE_NORMAL;
            if (property.getChangeMode()
                    == CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE) {
                rate = CarPropertyManager.SENSOR_RATE_ONCHANGE;
            }
            mCarPropertyManager.registerCallback(mCarPropertyEventCallback,
                    property.getPropertyId(), rate);
        }
    }

    private void initLocationSensor() {
        if (mLocationListener == null) {
            mLocationListener = new LocationListeners(getContext(),
                    new LocationInfoTextUpdateListener());
        }
        mLocationListener.startListening();
    }

    private void initPermissions() {
        Set<String> missingPermissions = checkExistingPermissions();
        if (!missingPermissions.isEmpty()) {
            requestPermissions(missingPermissions);
        }
    }

    private Set<String> checkExistingPermissions() {
        Set<String> missingPermissions = new HashSet<String>();
        for (String permission : REQUIRED_PERMISSIONS) {
            if (mContext.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(permission);
            }
        }
        return missingPermissions;
    }

    private void requestPermissions(Set<String> permissions) {
        Log.d(TAG, "requesting additional permissions=" + permissions);
        requestPermissions(permissions.toArray(new String[permissions.size()]),
                KS_PERMISSIONS_REQUEST);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
            int[] grantResults) {
        Log.d(TAG, "onRequestPermissionsResult reqCode=" + requestCode);
    }

    private void refreshCarSensorInfoText() {
        String summaryString;
        List<String> summary = formSummary();
        summaryString = TextUtils.join("\n", summary);
        mHandler.post(() -> mCarSensorInfo.setText(summaryString));
    }

    private List<String> formSummary() {
        List<String> summary = new ArrayList<>();
        for (CarPropertyConfig propertyConfig : mCarPropertyConfigs) {
            int propertyId = propertyConfig.getPropertyId();
            CarPropertyValue propertyValue = mValueMap.get(propertyId);
            if (propertyValue != null
                    && propertyValue.getStatus() != CarPropertyValue.STATUS_AVAILABLE) {
                propertyValue = null;
            }
            int resourceId = PROPERTY_TO_RESOURCE.get(propertyId);
            // for wheel_tick, add the configuration.
            if (propertyId == VehiclePropertyIds.WHEEL_TICK) {
                if (propertyValue != null) {
                    Long[] wheelTickData = (Long[]) propertyValue.getValue();
                    summary.add(getContext().getString(R.string.sensor_wheel_ticks,
                            getTimestamp(propertyValue),
                            wheelTickData[0],
                            wheelTickData[1],
                            wheelTickData[2],
                            wheelTickData[3],
                            wheelTickData[4]));
                } else {
                    summary.add(getContext().getString(R.string.sensor_wheel_ticks,
                            getTimestamp(propertyValue),
                            mNaString, mNaString, mNaString, mNaString, mNaString));
                }
                List<Integer> configArray = propertyConfig.getConfigArray();
                summary.add(getContext().getString(R.string.sensor_wheel_ticks_cfg,
                        configArray.get(0),
                        configArray.get(1),
                        configArray.get(2),
                        configArray.get(3),
                        configArray.get(4)));
            } else {
                summary.add(getContext().getString(
                        resourceId, getTimestamp(propertyValue),
                        getStringOfPropertyValue(propertyValue)));
            }
        }
        return summary;
    }

    private String getTimestamp(CarPropertyValue value) {
        if (value == null) {
            return mNaString;
        }
        return Double.toString(value.getTimestamp() / (1000L * 1000L * 1000L)) + " sec";
    }

    private String getTimestampNow() {
        return Double.toString(System.nanoTime() / (1000L * 1000L * 1000L)) + " sec";
    }

    private String getStringOfPropertyValue(CarPropertyValue value) {
        String defaultString = mNaString;
        if (value != null) {
            if (isArrayType(value.getPropertyId())) {
                defaultString = Arrays.toString((Object[]) value.getValue());
            } else {
                defaultString = value.getValue().toString();
            }
        }
        return defaultString;
    }

    private boolean isArrayType(int propertyId) {
        int mask = propertyId & VehiclePropertyType.MASK;
        return mask == VehiclePropertyType.FLOAT_VEC
                || mask == VehiclePropertyType.INT32_VEC
                || mask == VehiclePropertyType.INT64_VEC;
    }

    public class LocationInfoTextUpdateListener {
        public void setLocationField(String value) {
            setTimestampedTextField(mLocationInfo, value);
        }

        public void setAccelField(String value) {
            setTimestampedTextField(mAccelInfo, value);
        }

        public void setGyroField(String value) {
            setTimestampedTextField(mGyroInfo, value);
        }

        public void setMagField(String value) {
            setTimestampedTextField(mMagInfo, value);
        }

        public void setAccelUncalField(String value) {
            setTimestampedTextField(mAccelUncalInfo, value);
        }

        public void setGyroUncalField(String value) {
            setTimestampedTextField(mGyroUncalInfo, value);
        }

        public void setAccelLimitedAxesField(String value) {
            setTimestampedTextField(mAccelLimitedAxesInfo, value);
        }

        public void setGyroLimitedAxesField(String value) {
            setTimestampedTextField(mGyroLimitedAxesInfo, value);
        }

        public void setAccelLimitedAxesUncalField(String value) {
            setTimestampedTextField(mAccelLimitedAxesUncalInfo, value);
        }

        public void setGyroLimitedAxesUncalField(String value) {
            setTimestampedTextField(mGyroLimitedAxesUncalInfo, value);
        }

        public void setHeadingField(String value) {
            setTimestampedTextField(mHeadingInfo, value);
        }

        private void setTimestampedTextField(TextView text, String value) {
            synchronized (SensorsTestFragment.this) {
                text.setText(getTimestampNow() + ": " + value);
                Log.d(TAG, "setText: " + value);
            }
        }
    }
}
