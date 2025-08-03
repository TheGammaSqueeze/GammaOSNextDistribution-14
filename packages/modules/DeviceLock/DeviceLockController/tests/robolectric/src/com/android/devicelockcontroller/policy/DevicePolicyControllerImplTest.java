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

import static com.android.devicelockcontroller.activities.ProvisioningActivity.EXTRA_SHOW_CRITICAL_PROVISION_FAILED_UI_ON_START;
import static com.android.devicelockcontroller.common.DeviceLockConstants.ACTION_START_DEVICE_FINANCING_PROVISIONING;
import static com.android.devicelockcontroller.common.DeviceLockConstants.ACTION_START_DEVICE_SUBSIDY_PROVISIONING;
import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_KIOSK_PACKAGE;
import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_PROVISIONING_TYPE;
import static com.android.devicelockcontroller.common.DeviceLockConstants.ProvisioningType.TYPE_FINANCED;
import static com.android.devicelockcontroller.common.DeviceLockConstants.ProvisioningType.TYPE_SUBSIDY;
import static com.android.devicelockcontroller.common.DeviceLockConstants.ProvisioningType.TYPE_UNDEFINED;
import static com.android.devicelockcontroller.policy.DevicePolicyControllerImpl.ACTION_DEVICE_LOCK_KIOSK_SETUP;
import static com.android.devicelockcontroller.policy.DeviceStateController.DeviceState.CLEARED;
import static com.android.devicelockcontroller.policy.DeviceStateController.DeviceState.LOCKED;
import static com.android.devicelockcontroller.policy.DeviceStateController.DeviceState.UNDEFINED;
import static com.android.devicelockcontroller.policy.DeviceStateController.DeviceState.UNLOCKED;
import static com.android.devicelockcontroller.policy.StartLockTaskModeWorker.START_LOCK_TASK_MODE_WORK_NAME;
import static com.android.devicelockcontroller.provision.worker.ReportDeviceProvisionStateWorker.REPORT_PROVISION_STATE_WORK_NAME;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.os.Looper;
import android.os.OutcomeReceiver;
import android.os.UserManager;

import androidx.test.core.app.ApplicationProvider;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.testing.WorkManagerTestInitHelper;

import com.android.devicelockcontroller.SystemDeviceLockManager;
import com.android.devicelockcontroller.TestDeviceLockControllerApplication;
import com.android.devicelockcontroller.activities.LandingActivity;
import com.android.devicelockcontroller.activities.ProvisioningActivity;
import com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionState;
import com.android.devicelockcontroller.storage.GlobalParametersClient;
import com.android.devicelockcontroller.storage.SetupParametersClient;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.stubbing.Answer;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowPackageManager;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RunWith(RobolectricTestRunner.class)
public final class DevicePolicyControllerImplTest {
    private static final String TEST_KIOSK_PACKAGE = "test.package1";
    private static final String TEST_KIOSK_ACTIVITY = "TestActivity";
    @Rule
    public MockitoRule mMockitoRule = MockitoJUnit.rule();

    @Mock
    private ProvisionStateController mMockProvisionStateController;
    @Mock
    private SystemDeviceLockManager mMockSystemDeviceLockManager;
    @Mock
    private DevicePolicyManager mMockDpm;
    @Mock
    private UserManager mMockUserManager;
    @Captor
    private ArgumentCaptor<Integer> mAllowedFlags;

    private DevicePolicyController mDevicePolicyController;
    private TestDeviceLockControllerApplication mTestApp;

    @Before
    public void setUp() {
        mTestApp = ApplicationProvider.getApplicationContext();
        WorkManagerTestInitHelper.initializeTestWorkManager(mTestApp);
        ExecutorService bgExecutor = Executors.newSingleThreadExecutor();
        Bundle userRestrictions = new Bundle();
        when(mMockUserManager.getUserRestrictions()).thenReturn(userRestrictions);

        UserRestrictionsPolicyHandler userRestrictionsPolicyHandler =
                new UserRestrictionsPolicyHandler(mMockDpm,
                        mMockUserManager, /* isDebug =*/ false, bgExecutor);
        AppOpsPolicyHandler appOpsPolicyHandler = new AppOpsPolicyHandler(
                mMockSystemDeviceLockManager, bgExecutor);
        LockTaskModePolicyHandler lockTaskModePolicyHandler = new LockTaskModePolicyHandler(
                mTestApp, mMockDpm, bgExecutor);
        PackagePolicyHandler packagePolicyHandler = new PackagePolicyHandler(mTestApp, mMockDpm,
                bgExecutor);
        RolePolicyHandler rolePolicyHandler = new RolePolicyHandler(mMockSystemDeviceLockManager,
                bgExecutor);
        KioskKeepAlivePolicyHandler kioskKeepAlivePolicyHandler = new KioskKeepAlivePolicyHandler(
                mMockSystemDeviceLockManager,
                bgExecutor);
        ControllerKeepAlivePolicyHandler controllerKeepAlivePolicyHandler =
                new ControllerKeepAlivePolicyHandler(
                        mMockSystemDeviceLockManager,
                        bgExecutor);
        NotificationsPolicyHandler notificationsPolicyHandler =
                new NotificationsPolicyHandler(mMockSystemDeviceLockManager, bgExecutor);
        mDevicePolicyController =
                new DevicePolicyControllerImpl(mTestApp,
                        mMockDpm,
                        mMockUserManager,
                        userRestrictionsPolicyHandler,
                        appOpsPolicyHandler,
                        lockTaskModePolicyHandler,
                        packagePolicyHandler,
                        rolePolicyHandler,
                        kioskKeepAlivePolicyHandler,
                        controllerKeepAlivePolicyHandler,
                        notificationsPolicyHandler,
                        mMockProvisionStateController,
                        bgExecutor);
    }

    @Test
    public void wipeDevice_shouldMakeExpectedCalls() {
        assertThat(mDevicePolicyController.wipeDevice()).isTrue();
        verify(mMockDpm).wipeDevice(mAllowedFlags.capture());
        assertThat(mAllowedFlags.getValue()).isEqualTo(DevicePolicyManager.WIPE_SILENTLY
                | DevicePolicyManager.WIPE_RESET_PROTECTION_DATA);
    }

    @Test
    public void wipeDevice_withSecurityException_handleException() {
        doThrow(new SecurityException()).when(mMockDpm).wipeDevice(
                eq(DevicePolicyManager.WIPE_SILENTLY
                        | DevicePolicyManager.WIPE_RESET_PROTECTION_DATA));
        assertThat(mDevicePolicyController.wipeDevice()).isFalse();
    }

    @Test
    public void enforceCurrentPolicies_withUnprovisionedState_doesNotStartLockTaskMode()
            throws Exception {
        setupSetupParameters();
        when(mMockProvisionStateController.getState()).thenReturn(Futures.immediateFuture(
                ProvisionState.UNPROVISIONED));
        when(mMockUserManager.isUserUnlocked()).thenReturn(true);

        mDevicePolicyController.enforceCurrentPolicies().get();

        shadowOf(Looper.getMainLooper()).idle();
        assertLockTaskModeNotStarted();
    }

