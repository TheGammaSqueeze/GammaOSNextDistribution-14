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

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

@RunWith(JUnitParamsRunner.class)
public class RateDistortionCurvePairTests {
    @Test
    @Parameters
    public void cluster_correctlyClustersPoints(
            RateDistortionCurve originalCurve, RateDistortionCurve expectedClusteredCurve)
            throws Exception {
        RateDistortionCurve clusteredCurve = RateDistortionCurvePair.cluster(originalCurve);
        assertThat(clusteredCurve).isEqualTo(expectedClusteredCurve);
    }

    public Object[] parametersForCluster_correctlyClustersPoints() {
        return new Object[][] {
            {
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(1269000, 85.99145178011112))
                        .addPoint(RateDistortionPoint.create(1271000, 85.940599017292612))
                        // The points above should be averaged together since they
                        // fall within the threshold. The points after should be
                        // left as is.
                        .addPoint(RateDistortionPoint.create(1280000, 86.239622578028658))
                        .addPoint(RateDistortionPoint.create(1980000, 91.798753645900973))
                        .addPoint(RateDistortionPoint.create(2963000, 96.336865669564261))
                        .addPoint(RateDistortionPoint.create(3953000, 98.916661924718113))
                        .addPoint(RateDistortionPoint.create(4944000, 100.56809641317301))
                        .build(),
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(1270000, 85.966025))
                        .addPoint(RateDistortionPoint.create(1280000, 86.239622578028658))
                        .addPoint(RateDistortionPoint.create(1980000, 91.798753645900973))
                        .addPoint(RateDistortionPoint.create(2963000, 96.336865669564261))
                        .addPoint(RateDistortionPoint.create(3953000, 98.916661924718113))
                        .addPoint(RateDistortionPoint.create(4944000, 100.56809641317301))
                        .build()
            },
            {
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(4219000, 60.287099359895606))
                        .addPoint(RateDistortionPoint.create(4232000, 60.387644277419142))

                        // The points between these two comments are clustered.
                        .addPoint(RateDistortionPoint.create(4240000, 60.44304017191704))
                        .addPoint(RateDistortionPoint.create(4242000, 60.421901710401038))
                        // End of the clustered point.

                        .addPoint(RateDistortionPoint.create(4282000, 61.012684829487483))
                        .addPoint(RateDistortionPoint.create(4957000, 63.627313899593304))
                        .addPoint(RateDistortionPoint.create(5931000, 66.970500015421848))
                        .addPoint(RateDistortionPoint.create(7888000, 72.73460639108508))
                        .addPoint(RateDistortionPoint.create(8889000, 75.131165774588851))
                        .addPoint(RateDistortionPoint.create(9868000, 76.961177180326459))
                        .addPoint(RateDistortionPoint.create(11832000, 80.0394147595267))
                        .addPoint(RateDistortionPoint.create(13807000, 82.538133696703369))
                        .addPoint(RateDistortionPoint.create(17757000, 86.1460086879475))
                        .addPoint(RateDistortionPoint.create(21715000, 88.803835247098448))
                        .build(),
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(4219000, 60.287099359895606))
                        .addPoint(RateDistortionPoint.create(4232000, 60.387644277419142))
                        .addPoint(RateDistortionPoint.create(4241000, 60.43247094115904))
                        .addPoint(RateDistortionPoint.create(4282000, 61.012684829487483))
                        .addPoint(RateDistortionPoint.create(4957000, 63.627313899593304))
                        .addPoint(RateDistortionPoint.create(5931000, 66.970500015421848))
                        .addPoint(RateDistortionPoint.create(7888000, 72.73460639108508))
                        .addPoint(RateDistortionPoint.create(8889000, 75.131165774588851))
                        .addPoint(RateDistortionPoint.create(9868000, 76.961177180326459))
                        .addPoint(RateDistortionPoint.create(11832000, 80.0394147595267))
                        .addPoint(RateDistortionPoint.create(13807000, 82.538133696703369))
                        .addPoint(RateDistortionPoint.create(17757000, 86.1460086879475))
                        .addPoint(RateDistortionPoint.create(21715000, 88.803835247098448))
                        .build()
            }
        };
    }
}
