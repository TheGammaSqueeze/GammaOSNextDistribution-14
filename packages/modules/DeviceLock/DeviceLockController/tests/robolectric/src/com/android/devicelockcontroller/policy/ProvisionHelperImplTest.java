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

import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_KIOSK_PACKAGE;
import static com.android.devicelockcontroller.common.DeviceLockConstants.ProvisionFailureReason.NOT_IN_ELIGIBLE_COUNTRY;
import static com.android.devicelockcontroller.common.DeviceLockConstants.ProvisionFailureReason.PLAY_INSTALLATION_FAILED;
import static com.android.devicelockcontroller.common.DeviceLockConstants.ProvisionFailureReason.UNKNOWN_REASON;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionEvent.PROVISION_KIOSK;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionEvent.PROVISION_PAUSE;
import static com.android.devicelockcontroller.provision.worker.IsDeviceInApprovedCountryWorker.KEY_IS_IN_APPROVED_COUNTRY;
import static com.android.devicelockcontroller.provision.worker.PauseProvisioningWorker.REPORT_PROVISION_PAUSED_BY_USER_WORK;
import static com.android.devicelockcontroller.provision.worker.ReportDeviceProvisionStateWorker.KEY_IS_PROVISION_SUCCESSFUL;
import static com.android.devicelockcontroller.provision.worker.ReportDeviceProvisionStateWorker.KEY_PROVISION_FAILURE_REASON;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Looper;
import android.text.TextUtils;
import android.util.ArrayMap;

import androidx.annotation.NonNull;
import androidx.core.util.Consumer;
import androidx.core.util.Preconditions;
import androidx.lifecycle.Lifecycle.State;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import androidx.test.core.app.ApplicationProvider;
import androidx.work.Configuration;
import androidx.work.Data;
import androidx.work.ListenableWorker;
import androidx.work.ListenableWorker.Result;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.WorkerFactory;
import androidx.work.WorkerParameters;
import androidx.work.testing.TestDriver;
import androidx.work.testing.WorkManagerTestInitHelper;

import com.android.devicelockcontroller.TestDeviceLockControllerApplication;
import com.android.devicelockcontroller.TestDeviceLockControllerApplication.PlayInstallPackageWorker;
import com.android.devicelockcontroller.activities.ProvisioningProgress;
import com.android.devicelockcontroller.activities.ProvisioningProgressController;
import com.android.devicelockcontroller.common.DeviceLockConstants.ProvisionFailureReason;
import com.android.devicelockcontroller.provision.worker.IsDeviceInApprovedCountryWorker;
import com.android.devicelockcontroller.provision.worker.ReportDeviceProvisionStateWorker;
import com.android.devicelockcontroller.shadows.ShadowBuild;
import com.android.devicelockcontroller.storage.GlobalParametersClient;
import com.android.devicelockcontroller.storage.SetupParametersClient;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.testing.TestingExecutors;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowPackageManager;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

@RunWith(RobolectricTestRunner.class)
@Config(shadows = {ShadowBuild.class})
public final class ProvisionHelperImplTest {

    private static final String TEST_KIOSK_PACKAGE = "test.package.name";
    private static final String PLAY_INSTALL_WORKER_UNIQUE_NAME =
            PlayInstallPackageWorker.class.getSimpleName();
    private static final String PLAY_INSTALL_WORKER_CLASS_NAME =
            PlayInstallPackageWorker.class.getName();
    private static final String COUNTRY_WORKER_CLASS_NAME =
            IsDeviceInApprovedCountryWorker.class.getName();
    private static final String COUNTRY_WORKER_UNIQUE_NAME =
            IsDeviceInApprovedCountryWorker.class.getSimpleName();

    @Rule
    public final MockitoRule mMocks = MockitoJUnit.rule();

    private ProvisionStateController mMockStateController;
    @Mock
    private LifecycleOwner mMockLifecycleOwner;
    @Mock
    private ProvisioningProgressController mProgressController;
    @Captor
    private ArgumentCaptor<ProvisioningProgress> mProvisioningProgressArgumentCaptor;

    private TestDeviceLockControllerApplication mTestApp;
    private ProvisionHelperImpl mProvisionHelper;

