/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tradefed.device.metric;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.tradefed.config.ConfigurationDef;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.FailureDescription;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.TestDescription;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;

/** Unit tests for {@link BugreportzOnFailureCollector}. */
@RunWith(JUnit4.class)
public class BugreportzOnFailureCollectorTest {
    private BugreportzOnFailureCollector mCollector;
    @Mock ITestInvocationListener mMockListener;
    @Mock ITestDevice mMockDevice;

    private ITestInvocationListener mTestListener;
    private IInvocationContext mContext;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mCollector = new BugreportzOnFailureCollector();
        mContext = new InvocationContext();
        mContext.addAllocatedDevice(ConfigurationDef.DEFAULT_DEVICE_NAME, mMockDevice);
        mTestListener = mCollector.init(mContext, mMockListener);
        when(mMockDevice.getSerialNumber()).thenReturn("serial");
    }

    @Test
    public void testCollect() throws Exception {
        TestDescription test = new TestDescription("class", "test");

        when(mMockDevice.logBugreport(
                        Mockito.eq("run-name-serial-bugreportz-on-failure"), Mockito.any()))
                .thenReturn(true);

        mTestListener.testRunStarted("run-name", 1);
        mTestListener.testStarted(test);
        mTestListener.testFailed(test, "I failed");
        mTestListener.testEnded(test, new HashMap<String, Metric>());
        mTestListener.testRunFailed(FailureDescription.create("run fail"));

        verify(mMockListener)
                .testRunStarted(
                        Mockito.eq("run-name"), Mockito.eq(1), Mockito.eq(0), Mockito.anyLong());
        verify(mMockListener).testStarted(Mockito.eq(test), Mockito.anyLong());
        verify(mMockListener).testFailed(Mockito.eq(test), (String) Mockito.any());
        verify(mMockListener)
                .testEnded(
                        Mockito.eq(test),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        verify(mMockListener).testRunFailed((FailureDescription) Mockito.any());
    }
}
