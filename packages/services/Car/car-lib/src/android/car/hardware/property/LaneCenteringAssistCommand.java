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
 * Used by {@link android.car.VehiclePropertyIds#LANE_CENTERING_ASSIST_COMMAND} to enumerate
 * commands.
 *
 * @hide
 */
@SystemApi
public final class LaneCenteringAssistCommand {
    /**
     * When {@link android.car.VehiclePropertyIds#LANE_CENTERING_ASSIST_STATE} = {@link
     * LaneCenteringAssistState#ENABLED}, this command sends a request to activate steering control
     * that keeps the vehicle centered in its lane. While waiting for the LCA System to take control
     * of the vehicle, {@link android.car.VehiclePropertyIds#LANE_CENTERING_ASSIST_STATE} will be in
     * the {@link LaneCenteringAssistState#ACTIVATION_REQUESTED} state. Once the vehicle takes
     * control of steering, then {@link android.car.VehiclePropertyIds#LANE_CENTERING_ASSIST_STATE}
     * will be in the {@link LaneCenteringAssistState#ACTIVATED} state. Otherwise, an error
     * can be communicated through an {@link ErrorState} value.
     */
    public static final int ACTIVATE = 1;

    /**
     * When {@link android.car.VehiclePropertyIds#LANE_CENTERING_ASSIST_STATE} is set to {@link
     * LaneCenteringAssistState#ACTIVATION_REQUESTED} or {@link LaneCenteringAssistState#ACTIVATED},
     * this command deactivates steering control and the driver should take full control of the
     * vehicle. If this command succeeds, {@link
     * android.car.VehiclePropertyIds#LANE_CENTERING_ASSIST_STATE} will be updated to {@link
     * LaneCenteringAssistState#ENABLED}.
     */
    public static final int DEACTIVATE = 2;

    private LaneCenteringAssistCommand() {}

    /**
     * Returns a user-friendly representation of a {@code LaneCenteringAssistCommand}.
     */
    @NonNull
    public static String toString(
            @LaneCenteringAssistCommandInt int laneCenteringAssistCommand) {
        switch (laneCenteringAssistCommand) {
            case ACTIVATE:
                return "ACTIVATE";
            case DEACTIVATE:
                return "DEACTIVATE";
            default:
                return "0x" + Integer.toHexString(laneCenteringAssistCommand);
        }
    }

    /** @hide */
    @IntDef({ACTIVATE, DEACTIVATE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface LaneCenteringAssistCommandInt {}
}