    private TestDriver mTestDriver;
    private TestWorkerFactory mTestWorkerFactory;

    @Before
    public void setUp() {
        mTestApp = ApplicationProvider.getApplicationContext();
        mMockStateController = mTestApp.getProvisionStateController();
        Executor executor = TestingExecutors.sameThreadScheduledExecutor();
        ProvisionHelperImpl.getSharedPreferences(mTestApp).edit().clear().commit();
        mProvisionHelper = new ProvisionHelperImpl(mTestApp, mMockStateController, executor);
        mTestWorkerFactory = new TestWorkerFactory();
        WorkManagerTestInitHelper.initializeTestWorkManager(mTestApp,
                new Configuration.Builder().setExecutor(executor).setWorkerFactory(
                        mTestWorkerFactory).build());
        mTestDriver = WorkManagerTestInitHelper.getTestDriver(mTestApp);
        setupLifecycle();
    }

    @Test
    public void checkGeoEligibility_countryUnknown_thenProceedToFailure() throws Exception {
        // GIVEN Country is unknown
        mTestWorkerFactory.setWorkResult(COUNTRY_WORKER_CLASS_NAME,
                Result.failure());

        // WHEN Installation is executed
        mProvisionHelper.scheduleKioskAppInstallation(mMockLifecycleOwner,
                mProgressController, /* isProvisionMandatory= */ false);
        shadowOf(Looper.getMainLooper()).idle();
        executeWork(COUNTRY_WORKER_UNIQUE_NAME);

        // THEN Installation fails
        verifyProgressesSet(Arrays.asList(ProvisioningProgress.GETTING_DEVICE_READY,
                ProvisioningProgress.getNonMandatoryProvisioningFailedProgress(
                        ProvisionFailureReason.COUNTRY_INFO_UNAVAILABLE)));
    }

    @Test
    public void checkGeoEligibility_inUnapprovedCountry_thenProceedToFailure() throws Exception {
        // GIVEN Country is unapproved
        mTestWorkerFactory.setWorkResult(COUNTRY_WORKER_CLASS_NAME,
                Result.success(
                        new Data.Builder().putBoolean(KEY_IS_IN_APPROVED_COUNTRY, false).build()));

        // WHEN Installation is initiated
        mProvisionHelper.scheduleKioskAppInstallation(mMockLifecycleOwner,
                mProgressController, /* isProvisionMandatory= */ false);
        shadowOf(Looper.getMainLooper()).idle();
        executeWork(COUNTRY_WORKER_UNIQUE_NAME);

        // THEN Installation fails
        verifyProgressesSet(Arrays.asList(ProvisioningProgress.GETTING_DEVICE_READY,
                ProvisioningProgress.getNonMandatoryProvisioningFailedProgress(
                        ProvisionFailureReason.NOT_IN_ELIGIBLE_COUNTRY)));
    }

    @Test
    public void checkGeoEligibility_inApprovedCountry_thenProceedToInstalling() throws Exception {
        // GIVEN Country is approved
        mTestWorkerFactory.setWorkResult(COUNTRY_WORKER_CLASS_NAME,
                Result.success(
                        new Data.Builder().putBoolean(KEY_IS_IN_APPROVED_COUNTRY, true).build()));


        // WHEN Installation is initiated
        mProvisionHelper.scheduleKioskAppInstallation(mMockLifecycleOwner,
                mProgressController, /* isProvisionMandatory= */ false);
        shadowOf(Looper.getMainLooper()).idle();
        executeWork(IsDeviceInApprovedCountryWorker.class.getSimpleName());

        // THEN install kiosk app
        verifyProgressesSet(Arrays.asList(ProvisioningProgress.GETTING_DEVICE_READY,
                ProvisioningProgress.INSTALLING_KIOSK_APP));
    }

