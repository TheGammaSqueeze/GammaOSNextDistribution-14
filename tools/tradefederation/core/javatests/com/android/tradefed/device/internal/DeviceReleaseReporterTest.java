/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tradefed.device.internal;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.tradefed.device.FreeDeviceState;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.service.TradefedFeatureClient;

import com.proto.tradefed.feature.FeatureResponse;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.Map;

/** Unit tests for {@link DeviceReleaseReporter}. */
@RunWith(JUnit4.class)
public class DeviceReleaseReporterTest {

    private static final String DEVICE_1_NAME = "test device 1";
    private static final FreeDeviceState DEVICE_1_STATE = FreeDeviceState.AVAILABLE;
    private static final String DEVICE_2_NAME = "test device 2";
    private static final FreeDeviceState DEVICE_2_STATE = FreeDeviceState.UNAVAILABLE;

    private DeviceReleaseReporter mDeviceReleaseReporter;
    private @Mock IInvocationContext mInvocationContext;
    private @Mock ITestDevice mTestDevice1;
    private @Mock ITestDevice mTestDevice2;
    private @Mock TradefedFeatureClient mTradefedFeatureClient;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mDeviceReleaseReporter = new DeviceReleaseReporter(mTradefedFeatureClient);
    }

    @Test
    public void releaseDevices_oneDevice() {
        when(mInvocationContext.getDeviceName(mTestDevice1)).thenReturn(DEVICE_1_NAME);
        when(mTradefedFeatureClient.triggerFeature(any(), any()))
                .thenReturn(FeatureResponse.newBuilder().build());
        mDeviceReleaseReporter.releaseDevices(
                mInvocationContext, Map.of(mTestDevice1, DEVICE_1_STATE));

        ArgumentCaptor<Map<String, String>> capture = ArgumentCaptor.forClass(Map.class);
        verify(mTradefedFeatureClient)
                .triggerFeature(
                        Mockito.eq(EarlyDeviceReleaseFeature.EARLY_DEVICE_RELEASE_FEATURE_NAME),
                        capture.capture());
        Map<String, String> actual = capture.getValue();
        Map<String, String> expected = Map.of(DEVICE_1_NAME, DEVICE_1_STATE.name());
        assertEquals(actual, expected);
    }

    @Test
    public void releaseDevices_multipleDevices() {
        when(mInvocationContext.getDeviceName(mTestDevice1)).thenReturn(DEVICE_1_NAME);
        when(mInvocationContext.getDeviceName(mTestDevice2)).thenReturn(DEVICE_2_NAME);
        when(mTradefedFeatureClient.triggerFeature(any(), any()))
                .thenReturn(FeatureResponse.newBuilder().build());
        mDeviceReleaseReporter.releaseDevices(
                mInvocationContext,
                Map.of(
                        mTestDevice1, DEVICE_1_STATE,
                        mTestDevice2, DEVICE_2_STATE));

        ArgumentCaptor<Map<String, String>> capture = ArgumentCaptor.forClass(Map.class);
        verify(mTradefedFeatureClient)
                .triggerFeature(
                        Mockito.eq(EarlyDeviceReleaseFeature.EARLY_DEVICE_RELEASE_FEATURE_NAME),
                        capture.capture());
        Map<String, String> actual = capture.getValue();
        Map<String, String> expected =
                Map.of(
                        DEVICE_1_NAME, DEVICE_1_STATE.name(),
                        DEVICE_2_NAME, DEVICE_2_STATE.name());
        assertEquals(actual, expected);
    }
}
