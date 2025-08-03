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

import static android.car.test.mocks.AndroidMockitoHelper.mockQueryService;
import static android.car.test.mocks.AndroidMockitoHelper.mockUmGetUserHandles;
import static android.car.test.mocks.AndroidMockitoHelper.mockUmIsUserRunning;
import static android.car.watchdog.CarWatchdogManager.TIMEOUT_CRITICAL;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.StatsManager;
import android.automotive.watchdog.internal.ICarWatchdog;
import android.automotive.watchdog.internal.ICarWatchdogServiceForSystem;
import android.automotive.watchdog.internal.ProcessIdentifier;
import android.car.Car;
import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.car.watchdog.CarWatchdogManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.UserManager;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.car.CarLocalServices;
import com.android.car.CarServiceUtils;
import com.android.car.CarUxRestrictionsManagerService;
import com.android.car.power.CarPowerManagementService;
import com.android.car.systeminterface.SystemInterface;
import com.android.car.user.CarUserService;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * <p>This class contains unit tests for the {@link CarWatchdogService}.
 */
@RunWith(MockitoJUnitRunner.class)
public class CarWatchdogServiceTest extends AbstractExtendedMockitoTestCase {
    private static final String CAR_WATCHDOG_DAEMON_INTERFACE =
            "android.automotive.watchdog.internal.ICarWatchdog/default";
    private static final int MAX_WAIT_TIME_MS = 3000;
    private static final int INVALID_SESSION_ID = -1;
    private static final int RECURRING_OVERUSE_TIMES = 2;
    private static final int RECURRING_OVERUSE_PERIOD_IN_DAYS = 2;
    private static final String CANONICAL_PACKAGE_NAME =
            CarWatchdogServiceTest.class.getCanonicalName();

    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final Executor mExecutor =
            InstrumentationRegistry.getInstrumentation().getTargetContext().getMainExecutor();
    private final TestTimeSource mTimeSource = new TestTimeSource();
    private final UserHandle[] mUsers = new UserHandle[] {
            UserHandle.of(100),
            UserHandle.of(101)
    };

    @Mock private Context mMockContext;
    @Mock private Context mMockBuiltinPackageContext;
    @Mock private Car mMockCar;
    @Mock private Resources mMockResources;
    @Mock private UserManager mMockUserManager;
    @Mock private StatsManager mMockStatsManager;
    @Mock private SystemInterface mMockSystemInterface;
    @Mock private CarUserService mMockCarUserService;
    @Mock private CarUxRestrictionsManagerService mMockCarUxRestrictionsManagerService;
    @Mock private CarPowerManagementService mMockCarPowerManagementService;
    @Mock private IBinder mMockDaemonBinder;
    @Mock private IBinder mMockServiceBinder;
    @Mock private ICarWatchdog mMockCarWatchdogDaemon;
    @Mock private WatchdogStorage mMockWatchdogStorage;
    @Mock private PackageManager mMockPackageManager;

    @Captor private ArgumentCaptor<List<ProcessIdentifier>> mProcessIdentifiersCaptor;

    private CarWatchdogService mCarWatchdogService;
    private ICarWatchdogServiceForSystem mWatchdogServiceForSystemImpl;

    public CarWatchdogServiceTest() {
        super(CarWatchdogService.TAG);
    }

    @Before
    public void setUp() throws Exception {
        mockQueryService(CAR_WATCHDOG_DAEMON_INTERFACE, mMockDaemonBinder, mMockCarWatchdogDaemon);
        when(mMockCar.getEventHandler()).thenReturn(mMainHandler);
        when(mMockContext.getSystemService(UserManager.class)).thenReturn(mMockUserManager);
        when(mMockContext.getSystemService(StatsManager.class)).thenReturn(mMockStatsManager);
        when(mMockContext.getResources()).thenReturn(mMockResources);
        when(mMockResources.getInteger(
                com.android.car.R.integer.recurringResourceOverusePeriodInDays))
                .thenReturn(RECURRING_OVERUSE_PERIOD_IN_DAYS);
        when(mMockResources.getInteger(
                com.android.car.R.integer.recurringResourceOveruseTimes))
                .thenReturn(RECURRING_OVERUSE_TIMES);
        when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);

        doReturn(mMockSystemInterface)
                .when(() -> CarLocalServices.getService(SystemInterface.class));
        doReturn(mMockCarUserService)
                .when(() -> CarLocalServices.getService(CarUserService.class));
        doReturn(mMockCarUxRestrictionsManagerService)
                .when(() -> CarLocalServices.getService(CarUxRestrictionsManagerService.class));
        doReturn(mMockCarPowerManagementService)
                .when(() -> CarLocalServices.getService(CarPowerManagementService.class));

        mockUmGetUserHandles(mMockUserManager, /* excludeDying= */ false, mUsers);
        mockUmIsUserRunning(mMockUserManager, 100, true);
        mockUmIsUserRunning(mMockUserManager, 101, false);
        mockPackageManager();

        mCarWatchdogService = new CarWatchdogService(mMockContext, mMockBuiltinPackageContext,
                mMockWatchdogStorage, mTimeSource);

