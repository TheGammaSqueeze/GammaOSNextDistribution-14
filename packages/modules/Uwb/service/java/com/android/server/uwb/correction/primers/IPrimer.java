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
import com.android.server.uwb.correction.pose.IPoseSource;

/**
 * Given known data about a UWB reading, applies corrections that correct for nonlinearities,
 * missing data or other hardware limitations.
 */
public interface IPrimer {
    /** How quickly the FOM falls when readings are predicted. Use by implementing classes. */
    double FALLOFF_FOM_PER_SEC = 0.2f;
    /** The worst possible FOM from falloff. */
    double MINIMUM_FOM = 0.1f;

    /**
     * Applies corrections to a raw position.
     *
     * @param input The original UWB reading.
     * @param prediction The previous filtered UWB result adjusted by the pose change since then.
     * @param poseSource A pose source that may indicate phone orientation.
     * @param timeMs When the input occurred, in ms since boot.
     * @return A replacement value for the UWB input that has been corrected for  the situation.
     */
    SphericalVector.Annotated prime(
            @NonNull SphericalVector.Annotated input,
            @Nullable SphericalVector prediction,
            @Nullable IPoseSource poseSource,
            long timeMs
    );
}
