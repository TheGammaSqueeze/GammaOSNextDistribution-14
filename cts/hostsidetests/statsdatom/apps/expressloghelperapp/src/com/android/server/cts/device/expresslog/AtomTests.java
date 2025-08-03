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
package com.android.server.cts.device.expresslog;

import com.android.modules.expresslog.Counter;
import com.android.modules.expresslog.Histogram;

import org.junit.Test;

public class AtomTests {

    private static final int TEST_UID = 123;

    @Test
    public void testCounterMetric() throws Exception {
        Counter.logIncrement("tex_test.value_telemetry_express_test_counter");
        Thread.sleep(10);
        Counter.logIncrement("tex_test.value_telemetry_express_test_counter", 10);
    }

    @Test
    public void testCounterWithUidMetric() throws Exception {
        Counter.logIncrementWithUid(
                "tex_test.value_telemetry_express_test_counter_with_uid", TEST_UID);
        Thread.sleep(10);
        Counter.logIncrementWithUid(
                "tex_test.value_telemetry_express_test_counter_with_uid", TEST_UID, 10);
    }

    @Test
    public void testHistogramUniformMetric() throws Exception {
        final Histogram histogram =
                new Histogram(
                        "tex_test.value_telemetry_express_fixed_range_histogram",
                        new Histogram.UniformOptions(
                                /* binCount= */ 50,
                                /* minValue= */ 1,
                                /* exclusiveMaxValue= */ 1000000));
        histogram.logSample(0.f);
        Thread.sleep(10);
        histogram.logSample(1.f);
        Thread.sleep(10);
        histogram.logSample(100.f);
        Thread.sleep(10);
        histogram.logSample(2000000.f);
    }

    @Test
    public void testHistogramScaledMetric() throws Exception {
        final Histogram histogram =
                new Histogram(
                        "tex_test.value_telemetry_express_scaled_factor_histogram",
                        new Histogram.ScaledRangeOptions(
                                /* binCount= */ 20,
                                /* minValue= */ 1,
                                /* firstBinWidth= */ 10,
                                /* scaleFactor= */ 1.6f));
        histogram.logSample(0.f);
        Thread.sleep(10);
        histogram.logSample(1.f);
        Thread.sleep(10);
        histogram.logSample(100.f);
        Thread.sleep(10);
        histogram.logSample(2000000.f);
    }

    @Test
    public void testHistogramUniformWithUidMetric() throws Exception {
        final Histogram histogram =
                new Histogram(
                        "tex_test.value_telemetry_express_fixed_range_histogram_with_uid",
                        new Histogram.UniformOptions(
                                /* binCount= */ 50,
                                /* minValue= */ 1,
                                /* exclusiveMaxValue= */ 1000000));
        histogram.logSampleWithUid(TEST_UID, 0.f);
        Thread.sleep(10);
        histogram.logSampleWithUid(TEST_UID, 1.f);
        Thread.sleep(10);
        histogram.logSampleWithUid(TEST_UID, 100.f);
        Thread.sleep(10);
        histogram.logSampleWithUid(TEST_UID, 2000000.f);
    }

    @Test
    public void testHistogramScaledWithUidMetric() throws Exception {
        final Histogram histogram =
                new Histogram(
                        "tex_test.value_telemetry_express_scaled_range_histogram_with_uid",
                        new Histogram.ScaledRangeOptions(
                                /* binCount= */ 20,
                                /* minValue= */ 1,
                                /* firstBinWidth= */ 10,
                                /* scaleFactor= */ 1.6f));
        histogram.logSampleWithUid(TEST_UID, 0.f);
        Thread.sleep(10);
        histogram.logSampleWithUid(TEST_UID, 1.f);
        Thread.sleep(10);
        histogram.logSampleWithUid(TEST_UID, 100.f);
        Thread.sleep(10);
        histogram.logSampleWithUid(TEST_UID, 2000000.f);
    }
}
