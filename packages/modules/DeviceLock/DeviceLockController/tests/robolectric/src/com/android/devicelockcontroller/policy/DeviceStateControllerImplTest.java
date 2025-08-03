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

package com.android.devicelockcontroller.policy;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.android.devicelockcontroller.policy.DeviceStateController.DeviceState;
import com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionEvent;
import com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionState;
import com.android.devicelockcontroller.storage.GlobalParametersClient;

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
import java.util.concurrent.Executors;

@RunWith(RobolectricTestRunner.class)
public final class DeviceStateControllerImplTest {
    private static final String USER_HAS_NOT_BEEN_PROVISIONED = "User has not been provisioned!";
    private static final String DEVICE_HAS_BEEN_CLEARED = "Device has been cleared!";
    @Rule
    public MockitoRule mMockitoRule = MockitoJUnit.rule();

    @Mock
    private DevicePolicyController mMockDevicePolicyController;

    @Mock
    private ProvisionStateController mMockProvisionStateController;

    private DeviceStateController mDeviceStateController;

    @Before
    public void setUp() {
        mDeviceStateController = new DeviceStateControllerImpl(mMockDevicePolicyController,
                mMockProvisionStateController, Executors.newSingleThreadExecutor());
    }

    @Test
    public void lockDevice_withUnprovisionedState_shouldPseudoLockDevice()
            throws ExecutionException, InterruptedException {
        when(mMockProvisionStateController.getState()).thenReturn(
                Futures.immediateFuture(ProvisionState.UNPROVISIONED));
        mDeviceStateController.lockDevice().get();

        assertThat(mDeviceStateController.isLocked().get()).isTrue();

        // Should not have changed the real device state
        assertThat(GlobalParametersClient.getInstance().getDeviceState().get()).isEqualTo(
                DeviceState.UNDEFINED);
    }

    @Test
    public void lockDevice_withProvisionInProgressState_shouldThrowException()
            throws ExecutionException, InterruptedException {
        when(mMockProvisionStateController.getState()).thenReturn(
                Futures.immediateFuture(ProvisionState.PROVISION_IN_PROGRESS));

        ExecutionException thrown = assertThrows(ExecutionException.class,
                () -> mDeviceStateController.lockDevice().get());
        assertThat(thrown).hasCauseThat().isInstanceOf(RuntimeException.class);
        assertThat(thrown).hasMessageThat().contains(USER_HAS_NOT_BEEN_PROVISIONED);

        assertThat(GlobalParametersClient.getInstance().getDeviceState().get()).isEqualTo(
                DeviceState.UNDEFINED);
    }

    @Test
    public void lockDevice_withProvisionPausedState_shouldThrowException()
            throws ExecutionException, InterruptedException {
        when(mMockProvisionStateController.getState()).thenReturn(
                Futures.immediateFuture(ProvisionState.PROVISION_PAUSED));

        ExecutionException thrown = assertThrows(ExecutionException.class,
                () -> mDeviceStateController.lockDevice().get());
        assertThat(thrown).hasCauseThat().isInstanceOf(RuntimeException.class);
        assertThat(thrown).hasMessageThat().contains(USER_HAS_NOT_BEEN_PROVISIONED);

        assertThat(GlobalParametersClient.getInstance().getDeviceState().get()).isEqualTo(
                DeviceState.UNDEFINED);
    }

    @Test
    public void lockDevice_withProvisionFailedState_shouldThrowException()
            throws ExecutionException, InterruptedException {
        when(mMockProvisionStateController.getState()).thenReturn(
                Futures.immediateFuture(ProvisionState.PROVISION_FAILED));

        ExecutionException thrown = assertThrows(ExecutionException.class,
                () -> mDeviceStateController.lockDevice().get());
        assertThat(thrown).hasCauseThat().isInstanceOf(RuntimeException.class);
        assertThat(thrown).hasMessageThat().contains(USER_HAS_NOT_BEEN_PROVISIONED);

        assertThat(GlobalParametersClient.getInstance().getDeviceState().get()).isEqualTo(
                DeviceState.UNDEFINED);
    }

