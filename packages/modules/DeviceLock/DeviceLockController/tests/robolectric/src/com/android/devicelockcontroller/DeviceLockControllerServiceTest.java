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

import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_KIOSK_PACKAGE;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteCallback;
import android.os.RemoteException;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.rule.ServiceTestRule;

import com.android.devicelockcontroller.policy.DeviceStateController;
import com.android.devicelockcontroller.policy.FinalizationController;
import com.android.devicelockcontroller.stats.StatsLogger;
import com.android.devicelockcontroller.stats.StatsLoggerProvider;
import com.android.devicelockcontroller.storage.SetupParametersClient;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowPackageManager;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.util.concurrent.Futures;

@RunWith(RobolectricTestRunner.class)
public final class DeviceLockControllerServiceTest {

    @Rule
    public final ServiceTestRule mServiceRule = new ServiceTestRule();

    private static final int KIOSK_APP_UID = 123;

    private static final String KIOSK_APP_PACKAGE_NAME = "TEST_PACKAGE";

    private StatsLogger mStatsLogger;
    private TestDeviceLockControllerApplication mTestApp;

    @Before
    public void setUp() throws TimeoutException, ExecutionException, InterruptedException {
        mTestApp = ApplicationProvider.getApplicationContext();
        StatsLoggerProvider loggerProvider =
                (StatsLoggerProvider) mTestApp.getApplicationContext();
        mStatsLogger = loggerProvider.getStatsLogger();

        // Put Kiosk app package name and UID into SetupParameters and shadow PackageManager
        setupSetupParameters();
        ShadowPackageManager packageManager = Shadows.shadowOf(mTestApp.getPackageManager());
        packageManager.setPackagesForUid(KIOSK_APP_UID, KIOSK_APP_PACKAGE_NAME);
        final ShadowApplication shadowApplication = Shadows.shadowOf(mTestApp);

        // Setup the service for DeviceLockControllerService using Robolectric
        DeviceLockControllerService dlcService = Robolectric.setupService(
                DeviceLockControllerService.class);
        shadowApplication.setComponentNameAndServiceForBindService(
                new ComponentName(mTestApp, DeviceLockControllerService.class),
                dlcService.onBind(/* intent =*/null));
        shadowApplication.setBindServiceCallsOnServiceConnectedDirectly(/* callDirectly =*/true);
    }

    @Test
    public void lockDevice_shouldLogKioskRequest_andLogLockSuccess() throws RemoteException,
            TimeoutException {
        Intent serviceIntent = new Intent(mTestApp, DeviceLockControllerService.class);
        IBinder binder = mServiceRule.bindService(serviceIntent);
        when(mTestApp.getDeviceStateController().lockDevice()).thenReturn(
                Futures.immediateVoidFuture());

        assertThat(binder).isNotNull();

        IDeviceLockControllerService.Stub serviceStub = (IDeviceLockControllerService.Stub) binder;
        serviceStub.lockDevice(new RemoteCallback((result -> {})));

        verify(mStatsLogger).logKioskAppRequest(eq(KIOSK_APP_UID));
        verify(mStatsLogger).logSuccessfulLockingDevice();
    }

    @Test
    public void lockDevice_failure_shouldLogToStatsLogger() throws TimeoutException,
            RemoteException {
        DeviceStateController deviceStateController = mTestApp.getDeviceStateController();
        Intent serviceIntent = new Intent(mTestApp, DeviceLockControllerService.class);
        IBinder binder = mServiceRule.bindService(serviceIntent);
        when(deviceStateController.lockDevice()).thenReturn(
                Futures.immediateFailedFuture(new RuntimeException("Test Exception")));
        when(deviceStateController.getDeviceState()).thenReturn(Futures.immediateFuture(
                DeviceStateController.DeviceState.UNLOCKED));

        assertThat(binder).isNotNull();

        IDeviceLockControllerService.Stub serviceStub = (IDeviceLockControllerService.Stub) binder;
        serviceStub.lockDevice(new RemoteCallback((result -> {})));

        verify(mStatsLogger).logLockDeviceFailure(DeviceStateController.DeviceState.UNLOCKED);
    }

