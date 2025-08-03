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
package com.android.server.uwb.correction.math;

import java.util.Random;

/**
 * A generator of random test data. Instantiate once per test.
 */
public final class RandomTestData {

    private static final int RANDOM_SEED = 12345;
    private final Random mRandom = new Random(RANDOM_SEED);

    public float nextFloat() {
        return mRandom.nextFloat();
    }

    public float nextGaussian() {
        return (float) mRandom.nextGaussian();
    }

    public Vector3 nextVector() {
        return new Vector3(nextGaussian(), nextGaussian(), nextGaussian());
    }

    public Quaternion nextRotation() {
        return Quaternion.axisAngle(nextVector(), 360 * nextFloat());
    }

    public Pose nextPose() {
        return new Pose(nextVector(), nextRotation());
    }

    public Matrix nextMatrix() {
        float[] data = new float[16];
        for (int i = 0; i < data.length; ++i) {
            data[i] = nextGaussian();
        }
        return new Matrix(data);
    }
}
