/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tradefed.postprocessor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;

import com.android.tradefed.metrics.proto.MetricMeasurement.DataType;
import com.android.tradefed.metrics.proto.MetricMeasurement.DoubleValues;
import com.android.tradefed.metrics.proto.MetricMeasurement.Measurements;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.metrics.proto.MetricMeasurement.NumericValues;
import com.android.tradefed.result.ITestInvocationListener;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;

/** Unit tests for {@link AveragePostProcessor}. */
@RunWith(JUnit4.class)
public class AveragePostProcessorTest {

    private AveragePostProcessor mProcessor;
    @Mock ITestInvocationListener mMockListener;
    @Captor ArgumentCaptor<HashMap<String, Metric>> mCapture;
    private ITestInvocationListener mMainListener;
    private HashMap<String, Metric> mMetrics;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mProcessor = new AveragePostProcessor();

        mMainListener = mProcessor.init(mMockListener);
        mMetrics = new HashMap<>();
    }

    @Test
    public void testAverage_double() {
        mMetrics.put("key1", createDoubleListMetric());

        mMainListener.testRunEnded(15L, mMetrics);

        verify(mMockListener).testRunEnded(Mockito.eq(15L), mCapture.capture());

        HashMap<String, Metric> result = mCapture.getValue();
        // We added one metric but end up with two
        assertEquals(2, result.size());
        assertTrue(result.containsKey("key1"));
        assertTrue(result.containsKey("key1" + AveragePostProcessor.AVERAGE_KEY_TAG));

        // Check that the average contains a single value average of the list.
        Metric average = result.get("key1" + AveragePostProcessor.AVERAGE_KEY_TAG);
        assertEquals(DataType.PROCESSED, average.getType());
        assertEquals(7.5d, average.getMeasurements().getSingleDouble(), 0.001d);
    }

    @Test
    public void testAverage_long() {
        mMetrics.put("key1", createLongListMetric());

        mMainListener.testRunEnded(15L, mMetrics);

        verify(mMockListener).testRunEnded(Mockito.eq(15L), mCapture.capture());

        HashMap<String, Metric> result = mCapture.getValue();
        // We added one metric but end up with two
        assertEquals(2, result.size());
        assertTrue(result.containsKey("key1"));
        assertTrue(result.containsKey("key1" + AveragePostProcessor.AVERAGE_KEY_TAG));

        // Check that the average contains a single value average of the list.
        Metric average = result.get("key1" + AveragePostProcessor.AVERAGE_KEY_TAG);
        assertEquals(DataType.PROCESSED, average.getType());
        assertEquals(71.666d, average.getMeasurements().getSingleDouble(), 0.001d);
    }

    private Metric createDoubleListMetric() {
        Metric.Builder builder = Metric.newBuilder();
        Measurements.Builder measureBuilder = Measurements.newBuilder();
        measureBuilder.setDoubleValues(
                DoubleValues.newBuilder()
                        .addDoubleValue(5d)
                        .addDoubleValue(10.5d)
                        .addDoubleValue(7d)
                        .build());
        builder.setMeasurements(measureBuilder.build());
        return builder.build();
    }

    private Metric createLongListMetric() {
        Metric.Builder builder = Metric.newBuilder();
        Measurements.Builder measureBuilder = Measurements.newBuilder();
        measureBuilder.setNumericValues(
                NumericValues.newBuilder()
                        .addNumericValue(5L)
                        .addNumericValue(77L)
                        .addNumericValue(133L)
                        .build());
        builder.setMeasurements(measureBuilder.build());
        return builder.build();
    }
}
