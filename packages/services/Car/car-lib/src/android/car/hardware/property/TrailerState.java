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

package android.car.hardware.property;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.SystemApi;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Used to enumerate the current state of {@link android.car.VehiclePropertyIds#TRAILER_PRESENT}.
 *
 * @hide
 */
@SystemApi
public final class TrailerState {

    /**
     * This state is used as an alternative for any {@code TrailerState} value that is not defined
     * in the platform. Ideally, implementations of {@link
     * android.car.VehiclePropertyIds#TRAILER_PRESENT} should not use this state. The framework can
     * use this field to remain backwards compatible if {@code TrailerState} is extended to include
     * additional states.
     */
    public static final int STATE_UNKNOWN = 0;

    /**
     * A trailer is not attached to the vehicle.
     */
    public static final int STATE_NOT_PRESENT = 1;

    /**
     * A trailer is attached to the vehicle.
     */
    public static final int STATE_PRESENT = 2;

    /**
     * The state of the trailer is not available due to an error.
     */
    public static final int STATE_ERROR = 3;

    private TrailerState() {}

    /**
     * Returns a user-friendly representation of a {@code TrailerState}.
     */
    @NonNull
    public static String toString(
            @TrailerStateInt int trailerState) {
        switch (trailerState) {
            case STATE_UNKNOWN:
                return "STATE_UNKNOWN";
            case STATE_NOT_PRESENT:
                return "STATE_NOT_PRESENT";
            case STATE_PRESENT:
                return "STATE_PRESENT";
            case STATE_ERROR:
                return "STATE_ERROR";
            default:
                return "0x" + Integer.toHexString(trailerState);
        }
    }

    /** @hide */
    @IntDef({STATE_UNKNOWN, STATE_NOT_PRESENT, STATE_PRESENT, STATE_ERROR})
    @Retention(RetentionPolicy.SOURCE)
    public @interface TrailerStateInt {}
}

