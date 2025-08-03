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

package com.google.android.car.evs;

import static android.car.evs.CarEvsManager.ERROR_NONE;

import android.app.Activity;
import android.car.Car;
import android.car.Car.CarServiceLifecycleListener;
import android.car.evs.CarEvsManager;
import android.os.Bundle;
import android.util.Log;

public class CarEvsCameraActivity extends Activity {
    private static final String TAG = CarEvsCameraActivity.class.getSimpleName();
    private static final int CAR_WAIT_TIMEOUT_MS = 3_000;

    /** CarService status listener  */
    private final CarServiceLifecycleListener mCarServiceLifecycleListener = (car, ready) -> {
        if (!ready) {
            return;
        }

        try {
            CarEvsManager evsManager = (CarEvsManager) car.getCarManager(
                    Car.CAR_EVS_SERVICE);
            String config = getApplicationContext().getResources()
                    .getString(R.string.config_evsCameraType);
            int type = config == null ?
                    CarEvsManager.SERVICE_TYPE_REARVIEW : getServiceType(config);
            if (evsManager.startActivity(type) != ERROR_NONE) {
                Log.e(TAG, "Failed to start a camera preview activity");
            }
        } finally {
            mCar = car;
            finish();
        }
    };

    private Car mCar;

    static int getServiceType(String rawString) {
        switch (rawString) {
            case "REARVIEW": return CarEvsManager.SERVICE_TYPE_REARVIEW;
            case "SURROUNDVIEW": return CarEvsManager.SERVICE_TYPE_SURROUNDVIEW;
            case "FRONTVIEW": return CarEvsManager.SERVICE_TYPE_FRONTVIEW;
            case "LEFTVIEW": return CarEvsManager.SERVICE_TYPE_LEFTVIEW;
            case "RIGHTVIEW": return CarEvsManager.SERVICE_TYPE_RIGHTVIEW;
            case "DRIVERVIEW": return CarEvsManager.SERVICE_TYPE_DRIVERVIEW;
            case "FRONT_PASSENGERSVIEW": return CarEvsManager.SERVICE_TYPE_FRONT_PASSENGERSVIEW;
            case "REAR_PASSENGERSVIEW": return CarEvsManager.SERVICE_TYPE_REAR_PASSENGERSVIEW;
            case "USER_DEFINEDVIEW": return CarEvsManager.SERVICE_TYPE_USER_DEFINED;
            default:
                Log.w(TAG, "Unknown service type: " + rawString);
                return CarEvsManager.SERVICE_TYPE_REARVIEW;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Car.createCar(getApplicationContext(), /* handler = */ null, CAR_WAIT_TIMEOUT_MS,
                mCarServiceLifecycleListener);
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy");
        if (mCar != null) {
            // Explicitly stops monitoring the car service's status
            mCar.disconnect();
        }

        super.onDestroy();
    }
}
