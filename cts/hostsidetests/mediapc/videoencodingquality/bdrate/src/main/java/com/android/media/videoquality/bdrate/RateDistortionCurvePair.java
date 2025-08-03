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

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;

import org.apache.commons.math3.stat.descriptive.moment.Mean;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.logging.Logger;

/** Pair of two {@link RateDistortionCurve}s used for calculating a Bjontegaard-Delta (BD) value. */
@AutoValue
public abstract class RateDistortionCurvePair {
    private static final Logger LOGGER = Logger.getLogger(RateDistortionCurvePair.class.getName());

    private static final Mean MEAN = new Mean();

    public abstract RateDistortionCurve baseline();

    public abstract RateDistortionCurve target();

    /**
     * Creates a new {@link RateDistortionCurvePair} by first clustering the points to eliminate
     * noise in the data, then validating that the remaining points are sufficient for BD
     * calculations.
     */
    public static RateDistortionCurvePair createClusteredPair(
            RateDistortionCurve baseline, RateDistortionCurve target) {
        RateDistortionCurve clusteredBaseline = cluster(baseline);
        RateDistortionCurve clusteredTarget = cluster(target);

        // Check for correct number of points.
        if (clusteredBaseline.points().size() < 5) {
            throw new BdPreconditionFailedException(
                    "The reference curve does not have enough points.", /* isTargetCurve= */ false);
        }
        if (clusteredTarget.points().size() < 5) {
            throw new BdPreconditionFailedException(
                    "The target curve does not have enough points.", /* isTargetCurve= */ true);
        }

        // Check for monotonicity.
        if (!isMonotonicallyIncreasing(clusteredBaseline)) {
            throw new BdPreconditionFailedException(
                    "The reference curve is not monotonically increasing.",
                    /* isTargetCurve= */ false);
        }
        if (!isMonotonicallyIncreasing(clusteredTarget)) {
            throw new BdPreconditionFailedException(
                    "The is not monotonically increasing.", /* isTargetCurve= */ true);
        }

        return new AutoValue_RateDistortionCurvePair(clusteredBaseline, clusteredTarget);
    }

    /** To calculate BD-RATE, the two rate-distortion curves must overlap in terms of distortion. */
    public boolean canCalculateBdRate() {
        return !(baseline().getMaxDistortion() < target().getMinDistortion())
                && !(target().getMaxDistortion() < baseline().getMinDistortion());
    }

    /** To calculate BD-QUALITY, the two rate-distortion curves must overlap in terms of bitrate. */
    public boolean canCalculateBdQuality() {
        return !(baseline().getMaxLog10Bitrate() < target().getMinLog10Bitrate())
                && !(target().getMaxLog10Bitrate() < baseline().getMinLog10Bitrate());
    }

    /**
     * Clusters provided rate-distortion points together to reduce noise when the points are close
     * together in terms of bitrate.
     *
     * <p>"Clusters" are points that have a bitrate that is within 1% of the previous
     * rate-distortion point. Such points are bucketed and then averaged to provide a single point
     * in the same range as the cluster.
     */
    @VisibleForTesting
    static RateDistortionCurve cluster(RateDistortionCurve baseCurve) {
        if (baseCurve.points().size() < 3) {
            return baseCurve;
        }

        RateDistortionCurve.Builder newCurve = RateDistortionCurve.builder();

        LinkedList<ArrayList<RateDistortionPoint>> buckets = new LinkedList<>();

        // Bucket the items, moving through the points pairwise.
        buckets.add(new ArrayList<>());
        buckets.peekLast().add(baseCurve.points().first());

        Iterator<RateDistortionPoint> pointIterator = baseCurve.points().iterator();
        RateDistortionPoint lastPoint = pointIterator.next();
        RateDistortionPoint currentPoint;

        double maxObservedDistortion = lastPoint.distortion();
        while (pointIterator.hasNext()) {
            currentPoint = pointIterator.next();

            // Cluster points that are within 10% (bitrate) of each other that would make the curve
            // non-monotonic.
            if (currentPoint.rate() / lastPoint.rate() > 1.1
                    || currentPoint.distortion() > maxObservedDistortion) {
                buckets.add(new ArrayList<>());
                maxObservedDistortion = currentPoint.distortion();
            }
            buckets.peekLast().add(currentPoint);
            lastPoint = currentPoint;
        }

        for (ArrayList<RateDistortionPoint> bucket : buckets) {
            if (bucket.size() < 2) {
                newCurve.addPoint(bucket.get(0));
            }

            // For a bucket with multiple points, the new point is the average
            // between all other points.
            newCurve.addPoint(
                    RateDistortionPoint.create(
                            MEAN.evaluate(bucket.stream().mapToDouble(p -> p.rate()).toArray()),
                            MEAN.evaluate(
                                    bucket.stream().mapToDouble(p -> p.distortion()).toArray())));
        }

        return newCurve.build();
    }

    /**
     * Returns whether a {@link RateDistortionCurve} is monotonically increasing which is required
     * for the Cubic Spline interpolation performed during BD rate calculation.
     */
    private static boolean isMonotonicallyIncreasing(RateDistortionCurve rateDistortionCurve) {
        Iterator<RateDistortionPoint> pointIterator = rateDistortionCurve.points().iterator();

        RateDistortionPoint lastPoint = pointIterator.next();
        RateDistortionPoint currentPoint;
        while (pointIterator.hasNext()) {
            currentPoint = pointIterator.next();
            if (currentPoint.distortion() <= lastPoint.distortion()) {
                return false;
            }
            lastPoint = currentPoint;
        }

        return true;
    }
}
