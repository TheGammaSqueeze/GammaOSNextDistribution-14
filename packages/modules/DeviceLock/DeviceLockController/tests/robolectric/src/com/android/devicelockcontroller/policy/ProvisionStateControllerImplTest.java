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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.pm.PackageManager;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;

import androidx.test.core.app.ApplicationProvider;
import androidx.work.testing.WorkManagerTestInitHelper;

import com.android.devicelockcontroller.TestDeviceLockControllerApplication;
import com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionEvent;
import com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionState;
import com.android.devicelockcontroller.policy.ProvisionStateControllerImpl.StateTransitionException;
import com.android.devicelockcontroller.receivers.LockedBootCompletedReceiver;
import com.android.devicelockcontroller.stats.StatsLogger;
import com.android.devicelockcontroller.storage.GlobalParametersClient;
import com.android.devicelockcontroller.storage.UserParameters;

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
public final class ProvisionStateControllerImplTest {
    @Rule
    public MockitoRule mMockitoRule = MockitoJUnit.rule();

    @Mock
    private DevicePolicyController mMockPolicyController;
    @Mock
    private DeviceStateController mMockDeviceStateController;

    private TestDeviceLockControllerApplication mTestApp;
    private ProvisionStateController mProvisionStateController;
    private StatsLogger mStatsLogger;

    @Before
    public void setUp() {
        mTestApp = ApplicationProvider.getApplicationContext();
        WorkManagerTestInitHelper.initializeTestWorkManager(mTestApp);
        mProvisionStateController = new ProvisionStateControllerImpl(mTestApp,
                mMockPolicyController, mMockDeviceStateController,
                Executors.newSingleThreadExecutor());
        mStatsLogger = mTestApp.getStatsLogger();
    }

    @Test
    public void getState_shouldReturnDefaultProvisionState()
            throws ExecutionException, InterruptedException {
        assertThat(mProvisionStateController.getState().get()).isEqualTo(
                ProvisionState.UNPROVISIONED);
    }

    @Test
    public void postSetNextStateForEventRequest_shouldReturnExpectedProvisionState()
            throws ExecutionException, InterruptedException {
        when(mMockPolicyController.enforceCurrentPolicies()).thenReturn(
                Futures.immediateVoidFuture());

        mProvisionStateController.postSetNextStateForEventRequest(ProvisionEvent.PROVISION_READY);

        shadowOf(Looper.getMainLooper()).idle();
        assertThat(mProvisionStateController.getState().get()).isEqualTo(
                ProvisionState.PROVISION_IN_PROGRESS);
    }

    @Test
    public void setNextStateForEvent_shouldSetExpectedNextProvisionState()
            throws ExecutionException, InterruptedException {
        when(mMockPolicyController.enforceCurrentPolicies()).thenReturn(
                Futures.immediateVoidFuture());

        ComponentName lockedBootCompletedReceiver =
                new ComponentName(mTestApp, LockedBootCompletedReceiver.class);
        PackageManager packageManager = mTestApp.getPackageManager();
        assertThat(packageManager.getComponentEnabledSetting(lockedBootCompletedReceiver))
                .isEqualTo(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT);

        // Successful call to this method should put the provisioning state in progress.
        mProvisionStateController.setNextStateForEvent(ProvisionEvent.PROVISION_READY).get();

        assertThat(mProvisionStateController.getState().get()).isEqualTo(
                ProvisionState.PROVISION_IN_PROGRESS);

        // LockedBootCompletedReceiver should be enabled for provision in progress state
        assertThat(packageManager.getComponentEnabledSetting(lockedBootCompletedReceiver))
                .isEqualTo(PackageManager.COMPONENT_ENABLED_STATE_ENABLED);

        // Now transition from provision progress to pause state.
        mProvisionStateController.setNextStateForEvent(ProvisionEvent.PROVISION_PAUSE).get();

        shadowOf(Looper.getMainLooper()).idle();

        assertThat(mProvisionStateController.getState().get()).isEqualTo(
                ProvisionState.PROVISION_PAUSED);

        // Two times invocation of enforceCurrentPolicies method is expected because we are calling
        // setNextStateForEvent twice.
        verify(mMockPolicyController, times(2)).enforceCurrentPolicies();
    }

