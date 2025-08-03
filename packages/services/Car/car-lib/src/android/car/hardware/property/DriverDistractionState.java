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

import static android.car.feature.Flags.FLAG_ANDROID_VIC_VEHICLE_PROPERTIES;

import android.annotation.FlaggedApi;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.SystemApi;

import com.android.car.internal.util.ConstantDebugUtils;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Used to enumerate the current state of {@link
 * android.car.VehiclePropertyIds#DRIVER_DISTRACTION_STATE}.
 *
 * <p>This list of states may be extended in future releases to include additional states.
 *
 * @hide
 */
@FlaggedApi(FLAG_ANDROID_VIC_VEHICLE_PROPERTIES)
@SystemApi
public final class DriverDistractionState {
    /**
     * This state is used as an alternative for any {@code DriverDistractionState} value
     * that is not defined in the platform. Ideally, implementations of {@link
     * android.car.VehiclePropertyIds#DRIVER_DISTRACTION_STATE} should not use this state.
     * The framework can use this field to remain backwards compatible if {@code
     * DriverDistractionState} is extended to include additional states.
     */
    public static final int OTHER = 0;

    /**
     * The system detects that the driver is attentive / not distracted.
     */
    public static final int NOT_DISTRACTED = 1;

    /**
     * The system detects that the driver is distracted, which can be anything that
     * reduces the driver's foucs on the primary task of driving/controlling the
     * vehicle.
     */
    public static final int DISTRACTED = 2;

    private DriverDistractionState() {}

    /**
     * Returns a user-friendly representation of a {@code DriverDistractionState}.
     */
    @NonNull
    public static String toString(
            @DriverDistractionStateInt int driverDistractionState) {
        String driverDistractionStateString = ConstantDebugUtils.toName(
                DriverDistractionState.class, driverDistractionState);
        return (driverDistractionStateString != null)
                ? driverDistractionStateString
                : "0x" + Integer.toHexString(driverDistractionState);
    }

    /** @hide */
    @IntDef({
        OTHER,
        NOT_DISTRACTED,
        DISTRACTED})
    @Retention(RetentionPolicy.SOURCE)
    public @interface DriverDistractionStateInt {}
}
