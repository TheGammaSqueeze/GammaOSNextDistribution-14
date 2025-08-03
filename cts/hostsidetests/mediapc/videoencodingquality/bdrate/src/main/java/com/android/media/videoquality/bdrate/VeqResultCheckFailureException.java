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

package com.android.media.videoquality.bdrate;

/**
 * {@link RuntimeException} thrown when the BD-RATE result calculated is greater than the allowed
 * threshold for the test.
 *
 * <p>This indicates that a device has failed the VEQ test.
 */
public class VeqResultCheckFailureException extends RuntimeException {
    private final double mThreshold;
    private final double mBdResult;

    public VeqResultCheckFailureException(String message, double threshold, double bdResult) {
        super(message);
        mThreshold = threshold;
        this.mBdResult = bdResult;
    }

    /** Returns the threshold value defined for the test. */
    public double getThreshold() {
        return mThreshold;
    }

    /** Returns the calculated BD-RATE value. */
    public double getBdResult() {
        return mBdResult;
    }
}