    @Test
    public void enforceCurrentPolicies_withProvisionInProgressState_startsLockTaskMode()
            throws Exception {
        setupSetupParameters();
        setupAppOpsPolicyHandlerExpectations();
        setExpectationsOnEnableControllerKeepAlive();
        setExpectationsOnSetPostNotificationsSystemFixed();
        when(mMockProvisionStateController.getState()).thenReturn(Futures.immediateFuture(
                ProvisionState.PROVISION_IN_PROGRESS));
        when(mMockUserManager.isUserUnlocked()).thenReturn(true);

        mDevicePolicyController.enforceCurrentPolicies().get();

        shadowOf(Looper.getMainLooper()).idle();
        assertLockTaskModeStarted();
    }

    @Test
    public void enforceCurrentPolicies_withKioskProvisionedState_startsLockTaskMode()
            throws Exception {
        setupSetupParameters();
        setupAppOpsPolicyHandlerExpectations();
        setExpectationsOnEnableKioskKeepAlive();
        setExpectationsOnDisableControllerKeepAlive();
        setExpectationsOnAddFinancedDeviceKioskRole();
        when(mMockProvisionStateController.getState()).thenReturn(Futures.immediateFuture(
                ProvisionState.KIOSK_PROVISIONED));
        when(mMockUserManager.isUserUnlocked()).thenReturn(true);

        mDevicePolicyController.enforceCurrentPolicies().get();

        shadowOf(Looper.getMainLooper()).idle();
        assertLockTaskModeStarted();
    }

    @Test
    public void enforceCurrentPolicies_withProvisionPausedState_doesNotStartLockTaskMode()
            throws Exception {
        setupSetupParameters();
        setExpectationsOnDisableControllerKeepAlive();
        when(mMockProvisionStateController.getState()).thenReturn(Futures.immediateFuture(
                ProvisionState.PROVISION_PAUSED));
        when(mMockUserManager.isUserUnlocked()).thenReturn(true);

        mDevicePolicyController.enforceCurrentPolicies().get();

        shadowOf(Looper.getMainLooper()).idle();
        assertLockTaskModeNotStarted();
    }

    @Test
    public void enforceCurrentPolicies_withProvisionPausedAndDeviceLocked_doesNotStartLockTaskMode()
            throws Exception {
        setupSetupParameters();
        setExpectationsOnDisableControllerKeepAlive();
        when(mMockProvisionStateController.getState()).thenReturn(Futures.immediateFuture(
                ProvisionState.PROVISION_PAUSED));
        when(mMockUserManager.isUserUnlocked()).thenReturn(true);
        GlobalParametersClient.getInstance().setDeviceState(LOCKED).get();

        mDevicePolicyController.enforceCurrentPolicies().get();

        shadowOf(Looper.getMainLooper()).idle();
        assertLockTaskModeNotStarted();
    }

    @Test
    public void enforceCurrentPolicies_withProvisionFailedState_doesNotStartLockTaskMode()
            throws Exception {
        setupSetupParameters();
        setupAppOpsPolicyHandlerExpectations();
        when(mMockProvisionStateController.getState()).thenReturn(Futures.immediateFuture(
                ProvisionState.PROVISION_FAILED));
        when(mMockUserManager.isUserUnlocked()).thenReturn(true);

        mDevicePolicyController.enforceCurrentPolicies().get();

        shadowOf(Looper.getMainLooper()).idle();
        assertLockTaskModeNotStarted();
    }

    @Test
    public void enforceCurrentPolicies_withProvisionSucceededState_doesNotStartLockTaskMode()
            throws Exception {
        setupSetupParameters();
        setExpectationsOnDisableControllerKeepAlive();
        when(mMockProvisionStateController.getState()).thenReturn(Futures.immediateFuture(
                ProvisionState.PROVISION_SUCCEEDED));
        when(mMockUserManager.isUserUnlocked()).thenReturn(true);

        mDevicePolicyController.enforceCurrentPolicies().get();

        shadowOf(Looper.getMainLooper()).idle();
        assertLockTaskModeNotStarted();
    }

    @Test
    public void enforceCurrentPolicies_withLockedDeviceState_startsLockTaskMode()
            throws Exception {
        setupSetupParameters();
        setupAppOpsPolicyHandlerExpectations();
        when(mMockProvisionStateController.getState()).thenReturn(Futures.immediateFuture(
                ProvisionState.PROVISION_SUCCEEDED));
        when(mMockUserManager.isUserUnlocked()).thenReturn(true);
        GlobalParametersClient.getInstance().setDeviceState(LOCKED).get();

        mDevicePolicyController.enforceCurrentPolicies().get();

        shadowOf(Looper.getMainLooper()).idle();
        assertLockTaskModeStarted();
    }

    @Test
    public void enforceCurrentPolicies_withUnlockedDeviceState_doesNotStartLockTaskMode()
            throws Exception {
        setupSetupParameters();
        setupAppOpsPolicyHandlerExpectations();
        when(mMockProvisionStateController.getState()).thenReturn(Futures.immediateFuture(
                ProvisionState.PROVISION_SUCCEEDED));
        when(mMockUserManager.isUserUnlocked()).thenReturn(true);
        GlobalParametersClient.getInstance().setDeviceState(UNLOCKED).get();

        mDevicePolicyController.enforceCurrentPolicies().get();

        shadowOf(Looper.getMainLooper()).idle();
        assertLockTaskModeNotStarted();
    }

    @Test
    public void enforceCurrentPolicies_withClearedDeviceState_doesNotStartLockTaskMode()
            throws Exception {
        setupSetupParameters();
        setupAppOpsPolicyHandlerExpectations();
        setExpectationsOnDisableKioskKeepAlive();
        setExpectationsOnDisableControllerKeepAlive();
        setExpectationsOnRemoveFinancedDeviceKioskRole();
        setExpectationsOnSetPostNotificationsSystemFixed();
        setupFinalizationControllerExpectations();
        when(mMockProvisionStateController.getState()).thenReturn(Futures.immediateFuture(
                ProvisionState.PROVISION_SUCCEEDED));
        when(mMockUserManager.isUserUnlocked()).thenReturn(true);
        GlobalParametersClient.getInstance().setDeviceState(CLEARED).get();

        mDevicePolicyController.enforceCurrentPolicies().get();

        shadowOf(Looper.getMainLooper()).idle();
        assertLockTaskModeNotStarted();
    }

    @Test
    public void enforceCurrentPolicies_clearedButProvisionInProgress_doesNotStartLockTaskMode()
            throws Exception {
        setupSetupParameters();
        setupAppOpsPolicyHandlerExpectations();
        setExpectationsOnDisableKioskKeepAlive();
        setExpectationsOnDisableControllerKeepAlive();
        setExpectationsOnRemoveFinancedDeviceKioskRole();
        setExpectationsOnSetPostNotificationsSystemFixed();
        setupFinalizationControllerExpectations();
        when(mMockProvisionStateController.getState()).thenReturn(Futures.immediateFuture(
                ProvisionState.PROVISION_IN_PROGRESS));
        when(mMockUserManager.isUserUnlocked()).thenReturn(true);
        GlobalParametersClient.getInstance().setDeviceState(CLEARED).get();

        mDevicePolicyController.enforceCurrentPolicies().get();

        shadowOf(Looper.getMainLooper()).idle();
        assertLockTaskModeNotStarted();
    }