    @Test
    public void unlockDevice_shouldLogKioskRequest_AndLogUnlockSuccess() throws RemoteException,
            TimeoutException {
        Intent serviceIntent = new Intent(mTestApp, DeviceLockControllerService.class);
        IBinder binder = mServiceRule.bindService(serviceIntent);
        when(mTestApp.getDeviceStateController().unlockDevice()).thenReturn(
                Futures.immediateVoidFuture());

        assertThat(binder).isNotNull();

        IDeviceLockControllerService.Stub serviceStub = (IDeviceLockControllerService.Stub) binder;
        serviceStub.unlockDevice(new RemoteCallback((result -> {})));

        verify(mStatsLogger).logKioskAppRequest(eq(KIOSK_APP_UID));
        verify(mStatsLogger).logSuccessfulUnlockingDevice();
    }

    @Test
    public void unlockDevice_failure_shouldLogToStatsLogger() throws TimeoutException,
            RemoteException {
        DeviceStateController deviceStateController = mTestApp.getDeviceStateController();
        Intent serviceIntent = new Intent(mTestApp, DeviceLockControllerService.class);
        IBinder binder = mServiceRule.bindService(serviceIntent);
        when(deviceStateController.unlockDevice()).thenReturn(
                Futures.immediateFailedFuture(new RuntimeException("Test Exception")));
        when(deviceStateController.getDeviceState()).thenReturn(Futures.immediateFuture(
                DeviceStateController.DeviceState.LOCKED));

        assertThat(binder).isNotNull();

        IDeviceLockControllerService.Stub serviceStub = (IDeviceLockControllerService.Stub) binder;
        serviceStub.unlockDevice(new RemoteCallback((result -> {})));

        verify(mStatsLogger).logUnlockDeviceFailure(DeviceStateController.DeviceState.LOCKED);
    }

    @Test
    public void isDeviceLocked_shouldLogKioskRequest() throws RemoteException, TimeoutException {
        Intent serviceIntent = new Intent(mTestApp, DeviceLockControllerService.class);
        IBinder binder = mServiceRule.bindService(serviceIntent);
        when(mTestApp.getDeviceStateController().isLocked()).thenReturn(
                Futures.immediateFuture(true));

        assertThat(binder).isNotNull();

        IDeviceLockControllerService.Stub serviceStub = (IDeviceLockControllerService.Stub) binder;
        serviceStub.isDeviceLocked(new RemoteCallback((result -> {})));

        verify(mStatsLogger).logKioskAppRequest(eq(KIOSK_APP_UID));
    }

    @Test
    public void getDeviceIdentifier_shouldLogKioskRequest()
            throws RemoteException, TimeoutException {
        Intent serviceIntent = new Intent(mTestApp, DeviceLockControllerService.class);
        IBinder binder = mServiceRule.bindService(serviceIntent);

        assertThat(binder).isNotNull();

        IDeviceLockControllerService.Stub serviceStub = (IDeviceLockControllerService.Stub) binder;
        serviceStub.getDeviceIdentifier(new RemoteCallback((result -> {})));

        verify(mStatsLogger).logKioskAppRequest(eq(KIOSK_APP_UID));
    }

    @Test
    public void clearDeviceRestrictions_shouldLogKioskRequest()
            throws RemoteException, TimeoutException {
        Intent serviceIntent = new Intent(mTestApp, DeviceLockControllerService.class);
        IBinder binder = mServiceRule.bindService(serviceIntent);
        DeviceStateController deviceStateController = mTestApp.getDeviceStateController();
        when(deviceStateController.clearDevice()).thenReturn(Futures.immediateVoidFuture());
        FinalizationController finalizationController = mTestApp.getFinalizationController();
        when(finalizationController.notifyRestrictionsCleared()).
                thenReturn(Futures.immediateVoidFuture());

        assertThat(binder).isNotNull();

        IDeviceLockControllerService.Stub serviceStub = (IDeviceLockControllerService.Stub) binder;
        serviceStub.clearDeviceRestrictions(new RemoteCallback((result -> {})));

        verify(mStatsLogger).logKioskAppRequest(eq(KIOSK_APP_UID));
    }

    private static void setupSetupParameters() throws InterruptedException, ExecutionException {
        Bundle preferences = new Bundle();
        preferences.putString(EXTRA_KIOSK_PACKAGE, KIOSK_APP_PACKAGE_NAME);
        SetupParametersClient.getInstance().createPrefs(preferences).get();
    }
}
