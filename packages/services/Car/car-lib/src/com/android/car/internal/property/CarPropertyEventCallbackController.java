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

package com.android.car.internal.property;

import static java.util.Objects.requireNonNull;

import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyEvent;
import android.car.hardware.property.CarPropertyManager.CarPropertyEventCallback;
import android.util.Log;

import java.util.concurrent.Executor;

/**
 * Manages a group of property IDs and area IDs registered for the same {@link
 * CarPropertyEventCallback} at possibly different update rates.
 *
 * @hide
 */
public final class CarPropertyEventCallbackController extends CarPropertyEventController {
    // Abbreviating TAG because class name is longer than the 23 character Log tag limit.
    private static final String TAG = "CPECallbackController";
    private static final boolean DBG = Log.isLoggable(TAG, Log.DEBUG);
    private final CarPropertyEventCallback mCarPropertyEventCallback;
    private final Executor mExecutor;

    public CarPropertyEventCallbackController(CarPropertyEventCallback carPropertyEventCallback,
            Executor executor) {
        super(/* useSystemLogger= */ false);
        requireNonNull(carPropertyEventCallback);
        mCarPropertyEventCallback = carPropertyEventCallback;
        mExecutor = executor;
    }

    /**
     * Forward a {@link CarPropertyEvent} to {@link #mCarPropertyEventCallback} if the event is an
     * error event or the update rate threshold is met for a change event.
     */
    public void onEvent(CarPropertyEvent carPropertyEvent) {
        requireNonNull(carPropertyEvent);
        CarPropertyValue<?> carPropertyValue =
                getCarPropertyValueIfCallbackRequired(carPropertyEvent);
        if (carPropertyValue == null) {
            if (DBG) {
                Log.d(TAG, "onEvent should not be invoked, event: " + carPropertyEvent);
            }
            return;
        }
        CarPropertyEvent updatedCarPropertyEvent;
        if (!carPropertyValue.equals(carPropertyEvent.getCarPropertyValue())) {
            updatedCarPropertyEvent = new CarPropertyEvent(carPropertyEvent.getEventType(),
                    carPropertyValue, carPropertyEvent.getErrorCode());
        } else {
            updatedCarPropertyEvent = carPropertyEvent;
        }
        switch (updatedCarPropertyEvent.getEventType()) {
            case CarPropertyEvent.PROPERTY_EVENT_PROPERTY_CHANGE:
                mExecutor.execute(() -> mCarPropertyEventCallback.onChangeEvent(carPropertyValue));
                break;
            case CarPropertyEvent.PROPERTY_EVENT_ERROR:
                if (DBG) {
                    Log.d(TAG, "onErrorEvent for event: " + updatedCarPropertyEvent);
                }
                mExecutor.execute(() -> mCarPropertyEventCallback.onErrorEvent(
                        carPropertyValue.getPropertyId(), carPropertyValue.getAreaId(),
                        updatedCarPropertyEvent.getErrorCode()));
                break;
            default:
                Log.e(TAG, "onEvent: unknown errorCode=" + updatedCarPropertyEvent.getErrorCode()
                        + ", for event: " + updatedCarPropertyEvent);
                break;
        }
    }

    public Executor getExecutor() {
        return mExecutor;
    }
}
