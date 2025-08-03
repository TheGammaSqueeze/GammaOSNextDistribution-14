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

import static android.car.drivingstate.CarUxRestrictions.UX_RESTRICTIONS_BASELINE;
import static android.car.test.mocks.AndroidMockitoHelper.mockUmGetAllUsers;
import static android.car.test.mocks.AndroidMockitoHelper.mockUmGetUserHandles;
import static android.car.test.mocks.AndroidMockitoHelper.mockUmIsUserRunning;
import static android.car.test.util.AndroidHelper.assertFilterHasActions;
import static android.car.test.util.AndroidHelper.assertFilterHasDataScheme;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_POST_UNLOCKED;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_SWITCHING;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_UNLOCKING;
import static android.car.watchdog.CarWatchdogManager.FLAG_MINIMUM_STATS_IO_1_MB;
import static android.car.watchdog.CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO;
import static android.car.watchdog.CarWatchdogManager.STATS_PERIOD_CURRENT_DAY;
import static android.car.watchdog.CarWatchdogManager.TIMEOUT_CRITICAL;
import static android.content.Intent.ACTION_PACKAGE_CHANGED;
import static android.content.Intent.ACTION_USER_REMOVED;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED;

import static com.android.car.CarStatsLog.CAR_WATCHDOG_SYSTEM_IO_USAGE_SUMMARY;
import static com.android.car.CarStatsLog.CAR_WATCHDOG_UID_IO_USAGE_SUMMARY;
import static com.android.car.internal.NotificationHelperBase.CAR_WATCHDOG_ACTION_DISMISS_RESOURCE_OVERUSE_NOTIFICATION;
import static com.android.car.internal.NotificationHelperBase.CAR_WATCHDOG_ACTION_LAUNCH_APP_SETTINGS;
import static com.android.car.internal.NotificationHelperBase.CAR_WATCHDOG_ACTION_RESOURCE_OVERUSE_DISABLE_APP;
import static com.android.car.internal.NotificationHelperBase.RESOURCE_OVERUSE_NOTIFICATION_BASE_ID;
import static com.android.car.watchdog.CarWatchdogService.ACTION_GARAGE_MODE_OFF;
import static com.android.car.watchdog.CarWatchdogService.ACTION_GARAGE_MODE_ON;
import static com.android.car.watchdog.CarWatchdogService.MISSING_ARG_VALUE;
import static com.android.car.watchdog.TimeSource.ZONE_OFFSET;
import static com.android.car.watchdog.WatchdogPerfHandler.INTENT_EXTRA_NOTIFICATION_ID;
import static com.android.car.watchdog.WatchdogPerfHandler.USER_PACKAGE_SEPARATOR;
import static com.android.car.watchdog.WatchdogPerfHandlerUnitTest.constructPackageIoOveruseStats;
import static com.android.car.watchdog.WatchdogPerfHandlerUnitTest.createMockResourceOveruseListener;
import static com.android.car.watchdog.WatchdogPerfHandlerUnitTest.sampleInternalResourceOveruseConfigurations;
import static com.android.car.watchdog.WatchdogPerfHandlerUnitTest.sampleResourceOveruseConfigurations;
import static com.android.car.watchdog.WatchdogProcessHandler.MISSING_INT_PROPERTY_VALUE;
import static com.android.car.watchdog.WatchdogProcessHandler.PROPERTY_RO_CLIENT_HEALTHCHECK_INTERVAL;
import static com.android.car.watchdog.WatchdogStorage.WatchdogDbHelper.DATABASE_NAME;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.app.ActivityThread;
import android.app.StatsManager;
import android.automotive.watchdog.internal.ApplicationCategoryType;
import android.automotive.watchdog.internal.ComponentType;
import android.automotive.watchdog.internal.GarageMode;
import android.automotive.watchdog.internal.ICarWatchdog;
import android.automotive.watchdog.internal.ICarWatchdogServiceForSystem;
import android.automotive.watchdog.internal.PackageIdentifier;
import android.automotive.watchdog.internal.PackageInfo;
import android.automotive.watchdog.internal.PackageIoOveruseStats;
import android.automotive.watchdog.internal.PowerCycle;
import android.automotive.watchdog.internal.ResourceStats;
import android.automotive.watchdog.internal.StateType;
import android.automotive.watchdog.internal.UidType;
import android.automotive.watchdog.internal.UserPackageIoUsageStats;
import android.automotive.watchdog.internal.UserState;
import android.car.drivingstate.CarUxRestrictions;
import android.car.hardware.power.CarPowerManager;
import android.car.hardware.power.ICarPowerPolicyListener;
import android.car.hardware.power.ICarPowerStateListener;
import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.car.test.mocks.MockSettings;
import android.car.user.CarUserManager;
import android.car.watchdog.CarWatchdogManager;
import android.car.watchdog.ICarWatchdogServiceCallback;
import android.car.watchdog.IResourceOveruseListener;
import android.car.watchdog.ResourceOveruseConfiguration;
import android.car.watchdoglib.CarWatchdogDaemonHelper;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Binder;
import android.os.FileUtils;
import android.os.IBinder;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.SparseArray;
import android.util.StatsEvent;

import com.android.car.BuiltinPackageDependency;
import com.android.car.CarLocalServices;
import com.android.car.CarServiceHelperWrapper;
import com.android.car.CarServiceUtils;
import com.android.car.CarStatsLog;
import com.android.car.CarUxRestrictionsManagerService;
import com.android.car.admin.NotificationHelper;
import com.android.car.internal.ICarServiceHelper;
import com.android.car.power.CarPowerManagementService;
import com.android.car.systeminterface.SystemInterface;
import com.android.car.user.CarUserService;

