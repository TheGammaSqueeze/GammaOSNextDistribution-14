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

package android.car.watchdoglib;

import static android.car.test.mocks.AndroidMockitoHelper.mockQueryService;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.automotive.watchdog.internal.ComponentType;
import android.automotive.watchdog.internal.ICarWatchdog;
import android.automotive.watchdog.internal.ICarWatchdogMonitor;
import android.automotive.watchdog.internal.ICarWatchdogServiceForSystem;
import android.automotive.watchdog.internal.PackageInfo;
import android.automotive.watchdog.internal.PackageIoOveruseStats;
import android.automotive.watchdog.internal.PowerCycle;
import android.automotive.watchdog.internal.ProcessIdentifier;
import android.automotive.watchdog.internal.ResourceOveruseConfiguration;
import android.automotive.watchdog.internal.ResourceStats;
import android.automotive.watchdog.internal.StateType;
import android.automotive.watchdog.internal.ThreadPolicyWithPriority;
import android.automotive.watchdog.internal.UserPackageIoUsageStats;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.mockito.Spy;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * <p>This class contains unit tests for the {@link CarWatchdogDaemonHelper}.
 */
public class CarWatchdogDaemonHelperTest {
    private static final String CAR_WATCHDOG_DAEMON_INTERFACE =
            "android.automotive.watchdog.internal.ICarWatchdog/default";
    private static final int MAX_WAIT_TIME_MS = 3000;

    @Mock CarWatchdogDaemonHelper.OnConnectionChangeListener mListener;
    @Mock private IBinder mBinder = new Binder();

    @Captor
    private ArgumentCaptor<IBinder.DeathRecipient> mDeathRecipientCaptor;

    @Spy private ICarWatchdog mFakeCarWatchdog = new FakeCarWatchdog();

    private CarWatchdogDaemonHelper mCarWatchdogDaemonHelper;
    private MockitoSession mMockSession;
    private IBinder.DeathRecipient mCarWatchdogDaemonBinderDeathRecipient;

    @Before
    public void setUp() throws Exception {
        mMockSession = mockitoSession()
                .initMocks(this)
                .strictness(Strictness.LENIENT)
                .spyStatic(ServiceManager.class)
                .startMocking();
        doReturn(mBinder).when(
                () -> ServiceManager.checkService(CAR_WATCHDOG_DAEMON_INTERFACE));
        when(mFakeCarWatchdog.asBinder()).thenReturn(mBinder);
        mockQueryService(CAR_WATCHDOG_DAEMON_INTERFACE, mBinder, mFakeCarWatchdog);
        mCarWatchdogDaemonHelper = new CarWatchdogDaemonHelper();
        mCarWatchdogDaemonHelper.connect();
        captureAndVerifyRegistrationWithDaemon();
    }

    @After
    public void tearDown() {
        try {
            mMockSession.finishMocking();
        } finally {
            // When using inline mock maker, clean up inline mocks to prevent OutOfMemory errors.
            // See https://github.com/mockito/mockito/issues/1614 and b/259280359.
            Mockito.framework().clearInlineMocks();
        }
    }

    @Test
    public void testConnection() {
        CarWatchdogDaemonHelper carWatchdogDaemonHelper = new CarWatchdogDaemonHelper();
        carWatchdogDaemonHelper.addOnConnectionChangeListener(mListener);

        carWatchdogDaemonHelper.connect();

        verify(mListener).onConnectionChange(true);
    }

    @Test
    public void testRemoveConnectionChangeListener() {
        CarWatchdogDaemonHelper carWatchdogDaemonHelper = new CarWatchdogDaemonHelper();
        carWatchdogDaemonHelper.addOnConnectionChangeListener(mListener);
        carWatchdogDaemonHelper.removeOnConnectionChangeListener(mListener);
        carWatchdogDaemonHelper.connect();

        verify(mListener, never()).onConnectionChange(true);
    }

