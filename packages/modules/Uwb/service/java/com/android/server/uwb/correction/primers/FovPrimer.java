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
package com.android.server.uwb.correction.primers;

import static com.android.server.uwb.correction.math.MathHelper.F_PI;

import static java.lang.Math.abs;
import static java.lang.Math.cos;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.server.uwb.correction.math.SphericalVector;
import com.android.server.uwb.correction.math.SphericalVector.Annotated;
import com.android.server.uwb.correction.pose.IPoseSource;

/**
 * Limits the field view of incoming UWB readings by replacing angles outside the defined limits
 * with predicted angles (which are based on the last-known-good angle combined with pose changes).
 *
 * Most UWB hardware suffers from accuracy issues beyond a certain azimuth or elevation, and
 * conversely will produce erroneous steep angles when there are issues with the signal.
 *
 * This implementation imposes a double-cone-shaped FOV, meaning that the device can see a circular
 * area in front and behind the phone. Other primers can limit the view to forward-only if
 * necessary.
 */
public class FovPrimer implements IPrimer {
    private double mLastGoodReferenceTimeMs;
    private final double mCosFov;

    /**
     * Creates a new instance of the FovPrimer class.
     * @param fov The field-of-view to impose on hardware coordinates.
     */
    public FovPrimer(float fov) {
        if (fov > F_PI) {
            fov = F_PI;
        }
        this.mCosFov = cos(fov);
    }

    /**
     * Applies corrections to a raw position.
     * @param input      The original UWB reading.
     * @param prediction The previous filtered UWB result adjusted by the pose change since then.
     * @param poseSource A pose source that may indicate phone orientation.
     * @param timeMs When the input occurred, in ms since boot.
     * @return A replacement value for the UWB input that has been corrected for the situation.
     */
    @Override
    public SphericalVector.Annotated prime(
            @NonNull SphericalVector.Annotated input,
            @Nullable SphericalVector prediction,
            @Nullable IPoseSource poseSource,
            long timeMs) {
        if (prediction == null) {
            return input;
        }

        float azimuth = input.hasAzimuth ? input.azimuth : prediction.azimuth;
        float elevation = input.hasElevation ? input.elevation : prediction.elevation;

        // Compute the absolute cartesian Z-value of the az/el vector, ignoring distance,
        // as an indicator of the position's relation to the FOV.
        double zValue = abs(cos(elevation) * cos(azimuth));

        // Faster equivalent to acos(zValue) < mFov
        if (zValue < mCosFov) {
            Annotated result = new Annotated(SphericalVector.fromRadians(
                    prediction.azimuth,
                    prediction.elevation,
                    input.distance),
                    true, true, input.hasDistance);
            result.copyFomFrom(input);

            // Tweak the FOM based on how fresh our data is.
            double elapsedMs = timeMs - mLastGoodReferenceTimeMs;
            double fom = Math.max(1 - elapsedMs / 1000 * FALLOFF_FOM_PER_SEC, MINIMUM_FOM);
            result.azimuthFom *= fom;

            return result;
        } else {
            mLastGoodReferenceTimeMs = timeMs;
        }

        return input;
    }
}
