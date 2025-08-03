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

/**
 * Calculator for the Bjontegaard-Delta rate between two rate-distortion curves for an arbitrary
 * metric.
 *
 * <p>Bjontegaard's metric allows to compute the average gain in PSNR or the average percent saving
 * in bitrate between two rate-distortion curves [1]. The code is an implementation of Bjontegaard
 * metric to calculate average bit-rate saving.
 *
 * <p>1. G. Bjontegaard, Calculation of average PSNR differences between RD-curves (VCEG-M33) 2. S.
 * Pateux, J. Jung, An excel add-in for computing Bjontegaard metric and its evolution 3. VCEG-M34.
 * http://wftp3.itu.int/av-arch/video-site/0104_Aus/VCEG-M34.xls
 */
public class BdRateCalculator {
    private static final Logger LOGGER = Logger.getLogger(BdRateCalculator.class.getName());

    private BdRateCalculator() {}

    public static BdRateCalculator create() {
        return new BdRateCalculator();
    }

    /**
     * Calculates the Bjontegaard-Delta (BD) rate for the two provided rate-distortion curves.
     *
     * @return The Bjontegaard-Delta rate value, or Double.NaN if it could not be calculated.
     * @throws IllegalArgumentException if any of the input data is invalid in rate-distortion
     *     context (e.g. bitrate < 0).
     */
    public double calculate(RateDistortionCurvePair curvePair) {
        Preconditions.checkArgument(curvePair.canCalculateBdRate());

        AkimaSplineInterpolator akimaInterpolator = new AkimaSplineInterpolator();

        PolynomialSplineFunction referenceFitCurve =
                akimaInterpolator.interpolate(
                        curvePair.baseline().getDistortionsArray(),
                        curvePair.baseline().getLog10RatesArray());
        PolynomialSplineFunction targetFitCurve =
                akimaInterpolator.interpolate(
                        curvePair.target().getDistortionsArray(),
                        curvePair.target().getLog10RatesArray());

        double integrationRangeMin =
                Math.max(
                        curvePair.baseline().getMinDistortion(),
                        curvePair.target().getMinDistortion());
        double integrationRangeMax =
                Math.min(
                        curvePair.baseline().getMaxDistortion(),
                        curvePair.target().getMaxDistortion());

        double referenceAuc =
                BdCalculus.calculateAuc(
                        referenceFitCurve, integrationRangeMin, integrationRangeMax);
        double targetAuc =
                BdCalculus.calculateAuc(targetFitCurve, integrationRangeMin, integrationRangeMax);

        double bdRateLog = (targetAuc - referenceAuc) / (integrationRangeMax - integrationRangeMin);
        return Math.pow(10, bdRateLog) - 1;
    }
}