    @Test
    public void installKiosk_onDebuggableBuild_thenUseInstalledApp() throws Exception {
        // GIVEN build is debuggable build and kiosk app is installed
        ShadowBuild.setIsDebuggable(true);
        setupSetupParameters();
        installKioskApp();
        setupLifecycle();

        // GIVEN Country is approved and play installation would fail.
        mTestWorkerFactory.setWorkResult(COUNTRY_WORKER_CLASS_NAME,
                Result.success(
                        new Data.Builder().putBoolean(KEY_IS_IN_APPROVED_COUNTRY, true).build()));


        // WHEN installation is executed
        mProvisionHelper.scheduleKioskAppInstallation(mMockLifecycleOwner,
                mProgressController, /* isProvisionMandatory= */ false);
        shadowOf(Looper.getMainLooper()).idle();
        executeWork(COUNTRY_WORKER_UNIQUE_NAME);

        // THEN go through correct ProvisioningProgress and advance to next state.
        verify(mMockStateController).postSetNextStateForEventRequest(eq(PROVISION_KIOSK));
        verifyProgressesSet(Arrays.asList(ProvisioningProgress.GETTING_DEVICE_READY,
                ProvisioningProgress.INSTALLING_KIOSK_APP,
                ProvisioningProgress.OPENING_KIOSK_APP));

        // THEN play installation work is not enqueued.
        assertWorkNotEnqueued(PLAY_INSTALL_WORKER_UNIQUE_NAME);

        // THEN provision complete is reported
        mTestWorkerFactory.setWorkerInputDataAssert(
                ReportDeviceProvisionStateWorker.class.getName(), data -> {
                    assertThat(data.getBoolean(KEY_IS_PROVISION_SUCCESSFUL, false)).isTrue();
                });
        executeWork(ReportDeviceProvisionStateWorker.REPORT_PROVISION_STATE_WORK_NAME);
    }

    @Test
    public void installKiosk_whenFlagIsDisabledOnDebuggableBuild_thenInstallFromPlay()
            throws Exception {
        // GIVEN build is debuggable build and kiosk app is installed and flag is disabled
        ShadowBuild.setIsDebuggable(true);
        setupSetupParameters();
        installKioskApp();
        setupLifecycle();
        ProvisionHelperImpl.setPreinstalledKioskAllowed(mTestApp, false);

        // GIVEN Country is approved and play installation would success.
        mTestWorkerFactory.setWorkResult(COUNTRY_WORKER_CLASS_NAME,
                Result.success(
                        new Data.Builder().putBoolean(KEY_IS_IN_APPROVED_COUNTRY, true).build()));
        mTestWorkerFactory.setWorkResult(PLAY_INSTALL_WORKER_CLASS_NAME, Result.success());


        // WHEN installation is executed
        mProvisionHelper.scheduleKioskAppInstallation(mMockLifecycleOwner,
                mProgressController, /* isProvisionMandatory= */ false);
        shadowOf(Looper.getMainLooper()).idle();
        executeWork(COUNTRY_WORKER_UNIQUE_NAME);
        executeWork(PLAY_INSTALL_WORKER_UNIQUE_NAME);

        // THEN go through correct ProvisioningProgress and advance to next state.
        verifyProgressesSet(Arrays.asList(ProvisioningProgress.GETTING_DEVICE_READY,
                ProvisioningProgress.INSTALLING_KIOSK_APP,
                ProvisioningProgress.OPENING_KIOSK_APP));
        verify(mMockStateController).postSetNextStateForEventRequest(eq(PROVISION_KIOSK));

        // THEN provision complete is reported
        assertWorkEnqueued(PLAY_INSTALL_WORKER_UNIQUE_NAME);
        mTestWorkerFactory.setWorkerInputDataAssert(
                ReportDeviceProvisionStateWorker.class.getName(), data -> {
                    assertThat(data.getBoolean(KEY_IS_PROVISION_SUCCESSFUL, false)).isTrue();
                });
        executeWork(ReportDeviceProvisionStateWorker.REPORT_PROVISION_STATE_WORK_NAME);
    }

