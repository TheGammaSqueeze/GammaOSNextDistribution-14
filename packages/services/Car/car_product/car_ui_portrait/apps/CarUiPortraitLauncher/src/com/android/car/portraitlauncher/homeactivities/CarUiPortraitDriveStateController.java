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

package com.android.car.portraitlauncher.homeactivities;

import static android.car.drivingstate.CarDrivingStateEvent.DRIVING_STATE_MOVING;
import static android.car.drivingstate.CarDrivingStateEvent.DRIVING_STATE_UNKNOWN;

import android.car.Car;
import android.car.drivingstate.CarDrivingStateEvent;
import android.car.drivingstate.CarDrivingStateManager;
import android.content.Context;
import android.os.Build;
import android.util.Log;

/**
 * Controls the driving state for {@link CarUiPortraitHomeScreen}
 */
public class CarUiPortraitDriveStateController {
    private static final boolean DBG = Build.IS_DEBUGGABLE;
    private static final String TAG = CarUiPortraitDriveStateController.class.getSimpleName();

    private int mCurrentDrivingState = DRIVING_STATE_UNKNOWN;
    private final CarDrivingStateManager.CarDrivingStateEventListener mDrivingStateEventListener =
            this::handleDrivingStateChange;

    public CarUiPortraitDriveStateController(Context context) {
        Car car = Car.createCar(context);
        if (car != null) {
            CarDrivingStateManager mDrivingStateManager =
                    (CarDrivingStateManager) car.getCarManager(Car.CAR_DRIVING_STATE_SERVICE);
            mDrivingStateManager.registerListener(mDrivingStateEventListener);
            mDrivingStateEventListener.onDrivingStateChanged(
                    mDrivingStateManager.getCurrentCarDrivingState());
        } else {
            Log.e(TAG, "Failed to initialize car");
        }
    }

    private void handleDrivingStateChange(CarDrivingStateEvent carDrivingStateEvent) {
        mCurrentDrivingState = carDrivingStateEvent.eventValue;
    }

    /**
     * Returns true if the driving state is moving
     */
    public boolean isDrivingStateMoving() {
        logIfDebuggable("Driving state is " + mCurrentDrivingState);
        return mCurrentDrivingState == DRIVING_STATE_MOVING;
    }

    /**
     * Print debug log in debug build
     */
    private static void logIfDebuggable(String message) {
        if (DBG) {
            Log.d(TAG, message);
        }
    }
}
