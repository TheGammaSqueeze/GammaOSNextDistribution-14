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

package android.frameworks.location.altitude;

/** @hide */
@VintfStability
parcelable GetGeoidHeightResponse {

    /**
     * Defines the difference in meters between {@link android.location.Location#getAltitude()} and
     * {@link android.location.Location#getMslAltitudeMeters()} at this location.
     */
    double geoidHeightMeters;

    /**
     * Defines the independent altitude error contribution in meters of applying {@link
     * #geoidHeightMeters} at this location.
     */
    float geoidHeightErrorMeters;

    /**
     * Defines the horizontal distance in meters from this location where {@link #geoidHeightMeters}
     * can be applied with additional altitude error.
     */
    double expirationDistanceMeters;

    /**
     * Defines the additional altitude independent error contribution in meters of applying {@link
     * #geoidHeightMeters} at a different location within a horizontal distance of {@link
     * #expirationDistanceMeters}.
     */
    float additionalGeoidHeightErrorMeters;

    /** True if this call was successful. */
    boolean success;
}
