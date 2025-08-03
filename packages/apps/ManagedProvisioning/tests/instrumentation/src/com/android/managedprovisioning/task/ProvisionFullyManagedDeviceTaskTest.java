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

package com.android.managedprovisioning.task;

import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.app.admin.DevicePolicyManager;
import android.app.admin.FullyManagedDeviceProvisioningParams;
import android.app.admin.ProvisioningException;
import android.content.ComponentName;
import android.content.Context;
import android.content.res.Resources;

import androidx.test.filters.SmallTest;

import com.android.managedprovisioning.analytics.ProvisioningAnalyticsTracker;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.ProvisioningParams;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link ProvisionFullyManagedDeviceTask}.
 */
@SmallTest
@RunWith(JUnit4.class)
public class ProvisionFullyManagedDeviceTaskTest {
    private static final int TEST_USER_ID = 111;
    private static final String TEST_DPC_PACKAGE_NAME = "com.test.dpc";
    private static final String OWNER_NAME = "ownerName";
    private static final ComponentName ADMIN = new ComponentName(
            TEST_DPC_PACKAGE_NAME, ".Receiver");
    private static final ProvisioningParams TEST_PARAMS = new ProvisioningParams.Builder()
            .setDeviceAdminComponentName(ADMIN)
            .setProvisioningAction(ACTION_PROVISION_MANAGED_DEVICE)
            .build();

    @Mock private Context mContext;
    @Mock private DevicePolicyManager mDevicePolicyManager;
    @Mock private AbstractProvisioningTask.Callback mCallback;
    @Mock private Utils mUtils;
    @Mock private Resources mResources;

    @Captor private ArgumentCaptor<FullyManagedDeviceProvisioningParams> mParamsCaptor;

    private static final String TEST_ERROR_MESSAGE = "test error message";
    private static final ProvisioningException PROVISIONING_EXCEPTION = new ProvisioningException(
            new Exception(), /* provisioningError= */ 0, TEST_ERROR_MESSAGE);

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mContext.getSystemServiceName(DevicePolicyManager.class))
                .thenReturn(Context.DEVICE_POLICY_SERVICE);
        when(mContext.getSystemService(DevicePolicyManager.class))
                .thenReturn(mDevicePolicyManager);
        when(mContext.getResources()).thenReturn(mResources);
        when(mResources.getString(anyInt())).thenReturn(OWNER_NAME);
        when(mUtils.findDeviceAdmin(TEST_DPC_PACKAGE_NAME, ADMIN, mContext, TEST_USER_ID))
                .thenReturn(ADMIN);
    }

    @Test
    public void testSuccess() {
        ProvisionFullyManagedDeviceTask task = createProvisioningTask(TEST_PARAMS);

        task.run(TEST_USER_ID);

        verify(mCallback).onSuccess(task);
        verifyNoMoreInteractions(mCallback);
    }

    @Test
    public void testError() throws Exception {
        ProvisionFullyManagedDeviceTask task = createProvisioningTask(TEST_PARAMS);
        doThrow(createProvisioningException()).when(mDevicePolicyManager)
                .provisionFullyManagedDevice(any());

        task.run(TEST_USER_ID);

        verify(mCallback).onError(task, 0, /* errorMessage= */ null);
        verifyNoMoreInteractions(mCallback);
    }

    @Test
    public void testTextError() throws Exception {
        ProvisionFullyManagedDeviceTask task = createProvisioningTask(TEST_PARAMS);
        doThrow(PROVISIONING_EXCEPTION)
                .when(mDevicePolicyManager).provisionFullyManagedDevice(any());

        task.run(TEST_USER_ID);

        verify(mCallback).onError(task, 0, TEST_ERROR_MESSAGE);
        verifyNoMoreInteractions(mCallback);
    }

    @Test
    public void testCanGrantSensorsPermissionsByDefault() throws ProvisioningException {
        ProvisionFullyManagedDeviceTask task = createProvisioningTask(TEST_PARAMS);

        task.run(TEST_USER_ID);

        verify(mCallback).onSuccess(task);
        verifyNoMoreInteractions(mCallback);
        verify(mDevicePolicyManager).provisionFullyManagedDevice(mParamsCaptor.capture());
        FullyManagedDeviceProvisioningParams capturedParams = mParamsCaptor.getValue();
        assertThat(capturedParams.canDeviceOwnerGrantSensorsPermissions()).isTrue();
    }

    @Test
    public void testCanOptOutOfGrantingSensorsPermissions() throws ProvisioningException {
        final ProvisioningParams testParams = new ProvisioningParams.Builder()
                .setDeviceAdminComponentName(ADMIN)
                .setProvisioningAction(ACTION_PROVISION_MANAGED_DEVICE)
                .setDeviceOwnerPermissionGrantOptOut(true)
                .build();
        ProvisionFullyManagedDeviceTask task = createProvisioningTask(testParams);

        task.run(TEST_USER_ID);

        verify(mCallback).onSuccess(task);
        verifyNoMoreInteractions(mCallback);
        verify(mDevicePolicyManager).provisionFullyManagedDevice(mParamsCaptor.capture());
        FullyManagedDeviceProvisioningParams capturedParams = mParamsCaptor.getValue();
        assertThat(capturedParams.canDeviceOwnerGrantSensorsPermissions()).isFalse();
    }


    private ProvisionFullyManagedDeviceTask createProvisioningTask(ProvisioningParams params) {
        return new ProvisionFullyManagedDeviceTask(
                mUtils,
                mContext,
                params,
                mCallback,
                mock(ProvisioningAnalyticsTracker.class));
    }

    private ProvisioningException createProvisioningException() {
        return new ProvisioningException(
                new Exception(),
                ProvisioningException.ERROR_PRE_CONDITION_FAILED);
    }
}
