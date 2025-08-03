/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.car.watchdog;

import static android.car.watchdog.CarWatchdogManager.FLAG_MINIMUM_STATS_IO_1_MB;
import static android.car.watchdog.CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO;
import static android.car.watchdog.CarWatchdogManager.STATS_PERIOD_CURRENT_DAY;
import static android.car.watchdog.CarWatchdogManager.TIMEOUT_CRITICAL;
import static android.car.watchdog.ResourceOveruseConfiguration.COMPONENT_TYPE_SYSTEM;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.car.Car;
import android.car.watchdog.CarWatchdogManager;
import android.car.watchdog.ICarWatchdogService;
import android.car.watchdog.ICarWatchdogServiceCallback;
import android.car.watchdog.IResourceOveruseListener;
import android.car.watchdog.IoOveruseAlertThreshold;
import android.car.watchdog.IoOveruseConfiguration;
import android.car.watchdog.IoOveruseStats;
import android.car.watchdog.PackageKillableState;
import android.car.watchdog.PerStateBytes;
import android.car.watchdog.ResourceOveruseConfiguration;
import android.car.watchdog.ResourceOveruseStats;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.ArrayMap;

import androidx.test.platform.app.InstrumentationRegistry;

import com.google.common.util.concurrent.SettableFuture;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@RunWith(MockitoJUnitRunner.class)
public class CarWatchdogManagerUnitTest {
    private static final String TAG = CarWatchdogManager.class.getSimpleName();
    private static final int MAX_WAIT_TIME_MS = 3000;

    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    @Spy
    private final Context mContext =
            InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final Executor mExecutor = mContext.getMainExecutor();

    @Mock private Car mCar;
    @Mock private Context mCarContext;
    @Mock private ApplicationInfo mApplicationInfo;
    @Mock private IBinder mBinder;
    @Mock private ICarWatchdogService mService;

    private CarWatchdogManager mCarWatchdogManager;

    @Before
    public void setUp() {
        when(mCar.getContext()).thenReturn(mCarContext);
        mApplicationInfo.targetSdkVersion = Build.VERSION_CODES.CUR_DEVELOPMENT;
        when(mCarContext.getApplicationInfo()).thenReturn(mApplicationInfo);
        when(mCar.getEventHandler()).thenReturn(mMainHandler);
        when(mCar.handleRemoteExceptionFromCarService(any(RemoteException.class), any()))
                .thenCallRealMethod();
        when(mBinder.queryLocalInterface(anyString())).thenReturn(mService);
        mCarWatchdogManager = new CarWatchdogManager(mCar, mBinder);
    }

    @After
    public void tearDown() {
        mApplicationInfo.targetSdkVersion = Build.VERSION_CODES.CUR_DEVELOPMENT;
    }

    @Test
    public void testRegisterClient() throws Exception {
        TestClient client = new TestClient();
        ICarWatchdogServiceCallback clientImpl = registerClient(client);
        mCarWatchdogManager.unregisterClient(client);
        verify(mService).unregisterClient(clientImpl);

        clientImpl.onCheckHealthStatus(123456, TIMEOUT_CRITICAL);
        verify(mService, never()).tellClientAlive(clientImpl, 123456);
    }

    @Test
    public void testUnregisterUnregisteredClient() throws Exception {
        TestClient client = new TestClient();
        mCarWatchdogManager.registerClient(mExecutor, client, TIMEOUT_CRITICAL);
        mCarWatchdogManager.unregisterClient(client);
        // The following call should not throw an exception.
        mCarWatchdogManager.unregisterClient(client);
    }

    @Test
    public void testRegisterMultipleClients() {
        TestClient client1 = new TestClient();
        TestClient client2 = new TestClient();
        mCarWatchdogManager.registerClient(mExecutor, client1, TIMEOUT_CRITICAL);
        assertThrows(IllegalStateException.class,
                () -> mCarWatchdogManager.registerClient(mExecutor, client2, TIMEOUT_CRITICAL));
    }

