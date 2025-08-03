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

import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;

/** Utilities for performing the calculus involved for BD-RATE and BD-QUALITY calculations. */
public final class BdCalculus {

    private BdCalculus() {}

    /**
     * Calculates the area under the curve for the provided {@link PolynomialSplineFunction} between
     * the min and max values.
     */
    public static double calculateAuc(PolynomialSplineFunction func, double min, double max) {

        // Create the integral functions for each of the segments of the spline.
        PolynomialFunction[] segmentFuncs = func.getPolynomials();
        PolynomialFunction[] integralFuncs = new PolynomialFunction[segmentFuncs.length];
        for (int funcIdx = 0; funcIdx < segmentFuncs.length; funcIdx++) {
            integralFuncs[funcIdx] = integratePolynomial(segmentFuncs[funcIdx]);
        }

        // Calculate the integral for each segment, summing up the results
        // which is the value of the spline's integral.
        double result = 0;
        double[] knots = func.getKnots();
        for (int leftKnotIdx = 0; leftKnotIdx < knots.length - 1; leftKnotIdx++) {
            double leftKnot = knots[leftKnotIdx];
            double rightKnot = knots[leftKnotIdx + 1];

            if (rightKnot < min) {
                continue;
            }

            if (leftKnot > max) {
                break;
            }

            double integrationLeft = Math.max(0, min - leftKnot);
            double integrationRight = Math.min(rightKnot - leftKnot, max - leftKnot);

            PolynomialFunction integralFunc = integralFuncs[leftKnotIdx];
            result += integralFunc.value(integrationRight) - integralFunc.value(integrationLeft);
        }

        return result;
    }

    /**
     * Perform a standard polynomial integration by parts on the provided {@link
     * PolynomialFunction}, returning a new {@link PolynomialFunction} representing the integrated
     * function.
     */
    private static PolynomialFunction integratePolynomial(PolynomialFunction function) {
        double[] newCoeffs = new double[function.getCoefficients().length + 1];
        for (int i = 1; i <= function.getCoefficients().length; i++) {
            newCoeffs[i] = function.getCoefficients()[i - 1] / i;
        }
        newCoeffs[0] = 0;
        return new PolynomialFunction(newCoeffs);
    }
}
