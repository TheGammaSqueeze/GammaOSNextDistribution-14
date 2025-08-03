/*
 * Copyright (C) 2022 The Android Open Source Project
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

package androidx.core.uwb.backend.impl.internal;

import androidx.annotation.IntRange;
import androidx.annotation.Nullable;

import java.util.Locale;

/** Position of a device during ranging. */
public class RangingPosition {
    public static final int RSSI_UNKNOWN = -128;
    public static final int RSSI_MIN = -127;
    public static final int RSSI_MAX = -1;

    private final RangingMeasurement mDistance;
    @Nullable private final RangingMeasurement mAzimuth;
    @Nullable private final RangingMeasurement mElevation;
    @Nullable private final DlTdoaMeasurement mDlTdoaMeasurement;
    private final long mElapsedRealtimeNanos;
    private final int mRssi;

    public RangingPosition(
            RangingMeasurement distance,
            @Nullable RangingMeasurement azimuth,
            @Nullable RangingMeasurement elevation,
            long elapsedRealtimeNanos) {
        this(distance,
                azimuth,
                elevation,
                null, // DlTdoaMeasurement
                elapsedRealtimeNanos,
                RSSI_UNKNOWN);
    }

    public RangingPosition(
            RangingMeasurement distance,
            @Nullable RangingMeasurement azimuth,
            @Nullable RangingMeasurement elevation,
            @Nullable DlTdoaMeasurement dlTdoaMeasurement,
            long elapsedRealtimeNanos,
            int rssi) {
        this.mDistance = distance;
        this.mAzimuth = azimuth;
        this.mElevation = elevation;
        this.mDlTdoaMeasurement = dlTdoaMeasurement;
        this.mElapsedRealtimeNanos = elapsedRealtimeNanos;
        this.mRssi = rssi;
    }

    /** Gets the distance in meters of the ranging device, or null if not available. */
    public RangingMeasurement getDistance() {
        return mDistance;
    }

    /**
     * Gets the azimuth angle in degrees of the ranging device, or null if not available. The range
     * is (-90, 90].
     */
    @Nullable
    public RangingMeasurement getAzimuth() {
        return mAzimuth;
    }

    /**
     * Gets the elevation angle in degrees of the ranging device, or null if not available. The
     * range is (-90, 90].
     */
    @Nullable
    public RangingMeasurement getElevation() {
        return mElevation;
    }

    /** Returns nanoseconds since boot when the ranging position was taken. */
    public long getElapsedRealtimeNanos() {
        return mElapsedRealtimeNanos;
    }

    /** Returns the measured RSSI in dBm. */
    @IntRange(from = RSSI_UNKNOWN, to = RSSI_MAX)
    public int getRssiDbm() {
        return mRssi;
    }

    /**
     * Gets {@link DlTdoaMeasurement} related to Dl-TDoA, or null if not available
     */
    @Nullable
    public DlTdoaMeasurement getDlTdoaMeasurement() {
        return mDlTdoaMeasurement;
    }

    @Override
    public String toString() {
        String formatted =
                String.format(
                        Locale.US,
                        "elapsedRealtime (ms) %d | distance (m) %f",
                        mElapsedRealtimeNanos / 1000000,
                        mDistance.getValue());
        if (mAzimuth != null) {
            formatted += String.format(Locale.US, " | azimuth: %f", mAzimuth.getValue());
        }
        if (mElevation != null) {
            formatted += String.format(Locale.US, " | elevation: %f", mElevation.getValue());
        }
        formatted += String.format(Locale.US, " | rssi: %d", mRssi);
        if (mDlTdoaMeasurement != null) {
            formatted += String.format(Locale.US, " | dlTdoa: %s", mDlTdoaMeasurement);
        }
        return formatted;
    }
}
