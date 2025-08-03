/*
 * Copyright 2023 The Android Open Source Project
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

package com.android.bluetooth.gatt;

import android.bluetooth.BluetoothStatusCodes;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

/**
 * Distance Measurement Native Interface to/from JNI.
 */
@VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
public class DistanceMeasurementNativeInterface {
    private static final String TAG = DistanceMeasurementNativeInterface.class.getSimpleName();

    @GuardedBy("INSTANCE_LOCK")
    private static DistanceMeasurementNativeInterface sInstance;

    private static final Object INSTANCE_LOCK = new Object();

    private DistanceMeasurementManager mDistanceMeasurementManager;

    /**
     * Do not modify without updating distance_measurement_manager.h
     * match up with DistanceMeasurementErrorCode enum of distance_measurement_manager.h
     */
    private static final int REASON_FEATURE_NOT_SUPPORTED_LOCAL = 0;
    private static final int REASON_FEATURE_NOT_SUPPORTED_REMOTE = 1;
    private static final int REASON_LOCAL_REQUEST = 2;
    private static final int REASON_REMOTE_REQUEST = 3;
    private static final int REASON_DURATION_TIMEOUT = 4;
    private static final int REASON_NO_LE_CONNECTION = 5;
    private static final int REASON_INVALID_PARAMETERS = 6;
    private static final int REASON_INTERNAL_ERROR = 7;


    private DistanceMeasurementNativeInterface() {}

    /**
     * This class is a singleton because native library should only be loaded once
     *
     * @return default instance
     */
    public static DistanceMeasurementNativeInterface getInstance() {
        synchronized (INSTANCE_LOCK) {
            if (sInstance == null) {
                sInstance = new DistanceMeasurementNativeInterface();
            }
            return sInstance;
        }
    }

    /** Set singleton instance. */
    @VisibleForTesting
    public static void setInstance(DistanceMeasurementNativeInterface instance) {
        synchronized (INSTANCE_LOCK) {
            sInstance = instance;
        }
    }

    void init(DistanceMeasurementManager manager) {
        mDistanceMeasurementManager = manager;
        initializeNative();
    }

    void cleanup() {
        cleanupNative();
    }

    void startDistanceMeasurement(String address, int interval, int method) {
        startDistanceMeasurementNative(address, interval, method);
    }

    void stopDistanceMeasurement(String address, int method) {
        stopDistanceMeasurementNative(address, method);
    }

    void onDistanceMeasurementStarted(String address, int method) {
        mDistanceMeasurementManager.onDistanceMeasurementStarted(address, method);
    }

    void onDistanceMeasurementStartFail(String address, int reason, int method) {
        mDistanceMeasurementManager.onDistanceMeasurementStartFail(address,
                convertErrorCode(reason), method);
    }

    void onDistanceMeasurementStopped(String address, int reason, int method) {
        mDistanceMeasurementManager.onDistanceMeasurementStopped(address,
                convertErrorCode(reason), method);
    }

    void onDistanceMeasurementResult(String address, int centimeter, int errorCentimeter,
            int azimuthAngle, int errorAzimuthAngle, int altitudeAngle, int errorAltitudeAngle,
            int method) {
        mDistanceMeasurementManager.onDistanceMeasurementResult(address, centimeter,
                errorCentimeter, azimuthAngle, errorAzimuthAngle, altitudeAngle, errorAltitudeAngle,
                method);
    }

    private int convertErrorCode(int errorCode) {
        switch (errorCode) {
            case REASON_FEATURE_NOT_SUPPORTED_LOCAL:
                return BluetoothStatusCodes.FEATURE_NOT_SUPPORTED;
            case REASON_FEATURE_NOT_SUPPORTED_REMOTE:
                return BluetoothStatusCodes.ERROR_REMOTE_OPERATION_NOT_SUPPORTED;
            case REASON_LOCAL_REQUEST:
                return BluetoothStatusCodes.REASON_LOCAL_STACK_REQUEST;
            case REASON_REMOTE_REQUEST:
                return BluetoothStatusCodes.REASON_REMOTE_REQUEST;
            case REASON_DURATION_TIMEOUT:
                return BluetoothStatusCodes.ERROR_TIMEOUT;
            case REASON_NO_LE_CONNECTION:
                return BluetoothStatusCodes.ERROR_NO_LE_CONNECTION;
            case REASON_INVALID_PARAMETERS:
                return BluetoothStatusCodes.ERROR_BAD_PARAMETERS;
            case REASON_INTERNAL_ERROR:
                return BluetoothStatusCodes.ERROR_DISTANCE_MEASUREMENT_INTERNAL;
            default:
                return BluetoothStatusCodes.ERROR_UNKNOWN;
        }
    }

    /**********************************************************************************************/
    /******************************************* native *******************************************/
    /**********************************************************************************************/

    private native void initializeNative();

    private native void cleanupNative();

    private native void startDistanceMeasurementNative(String address, int interval, int method);

    private native void stopDistanceMeasurementNative(String address, int method);
}
