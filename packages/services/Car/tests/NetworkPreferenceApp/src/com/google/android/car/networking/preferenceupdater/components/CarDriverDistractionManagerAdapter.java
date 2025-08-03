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
package com.google.android.car.networking.preferenceupdater.components;

import android.car.Car;
import android.car.drivingstate.CarUxRestrictionsManager;
import android.content.Context;

/**
 * Module that interacts with CarDriverDistractionManager inside the Car service and provides latest
 * information about about driving state to the caller.
 */
public final class CarDriverDistractionManagerAdapter {
    private final CarUxRestrictionsManager mCarUxRestrictionsManager;
    private final Car mCar;

    public CarDriverDistractionManagerAdapter(Context ctx) {
        // Connect to car service
        mCar = Car.createCar(ctx);
        mCarUxRestrictionsManager = (CarUxRestrictionsManager) mCar.getCarManager(
                Car.CAR_UX_RESTRICTION_SERVICE);
    }
    /** Method that has to be called during destroy. */
    public void destroy() {
        mCar.disconnect();
    }

    /**
     * Returns true/false boolean based on the whether driver can be distracted right now or not
     */
    public boolean allowedToBeDistracted() {
        return mCarUxRestrictionsManager == null
                || !mCarUxRestrictionsManager.getCurrentCarUxRestrictions()
                        .isRequiresDistractionOptimization();
    }
}
