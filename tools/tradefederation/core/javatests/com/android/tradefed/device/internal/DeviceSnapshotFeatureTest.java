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
package com.android.tradefed.device.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tradefed.config.Configuration;
import com.android.tradefed.config.ConfigurationDef;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.invoker.TestInformation;

import com.proto.tradefed.feature.FeatureRequest;
import com.proto.tradefed.feature.FeatureResponse;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Unit tests for {@link DeviceSnapshotFeature}. */
@RunWith(JUnit4.class)
public class DeviceSnapshotFeatureTest {

    private DeviceSnapshotFeature mFeature;
    private IConfiguration mConfiguration;
    private TestInformation mTestInformation;
    private @Mock ITestDevice mMockDevice;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mFeature = new DeviceSnapshotFeature();
        mConfiguration = new Configuration("name", "description");

        mFeature.setConfiguration(mConfiguration);
        IInvocationContext context = new InvocationContext();
        context.addAllocatedDevice(ConfigurationDef.DEFAULT_DEVICE_NAME, mMockDevice);
        mTestInformation = TestInformation.newBuilder().setInvocationContext(context).build();
        mFeature.setTestInformation(mTestInformation);
    }

    @Test
    public void testFeature_noDeviceName() {
        FeatureRequest.Builder request =
                FeatureRequest.newBuilder().putArgs("serial", "device-serial");

        FeatureResponse response = mFeature.execute(request.build());
        assertTrue(response.hasErrorInfo());
        assertEquals("No device_name args specified.", response.getErrorInfo().getErrorTrace());
    }

    @Test
    public void testFeature_snapshot() throws Exception {
        FeatureRequest.Builder request =
                FeatureRequest.newBuilder()
                        .putArgs("serial", "device-serial")
                        .putArgs("device_name", ConfigurationDef.DEFAULT_DEVICE_NAME);

        FeatureResponse response = mFeature.execute(request.build());
        assertFalse(response.hasErrorInfo());
    }

    @Test
    public void testFeature_restoreSnapshot() throws Exception {
        FeatureRequest.Builder request =
                FeatureRequest.newBuilder()
                        .putArgs("serial", "device-serial")
                        .putArgs("device_name", ConfigurationDef.DEFAULT_DEVICE_NAME)
                        .putArgs("snapshot_id", "random_id");

        FeatureResponse response = mFeature.execute(request.build());
        assertFalse(response.hasErrorInfo());
    }
}
