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

package com.google.uwb.support.fira;

import android.os.PersistableBundle;

import com.google.common.primitives.Doubles;
import com.google.common.primitives.Floats;

/**
 * Parameters passed to update the device pose information for the UWB distance and azimuth filter.
 *
 * <p>This is passed as a bundle to the service's session API
 * {@link android.uwb.RangingSession#updatePose(PersistableBundle)}</p>
 */
public class FiraPoseUpdateParams extends FiraParams {
    private static final int BUNDLE_VERSION_1 = 1;
    private static final int BUNDLE_VERSION_CURRENT = BUNDLE_VERSION_1;
    private final double[] mPoseInfo;

    private static final String KEY_POSE_VQ = "pose_vq";

    @Override
    protected int getBundleVersion() {
        return BUNDLE_VERSION_CURRENT;
    }

    private FiraPoseUpdateParams(double[] poseInfo) {
        mPoseInfo = poseInfo;
    }

    /** Converts the pose params to a bundle. */
    @Override
    public PersistableBundle toBundle() {
        PersistableBundle bundle = super.toBundle();
        bundle.putDoubleArray(KEY_POSE_VQ, mPoseInfo);

        return bundle;
    }

    /** Converts a PersistableBundle to pose params */
    public static FiraPoseUpdateParams fromBundle(PersistableBundle bundle) {
        switch (getBundleVersion(bundle)) {
            case BUNDLE_VERSION_1:
                return parseBundleVersion1(bundle);

            default:
                throw new IllegalArgumentException("unknown bundle version");
        }
    }

    private static FiraPoseUpdateParams parseBundleVersion1(PersistableBundle bundle) {
        return new Builder().setPose(bundle.getDoubleArray(KEY_POSE_VQ)).build();
    }

    public double[] getPoseInfo() {
        return mPoseInfo;
    }

    /** Builder */
    public static final class Builder {
        public double[] mPoseInfo;

        /**
         * Sets the pose. This must be either vX, vY, vZ, qX, qY, qZ, qW from a vector and
         * quaternion OR a 16-element affine transformation matrix.
         *
         * @param poseInfo The 7 vector and quaternion values, or a 16-element matrix transform
         */
        public FiraPoseUpdateParams.Builder setPose(float[] poseInfo) {
            return setPose(Doubles.toArray(Floats.asList(poseInfo)));
        }

        /**
         * Sets the pose. This must be either vX, vY, vZ, qX, qY, qZ, qW from a vector and
         * quaternion OR a 16-element affine transformation matrix.
         *
         * @param poseInfo The 7 vector and quaternion values, or a 16-element matrix transform
         */
        public FiraPoseUpdateParams.Builder setPose(double[] poseInfo) {
            if (poseInfo.length != 7 && poseInfo.length != 16) {
                throw new IllegalArgumentException("Pose must be 7 elements (vector3 xyz and"
                        + " quaternion xyzw) or 16 elements (4x4 transformation matrix).");
            }

            this.mPoseInfo = poseInfo;

            return this;
        }

        /** Builds the {@link FiraPoseUpdateParams} */
        public FiraPoseUpdateParams build() {
            for (double k :
                    mPoseInfo) {
                if (!Double.isFinite(k)) {
                    throw new IllegalArgumentException("Cannot set pose; non-real numbers.");
                }
            }

            return new FiraPoseUpdateParams(mPoseInfo);
        }
    }
}
