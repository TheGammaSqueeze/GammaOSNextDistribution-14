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
import com.google.common.collect.ImmutableSortedSet;

import java.util.Comparator;

/**
 * Collection of {@link RateDistortionPoint}s that represent a rate-distorion curve for a given
 * encoder configuration for calculating Bjontegaard-Delta (BD).
 *
 * <p>{@link RateDistortionPoint}s are maintained in ascending bitrate order, which allows for easy
 * precondition checks (such as monotonicity).
 */
@AutoValue
public abstract class RateDistortionCurve {

    public static RateDistortionCurve.Builder builder() {
        return new AutoValue_RateDistortionCurve.Builder();
    }

    /** Returns the points that describe this curve, ordered by bitrate. */
    public abstract ImmutableSortedSet<RateDistortionPoint> points();

    @AutoValue.Builder
    public abstract static class Builder {
        private final ImmutableSortedSet.Builder<RateDistortionPoint> mPointsBuilder =
                new ImmutableSortedSet.Builder<>(Comparator.comparing(RateDistortionPoint::rate));

        public Builder addPoint(RateDistortionPoint point) {
            // Points are stored in weight order for easier precondition checks later on.
            mPointsBuilder.add(point);
            return this;
        }

        abstract Builder setPoints(ImmutableSortedSet<RateDistortionPoint> points);

        abstract RateDistortionCurve autoBuild();

        public RateDistortionCurve build() {
            setPoints(mPointsBuilder.build());
            return autoBuild();
        }
    }

    public double[] getLog10RatesArray() {
        return points().stream().mapToDouble(p -> Math.log10(p.rate())).toArray();
    }

    public double[] getDistortionsArray() {
        return points().stream().mapToDouble(RateDistortionPoint::distortion).toArray();
    }

    public double getMinDistortion() {
        return points().first().distortion();
    }

    public double getMaxDistortion() {
        return points().last().distortion();
    }

    public double getMinLog10Bitrate() {
        return Math.log10(points().first().rate());
    }

    public double getMaxLog10Bitrate() {
        return Math.log10(points().last().rate());
    }
}
