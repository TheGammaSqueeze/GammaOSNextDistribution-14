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

package com.android.car.internal;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.os.Handler;
import android.os.RemoteException;

/**
 * ICarBase exposes the APIs in {@link android.car.Car} that are used internally by car service
 * or car manager.
 *
 * This interface allows faking the implementation in unit tests.
 *
 * @hide
 */
public interface ICarBase {
    /**
     * Gets the context.
     */
    Context getContext();

    /**
     * Gets the event handler.
     */
    Handler getEventHandler();

    /**
     * Handles a {@link RemoteException} thrown from car service.
     */
    <T> T handleRemoteExceptionFromCarService(RemoteException e, T returnValue);

    /**
     * Handles a {@link RemoteException} thrown from car service.
     */
    void handleRemoteExceptionFromCarService(RemoteException e);

    /**
     * Get car specific service manager as in {@link Context#getSystemService(String)}. Returned
     * {@link Object} should be type-casted to the desired service manager.
     *
     * @deprecated Use {@link #getCarManager(Class)} instead.
     *
     * @param serviceName Name of service that should be created like {@link #SENSOR_SERVICE}.
     * @return Matching service manager or null if there is no such service.
     */
    @Deprecated
    @Nullable
    Object getCarManager(String serviceName);

    /**
     * Get car specific service manager by class as in {@link Context#getSystemService(Class)}.
     * Returns the desired service. No type casting is needed.
     *
     * <p>For example, to get the manager for sensor service,
     * <code>CarSensorManager carSensorManager = car.getCarManager(CarSensorManager.class);</code>
     *
     * @param serviceClass The class of the desired service.
     * @return Matching service manager or {@code null} if there is no such service.
     */
    @Nullable
    <T> T getCarManager(@NonNull Class<T> serviceClass);
}
