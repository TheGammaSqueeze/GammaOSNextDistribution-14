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

import com.android.server.uwb.correction.math.SphericalVector;
import com.android.server.uwb.correction.math.SphericalVector.Annotated;
import com.android.server.uwb.correction.pose.IPoseSource;

public class NullPrimer implements IPrimer {

    /**
     * Applies corrections to a raw position.
     *
     * @param input The original UWB reading.
     * @param prediction A prediction of where the signal probably came from.
     * @param poseSource A pose source that may indicate phone orientation.
     * @param timeMs When the input occurred. This is in milliseconds, relative to any consistent
     * epoch.
     * @return A replacement value for the UWB input that has been corrected for  the situation.
     */
    @Override
    public Annotated prime(@NonNull Annotated input, @Nullable SphericalVector prediction,
            @Nullable IPoseSource poseSource, long timeMs) {
        // This test primer will just turn any negative azimuth values to positive ones,
        // and use the prediction for any missing values.
        float azimuth = input.azimuth;
        float elevation = input.elevation;
        float distance = input.distance;
        if (!input.hasAzimuth) {
            azimuth = prediction.azimuth;
        }
        if (!input.hasElevation) {
            elevation = prediction.elevation;
        }
        if (!input.hasDistance) {
            distance = prediction.distance;
        }
        return new Annotated(SphericalVector.fromRadians(Math.abs(azimuth), elevation, distance));
    }
}
