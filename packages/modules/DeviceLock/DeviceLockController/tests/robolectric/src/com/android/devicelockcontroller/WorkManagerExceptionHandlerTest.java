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

package com.android.devicelockcontroller;

import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionState.KIOSK_PROVISIONED;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionState.PROVISION_FAILED;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionState.PROVISION_IN_PROGRESS;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionState.PROVISION_PAUSED;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionState.PROVISION_SUCCEEDED;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionState.UNPROVISIONED;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.test.core.app.ApplicationProvider;

import com.android.devicelockcontroller.policy.DevicePolicyController;
import com.android.devicelockcontroller.policy.DeviceStateController;
import com.android.devicelockcontroller.policy.ProvisionStateController;

import com.google.common.util.concurrent.Futures;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.RobolectricTestRunner;

import java.util.concurrent.ExecutionException;

@RunWith(RobolectricTestRunner.class)
public final class WorkManagerExceptionHandlerTest {
    private TestDeviceLockControllerApplication mTestApp;

    @Rule
    public final MockitoRule mocks = MockitoJUnit.rule();

    @Mock
    private Runnable mTerminationRunnableMock;

    @Before
    public void setUp() {
        mTestApp = ApplicationProvider.getApplicationContext();
    }

    @Test
    public void handleException_whenUnprovisioned_shouldTerminateNoWipe()
            throws ExecutionException, InterruptedException {
        WorkManagerExceptionHandler workManagerExceptionHandler =
                new WorkManagerExceptionHandler(mTestApp, mTerminationRunnableMock);
        ProvisionStateController provisionStateController = mTestApp.getProvisionStateController();
        DeviceStateController deviceStateController = mTestApp.getDeviceStateController();
        DevicePolicyController devicePolicyController = mTestApp.getPolicyController();

        when(provisionStateController.getState())
                .thenReturn(Futures.immediateFuture(UNPROVISIONED));
        when(deviceStateController.isCleared()).thenReturn(Futures.immediateFuture(false));

        workManagerExceptionHandler.handleException(mTestApp, new RuntimeException()).get();

        verify(mTerminationRunnableMock).run();
        verify(devicePolicyController, never()).wipeDevice();
    }

    @Test
    public void handleException_whenProvisionInProgress_shouldWipeNoTerminate()
            throws ExecutionException, InterruptedException {
        WorkManagerExceptionHandler workManagerExceptionHandler =
                new WorkManagerExceptionHandler(mTestApp, mTerminationRunnableMock);
        ProvisionStateController provisionStateController = mTestApp.getProvisionStateController();
        DeviceStateController deviceStateController = mTestApp.getDeviceStateController();
        DevicePolicyController devicePolicyController = mTestApp.getPolicyController();

        when(provisionStateController.getState())
                .thenReturn(Futures.immediateFuture(PROVISION_IN_PROGRESS));
        when(deviceStateController.isCleared()).thenReturn(Futures.immediateFuture(false));

        workManagerExceptionHandler.handleException(mTestApp, new RuntimeException()).get();

        verify(mTerminationRunnableMock, never()).run();
        verify(devicePolicyController).wipeDevice();
    }

    @Test
    public void handleException_whenProvisionPaused_shouldWipeNoTerminate()
            throws ExecutionException, InterruptedException {
        WorkManagerExceptionHandler workManagerExceptionHandler =
                new WorkManagerExceptionHandler(mTestApp, mTerminationRunnableMock);
        ProvisionStateController provisionStateController = mTestApp.getProvisionStateController();
        DeviceStateController deviceStateController = mTestApp.getDeviceStateController();
        DevicePolicyController devicePolicyController = mTestApp.getPolicyController();

        when(provisionStateController.getState())
                .thenReturn(Futures.immediateFuture(PROVISION_PAUSED));
        when(deviceStateController.isCleared()).thenReturn(Futures.immediateFuture(false));

        workManagerExceptionHandler.handleException(mTestApp, new RuntimeException()).get();

        verify(mTerminationRunnableMock, never()).run();
        verify(devicePolicyController).wipeDevice();
    }

