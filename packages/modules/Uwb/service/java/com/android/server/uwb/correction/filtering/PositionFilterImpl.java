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
package com.android.server.uwb.correction.filtering;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.server.uwb.correction.math.Pose;
import com.android.server.uwb.correction.math.SphericalVector;
import com.android.server.uwb.correction.math.SphericalVector.Annotated;
import com.android.server.uwb.correction.math.Vector3;
import com.android.server.uwb.correction.pose.IPoseSource;

import java.util.Objects;

/**
 * An implementation of a combined azimuth, distance and elevation filter, which can be shifted
 * as the pose changes.
 * A filter that operates on X/Y/Z may be faster, but it would not support filtering differently
 * for angle and distance.
 */
public class PositionFilterImpl implements IPositionFilter {
    @NonNull private final IFilter mAzimuthFilter;
    @NonNull private final IFilter mElevationFilter;
    @NonNull private final IFilter mDistanceFilter;
    private Pose mLastPose;

    public PositionFilterImpl(
            @NonNull IFilter azimuthFilter,
            @NonNull IFilter elevationFilter,
            @NonNull IFilter distanceFilter) {
        Objects.requireNonNull(azimuthFilter);
        Objects.requireNonNull(elevationFilter);
        Objects.requireNonNull(distanceFilter);
        this.mAzimuthFilter = azimuthFilter;
        this.mElevationFilter = elevationFilter;
        this.mDistanceFilter = distanceFilter;
    }

    /**
     * Adds a value to the filter.
     *
     * @param value   The value to add to the filter.
     * @param timeMs The time at which the UWB value was received, in ms since boot. This is
     * used to determine the latency introduced by the filter. Note that this has no effect on the
     * order in which the filter operates on values.
     */
    @Override
    public void add(@NonNull SphericalVector.Annotated value, long timeMs) {
        Objects.requireNonNull(value);
        if (value.hasAzimuth && value.azimuthFom != 0.0) {
            mAzimuthFilter.add(value.azimuth, timeMs, value.azimuthFom);
        }
        if (value.hasElevation && value.elevationFom != 0.0) {
            mElevationFilter.add(value.elevation, timeMs, value.elevationFom);
        }
        if (value.hasDistance && value.distanceFom != 0.0) {
            mDistanceFilter.add(value.distance, timeMs, value.distanceFom);
        }
    }

    /**
     * Computes a predicted UWB position based on the new pose.
     *
     * @param timeMs The time for which the UWB prediction should be computed, in ms since boot.
     */
    @Override
    public SphericalVector.Annotated compute(long timeMs) {
        // Cartesian extrapolation would happen here, such as target movement.
        // Spherical extrapolation can happen in the filter because it operates on
        // spherical values.

        Sample azimuth = mAzimuthFilter.getResult(timeMs);
        Sample elevation = mElevationFilter.getResult(timeMs);
        Sample distance = mDistanceFilter.getResult(timeMs);
        Annotated result = SphericalVector.fromRadians(
                azimuth.value,
                elevation.value,
                distance.value
        ).toAnnotated();
        result.azimuthFom = azimuth.fom;
        result.elevationFom = elevation.fom;
        result.distanceFom = distance.fom;
        return result;
    }

    /**
     * Updates the filter history to account for changes to the pose. Note that the entire
     * pose source object is provided, so that its capabilities can be assessed as a part
     * of the computation.
     *
     * @param poseSource The pose source that has the new pose.
     */
    @Override
    public void updatePose(@Nullable IPoseSource poseSource, long timeMs) {
        if (poseSource == null) {
            return;
        }
        Pose newPose = poseSource.getPose();
        if (mLastPose != null && newPose != null && newPose != mLastPose) {
            Pose deltaPose = Pose.compose(newPose.inverted(), mLastPose);
            updatePoseFromDelta(deltaPose, compute(timeMs));
        }
        mLastPose = newPose;
    }

    /**
     * Applies compensations to the azimuth, elevation and distance filters based on how the
     * pose changed, and how the last-known position of the tag would be affected.
     *
     * @param deltaPose A relative transform describing how the pose changed.
     * @param estimate The last known location of the UWB signal.
     */
    private void updatePoseFromDelta(@NonNull Pose deltaPose, @NonNull SphericalVector estimate) {
        // This conversion (Spherical -> Cartesian -> transform -> Spherical) is the best
        // I have for right now. At the expense of readability, this could more efficiently
        // transform spherical coordinates if performance is a problem.

        // Last known position of tag, relative to camera as of previous pose.
        Vector3 vecFromOldCam = estimate.toCartesian();

        // Convert to position of tag, relative to camera after the pose changed.
        Vector3 vecFromNewCam = deltaPose.transformPoint(vecFromOldCam);

        // New azimuth, elevation and distance based on this new tag position.
        SphericalVector newEstimate = SphericalVector.fromCartesian(vecFromNewCam);

        // Adjust the filters to represent this new estimation.
        mAzimuthFilter.compensate(newEstimate.azimuth - estimate.azimuth);
        mElevationFilter.compensate(newEstimate.elevation - estimate.elevation);
        mDistanceFilter.compensate(newEstimate.distance - estimate.distance);
    }
}
