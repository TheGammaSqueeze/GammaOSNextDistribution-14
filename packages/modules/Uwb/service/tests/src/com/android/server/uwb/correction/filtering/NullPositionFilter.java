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
import com.android.server.uwb.correction.math.Vector3;
import com.android.server.uwb.correction.pose.IPoseSource;

import java.util.Objects;

/**
 * A filter that simply applies pose changes without any filtering.
 */
public class NullPositionFilter implements IPositionFilter {
    private Pose mLastPose;
    private SphericalVector.Annotated mValue;

    /**
     * Adds a value to the filter.
     *
     * @param value   The value to add to the filter.
     * @param timeMs When the value occurred, used to determine the latency introduced by
     *                the filter. Note that this has no effect on the order in which the filter
     *                operates. This is in milliseconds, relative to any consistent epoch.
     */
    @Override
    public void add(@NonNull SphericalVector.Annotated value, long timeMs) {
        Objects.requireNonNull(value);
        mValue = value;
    }

    /**
     * Computes a predicted UWB position based on the new pose.
     *
     * @param timeMs The time for which the UWB prediction should be computed. This is in
     * milliseconds, relative to any consistent epoch.
     * @return A vector representing the last added value, compensated by any pose changes.
     */
    @Override
    public SphericalVector.Annotated compute(long timeMs) {
        return mValue;
    }

    /**
     * Updates the last value to compensate for the changed pose.
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
            // This conversion (Spherical -> Cartesian -> transform -> Spherical) is the best
            // I have for right now. At the expense of readability, this could more efficiently
            // transform spherical coordinates if performance is a problem.

            // Last known position of tag, relative to camera as of previous pose.
            Vector3 vecFromOldCam = compute(timeMs).toCartesian();

            // Convert to position of tag, relative to camera after the pose changed.
            Vector3 vecFromNewCam = deltaPose.transformPoint(vecFromOldCam);

            // New azimuth, elevation and distance based on this new tag position.
            mValue = SphericalVector.fromCartesian(vecFromNewCam).toAnnotated()
                .copyFomFrom(mValue);
        }
        mLastPose = newPose;
    }
}