    @Test
    public void setNextStateForEvent_shouldWriteStartTimeToUserParameters_whenProvisonReady()
            throws ExecutionException, InterruptedException {
        when(mMockPolicyController.enforceCurrentPolicies()).thenReturn(
                Futures.immediateVoidFuture());

        mProvisionStateController.setNextStateForEvent(ProvisionEvent.PROVISION_READY).get();

        assertThat(UserParameters.getProvisioningStartTimeMillis(mTestApp))
                .isEqualTo(SystemClock.elapsedRealtime());
    }

    @Test
    public void setNextStateForEvent_shouldLogSuccessfulProvisioning_whenProvisionSuccess()
            throws ExecutionException, InterruptedException {
        when(mMockPolicyController.enforceCurrentPolicies()).thenReturn(
                Futures.immediateVoidFuture());
        UserParameters.setProvisionState(mTestApp, ProvisionState.KIOSK_PROVISIONED);

        mProvisionStateController.setNextStateForEvent(ProvisionEvent.PROVISION_SUCCESS).get();

        verify(mStatsLogger).logSuccessfulProvisioning();
    }

    @Test
    public void setNextStateForEvent_withException_shouldRetainProvisionState()
            throws ExecutionException, InterruptedException {
        when(mMockPolicyController.enforceCurrentPolicies()).thenReturn(
                Futures.immediateVoidFuture());

        assertThat(mProvisionStateController.getState().get()).isEqualTo(
                ProvisionState.UNPROVISIONED);

        ExecutionException thrown = assertThrows(ExecutionException.class,
                () -> mProvisionStateController.setNextStateForEvent(
                        ProvisionEvent.PROVISION_PAUSE).get());

        shadowOf(Looper.getMainLooper()).idle();
        assertThat(thrown).hasCauseThat().isInstanceOf(StateTransitionException.class);
        assertThat(thrown).hasMessageThat().contains("Can not handle event: ");

        assertThat(mProvisionStateController.getState().get()).isEqualTo(
                ProvisionState.UNPROVISIONED);
        verify(mMockPolicyController, never()).enforceCurrentPolicies();
    }

    @Test
    public void setNextStateForEvent_withException_shouldHandlePolicyEnforcementFailure()
            throws ExecutionException, InterruptedException {
        when(mMockPolicyController.enforceCurrentPolicies()).thenReturn(
                Futures.immediateVoidFuture());
        when(mMockPolicyController.enforceCurrentPoliciesForCriticalFailure()).thenReturn(
                Futures.immediateVoidFuture());

        mProvisionStateController.setNextStateForEvent(ProvisionEvent.PROVISION_READY).get();
        assertThat(mProvisionStateController.getState().get()).isEqualTo(
                ProvisionState.PROVISION_IN_PROGRESS);

        // Simulate exception in enforceCurrentPolicies call
        when(mMockPolicyController.enforceCurrentPolicies()).thenReturn(
                Futures.immediateFailedFuture(new Exception()));

        assertThrows(Exception.class,
                () -> mProvisionStateController.setNextStateForEvent(
                        ProvisionEvent.PROVISION_PAUSE).get());
        shadowOf(Looper.getMainLooper()).idle();

        assertThat(mProvisionStateController.getState().get()).isEqualTo(
                ProvisionState.PROVISION_IN_PROGRESS);

        verify(mMockPolicyController).enforceCurrentPoliciesForCriticalFailure();
    }

    @Test
    public void notifyProvisioningReady_whenSetupIsNotComplete_shouldNotGoToProvisionInProgress()
            throws ExecutionException, InterruptedException {
        when(mMockPolicyController.enforceCurrentPolicies()).thenReturn(
                Futures.immediateVoidFuture());

        // Device setup is not complete
        mProvisionStateController.notifyProvisioningReady();

        shadowOf(Looper.getMainLooper()).idle();
        assertThat(mProvisionStateController.getState().get()).isEqualTo(
                ProvisionState.UNPROVISIONED);
    }

