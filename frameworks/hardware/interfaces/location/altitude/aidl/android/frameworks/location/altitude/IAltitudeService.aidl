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

import android.frameworks.location.altitude.AddMslAltitudeToLocationRequest;
import android.frameworks.location.altitude.AddMslAltitudeToLocationResponse;
import android.frameworks.location.altitude.GetGeoidHeightRequest;
import android.frameworks.location.altitude.GetGeoidHeightResponse;

/** @hide */
@VintfStability
interface IAltitudeService {

    /**
     * Calls {@link android.location.altitude.AltitudeConverter#addMslAltitudeToLocation(
     * android.content.Context, android.location.Location)} via system service.
     */
    AddMslAltitudeToLocationResponse addMslAltitudeToLocation(
            in AddMslAltitudeToLocationRequest request);

    /**
     * Returns the geoid height (a.k.a. geoid undulation) at the location specified in {@code
     * request}. The geoid height at a location is defined as the difference between an altitude
     * measured above the World Geodetic System 1984 reference ellipsoid (WGS84) and its
     * corresponding Mean Sea Level altitude.
     */
     GetGeoidHeightResponse getGeoidHeight(in GetGeoidHeightRequest request);
}
