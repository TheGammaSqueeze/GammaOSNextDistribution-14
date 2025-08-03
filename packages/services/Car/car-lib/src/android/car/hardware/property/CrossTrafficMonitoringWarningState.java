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
 * Used to enumerate the state of Cross Traffic Monitoring Warning system.
 *
 * @hide
 */
@FlaggedApi(FLAG_ANDROID_VIC_VEHICLE_PROPERTIES)
@SystemApi
public final class CrossTrafficMonitoringWarningState {

    /**
     * This state is used as an alternative to any {@code CrossTrafficMonitoringWarningState} value
     * that is not defined in the platform. Ideally, implementations of {@link
     * android.car.VehiclePropertyIds#CROSS_TRAFFIC_MONITORING_WARNING_STATE} should not use this
     * state. The framework can use this field to remain backwards compatible if {@code
     * CrossTrafficMonitoringWarningState} is extended to include additional states.
     */
    public static final int OTHER = 0;
    /**
     * Cross Traffic Monitoring Warning is enabled and monitoring safety, but no potential collision
     * is detected.
     */
    public static final int NO_WARNING = 1;
    /**
     * Cross Traffic Monitoring Warning is enabled and is actively warning the user of incoming
     * moving objects coming from the driver's left side in front of the vehicle.
     */
    public static final int WARNING_FRONT_LEFT = 2;
    /**
     * Cross Traffic Monitoring Warning is enabled and is actively warning the user of incoming
     * moving objects coming from the driver's right side in front of the vehicle.
     */
    public static final int WARNING_FRONT_RIGHT = 3;
    /**
     * Cross Traffic Monitoring Warning is enabled and is actively warning the user of incoming
     * moving objects coming from both the driver's left side and the driver's right side in front
     * of the vehicle.
     */
    public static final int WARNING_FRONT_BOTH = 4;
    /**
     * Cross Traffic Monitoring Warning is enabled and is actively warning the user of incoming
     * moving objects coming from the driver's left side behind the vehicle.
     */
    public static final int WARNING_REAR_LEFT = 5;
    /**
     * Cross Traffic Monitoring Warning is enabled and is actively warning the user of incoming
     * moving objects coming from the driver's right side behind the vehicle.
     */
    public static final int WARNING_REAR_RIGHT = 6;
    /**
     * Cross Traffic Monitoring Warning is enabled and is actively warning the user of incoming
     * moving objects coming from the driver's left side and the driver's right side behind the
     * vehicle.
     */
    public static final int WARNING_REAR_BOTH = 7;

    private CrossTrafficMonitoringWarningState() {}

    /**
     * Returns a user-friendly representation of {@code CrossTrafficMonitoringWarningState}.
     */
    @NonNull
    public static String toString(
            @CrossTrafficMonitoringWarningStateInt int crossTrafficMonitoringWarningState) {
        String crossTrafficMonitoringWarningStateString = ConstantDebugUtils.toName(
                CrossTrafficMonitoringWarningState.class, crossTrafficMonitoringWarningState);
        return (crossTrafficMonitoringWarningStateString != null)
                ? crossTrafficMonitoringWarningStateString
                : "0x" + Integer.toHexString(crossTrafficMonitoringWarningState);
    }

    /** @hide */
    @IntDef({OTHER, NO_WARNING, WARNING_FRONT_LEFT, WARNING_FRONT_RIGHT, WARNING_FRONT_BOTH,
            WARNING_REAR_LEFT, WARNING_REAR_RIGHT, WARNING_REAR_BOTH})
    @Retention(RetentionPolicy.SOURCE)
    public @interface CrossTrafficMonitoringWarningStateInt {}
}