    @Test
    public void installKiosk_whenFlagIsEnabledOnNonDebuggableBuild_thenInstallFromPlay()
            throws Exception {
        // GIVEN build is non-debuggable build and kiosk app is installed and flag is enabled
        ShadowBuild.setIsDebuggable(false);
        setupSetupParameters();
        installKioskApp();
        setupLifecycle();
        ProvisionHelperImpl.setPreinstalledKioskAllowed(mTestApp, true);

        // GIVEN Country is approved and play installation would success.
        mTestWorkerFactory.setWorkResult(COUNTRY_WORKER_CLASS_NAME,
                Result.success(
                        new Data.Builder().putBoolean(KEY_IS_IN_APPROVED_COUNTRY, true).build()));
        mTestWorkerFactory.setWorkResult(PLAY_INSTALL_WORKER_CLASS_NAME, Result.success());


        // WHEN installation is executed
        mProvisionHelper.scheduleKioskAppInstallation(mMockLifecycleOwner,
                mProgressController, /* isProvisionMandatory= */ false);
        shadowOf(Looper.getMainLooper()).idle();
        executeWork(COUNTRY_WORKER_UNIQUE_NAME);
        executeWork(PLAY_INSTALL_WORKER_UNIQUE_NAME);

        // THEN go through correct ProvisioningProgress and advance to next state.
        verifyProgressesSet(Arrays.asList(ProvisioningProgress.GETTING_DEVICE_READY,
                ProvisioningProgress.INSTALLING_KIOSK_APP,
                ProvisioningProgress.OPENING_KIOSK_APP));
        verify(mMockStateController).postSetNextStateForEventRequest(eq(PROVISION_KIOSK));

        // THEN provision complete is reported
        assertWorkEnqueued(PLAY_INSTALL_WORKER_UNIQUE_NAME);
        mTestWorkerFactory.setWorkerInputDataAssert(
                ReportDeviceProvisionStateWorker.class.getName(), data -> {
                    assertThat(data.getBoolean(KEY_IS_PROVISION_SUCCESSFUL, false)).isTrue();
                });
        executeWork(ReportDeviceProvisionStateWorker.REPORT_PROVISION_STATE_WORK_NAME);
    }

    @Test
    public void installKiosk_whenFlagIsDisabledOnNonDebuggableBuild_thenInstallFromPlay()
            throws Exception {
        // GIVEN build is non-debuggable build and kiosk app is installed and flag is disabled
        ShadowBuild.setIsDebuggable(false);
        setupSetupParameters();
        installKioskApp();
        setupLifecycle();
        ProvisionHelperImpl.setPreinstalledKioskAllowed(mTestApp, false);

        // GIVEN Country is approved and play installation would success.
        mTestWorkerFactory.setWorkResult(COUNTRY_WORKER_CLASS_NAME,
                Result.success(
                        new Data.Builder().putBoolean(KEY_IS_IN_APPROVED_COUNTRY, true).build()));
        mTestWorkerFactory.setWorkResult(PLAY_INSTALL_WORKER_CLASS_NAME, Result.success());


        // WHEN installation is executed
        mProvisionHelper.scheduleKioskAppInstallation(mMockLifecycleOwner,
                mProgressController, /* isProvisionMandatory= */ false);
        shadowOf(Looper.getMainLooper()).idle();
        executeWork(COUNTRY_WORKER_UNIQUE_NAME);
        executeWork(PLAY_INSTALL_WORKER_UNIQUE_NAME);

        // THEN go through correct ProvisioningProgress and advance to next state.
        verifyProgressesSet(Arrays.asList(ProvisioningProgress.GETTING_DEVICE_READY,
                ProvisioningProgress.INSTALLING_KIOSK_APP,
                ProvisioningProgress.OPENING_KIOSK_APP));
        verify(mMockStateController).postSetNextStateForEventRequest(eq(PROVISION_KIOSK));

        // THEN provision complete is reported
        assertWorkEnqueued(PLAY_INSTALL_WORKER_UNIQUE_NAME);
        mTestWorkerFactory.setWorkerInputDataAssert(
                ReportDeviceProvisionStateWorker.class.getName(), data -> {
                    assertThat(data.getBoolean(KEY_IS_PROVISION_SUCCESSFUL, false)).isTrue();
                });
        executeWork(ReportDeviceProvisionStateWorker.REPORT_PROVISION_STATE_WORK_NAME);
    }