    @Test
    public void notifyProvisioningReady_whenSetupIsComplete_shouldSetExpectedProvisionState()
            throws ExecutionException, InterruptedException {
        when(mMockPolicyController.enforceCurrentPolicies()).thenReturn(
                Futures.immediateVoidFuture());
        // Device setup is complete
        ContentResolver contentResolver = mTestApp.getContentResolver();
        Settings.Secure.putInt(contentResolver, Settings.Secure.USER_SETUP_COMPLETE, 1);

        mProvisionStateController.notifyProvisioningReady();

        shadowOf(Looper.getMainLooper()).idle();
        assertThat(mProvisionStateController.getState().get()).isEqualTo(
                ProvisionState.PROVISION_IN_PROGRESS);
    }

    @Test
    public void onUserUnlocked_shouldSetExpectedProvisionState()
            throws ExecutionException, InterruptedException {
        when(mMockPolicyController.enforceCurrentPolicies()).thenReturn(
                Futures.immediateVoidFuture());

        mProvisionStateController.onUserUnlocked().get();

        shadowOf(Looper.getMainLooper()).idle();
        assertThat(mProvisionStateController.getState().get()).isEqualTo(
                ProvisionState.UNPROVISIONED);
    }

    @Test
    public void onUserUnlocked_withProvisionReady_shouldSetExpectedProvisionState()
            throws ExecutionException, InterruptedException {
        when(mMockPolicyController.enforceCurrentPolicies()).thenReturn(
                Futures.immediateVoidFuture());

        GlobalParametersClient.getInstance().setProvisionReady(true).get();
        ContentResolver contentResolver = mTestApp.getContentResolver();
        Settings.Secure.putInt(contentResolver, Settings.Secure.USER_SETUP_COMPLETE, 1);

        mProvisionStateController.onUserUnlocked().get();

        shadowOf(Looper.getMainLooper()).idle();
        assertThat(mProvisionStateController.getState().get()).isEqualTo(
                ProvisionState.PROVISION_IN_PROGRESS);
    }

    @Test
    public void onUserUnlocked_withProvisionPaused_shouldSetExpectedProvisionState()
            throws ExecutionException, InterruptedException {
        when(mMockPolicyController.enforceCurrentPolicies()).thenReturn(
                Futures.immediateVoidFuture());
        UserParameters.setProvisionState(mTestApp, ProvisionState.PROVISION_PAUSED);

        mProvisionStateController.onUserUnlocked().get();

        shadowOf(Looper.getMainLooper()).idle();
        assertThat(mProvisionStateController.getState().get()).isEqualTo(
                ProvisionState.PROVISION_PAUSED);
    }

    @Test
    public void onUserSetupCompleted_withProvisionReady_shouldGoToProvisionInProgress()
            throws ExecutionException, InterruptedException {
        when(mMockPolicyController.enforceCurrentPolicies()).thenReturn(
                Futures.immediateVoidFuture());
        GlobalParametersClient.getInstance().setProvisionReady(true).get();
        // Device setup is complete
        ContentResolver contentResolver = mTestApp.getContentResolver();
        Settings.Secure.putInt(contentResolver, Settings.Secure.USER_SETUP_COMPLETE, 1);

        mProvisionStateController.onUserSetupCompleted().get();

        shadowOf(Looper.getMainLooper()).idle();
        assertThat(mProvisionStateController.getState().get()).isEqualTo(
                ProvisionState.PROVISION_IN_PROGRESS);
    }

    @Test
    public void getDeviceStateController_shouldReturnExpectedDeviceStateController() {
        assertThat(mProvisionStateController.getDeviceStateController()).isEqualTo(
                mMockDeviceStateController);
    }

    @Test
    public void getDevicePolicyController_shouldReturnExpectedDevicePolicyController() {
        assertThat(mProvisionStateController.getDevicePolicyController()).isEqualTo(
                mMockPolicyController);
    }
}
