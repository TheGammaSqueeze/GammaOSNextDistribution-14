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

package com.android.systemui;

import static android.car.CarOccupantZoneManager.DISPLAY_TYPE_MAIN;

import android.car.Car;
import android.car.CarOccupantZoneManager;
import android.view.Display;

import com.android.systemui.car.users.CarSystemUIUserUtil;

/**
 * Application class for CarSystemUI.
 */
public class CarSystemUIApplication extends SystemUIApplication {

    private boolean mIsSecondaryUserSystemUI;

    @Override
    public void onCreate() {
        mIsSecondaryUserSystemUI = CarSystemUIUserUtil.isSecondaryMUMDSystemUI();
        super.onCreate();
        if (mIsSecondaryUserSystemUI) {
            Car car = Car.createCar(this);
            if (car == null) {
                return;
            }
            CarOccupantZoneManager manager = (CarOccupantZoneManager) car.getCarManager(
                    Car.CAR_OCCUPANT_ZONE_SERVICE);
            if (manager != null) {
                CarOccupantZoneManager.OccupantZoneInfo info = manager.getMyOccupantZone();
                if (info != null) {
                    Display display = manager.getDisplayForOccupant(info, DISPLAY_TYPE_MAIN);
                    if (display != null) {
                        updateDisplay(display.getDisplayId());
                        startSystemUserServicesIfNeeded();
                    }
                }
            }
            car.disconnect();
        }
    }

    @Override
    void startSecondaryUserServicesIfNeeded() {
        if (mIsSecondaryUserSystemUI) {
            // Per-user services are not needed since this sysui process is running as the real user
            return;
        }
        super.startSecondaryUserServicesIfNeeded();
    }
}