    @Test
    public void installKiosk_whenAppIsNotPreinstalled_thenInstallFromPlay() throws Exception {
        // GIVEN build is debuggable build and kiosk app is not installed
        ShadowBuild.setIsDebuggable(true);
        setupSetupParameters();
        setupLifecycle();
        checkKioskAppNotInstalled();

        // GIVEN Country is approved and play installation would success.
        mTestWorkerFactory.setWorkResult(COUNTRY_WORKER_CLASS_NAME,
                Result.success(
                        new Data.Builder().putBoolean(KEY_IS_IN_APPROVED_COUNTRY, true).build()));
        mTestWorkerFactory.setWorkResult(PLAY_INSTALL_WORKER_CLASS_NAME, Result.success());


        // WHEN installation is executed
        mProvisionHelper.scheduleKioskAppInstallation(mMockLifecycleOwner,
                mProgressController, /* isProvisionMandatory= */ false);
        shadowOf(Looper.getMainLooper()).idle();
        executeWork(COUNTRY_WORKER_UNIQUE_NAME);
        executeWork(PLAY_INSTALL_WORKER_UNIQUE_NAME);

        // THEN go through correct ProvisioningProgress and advance to next state.
        verifyProgressesSet(Arrays.asList(ProvisioningProgress.GETTING_DEVICE_READY,
                ProvisioningProgress.INSTALLING_KIOSK_APP,
                ProvisioningProgress.OPENING_KIOSK_APP));
        verify(mMockStateController).postSetNextStateForEventRequest(eq(PROVISION_KIOSK));

        // THEN provision complete is reported
        assertWorkEnqueued(PLAY_INSTALL_WORKER_UNIQUE_NAME);
        mTestWorkerFactory.setWorkerInputDataAssert(
                ReportDeviceProvisionStateWorker.class.getName(), data -> {
                    assertThat(data.getBoolean(KEY_IS_PROVISION_SUCCESSFUL, false)).isTrue();
                });
        executeWork(ReportDeviceProvisionStateWorker.REPORT_PROVISION_STATE_WORK_NAME);
    }

    @Test
    public void installKiosk_whenInstallationFailsAndIsMandatory_thenReportFailure()
            throws Exception {
        // GIVEN Country is approved and play installation would fail.
        mTestWorkerFactory.setWorkResult(COUNTRY_WORKER_CLASS_NAME,
                Result.success(
                        new Data.Builder().putBoolean(KEY_IS_IN_APPROVED_COUNTRY, true).build()));
        mTestWorkerFactory.setWorkResult(PLAY_INSTALL_WORKER_CLASS_NAME, Result.failure());

        // WHEN installation is executed for mandatory provisioning
        mProvisionHelper.scheduleKioskAppInstallation(mMockLifecycleOwner,
                mProgressController, /* isProvisionMandatory= */ true);
        shadowOf(Looper.getMainLooper()).idle();
        executeWork(COUNTRY_WORKER_UNIQUE_NAME);
        executeWork(PLAY_INSTALL_WORKER_UNIQUE_NAME);

        // THEN go through correct ProvisioningProgress and schedule reset alarm.
        verifyProgressesSet(Arrays.asList(ProvisioningProgress.GETTING_DEVICE_READY,
                ProvisioningProgress.INSTALLING_KIOSK_APP,
                ProvisioningProgress.getMandatoryProvisioningFailedProgress(
                        PLAY_INSTALLATION_FAILED)));
        verify(mTestApp.getDeviceLockControllerScheduler()).scheduleMandatoryResetDeviceAlarm();

        // THEN provision failure is reported
        mTestWorkerFactory.setWorkerInputDataAssert(
                ReportDeviceProvisionStateWorker.class.getName(), data -> {
                    assertThat(data.getBoolean(KEY_IS_PROVISION_SUCCESSFUL, true)).isFalse();
                    assertThat(data.getInt(KEY_PROVISION_FAILURE_REASON,
                            UNKNOWN_REASON)).isEqualTo(NOT_IN_ELIGIBLE_COUNTRY);
                });
        executeWork(ReportDeviceProvisionStateWorker.REPORT_PROVISION_STATE_WORK_NAME);
    }

