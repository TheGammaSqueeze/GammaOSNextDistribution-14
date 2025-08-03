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

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class BdQualityCalculatorTests {

    /* The following data is sourced from the VMAF library's implementation of
     * a BD rate calculator, which is in turn sourced from JCTVC E137 data:
     *
     * http://phenix.it-sudparis.eu/jct/doc_end_user/documents/5_Geneva/wg11/JCTVC-E137-v1.zip
     *
     * There is a slight difference in values (hence the tolerance) due to the
     * difference in interpolation methods (Netflix's own PCHIP implementation
     * vs the Apache Commons Math's SplineInterpolator used here).
     */
    @Test
    public void calculate_dataFromJCTVCE137_1() {
        RateDistortionCurve baselineCurve =
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(108048.8736, 43.6471))
                        .addPoint(RateDistortionPoint.create(61279.976, 40.3953))
                        .addPoint(RateDistortionPoint.create(33905.6656, 37.247))
                        .addPoint(RateDistortionPoint.create(18883.6928, 34.2911))

                        // This point is synthetically added (generated with PCHIP) since
                        // the Akima Interpolation used by this calculator requires 5 points.
                        .addPoint(RateDistortionPoint.create(78915.635, 41.8147))
                        .build();
        RateDistortionCurve targetCurve =
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(108061.2784, 43.6768))
                        .addPoint(RateDistortionPoint.create(61299.9936, 40.4232))
                        .addPoint(RateDistortionPoint.create(33928.7472, 37.2761))
                        .addPoint(RateDistortionPoint.create(18910.912, 34.3147))

                        // This point is synthetically added (generated with PCHIP) since
                        // the Akima Interpolation used by this calculator requires 5 points.
                        .addPoint(RateDistortionPoint.create(78532.332, 41.8147))
                        .build();
        BdRateCalculator bdRateCalculator = BdRateCalculator.create();

        double bdQuality =
                BdQualityCalculator.create()
                        .calculate(
                                RateDistortionCurvePair.createClusteredPair(
                                        baselineCurve, targetCurve));

        assertThat(bdQuality).isWithin(0.00005).of(-0.00310);
    }

    @Test
    public void calculate_dataFromJCTVCE137_2() {
        RateDistortionCurve baselineCurve =
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(40433.8848, 37.5761))
                        .addPoint(RateDistortionPoint.create(7622.7456, 35.3756))
                        .addPoint(RateDistortionPoint.create(2394.488, 33.8977))
                        .addPoint(RateDistortionPoint.create(1017.6184, 32.0603))

                        // This point is synthetically added (generated with PCHIP) since
                        // the Akima Interpolation used by this calculator requires 5 points.
                        .addPoint(RateDistortionPoint.create(19179.138, 36.5822))
                        .build();
        RateDistortionCurve targetCurve =
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(40370.12, 37.5982))
                        .addPoint(RateDistortionPoint.create(7587.0024, 35.4025))
                        .addPoint(RateDistortionPoint.create(2390.0944, 33.9194))
                        .addPoint(RateDistortionPoint.create(1017.0984, 32.0822))

                        // This point is synthetically added (generated with PCHIP) since
                        // the Akima Interpolation used by this calculator requires 5 points.
                        .addPoint(RateDistortionPoint.create(18727.134, 36.5822))
                        .build();

        double bdQuality =
                BdQualityCalculator.create()
                        .calculate(
                                RateDistortionCurvePair.createClusteredPair(
                                        baselineCurve, targetCurve));

        assertThat(bdQuality).isWithin(0.00005).of(0.028504);
    }

    @Test
    public void calculate_ctsData_1() {
        RateDistortionCurve baselineCurve =
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(3797, 64))
                        .addPoint(RateDistortionPoint.create(4830, 67))
                        .addPoint(RateDistortionPoint.create(6016, 70))
                        .addPoint(RateDistortionPoint.create(8037, 74))
                        .addPoint(RateDistortionPoint.create(10110, 76))
                        .addPoint(RateDistortionPoint.create(12090, 77))
                        .build();
        RateDistortionCurve targetCurve =
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(2522.60, 79.34))
                        .addPoint(RateDistortionPoint.create(4022.00, 87.73))
                        .addPoint(RateDistortionPoint.create(6019.50, 93.18))
                        .addPoint(RateDistortionPoint.create(8039.90, 95.90))
                        .addPoint(RateDistortionPoint.create(10069.00, 97.34))
                        .addPoint(RateDistortionPoint.create(12021.30, 98.22))
                        .build();

        double bdQuality =
                BdQualityCalculator.create()
                        .calculate(
                                RateDistortionCurvePair.createClusteredPair(
                                        baselineCurve, targetCurve));

        // We have no ground-truth data, but we know that the target curve should have higher
        // quality than the baseline (and thus this should be greater than zero).
        assertThat(bdQuality).isGreaterThan(0);
    }
}
