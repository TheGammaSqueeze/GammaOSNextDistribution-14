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
 * Used to enumerate the current state of {@link
 * android.car.VehiclePropertyIds#FORWARD_COLLISION_WARNING_STATE}.
 *
 * <p>This list of states may be extended in future releases to include additional states.
 * @hide
 */
@SystemApi
public final class ForwardCollisionWarningState {
    /**
     * This state is used as an alternative for any {@code ForwardCollisionWarningState} value that
     * is not defined in the platform. Ideally, implementations of {@link
     * android.car.VehiclePropertyIds#FORWARD_COLLISION_WARNING_STATE} should not use this state.
     * The framework can use this field to remain backwards compatible if {@code
     * ForwardCollisionWarningState} is extended to include additional states.
     */
    public static final int OTHER = 0;

    /**
     * FCW is enabled and monitoring safety, but no potential collision is detected.
     */
    public static final int NO_WARNING = 1;

    /**
     * FCW is enabled, detects a potential collision, and is actively warning the user.
     */
    public static final int WARNING = 2;

    private ForwardCollisionWarningState() {}

    /**
     * Returns a user-friendly representation of a {@code ForwardCollisionWarningState}.
     */
    @NonNull
    public static String toString(
            @ForwardCollisionWarningStateInt int forwardCollisionWarningState) {
        switch (forwardCollisionWarningState) {
            case OTHER:
                return "OTHER";
            case NO_WARNING:
                return "NO_WARNING";
            case WARNING:
                return "WARNING";
            default:
                return "0x" + Integer.toHexString(forwardCollisionWarningState);
        }
    }

    /** @hide */
    @IntDef({OTHER, NO_WARNING, WARNING})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ForwardCollisionWarningStateInt {}
}