    @Test
    public void installKiosk_whenInstallationFailsAndIsMandatory_thenSetResetTimer()
            throws Exception {
        // GIVEN Country is approved and play installation would fail.
        mTestWorkerFactory.setWorkResult(COUNTRY_WORKER_CLASS_NAME,
                Result.success(
                        new Data.Builder().putBoolean(KEY_IS_IN_APPROVED_COUNTRY, true).build()));
        mTestWorkerFactory.setWorkResult(PLAY_INSTALL_WORKER_CLASS_NAME, Result.failure());


        // WHEN installation is executed for mandatory provisioning
        mProvisionHelper.scheduleKioskAppInstallation(mMockLifecycleOwner,
                mProgressController, /* isProvisionMandatory= */ true);
        shadowOf(Looper.getMainLooper()).idle();
        executeWork(COUNTRY_WORKER_UNIQUE_NAME);
        executeWork(PLAY_INSTALL_WORKER_UNIQUE_NAME);

        // THEN schedule reset alarm.
        verify(mTestApp.getDeviceLockControllerScheduler()).scheduleMandatoryResetDeviceAlarm();
    }

    @Test
    public void installKiosk_whenInstallationFailsAndIsMandatory_thenSetProgress()
            throws Exception {
        // GIVEN Country is approved and play installation would fail.
        mTestWorkerFactory.setWorkResult(COUNTRY_WORKER_CLASS_NAME,
                Result.success(
                        new Data.Builder().putBoolean(KEY_IS_IN_APPROVED_COUNTRY, true).build()));
        mTestWorkerFactory.setWorkResult(PLAY_INSTALL_WORKER_CLASS_NAME, Result.failure());


        // WHEN installation is executed for mandatory provisioning
        mProvisionHelper.scheduleKioskAppInstallation(mMockLifecycleOwner,
                mProgressController, /* isProvisionMandatory= */ true);
        shadowOf(Looper.getMainLooper()).idle();
        executeWork(COUNTRY_WORKER_UNIQUE_NAME);
        executeWork(PLAY_INSTALL_WORKER_UNIQUE_NAME);

        // THEN go through correct ProvisioningProgress.
        verifyProgressesSet(Arrays.asList(ProvisioningProgress.GETTING_DEVICE_READY,
                ProvisioningProgress.INSTALLING_KIOSK_APP,
                ProvisioningProgress.getMandatoryProvisioningFailedProgress(
                        PLAY_INSTALLATION_FAILED)));
    }

    @Test
    public void installKiosk_whenInstallationFailsAndIsNonMandatory_thenSetProgress()
            throws Exception {
        // GIVEN Country is approved and play installation would fail.
        mTestWorkerFactory.setWorkResult(COUNTRY_WORKER_CLASS_NAME,
                Result.success(
                        new Data.Builder().putBoolean(KEY_IS_IN_APPROVED_COUNTRY, true).build()));
        mTestWorkerFactory.setWorkResult(PLAY_INSTALL_WORKER_CLASS_NAME, Result.failure());


        // WHEN installation is executed for non-mandatory provisioning
        mProvisionHelper.scheduleKioskAppInstallation(mMockLifecycleOwner,
                mProgressController, /* isProvisionMandatory= */ false);
        shadowOf(Looper.getMainLooper()).idle();
        executeWork(COUNTRY_WORKER_UNIQUE_NAME);
        executeWork(PLAY_INSTALL_WORKER_UNIQUE_NAME);

        // THEN go through correct ProvisioningProgress.
        verifyProgressesSet(Arrays.asList(ProvisioningProgress.GETTING_DEVICE_READY,
                ProvisioningProgress.INSTALLING_KIOSK_APP,
                ProvisioningProgress.getNonMandatoryProvisioningFailedProgress(
                        PLAY_INSTALLATION_FAILED)));
    }