    @Test
    public void testIndirectCall_RegisterUnregisterMediator() throws Exception {
        ICarWatchdogServiceForSystem service = new ICarWatchdogServiceForSystem.Default();

        mCarWatchdogDaemonHelper.registerCarWatchdogService(service);

        verify(mFakeCarWatchdog).registerCarWatchdogService(service);

        mCarWatchdogDaemonHelper.unregisterCarWatchdogService(service);

        verify(mFakeCarWatchdog).unregisterCarWatchdogService(service);
    }

    @Test
    public void testIndirectCall_RegisterUnregisterMonitor() throws Exception {
        ICarWatchdogMonitor monitor = new ICarWatchdogMonitor.Default();

        mCarWatchdogDaemonHelper.registerMonitor(monitor);

        verify(mFakeCarWatchdog).registerMonitor(monitor);

        mCarWatchdogDaemonHelper.unregisterMonitor(monitor);

        verify(mFakeCarWatchdog).unregisterMonitor(monitor);
    }

    @Test
    public void testIndirectCall_TellCarWatchdogServiceAlive() throws Exception {
        ICarWatchdogServiceForSystem service = new ICarWatchdogServiceForSystem.Default();
        List<ProcessIdentifier> processIdentifiers = new ArrayList<>();
        ProcessIdentifier processIdentifier = new ProcessIdentifier();
        processIdentifier.pid = 111;
        processIdentifier.startTimeMillis = 1000;

        mCarWatchdogDaemonHelper.tellCarWatchdogServiceAlive(service, processIdentifiers, 123456);

        verify(mFakeCarWatchdog).tellCarWatchdogServiceAlive(service, processIdentifiers, 123456);
    }

    @Test
    public void testIndirectCall_TellDumpFinished() throws Exception {
        ICarWatchdogMonitor monitor = new ICarWatchdogMonitor.Default();

        ProcessIdentifier processIdentifier = new ProcessIdentifier();
        processIdentifier.pid = 123456;
        processIdentifier.startTimeMillis = 1000;
        mCarWatchdogDaemonHelper.tellDumpFinished(monitor, processIdentifier);

        verify(mFakeCarWatchdog).tellDumpFinished(monitor, processIdentifier);
    }

    @Test
    public void testIndirectCall_NotifySystemStateChange() throws Exception {
        mCarWatchdogDaemonHelper.notifySystemStateChange(StateType.POWER_CYCLE,
                PowerCycle.POWER_CYCLE_SHUTDOWN_PREPARE, -1);

        verify(mFakeCarWatchdog).notifySystemStateChange(StateType.POWER_CYCLE,
                PowerCycle.POWER_CYCLE_SHUTDOWN_PREPARE, -1);
    }

    @Test
    public void testIndirectCall_updateResourceOveruseConfigurations() throws Exception {
        ResourceOveruseConfiguration config = new ResourceOveruseConfiguration();
        config.componentType = ComponentType.SYSTEM;
        List<ResourceOveruseConfiguration> configs = new ArrayList<>(Collections.singleton(config));

        mCarWatchdogDaemonHelper.updateResourceOveruseConfigurations(configs);

        verify(mFakeCarWatchdog).updateResourceOveruseConfigurations(eq(configs));
    }