    @Test
    public void getLaunchIntent_withUnProvisionState_forCriticalFailure_shouldHaveExpectedIntent()
            throws Exception {
        when(mMockUserManager.isUserUnlocked()).thenReturn(true);

        mDevicePolicyController.enforceCurrentPoliciesForCriticalFailure().get();

        shadowOf(Looper.getMainLooper()).idle();
        assertLockTaskModeStarted();
        verify(mTestApp.getDeviceLockControllerScheduler()).scheduleMandatoryResetDeviceAlarm();
        assertReportSetupFailedWorkStarted();

        Intent intent = mDevicePolicyController.getLaunchIntentForCurrentState().get();

        shadowOf(Looper.getMainLooper()).idle();
        verify(mMockProvisionStateController).getState();
        assertCriticalFailureIntent(intent);
    }

    @Test
    public void
            getLaunchIntent_withProvisionPausedState_forCriticalFailure_shouldHaveExpectedIntent()
            throws Exception {
        when(mMockUserManager.isUserUnlocked()).thenReturn(true);
        setExpectationsOnDisableControllerKeepAlive();

        mDevicePolicyController.enforceCurrentPoliciesForCriticalFailure().get();

        shadowOf(Looper.getMainLooper()).idle();
        assertLockTaskModeStarted();
        verify(mTestApp.getDeviceLockControllerScheduler()).scheduleMandatoryResetDeviceAlarm();
        assertReportSetupFailedWorkStarted();

        when(mMockProvisionStateController.getState()).thenReturn(Futures.immediateFuture(
                ProvisionState.PROVISION_PAUSED));
        Intent intent = mDevicePolicyController.getLaunchIntentForCurrentState().get();

        shadowOf(Looper.getMainLooper()).idle();
        assertCriticalFailureIntent(intent);
    }

    @Test
    public void
            getLaunchIntent_withProvisionSucceededSt_forCriticalFailure_shouldHaveExpectedIntent()
            throws Exception {
        when(mMockUserManager.isUserUnlocked()).thenReturn(true);

        mDevicePolicyController.enforceCurrentPoliciesForCriticalFailure().get();

        shadowOf(Looper.getMainLooper()).idle();
        assertLockTaskModeStarted();
        verify(mTestApp.getDeviceLockControllerScheduler()).scheduleMandatoryResetDeviceAlarm();
        assertReportSetupFailedWorkStarted();

        when(mMockProvisionStateController.getState()).thenReturn(Futures.immediateFuture(
                ProvisionState.PROVISION_SUCCEEDED));
        Intent intent = mDevicePolicyController.getLaunchIntentForCurrentState().get();

        shadowOf(Looper.getMainLooper()).idle();
        assertCriticalFailureIntent(intent);
    }

    @Test
    public void
            getLaunchIntent_withKioskProvisionedState_forCriticalFailure_shouldHaveExpectedIntent()
            throws Exception {
        when(mMockUserManager.isUserUnlocked()).thenReturn(true);

        mDevicePolicyController.enforceCurrentPoliciesForCriticalFailure().get();

        shadowOf(Looper.getMainLooper()).idle();
        assertLockTaskModeStarted();
        verify(mTestApp.getDeviceLockControllerScheduler()).scheduleMandatoryResetDeviceAlarm();
        assertReportSetupFailedWorkStarted();

        when(mMockProvisionStateController.getState()).thenReturn(Futures.immediateFuture(
                ProvisionState.KIOSK_PROVISIONED));
        Intent intent = mDevicePolicyController.getLaunchIntentForCurrentState().get();

        shadowOf(Looper.getMainLooper()).idle();
        assertCriticalFailureIntent(intent);
    }

    @Test
    public void
            getLaunchIntent_withProvisionFailedState_forCriticalFailure_shouldHaveExpectedIntent()
            throws Exception {
        when(mMockUserManager.isUserUnlocked()).thenReturn(true);

        mDevicePolicyController.enforceCurrentPoliciesForCriticalFailure().get();

        shadowOf(Looper.getMainLooper()).idle();
        assertLockTaskModeStarted();
        verify(mTestApp.getDeviceLockControllerScheduler()).scheduleMandatoryResetDeviceAlarm();
        assertReportSetupFailedWorkStarted();

        when(mMockProvisionStateController.getState()).thenReturn(Futures.immediateFuture(
                ProvisionState.PROVISION_FAILED));
        Intent intent = mDevicePolicyController.getLaunchIntentForCurrentState().get();

        shadowOf(Looper.getMainLooper()).idle();
        assertCriticalFailureIntent(intent);
    }

    @Test
    public void
            getLaunchIntent_withProvisionInProgressSt_forCriticalFailure_shouldHaveExpectedIntent()
            throws Exception {
        when(mMockUserManager.isUserUnlocked()).thenReturn(true);
        setExpectationsOnEnableControllerKeepAlive();

        mDevicePolicyController.enforceCurrentPoliciesForCriticalFailure().get();

        shadowOf(Looper.getMainLooper()).idle();
        assertLockTaskModeStarted();
        verify(mTestApp.getDeviceLockControllerScheduler()).scheduleMandatoryResetDeviceAlarm();
        assertReportSetupFailedWorkStarted();

        when(mMockProvisionStateController.getState()).thenReturn(Futures.immediateFuture(
                ProvisionState.PROVISION_IN_PROGRESS));
        Intent intent = mDevicePolicyController.getLaunchIntentForCurrentState().get();

        shadowOf(Looper.getMainLooper()).idle();
        assertCriticalFailureIntent(intent);
    }

    @Test
    public void getLaunchIntentForCurrentState_withUnprovisionedState_shouldMakeExpectedCalls()
            throws ExecutionException, InterruptedException {
        when(mMockProvisionStateController.getState()).thenReturn(Futures.immediateFuture(
                ProvisionState.UNPROVISIONED));
        Intent intent = mDevicePolicyController.getLaunchIntentForCurrentState().get();
        assertThat(intent).isNull();
    }

    @Test
    public void getLaunchIntentForCurrentState_withProvisionPausedState_shouldMakeExpectedCalls()
            throws ExecutionException, InterruptedException {
        setExpectationsOnDisableControllerKeepAlive();
        when(mMockProvisionStateController.getState()).thenReturn(Futures.immediateFuture(
                ProvisionState.PROVISION_PAUSED));
        Intent intent = mDevicePolicyController.getLaunchIntentForCurrentState().get();
        assertThat(intent).isNull();
    }

    @Test
    public void getLaunchIntentForCurrentState_withProvisionFailedState_shouldMakeExpectedCalls()
            throws ExecutionException, InterruptedException {
        setupAppOpsPolicyHandlerExpectations();
        when(mMockProvisionStateController.getState()).thenReturn(Futures.immediateFuture(
                ProvisionState.PROVISION_FAILED));
        Intent intent = mDevicePolicyController.getLaunchIntentForCurrentState().get();
        assertThat(intent).isNull();
    }

    @Test
    public void getLaunchIntentForCurrentState_withProvisionSucceededState_shouldMakeExpectedCalls()
            throws ExecutionException, InterruptedException {
        setupAppOpsPolicyHandlerExpectations();
        setExpectationsOnDisableControllerKeepAlive();
        when(mMockProvisionStateController.getState()).thenReturn(Futures.immediateFuture(
                ProvisionState.PROVISION_SUCCEEDED));
        Intent intent = mDevicePolicyController.getLaunchIntentForCurrentState().get();
        assertThat(intent).isNull();
    }

