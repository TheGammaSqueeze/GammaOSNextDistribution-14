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

package com.android.car.pm;

import android.view.accessibility.AccessibilityEvent;

import com.android.car.CarLocalServices;
import com.android.car.internal.CarSafetyAccessibilityServiceImplBase;
import com.android.internal.annotations.Keep;

/**
 * An accessibility service to notify the Car Service of any change in the window state. The car
 * safety related code can read the events sent from this service and take the necessary actions.
 */
@Keep
public final class CarSafetyAccessibilityServiceImpl extends CarSafetyAccessibilityServiceImplBase {
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        CarPackageManagerService carPackageManagerService =
                CarLocalServices.getService(CarPackageManagerService.class);
        if (carPackageManagerService != null) {
            carPackageManagerService.onWindowChangeEvent(event);
        }
    }
}