    @Test
    public void installKiosk_whenInstallationFailsAndIsNonMandatory_thenDoNotReportFailure()
            throws Exception {
        // GIVEN Country is approved and play installation would fail.
        mTestWorkerFactory.setWorkResult(COUNTRY_WORKER_CLASS_NAME,
                Result.success(
                        new Data.Builder().putBoolean(KEY_IS_IN_APPROVED_COUNTRY, true).build()));
        mTestWorkerFactory.setWorkResult(PLAY_INSTALL_WORKER_CLASS_NAME, Result.failure());


        // WHEN installation is executed for mandatory provisioning
        mProvisionHelper.scheduleKioskAppInstallation(mMockLifecycleOwner,
                mProgressController, /* isProvisionMandatory= */ false);
        shadowOf(Looper.getMainLooper()).idle();
        executeWork(COUNTRY_WORKER_UNIQUE_NAME);
        executeWork(PLAY_INSTALL_WORKER_UNIQUE_NAME);

        // THEN provision failure is not reported
        assertWorkNotEnqueued(ReportDeviceProvisionStateWorker.REPORT_PROVISION_STATE_WORK_NAME);
    }

    @Test
    public void pauseProvision_thenMarkProvisionForced() throws Exception {
        // GIVEN provision can be paused
        assertThat(GlobalParametersClient.getInstance().isProvisionForced().get()).isFalse();
        when(mMockStateController.setNextStateForEvent(eq(PROVISION_PAUSE))).thenReturn(
                Futures.immediateVoidFuture());

        // WHEN pauseProvision() is called.
        mProvisionHelper.pauseProvision();

        // THEN provision should be marked forced.
        assertThat(GlobalParametersClient.getInstance().isProvisionForced().get()).isTrue();
    }

    @Test
    public void pauseProvision_thenReportPause() throws Exception {
        // GIVEN provision can be paused
        assertThat(GlobalParametersClient.getInstance().isProvisionForced().get()).isFalse();
        when(mMockStateController.setNextStateForEvent(eq(PROVISION_PAUSE))).thenReturn(
                Futures.immediateVoidFuture());

        // WHEN pauseProvision() is called.
        mProvisionHelper.pauseProvision();

        // THEN pause provision should be reported.
        assertWorkEnqueued(REPORT_PROVISION_PAUSED_BY_USER_WORK);
    }

    @Test
    public void pauseProvision_thenScheduleResume() throws Exception {
        // GIVEN provision can be paused
        assertThat(GlobalParametersClient.getInstance().isProvisionForced().get()).isFalse();
        when(mMockStateController.setNextStateForEvent(eq(PROVISION_PAUSE))).thenReturn(
                Futures.immediateVoidFuture());

        // WHEN pauseProvision() is called.
        mProvisionHelper.pauseProvision();

        // THEN schedule resume
        verify(mTestApp.getDeviceLockControllerScheduler()).scheduleResumeProvisionAlarm();
    }

    @Test
    public void pauseProvision_withException_thenMarkProvisionForced() throws Exception {
        // GIVEN pause provisioning throws exception
        assertThat(GlobalParametersClient.getInstance().isProvisionForced().get()).isFalse();
        when(mMockStateController.setNextStateForEvent(eq(PROVISION_PAUSE))).thenReturn(
                Futures.immediateFailedFuture(new Throwable()));

        // WHEN pauseProvision() is called.
        mProvisionHelper.pauseProvision();

        // THEN provision should be marked forced
        assertThat(GlobalParametersClient.getInstance().isProvisionForced().get()).isTrue();
    }

    @Test
    public void pauseProvision_withException_thenNotScheduleResume() throws Exception {
        // GIVEN pause provisioning throws exception
        assertThat(GlobalParametersClient.getInstance().isProvisionForced().get()).isFalse();
        when(mMockStateController.setNextStateForEvent(eq(PROVISION_PAUSE))).thenReturn(
                Futures.immediateFailedFuture(new Throwable()));

        // WHEN pauseProvision() is called.
        mProvisionHelper.pauseProvision();

        // THEN provision pause should not be reported
        verify(mTestApp.getDeviceLockControllerScheduler(), never()).scheduleResumeProvisionAlarm();
    }

    @Test
    public void pauseProvision_withException_thenNotReportPause() throws Exception {
        // GIVEN pause provisioning throws exception
        assertThat(GlobalParametersClient.getInstance().isProvisionForced().get()).isFalse();
        when(mMockStateController.setNextStateForEvent(eq(PROVISION_PAUSE))).thenReturn(
                Futures.immediateFailedFuture(new Throwable()));

        // WHEN pauseProvision() is called.
        mProvisionHelper.pauseProvision();

        // THEN provision pause should not be reported
        assertWorkNotEnqueued(REPORT_PROVISION_PAUSED_BY_USER_WORK);
    }