    @Test
    public void
            getLaunchIntentForCurrentState_withProvisionSucceededState_withoutKioskAppInstalled()
            throws ExecutionException, InterruptedException {
        setupSetupParameters();
        setupAppOpsPolicyHandlerExpectations();
        setExpectationsOnDisableControllerKeepAlive();
        GlobalParametersClient.getInstance().setDeviceState(LOCKED).get();
        when(mMockProvisionStateController.getState()).thenReturn(Futures.immediateFuture(
                ProvisionState.PROVISION_SUCCEEDED));

        ExecutionException thrown = assertThrows(ExecutionException.class,
                () -> mDevicePolicyController.getLaunchIntentForCurrentState().get());

        assertThat(thrown).hasCauseThat().isInstanceOf(IllegalStateException.class);
        assertThat(thrown).hasMessageThat().contains("Failed to get launch intent for kiosk app!");
    }

    @Test
    public void getLaunchIntentForCurrentState_withProvisionSucceededStateAndKioskAppInstalled()
            throws ExecutionException, InterruptedException {
        setupSetupParameters();
        setupAppOpsPolicyHandlerExpectations();
        GlobalParametersClient.getInstance().setDeviceState(LOCKED).get();
        installKioskAppWithLockScreenIntentFilter();
        when(mMockProvisionStateController.getState()).thenReturn(Futures.immediateFuture(
                ProvisionState.PROVISION_SUCCEEDED));

        Intent intent = mDevicePolicyController.getLaunchIntentForCurrentState().get();

        shadowOf(Looper.getMainLooper()).idle();
        assertThat(intent).isNotNull();
        assertThat(intent.getComponent().getClassName()).isEqualTo(TEST_KIOSK_ACTIVITY);
        assertThat(intent.getCategories()).containsExactly(Intent.CATEGORY_HOME);
        assertThat(intent.getAction()).isEqualTo(Intent.ACTION_MAIN);
    }

    @Test
    public void
            getLaunchIntentForCurrentState_withProvisionSucceededStateAndKioskAppWithoutHomeCateg()
            throws ExecutionException, InterruptedException {
        setupSetupParameters();
        setupAppOpsPolicyHandlerExpectations();
        GlobalParametersClient.getInstance().setDeviceState(LOCKED).get();
        installKioskAppWithoutCategoryHomeIntentFilter();
        when(mMockProvisionStateController.getState()).thenReturn(Futures.immediateFuture(
                ProvisionState.PROVISION_SUCCEEDED));

        Intent intent = mDevicePolicyController.getLaunchIntentForCurrentState().get();

        shadowOf(Looper.getMainLooper()).idle();
        assertThat(intent).isNotNull();
        assertThat(intent.getComponent().getClassName()).isEqualTo(TEST_KIOSK_ACTIVITY);
        assertThat(intent.getCategories()).containsExactly(Intent.CATEGORY_LAUNCHER);
        assertThat(intent.getAction()).isEqualTo(Intent.ACTION_MAIN);
    }

    @Test
    public void getLaunchIntentForCurrentState_withProvisionSucceededStateAndDeviceStateUnlocked()
            throws ExecutionException, InterruptedException {
        setupSetupParameters();
        setupAppOpsPolicyHandlerExpectations();
        GlobalParametersClient.getInstance().setDeviceState(UNLOCKED).get();
        installKioskAppWithoutCategoryHomeIntentFilter();
        when(mMockProvisionStateController.getState()).thenReturn(Futures.immediateFuture(
                ProvisionState.PROVISION_SUCCEEDED));

        Intent intent = mDevicePolicyController.getLaunchIntentForCurrentState().get();

        shadowOf(Looper.getMainLooper()).idle();
        assertThat(intent).isNull();
    }

    @Test
    public void getLaunchIntentForCurrentState_withDeviceStateCleared_returnsNull()
            throws ExecutionException, InterruptedException {
        setupSetupParameters();
        setupAppOpsPolicyHandlerExpectations();
        setExpectationsOnDisableKioskKeepAlive();
        setExpectationsOnDisableControllerKeepAlive();
        setExpectationsOnRemoveFinancedDeviceKioskRole();
        setExpectationsOnSetPostNotificationsSystemFixed();
        setupFinalizationControllerExpectations();
        GlobalParametersClient.getInstance().setDeviceState(CLEARED).get();
        installKioskAppWithoutCategoryHomeIntentFilter();
        when(mMockProvisionStateController.getState()).thenReturn(Futures.immediateFuture(
                ProvisionState.PROVISION_SUCCEEDED));

        Intent intent = mDevicePolicyController.getLaunchIntentForCurrentState().get();

        shadowOf(Looper.getMainLooper()).idle();
        assertThat(intent).isNull();
    }

    @Test
    public void getLaunchIntentForCurrentState_provisionInProgressButCleared_returnsNull()
            throws ExecutionException, InterruptedException {
        setupSetupParameters();
        setupAppOpsPolicyHandlerExpectations();
        setExpectationsOnDisableKioskKeepAlive();
        setExpectationsOnDisableControllerKeepAlive();
        setExpectationsOnRemoveFinancedDeviceKioskRole();
        setExpectationsOnSetPostNotificationsSystemFixed();
        setupFinalizationControllerExpectations();
        GlobalParametersClient.getInstance().setDeviceState(CLEARED).get();
        installKioskAppWithoutCategoryHomeIntentFilter();
        when(mMockProvisionStateController.getState()).thenReturn(Futures.immediateFuture(
                ProvisionState.PROVISION_IN_PROGRESS));

        Intent intent = mDevicePolicyController.getLaunchIntentForCurrentState().get();

        shadowOf(Looper.getMainLooper()).idle();
        assertThat(intent).isNull();
    }

    @Test
    public void getLaunchIntentForCurrentState_withProvisionSucceededStateAndDeviceStateUndefined()
            throws ExecutionException, InterruptedException {
        setupSetupParameters();
        GlobalParametersClient.getInstance().setDeviceState(UNDEFINED).get();
        installKioskAppWithoutCategoryHomeIntentFilter();
        when(mMockProvisionStateController.getState()).thenReturn(Futures.immediateFuture(
                ProvisionState.PROVISION_SUCCEEDED));

        Intent intent = mDevicePolicyController.getLaunchIntentForCurrentState().get();

        shadowOf(Looper.getMainLooper()).idle();
        assertThat(intent).isNull();
    }

    @Test
    public void getLaunchIntentForCurrentState_withKioskProvisionedState_shouldReturnIntent()
            throws ExecutionException, InterruptedException {
        setupSetupParameters();
        setupAppOpsPolicyHandlerExpectations();
        setExpectationsOnEnableKioskKeepAlive();
        setExpectationsOnDisableControllerKeepAlive();
        setExpectationsOnAddFinancedDeviceKioskRole();
        installKioskAppWithSetupIntentFilter();
        when(mMockProvisionStateController.getState()).thenReturn(Futures.immediateFuture(
                ProvisionState.KIOSK_PROVISIONED));

        Intent intent = mDevicePolicyController.getLaunchIntentForCurrentState().get();

        shadowOf(Looper.getMainLooper()).idle();
        assertThat(intent).isNotNull();
        assertThat(intent.getComponent().getClassName()).isEqualTo(TEST_KIOSK_ACTIVITY);
        assertThat(intent.getAction()).isEqualTo(ACTION_DEVICE_LOCK_KIOSK_SETUP);
    }