    @Test
    public void lockDevice_withKioskProvisionedState_shouldLockDevice()
            throws ExecutionException, InterruptedException {
        when(mMockProvisionStateController.getState()).thenReturn(
                Futures.immediateFuture(ProvisionState.KIOSK_PROVISIONED));
        when(mMockDevicePolicyController.enforceCurrentPolicies()).thenReturn(
                Futures.immediateVoidFuture());
        when(mMockProvisionStateController.setNextStateForEvent(
                eq(ProvisionEvent.PROVISION_SUCCESS))).thenReturn(
                Futures.immediateVoidFuture());
        mDeviceStateController.lockDevice().get();

        assertThat(GlobalParametersClient.getInstance().getDeviceState().get()).isEqualTo(
                DeviceState.LOCKED);
    }

    @Test
    public void lockDevice_withClearDeviceState_shouldThrowException()
            throws ExecutionException, InterruptedException {
        when(mMockProvisionStateController.getState()).thenReturn(
                Futures.immediateFuture(ProvisionState.PROVISION_SUCCEEDED));
        GlobalParametersClient globalParametersClient = GlobalParametersClient.getInstance();
        globalParametersClient.setDeviceState(DeviceState.CLEARED).get();
        ExecutionException thrown = assertThrows(ExecutionException.class,
                () -> mDeviceStateController.lockDevice().get());
        assertThat(thrown).hasCauseThat().isInstanceOf(RuntimeException.class);
        assertThat(thrown).hasMessageThat().contains(DEVICE_HAS_BEEN_CLEARED);
        assertThat(mDeviceStateController.isLocked().get()).isFalse();
    }

    @Test
    public void lockDevice_withProvisionSucceededState_shouldLockDevice()
            throws ExecutionException, InterruptedException {
        when(mMockProvisionStateController.getState()).thenReturn(
                Futures.immediateFuture(ProvisionState.PROVISION_SUCCEEDED));
        when(mMockDevicePolicyController.enforceCurrentPolicies()).thenReturn(
                Futures.immediateVoidFuture());
        mDeviceStateController.lockDevice().get();

        assertThat(GlobalParametersClient.getInstance().getDeviceState().get()).isEqualTo(
                DeviceState.LOCKED);
        assertThat(mDeviceStateController.isLocked().get()).isTrue();
    }

    @Test
    public void unlockDevice_withKioskProvisionedState_shouldUnlockDevice()
            throws ExecutionException, InterruptedException {
        when(mMockProvisionStateController.getState()).thenReturn(
                Futures.immediateFuture(ProvisionState.KIOSK_PROVISIONED));
        when(mMockDevicePolicyController.enforceCurrentPolicies()).thenReturn(
                Futures.immediateVoidFuture());
        when(mMockProvisionStateController.setNextStateForEvent(
                eq(ProvisionEvent.PROVISION_SUCCESS))).thenReturn(
                Futures.immediateVoidFuture());
        mDeviceStateController.unlockDevice().get();

        assertThat(GlobalParametersClient.getInstance().getDeviceState().get()).isEqualTo(
                DeviceState.UNLOCKED);
    }

    @Test
    public void unlockDevice_withUnprovisionedState_shouldPseudoUnlock()
            throws ExecutionException, InterruptedException {
        when(mMockProvisionStateController.getState()).thenReturn(
                Futures.immediateFuture(ProvisionState.UNPROVISIONED));
        mDeviceStateController.unlockDevice().get();

        assertThat(mDeviceStateController.isLocked().get()).isFalse();

        // Should not have changed the real device state
        assertThat(GlobalParametersClient.getInstance().getDeviceState().get()).isEqualTo(
                DeviceState.UNDEFINED);
    }

    @Test
    public void unlockDevice_withProvisionInProgressState_shouldThrowException()
            throws ExecutionException, InterruptedException {
        when(mMockProvisionStateController.getState()).thenReturn(
                Futures.immediateFuture(ProvisionState.PROVISION_IN_PROGRESS));

        ExecutionException thrown = assertThrows(ExecutionException.class,
                () -> mDeviceStateController.unlockDevice().get());
        assertThat(thrown).hasCauseThat().isInstanceOf(RuntimeException.class);
        assertThat(thrown).hasMessageThat().contains(USER_HAS_NOT_BEEN_PROVISIONED);

        assertThat(GlobalParametersClient.getInstance().getDeviceState().get()).isEqualTo(
                DeviceState.UNDEFINED);
    }