    private void executeWork(String uniqueWorkName) throws Exception {
        List<WorkInfo> workInfoList = Futures.getChecked(
                WorkManager.getInstance(mTestApp)
                        .getWorkInfosForUniqueWork(uniqueWorkName), Exception.class);
        assertThat(workInfoList.size()).isEqualTo(1);
        mTestDriver.setAllConstraintsMet(workInfoList.get(0).getId());
        ShadowLooper.runUiThreadTasks();
    }

    private void installKioskApp() {
        ShadowPackageManager pm = Shadows.shadowOf(mTestApp.getPackageManager());
        PackageInfo kioskPackageInfo = new PackageInfo();
        kioskPackageInfo.packageName = TEST_KIOSK_PACKAGE;
        pm.installPackage(kioskPackageInfo);
    }

    private void checkKioskAppNotInstalled() {
        Preconditions.checkState(
                !mTestApp.getPackageManager().getInstalledPackages(
                                PackageManager.PackageInfoFlags.of(0))
                        .stream().anyMatch(
                                packageInfo -> TextUtils.equals(packageInfo.packageName,
                                        TEST_KIOSK_PACKAGE)));
    }

    private void setupLifecycle() {
        LifecycleRegistry mockLifecycle = new LifecycleRegistry(mMockLifecycleOwner);
        mockLifecycle.setCurrentState(State.RESUMED);
        when(mMockLifecycleOwner.getLifecycle()).thenReturn(mockLifecycle);
    }

    private static void setupSetupParameters() throws InterruptedException, ExecutionException {
        Bundle preferences = new Bundle();
        preferences.putString(EXTRA_KIOSK_PACKAGE, TEST_KIOSK_PACKAGE);
        SetupParametersClient.getInstance().createPrefs(preferences).get();
    }

    private void verifyProgressesSet(List<ProvisioningProgress> progressList) {
        verify(mProgressController, times(progressList.size())).setProvisioningProgress(
                mProvisioningProgressArgumentCaptor.capture());
        List<ProvisioningProgress> allValues = mProvisioningProgressArgumentCaptor.getAllValues();
        assertThat(allValues).containsAtLeastElementsIn(progressList);
    }

    private void assertWorkNotEnqueued(String uniqueWorkerName) throws Exception {
        List<WorkInfo> workInfoList = Futures.getChecked(WorkManager.getInstance(
                mTestApp).getWorkInfosForUniqueWork(uniqueWorkerName), Exception.class);
        assertThat(workInfoList).isEmpty();
    }

    private void assertWorkEnqueued(String uniqueWorkerName) throws Exception {
        List<WorkInfo> workInfoList = Futures.getChecked(WorkManager.getInstance(
                mTestApp).getWorkInfosForUniqueWork(uniqueWorkerName), Exception.class);
        assertThat(workInfoList).isNotEmpty();
    }

    /**
     * A {@link WorkerFactory} creates {@link ListenableWorker} which returns result based on
     * caller's input.
     */
    private class TestWorkerFactory extends WorkerFactory {

        private ArrayMap<String, Result> mWorkerResults = new ArrayMap<>();

        private ArrayMap<String, Consumer<Data>> mWorkerInputDataAssertions = new ArrayMap<>();

        public void setWorkResult(String workerClassName, Result result) {
            mWorkerResults.put(workerClassName, result);
        }

        public void setWorkerInputDataAssert(String workerClassName, Consumer<Data> assertion) {
            mWorkerInputDataAssertions.put(workerClassName, assertion);
        }

        @Override
        public ListenableWorker createWorker(@NonNull Context appContext,
                @NonNull String workerClassName, @NonNull WorkerParameters workerParameters) {
            return new ListenableWorker(appContext, workerParameters) {
                @NonNull
                @Override
                public ListenableFuture<Result> startWork() {
                    mWorkerInputDataAssertions.getOrDefault(workerClassName,
                            data -> {}).accept(getInputData());
                    return Futures.immediateFuture(
                            mWorkerResults.getOrDefault(workerClassName, Result.success()));
                }
            };
        }
    }
}
