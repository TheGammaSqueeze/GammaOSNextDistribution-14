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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.server.uwb.correction.math.AoaVector;
import com.android.server.uwb.correction.math.SphericalVector;
import com.android.server.uwb.correction.math.SphericalVector.Annotated;
import com.android.server.uwb.correction.pose.IPoseSource;

/**
 * Converts a PDoA azimuth value to a spherical coordinate azimuth by accounting for elevation.
 * See {@link AoaVector} for information on the difference.
 * This primer is needed on hardware that does not support elevation, after the ElevationPrimer,
 * so that the estimated elevation can be used to perform the PDoA-to-azimuth conversion.
 * This primer is also needed on hardware that supports elevation, but with firmware that does
 * not perform the PDoA-to-azimuth conversion.
 */
public class AoaPrimer implements IPrimer {
    /**
     * Applies corrections to a raw position.
     *
     * @param input      The original UWB reading.
     * @param prediction The previous filtered UWB result adjusted by the pose change since then.
     * @param poseSource A pose source that may indicate phone orientation.
     * @param timeMs When the input occurred, in ms since boot.
     * @return A replacement value for the UWB input that has been corrected for  the situation.
     */
    @Override
    public SphericalVector.Annotated prime(
            @NonNull SphericalVector.Annotated input,
            @Nullable SphericalVector prediction,
            @Nullable IPoseSource poseSource,
            long timeMs) {
        if (input.hasElevation && input.hasAzimuth) {
            // Reinterpret the SphericalVector as an AoAVector, then convert it to a
            // SphericalVector.
            return new Annotated(
                    AoaVector.fromRadians(
                            input.azimuth,
                            input.elevation,
                            input.distance).toSphericalVector(),
                    true,
                    true,
                    input.hasDistance)
                    .copyFomFrom(input);
        }
        // Elevation is the same in both units. If only elevation is known, no conversion needed.
        return input;
    }
}