    @Test
    public void unlockDevice_withProvisionPausedState_shouldThrowException()
            throws ExecutionException, InterruptedException {
        when(mMockProvisionStateController.getState()).thenReturn(
                Futures.immediateFuture(ProvisionState.PROVISION_PAUSED));

        ExecutionException thrown = assertThrows(ExecutionException.class,
                () -> mDeviceStateController.unlockDevice().get());
        assertThat(thrown).hasCauseThat().isInstanceOf(RuntimeException.class);
        assertThat(thrown).hasMessageThat().contains(USER_HAS_NOT_BEEN_PROVISIONED);

        assertThat(GlobalParametersClient.getInstance().getDeviceState().get()).isEqualTo(
                DeviceState.UNDEFINED);
    }

    @Test
    public void unlockDevice_withProvisionFailedState_shouldThrowException()
            throws ExecutionException, InterruptedException {
        when(mMockProvisionStateController.getState()).thenReturn(
                Futures.immediateFuture(ProvisionState.PROVISION_FAILED));

        ExecutionException thrown = assertThrows(ExecutionException.class,
                () -> mDeviceStateController.unlockDevice().get());
        assertThat(thrown).hasCauseThat().isInstanceOf(RuntimeException.class);
        assertThat(thrown).hasMessageThat().contains(USER_HAS_NOT_BEEN_PROVISIONED);

        assertThat(GlobalParametersClient.getInstance().getDeviceState().get()).isEqualTo(
                DeviceState.UNDEFINED);
    }

    @Test
    public void unlockDevice_withClearDeviceState_shouldThrowException()
            throws ExecutionException, InterruptedException {
        when(mMockProvisionStateController.getState()).thenReturn(
                Futures.immediateFuture(ProvisionState.PROVISION_SUCCEEDED));
        GlobalParametersClient globalParametersClient = GlobalParametersClient.getInstance();
        globalParametersClient.setDeviceState(DeviceState.CLEARED).get();
        ExecutionException thrown = assertThrows(ExecutionException.class,
                () -> mDeviceStateController.unlockDevice().get());
        assertThat(thrown).hasCauseThat().isInstanceOf(RuntimeException.class);
        assertThat(thrown).hasMessageThat().contains(DEVICE_HAS_BEEN_CLEARED);
    }

    @Test
    public void unlockDevice_withProvisionSucceeded_shouldUnlockDevice()
            throws ExecutionException, InterruptedException {
        when(mMockProvisionStateController.getState()).thenReturn(
                Futures.immediateFuture(ProvisionState.PROVISION_SUCCEEDED));
        when(mMockDevicePolicyController.enforceCurrentPolicies()).thenReturn(
                Futures.immediateVoidFuture());
        mDeviceStateController.unlockDevice().get();

        assertThat(GlobalParametersClient.getInstance().getDeviceState().get()).isEqualTo(
                DeviceState.UNLOCKED);
        assertThat(mDeviceStateController.isLocked().get()).isFalse();
    }

    @Test
    public void clearDevice_withUnprovisionedState_shouldThrowException()
            throws ExecutionException, InterruptedException {
        when(mMockProvisionStateController.getState()).thenReturn(
                Futures.immediateFuture(ProvisionState.UNPROVISIONED));

        ExecutionException thrown = assertThrows(ExecutionException.class,
                () -> mDeviceStateController.clearDevice().get());
        assertThat(thrown).hasCauseThat().isInstanceOf(RuntimeException.class);
        assertThat(thrown).hasMessageThat().contains(USER_HAS_NOT_BEEN_PROVISIONED);

        assertThat(GlobalParametersClient.getInstance().getDeviceState().get()).isEqualTo(
                DeviceState.UNDEFINED);
    }

    @Test
    public void clearDevice_withProvisionInProgressState_shouldThrowException()
            throws ExecutionException, InterruptedException {
        when(mMockProvisionStateController.getState()).thenReturn(
                Futures.immediateFuture(ProvisionState.PROVISION_IN_PROGRESS));

        ExecutionException thrown = assertThrows(ExecutionException.class,
                () -> mDeviceStateController.clearDevice().get());
        assertThat(thrown).hasCauseThat().isInstanceOf(RuntimeException.class);
        assertThat(thrown).hasMessageThat().contains(USER_HAS_NOT_BEEN_PROVISIONED);

        assertThat(GlobalParametersClient.getInstance().getDeviceState().get()).isEqualTo(
                DeviceState.UNDEFINED);
    }