    @Test
    public void getLaunchIntentForCurrentState_withKioskProvisionedState_withoutKioskAppInstalled()
            throws ExecutionException, InterruptedException {
        setupSetupParameters();
        setupAppOpsPolicyHandlerExpectations();
        setExpectationsOnEnableKioskKeepAlive();
        setExpectationsOnDisableControllerKeepAlive();
        setExpectationsOnAddFinancedDeviceKioskRole();

        when(mMockProvisionStateController.getState()).thenReturn(Futures.immediateFuture(
                ProvisionState.KIOSK_PROVISIONED));

        ExecutionException thrown = assertThrows(ExecutionException.class,
                () -> mDevicePolicyController.getLaunchIntentForCurrentState().get());

        assertThat(thrown).hasCauseThat().isInstanceOf(IllegalStateException.class);
        assertThat(thrown).hasMessageThat().contains(
                "Failed to get setup activity intent for kiosk app!");
    }

    @Test
    public void getLaunchIntentForCurrentState_withKioskProvisionedState_withoutKioskAppPackage() {
        setupAppOpsPolicyHandlerExpectations();
        setExpectationsOnEnableKioskKeepAlive();
        setExpectationsOnDisableControllerKeepAlive();
        setExpectationsOnAddFinancedDeviceKioskRole();
        installKioskAppWithSetupIntentFilter();

        when(mMockProvisionStateController.getState()).thenReturn(Futures.immediateFuture(
                ProvisionState.KIOSK_PROVISIONED));

        ExecutionException thrown = assertThrows(ExecutionException.class,
                () -> mDevicePolicyController.getLaunchIntentForCurrentState().get());

        assertThat(thrown).hasCauseThat().isInstanceOf(IllegalStateException.class);
        assertThat(thrown).hasMessageThat().contains(
                "Failed to enforce policies for provision state");
    }

    @Test
    public void getLaunchIntentForCurrentState_withProvisionInProgressState_shouldThrowException()
            throws ExecutionException, InterruptedException {
        setupSetupParameters();
        setupAppOpsPolicyHandlerExpectations();
        setExpectationsOnEnableControllerKeepAlive();
        setExpectationsOnSetPostNotificationsSystemFixed();
        when(mMockProvisionStateController.getState()).thenReturn(Futures.immediateFuture(
                ProvisionState.PROVISION_IN_PROGRESS));

        ExecutionException thrown = assertThrows(ExecutionException.class,
                () -> mDevicePolicyController.getLaunchIntentForCurrentState().get());

        assertThat(thrown).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
        assertThat(thrown).hasMessageThat().contains("Provisioning type is unknown!");
    }

    @Test
    public void getLaunchIntentForCurrentState_withProvisionTypeUndefined_shouldThrowException()
            throws ExecutionException, InterruptedException {
        Bundle preferences = new Bundle();
        preferences.putString(EXTRA_KIOSK_PACKAGE, TEST_KIOSK_PACKAGE);
        preferences.putInt(EXTRA_PROVISIONING_TYPE, TYPE_UNDEFINED);
        SetupParametersClient.getInstance().createPrefs(preferences).get();

        setupAppOpsPolicyHandlerExpectations();
        setExpectationsOnEnableControllerKeepAlive();
        setExpectationsOnSetPostNotificationsSystemFixed();
        when(mMockProvisionStateController.getState()).thenReturn(Futures.immediateFuture(
                ProvisionState.PROVISION_IN_PROGRESS));

        ExecutionException thrown = assertThrows(ExecutionException.class,
                () -> mDevicePolicyController.getLaunchIntentForCurrentState().get());

        assertThat(thrown).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
        assertThat(thrown).hasMessageThat().contains("Provisioning type is unknown!");
    }

    @Test
    public void getLaunchIntentForCurrentState_withProvisionTypeFinanced_shouldReturnIntent()
            throws ExecutionException, InterruptedException {
        Bundle preferences = new Bundle();
        preferences.putString(EXTRA_KIOSK_PACKAGE, TEST_KIOSK_PACKAGE);
        preferences.putInt(EXTRA_PROVISIONING_TYPE, TYPE_FINANCED);
        SetupParametersClient.getInstance().createPrefs(preferences).get();

        setupAppOpsPolicyHandlerExpectations();
        setExpectationsOnEnableControllerKeepAlive();
        setExpectationsOnSetPostNotificationsSystemFixed();
        when(mMockProvisionStateController.getState()).thenReturn(Futures.immediateFuture(
                ProvisionState.PROVISION_IN_PROGRESS));

        Intent intent = mDevicePolicyController.getLaunchIntentForCurrentState().get();

        shadowOf(Looper.getMainLooper()).idle();
        assertThat(intent).isNotNull();
        assertThat(intent.getComponent().getClassName()).isEqualTo(LandingActivity.class.getName());
        assertThat(intent.getAction()).isEqualTo(ACTION_START_DEVICE_FINANCING_PROVISIONING);
    }

    @Test
    public void getLaunchIntentForCurrentState_withProvisionTypeSubsidy_shouldReturnIntent()
            throws ExecutionException, InterruptedException {
        Bundle preferences = new Bundle();
        preferences.putString(EXTRA_KIOSK_PACKAGE, TEST_KIOSK_PACKAGE);
        preferences.putInt(EXTRA_PROVISIONING_TYPE, TYPE_SUBSIDY);
        SetupParametersClient.getInstance().createPrefs(preferences).get();

        setupAppOpsPolicyHandlerExpectations();
        setExpectationsOnEnableControllerKeepAlive();
        setExpectationsOnSetPostNotificationsSystemFixed();
        when(mMockProvisionStateController.getState()).thenReturn(Futures.immediateFuture(
                ProvisionState.PROVISION_IN_PROGRESS));

        Intent intent = mDevicePolicyController.getLaunchIntentForCurrentState().get();

        shadowOf(Looper.getMainLooper()).idle();
        assertThat(intent).isNotNull();
        assertThat(intent.getComponent().getClassName()).isEqualTo(LandingActivity.class.getName());
        assertThat(intent.getAction()).isEqualTo(ACTION_START_DEVICE_SUBSIDY_PROVISIONING);
    }

    @Test
    public void onUserUnlocked_withUnprovisionedState_shouldCallExpectedMethods() throws Exception {
        when(mMockProvisionStateController.onUserUnlocked()).thenReturn(
                Futures.immediateVoidFuture());
        when(mMockProvisionStateController.getState()).thenReturn(Futures.immediateFuture(
                ProvisionState.UNPROVISIONED));
        when(mMockUserManager.isUserUnlocked()).thenReturn(true);

        mDevicePolicyController.onUserUnlocked().get();

        shadowOf(Looper.getMainLooper()).idle();
        assertLockTaskModeNotStarted();
    }

