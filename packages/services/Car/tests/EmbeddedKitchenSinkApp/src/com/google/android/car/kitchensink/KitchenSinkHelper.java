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

package com.google.android.car.kitchensink;

import android.car.Car;
import android.car.hardware.property.CarPropertyManager;
import android.os.Handler;

public interface KitchenSinkHelper {
    /**
     * @return Car api
     */
    Car getCar();

    /**
     * Gets the car property manager.
     */
    CarPropertyManager getPropertyManager();

    /**
     * Uses the handler to post the runnable to run after car service is connected.
     */
    void requestRefreshManager(Runnable r, Handler h);
}
