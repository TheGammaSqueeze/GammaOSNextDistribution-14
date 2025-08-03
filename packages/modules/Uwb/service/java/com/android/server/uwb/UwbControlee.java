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

package com.android.server.uwb;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.uwb.AngleMeasurement;
import android.uwb.AngleOfArrivalMeasurement;
import android.uwb.DistanceMeasurement;
import android.uwb.RangingMeasurement;
import android.uwb.UwbAddress;

import com.android.server.uwb.correction.UwbFilterEngine;
import com.android.server.uwb.correction.math.SphericalVector;

/**
 * Represents a remote controlee that is involved in a session.
 */
public class UwbControlee implements AutoCloseable {
    private static final long SEC_TO_MILLI = 1000;
    private final UwbAddress mUwbAddress;
    private final UwbInjector mUwbInjector;
    private final UwbFilterEngine mEngine;
    /** Error value to use when the engine produces a result that wasn't in the original reading. */
    private static final double DEFAULT_ERROR_DISTANCE = 0.0;
    private long mLastMeasurementInstant;
    private long mPredictionTimeoutMilli = 3000;

    /**
     * Creates a new UwbControlee.
     *
     * @param uwbAddress The address of the controlee.
     */
    public UwbControlee(
            @NonNull UwbAddress uwbAddress,
            @Nullable UwbFilterEngine engine,
            @Nullable UwbInjector uwbInjector) {
        mUwbAddress = uwbAddress;
        mEngine = engine;
        mUwbInjector = uwbInjector;
        if (mUwbInjector != null
                && mUwbInjector.getDeviceConfigFacade() != null) {
            // Injector or deviceConfigFacade might be null during tests and this is fine.
            mPredictionTimeoutMilli = mUwbInjector
                    .getDeviceConfigFacade()
                    .getPredictionTimeoutSeconds() * SEC_TO_MILLI;
        }
    }

    /**
     * Gets the address of the controlee.
     *
     * @return A UwbAddress of the associated controlee.
     */
    public UwbAddress getUwbAddress() {
        return mUwbAddress;
    }

    /** Shuts down any controlee-specific work. */
    @Override
    public void close() {
        if (mEngine != null) {
            mEngine.close();
        }
    }

    /**
     * Updates a RangingMeasurement builder to produce a filtered value. If the filter engine
     *  is not configured, this will not affect the builder.
     * @param rmBuilder The {@link RangingMeasurement.Builder} to reconfigure.
     */
    public void filterMeasurement(RangingMeasurement.Builder rmBuilder) {
        if (mEngine == null) {
            // Engine is disabled. Don't modify the builder.
            return;
        }
        RangingMeasurement rawMeasurement = rmBuilder.build();

        if (rawMeasurement.getStatus() != RangingMeasurement.RANGING_STATUS_SUCCESS) {
            if (getTime() - mPredictionTimeoutMilli > mLastMeasurementInstant) {
                // It's been some time since we last got a good report. Stop reporting values.
                return;
            }
        } else {
            mLastMeasurementInstant = getTime();
        }

        // Gather az/el/dist
        AngleOfArrivalMeasurement aoaMeasurement = rawMeasurement.getAngleOfArrivalMeasurement();
        DistanceMeasurement distMeasurement = rawMeasurement.getDistanceMeasurement();
        boolean hasAzimuth = false;
        boolean hasElevation = false;
        boolean hasDistance = false;
        float azimuth = 0;
        float elevation = 0;
        float distance = 0;
        double azimuthFom = 1;
        double elevationFom = 1;
        double distanceFom = 1;
        long nowMs = mUwbInjector.getElapsedSinceBootMillis();
        if (aoaMeasurement != null) {
            if (aoaMeasurement.getAzimuth() != null
                    && aoaMeasurement.getAzimuth().getConfidenceLevel() > 0) {
                hasAzimuth = true;
                azimuth = (float) aoaMeasurement.getAzimuth().getRadians();
                azimuthFom = aoaMeasurement.getAzimuth().getConfidenceLevel();
            }
            if (aoaMeasurement.getAltitude() != null
                    && aoaMeasurement.getAltitude().getConfidenceLevel() > 0) {
                hasElevation = true;
                elevation = (float) aoaMeasurement.getAltitude().getRadians();
                elevationFom = aoaMeasurement.getAltitude().getConfidenceLevel();
            }
        }
        if (distMeasurement != null) {
            hasDistance = true;
            distance = (float) distMeasurement.getMeters();
            distanceFom = distMeasurement.getConfidenceLevel();
        }
        SphericalVector.Annotated sv = SphericalVector.fromRadians(azimuth, elevation, distance)
                .toAnnotated(hasAzimuth, hasElevation, hasDistance);

        sv.azimuthFom = azimuthFom;
        sv.elevationFom = elevationFom;
        sv.distanceFom = distanceFom;

        // Give to the engine.
        mEngine.add(sv, nowMs);

        SphericalVector.Annotated engineResult = mEngine.compute(nowMs);
        if (engineResult == null) {
            // Bail early - the engine didn't compute a result, so just leave the builder alone.
            return;
        }

        // Now re-generate the az/el/dist readings based on engine result.
        updateBuilder(rmBuilder, rawMeasurement, engineResult);
    }