    @Test
    public void onUserUnlocked_withProvisionInProgressState_shouldCallExpectedMethods()
            throws Exception {
        setupSetupParameters();
        setupAppOpsPolicyHandlerExpectations();
        setExpectationsOnEnableControllerKeepAlive();
        setExpectationsOnSetPostNotificationsSystemFixed();
        when(mMockProvisionStateController.onUserUnlocked()).thenReturn(
                Futures.immediateVoidFuture());
        when(mMockProvisionStateController.getState()).thenReturn(Futures.immediateFuture(
                ProvisionState.PROVISION_IN_PROGRESS));
        when(mMockUserManager.isUserUnlocked()).thenReturn(true);

        mDevicePolicyController.onUserUnlocked().get();

        shadowOf(Looper.getMainLooper()).idle();
        assertLockTaskModeStarted();
    }

    @Test
    public void onUserUnlocked_withKioskProvisionedState_shouldMakeExpectedCalls()
            throws Exception {
        setupSetupParameters();
        setupAppOpsPolicyHandlerExpectations();
        setExpectationsOnEnableKioskKeepAlive();
        setExpectationsOnDisableControllerKeepAlive();
        setExpectationsOnAddFinancedDeviceKioskRole();
        when(mMockProvisionStateController.onUserUnlocked()).thenReturn(
                Futures.immediateVoidFuture());
        when(mMockProvisionStateController.getState()).thenReturn(Futures.immediateFuture(
                ProvisionState.KIOSK_PROVISIONED));
        when(mMockUserManager.isUserUnlocked()).thenReturn(true);

        mDevicePolicyController.onUserUnlocked().get();

        shadowOf(Looper.getMainLooper()).idle();
        assertLockTaskModeStarted();
    }

    @Test
    public void onUserUnlocked_withProvisionPausedState_shouldMakeExpectedCalls()
            throws Exception {
        setupSetupParameters();
        setExpectationsOnDisableControllerKeepAlive();
        when(mMockProvisionStateController.onUserUnlocked()).thenReturn(
                Futures.immediateVoidFuture());
        when(mMockProvisionStateController.getState()).thenReturn(Futures.immediateFuture(
                ProvisionState.PROVISION_PAUSED));
        when(mMockUserManager.isUserUnlocked()).thenReturn(true);

        mDevicePolicyController.onUserUnlocked().get();

        shadowOf(Looper.getMainLooper()).idle();
        assertLockTaskModeNotStarted();
    }

    @Test
    public void onUserUnlocked_withProvisionFailedState_shouldMakeExpectedCalls()
            throws Exception {
        setupSetupParameters();
        setupAppOpsPolicyHandlerExpectations();
        when(mMockProvisionStateController.onUserUnlocked()).thenReturn(
                Futures.immediateVoidFuture());
        when(mMockProvisionStateController.getState()).thenReturn(Futures.immediateFuture(
                ProvisionState.PROVISION_FAILED));
        when(mMockUserManager.isUserUnlocked()).thenReturn(true);

        mDevicePolicyController.onUserUnlocked().get();

        shadowOf(Looper.getMainLooper()).idle();
        assertLockTaskModeNotStarted();
    }

    @Test
    public void onUserUnlocked_withUndefinedDeviceState_shouldMakeExpectedCalls() throws Exception {
        setupSetupParameters();
        when(mMockProvisionStateController.onUserUnlocked()).thenReturn(
                Futures.immediateVoidFuture());
        when(mMockProvisionStateController.getState()).thenReturn(Futures.immediateFuture(
                ProvisionState.PROVISION_SUCCEEDED));
        when(mMockUserManager.isUserUnlocked()).thenReturn(true);
        GlobalParametersClient.getInstance().setDeviceState(UNDEFINED).get();

        mDevicePolicyController.onUserUnlocked().get();

        shadowOf(Looper.getMainLooper()).idle();
        assertLockTaskModeNotStarted();
    }

    @Test
    public void onUserUnlocked_withLockedDeviceState_shouldMakeExpectedCalls() throws Exception {
        setupSetupParameters();
        setupAppOpsPolicyHandlerExpectations();
        when(mMockProvisionStateController.onUserUnlocked()).thenReturn(
                Futures.immediateVoidFuture());
        when(mMockProvisionStateController.getState()).thenReturn(Futures.immediateFuture(
                ProvisionState.PROVISION_SUCCEEDED));
        when(mMockUserManager.isUserUnlocked()).thenReturn(true);
        GlobalParametersClient.getInstance().setDeviceState(LOCKED).get();

        mDevicePolicyController.onUserUnlocked().get();

        shadowOf(Looper.getMainLooper()).idle();
        assertLockTaskModeStarted();
    }

    @Test
    public void onUserUnlocked_withUnlockedDeviceState_shouldMakeExpectedCalls() throws Exception {
        setupSetupParameters();
        setupAppOpsPolicyHandlerExpectations();
        when(mMockProvisionStateController.onUserUnlocked()).thenReturn(
                Futures.immediateVoidFuture());
        when(mMockProvisionStateController.getState()).thenReturn(Futures.immediateFuture(
                ProvisionState.PROVISION_SUCCEEDED));
        when(mMockUserManager.isUserUnlocked()).thenReturn(true);
        GlobalParametersClient.getInstance().setDeviceState(UNLOCKED).get();

        mDevicePolicyController.onUserUnlocked().get();

        shadowOf(Looper.getMainLooper()).idle();
        assertLockTaskModeNotStarted();
    }

    @Test
    public void onUserUnlocked_withClearedDeviceState_shouldMakeExpectedCalls() throws Exception {
        setupSetupParameters();
        setupAppOpsPolicyHandlerExpectations();
        setExpectationsOnDisableKioskKeepAlive();
        setExpectationsOnDisableControllerKeepAlive();
        setExpectationsOnRemoveFinancedDeviceKioskRole();
        setExpectationsOnSetPostNotificationsSystemFixed();
        setupFinalizationControllerExpectations();
        when(mMockProvisionStateController.onUserUnlocked()).thenReturn(
                Futures.immediateVoidFuture());
        when(mMockProvisionStateController.getState()).thenReturn(Futures.immediateFuture(
                ProvisionState.PROVISION_SUCCEEDED));
        when(mMockUserManager.isUserUnlocked()).thenReturn(true);
        GlobalParametersClient.getInstance().setDeviceState(CLEARED).get();

        mDevicePolicyController.onUserUnlocked().get();

        shadowOf(Looper.getMainLooper()).idle();
        assertLockTaskModeNotStarted();
    }

    @Test
    public void onAppCrashed_withUnprovisionedState_shouldMakeExpectedCalls()
            throws Exception {
        when(mMockProvisionStateController.getState()).thenReturn(Futures.immediateFuture(
                ProvisionState.UNPROVISIONED));

        mDevicePolicyController.onAppCrashed(true /* isKiosk */).get();

        shadowOf(Looper.getMainLooper()).idle();
        assertLockTaskModeNotStarted();
    }

    @Test
    public void onAppCrashed_withProvisionInProgressState_shouldCallExpectedMethods()
            throws Exception {
        setupSetupParameters();
        setupAppOpsPolicyHandlerExpectations();
        setExpectationsOnEnableControllerKeepAlive();
        setExpectationsOnSetPostNotificationsSystemFixed();
        when(mMockProvisionStateController.getState()).thenReturn(Futures.immediateFuture(
                ProvisionState.PROVISION_IN_PROGRESS));
        when(mMockUserManager.isUserUnlocked()).thenReturn(true);

        mDevicePolicyController.onAppCrashed(true /* isKiosk */).get();

        shadowOf(Looper.getMainLooper()).idle();
        assertLockTaskModeStarted();
    }

