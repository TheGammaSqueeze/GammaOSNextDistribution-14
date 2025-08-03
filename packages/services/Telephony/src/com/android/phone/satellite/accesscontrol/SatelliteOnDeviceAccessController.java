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
package com.android.phone.satellite.accesscontrol;

import android.annotation.NonNull;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;

/**
 * A class that performs location-based access control for satellite communication synchronously
 * without exposing implementation details.
 */
public abstract class SatelliteOnDeviceAccessController implements Closeable {

    /**
     * Returns the default {@link SatelliteOnDeviceAccessController}. This method will open the
     * underlying storage and may carry some CPU and I/O expense; callers may want to hold the
     * {@link SatelliteOnDeviceAccessController} object for multiple lookups to amortize that cost
     * but at the cost of some memory, or close it immediately after a single use.
     *
     * @param file The input file that contains the location-based access restriction information.
     * @throws IOException in the unlikely event of errors when reading underlying file(s)
     * @throws IllegalArgumentException if the input file format does not match the format defined
     * by the device overlay configs.
     */
    public static SatelliteOnDeviceAccessController create(
            @NonNull File file) throws IOException, IllegalArgumentException {
        return S2RangeSatelliteOnDeviceAccessController.create(file);
    }

    /**
     * Returns a token for a given location. See {@link LocationToken} for details.
     */
    public static LocationToken createLocationTokenForLatLng(double latDegrees, double lngDegrees,
            int s2Level) {
        return S2RangeSatelliteOnDeviceAccessController
                .createLocationTokenForLatLng(latDegrees, lngDegrees, s2Level);
    }

    /**
     * Returns {@code true} if the satellite communication is allowed at the provided location,
     * {@code false} otherwise.
     *
     * @throws IOException in the unlikely event of errors when reading the underlying file
     */
    public abstract boolean isSatCommunicationAllowedAtLocation(LocationToken locationToken)
            throws IOException;

    /**
     * Returns the S2 level of the file.
     */
    public abstract int getS2Level();

    /**
     * A class that represents an area with the same value. Two locations with tokens that
     * {@link #equals(Object) equal each other} will definitely return the same value.
     *
     * <p>Depending on the implementation, it may be cheaper to obtain a {@link LocationToken} than
     * doing a full lookup.
     */
    public abstract static class LocationToken {
        @Override
        public abstract boolean equals(Object other);

        @Override
        public abstract int hashCode();

        /** This will print out the location information */
        public abstract String toPiiString();
    }
}
