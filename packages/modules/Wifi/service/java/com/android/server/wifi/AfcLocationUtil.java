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

import java.util.Random;

/**
 * Class that converts a Location object to a new AfcLocation and checks if a Location object is in
 * bounds of an AfcLocation object.
 */
public class AfcLocationUtil {
    // Enum that has values for inside, on the border, and outside the ellipse
    public enum InBoundsCheckResult {
        INSIDE_AFC_LOCATION,
        ON_BORDER,
        OUTSIDE_AFC_LOCATION,
    }

    /**
     * Return a new AfcEllipseLocation object with default values.
     */
    public AfcLocation createAfcLocation(Location location) {
        return new AfcEllipseLocation(AfcEllipseLocation.DEFAULT_SEMI_MINOR_AXIS_METERS,
                AfcEllipseLocation.DEFAULT_SEMI_MAJOR_AXIS_METERS,
                AfcEllipseLocation.DEFAULT_ORIENTATION,
                AfcEllipseLocation.DEFAULT_CENTER_LEEWAY_DEGREES,
                new Random(), location
        );
    }

    /**
     * Determine if location comparingLocation is within bounds of the afcLocation object.
     */
    public AfcLocationUtil.InBoundsCheckResult checkLocation(AfcLocation afcLocation,
            Location comparingLocation) {
        return afcLocation.checkLocation(comparingLocation);
    }
}
