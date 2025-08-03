/*
 * Copyright 2023 The Android Open Source Project
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

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;

/**
 * Single rate-distortion point representing the quality of a given encoded video at a given bitrate
 * point.
 */
@AutoValue
public abstract class RateDistortionPoint {

    public static RateDistortionPoint create(double rate, double distortion) {
        Preconditions.checkArgument(rate > 0.0, "Bitrate must be a strictly positive value.");
        return new AutoValue_RateDistortionPoint(rate, distortion);
    }

    /** The bitrate (in any scale) of this rate-distortion point. */
    public abstract double rate();

    /**
     * The distortion (in any metric, as long as that metric is monotonically increasing) of this
     * rate-distortion point.
     */
    public abstract double distortion();
}
