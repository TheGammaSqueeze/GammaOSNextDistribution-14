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


import org.junit.Test;
import org.junit.runner.RunWith;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

@RunWith(JUnitParamsRunner.class)
public class BdRateMainTests {
    private static final BdRateCalculator BD_RATE_CALCULATOR = BdRateCalculator.create();
    private static final BdQualityCalculator BD_QUALITY_CALCULATOR = BdQualityCalculator.create();

    @Test
    @Parameters
    public void checkVeq_passesOnKnownGoodResults(
            RateDistortionCurve baselineCurve, RateDistortionCurve targetCurve) {

        // This function will throw an exception if the check fails, so for good data
        // no exception should be thrown, indicating that this unit test has passed.
        BdRateMain.checkVeq(
                BD_RATE_CALCULATOR,
                BD_QUALITY_CALCULATOR,
                baselineCurve,
                targetCurve,
                /* threshold= */ 0.0);
    }

    public Object[] parametersForCheckVeq_passesOnKnownGoodResults() {
        return new Object[][] {
            {
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(3341.0, 63.0))
                        .addPoint(RateDistortionPoint.create(4028.0, 67.0))
                        .addPoint(RateDistortionPoint.create(6001.0, 75.0))
                        .addPoint(RateDistortionPoint.create(8000.0, 80.0))
                        .addPoint(RateDistortionPoint.create(10043.0, 84.0))
                        .addPoint(RateDistortionPoint.create(12058.0, 86.0))
                        .build(),
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(5444.5, 77.76))
                        .addPoint(RateDistortionPoint.create(5733.5, 78.71))
                        .addPoint(RateDistortionPoint.create(6689.0, 81.33))
                        .addPoint(RateDistortionPoint.create(8003.4, 84.01))
                        .addPoint(RateDistortionPoint.create(9888.3, 86.91))
                        .addPoint(RateDistortionPoint.create(11862.2, 89.37))
                        .build()
            },
            {
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(3124.0, 80.0))
                        .addPoint(RateDistortionPoint.create(4025.0, 85.0))
                        .addPoint(RateDistortionPoint.create(6031.0, 90.0))
                        .addPoint(RateDistortionPoint.create(8048.0, 93.0))
                        .addPoint(RateDistortionPoint.create(10071.0, 95.0))
                        .addPoint(RateDistortionPoint.create(12087.0, 96.0))
                        .build(),
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(2590.2, 79.4))
                        .addPoint(RateDistortionPoint.create(3992.5, 85.52))
                        .addPoint(RateDistortionPoint.create(5937.0, 90.49))
                        .addPoint(RateDistortionPoint.create(7905.3, 93.42))
                        .addPoint(RateDistortionPoint.create(9884.7, 95.15))
                        .addPoint(RateDistortionPoint.create(11857.7, 96.28))
                        .build()
            },
            {
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(2000.0, 87.0))
                        .addPoint(RateDistortionPoint.create(3113.0, 90.0))
                        .addPoint(RateDistortionPoint.create(3665.0, 91.0))
                        .addPoint(RateDistortionPoint.create(4513.0, 93.0))
                        .addPoint(RateDistortionPoint.create(5813.0, 94.0))
                        .addPoint(RateDistortionPoint.create(8007.0, 96.0))
                        .addPoint(RateDistortionPoint.create(11939.0, 97.0))
                        .build(),
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(2002.5, 86.94))
                        .addPoint(RateDistortionPoint.create(3962.9, 92.31))
                        .addPoint(RateDistortionPoint.create(5940.3, 94.78))
                        .addPoint(RateDistortionPoint.create(7928.1, 96.14))
                        .addPoint(RateDistortionPoint.create(9902.9, 97.02))
                        .addPoint(RateDistortionPoint.create(11868.9, 97.61))
                        .build()
            },
            {
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(3150.0, 55.0))
                        .addPoint(RateDistortionPoint.create(3689.0, 59.0))
                        .addPoint(RateDistortionPoint.create(5929.0, 68.0))
                        .addPoint(RateDistortionPoint.create(7943.0, 74.0))
                        .addPoint(RateDistortionPoint.create(9902.0, 78.0))
                        .addPoint(RateDistortionPoint.create(11942.0, 81.0))
                        .build(),
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(3599.8, 59.57))
                        .addPoint(RateDistortionPoint.create(4237.4, 63.73))
                        .addPoint(RateDistortionPoint.create(5950.7, 70.8))
                        .addPoint(RateDistortionPoint.create(7926.9, 75.73))
                        .addPoint(RateDistortionPoint.create(9887.8, 79.36))
                        .addPoint(RateDistortionPoint.create(11855.2, 82.18))
                        .build()
            },
            {
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(3167.0, 73.0))
                        .addPoint(RateDistortionPoint.create(4020.0, 81.0))
                        .addPoint(RateDistortionPoint.create(6029.0, 89.0))
                        .addPoint(RateDistortionPoint.create(8000.0, 92.0))
                        .addPoint(RateDistortionPoint.create(9991.0, 95.0))
                        .addPoint(RateDistortionPoint.create(12005.0, 96.0))
                        .build(),
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(2397.5, 78.12))
                        .addPoint(RateDistortionPoint.create(3965.6, 87.25))
                        .addPoint(RateDistortionPoint.create(5937.5, 93.32))
                        .addPoint(RateDistortionPoint.create(7918.5, 96.15))
                        .addPoint(RateDistortionPoint.create(9892.5, 97.68))
                        .addPoint(RateDistortionPoint.create(11876.9, 98.5))
                        .build()
            },
            {
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(3688.0, 64.0))
                        .addPoint(RateDistortionPoint.create(4626.0, 67.0))
                        .addPoint(RateDistortionPoint.create(5958.0, 71.0))
                        .addPoint(RateDistortionPoint.create(7896.0, 76.0))
                        .addPoint(RateDistortionPoint.create(9897.0, 78.0))
                        .addPoint(RateDistortionPoint.create(11859.0, 80.0))
                        .build(),
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(4713.3, 68.12))
                        .addPoint(RateDistortionPoint.create(5085.4, 70.17))
                        .addPoint(RateDistortionPoint.create(6168.7, 73.08))
                        .addPoint(RateDistortionPoint.create(7903.4, 76.26))
                        .addPoint(RateDistortionPoint.create(9909.3, 78.9))
                        .addPoint(RateDistortionPoint.create(11861.6, 81.0))
                        .build()
            },
            {
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(3130.0, 79.0))
                        .addPoint(RateDistortionPoint.create(3713.0, 83.0))
                        .addPoint(RateDistortionPoint.create(4773.0, 88.0))
                        .addPoint(RateDistortionPoint.create(5876.0, 91.0))
                        .addPoint(RateDistortionPoint.create(7960.0, 95.0))
                        .addPoint(RateDistortionPoint.create(9952.0, 97.0))
                        .addPoint(RateDistortionPoint.create(11925.0, 98.0))
                        .build(),
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(2751.9, 80.13))
                        .addPoint(RateDistortionPoint.create(3977.1, 86.37))
                        .addPoint(RateDistortionPoint.create(5965.3, 92.66))
                        .addPoint(RateDistortionPoint.create(7949.4, 95.91))
                        .addPoint(RateDistortionPoint.create(9934.4, 97.53))
                        .addPoint(RateDistortionPoint.create(11928.7, 98.36))
                        .build()
            },
            {
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(3206.0, 64.0))
                        .addPoint(RateDistortionPoint.create(3986.0, 72.0))
                        .addPoint(RateDistortionPoint.create(6004.0, 86.0))
                        .addPoint(RateDistortionPoint.create(8027.0, 90.0))
                        .addPoint(RateDistortionPoint.create(10042.0, 93.0))
                        .addPoint(RateDistortionPoint.create(12067.0, 95.0))
                        .build(),
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(3378.6, 77.46))
                        .addPoint(RateDistortionPoint.create(3982.4, 81.05))
                        .addPoint(RateDistortionPoint.create(5941.2, 88.8))
                        .addPoint(RateDistortionPoint.create(7921.5, 93.0))
                        .addPoint(RateDistortionPoint.create(9906.0, 95.49))
                        .addPoint(RateDistortionPoint.create(11889.2, 97.01))
                        .build()
            },
            {
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(3143.0, 82.0))
                        .addPoint(RateDistortionPoint.create(4029.0, 89.0))
                        .addPoint(RateDistortionPoint.create(6080.0, 95.0))
                        .addPoint(RateDistortionPoint.create(7965.0, 97.0))
                        .addPoint(RateDistortionPoint.create(9854.0, 99.11))
                        .addPoint(RateDistortionPoint.create(12026.0, 99.58))
                        .build(),
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(2507.4, 84.69))
                        .addPoint(RateDistortionPoint.create(3971.5, 91.66))
                        .addPoint(RateDistortionPoint.create(5954.4, 96.83))
                        .addPoint(RateDistortionPoint.create(7937.6, 98.84))
                        .addPoint(RateDistortionPoint.create(9918.5, 99.53))
                        .addPoint(RateDistortionPoint.create(11903.0, 99.77))
                        .build()
            },
            {
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(3712.0, 61.0))
                        .addPoint(RateDistortionPoint.create(4779.0, 66.0))
                        .addPoint(RateDistortionPoint.create(5965.0, 70.0))
                        .addPoint(RateDistortionPoint.create(7958.0, 75.0))
                        .addPoint(RateDistortionPoint.create(9976.0, 80.0))
                        .addPoint(RateDistortionPoint.create(11934.0, 81.0))
                        .build(),
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(5216.6, 77.13))
                        .addPoint(RateDistortionPoint.create(5563.2, 78.52))
                        .addPoint(RateDistortionPoint.create(6549.7, 81.32))
                        .addPoint(RateDistortionPoint.create(8025.7, 84.33))
                        .addPoint(RateDistortionPoint.create(9990.1, 87.24))
                        .addPoint(RateDistortionPoint.create(11964.5, 89.62))
                        .build()
            },
            {
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(3729.0, 78.0))
                        .addPoint(RateDistortionPoint.create(4782.0, 83.0))
                        .addPoint(RateDistortionPoint.create(5987.0, 87.0))
                        .addPoint(RateDistortionPoint.create(7962.0, 91.0))
                        .addPoint(RateDistortionPoint.create(9955.0, 93.0))
                        .addPoint(RateDistortionPoint.create(11924.0, 95.0))
                        .build(),
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(2973.0, 80.78))
                        .addPoint(RateDistortionPoint.create(4131.6, 85.86))
                        .addPoint(RateDistortionPoint.create(6008.8, 90.57))
                        .addPoint(RateDistortionPoint.create(7998.0, 93.57))
                        .addPoint(RateDistortionPoint.create(10006.9, 95.25))
                        .addPoint(RateDistortionPoint.create(12003.5, 96.36))
                        .build()
            },
            {
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(3685.0, 89.0))
                        .addPoint(RateDistortionPoint.create(4755.0, 91.0))
                        .addPoint(RateDistortionPoint.create(5979.0, 93.0))
                        .addPoint(RateDistortionPoint.create(7952.0, 95.0))
                        .addPoint(RateDistortionPoint.create(9945.0, 96.0))
                        .addPoint(RateDistortionPoint.create(11894.0, 97.0))
                        .build(),
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(2132.8, 88.26))
                        .addPoint(RateDistortionPoint.create(4015.6, 92.72))
                        .addPoint(RateDistortionPoint.create(5998.7, 95.02))
                        .addPoint(RateDistortionPoint.create(7993.7, 96.37))
                        .addPoint(RateDistortionPoint.create(9995.0, 97.19))
                        .addPoint(RateDistortionPoint.create(12012.5, 97.78))
                        .build()
            },
            {
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(3714.0, 57.0))
                        .addPoint(RateDistortionPoint.create(4738.0, 61.0))
                        .addPoint(RateDistortionPoint.create(5963.0, 66.0))
                        .addPoint(RateDistortionPoint.create(7941.0, 73.0))
                        .addPoint(RateDistortionPoint.create(9890.0, 77.0))
                        .addPoint(RateDistortionPoint.create(11822.0, 79.0))
                        .build(),
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(4148.8, 63.13))
                        .addPoint(RateDistortionPoint.create(4505.4, 65.98))
                        .addPoint(RateDistortionPoint.create(6127.5, 72.79))
                        .addPoint(RateDistortionPoint.create(8023.8, 77.53))
                        .addPoint(RateDistortionPoint.create(10006.7, 81.01))
                        .addPoint(RateDistortionPoint.create(11978.0, 83.8))
                        .build()
            },
            {
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(3739.0, 64.0))
                        .addPoint(RateDistortionPoint.create(4830.0, 67.0))
                        .addPoint(RateDistortionPoint.create(6016.0, 70.0))
                        .addPoint(RateDistortionPoint.create(8037.0, 74.0))
                        .addPoint(RateDistortionPoint.create(10110.0, 76.0))
                        .addPoint(RateDistortionPoint.create(12090.0, 77.0))
                        .build(),
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(2522.6, 79.34))
                        .addPoint(RateDistortionPoint.create(4022.0, 87.73))
                        .addPoint(RateDistortionPoint.create(6019.5, 93.18))
                        .addPoint(RateDistortionPoint.create(8039.9, 95.9))
                        .addPoint(RateDistortionPoint.create(10069.0, 97.34))
                        .addPoint(RateDistortionPoint.create(12021.3, 98.22))
                        .build()
            },
            {
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(3707.0, 64.0))
                        .addPoint(RateDistortionPoint.create(4812.0, 65.0))
                        .addPoint(RateDistortionPoint.create(6002.0, 73.0))
                        .addPoint(RateDistortionPoint.create(7910.0, 78.0))
                        .addPoint(RateDistortionPoint.create(9999.0, 80.0))
                        .addPoint(RateDistortionPoint.create(11965.0, 82.0))
                        .build(),
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(5222.7, 72.3))
                        .addPoint(RateDistortionPoint.create(5363.2, 73.12))
                        .addPoint(RateDistortionPoint.create(6020.2, 74.8))
                        .addPoint(RateDistortionPoint.create(8041.0, 78.3))
                        .addPoint(RateDistortionPoint.create(10029.2, 80.88))
                        .addPoint(RateDistortionPoint.create(12029.0, 82.93))
                        .build()
            },
            {
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(3705.0, 69.0))
                        .addPoint(RateDistortionPoint.create(4765.0, 76.0))
                        .addPoint(RateDistortionPoint.create(5965.0, 82.0))
                        .addPoint(RateDistortionPoint.create(7962.0, 87.0))
                        .addPoint(RateDistortionPoint.create(9940.0, 90.0))
                        .addPoint(RateDistortionPoint.create(11901.0, 93.0))
                        .build(),
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(3651.5, 80.49))
                        .addPoint(RateDistortionPoint.create(4139.7, 83.24))
                        .addPoint(RateDistortionPoint.create(6038.6, 89.32))
                        .addPoint(RateDistortionPoint.create(8048.4, 93.13))
                        .addPoint(RateDistortionPoint.create(10049.7, 95.48))
                        .addPoint(RateDistortionPoint.create(12044.5, 96.93))
                        .build()
            },
            {
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(3728.0, 84.0))
                        .addPoint(RateDistortionPoint.create(4812.0, 89.0))
                        .addPoint(RateDistortionPoint.create(6011.0, 93.0))
                        .addPoint(RateDistortionPoint.create(7985.0, 96.0))
                        .addPoint(RateDistortionPoint.create(9984.0, 98.0))
                        .addPoint(RateDistortionPoint.create(11999.0, 99.0))
                        .build(),
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(2786.6, 87.07))
                        .addPoint(RateDistortionPoint.create(4018.6, 92.43))
                        .addPoint(RateDistortionPoint.create(6012.8, 97.05))
                        .addPoint(RateDistortionPoint.create(8016.2, 98.85))
                        .addPoint(RateDistortionPoint.create(10008.7, 99.52))
                        .addPoint(RateDistortionPoint.create(12001.4, 99.77))
                        .build()
            },
            {
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(4242.0, 64.0))
                        .addPoint(RateDistortionPoint.create(6009.0, 72.0))
                        .addPoint(RateDistortionPoint.create(7996.0, 79.0))
                        .addPoint(RateDistortionPoint.create(9990.0, 83.0))
                        .addPoint(RateDistortionPoint.create(12093.0, 87.0))
                        .build(),
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(4799.1, 73.11))
                        .addPoint(RateDistortionPoint.create(4983.9, 73.8))
                        .addPoint(RateDistortionPoint.create(5940.6, 76.6))
                        .addPoint(RateDistortionPoint.create(7899.8, 81.81))
                        .addPoint(RateDistortionPoint.create(9885.8, 85.78))
                        .addPoint(RateDistortionPoint.create(11864.0, 88.58))
                        .build()
            },
            {
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(4072.0, 76.0))
                        .addPoint(RateDistortionPoint.create(6012.0, 87.0))
                        .addPoint(RateDistortionPoint.create(8038.0, 91.0))
                        .addPoint(RateDistortionPoint.create(10010.0, 94.0))
                        .addPoint(RateDistortionPoint.create(12049.0, 95.0))
                        .build(),
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(3452.9, 78.13))
                        .addPoint(RateDistortionPoint.create(4099.9, 81.48))
                        .addPoint(RateDistortionPoint.create(5945.5, 87.77))
                        .addPoint(RateDistortionPoint.create(7902.6, 91.77))
                        .addPoint(RateDistortionPoint.create(9877.5, 94.19))
                        .addPoint(RateDistortionPoint.create(11871.8, 95.64))
                        .build()
            },
            {
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(2406.0, 80.0))
                        .addPoint(RateDistortionPoint.create(4032.0, 88.0))
                        .addPoint(RateDistortionPoint.create(5797.0, 92.0))
                        .addPoint(RateDistortionPoint.create(6914.0, 93.0))
                        .addPoint(RateDistortionPoint.create(7315.0, 93.5))
                        .addPoint(RateDistortionPoint.create(7474.0, 94.0))
                        .build(),
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(2112.7, 81.36))
                        .addPoint(RateDistortionPoint.create(3947.3, 88.82))
                        .addPoint(RateDistortionPoint.create(5925.6, 92.62))
                        .addPoint(RateDistortionPoint.create(7909.5, 94.59))
                        .addPoint(RateDistortionPoint.create(9883.4, 95.85))
                        .addPoint(RateDistortionPoint.create(11852.3, 96.7))
                        .build()
            },
            {
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(3864.0, 55.0))
                        .addPoint(RateDistortionPoint.create(5933.0, 67.0))
                        .addPoint(RateDistortionPoint.create(7893.0, 72.0))
                        .addPoint(RateDistortionPoint.create(9988.0, 76.0))
                        .addPoint(RateDistortionPoint.create(12028.0, 79.0))
                        .build(),
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(4249.6, 60.26))
                        .addPoint(RateDistortionPoint.create(4270.1, 60.76))
                        .addPoint(RateDistortionPoint.create(5928.1, 66.98))
                        .addPoint(RateDistortionPoint.create(7896.4, 72.66))
                        .addPoint(RateDistortionPoint.create(9886.2, 76.77))
                        .addPoint(RateDistortionPoint.create(11839.9, 79.97))
                        .build()
            },
            {
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(4370.0, 75.0))
                        .addPoint(RateDistortionPoint.create(6051.0, 82.0))
                        .addPoint(RateDistortionPoint.create(8026.0, 90.0))
                        .addPoint(RateDistortionPoint.create(9999.0, 93.0))
                        .addPoint(RateDistortionPoint.create(12001.0, 95.0))
                        .build(),
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(3347.8, 77.05))
                        .addPoint(RateDistortionPoint.create(4026.2, 80.68))
                        .addPoint(RateDistortionPoint.create(5949.2, 88.64))
                        .addPoint(RateDistortionPoint.create(7940.9, 93.18))
                        .addPoint(RateDistortionPoint.create(9907.9, 95.78))
                        .addPoint(RateDistortionPoint.create(11887.0, 97.33))
                        .build()
            },
            {
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(3956.0, 61.0))
                        .addPoint(RateDistortionPoint.create(4561.0, 63.0))
                        .addPoint(RateDistortionPoint.create(5938.0, 68.0))
                        .addPoint(RateDistortionPoint.create(7904.0, 72.0))
                        .addPoint(RateDistortionPoint.create(10028.0, 76.0))
                        .addPoint(RateDistortionPoint.create(12023.0, 78.0))
                        .build(),
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(4564.0, 63.84))
                        .addPoint(RateDistortionPoint.create(4657.4, 64.61))
                        .addPoint(RateDistortionPoint.create(5940.6, 68.02))
                        .addPoint(RateDistortionPoint.create(7907.0, 72.68))
                        .addPoint(RateDistortionPoint.create(9880.4, 76.04))
                        .addPoint(RateDistortionPoint.create(11841.0, 78.61))
                        .build()
            },
            {
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(3942.0, 76.0))
                        .addPoint(RateDistortionPoint.create(6008.0, 89.0))
                        .addPoint(RateDistortionPoint.create(8047.0, 94.0))
                        .addPoint(RateDistortionPoint.create(10117.0, 96.0))
                        .addPoint(RateDistortionPoint.create(11944.0, 98.0))
                        .build(),
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(3364.3, 78.53))
                        .addPoint(RateDistortionPoint.create(3998.1, 82.29))
                        .addPoint(RateDistortionPoint.create(5964.2, 90.12))
                        .addPoint(RateDistortionPoint.create(7948.5, 94.6))
                        .addPoint(RateDistortionPoint.create(9932.1, 96.95))
                        .addPoint(RateDistortionPoint.create(11907.0, 98.14))
                        .build()
            },
            {
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(4360.0, 67.0))
                        .addPoint(RateDistortionPoint.create(5996.0, 72.0))
                        .addPoint(RateDistortionPoint.create(8006.0, 86.0))
                        .addPoint(RateDistortionPoint.create(10019.0, 90.0))
                        .addPoint(RateDistortionPoint.create(12027.0, 93.0))
                        .build(),
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(4409.6, 76.81))
                        .addPoint(RateDistortionPoint.create(4464.3, 77.08))
                        .addPoint(RateDistortionPoint.create(5947.6, 82.9))
                        .addPoint(RateDistortionPoint.create(7919.1, 89.24))
                        .addPoint(RateDistortionPoint.create(9909.0, 92.89))
                        .addPoint(RateDistortionPoint.create(11905.7, 95.19))
                        .build()
            },
            {
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(4049.0, 75.0))
                        .addPoint(RateDistortionPoint.create(6026.0, 91.0))
                        .addPoint(RateDistortionPoint.create(6888.0, 94.0))
                        .addPoint(RateDistortionPoint.create(8019.0, 95.0))
                        .addPoint(RateDistortionPoint.create(10020.0, 98.0))
                        .addPoint(RateDistortionPoint.create(12005.0, 99.0))
                        .build(),
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(2981.3, 79.52))
                        .addPoint(RateDistortionPoint.create(3975.0, 85.13))
                        .addPoint(RateDistortionPoint.create(5948.8, 93.39))
                        .addPoint(RateDistortionPoint.create(7944.2, 97.33))
                        .addPoint(RateDistortionPoint.create(9912.2, 98.93))
                        .addPoint(RateDistortionPoint.create(11895.2, 99.56))
                        .build()
            },
            {
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(4780.0, 62.0))
                        .addPoint(RateDistortionPoint.create(6879.0, 71.0))
                        .addPoint(RateDistortionPoint.create(7964.0, 74.0))
                        .addPoint(RateDistortionPoint.create(9959.0, 79.0))
                        .addPoint(RateDistortionPoint.create(11968.0, 82.0))
                        .build(),
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(5475.6, 73.04))
                        .addPoint(RateDistortionPoint.create(5588.6, 73.26))
                        .addPoint(RateDistortionPoint.create(6298.4, 75.83))
                        .addPoint(RateDistortionPoint.create(7956.4, 80.46))
                        .addPoint(RateDistortionPoint.create(9945.2, 84.35))
                        .addPoint(RateDistortionPoint.create(11924.4, 87.24))
                        .build()
            },
            {
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(4705.0, 77.0))
                        .addPoint(RateDistortionPoint.create(4779.0, 78.0))
                        .addPoint(RateDistortionPoint.create(6858.0, 86.0))
                        .addPoint(RateDistortionPoint.create(7979.0, 89.0))
                        .addPoint(RateDistortionPoint.create(9928.0, 92.0))
                        .addPoint(RateDistortionPoint.create(11941.0, 94.0))
                        .build(),
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(4022.5, 77.7))
                        .addPoint(RateDistortionPoint.create(4315.0, 80.11))
                        .addPoint(RateDistortionPoint.create(6000.8, 86.89))
                        .addPoint(RateDistortionPoint.create(7950.5, 91.04))
                        .addPoint(RateDistortionPoint.create(9962.1, 93.7))
                        .addPoint(RateDistortionPoint.create(11918.9, 95.3))
                        .build()
            },
            {
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(4669.0, 88.0))
                        .addPoint(RateDistortionPoint.create(6793.0, 92.0))
                        .addPoint(RateDistortionPoint.create(7929.0, 93.93))
                        .addPoint(RateDistortionPoint.create(9896.0, 95.0))
                        .addPoint(RateDistortionPoint.create(11925.0, 96.0))
                        .build(),
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(2463.5, 82.36))
                        .addPoint(RateDistortionPoint.create(3986.3, 89.17))
                        .addPoint(RateDistortionPoint.create(5969.9, 92.72))
                        .addPoint(RateDistortionPoint.create(7950.6, 94.59))
                        .addPoint(RateDistortionPoint.create(9935.6, 95.79))
                        .addPoint(RateDistortionPoint.create(11918.6, 96.64))
                        .build()
            },
            {
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(5076.0, 60.0))
                        .addPoint(RateDistortionPoint.create(6853.0, 70.0))
                        .addPoint(RateDistortionPoint.create(7945.0, 73.0))
                        .addPoint(RateDistortionPoint.create(9892.0, 77.0))
                        .addPoint(RateDistortionPoint.create(11862.0, 80.0))
                        .build(),
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(5025.1, 61.54))
                        .addPoint(RateDistortionPoint.create(5083.6, 60.78))
                        .addPoint(RateDistortionPoint.create(5999.4, 68.98))
                        .addPoint(RateDistortionPoint.create(7951.5, 75.18))
                        .addPoint(RateDistortionPoint.create(9950.6, 79.16))
                        .addPoint(RateDistortionPoint.create(11899.2, 82.11))
                        .build()
            },
            {
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(4695.0, 74.0))
                        .addPoint(RateDistortionPoint.create(4768.0, 75.0))
                        .addPoint(RateDistortionPoint.create(8015.0, 88.0))
                        .addPoint(RateDistortionPoint.create(9985.0, 92.0))
                        .addPoint(RateDistortionPoint.create(11919.0, 94.0))
                        .build(),
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(3440.6, 74.08))
                        .addPoint(RateDistortionPoint.create(4055.3, 78.78))
                        .addPoint(RateDistortionPoint.create(5990.9, 87.23))
                        .addPoint(RateDistortionPoint.create(7958.2, 92.06))
                        .addPoint(RateDistortionPoint.create(9949.3, 94.76))
                        .addPoint(RateDistortionPoint.create(11939.8, 96.46))
                        .build()
            },
            {
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(6710.0, 67.0))
                        .addPoint(RateDistortionPoint.create(6911.0, 72.0))
                        .addPoint(RateDistortionPoint.create(8000.0, 74.0))
                        .addPoint(RateDistortionPoint.create(9973.0, 77.0))
                        .addPoint(RateDistortionPoint.create(11972.0, 79.0))
                        .build(),
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(6396.5, 68.74))
                        .addPoint(RateDistortionPoint.create(6718.2, 67.46))
                        .addPoint(RateDistortionPoint.create(6743.5, 71.93))
                        .addPoint(RateDistortionPoint.create(8033.0, 76.17))
                        .addPoint(RateDistortionPoint.create(9966.0, 79.54))
                        .addPoint(RateDistortionPoint.create(11926.1, 81.9))
                        .build()
            },
            {
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(4709.0, 78.0))
                        .addPoint(RateDistortionPoint.create(4779.0, 79.0))
                        .addPoint(RateDistortionPoint.create(6830.0, 88.0))
                        .addPoint(RateDistortionPoint.create(7939.0, 91.0))
                        .addPoint(RateDistortionPoint.create(9927.0, 94.0))
                        .addPoint(RateDistortionPoint.create(11835.0, 96.0))
                        .build(),
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(3979.8, 78.6))
                        .addPoint(RateDistortionPoint.create(4173.6, 80.72))
                        .addPoint(RateDistortionPoint.create(6007.5, 89.77))
                        .addPoint(RateDistortionPoint.create(8005.1, 94.59))
                        .addPoint(RateDistortionPoint.create(10010.1, 96.91))
                        .addPoint(RateDistortionPoint.create(12031.4, 98.13))
                        .build()
            },
            {
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(4685.0, 62.0))
                        .addPoint(RateDistortionPoint.create(4766.0, 63.0))
                        .addPoint(RateDistortionPoint.create(6843.0, 77.0))
                        .addPoint(RateDistortionPoint.create(7939.0, 81.0))
                        .addPoint(RateDistortionPoint.create(9922.0, 87.0))
                        .addPoint(RateDistortionPoint.create(11904.0, 90.0))
                        .build(),
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(4701.5, 75.76))
                        .addPoint(RateDistortionPoint.create(4749.5, 76.33))
                        .addPoint(RateDistortionPoint.create(6011.4, 81.57))
                        .addPoint(RateDistortionPoint.create(7998.0, 87.84))
                        .addPoint(RateDistortionPoint.create(10012.1, 91.53))
                        .addPoint(RateDistortionPoint.create(12001.3, 93.92))
                        .build()
            },
            {
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(4802.0, 82.0))
                        .addPoint(RateDistortionPoint.create(6899.0, 91.0))
                        .addPoint(RateDistortionPoint.create(8003.0, 94.0))
                        .addPoint(RateDistortionPoint.create(9984.0, 97.0))
                        .addPoint(RateDistortionPoint.create(11992.0, 98.0))
                        .build(),
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(3251.8, 78.72))
                        .addPoint(RateDistortionPoint.create(3995.7, 83.92))
                        .addPoint(RateDistortionPoint.create(5976.3, 92.71))
                        .addPoint(RateDistortionPoint.create(7972.7, 96.8))
                        .addPoint(RateDistortionPoint.create(9980.9, 98.65))
                        .addPoint(RateDistortionPoint.create(11956.8, 99.33))
                        .build()
            },
            {
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(3341.0, 63.0))
                        .addPoint(RateDistortionPoint.create(4028.0, 67.0))
                        .addPoint(RateDistortionPoint.create(6001.0, 75.0))
                        .addPoint(RateDistortionPoint.create(8000.0, 80.0))
                        .addPoint(RateDistortionPoint.create(10043.0, 84.0))
                        .addPoint(RateDistortionPoint.create(12058.0, 86.0))
                        .build(),
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(5444.5, 77.76))
                        .addPoint(RateDistortionPoint.create(5733.5, 78.71))
                        .addPoint(RateDistortionPoint.create(6689.0, 81.33))
                        .addPoint(RateDistortionPoint.create(8003.4, 84.01))
                        .addPoint(RateDistortionPoint.create(9888.3, 86.91))
                        .addPoint(RateDistortionPoint.create(11862.2, 89.37))
                        .build()
            },
            {
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(3124.0, 80.0))
                        .addPoint(RateDistortionPoint.create(4025.0, 85.0))
                        .addPoint(RateDistortionPoint.create(6031.0, 90.0))
                        .addPoint(RateDistortionPoint.create(8048.0, 93.0))
                        .addPoint(RateDistortionPoint.create(10071.0, 95.0))
                        .addPoint(RateDistortionPoint.create(12087.0, 96.0))
                        .build(),
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(2590.2, 79.4))
                        .addPoint(RateDistortionPoint.create(3992.5, 85.52))
                        .addPoint(RateDistortionPoint.create(5937.0, 90.49))
                        .addPoint(RateDistortionPoint.create(7905.3, 93.42))
                        .addPoint(RateDistortionPoint.create(9884.7, 95.15))
                        .addPoint(RateDistortionPoint.create(11857.7, 96.28))
                        .build()
            },
            {
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(2000.0, 87.0))
                        .addPoint(RateDistortionPoint.create(3113.0, 90.0))
                        .addPoint(RateDistortionPoint.create(3665.0, 91.0))
                        .addPoint(RateDistortionPoint.create(4513.0, 93.0))
                        .addPoint(RateDistortionPoint.create(5813.0, 94.0))
                        .addPoint(RateDistortionPoint.create(8007.0, 96.0))
                        .addPoint(RateDistortionPoint.create(11939.0, 97.0))
                        .build(),
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(2002.5, 86.94))
                        .addPoint(RateDistortionPoint.create(3962.9, 92.31))
                        .addPoint(RateDistortionPoint.create(5940.3, 94.78))
                        .addPoint(RateDistortionPoint.create(7928.1, 96.14))
                        .addPoint(RateDistortionPoint.create(9902.9, 97.02))
                        .addPoint(RateDistortionPoint.create(11868.9, 97.61))
                        .build()
            },
            {
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(3150.0, 55.0))
                        .addPoint(RateDistortionPoint.create(3689.0, 59.0))
                        .addPoint(RateDistortionPoint.create(5929.0, 68.0))
                        .addPoint(RateDistortionPoint.create(7943.0, 74.0))
                        .addPoint(RateDistortionPoint.create(9902.0, 78.0))
                        .addPoint(RateDistortionPoint.create(11942.0, 81.0))
                        .build(),
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(3599.8, 59.57))
                        .addPoint(RateDistortionPoint.create(4237.4, 63.73))
                        .addPoint(RateDistortionPoint.create(5950.7, 70.8))
                        .addPoint(RateDistortionPoint.create(7926.9, 75.73))
                        .addPoint(RateDistortionPoint.create(9887.8, 79.36))
                        .addPoint(RateDistortionPoint.create(11855.2, 82.18))
                        .build()
            },
            {
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(3167.0, 73.0))
                        .addPoint(RateDistortionPoint.create(4020.0, 81.0))
                        .addPoint(RateDistortionPoint.create(6029.0, 89.0))
                        .addPoint(RateDistortionPoint.create(8000.0, 92.0))
                        .addPoint(RateDistortionPoint.create(9991.0, 95.0))
                        .addPoint(RateDistortionPoint.create(12005.0, 96.0))
                        .build(),
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(2397.5, 78.12))
                        .addPoint(RateDistortionPoint.create(3965.6, 87.25))
                        .addPoint(RateDistortionPoint.create(5937.5, 93.32))
                        .addPoint(RateDistortionPoint.create(7918.5, 96.15))
                        .addPoint(RateDistortionPoint.create(9892.5, 97.68))
                        .addPoint(RateDistortionPoint.create(11876.9, 98.5))
                        .build()
            },
            {
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(3688.0, 64.0))
                        .addPoint(RateDistortionPoint.create(4626.0, 67.0))
                        .addPoint(RateDistortionPoint.create(5958.0, 71.0))
                        .addPoint(RateDistortionPoint.create(7896.0, 76.0))
                        .addPoint(RateDistortionPoint.create(9897.0, 78.0))
                        .addPoint(RateDistortionPoint.create(11859.0, 80.0))
                        .build(),
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(4713.3, 68.12))
                        .addPoint(RateDistortionPoint.create(5085.4, 70.17))
                        .addPoint(RateDistortionPoint.create(6168.7, 73.08))
                        .addPoint(RateDistortionPoint.create(7903.4, 76.26))
                        .addPoint(RateDistortionPoint.create(9909.3, 78.9))
                        .addPoint(RateDistortionPoint.create(11861.6, 81.0))
                        .build()
            },
            {
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(3130.0, 79.0))
                        .addPoint(RateDistortionPoint.create(3713.0, 83.0))
                        .addPoint(RateDistortionPoint.create(4773.0, 88.0))
                        .addPoint(RateDistortionPoint.create(5876.0, 91.0))
                        .addPoint(RateDistortionPoint.create(7960.0, 95.0))
                        .addPoint(RateDistortionPoint.create(9952.0, 97.0))
                        .addPoint(RateDistortionPoint.create(11925.0, 98.0))
                        .build(),
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(2751.9, 80.13))
                        .addPoint(RateDistortionPoint.create(3977.1, 86.37))
                        .addPoint(RateDistortionPoint.create(5965.3, 92.66))
                        .addPoint(RateDistortionPoint.create(7949.4, 95.91))
                        .addPoint(RateDistortionPoint.create(9934.4, 97.53))
                        .addPoint(RateDistortionPoint.create(11928.7, 98.36))
                        .build()
            },
            {
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(3206.0, 64.0))
                        .addPoint(RateDistortionPoint.create(3986.0, 72.0))
                        .addPoint(RateDistortionPoint.create(6004.0, 86.0))
                        .addPoint(RateDistortionPoint.create(8027.0, 90.0))
                        .addPoint(RateDistortionPoint.create(10042.0, 93.0))
                        .addPoint(RateDistortionPoint.create(12067.0, 95.0))
                        .build(),
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(3378.6, 77.46))
                        .addPoint(RateDistortionPoint.create(3982.4, 81.05))
                        .addPoint(RateDistortionPoint.create(5941.2, 88.8))
                        .addPoint(RateDistortionPoint.create(7921.5, 93.0))
                        .addPoint(RateDistortionPoint.create(9906.0, 95.49))
                        .addPoint(RateDistortionPoint.create(11889.2, 97.01))
                        .build()
            },
            {
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(3143.0, 82.0))
                        .addPoint(RateDistortionPoint.create(4029.0, 89.0))
                        .addPoint(RateDistortionPoint.create(6080.0, 95.0))
                        .addPoint(RateDistortionPoint.create(7965.0, 97.0))
                        .addPoint(RateDistortionPoint.create(9854.0, 99.11))
                        .addPoint(RateDistortionPoint.create(12026.0, 99.58))
                        .build(),
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(2507.4, 84.69))
                        .addPoint(RateDistortionPoint.create(3971.5, 91.66))
                        .addPoint(RateDistortionPoint.create(5954.4, 96.83))
                        .addPoint(RateDistortionPoint.create(7937.6, 98.84))
                        .addPoint(RateDistortionPoint.create(9918.5, 99.53))
                        .addPoint(RateDistortionPoint.create(11903.0, 99.77))
                        .build()
            },
            {
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(3712.0, 61.0))
                        .addPoint(RateDistortionPoint.create(4779.0, 66.0))
                        .addPoint(RateDistortionPoint.create(5965.0, 70.0))
                        .addPoint(RateDistortionPoint.create(7958.0, 75.0))
                        .addPoint(RateDistortionPoint.create(9976.0, 80.0))
                        .addPoint(RateDistortionPoint.create(11934.0, 81.0))
                        .build(),
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(5216.6, 77.13))
                        .addPoint(RateDistortionPoint.create(5563.2, 78.52))
                        .addPoint(RateDistortionPoint.create(6549.7, 81.32))
                        .addPoint(RateDistortionPoint.create(8025.7, 84.33))
                        .addPoint(RateDistortionPoint.create(9990.1, 87.24))
                        .addPoint(RateDistortionPoint.create(11964.5, 89.62))
                        .build()
            },
            {
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(3729.0, 78.0))
                        .addPoint(RateDistortionPoint.create(4782.0, 83.0))
                        .addPoint(RateDistortionPoint.create(5987.0, 87.0))
                        .addPoint(RateDistortionPoint.create(7962.0, 91.0))
                        .addPoint(RateDistortionPoint.create(9955.0, 93.0))
                        .addPoint(RateDistortionPoint.create(11924.0, 95.0))
                        .build(),
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(2973.0, 80.78))
                        .addPoint(RateDistortionPoint.create(4131.6, 85.86))
                        .addPoint(RateDistortionPoint.create(6008.8, 90.57))
                        .addPoint(RateDistortionPoint.create(7998.0, 93.57))
                        .addPoint(RateDistortionPoint.create(10006.9, 95.25))
                        .addPoint(RateDistortionPoint.create(12003.5, 96.36))
                        .build()
            },
            {
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(3685.0, 89.0))
                        .addPoint(RateDistortionPoint.create(4755.0, 91.0))
                        .addPoint(RateDistortionPoint.create(5979.0, 93.0))
                        .addPoint(RateDistortionPoint.create(7952.0, 95.0))
                        .addPoint(RateDistortionPoint.create(9945.0, 96.0))
                        .addPoint(RateDistortionPoint.create(11894.0, 97.0))
                        .build(),
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(2132.8, 88.26))
                        .addPoint(RateDistortionPoint.create(4015.6, 92.72))
                        .addPoint(RateDistortionPoint.create(5998.7, 95.02))
                        .addPoint(RateDistortionPoint.create(7993.7, 96.37))
                        .addPoint(RateDistortionPoint.create(9995.0, 97.19))
                        .addPoint(RateDistortionPoint.create(12012.5, 97.78))
                        .build()
            },
            {
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(3714.0, 57.0))
                        .addPoint(RateDistortionPoint.create(4738.0, 61.0))
                        .addPoint(RateDistortionPoint.create(5963.0, 66.0))
                        .addPoint(RateDistortionPoint.create(7941.0, 73.0))
                        .addPoint(RateDistortionPoint.create(9890.0, 77.0))
                        .addPoint(RateDistortionPoint.create(11822.0, 79.0))
                        .build(),
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(4148.8, 63.13))
                        .addPoint(RateDistortionPoint.create(4505.4, 65.98))
                        .addPoint(RateDistortionPoint.create(6127.5, 72.79))
                        .addPoint(RateDistortionPoint.create(8023.8, 77.53))
                        .addPoint(RateDistortionPoint.create(10006.7, 81.01))
                        .addPoint(RateDistortionPoint.create(11978.0, 83.8))
                        .build()
            },
            {
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(3739.0, 64.0))
                        .addPoint(RateDistortionPoint.create(4830.0, 67.0))
                        .addPoint(RateDistortionPoint.create(6016.0, 70.0))
                        .addPoint(RateDistortionPoint.create(8037.0, 74.0))
                        .addPoint(RateDistortionPoint.create(10110.0, 76.0))
                        .addPoint(RateDistortionPoint.create(12090.0, 77.0))
                        .build(),
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(2522.6, 79.34))
                        .addPoint(RateDistortionPoint.create(4022.0, 87.73))
                        .addPoint(RateDistortionPoint.create(6019.5, 93.18))
                        .addPoint(RateDistortionPoint.create(8039.9, 95.9))
                        .addPoint(RateDistortionPoint.create(10069.0, 97.34))
                        .addPoint(RateDistortionPoint.create(12021.3, 98.22))
                        .build()
            },
            {
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(3707.0, 64.0))
                        .addPoint(RateDistortionPoint.create(4812.0, 65.0))
                        .addPoint(RateDistortionPoint.create(6002.0, 73.0))
                        .addPoint(RateDistortionPoint.create(7910.0, 78.0))
                        .addPoint(RateDistortionPoint.create(9999.0, 80.0))
                        .addPoint(RateDistortionPoint.create(11965.0, 82.0))
                        .build(),
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(5222.7, 72.3))
                        .addPoint(RateDistortionPoint.create(5363.2, 73.12))
                        .addPoint(RateDistortionPoint.create(6020.2, 74.8))
                        .addPoint(RateDistortionPoint.create(8041.0, 78.3))
                        .addPoint(RateDistortionPoint.create(10029.2, 80.88))
                        .addPoint(RateDistortionPoint.create(12029.0, 82.93))
                        .build()
            },
            {
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(3705.0, 69.0))
                        .addPoint(RateDistortionPoint.create(4765.0, 76.0))
                        .addPoint(RateDistortionPoint.create(5965.0, 82.0))
                        .addPoint(RateDistortionPoint.create(7962.0, 87.0))
                        .addPoint(RateDistortionPoint.create(9940.0, 90.0))
                        .addPoint(RateDistortionPoint.create(11901.0, 93.0))
                        .build(),
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(3651.5, 80.49))
                        .addPoint(RateDistortionPoint.create(4139.7, 83.24))
                        .addPoint(RateDistortionPoint.create(6038.6, 89.32))
                        .addPoint(RateDistortionPoint.create(8048.4, 93.13))
                        .addPoint(RateDistortionPoint.create(10049.7, 95.48))
                        .addPoint(RateDistortionPoint.create(12044.5, 96.93))
                        .build()
            },
            {
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(3728.0, 84.0))
                        .addPoint(RateDistortionPoint.create(4812.0, 89.0))
                        .addPoint(RateDistortionPoint.create(6011.0, 93.0))
                        .addPoint(RateDistortionPoint.create(7985.0, 96.0))
                        .addPoint(RateDistortionPoint.create(9984.0, 98.0))
                        .addPoint(RateDistortionPoint.create(11999.0, 99.0))
                        .build(),
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(2786.6, 87.07))
                        .addPoint(RateDistortionPoint.create(4018.6, 92.43))
                        .addPoint(RateDistortionPoint.create(6012.8, 97.05))
                        .addPoint(RateDistortionPoint.create(8016.2, 98.85))
                        .addPoint(RateDistortionPoint.create(10008.7, 99.52))
                        .addPoint(RateDistortionPoint.create(12001.4, 99.77))
                        .build()
            },
            {
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(4242.0, 64.0))
                        .addPoint(RateDistortionPoint.create(6009.0, 72.0))
                        .addPoint(RateDistortionPoint.create(7996.0, 79.0))
                        .addPoint(RateDistortionPoint.create(9990.0, 83.0))
                        .addPoint(RateDistortionPoint.create(12093.0, 87.0))
                        .build(),
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(4799.1, 73.11))
                        .addPoint(RateDistortionPoint.create(4983.9, 73.8))
                        .addPoint(RateDistortionPoint.create(5940.6, 76.6))
                        .addPoint(RateDistortionPoint.create(7899.8, 81.81))
                        .addPoint(RateDistortionPoint.create(9885.8, 85.78))
                        .addPoint(RateDistortionPoint.create(11864.0, 88.58))
                        .build()
            },
            {
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(4072.0, 76.0))
                        .addPoint(RateDistortionPoint.create(6012.0, 87.0))
                        .addPoint(RateDistortionPoint.create(8038.0, 91.0))
                        .addPoint(RateDistortionPoint.create(10010.0, 94.0))
                        .addPoint(RateDistortionPoint.create(12049.0, 95.0))
                        .build(),
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(3452.9, 78.13))
                        .addPoint(RateDistortionPoint.create(4099.9, 81.48))
                        .addPoint(RateDistortionPoint.create(5945.5, 87.77))
                        .addPoint(RateDistortionPoint.create(7902.6, 91.77))
                        .addPoint(RateDistortionPoint.create(9877.5, 94.19))
                        .addPoint(RateDistortionPoint.create(11871.8, 95.64))
                        .build()
            },
            {
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(2406.0, 80.0))
                        .addPoint(RateDistortionPoint.create(4032.0, 88.0))
                        .addPoint(RateDistortionPoint.create(5797.0, 92.0))
                        .addPoint(RateDistortionPoint.create(6914.0, 93.0))
                        .addPoint(RateDistortionPoint.create(7315.0, 93.5))
                        .addPoint(RateDistortionPoint.create(7474.0, 94.0))
                        .build(),
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(2112.7, 81.36))
                        .addPoint(RateDistortionPoint.create(3947.3, 88.82))
                        .addPoint(RateDistortionPoint.create(5925.6, 92.62))
                        .addPoint(RateDistortionPoint.create(7909.5, 94.59))
                        .addPoint(RateDistortionPoint.create(9883.4, 95.85))
                        .addPoint(RateDistortionPoint.create(11852.3, 96.7))
                        .build()
            },
            {
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(3864.0, 55.0))
                        .addPoint(RateDistortionPoint.create(5933.0, 67.0))
                        .addPoint(RateDistortionPoint.create(7893.0, 72.0))
                        .addPoint(RateDistortionPoint.create(9988.0, 76.0))
                        .addPoint(RateDistortionPoint.create(12028.0, 79.0))
                        .build(),
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(4249.6, 60.26))
                        .addPoint(RateDistortionPoint.create(4270.1, 60.76))
                        .addPoint(RateDistortionPoint.create(5928.1, 66.98))
                        .addPoint(RateDistortionPoint.create(7896.4, 72.66))
                        .addPoint(RateDistortionPoint.create(9886.2, 76.77))
                        .addPoint(RateDistortionPoint.create(11839.9, 79.97))
                        .build()
            },
            {
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(4370.0, 75.0))
                        .addPoint(RateDistortionPoint.create(6051.0, 82.0))
                        .addPoint(RateDistortionPoint.create(8026.0, 90.0))
                        .addPoint(RateDistortionPoint.create(9999.0, 93.0))
                        .addPoint(RateDistortionPoint.create(12001.0, 95.0))
                        .build(),
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(3347.8, 77.05))
                        .addPoint(RateDistortionPoint.create(4026.2, 80.68))
                        .addPoint(RateDistortionPoint.create(5949.2, 88.64))
                        .addPoint(RateDistortionPoint.create(7940.9, 93.18))
                        .addPoint(RateDistortionPoint.create(9907.9, 95.78))
                        .addPoint(RateDistortionPoint.create(11887.0, 97.33))
                        .build()
            },
            {
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(3956.0, 61.0))
                        .addPoint(RateDistortionPoint.create(4561.0, 63.0))
                        .addPoint(RateDistortionPoint.create(5938.0, 68.0))
                        .addPoint(RateDistortionPoint.create(7904.0, 72.0))
                        .addPoint(RateDistortionPoint.create(10028.0, 76.0))
                        .addPoint(RateDistortionPoint.create(12023.0, 78.0))
                        .build(),
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(4564.0, 63.84))
                        .addPoint(RateDistortionPoint.create(4657.4, 64.61))
                        .addPoint(RateDistortionPoint.create(5940.6, 68.02))
                        .addPoint(RateDistortionPoint.create(7907.0, 72.68))
                        .addPoint(RateDistortionPoint.create(9880.4, 76.04))
                        .addPoint(RateDistortionPoint.create(11841.0, 78.61))
                        .build()
            },
            {
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(3942.0, 76.0))
                        .addPoint(RateDistortionPoint.create(6008.0, 89.0))
                        .addPoint(RateDistortionPoint.create(8047.0, 94.0))
                        .addPoint(RateDistortionPoint.create(10117.0, 96.0))
                        .addPoint(RateDistortionPoint.create(11944.0, 98.0))
                        .build(),
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(3364.3, 78.53))
                        .addPoint(RateDistortionPoint.create(3998.1, 82.29))
                        .addPoint(RateDistortionPoint.create(5964.2, 90.12))
                        .addPoint(RateDistortionPoint.create(7948.5, 94.6))
                        .addPoint(RateDistortionPoint.create(9932.1, 96.95))
                        .addPoint(RateDistortionPoint.create(11907.0, 98.14))
                        .build()
            },
            {
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(4360.0, 67.0))
                        .addPoint(RateDistortionPoint.create(5996.0, 72.0))
                        .addPoint(RateDistortionPoint.create(8006.0, 86.0))
                        .addPoint(RateDistortionPoint.create(10019.0, 90.0))
                        .addPoint(RateDistortionPoint.create(12027.0, 93.0))
                        .build(),
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(4409.6, 76.81))
                        .addPoint(RateDistortionPoint.create(4464.3, 77.08))
                        .addPoint(RateDistortionPoint.create(5947.6, 82.9))
                        .addPoint(RateDistortionPoint.create(7919.1, 89.24))
                        .addPoint(RateDistortionPoint.create(9909.0, 92.89))
                        .addPoint(RateDistortionPoint.create(11905.7, 95.19))
                        .build()
            },
            {
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(4049.0, 75.0))
                        .addPoint(RateDistortionPoint.create(6026.0, 91.0))
                        .addPoint(RateDistortionPoint.create(6888.0, 94.0))
                        .addPoint(RateDistortionPoint.create(8019.0, 95.0))
                        .addPoint(RateDistortionPoint.create(10020.0, 98.0))
                        .addPoint(RateDistortionPoint.create(12005.0, 99.0))
                        .build(),
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(2981.3, 79.52))
                        .addPoint(RateDistortionPoint.create(3975.0, 85.13))
                        .addPoint(RateDistortionPoint.create(5948.8, 93.39))
                        .addPoint(RateDistortionPoint.create(7944.2, 97.33))
                        .addPoint(RateDistortionPoint.create(9912.2, 98.93))
                        .addPoint(RateDistortionPoint.create(11895.2, 99.56))
                        .build()
            },
            {
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(4780.0, 62.0))
                        .addPoint(RateDistortionPoint.create(6879.0, 71.0))
                        .addPoint(RateDistortionPoint.create(7964.0, 74.0))
                        .addPoint(RateDistortionPoint.create(9959.0, 79.0))
                        .addPoint(RateDistortionPoint.create(11968.0, 82.0))
                        .build(),
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(5475.6, 73.04))
                        .addPoint(RateDistortionPoint.create(5588.6, 73.26))
                        .addPoint(RateDistortionPoint.create(6298.4, 75.83))
                        .addPoint(RateDistortionPoint.create(7956.4, 80.46))
                        .addPoint(RateDistortionPoint.create(9945.2, 84.35))
                        .addPoint(RateDistortionPoint.create(11924.4, 87.24))
                        .build()
            },
            {
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(4705.0, 77.0))
                        .addPoint(RateDistortionPoint.create(4779.0, 78.0))
                        .addPoint(RateDistortionPoint.create(6858.0, 86.0))
                        .addPoint(RateDistortionPoint.create(7979.0, 89.0))
                        .addPoint(RateDistortionPoint.create(9928.0, 92.0))
                        .addPoint(RateDistortionPoint.create(11941.0, 94.0))
                        .build(),
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(4022.5, 77.7))
                        .addPoint(RateDistortionPoint.create(4315.0, 80.11))
                        .addPoint(RateDistortionPoint.create(6000.8, 86.89))
                        .addPoint(RateDistortionPoint.create(7950.5, 91.04))
                        .addPoint(RateDistortionPoint.create(9962.1, 93.7))
                        .addPoint(RateDistortionPoint.create(11918.9, 95.3))
                        .build()
            },
            {
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(4669.0, 88.0))
                        .addPoint(RateDistortionPoint.create(6793.0, 92.0))
                        .addPoint(RateDistortionPoint.create(7929.0, 93.93))
                        .addPoint(RateDistortionPoint.create(9896.0, 95.0))
                        .addPoint(RateDistortionPoint.create(11925.0, 96.0))
                        .build(),
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(2463.5, 82.36))
                        .addPoint(RateDistortionPoint.create(3986.3, 89.17))
                        .addPoint(RateDistortionPoint.create(5969.9, 92.72))
                        .addPoint(RateDistortionPoint.create(7950.6, 94.59))
                        .addPoint(RateDistortionPoint.create(9935.6, 95.79))
                        .addPoint(RateDistortionPoint.create(11918.6, 96.64))
                        .build()
            },
            {
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(5076.0, 60.0))
                        .addPoint(RateDistortionPoint.create(6853.0, 70.0))
                        .addPoint(RateDistortionPoint.create(7945.0, 73.0))
                        .addPoint(RateDistortionPoint.create(9892.0, 77.0))
                        .addPoint(RateDistortionPoint.create(11862.0, 80.0))
                        .build(),
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(5025.1, 61.54))
                        .addPoint(RateDistortionPoint.create(5083.6, 60.78))
                        .addPoint(RateDistortionPoint.create(5999.4, 68.98))
                        .addPoint(RateDistortionPoint.create(7951.5, 75.18))
                        .addPoint(RateDistortionPoint.create(9950.6, 79.16))
                        .addPoint(RateDistortionPoint.create(11899.2, 82.11))
                        .build()
            },
            {
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(4695.0, 74.0))
                        .addPoint(RateDistortionPoint.create(4768.0, 75.0))
                        .addPoint(RateDistortionPoint.create(8015.0, 88.0))
                        .addPoint(RateDistortionPoint.create(9985.0, 92.0))
                        .addPoint(RateDistortionPoint.create(11919.0, 94.0))
                        .build(),
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(3440.6, 74.08))
                        .addPoint(RateDistortionPoint.create(4055.3, 78.78))
                        .addPoint(RateDistortionPoint.create(5990.9, 87.23))
                        .addPoint(RateDistortionPoint.create(7958.2, 92.06))
                        .addPoint(RateDistortionPoint.create(9949.3, 94.76))
                        .addPoint(RateDistortionPoint.create(11939.8, 96.46))
                        .build()
            },
            {
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(6710.0, 67.0))
                        .addPoint(RateDistortionPoint.create(6911.0, 72.0))
                        .addPoint(RateDistortionPoint.create(8000.0, 74.0))
                        .addPoint(RateDistortionPoint.create(9973.0, 77.0))
                        .addPoint(RateDistortionPoint.create(11972.0, 79.0))
                        .build(),
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(6396.5, 68.74))
                        .addPoint(RateDistortionPoint.create(6718.2, 67.46))
                        .addPoint(RateDistortionPoint.create(6743.5, 71.93))
                        .addPoint(RateDistortionPoint.create(8033.0, 76.17))
                        .addPoint(RateDistortionPoint.create(9966.0, 79.54))
                        .addPoint(RateDistortionPoint.create(11926.1, 81.9))
                        .build()
            },
            {
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(4709.0, 78.0))
                        .addPoint(RateDistortionPoint.create(4779.0, 79.0))
                        .addPoint(RateDistortionPoint.create(6830.0, 88.0))
                        .addPoint(RateDistortionPoint.create(7939.0, 91.0))
                        .addPoint(RateDistortionPoint.create(9927.0, 94.0))
                        .addPoint(RateDistortionPoint.create(11835.0, 96.0))
                        .build(),
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(3979.8, 78.6))
                        .addPoint(RateDistortionPoint.create(4173.6, 80.72))
                        .addPoint(RateDistortionPoint.create(6007.5, 89.77))
                        .addPoint(RateDistortionPoint.create(8005.1, 94.59))
                        .addPoint(RateDistortionPoint.create(10010.1, 96.91))
                        .addPoint(RateDistortionPoint.create(12031.4, 98.13))
                        .build()
            },
            {
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(4685.0, 62.0))
                        .addPoint(RateDistortionPoint.create(4766.0, 63.0))
                        .addPoint(RateDistortionPoint.create(6843.0, 77.0))
                        .addPoint(RateDistortionPoint.create(7939.0, 81.0))
                        .addPoint(RateDistortionPoint.create(9922.0, 87.0))
                        .addPoint(RateDistortionPoint.create(11904.0, 90.0))
                        .build(),
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(4701.5, 75.76))
                        .addPoint(RateDistortionPoint.create(4749.5, 76.33))
                        .addPoint(RateDistortionPoint.create(6011.4, 81.57))
                        .addPoint(RateDistortionPoint.create(7998.0, 87.84))
                        .addPoint(RateDistortionPoint.create(10012.1, 91.53))
                        .addPoint(RateDistortionPoint.create(12001.3, 93.92))
                        .build()
            },
            {
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(4802.0, 82.0))
                        .addPoint(RateDistortionPoint.create(6899.0, 91.0))
                        .addPoint(RateDistortionPoint.create(8003.0, 94.0))
                        .addPoint(RateDistortionPoint.create(9984.0, 97.0))
                        .addPoint(RateDistortionPoint.create(11992.0, 98.0))
                        .build(),
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(3251.8, 78.72))
                        .addPoint(RateDistortionPoint.create(3995.7, 83.92))
                        .addPoint(RateDistortionPoint.create(5976.3, 92.71))
                        .addPoint(RateDistortionPoint.create(7972.7, 96.8))
                        .addPoint(RateDistortionPoint.create(9980.9, 98.65))
                        .addPoint(RateDistortionPoint.create(11956.8, 99.33))
                        .build()
            },
            {
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(4802.0, 82.0))
                        .addPoint(RateDistortionPoint.create(6899.0, 91.0))
                        .addPoint(RateDistortionPoint.create(8003.0, 94.0))
                        .addPoint(RateDistortionPoint.create(9984.0, 97.0))
                        .addPoint(RateDistortionPoint.create(11992.0, 98.0))
                        .build(),
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(3251.8, 78.72))
                        .addPoint(RateDistortionPoint.create(3995.7, 83.92))
                        .addPoint(RateDistortionPoint.create(5976.3, 92.71))
                        .addPoint(RateDistortionPoint.create(7972.7, 96.8))
                        .addPoint(RateDistortionPoint.create(9980.9, 98.65))
                        .addPoint(RateDistortionPoint.create(11956.8, 99.33))
                        .build()
            },
        };
    }
}