    @Test
    public void onAppCrashed_withKioskProvisionedState_shouldMakeExpectedCalls()
            throws Exception {
        setupSetupParameters();
        setupAppOpsPolicyHandlerExpectations();
        setExpectationsOnEnableKioskKeepAlive();
        setExpectationsOnDisableControllerKeepAlive();
        setExpectationsOnAddFinancedDeviceKioskRole();
        when(mMockProvisionStateController.getState()).thenReturn(Futures.immediateFuture(
                ProvisionState.KIOSK_PROVISIONED));
        when(mMockUserManager.isUserUnlocked()).thenReturn(true);

        mDevicePolicyController.onAppCrashed(true /* isKiosk */).get();

        shadowOf(Looper.getMainLooper()).idle();
        assertLockTaskModeStarted();
    }

    @Test
    public void onAppCrashed_withProvisionPausedState_shouldMakeExpectedCalls()
            throws Exception {
        setupSetupParameters();
        setExpectationsOnDisableControllerKeepAlive();
        when(mMockProvisionStateController.getState()).thenReturn(Futures.immediateFuture(
                ProvisionState.PROVISION_PAUSED));
        when(mMockUserManager.isUserUnlocked()).thenReturn(true);

        mDevicePolicyController.onAppCrashed(true /* isKiosk */).get();

        shadowOf(Looper.getMainLooper()).idle();
        assertLockTaskModeNotStarted();
    }

    @Test
    public void onAppCrashed_withProvisionFailedState_shouldMakeExpectedCalls()
            throws Exception {
        setupSetupParameters();
        setupAppOpsPolicyHandlerExpectations();
        when(mMockProvisionStateController.getState()).thenReturn(Futures.immediateFuture(
                ProvisionState.PROVISION_FAILED));
        when(mMockUserManager.isUserUnlocked()).thenReturn(true);

        mDevicePolicyController.onAppCrashed(true /* isKiosk */).get();

        shadowOf(Looper.getMainLooper()).idle();
        assertLockTaskModeNotStarted();
    }

    @Test
    public void onAppCrashed_withUndefinedDeviceState_shouldMakeExpectedCalls()
            throws Exception {
        setupSetupParameters();
        when(mMockProvisionStateController.getState()).thenReturn(Futures.immediateFuture(
                ProvisionState.PROVISION_SUCCEEDED));
        when(mMockUserManager.isUserUnlocked()).thenReturn(true);
        GlobalParametersClient.getInstance().setDeviceState(UNDEFINED).get();

        mDevicePolicyController.onAppCrashed(true /* isKiosk */).get();

        shadowOf(Looper.getMainLooper()).idle();
        assertLockTaskModeNotStarted();
    }

    @Test
    public void onAppCrashed_withLockedDeviceState_shouldMakeExpectedCalls() throws Exception {
        setupSetupParameters();
        setupAppOpsPolicyHandlerExpectations();
        when(mMockProvisionStateController.getState()).thenReturn(Futures.immediateFuture(
                ProvisionState.PROVISION_SUCCEEDED));
        when(mMockUserManager.isUserUnlocked()).thenReturn(true);
        GlobalParametersClient.getInstance().setDeviceState(LOCKED).get();

        mDevicePolicyController.onAppCrashed(true /* isKiosk */).get();

        shadowOf(Looper.getMainLooper()).idle();
        assertLockTaskModeStarted();
    }

    @Test
    public void onAppCrashed_withUnlockedDeviceState_shouldMakeExpectedCalls()
            throws Exception {
        setupSetupParameters();
        setupAppOpsPolicyHandlerExpectations();
        when(mMockProvisionStateController.getState()).thenReturn(Futures.immediateFuture(
                ProvisionState.PROVISION_SUCCEEDED));
        when(mMockUserManager.isUserUnlocked()).thenReturn(true);
        GlobalParametersClient.getInstance().setDeviceState(UNLOCKED).get();

        mDevicePolicyController.onAppCrashed(true /* isKiosk */).get();

        shadowOf(Looper.getMainLooper()).idle();
        assertLockTaskModeNotStarted();
    }

    @Test
    public void onAppCrashed_withClearedDeviceState_shouldMakeExpectedCalls()
            throws Exception {
        setupSetupParameters();
        setupAppOpsPolicyHandlerExpectations();
        setExpectationsOnDisableKioskKeepAlive();
        setExpectationsOnDisableControllerKeepAlive();
        setExpectationsOnRemoveFinancedDeviceKioskRole();
        setExpectationsOnSetPostNotificationsSystemFixed();
        setupFinalizationControllerExpectations();
        when(mMockProvisionStateController.getState()).thenReturn(Futures.immediateFuture(
                ProvisionState.PROVISION_SUCCEEDED));
        when(mMockUserManager.isUserUnlocked()).thenReturn(true);
        GlobalParametersClient.getInstance().setDeviceState(CLEARED).get();

        mDevicePolicyController.onAppCrashed(true /* isKiosk */).get();

        shadowOf(Looper.getMainLooper()).idle();
        assertLockTaskModeNotStarted();
    }

    private void assertLockTaskModeStarted() throws Exception {
        ListenableFuture<List<WorkInfo>> workInfosFuture = WorkManager.getInstance(mTestApp)
                .getWorkInfosForUniqueWork(START_LOCK_TASK_MODE_WORK_NAME);
        List<WorkInfo> workInfos = Futures.getChecked(workInfosFuture, Exception.class);
        assertThat(workInfos).isNotEmpty();
    }

    private void assertLockTaskModeNotStarted() throws Exception {
        ListenableFuture<List<WorkInfo>> workInfosFuture = WorkManager.getInstance(mTestApp)
                .getWorkInfosForUniqueWork(START_LOCK_TASK_MODE_WORK_NAME);
        List<WorkInfo> workInfos = Futures.getChecked(workInfosFuture, Exception.class);
        assertThat(workInfos).isEmpty();
    }

    private void assertReportSetupFailedWorkStarted() throws Exception {
        ListenableFuture<List<WorkInfo>> workInfosFuture = WorkManager.getInstance(mTestApp)
                .getWorkInfosForUniqueWork(REPORT_PROVISION_STATE_WORK_NAME);
        List<WorkInfo> workInfos = Futures.getChecked(workInfosFuture, Exception.class);
        assertThat(workInfos).isNotEmpty();
    }

    private static void assertCriticalFailureIntent(Intent intent) {
        assertThat(intent).isNotNull();
        assertThat(intent.getComponent().getClassName()).isEqualTo(
                ProvisioningActivity.class.getName());
        assertThat(intent.getExtras().getBoolean(
                EXTRA_SHOW_CRITICAL_PROVISION_FAILED_UI_ON_START)).isTrue();
    }

    private void installKioskAppWithoutCategoryHomeIntentFilter() {
        ShadowPackageManager shadowPackageManager = Shadows.shadowOf(mTestApp.getPackageManager());
        PackageInfo kioskPackageInfo = new PackageInfo();
        kioskPackageInfo.packageName = TEST_KIOSK_PACKAGE;
        shadowPackageManager.installPackage(kioskPackageInfo);

        IntentFilter kioskAppIntentFilter = new IntentFilter(Intent.ACTION_MAIN);
        kioskAppIntentFilter.addCategory(Intent.CATEGORY_DEFAULT);
        kioskAppIntentFilter.addCategory(Intent.CATEGORY_LAUNCHER);
        ComponentName kioskAppComponent =
                new ComponentName(TEST_KIOSK_PACKAGE, TEST_KIOSK_ACTIVITY);

        shadowPackageManager.addActivityIfNotPresent(kioskAppComponent);
        shadowPackageManager.addIntentFilterForActivity(kioskAppComponent, kioskAppIntentFilter);
    }

