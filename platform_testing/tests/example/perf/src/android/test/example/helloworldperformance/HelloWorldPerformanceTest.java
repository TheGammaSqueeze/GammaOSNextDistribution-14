/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.test.example.helloworldperformance;

import com.android.tradefed.metrics.proto.MetricMeasurement.DataType;
import com.android.tradefed.metrics.proto.MetricMeasurement.Measurements;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner.TestMetrics;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class HelloWorldPerformanceTest {

    @Rule public TestMetrics metrics = new TestMetrics();

    private static final String TAG = HelloWorldPerformanceTest.class.getSimpleName();

    @Test
    public void testHelloWorld() {
        metrics.addTestMetric(
                "key_hello_world",
                Metric.newBuilder()
                        .setType(DataType.RAW)
                        .setMeasurements(Measurements.newBuilder().setSingleString("10"))
                        .build());
    }

    @Test
    public void testHalloWelt() {
        metrics.addTestMetric(
                "key_hallo_welt",
                Metric.newBuilder()
                        .setType(DataType.RAW)
                        .setMeasurements(Measurements.newBuilder().setSingleString("20"))
                        .build());
    }
}