import com.google.common.truth.Correspondence;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.File;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * <p>This class contains unit tests for the {@link CarWatchdogService}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class CarWatchdogServiceUnitTest extends AbstractExtendedMockitoTestCase {
    private static final String CAR_WATCHDOG_DAEMON_INTERFACE =
            "android.automotive.watchdog.internal.ICarWatchdog/default";
    private static final String SYSTEM_PACKAGE_NAME = "system_package";
    private static final int MAX_WAIT_TIME_MS = 3000;
    private static final long OVERUSE_HANDLING_DELAY_MILLS = 1000;
    private static final int RECURRING_OVERUSE_TIMES = 2;
    private static final int RECURRING_OVERUSE_PERIOD_IN_DAYS = 2;
    private static final int UID_IO_USAGE_SUMMARY_TOP_COUNT = 3;
    private static final int IO_USAGE_SUMMARY_MIN_SYSTEM_TOTAL_WRITTEN_BYTES = 500 * 1024 * 1024;
    private static final long STATS_DURATION_SECONDS = 3 * 60 * 60;
    private static final long SYSTEM_DAILY_IO_USAGE_SUMMARY_MULTIPLIER = 10_000;
    private static final int PACKAGE_KILLABLE_STATE_RESET_DAYS = 90;
    private static final UserHandle TEST_USER_HANDLE = UserHandle.of(100);

    @Mock private Context mMockContext;
    @Mock private Context mMockBuiltinPackageContext;
    @Mock private ClassLoader mMockClassLoader;
    @Mock private PackageManager mMockPackageManager;
    @Mock private StatsManager mMockStatsManager;
    @Mock private UserManager mMockUserManager;
    @Mock private SystemInterface mMockSystemInterface;
    @Mock private CarPowerManagementService mMockCarPowerManagementService;
    @Mock private CarUserService mMockCarUserService;
    @Mock private CarUxRestrictionsManagerService mMockCarUxRestrictionsManagerService;
    @Mock private Resources mMockResources;
    @Mock private IBinder mMockBinder;
    @Mock private ICarWatchdog mMockCarWatchdogDaemon;
    @Mock private NotificationHelper mMockNotificationHelper;
    @Mock private ICarServiceHelper.Stub mMockCarServiceHelper;
    @Mock private WatchdogProcessHandler mMockWatchdogProcessHandler;
    @Mock private WatchdogPerfHandler mMockWatchdogPerfHandler;
    @Mock private CarWatchdogDaemonHelper mMockCarWatchdogDaemonHelper;

    @Captor private ArgumentCaptor<ICarPowerStateListener> mICarPowerStateListenerCaptor;
    @Captor private ArgumentCaptor<ICarPowerPolicyListener> mICarPowerPolicyListenerCaptor;
    @Captor private ArgumentCaptor<BroadcastReceiver> mBroadcastReceiverCaptor;
    @Captor private ArgumentCaptor<IntentFilter> mIntentFilterCaptor;
    @Captor private ArgumentCaptor<CarUserManager.UserLifecycleListener>
            mUserLifecycleListenerCaptor;
    @Captor private ArgumentCaptor<ICarWatchdogServiceForSystem>
            mICarWatchdogServiceForSystemCaptor;
    @Captor
    private ArgumentCaptor<CarWatchdogDaemonHelper.OnConnectionChangeListener>
            mOnConnectionChangeListenerArgumentCaptor;

    private CarWatchdogService mCarWatchdogService;
    private ICarWatchdogServiceForSystem mWatchdogServiceForSystemImpl;
    private WatchdogStorage mSpiedWatchdogStorage;
    private BroadcastReceiver mBroadcastReceiver;
    private ICarPowerStateListener mCarPowerStateListener;
    private ICarPowerPolicyListener mCarPowerPolicyListener;
    private CarUserManager.UserLifecycleListener mUserLifecycleListener;
    private CarWatchdogDaemonHelper.OnConnectionChangeListener mOnConnectionChangeListener;
    private File mTempSystemCarDir;
    // Not used directly, but sets proper mockStatic() expectations on Settings
    @SuppressWarnings("UnusedVariable")
    private MockSettings mMockSettings;

    private final TestTimeSource mTimeSource = new TestTimeSource();
    private final SparseArray<String> mGenericPackageNameByUid = new SparseArray<>();
    private final SparseArray<List<String>> mPackagesBySharedUid = new SparseArray<>();
    private final ArrayMap<String, android.content.pm.PackageInfo> mPmPackageInfoByUserPackage =
            new ArrayMap<>();
    private final ArraySet<String> mDisabledUserPackages = new ArraySet<>();
    private final SparseArray<String> mDisabledPackagesSettingsStringByUserid = new SparseArray<>();
    private final Set<WatchdogStorage.UserPackageSettingsEntry> mUserPackageSettingsEntries =
            new ArraySet<>();
    private final List<WatchdogStorage.IoUsageStatsEntry> mIoUsageStatsEntries = new ArrayList<>();
    private final List<AtomsProto.CarWatchdogSystemIoUsageSummary> mPulledSystemIoUsageSummaries =
            new ArrayList<>();
    private final List<AtomsProto.CarWatchdogUidIoUsageSummary> mPulledUidIoUsageSummaries =
            new ArrayList<>();
    private final IPackageManager mSpiedPackageManager = spy(ActivityThread.getPackageManager());

    public CarWatchdogServiceUnitTest() {
        super(CarWatchdogService.TAG);
    }

    @Override
    protected void onSessionBuilder(CustomMockitoSessionBuilder builder) {
        mMockSettings = new MockSettings(builder);
        builder
            .spyStatic(ServiceManager.class)
            .spyStatic(Binder.class)
            .spyStatic(ActivityManager.class)
            .spyStatic(ActivityThread.class)
            .spyStatic(CarLocalServices.class)
            .spyStatic(CarStatsLog.class)
            .spyStatic(CarServiceUtils.class)
            .spyStatic(BuiltinPackageDependency.class)
            .spyStatic(SystemProperties.class);
    }

    /**
     * Initialize all of the objects with the @Mock annotation.
     */
    @Before
    public void setUp() throws Exception {
        when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);
        when(mMockContext.getSystemService(StatsManager.class)).thenReturn(mMockStatsManager);
        when(mMockContext.getPackageName()).thenReturn(
                CarWatchdogServiceUnitTest.class.getCanonicalName());
        when(mMockContext.getResources()).thenReturn(mMockResources);
        when(mMockResources.getInteger(
                com.android.car.R.integer.watchdogUserPackageSettingsResetDays))
                .thenReturn(PACKAGE_KILLABLE_STATE_RESET_DAYS);
        when(mMockResources.getInteger(
                com.android.car.R.integer.recurringResourceOverusePeriodInDays))
                .thenReturn(RECURRING_OVERUSE_PERIOD_IN_DAYS);
        when(mMockResources.getInteger(
                com.android.car.R.integer.recurringResourceOveruseTimes))
                .thenReturn(RECURRING_OVERUSE_TIMES);
        when(mMockResources.getInteger(
                com.android.car.R.integer.uidIoUsageSummaryTopCount))
                .thenReturn(UID_IO_USAGE_SUMMARY_TOP_COUNT);
        when(mMockResources.getInteger(
                com.android.car.R.integer.ioUsageSummaryMinSystemTotalWrittenBytes))
                .thenReturn(IO_USAGE_SUMMARY_MIN_SYSTEM_TOTAL_WRITTEN_BYTES);
        doReturn(mMockSystemInterface)
                .when(() -> CarLocalServices.getService(SystemInterface.class));
        doReturn(mMockCarPowerManagementService)
                .when(() -> CarLocalServices.getService(CarPowerManagementService.class));
        doReturn(mMockCarUserService)
                .when(() -> CarLocalServices.getService(CarUserService.class));
        doReturn(mMockCarUxRestrictionsManagerService)
                .when(() -> CarLocalServices.getService(CarUxRestrictionsManagerService.class));
        doReturn(mSpiedPackageManager).when(() -> ActivityThread.getPackageManager());
        when(mMockBuiltinPackageContext.getClassLoader()).thenReturn(mMockClassLoader);
        doReturn(NotificationHelper.class).when(mMockClassLoader).loadClass(any());
        doReturn(mMockNotificationHelper)
                .when(() -> BuiltinPackageDependency.createNotificationHelper(
                        mMockBuiltinPackageContext));
        doReturn(MISSING_INT_PROPERTY_VALUE).when(
                () -> SystemProperties.getInt(PROPERTY_RO_CLIENT_HEALTHCHECK_INTERVAL,
                        MISSING_INT_PROPERTY_VALUE));

        CarServiceHelperWrapper wrapper = CarServiceHelperWrapper.create();
        wrapper.setCarServiceHelper(mMockCarServiceHelper);

        when(mMockCarUxRestrictionsManagerService.getCurrentUxRestrictions())
                .thenReturn(new CarUxRestrictions.Builder(/* reqOpt= */ false,
                        UX_RESTRICTIONS_BASELINE, /* time= */ 0).build());

        mTempSystemCarDir = Files.createTempDirectory("watchdog_test").toFile();
        when(mMockSystemInterface.getSystemCarDir()).thenReturn(mTempSystemCarDir);

        File tempDbFile = new File(mTempSystemCarDir.getPath(), DATABASE_NAME);
        when(mMockContext.createDeviceProtectedStorageContext()).thenReturn(mMockContext);
        when(mMockContext.getDatabasePath(DATABASE_NAME)).thenReturn(tempDbFile);
        mSpiedWatchdogStorage =
                spy(new WatchdogStorage(mMockContext, /* useDataSystemCarDir= */ false,
                        mTimeSource));

        setupUsers();
        mockWatchdogStorage();
        mockPackageManager();
        mockBuildStatsEventCalls();

        mTimeSource.updateNow(/* numDaysAgo= */ 0);
        mCarWatchdogService = new CarWatchdogService(mMockContext, mMockBuiltinPackageContext,
                mSpiedWatchdogStorage, mTimeSource, mMockWatchdogProcessHandler,
                mMockWatchdogPerfHandler);
        mCarWatchdogService.setCarWatchdogDaemonHelper(mMockCarWatchdogDaemonHelper);
        initService(/* wantedInvocations= */ 1);
    }

    /**
     * Releases resources.
     */
    @After
    public void tearDown() throws Exception {
        if (mTempSystemCarDir != null) {
            FileUtils.deleteContentsAndDir(mTempSystemCarDir);
        }
        CarLocalServices.removeServiceForTest(CarServiceHelperWrapper.class);
    }

    @Test
    public void testCarWatchdogServiceHealthCheck() throws Exception {
        mWatchdogServiceForSystemImpl.checkIfAlive(123456, TIMEOUT_CRITICAL);

        verify(mMockWatchdogProcessHandler).postHealthCheckMessage(eq(123456));
    }

    @Test
    public void testTellClientAlive() throws Exception {
        TestClient client = new TestClient();

        mCarWatchdogService.tellClientAlive(client, TIMEOUT_CRITICAL);

        verify(mMockWatchdogProcessHandler).tellClientAlive(eq(client), eq(TIMEOUT_CRITICAL));
    }

    @Test
    public void testRegisterClient() throws Exception {
        TestClient client = new TestClient();

        mCarWatchdogService.registerClient(client, TIMEOUT_CRITICAL);

        verify(mMockWatchdogProcessHandler).registerClient(eq(client), eq(TIMEOUT_CRITICAL));
    }

    @Test
    public void testUnregisterClient() throws Exception {
        TestClient client = new TestClient();

        mCarWatchdogService.registerClient(client, TIMEOUT_CRITICAL);
        mCarWatchdogService.unregisterClient(client);

        verify(mMockWatchdogProcessHandler).unregisterClient(eq(client));
    }

    @Test
    public void testRequestAidlVhalPid() throws Exception {
        mWatchdogServiceForSystemImpl.requestAidlVhalPid();

        verify(mMockWatchdogProcessHandler, timeout(MAX_WAIT_TIME_MS)).asyncFetchAidlVhalPid();
    }

    // TODO(b/262301082): Add a unit test to verify the race condition that caused watchdog to
    //  incorrectly terminate clients that were recently unregistered - b/261766872.

    @Test
    public void testGarageModeStateChangeToOn() throws Exception {
        mBroadcastReceiver.onReceive(mMockContext,
                new Intent().setAction(ACTION_GARAGE_MODE_ON));

        verify(mMockWatchdogPerfHandler).onGarageModeChange(GarageMode.GARAGE_MODE_ON);
        verify(mMockCarWatchdogDaemonHelper).notifySystemStateChange(StateType.GARAGE_MODE,
                GarageMode.GARAGE_MODE_ON, MISSING_ARG_VALUE);
        verify(mSpiedWatchdogStorage).shrinkDatabase();
    }

    @Test
    public void testGarageModeStateChangeToOff() throws Exception {
        mBroadcastReceiver.onReceive(mMockContext,
                new Intent().setAction(ACTION_GARAGE_MODE_OFF));

        // GARAGE_MODE_OFF is notified twice: Once during the initial daemon connect and once when
        // the ACTION_GARAGE_MODE_OFF intent is received.
        verify(mMockWatchdogPerfHandler).onGarageModeChange(GarageMode.GARAGE_MODE_OFF);
        verify(mMockCarWatchdogDaemonHelper, times(2)).notifySystemStateChange(
                StateType.GARAGE_MODE,
                GarageMode.GARAGE_MODE_OFF, MISSING_ARG_VALUE);
        verify(mSpiedWatchdogStorage, never()).shrinkDatabase();
    }

    @Test
    public void testWatchdogDaemonRestart() throws Exception {
        mockUmGetUserHandles(mMockUserManager, /* excludeDying= */ false, 101, 102);
        mockUmIsUserRunning(mMockUserManager, /* userId= */ 101, /* isRunning= */ false);
        mockUmIsUserRunning(mMockUserManager, /* userId= */ 102, /* isRunning= */ true);
        when(mMockCarPowerManagementService.getPowerState()).thenReturn(
                CarPowerManager.STATE_SHUTDOWN_ENTER);
        mBroadcastReceiver.onReceive(mMockContext,
                new Intent().setAction(ACTION_GARAGE_MODE_ON));

        // Simulate WatchdogDaemon reconnecting after restarting.
        mOnConnectionChangeListener.onConnectionChange(/* isConnected= */ true);

        verify(mMockCarWatchdogDaemonHelper).notifySystemStateChange(StateType.USER_STATE, 101,
                UserState.USER_STATE_STOPPED);
        verify(mMockCarWatchdogDaemonHelper).notifySystemStateChange(StateType.USER_STATE, 102,
                UserState.USER_STATE_STARTED);
        verify(mMockCarWatchdogDaemonHelper).notifySystemStateChange(StateType.POWER_CYCLE,
                PowerCycle.POWER_CYCLE_SHUTDOWN_ENTER, MISSING_ARG_VALUE);
        // notifySystemStateChange is called once when ACTION_GARAGE_MODE_ON is set in the
        // BroadcastReceiver and again when onConnectionChange is called.
        verify(mMockCarWatchdogDaemonHelper, times(2)).notifySystemStateChange(
                StateType.GARAGE_MODE, GarageMode.GARAGE_MODE_ON, MISSING_ARG_VALUE);
    }

    @Test
    public void testUserSwitchingLifecycleEvents() throws Exception {
        mUserLifecycleListener.onEvent(
                new CarUserManager.UserLifecycleEvent(
                        USER_LIFECYCLE_EVENT_TYPE_SWITCHING, 100, 101));
        mUserLifecycleListener.onEvent(
                new CarUserManager.UserLifecycleEvent(USER_LIFECYCLE_EVENT_TYPE_UNLOCKING, 101));
        mUserLifecycleListener.onEvent(
                new CarUserManager.UserLifecycleEvent(
                        USER_LIFECYCLE_EVENT_TYPE_POST_UNLOCKED, 101));

        verify(mMockCarWatchdogDaemonHelper).notifySystemStateChange(StateType.USER_STATE, 101,
                UserState.USER_STATE_SWITCHING);
        verify(mMockCarWatchdogDaemonHelper).notifySystemStateChange(StateType.USER_STATE, 101,
                UserState.USER_STATE_UNLOCKING);
        verify(mMockCarWatchdogDaemonHelper).notifySystemStateChange(StateType.USER_STATE, 101,
                UserState.USER_STATE_POST_UNLOCKED);
    }

    @Test
    public void testUserRemovedBroadcast() throws Exception {
        mBroadcastReceiver.onReceive(mMockContext,
                new Intent().setAction(Intent.ACTION_USER_REMOVED)
                        .putExtra(Intent.EXTRA_USER, TEST_USER_HANDLE));

        verify(mMockCarWatchdogDaemonHelper).notifySystemStateChange(StateType.USER_STATE, 100,
                UserState.USER_STATE_REMOVED);
        verify(mMockWatchdogPerfHandler).deleteUser(eq(100));
    }

    @Test
    public void testPowerCycleStateChangesDuringSuspend() throws Exception {
        setCarPowerState(CarPowerManager.STATE_SUSPEND_ENTER);
        setCarPowerState(CarPowerManager.STATE_SUSPEND_EXIT);
        setCarPowerState(CarPowerManager.STATE_ON);

        verify(mMockCarWatchdogDaemonHelper).notifySystemStateChange(StateType.POWER_CYCLE,
                PowerCycle.POWER_CYCLE_SHUTDOWN_ENTER, MISSING_ARG_VALUE);
        verify(mMockCarWatchdogDaemonHelper).notifySystemStateChange(StateType.POWER_CYCLE,
                PowerCycle.POWER_CYCLE_SUSPEND_EXIT, MISSING_ARG_VALUE);
        verify(mMockCarWatchdogDaemonHelper).notifySystemStateChange(StateType.POWER_CYCLE,
                PowerCycle.POWER_CYCLE_RESUME, MISSING_ARG_VALUE);
    }

    @Test
    public void testPowerCycleStateChangesDuringHibernation() throws Exception {
        setCarPowerState(CarPowerManager.STATE_HIBERNATION_ENTER);
        setCarPowerState(CarPowerManager.STATE_HIBERNATION_EXIT);
        setCarPowerState(CarPowerManager.STATE_ON);

        verify(mMockCarWatchdogDaemonHelper).notifySystemStateChange(StateType.POWER_CYCLE,
                PowerCycle.POWER_CYCLE_SHUTDOWN_ENTER, MISSING_ARG_VALUE);
        verify(mMockCarWatchdogDaemonHelper).notifySystemStateChange(StateType.POWER_CYCLE,
                PowerCycle.POWER_CYCLE_SUSPEND_EXIT, MISSING_ARG_VALUE);
        verify(mMockCarWatchdogDaemonHelper).notifySystemStateChange(StateType.POWER_CYCLE,
                PowerCycle.POWER_CYCLE_RESUME, MISSING_ARG_VALUE);
    }

    @Test
    public void testDeviceRebootBroadcast() throws Exception {
        mBroadcastReceiver.onReceive(mMockContext,
                new Intent().setAction(Intent.ACTION_REBOOT)
                        .setFlags(Intent.FLAG_RECEIVER_FOREGROUND));
        verify(mMockCarWatchdogDaemonHelper).notifySystemStateChange(StateType.POWER_CYCLE,
                PowerCycle.POWER_CYCLE_SHUTDOWN_ENTER, /* arg2= */ 0);
    }

    @Test
    public void testDeviceShutdownBroadcast() throws Exception {
        mBroadcastReceiver.onReceive(mMockContext,
                new Intent().setAction(Intent.ACTION_SHUTDOWN)
                        .setFlags(Intent.FLAG_RECEIVER_FOREGROUND));
        verify(mMockCarWatchdogDaemonHelper).notifySystemStateChange(StateType.POWER_CYCLE,
                PowerCycle.POWER_CYCLE_SHUTDOWN_ENTER, /* arg2= */ 0);
    }

    @Test
    public void testDeviceShutdownBroadcastWithoutFlagReceiverForeground() throws Exception {
        mBroadcastReceiver.onReceive(mMockContext,
                new Intent().setAction(Intent.ACTION_SHUTDOWN));
        verify(mMockCarWatchdogDaemonHelper, never()).notifySystemStateChange(StateType.POWER_CYCLE,
                PowerCycle.POWER_CYCLE_SHUTDOWN_ENTER, /* arg2= */ 0);
    }

    @Test
    public void testDisableAppBroadcast() throws Exception {
        Intent intent = new Intent(
                CAR_WATCHDOG_ACTION_RESOURCE_OVERUSE_DISABLE_APP).putExtra(
                Intent.EXTRA_PACKAGE_NAME, SYSTEM_PACKAGE_NAME).putExtra(Intent.EXTRA_USER,
                TEST_USER_HANDLE).putExtra(INTENT_EXTRA_NOTIFICATION_ID,
                RESOURCE_OVERUSE_NOTIFICATION_BASE_ID);

        mBroadcastReceiver.onReceive(mMockContext, intent);

        verify(mMockWatchdogPerfHandler).processUserNotificationIntent(eq(intent));
    }

    @Test
    public void testDisableAppBroadcastWithDisabledPackage() throws Exception {
        Intent intent = new Intent(
                CAR_WATCHDOG_ACTION_RESOURCE_OVERUSE_DISABLE_APP)
                .putExtra(Intent.EXTRA_PACKAGE_NAME, SYSTEM_PACKAGE_NAME)
                .putExtra(Intent.EXTRA_USER, TEST_USER_HANDLE)
                .putExtra(INTENT_EXTRA_NOTIFICATION_ID, RESOURCE_OVERUSE_NOTIFICATION_BASE_ID);

        mBroadcastReceiver.onReceive(mMockContext, intent);

        verify(mMockWatchdogPerfHandler).processUserNotificationIntent(eq(intent));
    }

    @Test
    public void testLaunchAppSettingsBroadcast() throws Exception {
        Intent intent = new Intent(
                CAR_WATCHDOG_ACTION_LAUNCH_APP_SETTINGS)
                .putExtra(Intent.EXTRA_PACKAGE_NAME, SYSTEM_PACKAGE_NAME)
                .putExtra(Intent.EXTRA_USER, TEST_USER_HANDLE)
                .putExtra(INTENT_EXTRA_NOTIFICATION_ID, RESOURCE_OVERUSE_NOTIFICATION_BASE_ID);

        mBroadcastReceiver.onReceive(mMockContext, intent);

        verify(mMockWatchdogPerfHandler).processUserNotificationIntent(eq(intent));
    }

    @Test
    public void testDismissUserNotificationBroadcast() throws Exception {
        Intent intent = new Intent(CAR_WATCHDOG_ACTION_DISMISS_RESOURCE_OVERUSE_NOTIFICATION)
                .putExtra(Intent.EXTRA_PACKAGE_NAME, SYSTEM_PACKAGE_NAME)
                .putExtra(Intent.EXTRA_USER, TEST_USER_HANDLE)
                .putExtra(INTENT_EXTRA_NOTIFICATION_ID,
                        RESOURCE_OVERUSE_NOTIFICATION_BASE_ID);

        mBroadcastReceiver.onReceive(mMockContext, intent);

        verify(mMockWatchdogPerfHandler).processUserNotificationIntent(eq(intent));
    }

    @Test
    public void testUserNotificationActionBroadcastsWithNullPackageName() throws Exception {
        List<String> actions = Arrays.asList(CAR_WATCHDOG_ACTION_RESOURCE_OVERUSE_DISABLE_APP,
                CAR_WATCHDOG_ACTION_LAUNCH_APP_SETTINGS,
                CAR_WATCHDOG_ACTION_DISMISS_RESOURCE_OVERUSE_NOTIFICATION);

        for (String action : actions) {
            mBroadcastReceiver.onReceive(mMockContext, new Intent(action)
                    .putExtra(Intent.EXTRA_USER, TEST_USER_HANDLE)
                    .putExtra(INTENT_EXTRA_NOTIFICATION_ID, RESOURCE_OVERUSE_NOTIFICATION_BASE_ID));
        }

        verify(mMockBuiltinPackageContext, never()).startActivityAsUser(any(), any());
        verifyNoMoreInteractions(mSpiedPackageManager);
        verify(mMockNotificationHelper, never()).cancelNotificationAsUser(any(), anyInt());
    }

    @Test
    public void testUserNotificationActionBroadcastsWithInvalidUserId() throws Exception {
        List<String> actions = Arrays.asList(CAR_WATCHDOG_ACTION_RESOURCE_OVERUSE_DISABLE_APP,
                CAR_WATCHDOG_ACTION_LAUNCH_APP_SETTINGS,
                CAR_WATCHDOG_ACTION_DISMISS_RESOURCE_OVERUSE_NOTIFICATION);

        for (String action : actions) {
            mBroadcastReceiver.onReceive(mMockContext, new Intent(action)
                    .putExtra(Intent.EXTRA_PACKAGE_NAME, SYSTEM_PACKAGE_NAME)
                    .putExtra(Intent.EXTRA_USER, UserHandle.of(-1))
                    .putExtra(INTENT_EXTRA_NOTIFICATION_ID, RESOURCE_OVERUSE_NOTIFICATION_BASE_ID));
        }

        verify(mMockBuiltinPackageContext, never()).startActivityAsUser(any(), any());
        verifyNoMoreInteractions(mSpiedPackageManager);
        verify(mMockNotificationHelper, never()).cancelNotificationAsUser(any(), anyInt());
    }

    @Test
    public void testUserNotificationActionBroadcastsWithMissingNotificationId() throws Exception {

        List<String> actions = Arrays.asList(CAR_WATCHDOG_ACTION_RESOURCE_OVERUSE_DISABLE_APP,
                CAR_WATCHDOG_ACTION_LAUNCH_APP_SETTINGS,
                CAR_WATCHDOG_ACTION_DISMISS_RESOURCE_OVERUSE_NOTIFICATION);

        for (String action : actions) {
            Intent intent = new Intent(action)
                    .putExtra(Intent.EXTRA_PACKAGE_NAME, SYSTEM_PACKAGE_NAME)
                    .putExtra(Intent.EXTRA_USER, TEST_USER_HANDLE);
            mBroadcastReceiver.onReceive(mMockContext, intent);
            verify(mMockWatchdogPerfHandler).processUserNotificationIntent(intent);
        }
    }

    @Test
    public void testHandlePackageChangedBroadcast() throws Exception {
        Intent intent = new Intent(ACTION_PACKAGE_CHANGED)
                .putExtra(Intent.EXTRA_USER_HANDLE, 100)
                .setData(Uri.parse("package:" + SYSTEM_PACKAGE_NAME));
        mBroadcastReceiver.onReceive(mMockContext, intent);

        verify(mMockWatchdogPerfHandler).processPackageChangedIntent(eq(intent));
    }

    @Test
    public void testSetOveruseHandlingDelay() {
        mCarWatchdogService.setOveruseHandlingDelay(OVERUSE_HANDLING_DELAY_MILLS);

        verify(mMockWatchdogPerfHandler).setOveruseHandlingDelay(eq(OVERUSE_HANDLING_DELAY_MILLS));
    }

    @Test
    public void testGetResourceOveruseStats() {
        mCarWatchdogService.getResourceOveruseStats(FLAG_RESOURCE_OVERUSE_IO,
                CarWatchdogManager.STATS_PERIOD_CURRENT_DAY);

        verify(mMockWatchdogPerfHandler).getResourceOveruseStats(eq(FLAG_RESOURCE_OVERUSE_IO),
                eq(CarWatchdogManager.STATS_PERIOD_CURRENT_DAY));
    }

    @Test
    public void testGetAllResourceOveruseStats() {
        mCarWatchdogService.getAllResourceOveruseStats(FLAG_RESOURCE_OVERUSE_IO,
                FLAG_MINIMUM_STATS_IO_1_MB, STATS_PERIOD_CURRENT_DAY);

        verify(mMockWatchdogPerfHandler).getAllResourceOveruseStats(eq(FLAG_RESOURCE_OVERUSE_IO),
                eq(FLAG_MINIMUM_STATS_IO_1_MB), eq(STATS_PERIOD_CURRENT_DAY));
    }

    @Test
    public void testGetResourceOveruseStatsForUserPackage() {
        UserHandle userHandle = UserHandle.of(12);
        mCarWatchdogService.getResourceOveruseStatsForUserPackage(
                "vendor_package.critical", userHandle, FLAG_RESOURCE_OVERUSE_IO,
                CarWatchdogManager.STATS_PERIOD_CURRENT_DAY);

        verify(mMockWatchdogPerfHandler).getResourceOveruseStatsForUserPackage(
                eq("vendor_package.critical"), eq(userHandle), eq(FLAG_RESOURCE_OVERUSE_IO),
                eq(CarWatchdogManager.STATS_PERIOD_CURRENT_DAY));
    }

    @Test
    public void testOnLatestResourceOveruseStats() throws Exception {
        // Random I/O overuse stats
        List<PackageIoOveruseStats> packageIoOveruseStats = Collections.singletonList(
                constructPackageIoOveruseStats(123456, /* shouldNotify= */ true,
                        /* forgivenWriteBytes= */
                        constructPerStateBytes(/* fgBytes= */ 80, /* bgBytes= */ 170,
                                /* gmBytes= */ 260),
                        constructInternalIoOveruseStats(/* killableOnOveruse= */ true,
                                /* remainingWriteBytes= */
                                constructPerStateBytes(/* fgBytes= */ 20, /* bgBytes= */ 20,
                                        /* gmBytes= */ 20),
                                /* writtenBytes= */
                                constructPerStateBytes(/* fgBytes= */ 100, /* bgBytes= */ 200,
                                        /* gmBytes= */ 300),
                                /* totalOveruses= */ 3)));
        ResourceStats resourceStats = new ResourceStats();
        resourceStats.resourceOveruseStats =
                new android.automotive.watchdog.internal.ResourceOveruseStats();
        resourceStats.resourceOveruseStats.packageIoOveruseStats = packageIoOveruseStats;

        mWatchdogServiceForSystemImpl.onLatestResourceStats(List.of(resourceStats));

        verify(mMockWatchdogPerfHandler).latestIoOveruseStats(eq(packageIoOveruseStats));
    }
    @Test
    public void testAddResourceOveruseListener() {
        IResourceOveruseListener mockListener = createMockResourceOveruseListener();
        mCarWatchdogService.addResourceOveruseListener(FLAG_RESOURCE_OVERUSE_IO, mockListener);

        verify(mMockWatchdogPerfHandler).addResourceOveruseListener(eq(FLAG_RESOURCE_OVERUSE_IO),
                eq(mockListener));
    }
    @Test
    public void testAddResourceOveruseListenerForSystem() {
        IResourceOveruseListener mockListener = createMockResourceOveruseListener();
        mCarWatchdogService.addResourceOveruseListenerForSystem(FLAG_RESOURCE_OVERUSE_IO,
                mockListener);

        verify(mMockWatchdogPerfHandler).addResourceOveruseListenerForSystem(
                eq(FLAG_RESOURCE_OVERUSE_IO), eq(mockListener));
    }

    @Test
    public void testRemoveResourceOveruseListener() {
        IResourceOveruseListener mockListener = createMockResourceOveruseListener();
        mCarWatchdogService.removeResourceOveruseListener(mockListener);

        verify(mMockWatchdogPerfHandler).removeResourceOveruseListener(eq(mockListener));
    }

    @Test
    public void testRemoveResourceOveruseListenerForSystem() {
        IResourceOveruseListener mockListener = createMockResourceOveruseListener();
        mCarWatchdogService.removeResourceOveruseListenerForSystem(mockListener);

        verify(mMockWatchdogPerfHandler).removeResourceOveruseListenerForSystem(eq(mockListener));
    }

    @Test
    public void testSetKillablePackageAsUser() throws Exception {
        UserHandle userHandle = UserHandle.of(101);
        mCarWatchdogService.setKillablePackageAsUser("third_party_package", userHandle,
                /* isKillable= */ false);

        verify(mMockWatchdogPerfHandler).setKillablePackageAsUser(
                eq("third_party_package"), eq(userHandle), eq(false));
    }

    @Test
    public void testGetPackageKillableStatesAsUser() throws Exception {
        UserHandle userHandle = UserHandle.of(101);
        mCarWatchdogService.getPackageKillableStatesAsUser(userHandle);

        verify(mMockWatchdogPerfHandler).getPackageKillableStatesAsUser(eq(userHandle));
    }

    @Test
    public void testSetResourceOveruseConfigurations() throws Exception {
        List<ResourceOveruseConfiguration> configurations = sampleResourceOveruseConfigurations();
        mCarWatchdogService.setResourceOveruseConfigurations(configurations,
                FLAG_RESOURCE_OVERUSE_IO);

        verify(mMockWatchdogPerfHandler).setResourceOveruseConfigurations(
                eq(configurations), eq(FLAG_RESOURCE_OVERUSE_IO));
    }

    @Test
    public void testGetResourceOveruseConfigurations() {
        mCarWatchdogService.getResourceOveruseConfigurations(FLAG_RESOURCE_OVERUSE_IO);

        verify(mMockWatchdogPerfHandler).getResourceOveruseConfigurations(
                eq(FLAG_RESOURCE_OVERUSE_IO));
    }

    @Test
    public void testRequestTodayIoUsageStats() throws Exception {
        mWatchdogServiceForSystemImpl.requestTodayIoUsageStats();

        verify(mMockWatchdogPerfHandler).asyncFetchTodayIoUsageStats();
    }
    @Test
    public void testGetTodayIoUsageStats() throws Exception {
        mWatchdogServiceForSystemImpl.getTodayIoUsageStats();

        verify(mMockWatchdogPerfHandler).getTodayIoUsageStats();
    }

    @Test
    public void testResetResourceOveruseStats() throws Exception {
        String packageName = mMockContext.getPackageName();
        mWatchdogServiceForSystemImpl.resetResourceOveruseStats(
                Collections.singletonList(packageName));

        verify(mMockWatchdogPerfHandler).resetResourceOveruseStats(any());
    }

    @Test
    public void testGetPackageInfosForUids() throws Exception {
        injectPackageInfos(Arrays.asList(
                constructPackageManagerPackageInfo(
                        "system_package.A", 6001, null, ApplicationInfo.FLAG_SYSTEM, 0),
                constructPackageManagerPackageInfo(
                        "vendor_package.B", 10005100, null, 0, ApplicationInfo.PRIVATE_FLAG_OEM),
                constructPackageManagerPackageInfo(
                        "vendor_package.C", 10345678, null, 0, ApplicationInfo.PRIVATE_FLAG_ODM),
                constructPackageManagerPackageInfo("third_party_package.D", 10200056, null)));

        int[] uids = new int[]{6001, 10005100, 10200056, 10345678};
        List<PackageInfo> actualPackageInfos = mWatchdogServiceForSystemImpl.getPackageInfosForUids(
                uids, new ArrayList<>());

        List<PackageInfo> expectedPackageInfos = Arrays.asList(
                constructPackageInfo("system_package.A", 6001, new ArrayList<>(),
                        UidType.NATIVE, ComponentType.SYSTEM, ApplicationCategoryType.OTHERS),
                constructPackageInfo("vendor_package.B", 10005100, new ArrayList<>(),
                        UidType.NATIVE, ComponentType.VENDOR, ApplicationCategoryType.OTHERS),
                constructPackageInfo("third_party_package.D", 10200056, new ArrayList<>(),
                        UidType.APPLICATION, ComponentType.THIRD_PARTY,
                        ApplicationCategoryType.OTHERS),
                constructPackageInfo("vendor_package.C", 10345678, new ArrayList<>(),
                        UidType.APPLICATION, ComponentType.VENDOR,
                        ApplicationCategoryType.OTHERS));

        assertPackageInfoEquals(actualPackageInfos, expectedPackageInfos);
    }

    @Test
    public void testGetPackageInfosWithSharedUids() throws Exception {
        injectPackageInfos(Arrays.asList(
                constructPackageManagerPackageInfo("system_package.A", 6050,
                        "system_shared_package", ApplicationInfo.FLAG_UPDATED_SYSTEM_APP, 0),
                constructPackageManagerPackageInfo("system_package.B", 10100035,
                        "vendor_shared_package", 0, ApplicationInfo.PRIVATE_FLAG_PRODUCT),
                constructPackageManagerPackageInfo("vendor_package.C", 10100035,
                        "vendor_shared_package", 0, ApplicationInfo.PRIVATE_FLAG_VENDOR),
                constructPackageManagerPackageInfo(
                        "third_party_package.D", 6050, "system_shared_package"),
                constructPackageManagerPackageInfo(
                        "third_party_package.E", 10100035, "vendor_shared_package"),
                constructPackageManagerPackageInfo(
                        "third_party_package.F", 10200078, "third_party_shared_package")));

        int[] uids = new int[]{6050, 10100035, 10200078};
        List<PackageInfo> actualPackageInfos = mWatchdogServiceForSystemImpl.getPackageInfosForUids(
                uids, new ArrayList<>());

        List<PackageInfo> expectedPackageInfos = Arrays.asList(
                constructPackageInfo("shared:system_shared_package", 6050,
                        Arrays.asList("system_package.A", "third_party_package.D"),
                        UidType.NATIVE, ComponentType.SYSTEM, ApplicationCategoryType.OTHERS),
                constructPackageInfo("shared:vendor_shared_package", 10100035,
                        Arrays.asList("vendor_package.C", "system_package.B",
                                "third_party_package.E"), UidType.APPLICATION,
                        ComponentType.VENDOR, ApplicationCategoryType.OTHERS),
                constructPackageInfo("shared:third_party_shared_package", 10200078,
                        Collections.singletonList("third_party_package.F"),
                        UidType.APPLICATION,  ComponentType.THIRD_PARTY,
                        ApplicationCategoryType.OTHERS));

        assertPackageInfoEquals(actualPackageInfos, expectedPackageInfos);
    }

    @Test
    public void testGetPackageInfosForUidsWithVendorPackagePrefixes() throws Exception {
        injectPackageInfos(Arrays.asList(
                constructPackageManagerPackageInfo(
                        "vendor_package.A", 10010034, null, 0,
                        ApplicationInfo.PRIVATE_FLAG_PRODUCT),
                constructPackageManagerPackageInfo("vendor_pkg.B", 10010035,
                        "vendor_shared_package", ApplicationInfo.FLAG_SYSTEM, 0),
                constructPackageManagerPackageInfo(
                        "third_party_package.C", 10010035, "vendor_shared_package"),
                constructPackageManagerPackageInfo(
                        "third_party_package.D", 10010035, "vendor_shared_package"),
                constructPackageManagerPackageInfo(
                        "third_party_package.F", 10200078, "third_party_shared_package"),
                constructPackageManagerPackageInfo("vndr_pkg.G", 10206345, "vendor_package.shared",
                        ApplicationInfo.FLAG_SYSTEM, 0),
                /*
                 * A 3p package pretending to be a vendor package because 3p packages won't have the
                 * required flags.
                 */
                constructPackageManagerPackageInfo("vendor_package.imposter", 10203456, null, 0,
                        0)));

        int[] uids = new int[]{10010034, 10010035, 10200078, 10206345, 10203456};
        List<PackageInfo> actualPackageInfos = mWatchdogServiceForSystemImpl.getPackageInfosForUids(
                uids, Arrays.asList("vendor_package.", "vendor_pkg.", "shared:vendor_package."));

        List<PackageInfo> expectedPackageInfos = Arrays.asList(
                constructPackageInfo("vendor_package.A", 10010034, new ArrayList<>(),
                        UidType.APPLICATION, ComponentType.VENDOR, ApplicationCategoryType.OTHERS),
                constructPackageInfo("shared:vendor_shared_package", 10010035,
                        Arrays.asList("vendor_pkg.B", "third_party_package.C",
                                "third_party_package.D"), UidType.APPLICATION,
                        ComponentType.VENDOR, ApplicationCategoryType.OTHERS),
                constructPackageInfo("shared:third_party_shared_package", 10200078,
                        Collections.singletonList("third_party_package.F"), UidType.APPLICATION,
                        ComponentType.THIRD_PARTY, ApplicationCategoryType.OTHERS),
                constructPackageInfo("shared:vendor_package.shared", 10206345,
                        Collections.singletonList("vndr_pkg.G"), UidType.APPLICATION,
                        ComponentType.VENDOR, ApplicationCategoryType.OTHERS),
                constructPackageInfo("vendor_package.imposter", 10203456,
                        new ArrayList<>(), UidType.APPLICATION, ComponentType.THIRD_PARTY,
                        ApplicationCategoryType.OTHERS));

        assertPackageInfoEquals(actualPackageInfos, expectedPackageInfos);
    }

    @Test
    public void testGetPackageInfosForUidsWithMissingApplicationInfos() throws Exception {
        injectPackageInfos(Arrays.asList(
                constructPackageManagerPackageInfo(
                        "vendor_package.A", 10100034, null, 0, ApplicationInfo.PRIVATE_FLAG_OEM),
                constructPackageManagerPackageInfo("vendor_package.B", 10100035,
                        "vendor_shared_package", 0, ApplicationInfo.PRIVATE_FLAG_VENDOR),
                constructPackageManagerPackageInfo(
                        "third_party_package.C", 10100035, "vendor_shared_package")));

        BiConsumer<Integer, String> addPackageToSharedUid = (uid, packageName) -> {
            List<String> packages = mPackagesBySharedUid.get(uid);
            if (packages == null) {
                packages = new ArrayList<>();
            }
            packages.add(packageName);
            mPackagesBySharedUid.put(uid, packages);
        };

        addPackageToSharedUid.accept(10100035, "third_party_package.G");
        mGenericPackageNameByUid.put(10200056, "third_party_package.H");
        mGenericPackageNameByUid.put(10200078, "shared:third_party_shared_package");
        addPackageToSharedUid.accept(10200078, "third_party_package.I");


        int[] uids = new int[]{10100034, 10100035, 10200056, 10200078};

        List<PackageInfo> actualPackageInfos = mWatchdogServiceForSystemImpl.getPackageInfosForUids(
                uids, new ArrayList<>());

        List<PackageInfo> expectedPackageInfos = Arrays.asList(
                constructPackageInfo("vendor_package.A", 10100034, new ArrayList<>(),
                        UidType.APPLICATION, ComponentType.VENDOR, ApplicationCategoryType.OTHERS),
                constructPackageInfo("shared:vendor_shared_package", 10100035,
                        Arrays.asList("vendor_package.B", "third_party_package.C",
                                "third_party_package.G"),
                        UidType.APPLICATION, ComponentType.VENDOR, ApplicationCategoryType.OTHERS),
                constructPackageInfo("third_party_package.H", 10200056, new ArrayList<>(),
                        UidType.APPLICATION, ComponentType.UNKNOWN,
                        ApplicationCategoryType.OTHERS),
                constructPackageInfo("shared:third_party_shared_package", 10200078,
                        Collections.singletonList("third_party_package.I"),
                        UidType.APPLICATION, ComponentType.UNKNOWN,
                        ApplicationCategoryType.OTHERS));

        assertPackageInfoEquals(actualPackageInfos, expectedPackageInfos);
    }

    @Test
    public void testControlProcessHealthCheckEnabled() throws Exception {
        mCarWatchdogService.controlProcessHealthCheck(true);

        verify(mMockWatchdogProcessHandler).controlProcessHealthCheck(eq(true));
    }

    @Test
    public void testControlProcessHealthCheckDisabled() throws Exception {
        mCarWatchdogService.controlProcessHealthCheck(false);

        verify(mMockWatchdogProcessHandler).controlProcessHealthCheck(eq(false));
    }

    @Test
    public void testDisablePackageForUser() throws Exception {
        mCarWatchdogService.performResourceOveruseKill("third_party_package", /* userId= */ 100);

        verify(mMockWatchdogPerfHandler).disablePackageForUser(eq("third_party_package"),
                eq(100));
    }

    @Test
    public void testOveruseConfigurationCacheGetVendorPackagePrefixes() throws Exception {
        OveruseConfigurationCache cache = new OveruseConfigurationCache();

        cache.set(sampleInternalResourceOveruseConfigurations());

        assertWithMessage("Vendor package prefixes").that(cache.getVendorPackagePrefixes())
                .containsExactly("vendor_package", "some_pkg_as_vendor_pkg");
    }

    @Test
    public void testOveruseConfigurationCacheFetchThreshold() throws Exception {
        OveruseConfigurationCache cache = new OveruseConfigurationCache();

        cache.set(sampleInternalResourceOveruseConfigurations());

        InternalPerStateBytesSubject.assertWithMessage(
                cache.fetchThreshold("system_package.non_critical.A", ComponentType.SYSTEM),
                "System package with generic threshold")
                .isEqualTo(constructPerStateBytes(10, 20, 30));

        InternalPerStateBytesSubject.assertWithMessage(
                cache.fetchThreshold("system_package.A", ComponentType.SYSTEM),
                "System package with package specific threshold")
                .isEqualTo(constructPerStateBytes(40, 50, 60));

        InternalPerStateBytesSubject.assertWithMessage(
                cache.fetchThreshold("system_package.MEDIA", ComponentType.SYSTEM),
                "System package with media category threshold")
                .isEqualTo(constructPerStateBytes(200, 400, 600));

        InternalPerStateBytesSubject.assertWithMessage(
                cache.fetchThreshold("vendor_package.non_critical.A", ComponentType.VENDOR),
                "Vendor package with generic threshold")
                .isEqualTo(constructPerStateBytes(20, 40, 60));

        InternalPerStateBytesSubject.assertWithMessage(
                cache.fetchThreshold("vendor_package.A", ComponentType.VENDOR),
                "Vendor package with package specific threshold")
                .isEqualTo(constructPerStateBytes(80, 100, 120));

        InternalPerStateBytesSubject.assertWithMessage(
                cache.fetchThreshold("vendor_package.MEDIA", ComponentType.VENDOR),
                "Vendor package with media category threshold")
                .isEqualTo(constructPerStateBytes(200, 400, 600));

        InternalPerStateBytesSubject.assertWithMessage(
                cache.fetchThreshold("third_party_package.A",
                        ComponentType.THIRD_PARTY),
                "3p package with generic threshold").isEqualTo(constructPerStateBytes(30, 60, 90));

        InternalPerStateBytesSubject.assertWithMessage(
                cache.fetchThreshold("third_party_package.MAPS", ComponentType.VENDOR),
                "3p package with maps category threshold")
                .isEqualTo(constructPerStateBytes(2200, 4400, 6600));
    }

    @Test
    public void testOveruseConfigurationCacheIsSafeToKill() throws Exception {
        OveruseConfigurationCache cache = new OveruseConfigurationCache();

        cache.set(sampleInternalResourceOveruseConfigurations());

        assertWithMessage("isSafeToKill non-critical system package").that(cache.isSafeToKill(
                "system_package.non_critical.A", ComponentType.SYSTEM, null)).isTrue();

        assertWithMessage("isSafeToKill shared non-critical system package")
                .that(cache.isSafeToKill("system_package.A", ComponentType.SYSTEM,
                        Collections.singletonList("system_package.non_critical.A"))).isTrue();

        assertWithMessage("isSafeToKill non-critical vendor package").that(cache.isSafeToKill(
                "vendor_package.non_critical.A", ComponentType.VENDOR, null)).isTrue();

        assertWithMessage("isSafeToKill shared non-critical vendor package")
                .that(cache.isSafeToKill("vendor_package.A", ComponentType.VENDOR,
                        Collections.singletonList("vendor_package.non_critical.A"))).isTrue();

        assertWithMessage("isSafeToKill 3p package").that(cache.isSafeToKill(
                "third_party_package.A", ComponentType.THIRD_PARTY, null)).isTrue();

        assertWithMessage("isSafeToKill critical system package").that(cache.isSafeToKill(
                "system_package.A", ComponentType.SYSTEM, null)).isFalse();

        assertWithMessage("isSafeToKill critical vendor package").that(cache.isSafeToKill(
                "vendor_package.A", ComponentType.VENDOR, null)).isFalse();
    }

    @Test
    public void testOverwriteOveruseConfigurationCache() throws Exception {
        OveruseConfigurationCache cache = new OveruseConfigurationCache();

        cache.set(sampleInternalResourceOveruseConfigurations());

        cache.set(new ArrayList<>());

        assertWithMessage("Vendor package prefixes").that(cache.getVendorPackagePrefixes())
                .isEmpty();

        InternalPerStateBytesSubject.assertWithMessage(
                cache.fetchThreshold("system_package.A", ComponentType.SYSTEM),
                "System package with default threshold")
                .isEqualTo(OveruseConfigurationCache.DEFAULT_THRESHOLD);

        InternalPerStateBytesSubject.assertWithMessage(
                cache.fetchThreshold("vendor_package.A", ComponentType.VENDOR),
                "Vendor package with default threshold")
                .isEqualTo(OveruseConfigurationCache.DEFAULT_THRESHOLD);

        InternalPerStateBytesSubject.assertWithMessage(
                cache.fetchThreshold("third_party_package.A", ComponentType.THIRD_PARTY),
                "3p package with default threshold")
                .isEqualTo(OveruseConfigurationCache.DEFAULT_THRESHOLD);

        assertWithMessage("isSafeToKill any system package").that(cache.isSafeToKill(
                "system_package.non_critical.A", ComponentType.SYSTEM, null)).isFalse();

        assertWithMessage("isSafeToKill any vendor package").that(cache.isSafeToKill(
                "vendor_package.non_critical.A", ComponentType.VENDOR, null)).isFalse();
    }

    @Test
    public void testRelease() throws Exception {
        mCarWatchdogService.release();

        verify(mMockCarPowerManagementService).unregisterListener(
                mICarPowerStateListenerCaptor.capture());
        verify(mMockCarPowerManagementService).removePowerPolicyListener(
                mICarPowerPolicyListenerCaptor.capture());
        verify(mMockWatchdogPerfHandler).release();
        verify(mSpiedWatchdogStorage).release();
        verify(mMockCarWatchdogDaemonHelper).unregisterCarWatchdogService(
                mICarWatchdogServiceForSystemCaptor.capture());
    }

    public static android.automotive.watchdog.PerStateBytes constructPerStateBytes(
            long fgBytes, long bgBytes, long gmBytes) {
        android.automotive.watchdog.PerStateBytes perStateBytes =
                new android.automotive.watchdog.PerStateBytes();
        perStateBytes.foregroundBytes = fgBytes;
        perStateBytes.backgroundBytes = bgBytes;
        perStateBytes.garageModeBytes = gmBytes;
        return perStateBytes;
    }

    private void mockWatchdogStorage() {
        doAnswer((args) -> {
            mUserPackageSettingsEntries.addAll(args.getArgument(0));
            return true;
        }).when(mSpiedWatchdogStorage).saveUserPackageSettings(any());
        doAnswer((args) -> {
            List<WatchdogStorage.IoUsageStatsEntry> ioUsageStatsEntries = args.getArgument(0);
            for (WatchdogStorage.IoUsageStatsEntry entry : ioUsageStatsEntries) {
                mIoUsageStatsEntries.add(
                        new WatchdogStorage.IoUsageStatsEntry(entry.userId, entry.packageName,
                                new WatchdogPerfHandler.PackageIoUsage(
                                        entry.ioUsage.getInternalIoOveruseStats(),
                                        entry.ioUsage.getForgivenWriteBytes(),
                                        entry.ioUsage.getForgivenOveruses(),
                                        entry.ioUsage.getTotalTimesKilled())));
            }
            return ioUsageStatsEntries.size();
        }).when(mSpiedWatchdogStorage).saveIoUsageStats(any());
        doAnswer((args) -> {
            List<WatchdogStorage.UserPackageSettingsEntry> entries =
                    new ArrayList<>(mUserPackageSettingsEntries.size());
            entries.addAll(mUserPackageSettingsEntries);
            return entries;
        }).when(mSpiedWatchdogStorage).getUserPackageSettings();
        doReturn(mIoUsageStatsEntries).when(mSpiedWatchdogStorage).getTodayIoUsageStats();
        doReturn(List.of()).when(mSpiedWatchdogStorage)
                .getNotForgivenHistoricalIoOveruses(RECURRING_OVERUSE_PERIOD_IN_DAYS);
        doAnswer(args -> sampleDailyIoUsageSummariesForAWeek(args.getArgument(1),
                SYSTEM_DAILY_IO_USAGE_SUMMARY_MULTIPLIER))
                .when(mSpiedWatchdogStorage)
                .getDailySystemIoUsageSummaries(anyLong(), anyLong(), anyLong());
        doAnswer(args -> {
            ArrayList<WatchdogStorage.UserPackageDailySummaries> summaries =
                    new ArrayList<>();
            for (int i = 0; i < mGenericPackageNameByUid.size(); ++i) {
                int uid = mGenericPackageNameByUid.keyAt(i);
                summaries.add(new WatchdogStorage.UserPackageDailySummaries(
                        UserHandle.getUserId(uid), mGenericPackageNameByUid.valueAt(i),
                        sampleDailyIoUsageSummariesForAWeek(args.getArgument(2),
                                /* sysOrUidMultiplier= */ uid)));
            }
            summaries.sort(Comparator.comparingLong(WatchdogStorage
                    .UserPackageDailySummaries::getTotalWrittenBytes).reversed());
            return summaries;
        }).when(mSpiedWatchdogStorage)
                .getTopUsersDailyIoUsageSummaries(anyInt(), anyLong(), anyLong(), anyLong());
    }

    private void setupUsers() {
        when(mMockContext.getSystemService(UserManager.class)).thenReturn(mMockUserManager);
        mockUmGetAllUsers(mMockUserManager, new UserHandle[0]);
    }

    private void initService(int wantedInvocations) throws Exception {
        mCarWatchdogService.init();
        verify(mMockWatchdogPerfHandler).init();
        captureCarPowerListeners(wantedInvocations);
        captureBroadcastReceiver(wantedInvocations);
        captureUserLifecycleListener(wantedInvocations);
        captureAndVerifyRegistrationWithDaemon(/* waitOnMain= */ true);
    }

    private void captureCarPowerListeners(int wantedInvocations) {
        verify(mMockCarPowerManagementService, times(wantedInvocations)).registerListener(
                mICarPowerStateListenerCaptor.capture());
        mCarPowerStateListener = mICarPowerStateListenerCaptor.getValue();
        assertWithMessage("Car power state listener").that(mCarPowerStateListener).isNotNull();

        verify(mMockCarPowerManagementService, times(wantedInvocations)).addPowerPolicyListener(
                any(), mICarPowerPolicyListenerCaptor.capture());
        mCarPowerPolicyListener = mICarPowerPolicyListenerCaptor.getValue();
        assertWithMessage("Car power policy listener").that(mCarPowerPolicyListener).isNotNull();
    }

    private void captureBroadcastReceiver(int wantedInvocations) {
        verify(mMockContext, times(wantedInvocations * 2))
                .registerReceiverForAllUsers(mBroadcastReceiverCaptor.capture(),
                        mIntentFilterCaptor.capture(), any(), any(), anyInt());

        mBroadcastReceiver = mBroadcastReceiverCaptor.getValue();
        assertWithMessage("Broadcast receiver").that(mBroadcastReceiver).isNotNull();

        List<IntentFilter> filters = mIntentFilterCaptor.getAllValues();
        int totalFilters = filters.size();
        assertThat(totalFilters).isAtLeast(2);
        // When CarWatchdogService is restarted, registerReceiverForAllUsers will be called more
        // than 2 times. Thus, verify the filters only from the latest 2 calls.
        IntentFilter filter = filters.get(totalFilters - 2);
        assertFilterHasActions(filter, CAR_WATCHDOG_ACTION_DISMISS_RESOURCE_OVERUSE_NOTIFICATION,
                ACTION_GARAGE_MODE_ON, ACTION_GARAGE_MODE_OFF,
                CAR_WATCHDOG_ACTION_LAUNCH_APP_SETTINGS,
                CAR_WATCHDOG_ACTION_RESOURCE_OVERUSE_DISABLE_APP, ACTION_USER_REMOVED);
        filter = filters.get(totalFilters - 1);
        assertFilterHasActions(filter, ACTION_PACKAGE_CHANGED);
        assertFilterHasDataScheme(filter, /* dataScheme= */ "package");
    }

    private void captureUserLifecycleListener(int wantedInvocations) {
        verify(mMockCarUserService, times(wantedInvocations)).addUserLifecycleListener(any(),
                mUserLifecycleListenerCaptor.capture());
        mUserLifecycleListener = mUserLifecycleListenerCaptor.getValue();
        assertWithMessage("User lifecycle listener").that(mUserLifecycleListener).isNotNull();
    }

    private void captureAndVerifyRegistrationWithDaemon(boolean waitOnMain) throws Exception {
        if (waitOnMain) {
            // Registering to daemon is done on the main thread. To ensure the registration
            // completes before verification, execute an empty block on the main thread.
            CarServiceUtils.runOnMainSync(() -> {});
        }

        verify(mMockCarWatchdogDaemonHelper).addOnConnectionChangeListener(
                mOnConnectionChangeListenerArgumentCaptor.capture());
        mOnConnectionChangeListener = mOnConnectionChangeListenerArgumentCaptor.getValue();
        mOnConnectionChangeListener.onConnectionChange(/* isConnected= */ true);

        verify(mMockCarWatchdogDaemonHelper, atLeastOnce()).registerCarWatchdogService(
                mICarWatchdogServiceForSystemCaptor.capture());
        mWatchdogServiceForSystemImpl = mICarWatchdogServiceForSystemCaptor.getValue();
        assertWithMessage("Car watchdog service for system")
                .that(mWatchdogServiceForSystemImpl).isNotNull();

        verify(mMockCarWatchdogDaemonHelper, atLeastOnce()).notifySystemStateChange(
                StateType.GARAGE_MODE, GarageMode.GARAGE_MODE_OFF, MISSING_ARG_VALUE);
    }

    private void mockBuildStatsEventCalls() {
        when(CarStatsLog.buildStatsEvent(eq(CAR_WATCHDOG_SYSTEM_IO_USAGE_SUMMARY),
                any(byte[].class), anyLong())).thenAnswer(args -> {
                    mPulledSystemIoUsageSummaries.add(AtomsProto.CarWatchdogSystemIoUsageSummary
                            .newBuilder()
                            .setIoUsageSummary(AtomsProto.CarWatchdogIoUsageSummary.parseFrom(
                                    (byte[]) args.getArgument(1)))
                            .setStartTimeMillis(args.getArgument(2))
                            .build());
                    // Returned event is not used in tests, so return an empty event.
                    return StatsEvent.newBuilder().build();
                });

        when(CarStatsLog.buildStatsEvent(eq(CAR_WATCHDOG_UID_IO_USAGE_SUMMARY), anyInt(),
                any(byte[].class), anyLong())).thenAnswer(args -> {
                    mPulledUidIoUsageSummaries.add(AtomsProto.CarWatchdogUidIoUsageSummary
                            .newBuilder()
                            .setUid(args.getArgument(1))
                            .setIoUsageSummary(AtomsProto.CarWatchdogIoUsageSummary.parseFrom(
                                    (byte[]) args.getArgument(2)))
                            .setStartTimeMillis(args.getArgument(3))
                            .build());
                    // Returned event is not used in tests, so return an empty event.
                    return StatsEvent.newBuilder().build();
                });
    }

    private void mockPackageManager() throws Exception {
        when(mMockPackageManager.getNamesForUids(any())).thenAnswer(args -> {
            int[] uids = args.getArgument(0);
            String[] names = new String[uids.length];
            for (int i = 0; i < uids.length; ++i) {
                names[i] = mGenericPackageNameByUid.get(uids[i], null);
            }
            return names;
        });
        when(mMockPackageManager.getPackagesForUid(anyInt())).thenAnswer(args -> {
            int uid = args.getArgument(0);
            List<String> packages = mPackagesBySharedUid.get(uid);
            return packages.toArray(new String[0]);
        });
        when(mMockPackageManager.getApplicationInfoAsUser(anyString(), anyInt(), any()))
                .thenAnswer(args -> {
                    int userId = ((UserHandle) args.getArgument(2)).getIdentifier();
                    String userPackageId = userId + USER_PACKAGE_SEPARATOR + args.getArgument(0);
                    android.content.pm.PackageInfo packageInfo =
                            mPmPackageInfoByUserPackage.get(userPackageId);
                    if (packageInfo == null) {
                        throw new PackageManager.NameNotFoundException(
                                "User package id '" + userPackageId + "' not found");
                    }
                    return packageInfo.applicationInfo;
                });
        when(mMockPackageManager.getPackageInfoAsUser(anyString(), anyInt(), anyInt()))
                .thenAnswer(args -> {
                    String userPackageId = args.getArgument(2) + USER_PACKAGE_SEPARATOR
                            + args.getArgument(0);
                    android.content.pm.PackageInfo packageInfo =
                            mPmPackageInfoByUserPackage.get(userPackageId);
                    if (packageInfo == null) {
                        throw new PackageManager.NameNotFoundException(
                                "User package id '" + userPackageId + "' not found");
                    }
                    return packageInfo;
                });
        when(mMockPackageManager.getInstalledPackagesAsUser(anyInt(), anyInt()))
                .thenAnswer(args -> {
                    int userId = args.getArgument(1);
                    List<android.content.pm.PackageInfo> packageInfos = new ArrayList<>();
                    for (android.content.pm.PackageInfo packageInfo :
                            mPmPackageInfoByUserPackage.values()) {
                        if (UserHandle.getUserId(packageInfo.applicationInfo.uid) == userId) {
                            packageInfos.add(packageInfo);
                        }
                    }
                    return packageInfos;
                });
        when(mMockPackageManager.getPackageUidAsUser(anyString(), anyInt()))
                .thenAnswer(args -> {
                    String userPackageId = args.getArgument(1) + USER_PACKAGE_SEPARATOR
                            + args.getArgument(0);
                    android.content.pm.PackageInfo packageInfo =
                            mPmPackageInfoByUserPackage.get(userPackageId);
                    if (packageInfo == null) {
                        throw new PackageManager.NameNotFoundException(
                                "User package id '" + userPackageId + "' not found");
                    }
                    return packageInfo.applicationInfo.uid;
                });
        doAnswer((args) -> {
            String value = args.getArgument(3) + USER_PACKAGE_SEPARATOR
                    + args.getArgument(0);
            mDisabledUserPackages.add(value);
            return null;
        }).when(mSpiedPackageManager).setApplicationEnabledSetting(
                anyString(), eq(COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED), anyInt(),
                anyInt(), anyString());
        doAnswer((args) -> {
            String value = args.getArgument(3) + USER_PACKAGE_SEPARATOR
                    + args.getArgument(0);
            mDisabledUserPackages.remove(value);
            return null;
        }).when(mSpiedPackageManager).setApplicationEnabledSetting(
                anyString(), eq(COMPONENT_ENABLED_STATE_ENABLED), anyInt(),
                anyInt(), anyString());
        doReturn(COMPONENT_ENABLED_STATE_ENABLED).when(mSpiedPackageManager)
                .getApplicationEnabledSetting(anyString(), anyInt());
    }

    private void setCarPowerState(int powerState) throws Exception {
        when(mMockCarPowerManagementService.getPowerState()).thenReturn(powerState);
        mCarPowerStateListener.onStateChanged(powerState, /* timeoutMs= */ -1);
    }

    private void injectPackageInfos(List<android.content.pm.PackageInfo> packageInfos) {
        for (android.content.pm.PackageInfo packageInfo : packageInfos) {
            String genericPackageName = packageInfo.packageName;
            int uid = packageInfo.applicationInfo.uid;
            int userId = UserHandle.getUserId(uid);
            if (packageInfo.sharedUserId != null) {
                genericPackageName =
                        PackageInfoHandler.SHARED_PACKAGE_PREFIX + packageInfo.sharedUserId;
                List<String> packages = mPackagesBySharedUid.get(uid);
                if (packages == null) {
                    packages = new ArrayList<>();
                }
                packages.add(packageInfo.packageName);
                mPackagesBySharedUid.put(uid, packages);
            }
            String userPackageId = userId + USER_PACKAGE_SEPARATOR + packageInfo.packageName;
            assertWithMessage("Duplicate package infos provided for user package id: %s",
                    userPackageId).that(mPmPackageInfoByUserPackage.containsKey(userPackageId))
                    .isFalse();
            assertWithMessage("Mismatch generic package names for the same uid '%s'",
                    uid).that(mGenericPackageNameByUid.get(uid, genericPackageName))
                    .isEqualTo(genericPackageName);
            mPmPackageInfoByUserPackage.put(userPackageId, packageInfo);
            mGenericPackageNameByUid.put(uid, genericPackageName);
        }
    }

    public static boolean isUserPackageIoUsageStatsEquals(UserPackageIoUsageStats actual,
            UserPackageIoUsageStats expected) {
        return actual.userId == expected.userId && actual.packageName.equals(expected.packageName)
                && isInternalPerStateBytesEquals(
                        actual.ioUsageStats.writtenBytes, expected.ioUsageStats.writtenBytes)
                && isInternalPerStateBytesEquals(actual.ioUsageStats.forgivenWriteBytes,
                        expected.ioUsageStats.forgivenWriteBytes)
                && actual.ioUsageStats.totalOveruses == expected.ioUsageStats.totalOveruses;
    }

    public static boolean isInternalPerStateBytesEquals(
            android.automotive.watchdog.PerStateBytes actual,
            android.automotive.watchdog.PerStateBytes expected) {
        return actual.foregroundBytes == expected.foregroundBytes
                && actual.backgroundBytes == expected.backgroundBytes
                && actual.garageModeBytes == expected.garageModeBytes;
    }

    private android.automotive.watchdog.IoOveruseStats constructInternalIoOveruseStats(
            boolean killableOnOveruse,
            android.automotive.watchdog.PerStateBytes remainingWriteBytes,
            android.automotive.watchdog.PerStateBytes writtenBytes, int totalOveruses) {
        return constructInternalIoOveruseStats(killableOnOveruse, STATS_DURATION_SECONDS,
                remainingWriteBytes, writtenBytes, totalOveruses);
    }

    private android.automotive.watchdog.IoOveruseStats constructInternalIoOveruseStats(
            boolean killableOnOveruse, long durationInSecs,
            android.automotive.watchdog.PerStateBytes remainingWriteBytes,
            android.automotive.watchdog.PerStateBytes writtenBytes, int totalOveruses) {
        android.automotive.watchdog.IoOveruseStats stats =
                new android.automotive.watchdog.IoOveruseStats();
        stats.startTime = mTimeSource.now().getEpochSecond();
        stats.durationInSeconds = durationInSecs;
        stats.killableOnOveruse = killableOnOveruse;
        stats.remainingWriteBytes = remainingWriteBytes;
        stats.writtenBytes = writtenBytes;
        stats.totalOveruses = totalOveruses;
        return stats;
    }

    private List<AtomsProto.CarWatchdogDailyIoUsageSummary> sampleDailyIoUsageSummariesForAWeek(
            long startEpochSeconds, long sysOrUidMultiplier) {
        List<AtomsProto.CarWatchdogDailyIoUsageSummary> summaries = new ArrayList<>();
        long weekMultiplier = ChronoUnit.WEEKS.between(
                ZonedDateTime.ofInstant(Instant.ofEpochSecond(startEpochSeconds), ZONE_OFFSET),
                mTimeSource.getCurrentDate());
        for (int i = 1; i < 8; ++i) {
            summaries.add(constructCarWatchdogDailyIoUsageSummary(
                    /* fgWrBytes= */ 100 * i * weekMultiplier * sysOrUidMultiplier,
                    /* bgWrBytes= */ 200 * i * weekMultiplier * sysOrUidMultiplier,
                    /* gmWrBytes= */ 300 * i * weekMultiplier * sysOrUidMultiplier,
                    /* overuseCount= */ 2 * i));
        }
        return summaries;
    }

    static AtomsProto.CarWatchdogDailyIoUsageSummary constructCarWatchdogDailyIoUsageSummary(
            long fgWrBytes, long bgWrBytes, long gmWrBytes, int overuseCount) {
        return AtomsProto.CarWatchdogDailyIoUsageSummary.newBuilder()
                .setWrittenBytes(WatchdogPerfHandler
                        .constructCarWatchdogPerStateBytes(fgWrBytes, bgWrBytes, gmWrBytes))
                .setOveruseCount(overuseCount)
                .build();
    }

    private static PackageInfo constructPackageInfo(String packageName, int uid,
            List<String> sharedUidPackages, int uidType, int componentType, int appCategoryType) {
        PackageInfo packageInfo = new PackageInfo();
        packageInfo.packageIdentifier = new PackageIdentifier();
        packageInfo.packageIdentifier.name = packageName;
        packageInfo.packageIdentifier.uid = uid;
        packageInfo.uidType = uidType;
        packageInfo.sharedUidPackages = sharedUidPackages;
        packageInfo.componentType = componentType;
        packageInfo.appCategoryType = appCategoryType;

        return packageInfo;
    }

    private static String toPackageInfosString(List<PackageInfo> packageInfos) {
        StringBuilder builder = new StringBuilder();
        for (PackageInfo packageInfo : packageInfos) {
            builder = packageInfoStringBuilder(builder, packageInfo).append('\n');
        }
        return builder.toString();
    }

    private static StringBuilder packageInfoStringBuilder(
            StringBuilder builder, PackageInfo packageInfo) {
        if (packageInfo == null) {
            return builder.append("Null package info\n");
        }
        builder.append("Package name: '").append(packageInfo.packageIdentifier.name)
                .append("', UID: ").append(packageInfo.packageIdentifier.uid).append('\n')
                .append("Owned packages: ");
        if (packageInfo.sharedUidPackages != null) {
            for (int i = 0; i < packageInfo.sharedUidPackages.size(); ++i) {
                builder.append('\'').append(packageInfo.sharedUidPackages.get(i)).append('\'');
                if (i < packageInfo.sharedUidPackages.size() - 1) {
                    builder.append(", ");
                }
            }
            builder.append('\n');
        } else {
            builder.append("Null");
        }
        builder.append("Component type: ").append(packageInfo.componentType).append('\n')
                .append("Application category type: ").append(packageInfo.appCategoryType).append(
                '\n');

        return builder;
    }

    private static void assertPackageInfoEquals(List<PackageInfo> actual,
            List<PackageInfo> expected) throws Exception {
        assertWithMessage("Package infos for UIDs:\nExpected: %s\nActual: %s",
                CarWatchdogServiceUnitTest.toPackageInfosString(expected),
                CarWatchdogServiceUnitTest.toPackageInfosString(actual))
                .that(actual)
                .comparingElementsUsing(
                        Correspondence.from(CarWatchdogServiceUnitTest::isPackageInfoEquals,
                                "is package info equal to")).containsExactlyElementsIn(expected);
    }

    private static boolean isPackageInfoEquals(PackageInfo lhs, PackageInfo rhs) {
        return isEquals(lhs.packageIdentifier, rhs.packageIdentifier)
                && lhs.sharedUidPackages.containsAll(rhs.sharedUidPackages)
                && lhs.componentType == rhs.componentType
                && lhs.appCategoryType == rhs.appCategoryType;
    }

    private static boolean isEquals(PackageIdentifier lhs, PackageIdentifier rhs) {
        return lhs.name.equals(rhs.name) && lhs.uid == rhs.uid;
    }

    private static android.content.pm.PackageInfo constructPackageManagerPackageInfo(
            String packageName, int uid, String sharedUserId) {
        if (packageName.startsWith("system")) {
            return constructPackageManagerPackageInfo(
                    packageName, uid, sharedUserId, ApplicationInfo.FLAG_SYSTEM, 0);
        }
        if (packageName.startsWith("vendor")) {
            return constructPackageManagerPackageInfo(
                    packageName, uid, sharedUserId, ApplicationInfo.FLAG_SYSTEM,
                    ApplicationInfo.PRIVATE_FLAG_OEM);
        }
        return constructPackageManagerPackageInfo(packageName, uid, sharedUserId, 0, 0);
    }

    private static android.content.pm.PackageInfo constructPackageManagerPackageInfo(
            String packageName, int uid, String sharedUserId, int flags, int privateFlags) {
        android.content.pm.PackageInfo packageInfo = new android.content.pm.PackageInfo();
        packageInfo.packageName = packageName;
        packageInfo.sharedUserId = sharedUserId;
        packageInfo.applicationInfo = new ApplicationInfo();
        packageInfo.applicationInfo.packageName = packageName;
        packageInfo.applicationInfo.uid = uid;
        packageInfo.applicationInfo.flags = flags;
        packageInfo.applicationInfo.privateFlags = privateFlags;
        return packageInfo;
    }

    private static final class TestClient extends ICarWatchdogServiceCallback.Stub {
        @Override
        public void onCheckHealthStatus(int sessionId, int timeout) {}
        @Override
        public void onPrepareProcessTermination() {}
    }

    static final class TestTimeSource extends TimeSource {
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

        void updateNow(int numDaysAgo) {
            mNow = TEST_DATE_TIME.minus(numDaysAgo, ChronoUnit.DAYS);
        }
    }
}