    private long getTime() {
        if (mUwbInjector == null) {
            return 0; // Can happen during testing; no time tracking will be supported.
        }
        return mUwbInjector.getElapsedSinceBootMillis();
    }

    /**
     * Replaces az/el/dist values in a RangingMeasurement builder.
     * @param rmBuilder The RangingMeasurement builder to update.
     * @param rawMeasurement The original raw measurements. Used for fallback and confidence values.
     * @param replacement The filter engine's result.
     */
    private static void updateBuilder(RangingMeasurement.Builder rmBuilder,
            RangingMeasurement rawMeasurement,
            SphericalVector.Annotated replacement) {
        // This is fairly verbose because of how nested data is, the risk of nulls, and the
        // fact that that azimuth is required up-front, even in the builder. Refactoring so the
        // RangingMeasurement can be cloned and changed would be nice, but it would change
        // (or at least add to) an external API.

        // Switch to success - error statuses cannot have any values.
        rmBuilder.setStatus(RangingMeasurement.RANGING_STATUS_SUCCESS);

        AngleOfArrivalMeasurement aoaMeasurement = rawMeasurement.getAngleOfArrivalMeasurement();
        DistanceMeasurement distMeasurement = rawMeasurement.getDistanceMeasurement();

        AngleMeasurement azimuthMeasurement = null;
        AngleMeasurement elevationMeasurement = null;

        // Any AoA in the original measurement?
        if (aoaMeasurement != null) {
            // Any azimuth in the original measurement?
            if (aoaMeasurement.getAzimuth() != null) {
                // Yes - create a new azimuth based on the filter's output.
                azimuthMeasurement = new AngleMeasurement(
                        replacement.azimuth,
                        aoaMeasurement.getAzimuth().getErrorRadians(),
                        replacement.azimuthFom
                );
            }
            // Any elevation in the original measurement?
            if (aoaMeasurement.getAltitude() != null) {
                // Yes - create a new elevation based on the filter's output.
                elevationMeasurement = new AngleMeasurement(
                        replacement.elevation,
                        aoaMeasurement.getAltitude().getErrorRadians(),
                        replacement.elevationFom
                );
            }
        }

        AngleOfArrivalMeasurement.Builder aoaBuilder = null;
        // Only create the aoaBuilder if there was an azimuth in the original measurement.
        if (azimuthMeasurement != null) {
            aoaBuilder = new AngleOfArrivalMeasurement.Builder(azimuthMeasurement);
            if (elevationMeasurement != null) {
                aoaBuilder.setAltitude(elevationMeasurement);
            }
        }

        DistanceMeasurement.Builder distanceBuilder = new DistanceMeasurement.Builder();
        if (distMeasurement == null) {
            // No distance value. Might have been a one-way AoA.

            // RangingMeasurement.Build requires that any non-error status has a valid
            //  DistanceMeasurement, so we will create one.
            distanceBuilder.setErrorMeters(DEFAULT_ERROR_DISTANCE);
        } else {
            distanceBuilder.setErrorMeters(distMeasurement.getErrorMeters());
        }
        distanceBuilder.setConfidenceLevel(replacement.distanceFom);
        distanceBuilder.setMeters(replacement.distance);

        rmBuilder.setDistanceMeasurement(distanceBuilder.build());
        if (aoaBuilder != null) {
            rmBuilder.setAngleOfArrivalMeasurement(aoaBuilder.build());
        }
    }
}
