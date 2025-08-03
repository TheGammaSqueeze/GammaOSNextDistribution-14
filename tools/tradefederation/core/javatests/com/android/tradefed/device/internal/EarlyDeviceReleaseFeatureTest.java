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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.tradefed.command.ICommandScheduler.IScheduledInvocationListener;
import com.android.tradefed.device.FreeDeviceState;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.TestInformation;

import com.proto.tradefed.feature.FeatureRequest;
import com.proto.tradefed.feature.FeatureResponse;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Unit tests for {@link EarlyDeviceReleaseFeature}. */
@RunWith(JUnit4.class)
public class EarlyDeviceReleaseFeatureTest {

    private static final String DEVICE_1_NAME = "device1";
    private static final FreeDeviceState DEVICE_1_STATE = FreeDeviceState.AVAILABLE;
    private static final String DEVICE_2_NAME = "device2";
    private static final FreeDeviceState DEVICE_2_STATE = FreeDeviceState.UNAVAILABLE;

    private EarlyDeviceReleaseFeature mEarlyDeviceReleaseFeature;
    private List<IScheduledInvocationListener> mScheduledInvocationListeners;

    private @Mock IScheduledInvocationListener mListener1;
    private @Mock IScheduledInvocationListener mListener2;
    private @Mock TestInformation mTestInformation;
    private @Mock IInvocationContext mInvocationContext;
    private @Mock ITestDevice mTestDevice1;
    private @Mock ITestDevice mTestDevice2;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mEarlyDeviceReleaseFeature = new EarlyDeviceReleaseFeature();

        mScheduledInvocationListeners = new ArrayList<>();
        mScheduledInvocationListeners.add(mListener1);
        mScheduledInvocationListeners.add(mListener2);

        when(mTestInformation.getContext()).thenReturn(mInvocationContext);
        when(mInvocationContext.getDevice(DEVICE_1_NAME)).thenReturn(mTestDevice1);
        when(mInvocationContext.getDevice(DEVICE_2_NAME)).thenReturn(mTestDevice2);

        mEarlyDeviceReleaseFeature.setListeners(mScheduledInvocationListeners);
        mEarlyDeviceReleaseFeature.setTestInformation(mTestInformation);
    }

    @Test
    public void testFeature_oneDevice() {
        FeatureRequest request =
                FeatureRequest.newBuilder().putArgs(DEVICE_1_NAME, DEVICE_1_STATE.name()).build();
        FeatureResponse response = mEarlyDeviceReleaseFeature.execute(request);

        ArgumentCaptor<Map<ITestDevice, FreeDeviceState>> capture =
                ArgumentCaptor.forClass(Map.class);
        assertFalse(response.hasErrorInfo());
        verify(mListener1).releaseDevices(Mockito.eq(mInvocationContext), capture.capture());
        Map<ITestDevice, FreeDeviceState> actual = capture.getValue();
        Map<ITestDevice, FreeDeviceState> expected = Map.of(mTestDevice1, DEVICE_1_STATE);
        assertEquals(expected, actual);
    }

    @Test
    public void testFeature_multipleDevices() {
        FeatureRequest request =
                FeatureRequest.newBuilder()
                        .putArgs(DEVICE_1_NAME, DEVICE_1_STATE.name())
                        .putArgs(DEVICE_2_NAME, DEVICE_2_STATE.name())
                        .build();
        FeatureResponse response = mEarlyDeviceReleaseFeature.execute(request);

        ArgumentCaptor<Map<ITestDevice, FreeDeviceState>> capture =
                ArgumentCaptor.forClass(Map.class);
        assertFalse(response.hasErrorInfo());
        verify(mListener1).releaseDevices(Mockito.eq(mInvocationContext), capture.capture());
        Map<ITestDevice, FreeDeviceState> actual = capture.getValue();
        Map<ITestDevice, FreeDeviceState> expected =
                Map.of(
                        mTestDevice1, DEVICE_1_STATE,
                        mTestDevice2, DEVICE_2_STATE);
        assertEquals(expected, actual);
    }
}