    @Test
    public void clearDevice_withProvisionPausedState_shouldThrowException()
            throws ExecutionException, InterruptedException {
        when(mMockProvisionStateController.getState()).thenReturn(
                Futures.immediateFuture(ProvisionState.PROVISION_PAUSED));

        ExecutionException thrown = assertThrows(ExecutionException.class,
                () -> mDeviceStateController.clearDevice().get());
        assertThat(thrown).hasCauseThat().isInstanceOf(RuntimeException.class);
        assertThat(thrown).hasMessageThat().contains(USER_HAS_NOT_BEEN_PROVISIONED);

        assertThat(GlobalParametersClient.getInstance().getDeviceState().get()).isEqualTo(
                DeviceState.UNDEFINED);
    }

    @Test
    public void clearDevice_withProvisionFailedState_shouldThrowException()
            throws ExecutionException, InterruptedException {
        when(mMockProvisionStateController.getState()).thenReturn(
                Futures.immediateFuture(ProvisionState.PROVISION_FAILED));

        ExecutionException thrown = assertThrows(ExecutionException.class,
                () -> mDeviceStateController.clearDevice().get());
        assertThat(thrown).hasCauseThat().isInstanceOf(RuntimeException.class);
        assertThat(thrown).hasMessageThat().contains(USER_HAS_NOT_BEEN_PROVISIONED);

        assertThat(GlobalParametersClient.getInstance().getDeviceState().get()).isEqualTo(
                DeviceState.UNDEFINED);
    }

    @Test
    public void clearDevice_withKioskProvisionedState_shouldClearDevice()
            throws ExecutionException, InterruptedException {
        when(mMockProvisionStateController.getState()).thenReturn(
                Futures.immediateFuture(ProvisionState.KIOSK_PROVISIONED));
        when(mMockDevicePolicyController.enforceCurrentPolicies()).thenReturn(
                Futures.immediateVoidFuture());
        when(mMockProvisionStateController.setNextStateForEvent(
                eq(ProvisionEvent.PROVISION_SUCCESS))).thenReturn(
                Futures.immediateVoidFuture());
        mDeviceStateController.clearDevice().get();

        assertThat(GlobalParametersClient.getInstance().getDeviceState().get()).isEqualTo(
                DeviceState.CLEARED);
    }

    @Test
    public void clearDevice_withClearDeviceState_shouldThrowException()
            throws ExecutionException, InterruptedException {
        when(mMockProvisionStateController.getState()).thenReturn(
                Futures.immediateFuture(ProvisionState.PROVISION_SUCCEEDED));
        GlobalParametersClient globalParametersClient = GlobalParametersClient.getInstance();
        globalParametersClient.setDeviceState(DeviceState.CLEARED).get();
        ExecutionException thrown = assertThrows(ExecutionException.class,
                () -> mDeviceStateController.clearDevice().get());
        assertThat(thrown).hasCauseThat().isInstanceOf(RuntimeException.class);
        assertThat(thrown).hasMessageThat().contains(DEVICE_HAS_BEEN_CLEARED);
    }

    @Test
    public void clearDevice_withProvisionSucceeded_shouldUnlockDevice()
            throws ExecutionException, InterruptedException {
        when(mMockProvisionStateController.getState()).thenReturn(
                Futures.immediateFuture(ProvisionState.PROVISION_SUCCEEDED));
        when(mMockDevicePolicyController.enforceCurrentPolicies()).thenReturn(
                Futures.immediateVoidFuture());
        mDeviceStateController.clearDevice().get();

        assertThat(GlobalParametersClient.getInstance().getDeviceState().get()).isEqualTo(
                DeviceState.CLEARED);
    }

    @Test
    public void getDeviceState_shouldReturnResultFromGlobalParametersClient()
            throws ExecutionException, InterruptedException {
        GlobalParametersClient globalParametersClient = GlobalParametersClient.getInstance();
        globalParametersClient.setDeviceState(DeviceState.UNLOCKED).get();

        int deviceState = mDeviceStateController.getDeviceState().get();

        assertThat(deviceState).isEqualTo(DeviceState.UNLOCKED);
    }
}
