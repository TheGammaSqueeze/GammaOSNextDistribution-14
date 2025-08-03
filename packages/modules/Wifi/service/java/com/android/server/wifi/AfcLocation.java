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

package com.android.server.wifi;

import android.location.Location;

import org.json.JSONObject;

/**
 * Helper class to store a coarse location used to send to the AFC server.
 */
public abstract class AfcLocation {
    // Reference level for the value of the height field.
    // "AGL": Above Ground Level. Antenna height as measured relative to the local
    // ground level.
    // "AMSL": Above Mean Sea Level. Antenna height as measured with respect to
    // WGS84 datum.
    enum HeightType {
        AGL,
        AMSL;
    }

    // Indicator for whether the deployment of the AP or Fixed Client device is located indoors,
    // outdoor, or is unknown.
    enum LocationType {
        LOCATION_TYPE_UNKNOWN,
        INDOOR,
        OUTDOOR;
    }

    int mLocationType = LocationType.LOCATION_TYPE_UNKNOWN.ordinal();
    double mHeight;
    double mVerticalUncertainty;
    String mHeightType;

    public AfcLocation(Location location) {
        mHeight = location.getAltitude();
        mVerticalUncertainty = location.getVerticalAccuracyMeters();
        mHeightType = AfcEllipseLocation.HeightType.AMSL.name();
    }

    /**
     * Converts this AfcLocation class to JSON format needed for AFC queries.
     */
    public abstract JSONObject toJson();

    /**
     * Determine if location comparingLocation is within bounds of this AfcLocation object.
     */
    public abstract AfcLocationUtil.InBoundsCheckResult checkLocation(Location comparingLocation);
}
