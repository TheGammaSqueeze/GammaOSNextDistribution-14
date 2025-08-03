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
 * Used to enumerate the state of {@link
 * android.car.VehiclePropertyIds#ELECTRONIC_STABILITY_CONTROL_STATE}
 *
 * @hide
 */
@FlaggedApi(FLAG_ANDROID_VIC_VEHICLE_PROPERTIES)
@SystemApi
public final class ElectronicStabilityControlState {

    /**
     * This state is used as an alternative to any {@code ElectronicStabilityControlState} value
     * that is not defined in the platform. Ideally, implementations of {@link
     * android.car.VehiclePropertyIds#ELECTRONIC_STABILITY_CONTROL_STATE} should not use this state.
     * The framework can use this field to remain backwards compatible if {@code
     * ElectronicStabilityControlState} is extended to include additional states.
     */
    public static final int OTHER = 0;
    /**
     * ESC is enabled and monitoring safety, but is not actively controlling the tires to prevent
     * the car from skidding.
     */
    public static final int ENABLED = 1;
    /**
     * ESC is enabled and is actively controlling the tires to prevent the car from skidding.
     */
    public static final int ACTIVATED = 2;

    private ElectronicStabilityControlState() {}

    /**
     * Returns a user-friendly representation of {@code ElectronicStabilityControlState}.
     */
    @NonNull
    public static String toString(
            @ElectronicStabilityControlStateInt int electronicStabilityControlState) {
        String electronicStabilityControlStateString = ConstantDebugUtils.toName(
                ElectronicStabilityControlState.class, electronicStabilityControlState);
        return (electronicStabilityControlStateString != null)
                ? electronicStabilityControlStateString
                : "0x" + Integer.toHexString(electronicStabilityControlState);
    }

    /** @hide */
    @IntDef({OTHER, ENABLED, ACTIVATED})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ElectronicStabilityControlStateInt {}
}