    @Test
    public void testRegisterUnregisterClientRaceConditionPostUdc() throws Exception {
        assumeTrue("Current development version is > UPSIDE_DOWN_CAKE",
                Build.VERSION_CODES.CUR_DEVELOPMENT > Build.VERSION_CODES.UPSIDE_DOWN_CAKE);

        TestClient client = new TestClient();
        CountDownLatch registerClientLatch = new CountDownLatch(1);
        CountDownLatch unregisterClientLatch = new CountDownLatch(1);
        doAnswer((args) -> {
            // Trigger CarWatchdogManager.unregisterClient call only after the registerClient call
            // is blocked on the service side.
            unregisterClientLatch.countDown();

            // In order to ensure that the unregisterClient call is completed, wait unit
            // the unregisterClient call is returned.
            registerClientLatch.await(MAX_WAIT_TIME_MS, TimeUnit.MILLISECONDS);
            return null;
        }).when(mService).registerClient(any(), eq(TIMEOUT_CRITICAL));

        final SettableFuture<ICarWatchdogServiceCallback> clientImplFuture =
                SettableFuture.create();
        mMainHandler.post(() -> {
            try {
                clientImplFuture.set(registerClient(client));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // In order to trigger `ClientInfo.isRegistrationInProgressLocked()` == `true`, wait until
        // the registerClient call is blocked.
        unregisterClientLatch.await(MAX_WAIT_TIME_MS, TimeUnit.MILLISECONDS);

        // IllegalStateException thrown on target SDK post UPSIDE_DOWN_CAKE.
        assertThrows(IllegalStateException.class,
                () -> mCarWatchdogManager.unregisterClient(client));

        // Unblock CarWatchdogManager.registerClient only after the unregisterClient has returned.
        registerClientLatch.countDown();

        ICarWatchdogServiceCallback clientImpl = clientImplFuture.get(MAX_WAIT_TIME_MS,
                TimeUnit.MILLISECONDS);
        verify(mService, never()).unregisterClient(clientImpl);

        clientImpl.onCheckHealthStatus(123456, TIMEOUT_CRITICAL);

        verify(mService, timeout(MAX_WAIT_TIME_MS)).tellClientAlive(clientImpl, 123456);
    }

    @Test
    public void testRegisterUnregisterClientRaceConditionPreVic() throws Exception {
        mApplicationInfo.targetSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE;

        TestClient client = new TestClient();
        CountDownLatch registerClientLatch = new CountDownLatch(1);
        CountDownLatch unregisterClientLatch = new CountDownLatch(1);
        doAnswer((args) -> {
            // Trigger CarWatchdogManager.unregisterClient call only after the registerClient call
            // is blocked on the service side.
            unregisterClientLatch.countDown();

            // In order to ensure that the unregisterClient call is completed, wait unit
            // the unregisterClient call is returned.
            registerClientLatch.await(MAX_WAIT_TIME_MS, TimeUnit.MILLISECONDS);
            return null;
        }).when(mService).registerClient(any(), eq(TIMEOUT_CRITICAL));

        final SettableFuture<ICarWatchdogServiceCallback> clientImplFuture =
                SettableFuture.create();
        mMainHandler.post(() -> {
            try {
                clientImplFuture.set(registerClient(client));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // In order to trigger `ClientInfo.isRegistrationInProgressLocked()` == `true`, wait until
        // the registerClient call is blocked.
        unregisterClientLatch.await(MAX_WAIT_TIME_MS, TimeUnit.MILLISECONDS);

        // No exception thrown on target SDK prior to VANILLA_ICE_CREAM.
        mCarWatchdogManager.unregisterClient(client);

        // Unblock CarWatchdogManager.registerClient only after the unregisterClient has returned.
        registerClientLatch.countDown();

        ICarWatchdogServiceCallback clientImpl = clientImplFuture.get(MAX_WAIT_TIME_MS,
                TimeUnit.MILLISECONDS);
        verify(mService, never()).unregisterClient(clientImpl);

        clientImpl.onCheckHealthStatus(123456, TIMEOUT_CRITICAL);

        verify(mService, timeout(MAX_WAIT_TIME_MS)).tellClientAlive(clientImpl, 123456);
    }

    @Test
    public void testHandlePongOnlyClient() throws Exception {
        testClientResponse(new TestClient());
    }

    @Test
    public void testHandleRedundantPongClient() throws Exception {
        testClientResponse(new RedundantPongClient());
    }

    @Test
    public void testHandleReturnAndPongClient() throws Exception {
        testClientResponse(new ReturnAndPongClient());
    }

    @Test
    public void testGetResourceOveruseStats() throws Exception {
        UserHandle userHandle = Process.myUserHandle();
        ResourceOveruseStats expectedStats =
                new ResourceOveruseStats.Builder("random.package", userHandle).build();
        when(mService.getResourceOveruseStats(FLAG_RESOURCE_OVERUSE_IO, STATS_PERIOD_CURRENT_DAY))
                .thenReturn(expectedStats);

        ResourceOveruseStats actualStats = mCarWatchdogManager.getResourceOveruseStats(
                FLAG_RESOURCE_OVERUSE_IO, STATS_PERIOD_CURRENT_DAY);

        assertThat(actualStats).isEqualTo(expectedStats);
    }

    @Test
    public void testFailsGetResourceOveruseStats() throws Exception {
        ResourceOveruseStats expectedStats =
                new ResourceOveruseStats.Builder("", UserHandle.CURRENT).build();
        when(mService.getResourceOveruseStats(FLAG_RESOURCE_OVERUSE_IO, STATS_PERIOD_CURRENT_DAY))
                .thenThrow(new RemoteException());

        ResourceOveruseStats actualStats = mCarWatchdogManager
                .getResourceOveruseStats(FLAG_RESOURCE_OVERUSE_IO, STATS_PERIOD_CURRENT_DAY);

        ResourceOveruseStatsSubject.isEquals(actualStats, expectedStats);
    }

    @Test
    public void testGetAllResourceOveruseStats() throws Exception {
        UserHandle userHandle = Process.myUserHandle();
        List<ResourceOveruseStats> expectedStats = new ArrayList<>();
        expectedStats.add(new ResourceOveruseStats.Builder("random.package.1", userHandle).build());
        expectedStats.add(new ResourceOveruseStats.Builder("random.package.2", userHandle).build());
        expectedStats.add(new ResourceOveruseStats.Builder("random.package.3", userHandle).build());
        when(mService.getAllResourceOveruseStats(FLAG_RESOURCE_OVERUSE_IO,
                FLAG_MINIMUM_STATS_IO_1_MB, STATS_PERIOD_CURRENT_DAY)).thenReturn(expectedStats);

        List<ResourceOveruseStats> actualStats = mCarWatchdogManager.getAllResourceOveruseStats(
                FLAG_RESOURCE_OVERUSE_IO, FLAG_MINIMUM_STATS_IO_1_MB, STATS_PERIOD_CURRENT_DAY);

        assertThat(actualStats).isEqualTo(expectedStats);
    }

    @Test
    public void testFailsGetAllResourceOveruseStats() throws Exception {
        when(mService.getAllResourceOveruseStats(FLAG_RESOURCE_OVERUSE_IO,
                FLAG_MINIMUM_STATS_IO_1_MB, STATS_PERIOD_CURRENT_DAY))
                .thenThrow(new RemoteException());

        List<ResourceOveruseStats> actualStats = mCarWatchdogManager
                .getAllResourceOveruseStats(FLAG_RESOURCE_OVERUSE_IO, FLAG_MINIMUM_STATS_IO_1_MB,
                        STATS_PERIOD_CURRENT_DAY);

        assertThat(actualStats).isEmpty();
    }

    @Test
    public void testGetResourceOveruseStatsForUserPackage() throws Exception {
        UserHandle userHandle = Process.myUserHandle();
        String packageName = "random.package";
        ResourceOveruseStats expectedStats =
                new ResourceOveruseStats.Builder(packageName, userHandle).build();
        when(mService.getResourceOveruseStatsForUserPackage(packageName, userHandle,
                FLAG_RESOURCE_OVERUSE_IO, STATS_PERIOD_CURRENT_DAY)).thenReturn(expectedStats);

        ResourceOveruseStats actualStats =
                mCarWatchdogManager.getResourceOveruseStatsForUserPackage(packageName, userHandle,
                        FLAG_RESOURCE_OVERUSE_IO, STATS_PERIOD_CURRENT_DAY);

        assertThat(actualStats).isEqualTo(expectedStats);
    }

    @Test
    public void testFailsGetResourceOveruseStatsForUserPackage() throws Exception {
        ResourceOveruseStats expectedStats =
                new ResourceOveruseStats.Builder("", UserHandle.of(100)).build();
        when(mService.getResourceOveruseStatsForUserPackage("random.package",
                UserHandle.of(100), FLAG_RESOURCE_OVERUSE_IO, STATS_PERIOD_CURRENT_DAY))
                .thenThrow(new RemoteException());

        ResourceOveruseStats actualStats = mCarWatchdogManager
                .getResourceOveruseStatsForUserPackage("random.package",
                        UserHandle.of(100), FLAG_RESOURCE_OVERUSE_IO, STATS_PERIOD_CURRENT_DAY);

        ResourceOveruseStatsSubject.isEquals(actualStats, expectedStats);
    }

    @Test
    public void testAddResourceOveruseListener() throws Exception {
        CarWatchdogManager.ResourceOveruseListener listener =
                mock(CarWatchdogManager.ResourceOveruseListener.class);

        mCarWatchdogManager.addResourceOveruseListener(
                mExecutor, FLAG_RESOURCE_OVERUSE_IO, listener);

        IResourceOveruseListener listenerImpl =
                captureListenerImplOnAdd(FLAG_RESOURCE_OVERUSE_IO);

        ResourceOveruseStats stats = setIoOveruseStats(sampleResourceOveruseStats()).build();
        listenerImpl.onOveruse(stats);

        verify(listener, timeout(MAX_WAIT_TIME_MS).times(1)).onOveruse(eq(stats));

        // Shouldn't call the underlying listener as the I/O overuse stats is null.
        listenerImpl.onOveruse(sampleResourceOveruseStats().build());

        mCarWatchdogManager.removeResourceOveruseListener(listener);
        verify(mService).removeResourceOveruseListener(listenerImpl);

        // Shouldn't call the listener as it is removed.
        listenerImpl.onOveruse(stats);

        verifyNoMoreInteractions(listener);
        verifyNoMoreInteractions(mService);
    }

    @Test
    public void testMultipleAddResourceOveruseListener() throws Exception {
        CarWatchdogManager.ResourceOveruseListener firstListener =
                mock(CarWatchdogManager.ResourceOveruseListener.class);
        CarWatchdogManager.ResourceOveruseListener secondListener =
                mock(CarWatchdogManager.ResourceOveruseListener.class);

        mCarWatchdogManager.addResourceOveruseListener(
                mExecutor, FLAG_RESOURCE_OVERUSE_IO, firstListener);

        IResourceOveruseListener listenerImpl =
                captureListenerImplOnAdd(FLAG_RESOURCE_OVERUSE_IO);

        mCarWatchdogManager.addResourceOveruseListener(
                mExecutor, FLAG_RESOURCE_OVERUSE_IO, secondListener);

        /*
         * Shouldn't add listener implementation to service again as the resource overuse flag
         * didn't change.
         */
        verifyNoMoreInteractions(mService);

        ResourceOveruseStats stats = setIoOveruseStats(sampleResourceOveruseStats()).build();
        listenerImpl.onOveruse(stats);

        verify(firstListener, timeout(MAX_WAIT_TIME_MS).times(1)).onOveruse(eq(stats));

        mCarWatchdogManager.removeResourceOveruseListener(firstListener);

        listenerImpl.onOveruse(stats);

        verifyNoMoreInteractions(firstListener);
        verify(secondListener, timeout(MAX_WAIT_TIME_MS).times(2)).onOveruse(eq(stats));

        mCarWatchdogManager.removeResourceOveruseListener(secondListener);

        // Should be called only once when removing last listener.
        verify(mService, times(1)).removeResourceOveruseListener(listenerImpl);
        verifyNoMoreInteractions(mService);
    }

    @Test
    public void testReAddResourceOveruseListener() throws Exception {
        CarWatchdogManager.ResourceOveruseListener firstListener =
                mock(CarWatchdogManager.ResourceOveruseListener.class);
        CarWatchdogManager.ResourceOveruseListener secondListener =
                mock(CarWatchdogManager.ResourceOveruseListener.class);

        mCarWatchdogManager.addResourceOveruseListener(
                mExecutor, FLAG_RESOURCE_OVERUSE_IO, firstListener);
        mCarWatchdogManager.addResourceOveruseListener(
                mExecutor, FLAG_RESOURCE_OVERUSE_IO, secondListener);
        mCarWatchdogManager.removeResourceOveruseListener(firstListener);
        mCarWatchdogManager.removeResourceOveruseListener(secondListener);

        mCarWatchdogManager.addResourceOveruseListener(
                mExecutor, FLAG_RESOURCE_OVERUSE_IO, firstListener);
        mCarWatchdogManager.removeResourceOveruseListener(firstListener);

        verify(mService, times(2)).addResourceOveruseListener(eq(FLAG_RESOURCE_OVERUSE_IO),
                any(IResourceOveruseListener.class));
        verify(mService, times(2))
                .removeResourceOveruseListener(any(IResourceOveruseListener.class));
        verifyNoMoreInteractions(mService);
    }

    @Test
    public void testRemoveRemovedResourceOveruseListener() throws Exception {
        CarWatchdogManager.ResourceOveruseListener listener =
                mock(CarWatchdogManager.ResourceOveruseListener.class);
        mCarWatchdogManager.addResourceOveruseListener(mExecutor, FLAG_RESOURCE_OVERUSE_IO,
                listener);
        mCarWatchdogManager.removeResourceOveruseListener(listener);
        // The following call should not throw an exception.
        mCarWatchdogManager.removeResourceOveruseListener(listener);
    }

    @Test
    public void testAddResourceOveruseListenerForSystem() throws Exception {
        CarWatchdogManager.ResourceOveruseListener listener =
                mock(CarWatchdogManager.ResourceOveruseListener.class);

        mCarWatchdogManager.addResourceOveruseListenerForSystem(
                mExecutor, FLAG_RESOURCE_OVERUSE_IO, listener);

        IResourceOveruseListener listenerImpl =
                captureListenerForSystemImplOnAdd(FLAG_RESOURCE_OVERUSE_IO);

        ResourceOveruseStats stats = setIoOveruseStats(sampleResourceOveruseStats()).build();
        listenerImpl.onOveruse(stats);

        verify(listener, timeout(MAX_WAIT_TIME_MS).times(1)).onOveruse(eq(stats));

        // Shouldn't call the underlying listener as the I/O overuse stats is null.
        listenerImpl.onOveruse(sampleResourceOveruseStats().build());

        mCarWatchdogManager.removeResourceOveruseListenerForSystem(listener);
        verify(mService).removeResourceOveruseListenerForSystem(listenerImpl);

        // Shouldn't call the listener as it is removed.
        listenerImpl.onOveruse(stats);

        verifyNoMoreInteractions(listener);
        verifyNoMoreInteractions(mService);
    }

    @Test
    public void testMultipleAddResourceOveruseListenerForSystem() throws Exception {
        CarWatchdogManager.ResourceOveruseListener firstListener =
                mock(CarWatchdogManager.ResourceOveruseListener.class);
        CarWatchdogManager.ResourceOveruseListener secondListener =
                mock(CarWatchdogManager.ResourceOveruseListener.class);

        mCarWatchdogManager.addResourceOveruseListenerForSystem(
                mExecutor, FLAG_RESOURCE_OVERUSE_IO, firstListener);

        IResourceOveruseListener listenerImpl =
                captureListenerForSystemImplOnAdd(FLAG_RESOURCE_OVERUSE_IO);

        mCarWatchdogManager.addResourceOveruseListenerForSystem(
                mExecutor, FLAG_RESOURCE_OVERUSE_IO, secondListener);

        /*
         * Shouldn't add listener implementation to service again as the resource overuse flag
         * didn't change.
         */
        verifyNoMoreInteractions(mService);

        ResourceOveruseStats stats = setIoOveruseStats(sampleResourceOveruseStats()).build();
        listenerImpl.onOveruse(stats);

        verify(firstListener, timeout(MAX_WAIT_TIME_MS).times(1)).onOveruse(eq(stats));

        mCarWatchdogManager.removeResourceOveruseListenerForSystem(firstListener);

        listenerImpl.onOveruse(stats);

        verifyNoMoreInteractions(firstListener);
        verify(secondListener, timeout(MAX_WAIT_TIME_MS).times(2)).onOveruse(eq(stats));

        mCarWatchdogManager.removeResourceOveruseListenerForSystem(secondListener);

        // Should be called only once when removing last listener.
        verify(mService, times(1)).removeResourceOveruseListenerForSystem(listenerImpl);
        verifyNoMoreInteractions(mService);
    }

    @Test
    public void testReAddResourceOveruseListenerForSystem() throws Exception {
        CarWatchdogManager.ResourceOveruseListener firstListener =
                mock(CarWatchdogManager.ResourceOveruseListener.class);
        CarWatchdogManager.ResourceOveruseListener secondListener =
                mock(CarWatchdogManager.ResourceOveruseListener.class);

        mCarWatchdogManager.addResourceOveruseListenerForSystem(
                mExecutor, FLAG_RESOURCE_OVERUSE_IO, firstListener);
        mCarWatchdogManager.addResourceOveruseListenerForSystem(
                mExecutor, FLAG_RESOURCE_OVERUSE_IO, secondListener);
        mCarWatchdogManager.removeResourceOveruseListenerForSystem(firstListener);
        mCarWatchdogManager.removeResourceOveruseListenerForSystem(secondListener);

        mCarWatchdogManager.addResourceOveruseListenerForSystem(
                mExecutor, FLAG_RESOURCE_OVERUSE_IO, firstListener);
        mCarWatchdogManager.removeResourceOveruseListenerForSystem(firstListener);

        verify(mService, times(2)).addResourceOveruseListenerForSystem(eq(FLAG_RESOURCE_OVERUSE_IO),
                any(IResourceOveruseListener.class));
        verify(mService, times(2))
                .removeResourceOveruseListenerForSystem(any(IResourceOveruseListener.class));
        verifyNoMoreInteractions(mService);
    }

    @Test
    public void testRemoveRemovedResourceOveruseListenerForSystem() throws Exception {
        CarWatchdogManager.ResourceOveruseListener listener =
                mock(CarWatchdogManager.ResourceOveruseListener.class);

        mCarWatchdogManager.addResourceOveruseListenerForSystem(mExecutor, FLAG_RESOURCE_OVERUSE_IO,
                listener);

        mCarWatchdogManager.removeResourceOveruseListenerForSystem(listener);

        // The following call should not throw an exception.
        mCarWatchdogManager.removeResourceOveruseListenerForSystem(listener);
    }

    @Test
    public void testSetKillablePackageAsUser() throws Exception {
        UserHandle userHandle = Process.myUserHandle();

        mCarWatchdogManager.setKillablePackageAsUser("random.package", userHandle,
                /*isKillable=*/true);

        verify(mService, times(1)).setKillablePackageAsUser("random.package", userHandle,
                /*isKillable=*/true);
    }

    @Test
    public void testGetPackageKillableStatesAsUser() throws Exception {
        UserHandle userHandle = Process.myUserHandle();
        List<PackageKillableState> expectedPackages = Arrays.asList(
                new PackageKillableState("random.package.A", /*userId=*/10,
                        PackageKillableState.KILLABLE_STATE_YES),
                new PackageKillableState("random.package.B", /*userId=*/11,
                        PackageKillableState.KILLABLE_STATE_NEVER));
        when(mService.getPackageKillableStatesAsUser(userHandle)).thenReturn(expectedPackages);

        List<PackageKillableState> actualPackages =
                mCarWatchdogManager.getPackageKillableStatesAsUser(userHandle);

        assertThat(actualPackages).isEqualTo(expectedPackages);
    }

    @Test
    public void testFailsGetPackageKillableStatesAsUser() throws Exception {
        when(mService.getPackageKillableStatesAsUser(UserHandle.of(100)))
                .thenThrow(new RemoteException());

        List<PackageKillableState> actualPackageStates =
                mCarWatchdogManager.getPackageKillableStatesAsUser(UserHandle.of(100));

        assertThat(actualPackageStates).isEmpty();
    }

    @Test
    public void testSetResourceOveruseConfigurations() throws Exception {
        List<ResourceOveruseConfiguration> configs =
                Collections.singletonList(sampleResourceOveruseConfigBuilder().build());

        when(mService.setResourceOveruseConfigurations(configs, FLAG_RESOURCE_OVERUSE_IO))
                .thenReturn(CarWatchdogManager.RETURN_CODE_SUCCESS);

        assertThat(mCarWatchdogManager.setResourceOveruseConfigurations(configs,
                FLAG_RESOURCE_OVERUSE_IO)).isEqualTo(CarWatchdogManager.RETURN_CODE_SUCCESS);

        verify(mService, times(1)).setResourceOveruseConfigurations(configs,
                FLAG_RESOURCE_OVERUSE_IO);
    }

    @Test
    public void testFailsSetResourceOveruseConfigurations() throws Exception {
        List<ResourceOveruseConfiguration> configs =
                Collections.singletonList(sampleResourceOveruseConfigBuilder().build());

        when(mService.setResourceOveruseConfigurations(configs, FLAG_RESOURCE_OVERUSE_IO))
                .thenThrow(new RemoteException());

        assertThat(mCarWatchdogManager.setResourceOveruseConfigurations(configs,
                FLAG_RESOURCE_OVERUSE_IO))
                .isEqualTo(CarWatchdogManager.RETURN_CODE_ERROR);
    }

    @Test
    public void testGetResourceOveruseConfigurations() throws Exception {
        List<ResourceOveruseConfiguration> expectedConfigs =
                Collections.singletonList(sampleResourceOveruseConfigBuilder().build());

        when(mService.getResourceOveruseConfigurations(FLAG_RESOURCE_OVERUSE_IO))
                .thenReturn(expectedConfigs);

        assertThat(mCarWatchdogManager.getResourceOveruseConfigurations(FLAG_RESOURCE_OVERUSE_IO))
                .isEqualTo(expectedConfigs);
    }

    @Test
    public void testFailsGetResourceOveruseConfigurations() throws Exception {
        when(mService.getResourceOveruseConfigurations(FLAG_RESOURCE_OVERUSE_IO))
                .thenThrow(new RemoteException());

        assertThat(mCarWatchdogManager.getResourceOveruseConfigurations(FLAG_RESOURCE_OVERUSE_IO))
                .isEqualTo(null);
    }

    @Test
    public void testIoOveruseAlertThresholdToStringAndDescribeContent() {
        IoOveruseAlertThreshold ioOveruseAlertThreshold = new IoOveruseAlertThreshold(1, 2);
        int content = ioOveruseAlertThreshold.describeContents();
        String string = ioOveruseAlertThreshold.toString();

        assertThat(content).isEqualTo(0);
        assertThat(string).isEqualTo(
                "IoOveruseAlertThreshold { durationInSeconds = 1, writtenBytesPerSecond = 2 }");
    }

    @Test
    public void testIoOveruseConfigurationToStringAndDescribeContent() {
        PerStateBytes expectedPerStateBytes = new PerStateBytes(6666666, 7777777, 8888888);
        IoOveruseConfiguration ioOveruseConfiguration = new IoOveruseConfiguration.Builder(
                expectedPerStateBytes,
                new HashMap<>(), new HashMap<>(), new ArrayList<>()).build();
        int content = ioOveruseConfiguration.describeContents();
        String string = ioOveruseConfiguration.toString();

        assertThat(content).isEqualTo(0);
        assertThat(string)
                .isEqualTo("IoOveruseConfiguration { componentLevelThresholds = "
                        + expectedPerStateBytes
                        + ", packageSpecificThresholds = {}, appCategorySpecificThresholds = {}, "
                        + "systemWideThresholds = [] }");
    }

    @Test
    public void testIoOveruseStatsDescribeContent() {
        IoOveruseStats ioOveruseStats =
                new IoOveruseStats.Builder(0, 10).setStartTime(1).setDurationInSeconds(5).build();
        int content = ioOveruseStats.describeContents();

        assertThat(content).isEqualTo(0);
    }

    @Test
    public void testPackageKillableStateToStringAndDescribeContent() {
        PackageKillableState packageKillableState = new PackageKillableState("packageName", 10, 1);
        int content = packageKillableState.describeContents();
        String string = packageKillableState.toString();

        assertThat(content).isEqualTo(0);
        assertThat(string).isEqualTo(
                "PackageKillableState { packageName = packageName, userId = 10, "
                        + "killableState = "
                        + PackageKillableState.killableStateToString(1)
                        + " }");
    }

    @Test
    public void testResourceOveruseConfigurationToStringAndDescribeContent() {
        ResourceOveruseConfiguration resourceOveruseConfiguration =
                new ResourceOveruseConfiguration.Builder(
                    ResourceOveruseConfiguration.COMPONENT_TYPE_SYSTEM, new ArrayList<>(),
                    new ArrayList<>(), new HashMap<>())
                .addPackagesToAppCategoryTypes("key", "value")
                .addSafeToKillPackages("safeToKillApp")
                .addVendorPackagePrefixes("vendorPackagePrefix")
                .build();
        int content = resourceOveruseConfiguration.describeContents();
        String string = resourceOveruseConfiguration.toString();

        assertThat(content).isEqualTo(0);
        assertThat(string)
                .isEqualTo("ResourceOveruseConfiguration { componentType = "
                        + ResourceOveruseConfiguration.componentTypeToString(
                                ResourceOveruseConfiguration.COMPONENT_TYPE_SYSTEM)
                        + ", safeToKillPackages = [safeToKillApp], vendorPackagePrefixes = "
                        + "[vendorPackagePrefix], packagesToAppCategoryTypes = {key=value}, "
                        + "ioOveruseConfiguration = null }");
    }

    private ICarWatchdogServiceCallback registerClient(
            CarWatchdogManager.CarWatchdogClientCallback client) throws Exception {
        mCarWatchdogManager.registerClient(mExecutor, client, TIMEOUT_CRITICAL);
        ArgumentCaptor<ICarWatchdogServiceCallback> clientImplCaptor =
                ArgumentCaptor.forClass(ICarWatchdogServiceCallback.class);

        verify(mService).registerClient(clientImplCaptor.capture(), eq(TIMEOUT_CRITICAL));
        return clientImplCaptor.getValue();
    }

    private ResourceOveruseConfiguration.Builder sampleResourceOveruseConfigBuilder() {
        IoOveruseConfiguration ioOveruseConfig = new IoOveruseConfiguration.Builder(
                /* componentLevelThresholds= */new PerStateBytes(200, 300, 400),
                /* packageSpecificThresholds= */new ArrayMap<>(),
                /* appCategorySpecificThresholds= */new ArrayMap<>(),
                /* systemWideThresholds= */new ArrayList<>()).build();
        ResourceOveruseConfiguration.Builder configBuilder =
                new ResourceOveruseConfiguration.Builder(
                        COMPONENT_TYPE_SYSTEM, /* safeTiKillPackages= */new ArrayList<>(),
                        /* vendorPackagePrefixes= */new ArrayList<>(),
                        /* packagesToAppCategoryTypes= */ new ArrayMap<>());
        configBuilder.setIoOveruseConfiguration(ioOveruseConfig);
        return configBuilder;
    }

    private void testClientResponse(CarWatchdogManager.CarWatchdogClientCallback client)
            throws Exception {
        ICarWatchdogServiceCallback clientImpl = registerClient(client);
        clientImpl.onCheckHealthStatus(123456, TIMEOUT_CRITICAL);
        verify(mService, timeout(MAX_WAIT_TIME_MS)).tellClientAlive(clientImpl, 123456);
    }

    private ResourceOveruseStats.Builder sampleResourceOveruseStats() {
        return new ResourceOveruseStats.Builder("", Process.myUserHandle());
    }

    private ResourceOveruseStats.Builder setIoOveruseStats(
            ResourceOveruseStats.Builder resourceOveruseStatsBuilder) {
        IoOveruseStats.Builder ioOveruseStatsBuilder = new IoOveruseStats.Builder(0, 120)
                .setTotalOveruses(10).setTotalTimesKilled(2)
                .setTotalBytesWritten(10 * 1024 * 1024).setKillableOnOveruse(true)
                .setRemainingWriteBytes(new PerStateBytes(1024, 1024 * 1024, 0));
        resourceOveruseStatsBuilder.setIoOveruseStats(ioOveruseStatsBuilder.build());
        return resourceOveruseStatsBuilder;
    }

    private IResourceOveruseListener captureListenerImplOnAdd(
            int resourceOveruseFlag) throws Exception {
        ArgumentCaptor<IResourceOveruseListener> listenerArgumentCaptor =
                ArgumentCaptor.forClass(IResourceOveruseListener.class);

        verify(mService, times(1)).addResourceOveruseListener(eq(resourceOveruseFlag),
                listenerArgumentCaptor.capture());
        return listenerArgumentCaptor.getValue();
    }

    private IResourceOveruseListener captureListenerForSystemImplOnAdd(
            int resourceOveruseFlag) throws Exception {
        ArgumentCaptor<IResourceOveruseListener> listenerArgumentCaptor =
                ArgumentCaptor.forClass(IResourceOveruseListener.class);

        verify(mService).addResourceOveruseListenerForSystem(eq(resourceOveruseFlag),
                listenerArgumentCaptor.capture());
        return listenerArgumentCaptor.getValue();
    }

    private final class TestClient extends CarWatchdogManager.CarWatchdogClientCallback {
        @Override
        public boolean onCheckHealthStatus(int sessionId, int timeout) {
            mMainHandler.post(() -> mCarWatchdogManager.tellClientAlive(this, sessionId));
            return false;
        }
    }

    private final class RedundantPongClient extends CarWatchdogManager.CarWatchdogClientCallback {
        @Override
        public boolean onCheckHealthStatus(int sessionId, int timeout) {
            mCarWatchdogManager.tellClientAlive(this, sessionId);
            mCarWatchdogManager.tellClientAlive(this, sessionId);
            return false;
        }
    }

    private final class ReturnAndPongClient extends CarWatchdogManager.CarWatchdogClientCallback {
        @Override
        public boolean onCheckHealthStatus(int sessionId, int timeout) {
            mMainHandler.post(() -> mCarWatchdogManager.tellClientAlive(this, sessionId));
            return true;
        }
    }
}
