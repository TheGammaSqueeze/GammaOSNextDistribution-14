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
package com.android.server.uwb.correction.pose;

import androidx.annotation.NonNull;

import com.android.server.uwb.correction.math.Matrix;
import com.android.server.uwb.correction.math.Pose;
import com.android.server.uwb.correction.math.Quaternion;
import com.android.server.uwb.correction.math.Vector3;

import com.google.common.primitives.Doubles;
import com.google.common.primitives.Floats;

import java.security.InvalidParameterException;
import java.util.EnumSet;

/**
 * Provides whatever pose was applied by {@link ApplicationPoseSource#applyPose}. This is used for
 * applications that have an external source for pose information, such as ARCore, through the
 * {@link android.uwb.RangingSession#updatePose} API.
 *
 * This assumes the application has all capabilities (rotation, position, upright)
 */
public class ApplicationPoseSource extends PoseSourceBase {
    private static final String TAG = "AppPoseSource";

    /**
     * Creates a new instance of the ApplicationPoseSource.
     */
    public ApplicationPoseSource() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void start() { }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void stop() { }

    /**
     * Applies an updated pose.
     *
     * @param pose The new pose.
     */
    public void applyPose(Pose pose) {
        publish(pose);
    }

    /**
     * Applies an updated pose from raw vector and quaternion values (vx, vy, vz, qx, qy, qz, qw)
     * or a matrix transform (16 values).
     *
     * @param rawValues The new pose information.
     */
    public void applyPose(double[] rawValues) {
        applyPose(Floats.toArray(Doubles.asList(rawValues)));
    }

    /**
     * Applies an updated pose from raw vector and quaternion values (vx, vy, vz, qx, qy, qz, qw)
     * or a matrix transform (16 values).
     *
     * @param v The new pose information.
     */
    public void applyPose(float[] v) {
        if (v.length == 7) {
            publish(new Pose(new Vector3(v[0], v[1], v[2]),
                    new Quaternion(v[3], v[4], v[5], v[6])));
        } else if (v.length == 16) {
            publish(Pose.fromMatrix(new Matrix(v)));
        } else {
            throw new InvalidParameterException("Pose must be 7 or 16 entries long.");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public EnumSet<Capabilities> getCapabilities() {
        return Capabilities.ALL;
    }
}
