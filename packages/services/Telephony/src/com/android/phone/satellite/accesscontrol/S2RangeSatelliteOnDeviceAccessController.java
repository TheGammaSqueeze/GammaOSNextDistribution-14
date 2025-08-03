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
import android.telephony.Rlog;

import com.android.storage.s2.S2LevelRange;
import com.android.telephony.sats2range.read.SatS2RangeFileReader;

import com.google.common.geometry.S2CellId;
import com.google.common.geometry.S2LatLng;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

/**
 * An implementation of {@link SatelliteOnDeviceAccessController} that uses
 * {@link SatS2RangeFileReader}.
 */
final class S2RangeSatelliteOnDeviceAccessController extends SatelliteOnDeviceAccessController {
    private static final String TAG = "S2RangeSatelliteOnDeviceAccessController";
    private static final boolean DBG = false;

    @NonNull private final SatS2RangeFileReader mSatS2RangeFileReader;

    private final int mS2Level;

    private S2RangeSatelliteOnDeviceAccessController(
            @NonNull SatS2RangeFileReader satS2RangeFileReader, int s2Level) {
        mSatS2RangeFileReader = Objects.requireNonNull(satS2RangeFileReader);
        mS2Level = s2Level;
    }

    /**
     * Returns a new {@link S2RangeSatelliteOnDeviceAccessController} using the specified data file.
     *
     * @param file The input file that contains the S2-range-based access restriction information.
     * @throws IOException in the event of a problem while reading the underlying file.
     * @throws IllegalArgumentException if either the S2 level defined by
     * {@code config_oem_enabled_satellite_s2cell_level} or the satellite access allow defined by
     * {@code config_oem_enabled_satellite_access_allow} does not match the values included in the
     * header of the input file.
     */
    public static S2RangeSatelliteOnDeviceAccessController create(
            @NonNull File file) throws IOException, IllegalArgumentException {
        SatS2RangeFileReader reader = SatS2RangeFileReader.open(file);
        int s2Level = reader.getS2Level();
        return new S2RangeSatelliteOnDeviceAccessController(reader, s2Level);
    }

    public static LocationToken createLocationTokenForLatLng(
            double latDegrees, double lngDegrees, int s2Level) {
        return new LocationTokenImpl(getS2CellId(latDegrees, lngDegrees, s2Level).id());
    }

    @Override
    public boolean isSatCommunicationAllowedAtLocation(LocationToken locationToken)
            throws IOException {
        if (!(locationToken instanceof LocationTokenImpl)) {
            throw new IllegalArgumentException("Unknown locationToken=" + locationToken);
        }
        LocationTokenImpl locationTokenImpl = (LocationTokenImpl) locationToken;
        return isSatCommunicationAllowedAtLocation(locationTokenImpl.getS2CellId());
    }

    @Override
    public int getS2Level() {
        return mS2Level;
    }

    private boolean isSatCommunicationAllowedAtLocation(long s2CellId) throws IOException {
        S2LevelRange entry = mSatS2RangeFileReader.findEntryByCellId(s2CellId);
        if (mSatS2RangeFileReader.isAllowedList()) {
            // The file contains an allowed list of S2 cells. Thus, satellite is allowed if an
            // entry is found
            return (entry != null);
        } else {
            // The file contains a disallowed list of S2 cells. Thus, satellite is allowed if an
            // entry is not found
            return (entry == null);
        }
    }

    private static S2CellId getS2CellId(double latDegrees, double lngDegrees, int s2Level) {
        // Create the leaf S2 cell containing the given S2LatLng
        S2CellId cellId = S2CellId.fromLatLng(S2LatLng.fromDegrees(latDegrees, lngDegrees));

        // Return the S2 cell at the expected S2 level
        return cellId.parent(s2Level);
    }

    @Override
    public void close() throws IOException {
        mSatS2RangeFileReader.close();
    }

    private static void logd(@NonNull String log) {
        Rlog.d(TAG, log);
    }

    private static void loge(@NonNull String log) {
        Rlog.e(TAG, log);
    }

    private static class LocationTokenImpl extends LocationToken {

        private final long mS2CellId;

        private LocationTokenImpl(long s2CellId) {
            this.mS2CellId = s2CellId;
        }

        long getS2CellId() {
            return mS2CellId;
        }

        @Override
        public String toString() {
            return DBG ? toPiiString() : "LocationToken{<redacted>}";
        }

        @Override
        public String toPiiString() {
            return "LocationToken{"
                    + "mS2CellId=" + mS2CellId
                    + '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof LocationTokenImpl)) {
                return false;
            }
            LocationTokenImpl that = (LocationTokenImpl) o;
            return mS2CellId == that.mS2CellId;
        }

        @Override
        public int hashCode() {
            return Objects.hash(mS2CellId);
        }
    }
}
