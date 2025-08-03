/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.timezone.location.lookup;

import com.android.timezone.location.common.PiiLoggable;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * A class that performs location-based time zone lookups synchronously without exposing
 * implementation details.
 */
public abstract class GeoTimeZonesFinder implements Closeable {

    /**
     * Returns the default {@link GeoTimeZonesFinder}. This method will open the underlying storage
     * and may carry some CPU and I/O expense; callers may want to hold the
     * {@link GeoTimeZonesFinder} object for multiple lookups to amortize that cost but at the cost
     * of some memory, or close it immediately after a single use.
     *
     * @throws IOException in the unlikely event of errors when reading underlying file(s)
     */
    // @NonNull
    public static GeoTimeZonesFinder create(File file) throws IOException {
        return S2RangeFileBasedGeoTimeZonesFinder.create(file);
    }

    /**
     * Returns a token for a given location. See {@link LocationToken} for details.
     *
     * @throws IOException in the unlikely event of errors when reading the underlying file
     */
    // @NonNull
    public abstract LocationToken createLocationTokenForLatLng(double latDegrees, double lngDegrees)
            throws IOException;

    /**
     * Returns the time zone ID(s) associated with a location. The list returned will never be
     * {@code null}, but may be empty, contain one time zone ID or several. When there are several
     * it is typical to treat the first as the preferred, but multiple IDs can be returned, for
     * example, for border cases in case the caller has other information that may indicate one of
     * the other candidate zones should be used.
     *
     * <p>The IDs returned can be passed to methods like {@link
     * java.util.TimeZone#getTimeZone(String)},
     *
     * @throws IOException in the unlikely event of errors when reading the underlying file
     */
    // @NonNull
    public abstract List<String> findTimeZonesForLatLng(double latDegrees, double lngDegrees)
            throws IOException;

    /**
     * Returns the time zone ID(s) associated with a location token. The list returned will never be
     * {@code null}, but may be empty, contain one time zone ID or several. When there are several
     * it is typical to treat the first as the preferred, but multiple IDs can be returned, for
     * example, for border cases in case the caller has other information that may indicate one of
     * the other candidate zones should be used.
     *
     * <p>The IDs returned can be passed to methods like {@link
     * java.util.TimeZone#getTimeZone(String)},
     *
     * @throws IOException in the unlikely event of errors when reading the underlying file
     */
    // @NonNull
    public abstract List<String> findTimeZonesForLocationToken(LocationToken locationToken)
            throws IOException;

    /**
     * A class that represents an area with the same time zones. Two locations with tokens that
     * {@link #equals(Object) equal each other} will definitely return the same time zone IDs.
     *
     * <p>Depending on the implementation, it may be cheaper to obtain a {@link LocationToken} than
     * doing a full lookup.
     */
    public abstract static class LocationToken implements PiiLoggable {
        @Override
        public abstract boolean equals(Object other);

        @Override
        public abstract int hashCode();
    }
}