    private void installKioskAppWithLockScreenIntentFilter() {
        ShadowPackageManager shadowPackageManager = Shadows.shadowOf(mTestApp.getPackageManager());
        PackageInfo kioskPackageInfo = new PackageInfo();
        kioskPackageInfo.packageName = TEST_KIOSK_PACKAGE;
        shadowPackageManager.installPackage(kioskPackageInfo);

        IntentFilter kioskAppIntentFilter = new IntentFilter(Intent.ACTION_MAIN);
        kioskAppIntentFilter.addCategory(Intent.CATEGORY_HOME);
        kioskAppIntentFilter.addCategory(Intent.CATEGORY_DEFAULT);
        ComponentName kioskAppComponent =
                new ComponentName(TEST_KIOSK_PACKAGE, TEST_KIOSK_ACTIVITY);

        shadowPackageManager.addActivityIfNotPresent(kioskAppComponent);
        shadowPackageManager.addIntentFilterForActivity(kioskAppComponent, kioskAppIntentFilter);
    }

    private void installKioskAppWithSetupIntentFilter() {
        ShadowPackageManager shadowPackageManager = Shadows.shadowOf(mTestApp.getPackageManager());
        PackageInfo kioskPackageInfo = new PackageInfo();
        kioskPackageInfo.packageName = TEST_KIOSK_PACKAGE;
        shadowPackageManager.installPackage(kioskPackageInfo);

        IntentFilter kioskAppIntentFilter =
                new IntentFilter(ACTION_DEVICE_LOCK_KIOSK_SETUP);
        kioskAppIntentFilter.addCategory(Intent.CATEGORY_DEFAULT);
        ComponentName kioskAppComponent =
                new ComponentName(TEST_KIOSK_PACKAGE, TEST_KIOSK_ACTIVITY);

        shadowPackageManager.addActivityIfNotPresent(kioskAppComponent);
        shadowPackageManager.addIntentFilterForActivity(kioskAppComponent, kioskAppIntentFilter);
    }

    private static void setupSetupParameters() throws ExecutionException, InterruptedException {
        Bundle preferences = new Bundle();
        preferences.putString(EXTRA_KIOSK_PACKAGE, TEST_KIOSK_PACKAGE);
        SetupParametersClient.getInstance().createPrefs(preferences).get();
    }

    private void setExpectationsOnAddFinancedDeviceKioskRole() {
        doAnswer((Answer<Object>) invocation -> {
            OutcomeReceiver<Void, Exception> callback = invocation.getArgument(/* callback =*/ 2);
            callback.onResult(/* result =*/ null);
            return null;
        }).when(mMockSystemDeviceLockManager).addFinancedDeviceKioskRole(anyString(),
                any(Executor.class), any());
    }

    private void setExpectationsOnRemoveFinancedDeviceKioskRole() {
        doAnswer((Answer<Object>) invocation -> {
            OutcomeReceiver<Void, Exception> callback = invocation.getArgument(/* callback =*/ 2);
            callback.onResult(/* result =*/ null);
            return null;
        }).when(mMockSystemDeviceLockManager).removeFinancedDeviceKioskRole(anyString(),
                any(Executor.class), any());
    }

    private void setExpectationsOnEnableKioskKeepAlive() {
        doAnswer((Answer<Object>) invocation -> {
            OutcomeReceiver<Void, Exception> callback = invocation.getArgument(/* callback =*/ 2);
            callback.onResult(/* result =*/ null);
            return null;
        }).when(mMockSystemDeviceLockManager).enableKioskKeepalive(anyString(),
                any(Executor.class), any());
    }

    private void setExpectationsOnDisableKioskKeepAlive() {
        doAnswer((Answer<Object>) invocation -> {
            OutcomeReceiver<Void, Exception> callback = invocation.getArgument(/* callback =*/ 1);
            callback.onResult(/* result =*/ null);
            return null;
        }).when(mMockSystemDeviceLockManager).disableKioskKeepalive(any(Executor.class), any());
    }

    private void setExpectationsOnEnableControllerKeepAlive() {
        doAnswer((Answer<Object>) invocation -> {
            OutcomeReceiver<Void, Exception> callback = invocation.getArgument(/* callback =*/ 1);
            callback.onResult(/* result =*/ null);
            return null;
        }).when(mMockSystemDeviceLockManager).enableControllerKeepalive(any(Executor.class),
                any());
    }

    private void setExpectationsOnDisableControllerKeepAlive() {
        doAnswer((Answer<Object>) invocation -> {
            OutcomeReceiver<Void, Exception> callback = invocation.getArgument(/* callback =*/ 1);
            callback.onResult(/* result =*/ null);
            return null;
        }).when(mMockSystemDeviceLockManager).disableControllerKeepalive(any(Executor.class),
                any());
    }

    private void setupAppOpsPolicyHandlerExpectations() {
        doAnswer((Answer<Boolean>) invocation -> {
            OutcomeReceiver<Void, Exception> callback = invocation.getArgument(2 /* callback */);
            callback.onResult(null /* result */);

            return null;
        }).when(mMockSystemDeviceLockManager)
                .setDlcExemptFromActivityBgStartRestrictionState(anyBoolean(),
                        any(Executor.class),
                        any());
        doAnswer((Answer<Boolean>) invocation -> {
            OutcomeReceiver<Void, Exception> callback = invocation.getArgument(2 /* callback */);
            callback.onResult(null /* result */);

            return null;
        }).when(mMockSystemDeviceLockManager)
                .setDlcAllowedToSendUndismissibleNotifications(anyBoolean(),
                        any(Executor.class),
                        any());

        doAnswer((Answer<Boolean>) invocation -> {
            OutcomeReceiver<Void, Exception> callback = invocation.getArgument(3 /* callback */);
            callback.onResult(null /* result */);

            return null;
        }).when(mMockSystemDeviceLockManager)
                .setKioskAppExemptFromRestrictionsState(anyString(), anyBoolean(),
                        any(Executor.class), any());
    }

    private void setupFinalizationControllerExpectations() {
        doAnswer((Answer<Boolean>) invocation -> {
            OutcomeReceiver<Void, Exception> callback = invocation.getArgument(2 /* callback */);
            callback.onResult(null /* result */);

            return null;
        }).when(mMockSystemDeviceLockManager)
                .setDeviceFinalized(anyBoolean(),
                        any(Executor.class),
                        any());
    }

    private void setExpectationsOnSetPostNotificationsSystemFixed() {
        doAnswer((Answer<Boolean>) invocation -> {
            OutcomeReceiver<Void, Exception> callback = invocation.getArgument(2 /* callback */);
            callback.onResult(null /* result */);

            return null;
        }).when(mMockSystemDeviceLockManager)
                .setPostNotificationsSystemFixed(anyBoolean(),
                        any(Executor.class),
                        any());
    }
}