        when(mMockServiceBinder.queryLocalInterface(anyString())).thenReturn(mCarWatchdogService);

        mCarWatchdogService.init();
        mWatchdogServiceForSystemImpl = registerCarWatchdogService();
    }

    @Override
    protected void onSessionBuilder(CustomMockitoSessionBuilder builder) {
        builder
            .spyStatic(CarLocalServices.class)
            .spyStatic(CarServiceUtils.class)
            .spyStatic(ServiceManager.class)
            .spyStatic(UserHandle.class);
    }

    @Test
    public void testRegisterUnregisterClient() throws Exception {
        TestClient client = new TestClient(new SelfCheckGoodClient());
        client.registerClient();
        assertThat(mCarWatchdogService.getClientCount(TIMEOUT_CRITICAL)).isEqualTo(1);
        client.unregisterClient();
        assertThat(mCarWatchdogService.getClientCount(TIMEOUT_CRITICAL)).isEqualTo(0);
    }

    @Test
    public void testNoSelfCheckGoodClient() throws Exception {
        testClientResponse(new NoSelfCheckGoodClient(), 0);
    }

    @Test
    public void testSelfCheckGoodClient() throws Exception {
        testClientResponse(new SelfCheckGoodClient(), 0);
    }

    @Test
    public void testBadClient() throws Exception {
        BadTestClient client = new BadTestClient();
        testClientResponse(client, 1);
        assertThat(client.makeSureProcessTerminationNotified()).isEqualTo(true);
    }

    @Test
    public void testClientUnderStoppedUser() throws Exception {
        expectStoppedUser();
        TestClient client = new TestClient(new BadTestClient());
        client.registerClient();
        mWatchdogServiceForSystemImpl.checkIfAlive(123456, TIMEOUT_CRITICAL);
        verify(mMockCarWatchdogDaemon, timeout(MAX_WAIT_TIME_MS))
                .tellCarWatchdogServiceAlive(eq(mWatchdogServiceForSystemImpl),
                        mProcessIdentifiersCaptor.capture(), eq(123456));
        assertThat(mProcessIdentifiersCaptor.getValue()).isEmpty();
        mWatchdogServiceForSystemImpl.checkIfAlive(987654, TIMEOUT_CRITICAL);
        verify(mMockCarWatchdogDaemon, timeout(MAX_WAIT_TIME_MS))
                .tellCarWatchdogServiceAlive(eq(mWatchdogServiceForSystemImpl),
                        mProcessIdentifiersCaptor.capture(), eq(987654));
        assertThat(mProcessIdentifiersCaptor.getValue()).isEmpty();
    }

    @Test
    public void testMultipleClients() throws Exception {
        expectRunningUser();
        ArrayList<TestClient> clients = new ArrayList<>(Arrays.asList(
                new TestClient(new NoSelfCheckGoodClient()),
                new TestClient(new SelfCheckGoodClient()),
                new TestClient(new BadTestClient()),
                new TestClient(new BadTestClient())
        ));
        for (int i = 0; i < clients.size(); i++) {
            clients.get(i).registerClient();
        }

        mWatchdogServiceForSystemImpl.checkIfAlive(123456, TIMEOUT_CRITICAL);
        for (int i = 0; i < clients.size(); i++) {
            assertThat(clients.get(i).mAndroidClient.makeSureHealthCheckDone()).isEqualTo(true);
        }
        verify(mMockCarWatchdogDaemon, timeout(MAX_WAIT_TIME_MS))
                .tellCarWatchdogServiceAlive(eq(mWatchdogServiceForSystemImpl),
                        mProcessIdentifiersCaptor.capture(), eq(123456));
        assertThat(mProcessIdentifiersCaptor.getValue()).isEmpty();

        mWatchdogServiceForSystemImpl.checkIfAlive(987654, TIMEOUT_CRITICAL);

        verify(mMockCarWatchdogDaemon, timeout(MAX_WAIT_TIME_MS))
                .tellCarWatchdogServiceAlive(eq(mWatchdogServiceForSystemImpl),
                        mProcessIdentifiersCaptor.capture(), eq(987654));

        assertThat(mProcessIdentifiersCaptor.getValue().size()).isEqualTo(2);
    }

    private ICarWatchdogServiceForSystem registerCarWatchdogService() throws Exception {
        ArgumentCaptor<ICarWatchdogServiceForSystem> watchdogServiceForSystemImplCaptor =
                ArgumentCaptor.forClass(ICarWatchdogServiceForSystem.class);
        // Registering to daemon is done through a message handler. So, a buffer time of 1000ms is
        // given.
        verify(mMockCarWatchdogDaemon, timeout(1000))
                .registerCarWatchdogService(watchdogServiceForSystemImplCaptor.capture());
        return watchdogServiceForSystemImplCaptor.getValue();
    }

    private void testClientResponse(BaseAndroidClient androidClient, int badClientCount)
            throws Exception {
        expectRunningUser();
        TestClient client = new TestClient(androidClient);
        client.registerClient();
        mWatchdogServiceForSystemImpl.checkIfAlive(123456, TIMEOUT_CRITICAL);
        verify(mMockCarWatchdogDaemon, timeout(MAX_WAIT_TIME_MS))
                .tellCarWatchdogServiceAlive(eq(mWatchdogServiceForSystemImpl),
                        mProcessIdentifiersCaptor.capture(), eq(123456));
        // Checking Android client health is asynchronous, so wait at most 1 second.
        int repeat = 10;
        while (repeat > 0) {
            int sessionId = androidClient.getLastSessionId();
            if (sessionId != INVALID_SESSION_ID) {
                break;
            }
            SystemClock.sleep(100L);
            repeat--;
        }
        assertThat(androidClient.getLastSessionId()).isNotEqualTo(INVALID_SESSION_ID);
        assertThat(mProcessIdentifiersCaptor.getValue()).isEmpty();
        assertThat(androidClient.makeSureHealthCheckDone()).isEqualTo(true);

        mWatchdogServiceForSystemImpl.checkIfAlive(987654, TIMEOUT_CRITICAL);

        verify(mMockCarWatchdogDaemon, timeout(MAX_WAIT_TIME_MS))
                .tellCarWatchdogServiceAlive(eq(mWatchdogServiceForSystemImpl),
                        mProcessIdentifiersCaptor.capture(), eq(987654));

        assertThat(mProcessIdentifiersCaptor.getValue().size()).isEqualTo(badClientCount);
    }

    private void expectRunningUser() {
        doReturn(100).when(() -> UserHandle.getUserId(Binder.getCallingUid()));
    }

    private void expectStoppedUser() {
        doReturn(101).when(() -> UserHandle.getUserId(Binder.getCallingUid()));
    }

    private void mockPackageManager() {
        when(mMockPackageManager.getNamesForUids(any())).thenAnswer(
                args -> new String[]{CANONICAL_PACKAGE_NAME});
    }

    private final class TestClient {
        final CarWatchdogManager mCarWatchdogManager;
        BaseAndroidClient mAndroidClient;

        TestClient(BaseAndroidClient actualClient) {
            mCarWatchdogManager = new CarWatchdogManager(mMockCar, mMockServiceBinder);
            mAndroidClient = actualClient;
            actualClient.setManager(mCarWatchdogManager);
        }

        public void registerClient() {
            mCarWatchdogManager.registerClient(mExecutor, mAndroidClient, TIMEOUT_CRITICAL);
        }

        public void unregisterClient() {
            mCarWatchdogManager.unregisterClient(mAndroidClient);
        }
    }

    private abstract static class BaseAndroidClient
            extends CarWatchdogManager.CarWatchdogClientCallback {
        protected final CountDownLatch mLatchHealthCheckDone = new CountDownLatch(1);
        protected final CountDownLatch mLatchProcessTermination = new CountDownLatch(1);
        protected CarWatchdogManager mManager;
        protected int mLastSessionId = INVALID_SESSION_ID;

        @Override
        public boolean onCheckHealthStatus(int sessionId, int timeout) {
            mLastSessionId = sessionId;
            return false;
        }

        @Override
        public void onPrepareProcessTermination() {
            mLatchProcessTermination.countDown();
        }

        public int getLastSessionId() {
            return mLastSessionId;
        }

        public boolean makeSureProcessTerminationNotified() {
            try {
                return mLatchProcessTermination.await(1000, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ignore) {
            }
            return false;
        }

        public boolean makeSureHealthCheckDone() {
            try {
                return mLatchHealthCheckDone.await(1000, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ignore) {
            }
            return false;
        }

        public void setManager(CarWatchdogManager manager) {
            mManager = manager;
        }
    }

    private final class SelfCheckGoodClient extends BaseAndroidClient {
        @Override
        public boolean onCheckHealthStatus(int sessionId, int timeout) {
            super.onCheckHealthStatus(sessionId, timeout);
            mMainHandler.post(() -> {
                mManager.tellClientAlive(this, sessionId);
                mLatchHealthCheckDone.countDown();
            });
            return false;
        }
    }

    private static final class NoSelfCheckGoodClient extends BaseAndroidClient {
        @Override
        public boolean onCheckHealthStatus(int sessionId, int timeout) {
            super.onCheckHealthStatus(sessionId, timeout);
            mLatchHealthCheckDone.countDown();
            return true;
        }
    }

    private static final class BadTestClient extends BaseAndroidClient {
        @Override
        public boolean onCheckHealthStatus(int sessionId, int timeout) {
            super.onCheckHealthStatus(sessionId, timeout);
            mLatchHealthCheckDone.countDown();
            return false;
        }
    }

    private static final class TestTimeSource extends TimeSource {
        private static final Instant TEST_DATE_TIME = Instant.parse("2021-11-12T13:14:15.16Z");
        private Instant mNow;
        TestTimeSource() {
            mNow = TEST_DATE_TIME;
        }

        @Override
        public Instant now() {
            /* Return the same time, so the tests are deterministic. */
            return mNow;
        }

        @Override
        public String toString() {
            return "Mocked date to " + now();
        }
    }
}
