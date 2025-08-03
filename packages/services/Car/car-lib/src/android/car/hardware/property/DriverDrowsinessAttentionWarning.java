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
 * Used to enumerate the current warning state of {@link
 * android.car.VehiclePropertyIds#DRIVER_DROWSINESS_ATTENTION_WARNING}.
 *
 * <p>This enum could be extended in future releases to include additional feature states.
 *
 * @hide
 */
@FlaggedApi(FLAG_ANDROID_VIC_VEHICLE_PROPERTIES)
@SystemApi
public final class DriverDrowsinessAttentionWarning {
    /**
     * This state is used as an alternative for any {@code DriverDrowsinessAttentionWarning} value
     * that is not defined in the platform. Ideally, implementations of {@link
     * android.car.VehiclePropertyIds#DRIVER_DROWSINESS_ATTENTION_WARNING} should not use this
     * state. The framework can use this field to remain backwards compatible if {@code
     * DriverDrowsinessAttentionWarning} is extended to include additional states.
     */
    public static final int OTHER = 0;
    /**
     * When the driver drowsiness and attention warning is enabled, and the driver's current
     * drowsiness and attention level does not warrant the system to send a warning.
     */
    public static final int NO_WARNING = 1;
    /**
     * When the driver drowsiness and attention warning is enabled, and the system is warning the
     * driver based on its assessment of the driver's current drowsiness and attention level.
     */
    public static final int WARNING = 2;

    private DriverDrowsinessAttentionWarning() {}

    /**
     * Returns a user-friendly representation of a {@code DriverDrowsinessAttentionWarning}.
     */
    @NonNull
    public static String toString(
            @DriverDrowsinessAttentionWarning.DriverDrowsinessAttentionWarningInt
            int driverDrowsinessAttentionWarning) {
        String driverDrowsinessAttentionWarningString = ConstantDebugUtils.toName(
                DriverDrowsinessAttentionWarning.class, driverDrowsinessAttentionWarning);
        return (driverDrowsinessAttentionWarningString != null)
                ? driverDrowsinessAttentionWarningString
                : "0x" + Integer.toHexString(driverDrowsinessAttentionWarning);
    }

    /** @hide */
    @IntDef({OTHER, NO_WARNING, WARNING})
    @Retention(RetentionPolicy.SOURCE)
    public @interface DriverDrowsinessAttentionWarningInt {}
}