    @Test
    public void handleException_whenProvisionFailed_shouldWipeNoTerminate()
            throws ExecutionException, InterruptedException {
        WorkManagerExceptionHandler workManagerExceptionHandler =
                new WorkManagerExceptionHandler(mTestApp, mTerminationRunnableMock);
        ProvisionStateController provisionStateController = mTestApp.getProvisionStateController();
        DeviceStateController deviceStateController = mTestApp.getDeviceStateController();
        DevicePolicyController devicePolicyController = mTestApp.getPolicyController();

        when(provisionStateController.getState())
                .thenReturn(Futures.immediateFuture(PROVISION_FAILED));
        when(deviceStateController.isCleared()).thenReturn(Futures.immediateFuture(false));

        workManagerExceptionHandler.handleException(mTestApp, new RuntimeException()).get();

        verify(mTerminationRunnableMock, never()).run();
        verify(devicePolicyController).wipeDevice();
    }

    @Test
    public void handleException_whenKioskProvisioned_shouldWipeNoTerminate()
            throws ExecutionException, InterruptedException {
        WorkManagerExceptionHandler workManagerExceptionHandler =
                new WorkManagerExceptionHandler(mTestApp, mTerminationRunnableMock);
        ProvisionStateController provisionStateController = mTestApp.getProvisionStateController();
        DeviceStateController deviceStateController = mTestApp.getDeviceStateController();
        DevicePolicyController devicePolicyController = mTestApp.getPolicyController();

        when(provisionStateController.getState())
                .thenReturn(Futures.immediateFuture(KIOSK_PROVISIONED));
        when(deviceStateController.isCleared()).thenReturn(Futures.immediateFuture(false));

        workManagerExceptionHandler.handleException(mTestApp, new RuntimeException()).get();

        verify(mTerminationRunnableMock, never()).run();
        verify(devicePolicyController).wipeDevice();
    }

    @Test
    public void handleException_whenProvisionSucceededRestrictionsNotCleared_shouldWipeNoTerminate()
            throws ExecutionException, InterruptedException {
        WorkManagerExceptionHandler workManagerExceptionHandler =
                new WorkManagerExceptionHandler(mTestApp, mTerminationRunnableMock);
        ProvisionStateController provisionStateController = mTestApp.getProvisionStateController();
        DeviceStateController deviceStateController = mTestApp.getDeviceStateController();
        DevicePolicyController devicePolicyController = mTestApp.getPolicyController();

        when(provisionStateController.getState())
                .thenReturn(Futures.immediateFuture(PROVISION_SUCCEEDED));
        when(deviceStateController.isCleared()).thenReturn(Futures.immediateFuture(false));

        workManagerExceptionHandler.handleException(mTestApp, new RuntimeException()).get();

        verify(mTerminationRunnableMock, never()).run();
        verify(devicePolicyController).wipeDevice();
    }

    @Test
    public void handleException_whenProvisionSucceededRestrictionsCleared_shouldntWipe()
            throws ExecutionException, InterruptedException {
        WorkManagerExceptionHandler workManagerExceptionHandler =
                new WorkManagerExceptionHandler(mTestApp, mTerminationRunnableMock);
        ProvisionStateController provisionStateController = mTestApp.getProvisionStateController();
        DeviceStateController deviceStateController = mTestApp.getDeviceStateController();
        DevicePolicyController devicePolicyController = mTestApp.getPolicyController();

        when(provisionStateController.getState())
                .thenReturn(Futures.immediateFuture(PROVISION_SUCCEEDED));
        when(deviceStateController.isCleared()).thenReturn(Futures.immediateFuture(true));

        workManagerExceptionHandler.handleException(mTestApp, new RuntimeException()).get();

        verify(devicePolicyController, never()).wipeDevice();
    }
}
