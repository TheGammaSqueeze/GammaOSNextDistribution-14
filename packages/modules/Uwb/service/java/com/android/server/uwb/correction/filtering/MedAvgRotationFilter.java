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

import static com.android.server.uwb.correction.math.MathHelper.F_PI;
import static com.android.server.uwb.correction.math.MathHelper.normalizeRadians;

import static java.lang.Math.atan2;
import static java.lang.Math.cos;
import static java.lang.Math.sin;

import com.android.server.uwb.correction.math.MathHelper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * A median and average filter that operates identically to {@link MedAvgFilter}, but uses the
 * radians circular number system, wherein numbers refer to points on a circle such that +PI and
 * -PI are the same, and averages refer to the average angle on a circle rather than the average of
 * their linear numerical values.
 */
public class MedAvgRotationFilter extends MedAvgFilter {

    public MedAvgRotationFilter(int windowSize, float cut) {
        super(windowSize, cut);
    }

    /**
     * Creates a naive average of the given samples. Both the value and instant of the samples are
     * averaged. This will probably not produce a desired result if the samples are normalized to
     * roll over at +/-PI rad. Use {@link #sortSamples(Collection)} to shift the roll over to a more
     * desired location.
     *
     * @param samples The list of samples.
     * @return The average of the samples, normalized within +/-PI.
     */
    @Override
    protected Sample averageSamples(Collection<Sample> samples) {
        Sample result = super.averageSamples(samples);
        result.value = MathHelper.normalizeRadians(result.value);
        return result;
    }

    /**
     * Rewrites all sample values based on the selector.
     *
     * @param selector The interface containing the remapping function.
     */
    public void remap(RemapFunction selector) {
        super.remap(v -> MathHelper.normalizeRadians(selector.run(v)));
    }

    /**
     * Creates a new sorted list of angles based on the input list. 2pi is added to some angles to
     * ensure that the sorted result is also in a clockwise order, and the numerical average
     * will equal the directional average.
     * The input angles must be between ±pi, but some output angles will exceed pi.
     */
    protected List<Sample> sortSamples(Collection<Sample> list) {
        List<Sample> result = new ArrayList<>(list);
        int size = result.size();
        if (size < 2) {
            return result;
        }

        // Get the direction of all the positions on the unit circles; the directional average.
        float avgAngle = (float) atan2(
                result.stream().mapToDouble(sample -> sin(sample.value)).sum(),
                result.stream().mapToDouble(sample -> cos(sample.value)).sum()
        );

        // All output values must be between avgAngle ± π. Compute the lowest allowed angle:
        float lowestAngle = normalizeRadians(avgAngle - F_PI);

        // Wrap around any angles that are below our allowed angle by adding 2π.
        int index;
        for (index = 0; index < size; index++) {
            Sample sample = result.get(index);
            if (sample.value < lowestAngle) {
                result.set(index, new Sample(sample.value + 2 * F_PI, sample.timeMs, sample.fom));
            }
        }

        // Sort the result.
        Collections.sort(result);
        return result;
    }
}