    @Test
    public void testIndirectCall_getResourceOveruseConfigurations() throws Exception {
        ResourceOveruseConfiguration config = new ResourceOveruseConfiguration();
        config.componentType = ComponentType.SYSTEM;
        List<ResourceOveruseConfiguration> expected =
                new ArrayList<>(Collections.singleton(config));
        when(mFakeCarWatchdog.getResourceOveruseConfigurations()).thenReturn(expected);

        List<ResourceOveruseConfiguration> actual =
                mCarWatchdogDaemonHelper.getResourceOveruseConfigurations();

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void testIndirectCall_controlProcessHealthCheck() throws Exception {
        mCarWatchdogDaemonHelper.controlProcessHealthCheck(true);

        verify(mFakeCarWatchdog).controlProcessHealthCheck(eq(true));
    }

    /*
     * Test that the {@link CarWatchdogDaemonHelper} throws {@code IllegalArgumentException} when
     * trying to register already-registered service again.
     */
    @Test
    public void testMultipleRegistration() throws Exception {
        ICarWatchdogServiceForSystem service = new ICarWatchdogServiceForSystemImpl();

        mCarWatchdogDaemonHelper.registerCarWatchdogService(service);

        assertThrows(IllegalArgumentException.class,
                () -> mCarWatchdogDaemonHelper.registerCarWatchdogService(service));
    }

    /*
     * Test that the {@link CarWatchdogDaemonHelper} throws {@code IllegalArgumentException} when
     * trying to unregister not-registered service.
     */
    @Test
    public void testInvalidUnregistration() throws Exception {
        ICarWatchdogServiceForSystem service = new ICarWatchdogServiceForSystemImpl();

        assertThrows(IllegalArgumentException.class,
                () -> mCarWatchdogDaemonHelper.unregisterCarWatchdogService(service));
    }

    @Test
    public void testSetThreadPriority() throws Exception {
        int testPid = 1;
        int testTid = 2;
        int testUid = 3;
        int testPolicy = 4;
        int testPriority = 5;

        mCarWatchdogDaemonHelper.setThreadPriority(
                    testPid, testTid, testUid, testPolicy, testPriority);

        verify(mFakeCarWatchdog).setThreadPriority(
                testPid, testTid, testUid, testPolicy, testPriority);
    }

    @Test
    public void testSetThreadPriority_DaemonVersionTooLow() throws Exception {
        int testPid = 1;
        int testTid = 2;
        int testUid = 3;
        int testPolicy = 4;
        int testPriority = 5;
        when(mFakeCarWatchdog.getInterfaceVersion()).thenReturn(1);

        assertThrows(UnsupportedOperationException.class,
                () -> mCarWatchdogDaemonHelper.setThreadPriority(
                        testPid, testTid, testUid, testPolicy, testPriority));
    }

    @Test
    public void testGetThreadPriority() throws Exception {
        int testPid = 1;
        int testTid = 2;
        int testUid = 3;
        int testPolicy = 4;
        int testPriority = 5;
        ThreadPolicyWithPriority p = new ThreadPolicyWithPriority();
        p.policy = testPolicy;
        p.priority = testPriority;
        when(mFakeCarWatchdog.getThreadPriority(testPid, testTid, testUid))
                .thenReturn(p);

        int[] result = mCarWatchdogDaemonHelper.getThreadPriority(testPid, testTid, testUid);

        assertThat(result[0]).isEqualTo(testPolicy);
        assertThat(result[1]).isEqualTo(testPriority);
    }

    @Test
    public void testGetThreadPriority_DaemonVersionTooLow() throws Exception {
        int testPid = 1;
        int testTid = 2;
        int testUid = 3;
        when(mFakeCarWatchdog.getInterfaceVersion()).thenReturn(1);

        assertThrows(UnsupportedOperationException.class,
                () -> mCarWatchdogDaemonHelper.getThreadPriority(testPid, testTid, testUid));
    }

    @Test
    public void testOnAidlVhalPidFetched() throws Exception {
        int vhalPid = 12846;

        mCarWatchdogDaemonHelper.onAidlVhalPidFetched(vhalPid);

        verify(mFakeCarWatchdog).onAidlVhalPidFetched(vhalPid);
    }

    @Test
    public void testOnAidlVhalPidFetched_DaemonVersionTooLow() throws Exception {
        when(mFakeCarWatchdog.getInterfaceVersion()).thenReturn(1);

        assertThrows(UnsupportedOperationException.class,
                () -> mCarWatchdogDaemonHelper.onAidlVhalPidFetched(12846));
    }


    @Test
    public void testOnTodayIoUsageStatsFetched() throws Exception {
        List<UserPackageIoUsageStats> testUserPackageIoUsageStats = Collections.emptyList();

        mCarWatchdogDaemonHelper.onTodayIoUsageStatsFetched(testUserPackageIoUsageStats);

        verify(mFakeCarWatchdog).onTodayIoUsageStatsFetched(testUserPackageIoUsageStats);
    }

    @Test
    public void testOnTodayIoUsageStatsFetched_DaemonVersionTooLow() throws Exception {
        when(mFakeCarWatchdog.getInterfaceVersion()).thenReturn(2);

        assertThrows(UnsupportedOperationException.class,
                () -> mCarWatchdogDaemonHelper.onTodayIoUsageStatsFetched(Collections.emptyList()));
    }

    @Test
    public void testWatchdogDaemonRestart() throws Exception {
        mCarWatchdogDaemonHelper.addOnConnectionChangeListener(mListener);
        mCarWatchdogDaemonBinderDeathRecipient.binderDied();

        verify(mListener).onConnectionChange(false);

        // Wait for daemon to reconnect.
        verify(mBinder, timeout(MAX_WAIT_TIME_MS)).linkToDeath(mDeathRecipientCaptor.capture(),
                anyInt());

        verify(mListener, timeout(MAX_WAIT_TIME_MS)).onConnectionChange(true);
        mCarWatchdogDaemonBinderDeathRecipient = mDeathRecipientCaptor.getValue();
        assertWithMessage("Watchdog daemon binder death recipient")
                .that(mCarWatchdogDaemonBinderDeathRecipient).isNotNull();

        mCarWatchdogDaemonHelper.removeOnConnectionChangeListener(mListener);
    }

    private void captureAndVerifyRegistrationWithDaemon() throws RemoteException {
        verify(mBinder, atLeastOnce()).linkToDeath(mDeathRecipientCaptor.capture(), anyInt());
        mCarWatchdogDaemonBinderDeathRecipient = mDeathRecipientCaptor.getValue();
        assertWithMessage("Watchdog daemon binder death recipient")
                .that(mCarWatchdogDaemonBinderDeathRecipient).isNotNull();
    }

    // FakeCarWatchdog mimics ICarWatchdog daemon in local process.
    private final class FakeCarWatchdog extends ICarWatchdog.Default {
        private static final int UDC_INTERFACE_VERSION = 3;

        private final ArrayList<ICarWatchdogServiceForSystem> mServices = new ArrayList<>();

        @Override
        public void registerCarWatchdogService(
                ICarWatchdogServiceForSystem service) throws RemoteException {
            for (ICarWatchdogServiceForSystem curService : mServices) {
                if (curService == service) {
                    throw new IllegalArgumentException("Already registered service");
                }
            }
            mServices.add(service);
        }

        @Override
        public void unregisterCarWatchdogService(
                ICarWatchdogServiceForSystem service) throws RemoteException {
            for (ICarWatchdogServiceForSystem curService : mServices) {
                if (curService == service) {
                    mServices.remove(service);
                    return;
                }
            }
            throw new IllegalArgumentException("Not registered service");
        }

        @Override
        public int getInterfaceVersion() {
            return UDC_INTERFACE_VERSION;
        }
    }

    private final class ICarWatchdogServiceForSystemImpl extends ICarWatchdogServiceForSystem.Stub {
        @Override
        public void checkIfAlive(int sessionId, int timeout) {}

        @Override
        public void prepareProcessTermination() {}

        @Override
        public List<PackageInfo> getPackageInfosForUids(
                int[] uids, List<String> vendorPackagePrefixes) {
            return new ArrayList<>();
        }

        @Override
        public void latestIoOveruseStats(List<PackageIoOveruseStats> ioOveruseStats) {}

        @Override
        public void resetResourceOveruseStats(List<String> packageNames) {}

        @Override
        public List<UserPackageIoUsageStats> getTodayIoUsageStats() {
            return new ArrayList<>();
        }

        @Override
        public void onLatestResourceStats(List<ResourceStats> resourceStats) {}

        @Override
        public void requestAidlVhalPid() {}

        @Override
        public void requestTodayIoUsageStats() {}

        @Override
        public String getInterfaceHash() {
            return ICarWatchdogServiceForSystem.HASH;
        }

        @Override
        public int getInterfaceVersion() {
            return ICarWatchdogServiceForSystem.VERSION;
        }
    }
}
