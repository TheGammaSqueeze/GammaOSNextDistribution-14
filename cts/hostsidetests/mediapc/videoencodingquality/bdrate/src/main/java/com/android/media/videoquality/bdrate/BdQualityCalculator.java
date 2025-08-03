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

import com.google.common.base.Preconditions;

import org.apache.commons.math3.analysis.interpolation.AkimaSplineInterpolator;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;

import java.util.logging.Logger;

public class BdQualityCalculator {
    private static final Logger LOGGER = Logger.getLogger(BdQualityCalculator.class.getName());

    private BdQualityCalculator() {}

    public static BdQualityCalculator create() {
        return new BdQualityCalculator();
    }

    /**
     * Calculates the Bjontegaard-Delta (BD) Quality for the two provided rate-distortion curves.
     * The resulting value represents the average change in distortion (quality-metric) at any given
     * bitrate point along the two curves.
     *
     * <p>This requires that the curves are overlapping in terms of bitrate, which can be checked
     * with the {@link RateDistortionCurvePair#canCalculateBdQuality()} method.
     */
    public double calculate(RateDistortionCurvePair curvePair) {
        Preconditions.checkArgument(curvePair.canCalculateBdQuality());

        AkimaSplineInterpolator akimaInterpolator = new AkimaSplineInterpolator();

        PolynomialSplineFunction referenceFitCurve =
                akimaInterpolator.interpolate(
                        curvePair.baseline().getLog10RatesArray(),
                        curvePair.baseline().getDistortionsArray());
        PolynomialSplineFunction targetFitCurve =
                akimaInterpolator.interpolate(
                        curvePair.target().getLog10RatesArray(),
                        curvePair.target().getDistortionsArray());

        double minCommonRate =
                Math.max(
                        curvePair.baseline().getMinLog10Bitrate(),
                        curvePair.baseline().getMinLog10Bitrate());
        double maxCommonRate =
                Math.min(
                        curvePair.baseline().getMaxLog10Bitrate(),
                        curvePair.target().getMaxLog10Bitrate());

        double referenceAuc =
                BdCalculus.calculateAuc(referenceFitCurve, minCommonRate, maxCommonRate);
        double targetAuc = BdCalculus.calculateAuc(targetFitCurve, minCommonRate, maxCommonRate);

        return (targetAuc - referenceAuc) / (maxCommonRate - minCommonRate);
    }
}
