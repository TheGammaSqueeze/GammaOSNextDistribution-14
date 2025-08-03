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

package com.android.car.watchdog;

import static android.app.StatsManager.PULL_SKIP;
import static android.app.StatsManager.PULL_SUCCESS;
import static android.car.drivingstate.CarUxRestrictions.UX_RESTRICTIONS_BASELINE;
import static android.car.settings.CarSettings.Secure.KEY_PACKAGES_DISABLED_ON_RESOURCE_OVERUSE;
import static android.car.test.mocks.AndroidMockitoHelper.mockAmGetCurrentUser;
import static android.car.test.mocks.AndroidMockitoHelper.mockUmGetAllUsers;
import static android.car.test.mocks.AndroidMockitoHelper.mockUmGetUserHandles;
import static android.car.watchdog.CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO;
import static android.car.watchdog.CarWatchdogManager.RETURN_CODE_SUCCESS;
import static android.content.Intent.ACTION_PACKAGE_CHANGED;
import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
import static android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS;

import static com.android.car.CarStatsLog.CAR_WATCHDOG_KILL_STATS_REPORTED__KILL_REASON__KILLED_ON_IO_OVERUSE;
import static com.android.car.CarStatsLog.CAR_WATCHDOG_KILL_STATS_REPORTED__SYSTEM_STATE__GARAGE_MODE;
import static com.android.car.CarStatsLog.CAR_WATCHDOG_KILL_STATS_REPORTED__SYSTEM_STATE__USER_NO_INTERACTION_MODE;
import static com.android.car.CarStatsLog.CAR_WATCHDOG_KILL_STATS_REPORTED__UID_STATE__UNKNOWN_UID_STATE;
import static com.android.car.CarStatsLog.CAR_WATCHDOG_SYSTEM_IO_USAGE_SUMMARY;
import static com.android.car.CarStatsLog.CAR_WATCHDOG_UID_IO_USAGE_SUMMARY;
import static com.android.car.internal.NotificationHelperBase.CAR_WATCHDOG_ACTION_DISMISS_RESOURCE_OVERUSE_NOTIFICATION;
import static com.android.car.internal.NotificationHelperBase.CAR_WATCHDOG_ACTION_LAUNCH_APP_SETTINGS;
import static com.android.car.internal.NotificationHelperBase.CAR_WATCHDOG_ACTION_RESOURCE_OVERUSE_DISABLE_APP;
import static com.android.car.internal.NotificationHelperBase.RESOURCE_OVERUSE_NOTIFICATION_BASE_ID;
import static com.android.car.internal.NotificationHelperBase.RESOURCE_OVERUSE_NOTIFICATION_MAX_OFFSET;
import static com.android.car.watchdog.TimeSource.ZONE_OFFSET;
import static com.android.car.watchdog.WatchdogPerfHandler.MAX_WAIT_TIME_MILLS;
import static com.android.car.watchdog.WatchdogPerfHandler.PACKAGES_DISABLED_ON_RESOURCE_OVERUSE_SEPARATOR;
import static com.android.car.watchdog.WatchdogPerfHandler.USER_PACKAGE_SEPARATOR;
import static com.android.car.watchdog.WatchdogStorage.RETENTION_PERIOD;
import static com.android.car.watchdog.WatchdogStorage.WatchdogDbHelper.DATABASE_NAME;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.mockito.AdditionalMatchers.or;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
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
import android.automotive.watchdog.internal.IoUsageStats;
import android.automotive.watchdog.internal.PackageIoOveruseStats;
import android.automotive.watchdog.internal.PackageMetadata;
import android.automotive.watchdog.internal.PerStateIoOveruseThreshold;
import android.automotive.watchdog.internal.ResourceSpecificConfiguration;
import android.automotive.watchdog.internal.UserPackageIoUsageStats;
import android.car.builtin.content.pm.PackageManagerHelper;
import android.car.drivingstate.CarUxRestrictions;
import android.car.drivingstate.ICarUxRestrictionsChangeListener;
import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.car.test.mocks.MockSettings;
import android.car.watchdog.CarWatchdogManager;
import android.car.watchdog.IResourceOveruseListener;
import android.car.watchdog.IoOveruseAlertThreshold;
import android.car.watchdog.IoOveruseConfiguration;
import android.car.watchdog.IoOveruseStats;
import android.car.watchdog.PackageKillableState;
import android.car.watchdog.PerStateBytes;
import android.car.watchdog.ResourceOveruseConfiguration;
import android.car.watchdog.ResourceOveruseStats;
import android.car.watchdoglib.CarWatchdogDaemonHelper;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Binder;
import android.os.FileUtils;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.AtomicFile;
import android.util.JsonReader;
import android.util.SparseArray;
import android.util.StatsEvent;
import android.util.proto.ProtoOutputStream;
import android.view.Display;

import com.android.car.BuiltinPackageDependency;
import com.android.car.CarLocalServices;
import com.android.car.CarServiceUtils;
import com.android.car.CarStatsLog;
import com.android.car.CarUxRestrictionsManagerService;
import com.android.car.admin.NotificationHelper;

import com.google.common.truth.Correspondence;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

public class WatchdogPerfHandlerUnitTest extends AbstractExtendedMockitoTestCase {
    private static final int UID_IO_USAGE_SUMMARY_TOP_COUNT = 3;
    private static final int RECURRING_OVERUSE_TIMES = 2;
    private static final int RECURRING_OVERUSE_PERIOD_IN_DAYS = 2;
    private static final int IO_USAGE_SUMMARY_MIN_SYSTEM_TOTAL_WRITTEN_BYTES = 500 * 1024 * 1024;
    private static final long STATS_DURATION_SECONDS = 3 * 60 * 60;
    private static final long OVERUSE_HANDLING_DELAY_MILLS = 50;
    private static final int PACKAGE_KILLABLE_STATE_RESET_DAYS = 90;
    private static final long SYSTEM_DAILY_IO_USAGE_SUMMARY_MULTIPLIER = 10_000;
    private static final int CURRENT_USER_ID = 100;
    private static final String WATCHDOG_DIR_NAME = "watchdog";
    private static final String CANONICAL_NAME =
            WatchdogPerfHandlerUnitTest.class.getCanonicalName();
    private static final String CAR_WATCHDOG_SERVICE_NAME =
            CarWatchdogService.class.getSimpleName();
    private static final String SYSTEM_IO_USAGE_SUMMARY_REPORTED_DATE =
            "systemIoUsageSummaryReportedDate";
    private static final String UID_IO_USAGE_SUMMARY_REPORTED_DATE =
            "uidIoUsageSummaryReportedDate";
    private static final String METADATA_FILENAME = "metadata.json";

    @Mock
    private Context mMockContext;
    @Mock
    private Context mMockBuiltinPackageContext;
    @Mock
    private CarWatchdogDaemonHelper mMockCarWatchdogDaemonHelper;
    @Mock
    private CarUxRestrictionsManagerService mMockCarUxRestrictionsManagerService;
    @Mock
    private Resources mMockResources;
    @Mock
    private PackageManager mMockPackageManager;
    @Mock private ClassLoader mMockClassLoader;
    @Mock
    private UserManager mMockUserManager;
    @Mock
    private StatsManager mMockStatsManager;
    @Mock
    private NotificationHelper mMockNotificationHelper;
    @Mock
    private ContentResolver mMockContentResolver;

    @Captor
    private ArgumentCaptor<ICarUxRestrictionsChangeListener>
            mICarUxRestrictionsChangeListenerCaptor;
    @Captor
    private ArgumentCaptor<StatsManager.StatsPullAtomCallback> mStatsPullAtomCallbackCaptor;
    @Captor
    private ArgumentCaptor<List<
            android.automotive.watchdog.internal.ResourceOveruseConfiguration>>
            mResourceOveruseConfigurationsCaptor;
    @Captor private ArgumentCaptor<SparseArray<List<String>>> mPackagesByUserIdCaptor;
    @Captor private ArgumentCaptor<byte[]> mOveruseStatsCaptor;
    @Captor private ArgumentCaptor<byte[]> mKilledStatsCaptor;
    @Captor private ArgumentCaptor<Integer> mOverusingUidCaptor;
    @Captor private ArgumentCaptor<Integer> mKilledUidCaptor;
    @Captor private ArgumentCaptor<Integer> mUidStateCaptor;
    @Captor private ArgumentCaptor<Integer> mSystemStateCaptor;
    @Captor private ArgumentCaptor<Integer> mKillReasonCaptor;
    @Captor private ArgumentCaptor<UserHandle> mUserHandleCaptor;
    @Captor private ArgumentCaptor<SparseArray<String>> mHeadsUpPackagesCaptor;
    @Captor private ArgumentCaptor<SparseArray<String>> mNotificationCenterPackagesCaptor;
    @Captor private ArgumentCaptor<Intent> mStartActivityAsUserIntentCaptor;
    @Captor
    private ArgumentCaptor<List<WatchdogStorage.UserPackageSettingsEntry>>
            mUserPackageSettingsEntryCaptor;
    @Captor
    private ArgumentCaptor<List<WatchdogStorage.IoUsageStatsEntry>> mIoUsageStatsEntryCaptor;

    private ICarUxRestrictionsChangeListener mCarUxRestrictionsChangeListener;
    private StatsManager.StatsPullAtomCallback mStatsPullAtomCallback;
    private WatchdogStorage mSpiedWatchdogStorage;
    private WatchdogPerfHandler mWatchdogPerfHandler;
    private File mTempSystemCarDir;
    // Not used directly, but sets proper mockStatic() expectations on Settings
    @SuppressWarnings("UnusedVariable")
    private MockSettings mMockSettings;

    private final SparseArray<String> mGenericPackageNameByUid = new SparseArray<>();
    private final CarWatchdogServiceUnitTest.TestTimeSource mTimeSource =
            new CarWatchdogServiceUnitTest.TestTimeSource();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final SparseArray<List<String>> mPackagesBySharedUid = new SparseArray<>();
    private final ArrayMap<String, android.content.pm.PackageInfo> mPmPackageInfoByUserPackage =
            new ArrayMap<>();
    private final ArraySet<String> mDisabledUserPackages = new ArraySet<>();
    private final SparseArray<String> mDisabledPackagesSettingsStringByUserid = new SparseArray<>();
    private final IPackageManager mSpiedPackageManager = spy(ActivityThread.getPackageManager());
    private final Set<WatchdogStorage.UserPackageSettingsEntry> mUserPackageSettingsEntries =
            new ArraySet<>();
    private final List<WatchdogStorage.IoUsageStatsEntry> mIoUsageStatsEntries = new ArrayList<>();
    private final List<AtomsProto.CarWatchdogSystemIoUsageSummary> mPulledSystemIoUsageSummaries =
            new ArrayList<>();
    private final List<AtomsProto.CarWatchdogUidIoUsageSummary> mPulledUidIoUsageSummaries =
            new ArrayList<>();

    public WatchdogPerfHandlerUnitTest() {
        super(CarWatchdogService.TAG);
    }

    @Override
    protected void onSessionBuilder(CustomMockitoSessionBuilder builder) {
        mMockSettings = new MockSettings(builder);
        builder.spyStatic(PackageManagerHelper.class)
                .spyStatic(CarServiceUtils.class)
                .spyStatic(ActivityManager.class)
                .spyStatic(ActivityThread.class)
                .spyStatic(CarStatsLog.class)
                .spyStatic(BuiltinPackageDependency.class)
                .spyStatic(CarWatchdogService.class)
                .spyStatic(CarLocalServices.class);
    }

    @Before
    public void setUp() throws Exception {
        when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);
        when(mMockContext.getResources()).thenReturn(mMockResources);
        when(mMockContext.getSystemService(StatsManager.class)).thenReturn(mMockStatsManager);
        when(mMockContext.getPackageName()).thenReturn(CANONICAL_NAME);
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
        doReturn(mMockCarUxRestrictionsManagerService)
                .when(() -> CarLocalServices.getService(CarUxRestrictionsManagerService.class));
        doReturn(mSpiedPackageManager).when(() -> ActivityThread.getPackageManager());
        when(mMockCarUxRestrictionsManagerService.getCurrentUxRestrictions())
                .thenReturn(new CarUxRestrictions.Builder(/* reqOpt= */ false,
                        UX_RESTRICTIONS_BASELINE, /* time= */ 0).build());
        when(mMockCarWatchdogDaemonHelper.getResourceOveruseConfigurations()).thenReturn(
                sampleInternalResourceOveruseConfigurations());
        when(mMockBuiltinPackageContext.getClassLoader()).thenReturn(mMockClassLoader);
        doReturn(NotificationHelper.class).when(mMockClassLoader).loadClass(any());
        doReturn(mMockNotificationHelper)
                .when(() -> BuiltinPackageDependency.createNotificationHelper(
                        mMockBuiltinPackageContext));

        mTempSystemCarDir = Files.createTempDirectory("watchdog_test").toFile();
        doReturn(new File(mTempSystemCarDir.getAbsolutePath(), WATCHDOG_DIR_NAME)).when(
                CarWatchdogService::getWatchdogDirFile);

        File tempDbFile = new File(mTempSystemCarDir.getPath(), DATABASE_NAME);
        when(mMockContext.createDeviceProtectedStorageContext()).thenReturn(mMockContext);
        when(mMockContext.getDatabasePath(DATABASE_NAME)).thenReturn(tempDbFile);
        mSpiedWatchdogStorage =
                spy(new WatchdogStorage(mMockContext, /* useDataSystemCarDir= */ false,
                        mTimeSource));
        mWatchdogPerfHandler = new WatchdogPerfHandler(mMockContext,
                mMockBuiltinPackageContext, mMockCarWatchdogDaemonHelper,
                new PackageInfoHandler(mMockContext.getPackageManager()),
                mSpiedWatchdogStorage, mTimeSource);

        setupUsers();
        mockSettingsStringCalls();
        mockBuildStatsEventCalls();
        mockPackageManager();
        mockWatchdogStorage();
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
    }

    @Test
    public void testAddResourceOveruseListenerThrowsWithInvalidFlag() throws Exception {
        IResourceOveruseListener mockListener = createMockResourceOveruseListener();

        assertThrows(IllegalArgumentException.class, () -> {
            mWatchdogPerfHandler.addResourceOveruseListener(/* resourceOveruseFlag= */ 0,
                    mockListener);
        });
    }

    @Test
    public void testProcessUserNotificationIntentDisablePackage() {
        String packageName = "third_party_package";
        UserHandle userHandle = UserHandle.of(100);
        int notificationId = 150;

        Intent intent = new Intent(CAR_WATCHDOG_ACTION_RESOURCE_OVERUSE_DISABLE_APP)
                .putExtra(Intent.EXTRA_PACKAGE_NAME, packageName)
                .putExtra(Intent.EXTRA_USER, userHandle)
                .putExtra(WatchdogPerfHandler.INTENT_EXTRA_NOTIFICATION_ID, notificationId);

        mWatchdogPerfHandler.processUserNotificationIntent(intent);

        verifyDisabledPackages(/* userPackagesCsv= */ "100:third_party_package");

        verify(mMockNotificationHelper).cancelNotificationAsUser(eq(userHandle),
                eq(notificationId));
    }

    @Test
    public void testProcessUserNotificationIntentDisablePackageWithDisabledPackage() {
        String packageName = "third_party_package";
        UserHandle userHandle = UserHandle.of(100);
        int notificationId = 150;
        doReturn(COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED).when(() -> PackageManagerHelper
                .getApplicationEnabledSettingForUser(anyString(), anyInt()));

        Intent intent = new Intent(CAR_WATCHDOG_ACTION_RESOURCE_OVERUSE_DISABLE_APP)
                .putExtra(Intent.EXTRA_PACKAGE_NAME, packageName)
                .putExtra(Intent.EXTRA_USER, userHandle)
                .putExtra(WatchdogPerfHandler.INTENT_EXTRA_NOTIFICATION_ID, notificationId);

        mWatchdogPerfHandler.processUserNotificationIntent(intent);

        verifyNoDisabledPackages();

        verify(mMockNotificationHelper).cancelNotificationAsUser(eq(userHandle),
                eq(notificationId));
    }

    @Test
    public void testProcessUserNotificationIntentDisablePackageWithMissingNotificationId() {
        String packageName = "third_party_package";
        UserHandle userHandle = UserHandle.of(100);

        Intent intent = new Intent(CAR_WATCHDOG_ACTION_RESOURCE_OVERUSE_DISABLE_APP)
                .putExtra(Intent.EXTRA_PACKAGE_NAME, packageName)
                .putExtra(Intent.EXTRA_USER, userHandle);

        mWatchdogPerfHandler.processUserNotificationIntent(intent);

        verifyDisabledPackages(/* userPackagesCsv= */ "100:third_party_package");

        verify(mMockNotificationHelper, never()).cancelNotificationAsUser(any(), anyInt());
    }

    @Test
    public void testProcessUserNotificationIntentDismissNotification() {
        String packageName = "third_party_package";
        UserHandle userHandle = UserHandle.of(100);
        int notificationId = 150;

        Intent intent = new Intent(CAR_WATCHDOG_ACTION_DISMISS_RESOURCE_OVERUSE_NOTIFICATION)
                .putExtra(Intent.EXTRA_PACKAGE_NAME, packageName)
                .putExtra(Intent.EXTRA_USER, userHandle)
                .putExtra(WatchdogPerfHandler.INTENT_EXTRA_NOTIFICATION_ID, notificationId);

        mWatchdogPerfHandler.processUserNotificationIntent(intent);

        verifyNoDisabledPackages();

        verify(mMockNotificationHelper).cancelNotificationAsUser(eq(userHandle),
                eq(notificationId));
    }

    @Test
    public void testProcessUserNotificationIntentDismissNotificationWithMissingNotificationId() {
        String packageName = "third_party_package";
        UserHandle userHandle = UserHandle.of(100);

        Intent intent = new Intent(CAR_WATCHDOG_ACTION_DISMISS_RESOURCE_OVERUSE_NOTIFICATION)
                .putExtra(Intent.EXTRA_PACKAGE_NAME, packageName)
                .putExtra(Intent.EXTRA_USER, userHandle);

        mWatchdogPerfHandler.processUserNotificationIntent(intent);

        verifyNoDisabledPackages();

        verify(mMockNotificationHelper, never()).cancelNotificationAsUser(any(), anyInt());
    }

    @Test
    public void testProcessUserNotificationIntentLaunchAppSettings() {
        String packageName = "third_party_package";
        UserHandle userHandle = UserHandle.of(100);
        int notificationId = 150;

        Intent intent = new Intent(CAR_WATCHDOG_ACTION_LAUNCH_APP_SETTINGS)
                .putExtra(Intent.EXTRA_PACKAGE_NAME, packageName)
                .putExtra(Intent.EXTRA_USER, userHandle)
                .putExtra(WatchdogPerfHandler.INTENT_EXTRA_NOTIFICATION_ID, notificationId);

        mWatchdogPerfHandler.processUserNotificationIntent(intent);

        verify(mMockBuiltinPackageContext).startActivityAsUser(
                mStartActivityAsUserIntentCaptor.capture(), eq(userHandle));

        Intent startActivityAsUserIntent = mStartActivityAsUserIntentCaptor.getValue();

        expectWithMessage("Start Activity Intent Action").that(
                startActivityAsUserIntent.getAction()).isEqualTo(
                ACTION_APPLICATION_DETAILS_SETTINGS);
        expectWithMessage("Start Activity Intent Data").that(
                startActivityAsUserIntent.getData()).isEqualTo(Uri.parse("package:" + packageName));
        expectWithMessage("Start Activity Intent Flag").that(
                startActivityAsUserIntent.getFlags()).isEqualTo(
                FLAG_ACTIVITY_CLEAR_TASK | FLAG_ACTIVITY_NEW_TASK);

        verify(mMockNotificationHelper).cancelNotificationAsUser(eq(userHandle),
                eq(notificationId));
    }

    @Test
    public void testWriteToDatabaseForSaveUserPackageSettings() {
        UserHandle userHandle = UserHandle.of(101);
        injectPackageInfos(Collections.singletonList(
                constructPackageManagerPackageInfo("third_party_package",
                        10103456, /* sharedUserId= */  null)));
        mWatchdogPerfHandler.setKillablePackageAsUser("third_party_package", userHandle,
                /* isKillable= */ false);
        List<WatchdogStorage.UserPackageSettingsEntry> expectedSavedUserPackageEntries =
                List.of(
                        new WatchdogStorage.UserPackageSettingsEntry(/* userId= */ 101,
                                "third_party_package",
                                /* killableState= */ PackageKillableState.KILLABLE_STATE_NO,
                                /* lastModifiedKillableStateEpoch= */ 123456789));

        mWatchdogPerfHandler.writeToDatabase();

        verify(mSpiedWatchdogStorage, times(1)).startWrite();
        verify(mSpiedWatchdogStorage, times(1)).saveUserPackageSettings(
                mUserPackageSettingsEntryCaptor.capture());
        expectWithMessage("User Package Settings Saved").that(
                mUserPackageSettingsEntryCaptor.getValue()).containsExactlyElementsIn(
                expectedSavedUserPackageEntries);
        verify(mSpiedWatchdogStorage, times(1)).markWriteSuccessful();
        verify(mSpiedWatchdogStorage, times(1)).endWrite();
    }

    @Test
    public void testWriteToDatabaseForSaveIoUsageStatsWithForgive() throws Exception {
        doReturn(Arrays.asList(new WatchdogStorage.NotForgivenOverusesEntry(100,
                "third_party_package", 2))).when(mSpiedWatchdogStorage)
                .getNotForgivenHistoricalIoOveruses(RECURRING_OVERUSE_PERIOD_IN_DAYS);
        mTimeSource.updateNow(/* numDaysAgo= */ 1);
        mockUmGetUserHandles(mMockUserManager, /* excludeDying= */ true, 100);
        injectPackageInfos(Arrays.asList(
                constructPackageManagerPackageInfo("system_package", 10011200, null),
                constructPackageManagerPackageInfo("third_party_package", 10001100, null)));

        List<PackageIoOveruseStats> packageIoOveruseStats = Arrays.asList(
                constructPackageIoOveruseStats(10011200, /* shouldNotify= */ false,
                        /* forgivenWriteBytes= */ constructPerStateBytes(600, 700, 800),
                        constructInternalIoOveruseStats(/* killableOnOveruse= */ true,
                                /* remainingWriteBytes= */ constructPerStateBytes(0, 0, 0),
                                /* writtenBytes= */ constructPerStateBytes(600, 700, 800),
                                /* totalOveruses= */ 3)),
                constructPackageIoOveruseStats(10001100, /* shouldNotify= */ false,
                        /* forgivenWriteBytes= */ constructPerStateBytes(1050, 1100, 1200),
                        constructInternalIoOveruseStats(/* killableOnOveruse= */ true,
                                /* remainingWriteBytes= */ constructPerStateBytes(50, 60, 70),
                                /* writtenBytes= */ constructPerStateBytes(1100, 1200, 1300),
                                /* totalOveruses= */ 5)));

        pushLatestIoOveruseStatsAndWait(packageIoOveruseStats);

        List<WatchdogStorage.IoUsageStatsEntry> expectedSavedIoUsageEntries = Arrays.asList(
                new WatchdogStorage.IoUsageStatsEntry(/* userId= */ 100, "system_package",
                        new WatchdogPerfHandler.PackageIoUsage(
                                packageIoOveruseStats.get(0).ioOveruseStats,
                                /* forgivenWriteBytes= */ constructPerStateBytes(600, 700, 800),
                                /* forgivenOveruses= */ 3, /* totalTimesKilled= */ 0)),
                new WatchdogStorage.IoUsageStatsEntry(/* userId= */ 100, "third_party_package",
                        new WatchdogPerfHandler.PackageIoUsage(
                                packageIoOveruseStats.get(1).ioOveruseStats,
                                /* forgivenWriteBytes= */ constructPerStateBytes(1050, 1100, 1200),
                                /* forgivenOveruses= */ 0, /* totalTimesKilled= */ 0)));

        mWatchdogPerfHandler.writeToDatabase();

        // An attempt to write to the database is also triggered by checkAndHandleDateChange after
        // changing the date.
        verify(mSpiedWatchdogStorage, times(2)).startWrite();
        verify(mSpiedWatchdogStorage).forgiveHistoricalOveruses(mPackagesByUserIdCaptor.capture(),
                eq(RECURRING_OVERUSE_PERIOD_IN_DAYS));
        assertWithMessage("Forgiven packages")
                .that(mPackagesByUserIdCaptor.getValue().get(100))
                .containsExactlyElementsIn(Arrays.asList("third_party_package", "system_package"));
        verify(mSpiedWatchdogStorage, times(1)).saveIoUsageStats(
                mIoUsageStatsEntryCaptor.capture());
        IoUsageStatsEntrySubject.assertThat(mIoUsageStatsEntryCaptor.getValue())
                .containsExactlyElementsIn(expectedSavedIoUsageEntries);
        verify(mSpiedWatchdogStorage, times(1)).markWriteSuccessful();
        verify(mSpiedWatchdogStorage, times(1)).endWrite();
    }

    @Test
    public void testWriteToDatabaseForSaveIoUsageStatsWithoutForgive() throws Exception {
        mockUmGetUserHandles(mMockUserManager, /* excludeDying= */ true, 100);
        injectPackageInfos(Arrays.asList(
                constructPackageManagerPackageInfo("system_package", 10011200, null),
                constructPackageManagerPackageInfo("third_party_package", 10001100, null)));

        List<PackageIoOveruseStats> packageIoOveruseStats = Arrays.asList(
                constructPackageIoOveruseStats(10011200, /* shouldNotify= */ false,
                        /* forgivenWriteBytes= */ constructPerStateBytes(600, 700, 800),
                        constructInternalIoOveruseStats(/* killableOnOveruse= */ true,
                                /* remainingWriteBytes= */ constructPerStateBytes(0, 0, 0),
                                /* writtenBytes= */ constructPerStateBytes(600, 700, 800),
                                /* totalOveruses= */ 3)),
                constructPackageIoOveruseStats(10001100, /* shouldNotify= */ false,
                        /* forgivenWriteBytes= */ constructPerStateBytes(1050, 1100, 1200),
                        constructInternalIoOveruseStats(/* killableOnOveruse= */ true,
                                /* remainingWriteBytes= */ constructPerStateBytes(50, 60, 70),
                                /* writtenBytes= */ constructPerStateBytes(1100, 1200, 1300),
                                /* totalOveruses= */ 5)));

        pushLatestIoOveruseStatsAndWait(packageIoOveruseStats);

        List<WatchdogStorage.IoUsageStatsEntry> expectedSavedIoUsageEntries = Arrays.asList(
                new WatchdogStorage.IoUsageStatsEntry(/* userId= */ 100, "system_package",
                        new WatchdogPerfHandler.PackageIoUsage(
                                packageIoOveruseStats.get(0).ioOveruseStats,
                                /* forgivenWriteBytes= */ constructPerStateBytes(600, 700, 800),
                                /* forgivenOveruses= */ 3, /* totalTimesKilled= */ 0)),
                new WatchdogStorage.IoUsageStatsEntry(/* userId= */ 100, "third_party_package",
                        new WatchdogPerfHandler.PackageIoUsage(
                                packageIoOveruseStats.get(1).ioOveruseStats,
                                /* forgivenWriteBytes= */ constructPerStateBytes(1050, 1100, 1200),
                                /* forgivenOveruses= */ 0, /* totalTimesKilled= */ 0)));

        mWatchdogPerfHandler.writeToDatabase();

        verify(mSpiedWatchdogStorage, times(1)).startWrite();
        verify(mSpiedWatchdogStorage, times(1)).saveIoUsageStats(
                mIoUsageStatsEntryCaptor.capture());
        IoUsageStatsEntrySubject.assertThat(mIoUsageStatsEntryCaptor.getValue())
                .containsExactlyElementsIn(expectedSavedIoUsageEntries);
        verify(mSpiedWatchdogStorage, times(1)).markWriteSuccessful();
        verify(mSpiedWatchdogStorage, times(1)).endWrite();
    }

    @Test
    public void testDeleteUser() throws Exception {
        mockUmGetUserHandles(mMockUserManager, /* excludeDying= */ true, 100, 101, 102);
        injectPackageInfos(Arrays.asList(
                constructPackageManagerPackageInfo(
                        "third_party_package", 10103456, "vendor_shared_package.critical"),
                constructPackageManagerPackageInfo(
                        "vendor_package", 10103456, "vendor_shared_package.critical"),
                constructPackageManagerPackageInfo("third_party_package.A", 10001100, null),
                constructPackageManagerPackageInfo("third_party_package.A", 10201100, null)));

        SparseArray<PackageIoOveruseStats> packageIoOveruseStatsByUid =
                injectIoOveruseStatsForPackages(
                        mGenericPackageNameByUid,
                        /* killablePackages= */ new ArraySet<>(Collections.singletonList(
                                "third_party_package.A")),
                        /* shouldNotifyPackages= */ new ArraySet<>());

        mWatchdogPerfHandler.setKillablePackageAsUser(
                "third_party_package.A", UserHandle.of(102), /* isKillable= */ false);

        mWatchdogPerfHandler.deleteUser(102);

        List<ResourceOveruseStats> actualStats = mWatchdogPerfHandler.getAllResourceOveruseStats(
                FLAG_RESOURCE_OVERUSE_IO, /* minimumStatsFlag= */ 0,
                CarWatchdogManager.STATS_PERIOD_CURRENT_DAY);

        List<ResourceOveruseStats> expectedStats = Arrays.asList(
                constructResourceOveruseStats(
                        /* uid= */ 10103456, "shared:vendor_shared_package.critical",
                        packageIoOveruseStatsByUid.get(10103456).ioOveruseStats),
                constructResourceOveruseStats(/* uid= */ 10001100, "third_party_package.A",
                        packageIoOveruseStatsByUid.get(10001100).ioOveruseStats));

        ResourceOveruseStatsSubject.assertThat(actualStats)
                .containsExactlyElementsIn(expectedStats);

        verify(mSpiedWatchdogStorage, times(2)).syncUsers(any());
    }

    @Test
    public void testRelease() {
        mWatchdogPerfHandler.release();

        verify(mMockCarUxRestrictionsManagerService).unregisterUxRestrictionsChangeListener(
                mICarUxRestrictionsChangeListenerCaptor.capture());

        assertThat(mICarUxRestrictionsChangeListenerCaptor.getValue())
                .isEqualTo(mCarUxRestrictionsChangeListener);
    }

    @Test
    public void testWriteMetadataFile() throws Exception {
        ZonedDateTime systemIoUsageReportedDate = null;
        ZonedDateTime uidIoUsageReportedDate = null;

        // Set mLastSystemIoUsageSummaryReportedDate and mLastUidIoUsageSummaryReportedDate.
        assertWithMessage("Stats pull atom callback status")
                .that(mStatsPullAtomCallback.onPullAtom(CAR_WATCHDOG_SYSTEM_IO_USAGE_SUMMARY,
                        new ArrayList<>())).isEqualTo(PULL_SUCCESS);

        mWatchdogPerfHandler.writeMetadataFile();

        File metadataFile = new File(mTempSystemCarDir.getAbsolutePath() + '/' + WATCHDOG_DIR_NAME,
                METADATA_FILENAME);
        AtomicFile atomicFile = new AtomicFile(metadataFile);

        FileInputStream fis = atomicFile.openRead();
        JsonReader reader = new JsonReader(new InputStreamReader(fis, StandardCharsets.UTF_8));
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            switch (name) {
                case SYSTEM_IO_USAGE_SUMMARY_REPORTED_DATE:
                    systemIoUsageReportedDate = ZonedDateTime.parse(reader.nextString(),
                            DateTimeFormatter.ISO_DATE_TIME.withZone(ZONE_OFFSET));
                    break;
                case UID_IO_USAGE_SUMMARY_REPORTED_DATE:
                    uidIoUsageReportedDate = ZonedDateTime.parse(reader.nextString(),
                            DateTimeFormatter.ISO_DATE_TIME.withZone(ZONE_OFFSET));
                    break;
                default:
                    reader.skipValue();
            }
        }
        reader.endObject();

        expectWithMessage("System IO Usage Summary Reported Date")
                .that(systemIoUsageReportedDate)
                .isEqualTo(mTimeSource.getCurrentDate());
        expectWithMessage("UID IO Usage Summary Reported Date")
                .that(uidIoUsageReportedDate)
                .isEqualTo(mTimeSource.getCurrentDate().minus(RETENTION_PERIOD));
    }

    @Test
    public void testResourceOveruseListener() throws Exception {
        mGenericPackageNameByUid.put(Binder.getCallingUid(), CANONICAL_NAME);

        IResourceOveruseListener mockListener = createMockResourceOveruseListener();
        IBinder mockBinder = mockListener.asBinder();

        mWatchdogPerfHandler.addResourceOveruseListener(FLAG_RESOURCE_OVERUSE_IO,
                mockListener);

        verify(mockBinder).linkToDeath(any(IBinder.DeathRecipient.class), anyInt());

        injectIoOveruseStatsForPackages(
                mGenericPackageNameByUid, /* killablePackages= */ new ArraySet<>(),
                /* shouldNotifyPackages= */ new ArraySet<>(
                        Collections.singleton(CANONICAL_NAME)));

        verify(mockListener).onOveruse(any());

        mWatchdogPerfHandler.removeResourceOveruseListener(mockListener);

        verify(mockListener, atLeastOnce()).asBinder();
        verify(mockBinder).unlinkToDeath(any(IBinder.DeathRecipient.class), anyInt());

        injectIoOveruseStatsForPackages(
                mGenericPackageNameByUid, /* killablePackages= */ new ArraySet<>(),
                /* shouldNotifyPackages= */ new ArraySet<>(
                        Collections.singletonList(CANONICAL_NAME)));

        verifyNoMoreInteractions(mockListener);
    }

    @Test
    public void testDuplicateAddResourceOveruseListener() throws Exception {
        mGenericPackageNameByUid.put(Binder.getCallingUid(), CANONICAL_NAME);

        IResourceOveruseListener mockListener = createMockResourceOveruseListener();
        IBinder mockBinder = mockListener.asBinder();

        mWatchdogPerfHandler.addResourceOveruseListener(FLAG_RESOURCE_OVERUSE_IO,
                mockListener);

        assertThrows(IllegalStateException.class,
                () -> mWatchdogPerfHandler.addResourceOveruseListener(
                        FLAG_RESOURCE_OVERUSE_IO, mockListener));

        verify(mockBinder).linkToDeath(any(IBinder.DeathRecipient.class), anyInt());

        mWatchdogPerfHandler.removeResourceOveruseListener(mockListener);

        verify(mockListener, atLeastOnce()).asBinder();
        verify(mockBinder).unlinkToDeath(any(IBinder.DeathRecipient.class), anyInt());

        verifyNoMoreInteractions(mockListener);
    }

    @Test
    public void testAddMultipleResourceOveruseListeners() throws Exception {
        mGenericPackageNameByUid.put(Binder.getCallingUid(), CANONICAL_NAME);

        IResourceOveruseListener firstMockListener = createMockResourceOveruseListener();
        IBinder firstMockBinder = firstMockListener.asBinder();
        IResourceOveruseListener secondMockListener = createMockResourceOveruseListener();
        IBinder secondMockBinder = secondMockListener.asBinder();

        mWatchdogPerfHandler.addResourceOveruseListener(FLAG_RESOURCE_OVERUSE_IO,
                firstMockListener);
        mWatchdogPerfHandler.addResourceOveruseListener(FLAG_RESOURCE_OVERUSE_IO,
                secondMockListener);

        verify(firstMockBinder).linkToDeath(any(IBinder.DeathRecipient.class), anyInt());
        verify(secondMockBinder).linkToDeath(any(IBinder.DeathRecipient.class), anyInt());

        injectIoOveruseStatsForPackages(
                mGenericPackageNameByUid, /* killablePackages= */ new ArraySet<>(),
                /* shouldNotifyPackages= */ new ArraySet<>(
                        Collections.singleton(CANONICAL_NAME)));

        verify(firstMockListener).onOveruse(any());

        mWatchdogPerfHandler.removeResourceOveruseListener(firstMockListener);

        verify(firstMockListener, atLeastOnce()).asBinder();
        verify(firstMockBinder).unlinkToDeath(any(IBinder.DeathRecipient.class), anyInt());

        injectIoOveruseStatsForPackages(
                mGenericPackageNameByUid, /* killablePackages= */ new ArraySet<>(),
                /* shouldNotifyPackages= */ new ArraySet<>(
                        Collections.singletonList(CANONICAL_NAME)));

        verify(secondMockListener, times(2)).onOveruse(any());

        mWatchdogPerfHandler.removeResourceOveruseListener(secondMockListener);

        verify(secondMockListener, atLeastOnce()).asBinder();
        verify(secondMockBinder).unlinkToDeath(any(IBinder.DeathRecipient.class), anyInt());

        injectIoOveruseStatsForPackages(
                mGenericPackageNameByUid, /* killablePackages= */ new ArraySet<>(),
                /* shouldNotifyPackages= */ new ArraySet<>(
                        Collections.singletonList(CANONICAL_NAME)));

        verifyNoMoreInteractions(firstMockListener);
        verifyNoMoreInteractions(secondMockListener);
    }

    @Test
    public void testAddResourceOveruseListenerForSystemThrowsWithInvalidFlag() throws Exception {
        IResourceOveruseListener mockListener = createMockResourceOveruseListener();

        assertThrows(IllegalArgumentException.class, () -> {
            mWatchdogPerfHandler.addResourceOveruseListenerForSystem(/* resourceOveruseFlag= */ 0,
                    mockListener);
        });
    }

    @Test
    public void testResourceOveruseListenerForSystem() throws Exception {
        int callingUid = Binder.getCallingUid();
        mGenericPackageNameByUid.put(callingUid, "system_package.critical");

        IResourceOveruseListener mockListener = createMockResourceOveruseListener();
        mWatchdogPerfHandler.addResourceOveruseListenerForSystem(FLAG_RESOURCE_OVERUSE_IO,
                mockListener);

        IBinder mockBinder = mockListener.asBinder();
        verify(mockBinder).linkToDeath(any(IBinder.DeathRecipient.class), anyInt());

        List<PackageIoOveruseStats> packageIoOveruseStats = Collections.singletonList(
                constructPackageIoOveruseStats(callingUid, /* shouldNotify= */ true,
                        /* forgivenWriteBytes= */ constructPerStateBytes(/* fgBytes= */ 80,
                                /* bgBytes= */ 170, /* gmBytes= */ 260),
                        constructInternalIoOveruseStats(/* killableOnOveruse= */ true,
                                /* remainingWriteBytes= */ constructPerStateBytes(/* fgBytes= */ 20,
                                        /* bgBytes= */ 20, /* gmBytes= */ 20),
                                /* writtenBytes= */ constructPerStateBytes(/* fgBytes= */ 100,
                                        /* bgBytes= */ 200, /* gmBytes= */ 300),
                                /* totalOveruses= */ 3)));

        pushLatestIoOveruseStatsAndWait(packageIoOveruseStats);

        verify(mockListener).onOveruse(any());

        mWatchdogPerfHandler.removeResourceOveruseListenerForSystem(mockListener);

        verify(mockListener, atLeastOnce()).asBinder();
        verify(mockBinder).unlinkToDeath(any(IBinder.DeathRecipient.class), anyInt());

        pushLatestIoOveruseStatsAndWait(packageIoOveruseStats);

        verifyNoMoreInteractions(mockListener);
    }

    @Test
    public void testSetResourceOveruseConfigurations() throws Exception {
        assertThat(mWatchdogPerfHandler.setResourceOveruseConfigurations(
                sampleResourceOveruseConfigurations(), FLAG_RESOURCE_OVERUSE_IO))
                .isEqualTo(RETURN_CODE_SUCCESS);

        InternalResourceOveruseConfigurationSubject
                .assertThat(captureOnSetResourceOveruseConfigurations(/* wantedInvocations= */ 1))
                .containsExactlyElementsIn(sampleInternalResourceOveruseConfigurations());

        // CarService fetches and syncs resource overuse configuration on the main thread by posting
        // a new message. Wait until this completes.
        CarServiceUtils.runOnMainSync(() -> {});

        /* Expect two calls, the first is made at car watchdog service init */
        verify(mMockCarWatchdogDaemonHelper, times(2)).getResourceOveruseConfigurations();
    }

    @Test
    public void testSetResourceOveruseConfigurationsRetriedWithRepeatedRemoteException()
            throws Exception {
        doThrow(RemoteException.class)
                .when(mMockCarWatchdogDaemonHelper).updateResourceOveruseConfigurations(anyList());

        assertThat(mWatchdogPerfHandler.setResourceOveruseConfigurations(
                sampleResourceOveruseConfigurations(), FLAG_RESOURCE_OVERUSE_IO))
                .isEqualTo(CarWatchdogManager.RETURN_CODE_SUCCESS);

        mWatchdogPerfHandler.onDaemonConnectionChange(/* isConnected= */ true);

        doNothing().when(mMockCarWatchdogDaemonHelper).updateResourceOveruseConfigurations(
                anyList());

        /* The below final restart should set the resource overuse configurations successfully. */
        mWatchdogPerfHandler.onDaemonConnectionChange(/* isConnected= */ true);

        InternalResourceOveruseConfigurationSubject
                .assertThat(captureOnSetResourceOveruseConfigurations(/* wantedInvocations= */ 3))
                .containsExactlyElementsIn(sampleInternalResourceOveruseConfigurations());

        verify(mMockCarWatchdogDaemonHelper, times(3)).updateResourceOveruseConfigurations(
                anyList());
    }

    @Test
    public void testSetResourceOveruseConfigurationsRetriedWithDisconnectedDaemon()
            throws Exception {
        mWatchdogPerfHandler.onDaemonConnectionChange(/* isConnected= */ false);

        assertThat(mWatchdogPerfHandler.setResourceOveruseConfigurations(
                sampleResourceOveruseConfigurations(), FLAG_RESOURCE_OVERUSE_IO))
                .isEqualTo(RETURN_CODE_SUCCESS);

        mWatchdogPerfHandler.onDaemonConnectionChange(/* isConnected= */ true);

        InternalResourceOveruseConfigurationSubject
                .assertThat(captureOnSetResourceOveruseConfigurations(/* wantedInvocations= */ 1))
                .containsExactlyElementsIn(sampleInternalResourceOveruseConfigurations());
    }

    @Test
    public void testFailsSetResourceOveruseConfigurationsWithPendingRequest()
            throws Exception {
        mWatchdogPerfHandler.onDaemonConnectionChange(/* isConnected= */ false);

        assertThat(mWatchdogPerfHandler.setResourceOveruseConfigurations(
                sampleResourceOveruseConfigurations(), FLAG_RESOURCE_OVERUSE_IO))
                .isEqualTo(RETURN_CODE_SUCCESS);

        assertThrows(IllegalStateException.class,
                () -> mWatchdogPerfHandler.setResourceOveruseConfigurations(
                        sampleResourceOveruseConfigurations(), FLAG_RESOURCE_OVERUSE_IO));
    }

    @Test
    public void testFailsSetResourceOveruseConfigurationsOnInvalidArgs() throws Exception {
        assertThrows(NullPointerException.class,
                () -> mWatchdogPerfHandler.setResourceOveruseConfigurations(
                        /* configurations= */ null, FLAG_RESOURCE_OVERUSE_IO));

        assertThrows(IllegalArgumentException.class,
                () -> mWatchdogPerfHandler.setResourceOveruseConfigurations(
                        /* configurations= */ new ArrayList<>(), FLAG_RESOURCE_OVERUSE_IO));

        List<ResourceOveruseConfiguration> resourceOveruseConfigs = Collections.singletonList(
                sampleResourceOveruseConfigurationBuilder(ComponentType.SYSTEM,
                        sampleIoOveruseConfigurationBuilder(ComponentType.SYSTEM).build())
                        .build());

        assertThrows(IllegalArgumentException.class,
                () -> mWatchdogPerfHandler.setResourceOveruseConfigurations(resourceOveruseConfigs,
                        /* resourceOveruseFlag= */ 0));
    }

    @Test
    public void testFailsSetResourceOveruseConfigurationsOnDuplicateComponents() throws Exception {
        ResourceOveruseConfiguration config =
                sampleResourceOveruseConfigurationBuilder(ComponentType.SYSTEM,
                        sampleIoOveruseConfigurationBuilder(ComponentType.SYSTEM).build()).build();
        List<ResourceOveruseConfiguration> resourceOveruseConfigs = Arrays.asList(config, config);

        assertThrows(IllegalArgumentException.class,
                () -> mWatchdogPerfHandler.setResourceOveruseConfigurations(resourceOveruseConfigs,
                        FLAG_RESOURCE_OVERUSE_IO));
    }

    @Test
    public void testFailsSetResourceOveruseConfigurationsOnZeroComponentLevelIoOveruseThresholds()
            throws Exception {
        List<ResourceOveruseConfiguration> resourceOveruseConfigs =
                Collections.singletonList(
                        sampleResourceOveruseConfigurationBuilder(ComponentType.SYSTEM,
                                sampleIoOveruseConfigurationBuilder(ComponentType.SYSTEM)
                                        .setComponentLevelThresholds(
                                                new PerStateBytes(
                                                        /* foregroundModeBytes= */ 200,
                                                        /* backgroundModeBytes= */ 0,
                                                        /* garageModeBytes= */ 200))
                                        .build())
                                .build());

        assertThrows(IllegalArgumentException.class,
                () -> mWatchdogPerfHandler.setResourceOveruseConfigurations(resourceOveruseConfigs,
                        FLAG_RESOURCE_OVERUSE_IO));
    }

    @Test
    public void testFailsSetResourceOveruseConfigurationsOnNullIoOveruseConfiguration()
            throws Exception {
        List<ResourceOveruseConfiguration> resourceOveruseConfigs = Collections.singletonList(
                sampleResourceOveruseConfigurationBuilder(
                        ComponentType.SYSTEM, /* ioOveruseConfig= */ null).build());

        assertThrows(IllegalArgumentException.class,
                () -> mWatchdogPerfHandler.setResourceOveruseConfigurations(resourceOveruseConfigs,
                        FLAG_RESOURCE_OVERUSE_IO));
    }

    @Test
    public void testFailsSetResourceOveruseConfigurationsOnEmptyIoOveruseSystemWideThresholds()
            throws Exception {
        List<ResourceOveruseConfiguration> resourceOveruseConfigs =
                Collections.singletonList(
                        sampleResourceOveruseConfigurationBuilder(ComponentType.SYSTEM,
                                sampleIoOveruseConfigurationBuilder(ComponentType.SYSTEM)
                                        .setSystemWideThresholds(new ArrayList<>())
                                        .build())
                                .build());

        assertThrows(IllegalArgumentException.class,
                () -> mWatchdogPerfHandler.setResourceOveruseConfigurations(resourceOveruseConfigs,
                        FLAG_RESOURCE_OVERUSE_IO));
    }

    @Test
    public void testFailsSetResourceOveruseConfigurationsOnIoOveruseInvalidSystemWideThreshold()
            throws Exception {
        List<ResourceOveruseConfiguration> resourceOveruseConfigs = new ArrayList<>();
        resourceOveruseConfigs.add(sampleResourceOveruseConfigurationBuilder(ComponentType.SYSTEM,
                sampleIoOveruseConfigurationBuilder(ComponentType.SYSTEM)
                        .setSystemWideThresholds(Collections.singletonList(
                                new IoOveruseAlertThreshold(
                                        /* durationInSeconds= */ 30,
                                        /* writtenBytesPerSecond= */ 0)))
                        .build())
                .build());

        assertThrows(IllegalArgumentException.class,
                () -> mWatchdogPerfHandler.setResourceOveruseConfigurations(
                        resourceOveruseConfigs, FLAG_RESOURCE_OVERUSE_IO));

        resourceOveruseConfigs.set(0,
                sampleResourceOveruseConfigurationBuilder(ComponentType.SYSTEM,
                        sampleIoOveruseConfigurationBuilder(ComponentType.SYSTEM)
                                .setSystemWideThresholds(Collections.singletonList(
                                        new IoOveruseAlertThreshold(
                                                /* durationInSeconds= */ 0,
                                                /* writtenBytesPerSecond= */ 300)))
                                .build())
                        .build());

        assertThrows(IllegalArgumentException.class,
                () -> mWatchdogPerfHandler.setResourceOveruseConfigurations(
                        resourceOveruseConfigs, FLAG_RESOURCE_OVERUSE_IO));
    }

    @Test
    public void testGetResourceOveruseConfigurations() throws Exception {
        List<ResourceOveruseConfiguration> actualConfigs =
                mWatchdogPerfHandler.getResourceOveruseConfigurations(FLAG_RESOURCE_OVERUSE_IO);

        ResourceOveruseConfigurationSubject.assertThat(actualConfigs)
                .containsExactlyElementsIn(sampleResourceOveruseConfigurations());
    }

    @Test
    public void testGetResourceOveruseConfigurationsWithDisconnectedDaemon() throws Exception {
        mWatchdogPerfHandler.onDaemonConnectionChange(/* isConnected= */ false);

        assertThrows(IllegalStateException.class,
                () -> mWatchdogPerfHandler.getResourceOveruseConfigurations(
                        FLAG_RESOURCE_OVERUSE_IO));

        /* Method initially called in WatchdogPerfHandler init */
        verify(mMockCarWatchdogDaemonHelper).getResourceOveruseConfigurations();
    }

    @Test
    public void testGetResourceOveruseConfigurationsWithReconnectedDaemon() throws Exception {
        crashAndDelayReconnectDaemon();

        List<ResourceOveruseConfiguration> actualConfigs =
                mWatchdogPerfHandler.getResourceOveruseConfigurations(FLAG_RESOURCE_OVERUSE_IO);

        ResourceOveruseConfigurationSubject.assertThat(actualConfigs)
                .containsExactlyElementsIn(sampleResourceOveruseConfigurations());
    }

    @Test
    public void testConcurrentSetGetResourceOveruseConfigurationsWithReconnectedDaemon()
            throws Exception {
        crashAndDelayReconnectDaemon();

        /* Capture and respond with the configuration received in the set request. */
        List<android.automotive.watchdog.internal.ResourceOveruseConfiguration> internalConfigs =
                new ArrayList<>();
        doAnswer(args -> {
            List<android.automotive.watchdog.internal.ResourceOveruseConfiguration> configs =
                    args.getArgument(0);
            internalConfigs.addAll(configs);
            return null;
        }).when(mMockCarWatchdogDaemonHelper).updateResourceOveruseConfigurations(anyList());
        when(mMockCarWatchdogDaemonHelper.getResourceOveruseConfigurations()).thenReturn(
                internalConfigs);

        /* Start a set request that will become pending and a blocking get request. */
        List<ResourceOveruseConfiguration> setConfigs = sampleResourceOveruseConfigurations();
        assertThat(mWatchdogPerfHandler.setResourceOveruseConfigurations(
                setConfigs, FLAG_RESOURCE_OVERUSE_IO))
                .isEqualTo(CarWatchdogManager.RETURN_CODE_SUCCESS);

        List<ResourceOveruseConfiguration> getConfigs =
                mWatchdogPerfHandler.getResourceOveruseConfigurations(FLAG_RESOURCE_OVERUSE_IO);

        ResourceOveruseConfigurationSubject.assertThat(getConfigs)
                .containsExactlyElementsIn(setConfigs);
    }

    @Test
    public void testFailsGetResourceOveruseConfigurationsOnInvalidArgs() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> mWatchdogPerfHandler.getResourceOveruseConfigurations(0));
    }

    private void crashAndDelayReconnectDaemon() {
        mWatchdogPerfHandler.onDaemonConnectionChange(/* isConnected= */ false);

        mMainHandler.postAtTime(
                () -> mWatchdogPerfHandler.onDaemonConnectionChange(/* isConnected= */ true),
                MAX_WAIT_TIME_MILLS - 1000);
    }

    @Test
    public void testAsyncFetchTodayIoUsageStats() throws Exception {
        List<WatchdogStorage.IoUsageStatsEntry> ioUsageStatsEntries = Arrays.asList(
                WatchdogStorageUnitTest.constructIoUsageStatsEntry(
                        /* userId= */ 100, "system_package", /* startTime */ 0,
                        /* duration= */ 1234,
                        /* remainingWriteBytes= */ constructPerStateBytes(200, 300, 400),
                        /* writtenBytes= */ constructPerStateBytes(1000, 2000, 3000),
                        /* forgivenWriteBytes= */ constructPerStateBytes(100, 100, 100),
                        /* totalOveruses= */ 2, /* forgivenOveruses= */ 0,
                        /* totalTimesKilled= */ 1),
                WatchdogStorageUnitTest.constructIoUsageStatsEntry(
                        /* userId= */ 101, "vendor_package", /* startTime */ 0,
                        /* duration= */ 1234,
                        /* remainingWriteBytes= */ constructPerStateBytes(500, 600, 700),
                        /* writtenBytes= */ constructPerStateBytes(1100, 2300, 4300),
                        /* forgivenWriteBytes= */ constructPerStateBytes(100, 100, 100),
                        /* totalOveruses= */ 4, /* forgivenOveruses= */ 1,
                        /* totalTimesKilled= */ 10));
        when(mSpiedWatchdogStorage.getTodayIoUsageStats()).thenReturn(ioUsageStatsEntries);

        mWatchdogPerfHandler.asyncFetchTodayIoUsageStats();

        ArgumentCaptor<List<UserPackageIoUsageStats>> userPackageIoUsageStatsCaptor =
                ArgumentCaptor.forClass(List.class);

        verify(mMockCarWatchdogDaemonHelper, timeout(MAX_WAIT_TIME_MILLS))
                .onTodayIoUsageStatsFetched(userPackageIoUsageStatsCaptor.capture());

        List<UserPackageIoUsageStats> expectedStats = Arrays.asList(
                constructUserPackageIoUsageStats(/* userId= */ 100, "system_package",
                        /* writtenBytes= */ constructPerStateBytes(1000, 2000, 3000),
                        /* forgivenWriteBytes= */ constructPerStateBytes(100, 100, 100),
                        /* totalOveruses= */ 2),
                constructUserPackageIoUsageStats(/* userId= */ 101, "vendor_package",
                        /* writtenBytes= */ constructPerStateBytes(1100, 2300, 4300),
                        /* forgivenWriteBytes= */ constructPerStateBytes(100, 100, 100),
                        /* totalOveruses= */ 4));

        assertThat(userPackageIoUsageStatsCaptor.getValue())
                .comparingElementsUsing(Correspondence.from(
                        CarWatchdogServiceUnitTest::isUserPackageIoUsageStatsEquals,
                        "is user package I/O usage stats equal to"))
                .containsExactlyElementsIn(expectedStats);
    }

    @Test
    public void testAsyncFetchTodayIoUsageStatsWithDaemonRemoteException() throws Exception {
        List<WatchdogStorage.IoUsageStatsEntry> ioUsageStatsEntries = Arrays.asList(
                WatchdogStorageUnitTest.constructIoUsageStatsEntry(
                        /* userId= */ 100, "system_package", /* startTime */ 0,
                        /* duration= */ 1234,
                        /* remainingWriteBytes= */ constructPerStateBytes(200, 300, 400),
                        /* writtenBytes= */ constructPerStateBytes(1000, 2000, 3000),
                        /* forgivenWriteBytes= */ constructPerStateBytes(100, 100, 100),
                        /* totalOveruses= */ 2, /* forgivenOveruses= */ 0,
                        /* totalTimesKilled= */ 1),
                WatchdogStorageUnitTest.constructIoUsageStatsEntry(
                        /* userId= */ 101, "vendor_package", /* startTime */ 0,
                        /* duration= */ 1234,
                        /* remainingWriteBytes= */ constructPerStateBytes(500, 600, 700),
                        /* writtenBytes= */ constructPerStateBytes(1100, 2300, 4300),
                        /* forgivenWriteBytes= */ constructPerStateBytes(100, 100, 100),
                        /* totalOveruses= */ 4, /* forgivenOveruses= */ 1,
                        /* totalTimesKilled= */ 10));
        when(mSpiedWatchdogStorage.getTodayIoUsageStats()).thenReturn(ioUsageStatsEntries);
        doThrow(RemoteException.class)
                .when(mMockCarWatchdogDaemonHelper).onTodayIoUsageStatsFetched(any());

        mWatchdogPerfHandler.asyncFetchTodayIoUsageStats();

        verify(mMockCarWatchdogDaemonHelper,
                timeout(MAX_WAIT_TIME_MILLS)).onTodayIoUsageStatsFetched(any());
    }

    @Test
    public void testGetTodayIoUsageStats() throws Exception {
        List<WatchdogStorage.IoUsageStatsEntry> ioUsageStatsEntries = Arrays.asList(
                WatchdogStorageUnitTest.constructIoUsageStatsEntry(
                        /* userId= */ 100, "system_package", /* startTime */ 0,
                        /* duration= */ 1234,
                        /* remainingWriteBytes= */ constructPerStateBytes(200, 300, 400),
                        /* writtenBytes= */ constructPerStateBytes(1000, 2000, 3000),
                        /* forgivenWriteBytes= */ constructPerStateBytes(100, 100, 100),
                        /* totalOveruses= */ 2, /* forgivenOveruses= */ 0,
                        /* totalTimesKilled= */ 1),
                WatchdogStorageUnitTest.constructIoUsageStatsEntry(
                        /* userId= */ 101, "vendor_package", /* startTime */ 0,
                        /* duration= */ 1234,
                        /* remainingWriteBytes= */ constructPerStateBytes(500, 600, 700),
                        /* writtenBytes= */ constructPerStateBytes(1100, 2300, 4300),
                        /* forgivenWriteBytes= */ constructPerStateBytes(100, 100, 100),
                        /* totalOveruses= */ 4, /* forgivenOveruses= */ 1,
                        /* totalTimesKilled= */ 10));
        when(mSpiedWatchdogStorage.getTodayIoUsageStats()).thenReturn(ioUsageStatsEntries);

        List<UserPackageIoUsageStats> actualStats =
                mWatchdogPerfHandler.getTodayIoUsageStats();

        List<UserPackageIoUsageStats> expectedStats = Arrays.asList(
                constructUserPackageIoUsageStats(/* userId= */ 100, "system_package",
                        /* writtenBytes= */ constructPerStateBytes(1000, 2000, 3000),
                        /* forgivenWriteBytes= */ constructPerStateBytes(100, 100, 100),
                        /* totalOveruses= */ 2),
                constructUserPackageIoUsageStats(/* userId= */ 101, "vendor_package",
                        /* writtenBytes= */ constructPerStateBytes(1100, 2300, 4300),
                        /* forgivenWriteBytes= */ constructPerStateBytes(100, 100, 100),
                        /* totalOveruses= */ 4));

        assertThat(actualStats).comparingElementsUsing(Correspondence.from(
                        CarWatchdogServiceUnitTest::isUserPackageIoUsageStatsEquals,
                        "is user package I/O usage stats equal to"))
                .containsExactlyElementsIn(expectedStats);
    }

    @Test
    public void testDisablePackageForUser() throws Exception {
        assertWithMessage("Performed resource overuse kill")
                .that(mWatchdogPerfHandler.disablePackageForUser("third_party_package",
                        /* userId= */ 100)).isTrue();

        verifyDisabledPackages(/* userPackagesCsv= */ "100:third_party_package");
    }

    @Test
    public void testDisablePackageForUserWithDisabledPackage() throws Exception {
        doReturn(COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED).when(() -> PackageManagerHelper
                .getApplicationEnabledSettingForUser(anyString(), anyInt()));

        assertWithMessage("Performed resource overuse kill")
                .that(mWatchdogPerfHandler.disablePackageForUser("third_party_package",
                        /* userId= */ 100)).isFalse();

        verifyNoDisabledPackages();
    }

    @Test
    public void testDisablePackageForUserWithNonexistentPackage() throws Exception {
        doThrow(IllegalArgumentException.class).when(mSpiedPackageManager)
                .getApplicationEnabledSetting(anyString(), anyInt());

        assertWithMessage("Performed resource overuse kill")
                .that(mWatchdogPerfHandler.disablePackageForUser("fake_package",
                        /* userId= */ 100)).isFalse();

        verifyNoDisabledPackages();
    }

    @Test
    public void testResetResourceOveruseStatsResetsStats() throws Exception {
        UserHandle user = UserHandle.getUserHandleForUid(10003346);
        String packageName = mMockContext.getPackageName();
        mGenericPackageNameByUid.put(10003346, packageName);
        mGenericPackageNameByUid.put(10101278, "vendor_package.critical");
        injectIoOveruseStatsForPackages(
                mGenericPackageNameByUid, /* killablePackages= */ new ArraySet<>(),
                /* shouldNotifyPackages= */ new ArraySet<>());

        mWatchdogPerfHandler.resetResourceOveruseStats(Collections.singleton(packageName));

        // Resetting resource overuse stats is done on the CarWatchdogService service handler
        // thread. Wait until the below message is processed before returning, so the resource
        // overuse stats resetting is completed.
        CarServiceUtils.runEmptyRunnableOnLooperSync(CAR_WATCHDOG_SERVICE_NAME);

        ResourceOveruseStats actualStats =
                mWatchdogPerfHandler.getResourceOveruseStatsForUserPackage(
                        packageName, user,
                        CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO,
                        CarWatchdogManager.STATS_PERIOD_CURRENT_DAY);

        ResourceOveruseStats expectedStats = new ResourceOveruseStats.Builder(
                packageName, user).build();

        ResourceOveruseStatsSubject.assertEquals(actualStats, expectedStats);

        verify(mSpiedWatchdogStorage).deleteUserPackage(eq(user.getIdentifier()), eq(packageName));
    }

    @Test
    public void testResetResourceOveruseStatsEnablesPackage() throws Exception {
        injectPackageInfos(Arrays.asList(
                constructPackageManagerPackageInfo("third_party_package.A", 10012345,
                        /* sharedUserId= */ null),
                constructPackageManagerPackageInfo("vendor_package.critical.A", 10014567,
                        "vendor_shared_package.A"),
                constructPackageManagerPackageInfo("vendor_package.critical.B", 10014567,
                        "vendor_shared_package.A"),
                constructPackageManagerPackageInfo("system_package.critical.A", 10001278,
                        "system_shared_package.A"),
                constructPackageManagerPackageInfo("third_party_package.B", 10056790,
                        /* sharedUserId= */ null),
                constructPackageManagerPackageInfo("system_package.non_critical.B", 10007345,
                        "system_shared_package.B")));

        injectIoOveruseStatsForPackages(
                mGenericPackageNameByUid, /* killablePackages= */ new ArraySet<>(),
                /* shouldNotifyPackages= */ new ArraySet<>());

        disableUserPackage("third_party_package.A", 100);
        disableUserPackage("vendor_package.critical.A", 100);
        disableUserPackage("vendor_package.critical.B", 100);

        mWatchdogPerfHandler.resetResourceOveruseStats(new ArraySet<>(
                Arrays.asList("third_party_package.A", "shared:vendor_shared_package.A",
                        "shared:system_shared_package.A", "third_party_package.B")));

        // Resetting resource overuse stats is done on the CarWatchdogService service handler
        // thread. Wait until the below message is processed before returning, so the resource
        // overuse stats resetting is completed.
        CarServiceUtils.runEmptyRunnableOnLooperSync(CAR_WATCHDOG_SERVICE_NAME);

        verify(mSpiedPackageManager, times(2))
                .getApplicationEnabledSetting("third_party_package.A", 100);
        verify(mSpiedPackageManager, times(2))
                .getApplicationEnabledSetting("vendor_package.critical.A", 100);
        verify(mSpiedPackageManager, times(2))
                .getApplicationEnabledSetting("vendor_package.critical.B", 100);
        verify(mSpiedPackageManager, never())
                .getApplicationEnabledSetting("system_package.critical.A", 100);
        verify(mSpiedPackageManager, never())
                .getApplicationEnabledSetting("third_party_package.B", 100);

        verify(mSpiedPackageManager).setApplicationEnabledSetting(eq("third_party_package.A"),
                eq(COMPONENT_ENABLED_STATE_ENABLED), anyInt(), eq(100), anyString());
        verify(mSpiedPackageManager).setApplicationEnabledSetting(eq("vendor_package.critical.A"),
                eq(COMPONENT_ENABLED_STATE_ENABLED), anyInt(), eq(100), anyString());
        verify(mSpiedPackageManager).setApplicationEnabledSetting(eq("vendor_package.critical.B"),
                eq(COMPONENT_ENABLED_STATE_ENABLED), anyInt(), eq(100), anyString());
    }

    @Test
    public void testResetResourceOveruseStatsResetsUserPackageSettings() throws Exception {
        mockUmGetUserHandles(mMockUserManager, /* excludeDying= */ true, 100, 101);
        injectPackageInfos(Arrays.asList(
                constructPackageManagerPackageInfo("third_party_package.A", 10001278, null),
                constructPackageManagerPackageInfo("third_party_package.A", 10101278, null),
                constructPackageManagerPackageInfo("third_party_package.B", 10003346, null),
                constructPackageManagerPackageInfo("third_party_package.B", 10103346, null)));
        injectIoOveruseStatsForPackages(mGenericPackageNameByUid,
                /* killablePackages= */ Set.of("third_party_package.A", "third_party_package.B"),
                /* shouldNotifyPackages= */ new ArraySet<>());

        mWatchdogPerfHandler.setKillablePackageAsUser("third_party_package.A",
                UserHandle.ALL, /* isKillable= */ false);
        mWatchdogPerfHandler.setKillablePackageAsUser("third_party_package.B",
                UserHandle.ALL, /* isKillable= */ false);

        mWatchdogPerfHandler.resetResourceOveruseStats(
                Collections.singleton("third_party_package.A"));

        // Resetting resource overuse stats is done on the CarWatchdogService service handler
        // thread. Wait until the below message is processed before returning, so the resource
        // overuse stats resetting is completed.
        CarServiceUtils.runEmptyRunnableOnLooperSync(CAR_WATCHDOG_SERVICE_NAME);

        PackageKillableStateSubject.assertThat(
                mWatchdogPerfHandler.getPackageKillableStatesAsUser(
                        UserHandle.ALL)).containsExactly(
                new PackageKillableState("third_party_package.A", 100,
                        PackageKillableState.KILLABLE_STATE_YES),
                new PackageKillableState("third_party_package.A", 101,
                        PackageKillableState.KILLABLE_STATE_YES),
                new PackageKillableState("third_party_package.B", 100,
                        PackageKillableState.KILLABLE_STATE_NO),
                new PackageKillableState("third_party_package.B", 101,
                        PackageKillableState.KILLABLE_STATE_NO)
        );

        verify(mSpiedWatchdogStorage, times(2)).deleteUserPackage(anyInt(),
                eq("third_party_package.A"));
    }

    @Test
    public void testSetKillablePackageAsUser() throws Exception {
        mockUmGetUserHandles(mMockUserManager, /* excludeDying= */ true, 101, 102);
        injectPackageInfos(Arrays.asList(
                constructPackageManagerPackageInfo("third_party_package", 10103456, null),
                constructPackageManagerPackageInfo("vendor_package.critical", 10101278, null),
                constructPackageManagerPackageInfo("third_party_package", 10203456, null),
                constructPackageManagerPackageInfo("vendor_package.critical", 10201278, null)));

        UserHandle userHandle = UserHandle.of(101);
        mWatchdogPerfHandler.setKillablePackageAsUser("third_party_package", userHandle,
                /* isKillable= */ false);
        mWatchdogPerfHandler.setKillablePackageAsUser("vendor_package.critical",
                userHandle, /* isKillable= */ false);

        PackageKillableStateSubject.assertThat(
                mWatchdogPerfHandler.getPackageKillableStatesAsUser(
                        UserHandle.ALL)).containsExactly(
                new PackageKillableState("third_party_package", 101,
                        PackageKillableState.KILLABLE_STATE_NO),
                new PackageKillableState("vendor_package.critical", 101,
                        PackageKillableState.KILLABLE_STATE_NEVER),
                new PackageKillableState("third_party_package", 102,
                        PackageKillableState.KILLABLE_STATE_YES),
                new PackageKillableState("vendor_package.critical", 102,
                        PackageKillableState.KILLABLE_STATE_NEVER));

        assertThrows(IllegalArgumentException.class,
                () -> mWatchdogPerfHandler.setKillablePackageAsUser("vendor_package.critical",
                        userHandle, /* isKillable= */ true));

        mockUmGetUserHandles(mMockUserManager, /* excludeDying= */ true, 101, 102, 103);
        injectPackageInfos(Collections.singletonList(
                constructPackageManagerPackageInfo("third_party_package", 10303456, null)));

        PackageKillableStateSubject.assertThat(
                mWatchdogPerfHandler.getPackageKillableStatesAsUser(
                        UserHandle.ALL)).containsExactly(
                new PackageKillableState("third_party_package", 101,
                        PackageKillableState.KILLABLE_STATE_NO),
                new PackageKillableState("vendor_package.critical", 101,
                        PackageKillableState.KILLABLE_STATE_NEVER),
                new PackageKillableState("third_party_package", 102,
                        PackageKillableState.KILLABLE_STATE_YES),
                new PackageKillableState("vendor_package.critical", 102,
                        PackageKillableState.KILLABLE_STATE_NEVER),
                new PackageKillableState("third_party_package", 103,
                        PackageKillableState.KILLABLE_STATE_YES));

        verify(mSpiedWatchdogStorage, times(11)).markDirty();
    }

    @Test
    public void testSetKillablePackageAsUserWithSharedUids() throws Exception {
        mockUmGetUserHandles(mMockUserManager, /* excludeDying= */ true, 101, 102);
        injectPackageInfos(Arrays.asList(
                constructPackageManagerPackageInfo(
                        "third_party_package.A", 10103456, "third_party_shared_package.A"),
                constructPackageManagerPackageInfo(
                        "third_party_package.B", 10103456, "third_party_shared_package.A"),
                constructPackageManagerPackageInfo(
                        "third_party_package.C", 10101356, "third_party_shared_package.B"),
                constructPackageManagerPackageInfo(
                        "third_party_package.D", 10101356, "third_party_shared_package.B")));

        UserHandle userHandle = UserHandle.of(101);
        mWatchdogPerfHandler.setKillablePackageAsUser("third_party_package.A", userHandle,
                /* isKillable= */ false);

        PackageKillableStateSubject.assertThat(
                mWatchdogPerfHandler.getPackageKillableStatesAsUser(
                        UserHandle.ALL)).containsExactly(
                new PackageKillableState("third_party_package.A", 101,
                        PackageKillableState.KILLABLE_STATE_NO),
                new PackageKillableState("third_party_package.B", 101,
                        PackageKillableState.KILLABLE_STATE_NO),
                new PackageKillableState("third_party_package.C", 101,
                        PackageKillableState.KILLABLE_STATE_YES),
                new PackageKillableState("third_party_package.D", 101,
                        PackageKillableState.KILLABLE_STATE_YES));

        mWatchdogPerfHandler.setKillablePackageAsUser("third_party_package.B", userHandle,
                /* isKillable= */ true);
        mWatchdogPerfHandler.setKillablePackageAsUser("third_party_package.C", userHandle,
                /* isKillable= */ false);

        PackageKillableStateSubject.assertThat(
                mWatchdogPerfHandler.getPackageKillableStatesAsUser(
                        UserHandle.ALL)).containsExactly(
                new PackageKillableState("third_party_package.A", 101,
                        PackageKillableState.KILLABLE_STATE_YES),
                new PackageKillableState("third_party_package.B", 101,
                        PackageKillableState.KILLABLE_STATE_YES),
                new PackageKillableState("third_party_package.C", 101,
                        PackageKillableState.KILLABLE_STATE_NO),
                new PackageKillableState("third_party_package.D", 101,
                        PackageKillableState.KILLABLE_STATE_NO));

        verify(mSpiedWatchdogStorage, times(7)).markDirty();
    }

    @Test
    public void testSetKillablePackageAsUserForAllUsers() throws Exception {
        mockUmGetUserHandles(mMockUserManager, /* excludeDying= */ true, 101, 102);
        injectPackageInfos(Arrays.asList(
                constructPackageManagerPackageInfo("third_party_package", 10103456, null),
                constructPackageManagerPackageInfo("vendor_package.critical", 10101278, null),
                constructPackageManagerPackageInfo("third_party_package", 10203456, null),
                constructPackageManagerPackageInfo("vendor_package.critical", 10201278, null)));

        mWatchdogPerfHandler.setKillablePackageAsUser("third_party_package", UserHandle.ALL,
                /* isKillable= */ false);
        mWatchdogPerfHandler.setKillablePackageAsUser("vendor_package.critical",
                UserHandle.ALL, /* isKillable= */ false);

        PackageKillableStateSubject.assertThat(
                mWatchdogPerfHandler.getPackageKillableStatesAsUser(
                        UserHandle.ALL)).containsExactly(
                new PackageKillableState("third_party_package", 101,
                        PackageKillableState.KILLABLE_STATE_NO),
                new PackageKillableState("vendor_package.critical", 101,
                        PackageKillableState.KILLABLE_STATE_NEVER),
                new PackageKillableState("third_party_package", 102,
                        PackageKillableState.KILLABLE_STATE_NO),
                new PackageKillableState("vendor_package.critical", 102,
                        PackageKillableState.KILLABLE_STATE_NEVER));

        assertThrows(IllegalArgumentException.class,
                () -> mWatchdogPerfHandler.setKillablePackageAsUser("vendor_package.critical",
                        UserHandle.ALL, /* isKillable= */ true));

        mockUmGetUserHandles(mMockUserManager, /* excludeDying= */ true, 101, 102, 103);
        injectPackageInfos(Collections.singletonList(
                constructPackageManagerPackageInfo("third_party_package", 10303456, null)));

        PackageKillableStateSubject.assertThat(
                mWatchdogPerfHandler.getPackageKillableStatesAsUser(
                        UserHandle.ALL)).containsExactly(
                new PackageKillableState("third_party_package", 101,
                        PackageKillableState.KILLABLE_STATE_NO),
                new PackageKillableState("vendor_package.critical", 101,
                        PackageKillableState.KILLABLE_STATE_NEVER),
                new PackageKillableState("third_party_package", 102,
                        PackageKillableState.KILLABLE_STATE_NO),
                new PackageKillableState("vendor_package.critical", 102,
                        PackageKillableState.KILLABLE_STATE_NEVER),
                new PackageKillableState("third_party_package", 103,
                        PackageKillableState.KILLABLE_STATE_NO));

        verify(mSpiedWatchdogStorage, times(11)).markDirty();
    }

    @Test
    public void testSetKillablePackageAsUsersForAllUsersWithSharedUids() throws Exception {
        mockUmGetUserHandles(mMockUserManager, /* excludeDying= */ true, 101, 102);
        injectPackageInfos(Arrays.asList(
                constructPackageManagerPackageInfo(
                        "third_party_package.A", 10103456, "third_party_shared_package.A"),
                constructPackageManagerPackageInfo(
                        "third_party_package.B", 10103456, "third_party_shared_package.A"),
                constructPackageManagerPackageInfo(
                        "third_party_package.C", 10101356, "third_party_shared_package.B"),
                constructPackageManagerPackageInfo(
                        "third_party_package.D", 10101356, "third_party_shared_package.B"),
                constructPackageManagerPackageInfo(
                        "third_party_package.A", 10203456, "third_party_shared_package.A"),
                constructPackageManagerPackageInfo(
                        "third_party_package.B", 10203456, "third_party_shared_package.A")));

        mWatchdogPerfHandler.setKillablePackageAsUser("third_party_package.A", UserHandle.ALL,
                /* isKillable= */ false);

        PackageKillableStateSubject.assertThat(
                mWatchdogPerfHandler.getPackageKillableStatesAsUser(
                        UserHandle.ALL)).containsExactly(
                new PackageKillableState("third_party_package.A", 101,
                        PackageKillableState.KILLABLE_STATE_NO),
                new PackageKillableState("third_party_package.B", 101,
                        PackageKillableState.KILLABLE_STATE_NO),
                new PackageKillableState("third_party_package.C", 101,
                        PackageKillableState.KILLABLE_STATE_YES),
                new PackageKillableState("third_party_package.D", 101,
                        PackageKillableState.KILLABLE_STATE_YES),
                new PackageKillableState("third_party_package.A", 102,
                        PackageKillableState.KILLABLE_STATE_NO),
                new PackageKillableState("third_party_package.B", 102,
                        PackageKillableState.KILLABLE_STATE_NO));

        mockUmGetUserHandles(mMockUserManager, /* excludeDying= */ true, 101, 102, 103);
        injectPackageInfos(Arrays.asList(
                constructPackageManagerPackageInfo(
                        "third_party_package.A", 10303456, "third_party_shared_package.A"),
                constructPackageManagerPackageInfo(
                        "third_party_package.B", 10303456, "third_party_shared_package.A")));

        PackageKillableStateSubject.assertThat(
                        mWatchdogPerfHandler.getPackageKillableStatesAsUser(UserHandle.of(103)))
                .containsExactly(
                        new PackageKillableState("third_party_package.A", 103,
                                PackageKillableState.KILLABLE_STATE_NO),
                        new PackageKillableState("third_party_package.B", 103,
                                PackageKillableState.KILLABLE_STATE_NO));

        verify(mSpiedWatchdogStorage, times(5)).markDirty();
    }

    @Test
    public void testSetKillablePackageAsUserReenablesPackage() throws Exception {
        mockUmGetUserHandles(mMockUserManager, /* excludeDying= */ true, 101);
        disableUserPackage("third_party_package", 101);

        injectPackageInfos(Collections.singletonList(
                constructPackageManagerPackageInfo("third_party_package", 10103456, null)));

        UserHandle userHandle = UserHandle.of(101);
        mWatchdogPerfHandler.setKillablePackageAsUser("third_party_package", userHandle,
                /* isKillable= */ false);

        PackageKillableStateSubject.assertThat(
                        mWatchdogPerfHandler.getPackageKillableStatesAsUser(userHandle))
                .containsExactly(new PackageKillableState("third_party_package", 101,
                        PackageKillableState.KILLABLE_STATE_NO));

        verify(mSpiedPackageManager, times(2))
                .getApplicationEnabledSetting("third_party_package", 101);

        verify(mSpiedPackageManager).setApplicationEnabledSetting(eq("third_party_package"),
                eq(COMPONENT_ENABLED_STATE_ENABLED), anyInt(), eq(101), anyString());
    }

    @Test
    public void testSetKillablePackageAsUserReenablesPackagesWithSharedUids() throws Exception {
        mockUmGetUserHandles(mMockUserManager, /* excludeDying= */ true, 101);
        disableUserPackage("third_party_package.A", 101);
        disableUserPackage("third_party_package.B", 101);

        injectPackageInfos(Arrays.asList(
                constructPackageManagerPackageInfo(
                        "third_party_package.A", 10103456, "third_party_shared_package.A"),
                constructPackageManagerPackageInfo(
                        "third_party_package.B", 10103456, "third_party_shared_package.A")));

        UserHandle userHandle = UserHandle.of(101);
        mWatchdogPerfHandler.setKillablePackageAsUser("third_party_package.A", userHandle,
                /* isKillable= */ false);

        PackageKillableStateSubject.assertThat(
                mWatchdogPerfHandler.getPackageKillableStatesAsUser(userHandle)).containsExactly(
                new PackageKillableState("third_party_package.A", 101,
                        PackageKillableState.KILLABLE_STATE_NO),
                new PackageKillableState("third_party_package.B", 101,
                        PackageKillableState.KILLABLE_STATE_NO));

        verify(mSpiedPackageManager, times(2))
                .getApplicationEnabledSetting("third_party_package.A", 101);
        verify(mSpiedPackageManager, times(2))
                .getApplicationEnabledSetting("third_party_package.B", 101);

        verify(mSpiedPackageManager).setApplicationEnabledSetting(eq("third_party_package.A"),
                eq(COMPONENT_ENABLED_STATE_ENABLED), anyInt(), eq(101), anyString());
        verify(mSpiedPackageManager).setApplicationEnabledSetting(eq("third_party_package.B"),
                eq(COMPONENT_ENABLED_STATE_ENABLED), anyInt(), eq(101), anyString());
    }

    @Test
    public void testSetKillablePackageAsUserForAllUsersReenablesPackages() throws Exception {
        mockUmGetUserHandles(mMockUserManager, /* excludeDying= */ true, 101, 102);
        disableUserPackage("third_party_package", 101, 102);

        injectPackageInfos(Arrays.asList(
                constructPackageManagerPackageInfo("third_party_package", 10103456, null),
                constructPackageManagerPackageInfo("third_party_package", 10203456, null)));

        List<PackageIoOveruseStats> packageIoOveruseStats = Arrays.asList(
                constructPackageIoOveruseStats(10103456, /* shouldNotify= */ false,
                        /* forgivenWriteBytes= */ constructPerStateBytes(80, 170, 260),
                        constructInternalIoOveruseStats(/* killableOnOveruse= */ true,
                                /* remainingWriteBytes= */ constructPerStateBytes(20, 20, 20),
                                /* writtenBytes= */ constructPerStateBytes(100, 200, 300),
                                /* totalOveruses= */ 1)),
                constructPackageIoOveruseStats(10203456, /* shouldNotify= */ false,
                        /* forgivenWriteBytes= */ constructPerStateBytes(80, 170, 260),
                        constructInternalIoOveruseStats(/* killableOnOveruse= */ true,
                                /* remainingWriteBytes= */ constructPerStateBytes(20, 20, 20),
                                /* writtenBytes= */ constructPerStateBytes(100, 200, 300),
                                /* totalOveruses= */ 1)));

        // Push stats in order to create and cache the usages of the third_party_package
        pushLatestIoOveruseStatsAndWait(packageIoOveruseStats);

        mWatchdogPerfHandler.setKillablePackageAsUser("third_party_package", UserHandle.ALL,
                /* isKillable= */ false);

        PackageKillableStateSubject.assertThat(
                mWatchdogPerfHandler.getPackageKillableStatesAsUser(
                        UserHandle.ALL)).containsExactly(
                new PackageKillableState("third_party_package", 101,
                        PackageKillableState.KILLABLE_STATE_NO),
                new PackageKillableState("third_party_package", 102,
                        PackageKillableState.KILLABLE_STATE_NO));

        verify(mSpiedPackageManager, times(2))
                .getApplicationEnabledSetting("third_party_package", 101);
        verify(mSpiedPackageManager, times(2))
                .getApplicationEnabledSetting("third_party_package", 102);

        verify(mSpiedPackageManager).setApplicationEnabledSetting(eq("third_party_package"),
                eq(COMPONENT_ENABLED_STATE_ENABLED), anyInt(), eq(101), anyString());
        verify(mSpiedPackageManager).setApplicationEnabledSetting(eq("third_party_package"),
                eq(COMPONENT_ENABLED_STATE_ENABLED), anyInt(), eq(102), anyString());
    }

    @Test
    public void testSetKillablePackageAsUsersForAllUsersReenablesPackagesWithSharedUids()
            throws Exception {
        mockUmGetUserHandles(mMockUserManager, /* excludeDying= */ true, 101, 102);
        disableUserPackage("third_party_package.A", 101, 102);
        disableUserPackage("third_party_package.B", 101, 102);

        injectPackageInfos(Arrays.asList(
                constructPackageManagerPackageInfo(
                        "third_party_package.A", 10103456, "third_party_shared_package"),
                constructPackageManagerPackageInfo(
                        "third_party_package.B", 10103456, "third_party_shared_package"),
                constructPackageManagerPackageInfo(
                        "third_party_package.A", 10203456, "third_party_shared_package"),
                constructPackageManagerPackageInfo(
                        "third_party_package.B", 10203456, "third_party_shared_package")));

        List<PackageIoOveruseStats> packageIoOveruseStats = Arrays.asList(
                constructPackageIoOveruseStats(10103456, /* shouldNotify= */ false,
                        /* forgivenWriteBytes= */ constructPerStateBytes(80, 170, 260),
                        constructInternalIoOveruseStats(/* killableOnOveruse= */ true,
                                /* remainingWriteBytes= */ constructPerStateBytes(20, 20, 20),
                                /* writtenBytes= */ constructPerStateBytes(100, 200, 300),
                                /* totalOveruses= */ 1)),
                constructPackageIoOveruseStats(10203456, /* shouldNotify= */ false,
                        /* forgivenWriteBytes= */ constructPerStateBytes(80, 170, 260),
                        constructInternalIoOveruseStats(/* killableOnOveruse= */ true,
                                /* remainingWriteBytes= */ constructPerStateBytes(20, 20, 20),
                                /* writtenBytes= */ constructPerStateBytes(100, 200, 300),
                                /* totalOveruses= */ 1)));

        // Push stats in order to create and cache the usages of the third_party_shared_package
        pushLatestIoOveruseStatsAndWait(packageIoOveruseStats);

        mWatchdogPerfHandler.setKillablePackageAsUser("third_party_package.A", UserHandle.ALL,
                /* isKillable= */ false);

        PackageKillableStateSubject.assertThat(
                mWatchdogPerfHandler.getPackageKillableStatesAsUser(
                        UserHandle.ALL)).containsExactly(
                new PackageKillableState("third_party_package.A", 101,
                        PackageKillableState.KILLABLE_STATE_NO),
                new PackageKillableState("third_party_package.B", 101,
                        PackageKillableState.KILLABLE_STATE_NO),
                new PackageKillableState("third_party_package.A", 102,
                        PackageKillableState.KILLABLE_STATE_NO),
                new PackageKillableState("third_party_package.B", 102,
                        PackageKillableState.KILLABLE_STATE_NO));

        verify(mSpiedPackageManager, times(2))
                .getApplicationEnabledSetting("third_party_package.A", 101);
        verify(mSpiedPackageManager, times(2))
                .getApplicationEnabledSetting("third_party_package.B", 101);
        verify(mSpiedPackageManager, times(2))
                .getApplicationEnabledSetting("third_party_package.A", 102);
        verify(mSpiedPackageManager, times(2))
                .getApplicationEnabledSetting("third_party_package.B", 102);

        verify(mSpiedPackageManager).setApplicationEnabledSetting(eq("third_party_package.A"),
                eq(COMPONENT_ENABLED_STATE_ENABLED), anyInt(), eq(101), anyString());
        verify(mSpiedPackageManager).setApplicationEnabledSetting(eq("third_party_package.B"),
                eq(COMPONENT_ENABLED_STATE_ENABLED), anyInt(), eq(101), anyString());
        verify(mSpiedPackageManager).setApplicationEnabledSetting(eq("third_party_package.A"),
                eq(COMPONENT_ENABLED_STATE_ENABLED), anyInt(), eq(102), anyString());
        verify(mSpiedPackageManager).setApplicationEnabledSetting(eq("third_party_package.B"),
                eq(COMPONENT_ENABLED_STATE_ENABLED), anyInt(), eq(102), anyString());
    }

    @Test
    public void testResetPackageKillableStateDuringBootup() throws Exception {
        mockUmGetUserHandles(mMockUserManager, /* excludeDying= */ true, 101);
        injectPackageInfos(Collections.singletonList(
                constructPackageManagerPackageInfo("third_party_package",
                        10103456, /* sharedUserId= */  null)));

        mTimeSource.updateNow(PACKAGE_KILLABLE_STATE_RESET_DAYS);
        UserHandle userHandle = UserHandle.of(101);

        mWatchdogPerfHandler.setKillablePackageAsUser("third_party_package", userHandle,
                /* isKillable= */ false);

        PackageKillableStateSubject
                .assertThat(mWatchdogPerfHandler.getPackageKillableStatesAsUser(userHandle))
                .containsExactly(new PackageKillableState("third_party_package", 101,
                        PackageKillableState.KILLABLE_STATE_NO));

        mTimeSource.updateNow(PACKAGE_KILLABLE_STATE_RESET_DAYS / 2);
        restartService(/* totalRestarts= */ 1, /* wantedDbWrites= */ 1,
                /* isWriteIoStats= */ false);

        PackageKillableStateSubject
                .assertThat(mWatchdogPerfHandler.getPackageKillableStatesAsUser(userHandle))
                .containsExactly(new PackageKillableState("third_party_package", 101,
                        PackageKillableState.KILLABLE_STATE_NO));

        mTimeSource.updateNow(/* numDaysAgo= */ 0);
        restartService(/* totalRestarts= */ 2, /* wantedDbWrites= */ 2,
                /* isWriteIoStats= */ false);

        PackageKillableStateSubject
                .assertThat(mWatchdogPerfHandler.getPackageKillableStatesAsUser(userHandle))
                .containsExactly(new PackageKillableState("third_party_package", 101,
                        PackageKillableState.KILLABLE_STATE_YES));
    }

    @Test
    public void testResetPackageKillableStateDuringDateChange() throws Exception {
        mockUmGetUserHandles(mMockUserManager, /* excludeDying= */ true, 101);
        injectPackageInfos(Collections.singletonList(
                constructPackageManagerPackageInfo("third_party_package",
                        10103456, /* sharedUserId= */ null)));

        UserHandle userHandle = UserHandle.of(101);

        // Reset the latest reported date
        mTimeSource.updateNow(PACKAGE_KILLABLE_STATE_RESET_DAYS);
        restartService(/* totalRestarts= */ 1, /* wantedDbWrites= */ 0, /* isWriteIoStats= */ true);

        mWatchdogPerfHandler.setKillablePackageAsUser("third_party_package", userHandle,
                /* isKillable= */ false);

        PackageKillableStateSubject
                .assertThat(mWatchdogPerfHandler.getPackageKillableStatesAsUser(userHandle))
                .containsExactly(new PackageKillableState("third_party_package", 101,
                        PackageKillableState.KILLABLE_STATE_NO));

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

        mTimeSource.updateNow(/* numDaysAgo= */ 0);
        pushLatestIoOveruseStatsAndWait(packageIoOveruseStats);

        PackageKillableStateSubject
                .assertThat(mWatchdogPerfHandler.getPackageKillableStatesAsUser(userHandle))
                .containsExactly(new PackageKillableState("third_party_package", 101,
                        PackageKillableState.KILLABLE_STATE_YES));
    }

    @Test
    public void testGetPackageKillableStatesAsUser() throws Exception {
        mockUmGetUserHandles(mMockUserManager, /* excludeDying= */ true, 101, 102);
        injectPackageInfos(Arrays.asList(
                constructPackageManagerPackageInfo("third_party_package", 10103456, null),
                constructPackageManagerPackageInfo("vendor_package.critical", 10101278, null),
                constructPackageManagerPackageInfo("third_party_package", 10203456, null),
                constructPackageManagerPackageInfo("vendor_package.critical", 10201278, null)));

        PackageKillableStateSubject.assertThat(
                        mWatchdogPerfHandler.getPackageKillableStatesAsUser(UserHandle.of(101)))
                .containsExactly(
                        new PackageKillableState("third_party_package", 101,
                                PackageKillableState.KILLABLE_STATE_YES),
                        new PackageKillableState("vendor_package.critical", 101,
                                PackageKillableState.KILLABLE_STATE_NEVER));

        verify(mSpiedWatchdogStorage, times(2)).markDirty();
    }

    @Test
    public void testGetPackageKillableStatesAsUserWithSafeToKillPackages() throws Exception {
        mockUmGetUserHandles(mMockUserManager, /* excludeDying= */ true, 100, 101);
        injectPackageInfos(Arrays.asList(
                constructPackageManagerPackageInfo("system_package.non_critical.A", 10002459, null),
                constructPackageManagerPackageInfo("third_party_package", 10003456, null),
                constructPackageManagerPackageInfo("vendor_package.critical.B", 10001278, null),
                constructPackageManagerPackageInfo("vendor_package.non_critical.A", 10005573, null),
                constructPackageManagerPackageInfo("third_party_package", 10103456, null),
                constructPackageManagerPackageInfo("vendor_package.critical.B", 10101278, null)));

        PackageKillableStateSubject.assertThat(
                        mWatchdogPerfHandler.getPackageKillableStatesAsUser(UserHandle.ALL))
                .containsExactly(
                        new PackageKillableState("system_package.non_critical.A", 100,
                                PackageKillableState.KILLABLE_STATE_YES),
                        new PackageKillableState("third_party_package", 100,
                                PackageKillableState.KILLABLE_STATE_YES),
                        new PackageKillableState("vendor_package.critical.B", 100,
                                PackageKillableState.KILLABLE_STATE_NEVER),
                        new PackageKillableState("vendor_package.non_critical.A", 100,
                                PackageKillableState.KILLABLE_STATE_YES),
                        new PackageKillableState("third_party_package", 101,
                                PackageKillableState.KILLABLE_STATE_YES),
                        new PackageKillableState("vendor_package.critical.B", 101,
                                PackageKillableState.KILLABLE_STATE_NEVER));

        verify(mSpiedWatchdogStorage, times(6)).markDirty();
    }

    @Test
    public void testGetPackageKillableStatesAsUserWithVendorPackagePrefixes() throws Exception {
        mockUmGetUserHandles(mMockUserManager, /* excludeDying= */ true, 100);
        injectPackageInfos(Collections.singletonList(constructPackageManagerPackageInfo(
                "some_pkg_as_vendor_pkg", 10002459, /* sharedUserId= */ null, /* flags= */ 0,
                ApplicationInfo.PRIVATE_FLAG_SYSTEM_EXT)));

        List<PackageKillableState> killableStates =
                mWatchdogPerfHandler.getPackageKillableStatesAsUser(UserHandle.of(100));

        // The vendor package prefixes in the resource overuse configs help identify vendor
        // packages. The safe-to-kill list in the vendor configs helps identify safe-to-kill vendor
        // packages. |system_package_as_vendor| is a critical system package by default but with
        // the resource overuse configs, this package should be classified as a safe-to-kill vendor
        // package.
        PackageKillableStateSubject.assertThat(killableStates)
                .containsExactly(new PackageKillableState("some_pkg_as_vendor_pkg", 100,
                        PackageKillableState.KILLABLE_STATE_YES));

        verify(mSpiedWatchdogStorage).markDirty();
    }

    @Test
    public void testGetPackageKillableStatesAsUserWithSharedUids() throws Exception {
        mockUmGetUserHandles(mMockUserManager, /* excludeDying= */ true, 101, 102);
        injectPackageInfos(Arrays.asList(
                constructPackageManagerPackageInfo(
                        "system_package.A", 10103456, "vendor_shared_package.A"),
                constructPackageManagerPackageInfo(
                        "vendor_package.B", 10103456, "vendor_shared_package.A"),
                constructPackageManagerPackageInfo(
                        "third_party_package.C", 10105678, "third_party_shared_package"),
                constructPackageManagerPackageInfo(
                        "third_party_package.D", 10105678, "third_party_shared_package"),
                constructPackageManagerPackageInfo(
                        "system_package.A", 10203456, "vendor_shared_package.A"),
                constructPackageManagerPackageInfo(
                        "vendor_package.B", 10203456, "vendor_shared_package.A")));

        PackageKillableStateSubject.assertThat(
                        mWatchdogPerfHandler.getPackageKillableStatesAsUser(UserHandle.of(101)))
                .containsExactly(
                        new PackageKillableState("system_package.A", 101,
                                PackageKillableState.KILLABLE_STATE_NEVER),
                        new PackageKillableState("vendor_package.B", 101,
                                PackageKillableState.KILLABLE_STATE_NEVER),
                        new PackageKillableState("third_party_package.C", 101,
                                PackageKillableState.KILLABLE_STATE_YES),
                        new PackageKillableState("third_party_package.D", 101,
                                PackageKillableState.KILLABLE_STATE_YES));

        verify(mSpiedWatchdogStorage, times(2)).markDirty();
    }

    @Test
    public void testGetPackageKillableStatesAsUserWithSharedUidsAndSafeToKillPackages()
            throws Exception {
        mockUmGetUserHandles(mMockUserManager, /* excludeDying= */ true, 100);
        injectPackageInfos(Arrays.asList(
                constructPackageManagerPackageInfo(
                        "vendor_package.non_critical.A", 10003456, "vendor_shared_package.A"),
                constructPackageManagerPackageInfo(
                        "system_package.A", 10003456, "vendor_shared_package.A"),
                constructPackageManagerPackageInfo(
                        "vendor_package.B", 10003456, "vendor_shared_package.A"),
                constructPackageManagerPackageInfo(
                        "third_party_package.C", 10005678, "third_party_shared_package"),
                constructPackageManagerPackageInfo(
                        "third_party_package.D", 10005678, "third_party_shared_package")));

        PackageKillableStateSubject.assertThat(
                        mWatchdogPerfHandler.getPackageKillableStatesAsUser(UserHandle.of(100)))
                .containsExactly(
                        new PackageKillableState("vendor_package.non_critical.A", 100,
                                PackageKillableState.KILLABLE_STATE_YES),
                        new PackageKillableState("system_package.A", 100,
                                PackageKillableState.KILLABLE_STATE_YES),
                        new PackageKillableState("vendor_package.B", 100,
                                PackageKillableState.KILLABLE_STATE_YES),
                        new PackageKillableState("third_party_package.C", 100,
                                PackageKillableState.KILLABLE_STATE_YES),
                        new PackageKillableState("third_party_package.D", 100,
                                PackageKillableState.KILLABLE_STATE_YES));

        verify(mSpiedWatchdogStorage, times(2)).markDirty();
    }

    @Test
    public void testGetPackageKillableStatesAsUserWithSharedUidsAndSafeToKillSharedPackage()
            throws Exception {
        mockUmGetUserHandles(mMockUserManager, /* excludeDying= */ true, 100);
        injectPackageInfos(Arrays.asList(
                constructPackageManagerPackageInfo(
                        "vendor_package.A", 10003456, "vendor_shared_package.non_critical.B"),
                constructPackageManagerPackageInfo(
                        "system_package.A", 10003456, "vendor_shared_package.non_critical.B"),
                constructPackageManagerPackageInfo(
                        "vendor_package.B", 10003456, "vendor_shared_package.non_critical.B")));


        PackageKillableStateSubject.assertThat(
                        mWatchdogPerfHandler.getPackageKillableStatesAsUser(UserHandle.of(100)))
                .containsExactly(
                        new PackageKillableState("vendor_package.A", 100,
                                PackageKillableState.KILLABLE_STATE_YES),
                        new PackageKillableState("system_package.A", 100,
                                PackageKillableState.KILLABLE_STATE_YES),
                        new PackageKillableState("vendor_package.B", 100,
                                PackageKillableState.KILLABLE_STATE_YES));

        verify(mSpiedWatchdogStorage).markDirty();
    }

    @Test
    public void testGetPackageKillableStatesAsUserForAllUsers() throws Exception {
        mockUmGetUserHandles(mMockUserManager, /* excludeDying= */ true, 101, 102);
        injectPackageInfos(Arrays.asList(
                constructPackageManagerPackageInfo("third_party_package", 10103456, null),
                constructPackageManagerPackageInfo("vendor_package.critical", 10101278, null),
                constructPackageManagerPackageInfo("third_party_package", 10203456, null),
                constructPackageManagerPackageInfo("vendor_package.critical", 10201278, null)));

        PackageKillableStateSubject.assertThat(
                mWatchdogPerfHandler.getPackageKillableStatesAsUser(
                        UserHandle.ALL)).containsExactly(
                new PackageKillableState("third_party_package", 101,
                        PackageKillableState.KILLABLE_STATE_YES),
                new PackageKillableState("vendor_package.critical", 101,
                        PackageKillableState.KILLABLE_STATE_NEVER),
                new PackageKillableState("third_party_package", 102,
                        PackageKillableState.KILLABLE_STATE_YES),
                new PackageKillableState("vendor_package.critical", 102,
                        PackageKillableState.KILLABLE_STATE_NEVER));

        verify(mSpiedWatchdogStorage, times(4)).markDirty();
    }

    @Test
    public void testGetPackageKillableStatesAsUserForAllUsersWithSharedUids() throws Exception {
        mockUmGetUserHandles(mMockUserManager, /* excludeDying= */ true, 101, 102);
        injectPackageInfos(Arrays.asList(
                constructPackageManagerPackageInfo(
                        "system_package.A", 10103456, "vendor_shared_package.A"),
                constructPackageManagerPackageInfo(
                        "vendor_package.B", 10103456, "vendor_shared_package.A"),
                constructPackageManagerPackageInfo(
                        "third_party_package.C", 10105678, "third_party_shared_package"),
                constructPackageManagerPackageInfo(
                        "third_party_package.D", 10105678, "third_party_shared_package"),
                constructPackageManagerPackageInfo(
                        "system_package.A", 10203456, "vendor_shared_package.A"),
                constructPackageManagerPackageInfo(
                        "vendor_package.B", 10203456, "vendor_shared_package.A")));

        PackageKillableStateSubject.assertThat(
                        mWatchdogPerfHandler.getPackageKillableStatesAsUser(UserHandle.ALL))
                .containsExactly(
                        new PackageKillableState("system_package.A", 101,
                                PackageKillableState.KILLABLE_STATE_NEVER),
                        new PackageKillableState("vendor_package.B", 101,
                                PackageKillableState.KILLABLE_STATE_NEVER),
                        new PackageKillableState("third_party_package.C", 101,
                                PackageKillableState.KILLABLE_STATE_YES),
                        new PackageKillableState("third_party_package.D", 101,
                                PackageKillableState.KILLABLE_STATE_YES),
                        new PackageKillableState("system_package.A", 102,
                                PackageKillableState.KILLABLE_STATE_NEVER),
                        new PackageKillableState("vendor_package.B", 102,
                                PackageKillableState.KILLABLE_STATE_NEVER));

        verify(mSpiedWatchdogStorage, times(3)).markDirty();
    }

    @Test
    public void testGetAllResourceOveruseStatsWithNoMinimum() throws Exception {
        injectPackageInfos(Arrays.asList(
                constructPackageManagerPackageInfo("third_party_package", 1103456, null),
                constructPackageManagerPackageInfo("vendor_package.critical", 1201278, null)));

        List<PackageIoOveruseStats> packageIoOveruseStats = Arrays.asList(
                constructPackageIoOveruseStats(1103456,
                        /* shouldNotify= */ true,
                        /* forgivenWriteBytes= */ constructPerStateBytes(100, 200, 300),
                        constructInternalIoOveruseStats(/* killableOnOveruse= */ true,
                                /* remainingWriteBytes= */ constructPerStateBytes(0, 0, 0),
                                /* writtenBytes= */ constructPerStateBytes(100, 200, 300),
                                /* totalOveruses= */ 3)),
                constructPackageIoOveruseStats(1201278,
                        /* shouldNotify= */ false,
                        /* forgivenWriteBytes= */ constructPerStateBytes(5000, 6000, 9000),
                        constructInternalIoOveruseStats(/* killableOnOveruse= */ false,
                                /* remainingWriteBytes= */ constructPerStateBytes(450, 120, 340),
                                /* writtenBytes= */ constructPerStateBytes(5000, 6000, 9000),
                                /* totalOveruses= */ 3)));
        pushLatestIoOveruseStatsAndWait(packageIoOveruseStats);

        List<ResourceOveruseStats> expectedStats = Arrays.asList(
                constructResourceOveruseStats(1103456, "third_party_package",
                        packageIoOveruseStats.get(0).ioOveruseStats),
                constructResourceOveruseStats(1201278, "vendor_package.critical",
                        packageIoOveruseStats.get(1).ioOveruseStats));

        List<ResourceOveruseStats> actualStats = mWatchdogPerfHandler.getAllResourceOveruseStats(
                CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO, /* minimumStatsFlag= */ 0,
                CarWatchdogManager.STATS_PERIOD_CURRENT_DAY);

        ResourceOveruseStatsSubject.assertThat(actualStats)
                .containsExactlyElementsIn(expectedStats);

        verifyNoMoreInteractions(mSpiedWatchdogStorage);
    }

    @Test
    public void testGetAllResourceOveruseStatsWithNoMinimumForPast7days() throws Exception {
        injectPackageInfos(Arrays.asList(
                constructPackageManagerPackageInfo("third_party_package", 1103456, null),
                constructPackageManagerPackageInfo("vendor_package.critical", 1201278, null)));

        List<PackageIoOveruseStats> packageIoOveruseStats = Arrays.asList(
                constructPackageIoOveruseStats(1103456,
                        /* shouldNotify= */ true,
                        /* forgivenWriteBytes= */ constructPerStateBytes(100, 200, 300),
                        constructInternalIoOveruseStats(/* killableOnOveruse= */ true,
                                /* remainingWriteBytes= */ constructPerStateBytes(0, 0, 0),
                                /* writtenBytes= */ constructPerStateBytes(100, 200, 300),
                                /* totalOveruses= */ 3)),
                constructPackageIoOveruseStats(1201278,
                        /* shouldNotify= */ false,
                        /* forgivenWriteBytes= */ constructPerStateBytes(0, 0, 0),
                        constructInternalIoOveruseStats(/* killableOnOveruse= */ false,
                                /* remainingWriteBytes= */ constructPerStateBytes(450, 120, 340),
                                /* writtenBytes= */ constructPerStateBytes(5000, 6000, 9000),
                                /* totalOveruses= */ 0)));
        pushLatestIoOveruseStatsAndWait(packageIoOveruseStats);

        ZonedDateTime now = mTimeSource.getCurrentDateTime();
        long startTime = now.minusDays(4).toEpochSecond();
        IoOveruseStats thirdPartyPkgOldStats = new IoOveruseStats.Builder(
                startTime, now.toEpochSecond() - startTime).setTotalOveruses(5)
                .setTotalTimesKilled(2).setTotalBytesWritten(24_000).build();
        doReturn(thirdPartyPkgOldStats).when(mSpiedWatchdogStorage)
                .getHistoricalIoOveruseStats(11, "third_party_package", 6);

        startTime = now.minusDays(6).toEpochSecond();
        IoOveruseStats vendorPkgOldStats = new IoOveruseStats.Builder(
                startTime, now.toEpochSecond() - startTime).setTotalOveruses(2)
                .setTotalTimesKilled(0).setTotalBytesWritten(35_000).build();
        doReturn(vendorPkgOldStats).when(mSpiedWatchdogStorage)
                .getHistoricalIoOveruseStats(12, "vendor_package.critical", 6);

        List<ResourceOveruseStats> actualStats = mWatchdogPerfHandler.getAllResourceOveruseStats(
                CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO, /* minimumStatsFlag= */ 0,
                CarWatchdogManager.STATS_PERIOD_PAST_7_DAYS);

        IoOveruseStats thirdPartyIoStats = new IoOveruseStats.Builder(
                thirdPartyPkgOldStats.getStartTime(),
                thirdPartyPkgOldStats.getDurationInSeconds() + STATS_DURATION_SECONDS)
                .setKillableOnOveruse(true).setTotalOveruses(8).setTotalBytesWritten(24_600)
                .setTotalTimesKilled(2).setRemainingWriteBytes(new PerStateBytes(0, 0, 0))
                .build();
        IoOveruseStats vendorIoStats = new IoOveruseStats.Builder(
                vendorPkgOldStats.getStartTime(),
                vendorPkgOldStats.getDurationInSeconds() + STATS_DURATION_SECONDS)
                .setKillableOnOveruse(false).setTotalOveruses(2).setTotalBytesWritten(55_000)
                .setTotalTimesKilled(0).setRemainingWriteBytes(new PerStateBytes(450, 120, 340))
                .build();

        List<ResourceOveruseStats> expectedStats = Arrays.asList(
                new ResourceOveruseStats.Builder("third_party_package", UserHandle.of(11))
                        .setIoOveruseStats(thirdPartyIoStats).build(),
                new ResourceOveruseStats.Builder("vendor_package.critical", UserHandle.of(12))
                        .setIoOveruseStats(vendorIoStats).build());

        ResourceOveruseStatsSubject.assertThat(actualStats)
                .containsExactlyElementsIn(expectedStats);
    }

    @Test
    public void testGetAllResourceOveruseStatsForSharedPackage() throws Exception {
        injectPackageInfos(Arrays.asList(
                constructPackageManagerPackageInfo(
                        "vendor_package.A", 1103456, "vendor_shared_package"),
                constructPackageManagerPackageInfo(
                        "third_party_package.B", 1103456, "vendor_shared_package"),
                constructPackageManagerPackageInfo(
                        "system_package.C", 1201000, "system_shared_package"),
                constructPackageManagerPackageInfo(
                        "system_package.D", 1201000, "system_shared_package"),
                constructPackageManagerPackageInfo(
                        "third_party_package.A", 1303456, "vendor_shared_package"),
                constructPackageManagerPackageInfo(
                        "vendor_package.B", 1303456, "vendor_shared_package")));

        List<PackageIoOveruseStats> packageIoOveruseStats = Arrays.asList(
                constructPackageIoOveruseStats(1103456,
                        /* shouldNotify= */ false,
                        /* forgivenWriteBytes= */ constructPerStateBytes(50, 100, 150),
                        constructInternalIoOveruseStats(/* killableOnOveruse= */ true,
                                /* remainingWriteBytes= */ constructPerStateBytes(20, 20, 20),
                                /* writtenBytes= */ constructPerStateBytes(100, 200, 300),
                                /* totalOveruses= */ 3)),
                constructPackageIoOveruseStats(1201000,
                        /* shouldNotify= */ false,
                        /* forgivenWriteBytes= */ constructPerStateBytes(0, 0, 0),
                        constructInternalIoOveruseStats(/* killableOnOveruse= */ false,
                                /* remainingWriteBytes= */ constructPerStateBytes(450, 120, 340),
                                /* writtenBytes= */ constructPerStateBytes(5000, 6000, 9000),
                                /* totalOveruses= */ 0)),
                constructPackageIoOveruseStats(1303456,
                        /* shouldNotify= */ true,
                        /* forgivenWriteBytes= */ constructPerStateBytes(80, 170, 260),
                        constructInternalIoOveruseStats(/* killableOnOveruse= */ true,
                                /* remainingWriteBytes= */ constructPerStateBytes(0, 0, 0),
                                /* writtenBytes= */ constructPerStateBytes(80, 170, 260),
                                /* totalOveruses= */ 1)));

        pushLatestIoOveruseStatsAndWait(packageIoOveruseStats);

        List<ResourceOveruseStats> expectedStats = Arrays.asList(
                constructResourceOveruseStats(1103456, "shared:vendor_shared_package",
                        packageIoOveruseStats.get(0).ioOveruseStats),
                constructResourceOveruseStats(1201278, "shared:system_shared_package",
                        packageIoOveruseStats.get(1).ioOveruseStats),
                constructResourceOveruseStats(1303456, "shared:vendor_shared_package",
                        packageIoOveruseStats.get(2).ioOveruseStats));

        List<ResourceOveruseStats> actualStats = mWatchdogPerfHandler.getAllResourceOveruseStats(
                CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO, /* minimumStatsFlag= */ 0,
                CarWatchdogManager.STATS_PERIOD_CURRENT_DAY);

        ResourceOveruseStatsSubject.assertThat(actualStats)
                .containsExactlyElementsIn(expectedStats);

        verifyNoMoreInteractions(mSpiedWatchdogStorage);
    }

    @Test
    public void testFailsGetAllResourceOveruseStatsWithInvalidArgs() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> mWatchdogPerfHandler.getAllResourceOveruseStats(0, /* minimumStatsFlag= */ 0,
                        CarWatchdogManager.STATS_PERIOD_CURRENT_DAY));

        assertThrows(IllegalArgumentException.class,
                () -> mWatchdogPerfHandler.getAllResourceOveruseStats(
                        CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO,
                        CarWatchdogManager.FLAG_MINIMUM_STATS_IO_1_MB
                                | CarWatchdogManager.FLAG_MINIMUM_STATS_IO_100_MB,
                        CarWatchdogManager.STATS_PERIOD_CURRENT_DAY));

        assertThrows(IllegalArgumentException.class,
                () -> mWatchdogPerfHandler.getAllResourceOveruseStats(
                        CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO, /* minimumStatsFlag= */ 1 << 5,
                        CarWatchdogManager.STATS_PERIOD_CURRENT_DAY));

        assertThrows(IllegalArgumentException.class,
                () -> mWatchdogPerfHandler.getAllResourceOveruseStats(
                        CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO, /* minimumStatsFlag= */ 0,
                        /* maxStatsPeriod= */ 0));
    }

    @Test
    public void testGetAllResourceOveruseStatsWithMinimum() throws Exception {
        injectPackageInfos(Arrays.asList(
                constructPackageManagerPackageInfo("third_party_package", 1103456, null),
                constructPackageManagerPackageInfo("vendor_package.critical", 1201278, null)));

        List<PackageIoOveruseStats> packageIoOveruseStats = Arrays.asList(
                constructPackageIoOveruseStats(1103456, /* shouldNotify= */ false,
                        /* forgivenWriteBytes= */ constructPerStateBytes(80, 170, 260),
                        constructInternalIoOveruseStats(/* killableOnOveruse= */ true,
                                /* remainingWriteBytes= */ constructPerStateBytes(20, 20, 20),
                                /* writtenBytes= */ constructPerStateBytes(100, 200, 300),
                                /* totalOveruses= */ 3)),
                constructPackageIoOveruseStats(1201278, /* shouldNotify= */ false,
                        /* forgivenWriteBytes= */ constructPerStateBytes(5_070_000, 4500, 7000),
                        constructInternalIoOveruseStats(/* killableOnOveruse= */ false,
                                /* remainingWriteBytes= */ constructPerStateBytes(450, 120, 340),
                                /* writtenBytes= */ constructPerStateBytes(7_000_000, 6000, 9000),
                                /* totalOveruses= */ 3)));
        pushLatestIoOveruseStatsAndWait(packageIoOveruseStats);

        List<ResourceOveruseStats> expectedStats = Collections.singletonList(
                constructResourceOveruseStats(1201278, "vendor_package.critical",
                        packageIoOveruseStats.get(1).ioOveruseStats));

        List<ResourceOveruseStats> actualStats = mWatchdogPerfHandler.getAllResourceOveruseStats(
                CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO,
                CarWatchdogManager.FLAG_MINIMUM_STATS_IO_1_MB,
                CarWatchdogManager.STATS_PERIOD_CURRENT_DAY);

        ResourceOveruseStatsSubject.assertThat(actualStats)
                .containsExactlyElementsIn(expectedStats);

        verifyNoMoreInteractions(mSpiedWatchdogStorage);
    }

    @Test
    public void testGetAllResourceOveruseStatsWithMinimumForPast7days() throws Exception {
        injectPackageInfos(Arrays.asList(
                constructPackageManagerPackageInfo("third_party_package", 1103456, null),
                constructPackageManagerPackageInfo("vendor_package.critical", 1201278, null)));

        List<PackageIoOveruseStats> packageIoOveruseStats = Arrays.asList(
                constructPackageIoOveruseStats(1103456,
                        /* shouldNotify= */ true,
                        /* forgivenWriteBytes= */ constructPerStateBytes(80, 170, 260),
                        constructInternalIoOveruseStats(/* killableOnOveruse= */ true,
                                /* remainingWriteBytes= */ constructPerStateBytes(20, 20, 20),
                                /* writtenBytes= */ constructPerStateBytes(100, 200, 300),
                                /* totalOveruses= */ 3)),
                constructPackageIoOveruseStats(1201278,
                        /* shouldNotify= */ false,
                        /* forgivenWriteBytes= */ constructPerStateBytes(0, 0, 0),
                        constructInternalIoOveruseStats(/* killableOnOveruse= */ false,
                                /* remainingWriteBytes= */ constructPerStateBytes(450, 120, 340),
                                /* writtenBytes= */ constructPerStateBytes(100_000, 6000, 9000),
                                /* totalOveruses= */ 0)));
        pushLatestIoOveruseStatsAndWait(packageIoOveruseStats);

        ZonedDateTime now = mTimeSource.getCurrentDateTime();
        long startTime = now.minusDays(4).toEpochSecond();
        IoOveruseStats thirdPartyPkgOldStats = new IoOveruseStats.Builder(
                startTime, now.toEpochSecond() - startTime).setTotalOveruses(5)
                .setTotalTimesKilled(2).setTotalBytesWritten(24_000).build();
        doReturn(thirdPartyPkgOldStats).when(mSpiedWatchdogStorage)
                .getHistoricalIoOveruseStats(11, "third_party_package", 6);

        startTime = now.minusDays(6).toEpochSecond();
        IoOveruseStats vendorPkgOldStats = new IoOveruseStats.Builder(
                startTime, now.toEpochSecond() - startTime).setTotalOveruses(2)
                .setTotalTimesKilled(0).setTotalBytesWritten(6_900_000).build();
        doReturn(vendorPkgOldStats).when(mSpiedWatchdogStorage)
                .getHistoricalIoOveruseStats(12, "vendor_package.critical", 6);

        List<ResourceOveruseStats> actualStats = mWatchdogPerfHandler.getAllResourceOveruseStats(
                CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO,
                CarWatchdogManager.FLAG_MINIMUM_STATS_IO_1_MB,
                CarWatchdogManager.STATS_PERIOD_PAST_7_DAYS);

        IoOveruseStats vendorIoStats = new IoOveruseStats.Builder(
                vendorPkgOldStats.getStartTime(),
                vendorPkgOldStats.getDurationInSeconds() + STATS_DURATION_SECONDS)
                .setKillableOnOveruse(false).setTotalOveruses(2).setTotalBytesWritten(7_015_000)
                .setTotalTimesKilled(0).setRemainingWriteBytes(new PerStateBytes(450, 120, 340))
                .build();

        List<ResourceOveruseStats> expectedStats = Collections.singletonList(
                new ResourceOveruseStats.Builder("vendor_package.critical", UserHandle.of(12))
                        .setIoOveruseStats(vendorIoStats).build());

        ResourceOveruseStatsSubject.assertThat(actualStats)
                .containsExactlyElementsIn(expectedStats);
    }

    @Test
    public void testGetResourceOveruseStats() throws Exception {
        int uid = Binder.getCallingUid();
        injectPackageInfos(Collections.singletonList(
                constructPackageManagerPackageInfo(
                        mMockContext.getPackageName(), uid, null, ApplicationInfo.FLAG_SYSTEM, 0)));

        SparseArray<PackageIoOveruseStats> packageIoOveruseStatsByUid =
                injectIoOveruseStatsForPackages(
                        mGenericPackageNameByUid, /* killablePackages= */ new ArraySet<>(),
                        /* shouldNotifyPackages= */ new ArraySet<>());

        ResourceOveruseStats expectedStats =
                constructResourceOveruseStats(uid, mMockContext.getPackageName(),
                        packageIoOveruseStatsByUid.get(uid).ioOveruseStats);

        ResourceOveruseStats actualStats = mWatchdogPerfHandler.getResourceOveruseStats(
                FLAG_RESOURCE_OVERUSE_IO,
                CarWatchdogManager.STATS_PERIOD_CURRENT_DAY);

        ResourceOveruseStatsSubject.assertEquals(actualStats, expectedStats);

        verifyNoMoreInteractions(mSpiedWatchdogStorage);
    }

    @Test
    public void testGetResourceOveruseStatsForPast7days() throws Exception {
        int uid = Binder.getCallingUid();
        String packageName = mMockContext.getPackageName();
        injectPackageInfos(Collections.singletonList(constructPackageManagerPackageInfo(
                packageName, uid, null, ApplicationInfo.FLAG_SYSTEM, 0)));

        long startTime = mTimeSource.getCurrentDateTime().minusDays(4).toEpochSecond();
        long duration = mTimeSource.now().getEpochSecond() - startTime;
        doReturn(new IoOveruseStats.Builder(startTime, duration).setTotalOveruses(5)
                .setTotalTimesKilled(2).setTotalBytesWritten(24_000).build())
                .when(mSpiedWatchdogStorage)
                .getHistoricalIoOveruseStats(UserHandle.getUserId(uid), packageName, 6);

        injectIoOveruseStatsForPackages(mGenericPackageNameByUid,
                /* killablePackages= */ Collections.singleton(packageName),
                /* shouldNotifyPackages= */ new ArraySet<>());

        ResourceOveruseStats actualStats = mWatchdogPerfHandler.getResourceOveruseStats(
                FLAG_RESOURCE_OVERUSE_IO,
                CarWatchdogManager.STATS_PERIOD_PAST_7_DAYS);

        IoOveruseStats ioOveruseStats =
                new IoOveruseStats.Builder(startTime, duration + STATS_DURATION_SECONDS)
                        .setKillableOnOveruse(true).setTotalOveruses(8).setTotalBytesWritten(24_600)
                        .setTotalTimesKilled(2)
                        .setRemainingWriteBytes(new PerStateBytes(20, 20, 20)).build();

        ResourceOveruseStats expectedStats =
                new ResourceOveruseStats.Builder(packageName, UserHandle.getUserHandleForUid(uid))
                        .setIoOveruseStats(ioOveruseStats).build();

        ResourceOveruseStatsSubject.assertEquals(actualStats, expectedStats);
    }

    @Test
    public void testGetResourceOveruseStatsForPast7daysWithNoHistory() throws Exception {
        int uid = Binder.getCallingUid();
        String packageName = mMockContext.getPackageName();
        injectPackageInfos(Collections.singletonList(constructPackageManagerPackageInfo(
                packageName, uid, null, ApplicationInfo.FLAG_SYSTEM, 0)));

        doReturn(null).when(mSpiedWatchdogStorage)
                .getHistoricalIoOveruseStats(UserHandle.getUserId(uid), packageName, 6);

        injectIoOveruseStatsForPackages(mGenericPackageNameByUid,
                /* killablePackages= */ Collections.singleton(packageName),
                /* shouldNotifyPackages= */ new ArraySet<>());

        ResourceOveruseStats actualStats = mWatchdogPerfHandler.getResourceOveruseStats(
                FLAG_RESOURCE_OVERUSE_IO,
                CarWatchdogManager.STATS_PERIOD_PAST_7_DAYS);

        ResourceOveruseStats expectedStats =
                new ResourceOveruseStats.Builder(packageName, UserHandle.getUserHandleForUid(uid))
                        .setIoOveruseStats(new IoOveruseStats.Builder(
                                mTimeSource.now().getEpochSecond(), STATS_DURATION_SECONDS)
                                .setKillableOnOveruse(true).setTotalOveruses(3)
                                .setTotalBytesWritten(600)
                                .setRemainingWriteBytes(new PerStateBytes(20, 20, 20)).build())
                        .build();

        ResourceOveruseStatsSubject.assertEquals(actualStats, expectedStats);
    }

    @Test
    public void testGetResourceOveruseStatsForPast7daysWithNoCurrentStats() throws Exception {
        int uid = Binder.getCallingUid();
        String packageName = mMockContext.getPackageName();
        injectPackageInfos(Collections.singletonList(constructPackageManagerPackageInfo(
                packageName, uid, null, ApplicationInfo.FLAG_SYSTEM, 0)));

        long startTime = mTimeSource.getCurrentDateTime().minusDays(4).toEpochSecond();
        long duration = mTimeSource.now().getEpochSecond() - startTime;
        doReturn(new IoOveruseStats.Builder(startTime, duration).setTotalOveruses(5)
                .setTotalTimesKilled(2).setTotalBytesWritten(24_000).build())
                .when(mSpiedWatchdogStorage)
                .getHistoricalIoOveruseStats(UserHandle.getUserId(uid), packageName, 6);

        ResourceOveruseStats actualStats = mWatchdogPerfHandler.getResourceOveruseStats(
                FLAG_RESOURCE_OVERUSE_IO,
                CarWatchdogManager.STATS_PERIOD_PAST_7_DAYS);

        ResourceOveruseStats expectedStats =
                new ResourceOveruseStats.Builder(packageName, UserHandle.getUserHandleForUid(uid))
                        .build();

        ResourceOveruseStatsSubject.assertEquals(actualStats, expectedStats);
    }

    @Test
    public void testGetResourceOveruseStatsForSharedUid() throws Exception {
        int sharedUid = Binder.getCallingUid();
        injectPackageInfos(Collections.singletonList(
                constructPackageManagerPackageInfo(
                        mMockContext.getPackageName(), sharedUid, "system_shared_package",
                        ApplicationInfo.FLAG_SYSTEM, 0)));

        SparseArray<PackageIoOveruseStats> packageIoOveruseStatsByUid =
                injectIoOveruseStatsForPackages(
                        mGenericPackageNameByUid, /* killablePackages= */ new ArraySet<>(),
                        /* shouldNotifyPackages= */ new ArraySet<>());

        ResourceOveruseStats expectedStats =
                constructResourceOveruseStats(sharedUid, "shared:system_shared_package",
                        packageIoOveruseStatsByUid.get(sharedUid).ioOveruseStats);

        ResourceOveruseStats actualStats = mWatchdogPerfHandler.getResourceOveruseStats(
                FLAG_RESOURCE_OVERUSE_IO,
                CarWatchdogManager.STATS_PERIOD_CURRENT_DAY);

        ResourceOveruseStatsSubject.assertEquals(actualStats, expectedStats);
    }

    @Test
    public void testGetResourceOveruseStatsForUserPackage() throws Exception {
        injectPackageInfos(Arrays.asList(
                constructPackageManagerPackageInfo("third_party_package", 1103456, null),
                constructPackageManagerPackageInfo("vendor_package.critical", 1201278, null)));

        List<PackageIoOveruseStats> packageIoOveruseStats = Arrays.asList(
                constructPackageIoOveruseStats(1103456,
                        /* shouldNotify= */ false,
                        /* forgivenWriteBytes= */ constructPerStateBytes(80, 170, 260),
                        constructInternalIoOveruseStats(/* killableOnOveruse= */ true,
                                /* remainingWriteBytes= */ constructPerStateBytes(20, 20, 20),
                                /* writtenBytes= */ constructPerStateBytes(100, 200, 300),
                                /* totalOveruses= */ 3)),
                constructPackageIoOveruseStats(1201278,
                        /* shouldNotify= */ false,
                        /* forgivenWriteBytes= */ constructPerStateBytes(300, 400, 700),
                        constructInternalIoOveruseStats(/* killableOnOveruse= */ false,
                                /* remainingWriteBytes= */ constructPerStateBytes(450, 120, 340),
                                /* writtenBytes= */ constructPerStateBytes(500, 600, 900),
                                /* totalOveruses= */ 3)));
        pushLatestIoOveruseStatsAndWait(packageIoOveruseStats);

        ResourceOveruseStats expectedStats =
                constructResourceOveruseStats(1201278, "vendor_package.critical",
                        packageIoOveruseStats.get(1).ioOveruseStats);

        ResourceOveruseStats actualStats =
                mWatchdogPerfHandler.getResourceOveruseStatsForUserPackage(
                        "vendor_package.critical", UserHandle.of(12),
                        CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO,
                        CarWatchdogManager.STATS_PERIOD_CURRENT_DAY);

        ResourceOveruseStatsSubject.assertEquals(actualStats, expectedStats);
    }

    @Test
    public void testGetResourceOveruseStatsForUserPackageForPast7days() throws Exception {
        injectPackageInfos(Arrays.asList(
                constructPackageManagerPackageInfo("third_party_package", 1103456, null),
                constructPackageManagerPackageInfo("vendor_package.critical", 1201278, null)));

        List<PackageIoOveruseStats> packageIoOveruseStats = Arrays.asList(
                constructPackageIoOveruseStats(1103456,
                        /* shouldNotify= */ false,
                        /* forgivenWriteBytes= */ constructPerStateBytes(80, 170, 260),
                        constructInternalIoOveruseStats(/* killableOnOveruse= */ true,
                                /* remainingWriteBytes= */ constructPerStateBytes(20, 20, 20),
                                /* writtenBytes= */ constructPerStateBytes(100, 200, 300),
                                /* totalOveruses= */ 3)),
                constructPackageIoOveruseStats(1201278,
                        /* shouldNotify= */ false,
                        /* forgivenWriteBytes= */ constructPerStateBytes(300, 400, 700),
                        constructInternalIoOveruseStats(/* killableOnOveruse= */ false,
                                /* remainingWriteBytes= */ constructPerStateBytes(450, 120, 340),
                                /* writtenBytes= */ constructPerStateBytes(500, 600, 900),
                                /* totalOveruses= */ 3)));
        pushLatestIoOveruseStatsAndWait(packageIoOveruseStats);

        ZonedDateTime now = mTimeSource.getCurrentDateTime();
        long startTime = now.minusDays(4).toEpochSecond();
        IoOveruseStats vendorPkgOldStats = new IoOveruseStats.Builder(
                startTime, now.toEpochSecond() - startTime).setTotalOveruses(2)
                .setTotalTimesKilled(0).setTotalBytesWritten(6_900_000).build();

        doReturn(vendorPkgOldStats).when(mSpiedWatchdogStorage)
                .getHistoricalIoOveruseStats(12, "vendor_package.critical", 6);

        ResourceOveruseStats actualStats =
                mWatchdogPerfHandler.getResourceOveruseStatsForUserPackage(
                        "vendor_package.critical", UserHandle.of(12),
                        CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO,
                        CarWatchdogManager.STATS_PERIOD_PAST_7_DAYS);

        IoOveruseStats vendorIoStats = new IoOveruseStats.Builder(
                vendorPkgOldStats.getStartTime(),
                vendorPkgOldStats.getDurationInSeconds() + STATS_DURATION_SECONDS)
                .setKillableOnOveruse(false).setTotalOveruses(5).setTotalBytesWritten(6_902_000)
                .setTotalTimesKilled(0).setRemainingWriteBytes(new PerStateBytes(450, 120, 340))
                .build();

        ResourceOveruseStats expectedStats = new ResourceOveruseStats.Builder(
                "vendor_package.critical", UserHandle.of(12)).setIoOveruseStats(vendorIoStats)
                .build();

        ResourceOveruseStatsSubject.assertEquals(actualStats, expectedStats);
    }

    @Test
    public void testGetResourceOveruseStatsForUserPackageWithSharedUids() throws Exception {
        injectPackageInfos(Arrays.asList(
                constructPackageManagerPackageInfo(
                        "third_party_package", 1103456, "vendor_shared_package"),
                constructPackageManagerPackageInfo(
                        "vendor_package", 1103456, "vendor_shared_package"),
                constructPackageManagerPackageInfo("system_package", 1101100,
                        "shared_system_package")));

        SparseArray<PackageIoOveruseStats> packageIoOveruseStatsByUid =
                injectIoOveruseStatsForPackages(
                        mGenericPackageNameByUid, /* killablePackages= */ new ArraySet<>(
                                Collections.singleton("shared:vendor_shared_package")),
                        /* shouldNotifyPackages= */ new ArraySet<>());

        ResourceOveruseStats expectedStats =
                constructResourceOveruseStats(1103456, "shared:vendor_shared_package",
                        packageIoOveruseStatsByUid.get(1103456).ioOveruseStats);

        ResourceOveruseStats actualStats =
                mWatchdogPerfHandler.getResourceOveruseStatsForUserPackage(
                        "vendor_package", UserHandle.of(11),
                        CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO,
                        CarWatchdogManager.STATS_PERIOD_CURRENT_DAY);

        ResourceOveruseStatsSubject.assertEquals(actualStats, expectedStats);
    }

    @Test
    public void testFailsGetResourceOveruseStatsForUserPackageWithInvalidArgs() throws Exception {
        assertThrows(NullPointerException.class,
                () -> mWatchdogPerfHandler.getResourceOveruseStatsForUserPackage(
                        /* packageName= */ null, UserHandle.of(100),
                        CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO,
                        CarWatchdogManager.STATS_PERIOD_CURRENT_DAY));

        assertThrows(NullPointerException.class,
                () -> mWatchdogPerfHandler.getResourceOveruseStatsForUserPackage("some_package",
                        /* userHandle= */ null, CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO,
                        CarWatchdogManager.STATS_PERIOD_CURRENT_DAY));

        assertThrows(IllegalArgumentException.class,
                () -> mWatchdogPerfHandler.getResourceOveruseStatsForUserPackage("some_package",
                        UserHandle.ALL, CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO,
                        CarWatchdogManager.STATS_PERIOD_CURRENT_DAY));

        assertThrows(IllegalArgumentException.class,
                () -> mWatchdogPerfHandler.getResourceOveruseStatsForUserPackage("some_package",
                        UserHandle.of(100), /* resourceOveruseFlag= */ 0,
                        CarWatchdogManager.STATS_PERIOD_CURRENT_DAY));

        assertThrows(IllegalArgumentException.class,
                () -> mWatchdogPerfHandler.getResourceOveruseStatsForUserPackage("some_package",
                        UserHandle.of(100), CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO,
                        /* maxStatsPeriod= */ 0));
    }

    @Test
    public void testNoDisableRecurrentlyOverusingAppWhenDisplayEnabled() throws Exception {
        setUpSampleUserAndPackages();
        setRequiresDistractionOptimization(false);
        mWatchdogPerfHandler.onDisplayStateChanged(/* isEnabled= */ true);

        List<PackageIoOveruseStats> packageIoOveruseStats =
                sampleIoOveruseStats(/* requireRecurrentOveruseStats= */ true);

        pushLatestIoOveruseStatsAndWait(packageIoOveruseStats);

        captureAndVerifyIoOveruseStatsReported(sampleReportedOveruseStats());

        verify(() -> CarStatsLog.write(eq(CarStatsLog.CAR_WATCHDOG_KILL_STATS_REPORTED), anyInt(),
                anyInt(), anyInt(), anyInt(), any(), any()), never());

        verifyNoDisabledPackages();
    }

    @Test
    public void testDisableRecurrentlyOverusingAppAfterDisplayDisabled() throws Exception {
        setUpSampleUserAndPackages();
        setRequiresDistractionOptimization(true);
        mWatchdogPerfHandler.onDisplayStateChanged(/* isEnabled= */ true);

        List<PackageIoOveruseStats> packageIoOveruseStats =
                sampleIoOveruseStats(/* requireRecurrentOveruseStats= */ true);

        pushLatestIoOveruseStatsAndWait(packageIoOveruseStats);

        verifyNoDisabledPackages();

        setRequiresDistractionOptimization(false);

        verifyNoDisabledPackages();

        mWatchdogPerfHandler.onDisplayStateChanged(/* isEnabled= */ false);

        captureAndVerifyIoOveruseStatsReported(sampleReportedOveruseStats());

        captureAndVerifyKillStatsReported(sampleReportedKillStats(
                CAR_WATCHDOG_KILL_STATS_REPORTED__SYSTEM_STATE__USER_NO_INTERACTION_MODE,
                /* killedUids= */ new int[]{10010004, 10110004, 10010005, 10110005}));

        verifyDisabledPackages(/* userPackagesCsv= */ "100:vendor_package.non_critical,"
                + "101:vendor_package.non_critical,100:third_party_package.A,"
                + "101:third_party_package.A,100:third_party_package.B,"
                + "101:third_party_package.B");
    }

    @Test
    public void testImmediateDisableRecurrentlyOverusingAppDuringDisabledDisplay()
            throws Exception {
        setUpSampleUserAndPackages();
        setRequiresDistractionOptimization(false);
        mWatchdogPerfHandler.onDisplayStateChanged(/* isEnabled= */ false);

        List<PackageIoOveruseStats> packageIoOveruseStats =
                sampleIoOveruseStats(/* requireRecurrentOveruseStats= */ true);

        pushLatestIoOveruseStatsAndWait(packageIoOveruseStats);

        captureAndVerifyIoOveruseStatsReported(sampleReportedOveruseStats());

        captureAndVerifyKillStatsReported(sampleReportedKillStats(
                CAR_WATCHDOG_KILL_STATS_REPORTED__SYSTEM_STATE__USER_NO_INTERACTION_MODE,
                /* killedUids= */ new int[]{10010004, 10110004, 10010005, 10110005}));

        verifyDisabledPackages(/* userPackagesCsv= */ "100:vendor_package.non_critical,"
                + "101:vendor_package.non_critical,100:third_party_package.A,"
                + "101:third_party_package.A,100:third_party_package.B,"
                + "101:third_party_package.B");
    }

    @Test
    public void testDisableRecurrentlyOverusingAppWhenDisplayDisabledAfterDateChange()
            throws Exception {
        mTimeSource.updateNow(/* numDaysAgo= */ 1);
        setUpSampleUserAndPackages();
        setRequiresDistractionOptimization(true);
        mWatchdogPerfHandler.onDisplayStateChanged(/* isEnabled= */ true);

        List<PackageIoOveruseStats> packageIoOveruseStats =
                sampleIoOveruseStats(/* requireRecurrentOveruseStats= */ true);

        pushLatestIoOveruseStatsAndWait(packageIoOveruseStats);

        verifyNoDisabledPackages();

        mTimeSource.updateNow(/* numDaysAgo= */ 0);

        setRequiresDistractionOptimization(false);
        mWatchdogPerfHandler.onDisplayStateChanged(/* isEnabled= */ false);

        captureAndVerifyIoOveruseStatsReported(sampleReportedOveruseStats());

        captureAndVerifyKillStatsReported(sampleReportedKillStats(
                CAR_WATCHDOG_KILL_STATS_REPORTED__SYSTEM_STATE__USER_NO_INTERACTION_MODE,
                /* killedUids= */ new int[]{10010004, 10110004, 10010005, 10110005}));

        verifyDisabledPackages(/* userPackagesCsv= */ "100:vendor_package.non_critical,"
                + "101:vendor_package.non_critical,100:third_party_package.A,"
                + "101:third_party_package.A,100:third_party_package.B,"
                + "101:third_party_package.B");
    }

    @Test
    public void testNoDisableRecurrentlyOverusingPrePrioritizedApp() throws Exception {
        setUpSampleUserAndPackages();
        setRequiresDistractionOptimization(true);
        mWatchdogPerfHandler.onDisplayStateChanged(/* isEnabled= */ true);

        mWatchdogPerfHandler.setKillablePackageAsUser(
                "vendor_package.non_critical", new UserHandle(100), /* isKillable= */ false);
        mWatchdogPerfHandler.setKillablePackageAsUser(
                "third_party_package.A", new UserHandle(101), /* isKillable= */ false);

        List<PackageIoOveruseStats> packageIoOveruseStats =
                sampleIoOveruseStats(/* requireRecurrentOveruseStats= */ true);

        pushLatestIoOveruseStatsAndWait(packageIoOveruseStats);

        verifyNoDisabledPackages();

        setRequiresDistractionOptimization(false);
        mWatchdogPerfHandler.onDisplayStateChanged(/* isEnabled= */ false);

        captureAndVerifyIoOveruseStatsReported(sampleReportedOveruseStats());

        captureAndVerifyKillStatsReported(sampleReportedKillStats(
                CAR_WATCHDOG_KILL_STATS_REPORTED__SYSTEM_STATE__USER_NO_INTERACTION_MODE,
                /* killedUids= */ new int[]{10110004, 10010005}));

        verifyDisabledPackages(/* userPackagesCsv= */ "101:vendor_package.non_critical,"
                + "100:third_party_package.A,100:third_party_package.B");
    }

    @Test
    public void testNoDisableRecurrentlyOverusingPostPrioritizedApp() throws Exception {
        setUpSampleUserAndPackages();
        setRequiresDistractionOptimization(true);
        mWatchdogPerfHandler.onDisplayStateChanged(/* isEnabled= */ true);

        List<PackageIoOveruseStats> packageIoOveruseStats =
                sampleIoOveruseStats(/* requireRecurrentOveruseStats= */ true);

        pushLatestIoOveruseStatsAndWait(packageIoOveruseStats);

        verifyNoDisabledPackages();

        mWatchdogPerfHandler.setKillablePackageAsUser(
                "vendor_package.non_critical", new UserHandle(100), /* isKillable= */ false);
        mWatchdogPerfHandler.setKillablePackageAsUser(
                "third_party_package.A", new UserHandle(101), /* isKillable= */ false);

        setRequiresDistractionOptimization(false);
        mWatchdogPerfHandler.onDisplayStateChanged(/* isEnabled= */ false);

        captureAndVerifyIoOveruseStatsReported(sampleReportedOveruseStats());

        captureAndVerifyKillStatsReported(sampleReportedKillStats(
                CAR_WATCHDOG_KILL_STATS_REPORTED__SYSTEM_STATE__USER_NO_INTERACTION_MODE,
                /* killedUids= */ new int[]{10110004, 10010005}));

        verifyDisabledPackages(/* userPackagesCsv= */ "101:vendor_package.non_critical,"
                + "100:third_party_package.A,100:third_party_package.B");
    }

    @Test
    public void testDisableRecurrentlyOverusingPriorityResettedApp() throws Exception {
        setUpSampleUserAndPackages();
        setRequiresDistractionOptimization(true);
        mWatchdogPerfHandler.onDisplayStateChanged(/* isEnabled= */ true);

        mWatchdogPerfHandler.setKillablePackageAsUser(
                "vendor_package.non_critical", new UserHandle(100), /* isKillable= */ false);

        List<PackageIoOveruseStats> packageIoOveruseStats =
                sampleIoOveruseStats(/* requireRecurrentOveruseStats= */ true);

        pushLatestIoOveruseStatsAndWait(packageIoOveruseStats);

        verifyNoDisabledPackages();

        mWatchdogPerfHandler.setKillablePackageAsUser(
                "vendor_package.non_critical", new UserHandle(100), /* isKillable= */ true);

        setRequiresDistractionOptimization(false);
        mWatchdogPerfHandler.onDisplayStateChanged(/* isEnabled= */ false);

        captureAndVerifyIoOveruseStatsReported(sampleReportedOveruseStats());

        captureAndVerifyKillStatsReported(sampleReportedKillStats(
                CAR_WATCHDOG_KILL_STATS_REPORTED__SYSTEM_STATE__USER_NO_INTERACTION_MODE,
                /* killedUids= */ new int[]{10010004, 10110004, 10010005, 10110005}));

        verifyDisabledPackages(/* userPackagesCsv= */ "100:vendor_package.non_critical,"
                + "101:vendor_package.non_critical,100:third_party_package.A,"
                + "101:third_party_package.A,100:third_party_package.B,"
                + "101:third_party_package.B");
    }

    @Test
    public void testImmediateDisableRecurrentlyOverusingAppDuringGarageMode()
            throws Exception {
        setUpSampleUserAndPackages();
        setRequiresDistractionOptimization(false);
        mWatchdogPerfHandler.onDisplayStateChanged(/* isEnabled= */ false);
        mWatchdogPerfHandler.onGarageModeChange(GarageMode.GARAGE_MODE_ON);

        List<PackageIoOveruseStats> packageIoOveruseStats =
                sampleIoOveruseStats(/* requireRecurrentOveruseStats= */ true);

        pushLatestIoOveruseStatsAndWait(packageIoOveruseStats);

        captureAndVerifyIoOveruseStatsReported(sampleReportedOveruseStats());

        captureAndVerifyKillStatsReported(sampleReportedKillStats(
                CAR_WATCHDOG_KILL_STATS_REPORTED__SYSTEM_STATE__GARAGE_MODE,
                /* killedUids= */ new int[]{10010004, 10110004, 10010005, 10110005}));

        verifyDisabledPackages(/* userPackagesCsv= */ "100:vendor_package.non_critical,"
                + "101:vendor_package.non_critical,100:third_party_package.A,"
                + "101:third_party_package.A,100:third_party_package.B,"
                + "101:third_party_package.B");
    }

    @Test
    public void testDisableHistoricalRecurrentlyOverusingApp() throws Exception {
        doReturn(Arrays.asList(new WatchdogStorage.NotForgivenOverusesEntry(100,
                "third_party_package", 2))).when(mSpiedWatchdogStorage)
                .getNotForgivenHistoricalIoOveruses(RECURRING_OVERUSE_PERIOD_IN_DAYS);

        // Force CarWatchdogService to fetch historical not forgiven overuses.
        restartService(/* totalRestarts= */ 1, /* wantedDbWrites= */ 0);
        setRequiresDistractionOptimization(true);
        mWatchdogPerfHandler.onDisplayStateChanged(/* isEnabled= */ false);
        int thirdPartyPkgUid = UserHandle.getUid(100, 10005);

        injectPackageInfos(Collections.singletonList(constructPackageManagerPackageInfo(
                "third_party_package", thirdPartyPkgUid, null)));

        pushLatestIoOveruseStatsAndWait(
                sampleIoOveruseStats(/* requireRecurrentOveruseStats= */ false));

        // Third party package is disabled given the two historical overuses and one current
        // overuse.
        verifyDisabledPackages(/* message= */ "after recurring overuse with history",
                /* userPackagesCsv= */ "100:third_party_package");

        // Package was enabled again.
        doReturn(COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED).when(mSpiedPackageManager)
                .getApplicationEnabledSetting("third_party_package", 100);
        enableUserPackage("third_party_package", 100, true);

        PackageIoOveruseStats packageIoOveruseStats =
                constructPackageIoOveruseStats(thirdPartyPkgUid, /* shouldNotify= */ true,
                        /* forgivenWriteBytes= */ constructPerStateBytes(200, 400, 600),
                        constructInternalIoOveruseStats(/* killableOnOveruse= */ true,
                                /* remainingWriteBytes= */ constructPerStateBytes(0, 0, 0),
                                /* writtenBytes= */ constructPerStateBytes(200, 400, 600),
                                /* totalOveruses= */ 3));

        pushLatestIoOveruseStatsAndWait(Collections.singletonList(packageIoOveruseStats));

        // From the 3 total overuses, one overuse was forgiven previously.
        verifyNoDisabledPackages(/* message= */ "after non-recurring overuse");

        // Add one overuse.
        packageIoOveruseStats.ioOveruseStats.totalOveruses = 4;

        pushLatestIoOveruseStatsAndWait(Collections.singletonList(packageIoOveruseStats));

        // Third party package is disabled again given the three current overuses. From the 4 total
        // overuses, one overuse was forgiven previously.
        verifyDisabledPackages(/* message= */ "after recurring overuse from the same day",
                /* userPackagesCsv= */ "100:third_party_package");

        // Force write to database
        restartService(/* totalRestarts= */ 2, /* wantedDbWrites= */ 1);

        verify(mSpiedWatchdogStorage).forgiveHistoricalOveruses(mPackagesByUserIdCaptor.capture(),
                eq(RECURRING_OVERUSE_PERIOD_IN_DAYS));

        assertWithMessage("Forgiven packages")
                .that(mPackagesByUserIdCaptor.getValue().get(100))
                .containsExactlyElementsIn(Arrays.asList("third_party_package"));
    }

    @Test
    public void testDisableHistoricalRecurrentlyOverusingAppAfterDateChange() throws Exception {
        doReturn(Arrays.asList(new WatchdogStorage.NotForgivenOverusesEntry(100,
                "third_party_package", 2))).when(mSpiedWatchdogStorage)
                .getNotForgivenHistoricalIoOveruses(RECURRING_OVERUSE_PERIOD_IN_DAYS);

        mTimeSource.updateNow(/* numDaysAgo= */ 1);
        setRequiresDistractionOptimization(true);
        mWatchdogPerfHandler.onDisplayStateChanged(/* isEnabled= */ false);
        int thirdPartyPkgUid = UserHandle.getUid(100, 10005);

        injectPackageInfos(Collections.singletonList(constructPackageManagerPackageInfo(
                "third_party_package", thirdPartyPkgUid, /* sharedUserId */ null)));

        List<PackageIoOveruseStats> ioOveruseStats =
                sampleIoOveruseStats(/* requireRecurrentOveruseStats= */ false);
        pushLatestIoOveruseStatsAndWait(ioOveruseStats);

        // Third party package is disabled given the two historical overuses and one current
        // overuse.
        verifyDisabledPackages(/* userPackagesCsv= */ "100:third_party_package");

        // Force write to database by pushing non-overusing I/O overuse stats.
        mTimeSource.updateNow(/* numDaysAgo= */ 0);
        pushLatestIoOveruseStatsAndWait(Collections.singletonList(ioOveruseStats.get(0)));

        verify(mSpiedWatchdogStorage).forgiveHistoricalOveruses(mPackagesByUserIdCaptor.capture(),
                eq(RECURRING_OVERUSE_PERIOD_IN_DAYS));

        assertWithMessage("Forgiven packages")
                .that(mPackagesByUserIdCaptor.getValue().get(100))
                .containsExactlyElementsIn(Arrays.asList("third_party_package"));
    }

    @Test
    public void testLatestIoOveruseStats() throws Exception {
        setRequiresDistractionOptimization(/* isRequires= */ true);
        mWatchdogPerfHandler.onDisplayStateChanged(/* isEnabled= */ false);
        int criticalSysPkgUid = Binder.getCallingUid();
        int nonCriticalSysPkgUid = 10001056;
        int nonCriticalVndrPkgUid = 10002564;
        int thirdPartyPkgUid = 10002044;

        injectPackageInfos(Arrays.asList(
                constructPackageManagerPackageInfo(
                        "system_package.critical", criticalSysPkgUid, null),
                constructPackageManagerPackageInfo(
                        "system_package.non_critical", nonCriticalSysPkgUid, null),
                constructPackageManagerPackageInfo(
                        "vendor_package.non_critical", nonCriticalVndrPkgUid, null),
                constructPackageManagerPackageInfo(
                        "third_party_package", thirdPartyPkgUid, null)));

        IResourceOveruseListener mockSystemListener = createMockResourceOveruseListener();
        mWatchdogPerfHandler.addResourceOveruseListenerForSystem(
                FLAG_RESOURCE_OVERUSE_IO, mockSystemListener);

        IResourceOveruseListener mockListener = createMockResourceOveruseListener();
        mWatchdogPerfHandler.addResourceOveruseListener(
                FLAG_RESOURCE_OVERUSE_IO, mockListener);

        List<PackageIoOveruseStats> packageIoOveruseStats = Arrays.asList(
                /* Overuse occurred but cannot be killed/disabled. */
                constructPackageIoOveruseStats(criticalSysPkgUid, /* shouldNotify= */ true,
                        /* forgivenWriteBytes= */ constructPerStateBytes(100, 200, 300),
                        constructInternalIoOveruseStats(/* killableOnOveruse= */ false,
                                /* remainingWriteBytes= */ constructPerStateBytes(0, 0, 0),
                                /* writtenBytes= */ constructPerStateBytes(100, 200, 300),
                                /* totalOveruses= */ 3)),
                /* No overuse occurred but should be notified. */
                constructPackageIoOveruseStats(nonCriticalSysPkgUid, /* shouldNotify= */ true,
                        /* forgivenWriteBytes= */ constructPerStateBytes(50, 100, 150),
                        constructInternalIoOveruseStats(/* killableOnOveruse= */ true,
                                /* remainingWriteBytes= */ constructPerStateBytes(20, 30, 40),
                                /* writtenBytes= */ constructPerStateBytes(100, 200, 300),
                                /* totalOveruses= */ 3)),
                /* Neither overuse occurred nor be notified. */
                constructPackageIoOveruseStats(nonCriticalVndrPkgUid, /* shouldNotify= */ false,
                        /* forgivenWriteBytes= */ constructPerStateBytes(25, 50, 75),
                        constructInternalIoOveruseStats(/* killableOnOveruse= */ true,
                                /* remainingWriteBytes= */ constructPerStateBytes(200, 300, 400),
                                /* writtenBytes= */ constructPerStateBytes(100, 200, 300),
                                /* totalOveruses= */ 3)),
                /* Overuse occurred and can be killed/disabled. */
                constructPackageIoOveruseStats(thirdPartyPkgUid, /* shouldNotify= */ true,
                        /* forgivenWriteBytes= */ constructPerStateBytes(100, 200, 300),
                        constructInternalIoOveruseStats(/* killableOnOveruse= */ true,
                                /* remainingWriteBytes= */ constructPerStateBytes(0, 0, 0),
                                /* writtenBytes= */ constructPerStateBytes(300, 600, 900),
                                /* totalOveruses= */ 3)));

        pushLatestIoOveruseStatsAndWait(packageIoOveruseStats);

        verifyDisabledPackages(/* userPackagesCsv= */ "100:third_party_package");

        List<ResourceOveruseStats> expectedStats = new ArrayList<>();

        expectedStats.add(constructResourceOveruseStats(criticalSysPkgUid,
                "system_package.critical", packageIoOveruseStats.get(0).ioOveruseStats));

        verifyOnOveruseCalled(expectedStats, mockListener);

        expectedStats.add(constructResourceOveruseStats(nonCriticalSysPkgUid,
                "system_package.non_critical", packageIoOveruseStats.get(1).ioOveruseStats));

        /*
         * When the package receives overuse notification, the package is not yet killed so the
         * totalTimesKilled counter is not yet incremented.
         */
        expectedStats.add(constructResourceOveruseStats(thirdPartyPkgUid, "third_party_package",
                packageIoOveruseStats.get(3).ioOveruseStats));

        verifyOnOveruseCalled(expectedStats, mockSystemListener);

        List<AtomsProto.CarWatchdogIoOveruseStatsReported> expectedReportedOveruseStats =
                new ArrayList<>();
        expectedReportedOveruseStats.add(constructIoOveruseStatsReported(criticalSysPkgUid,
                WatchdogPerfHandler.constructCarWatchdogPerStateBytes(10, 20, 30),
                WatchdogPerfHandler.constructCarWatchdogPerStateBytes(100, 200, 300)));
        expectedReportedOveruseStats.add(constructIoOveruseStatsReported(thirdPartyPkgUid,
                WatchdogPerfHandler.constructCarWatchdogPerStateBytes(30, 60, 90),
                WatchdogPerfHandler.constructCarWatchdogPerStateBytes(300, 600, 900)));

        captureAndVerifyIoOveruseStatsReported(expectedReportedOveruseStats);

        List<AtomsProto.CarWatchdogKillStatsReported> expectedReportedKillStats =
                Collections.singletonList(constructIoOveruseKillStatsReported(thirdPartyPkgUid,
                        CAR_WATCHDOG_KILL_STATS_REPORTED__SYSTEM_STATE__USER_NO_INTERACTION_MODE,
                        WatchdogPerfHandler.constructCarWatchdogPerStateBytes(30, 60, 90),
                        WatchdogPerfHandler.constructCarWatchdogPerStateBytes(300, 600, 900)));

        captureAndVerifyKillStatsReported(expectedReportedKillStats);
    }

    @Test
    public void testLatestIoOveruseStatsWithSharedUid() throws Exception {
        setRequiresDistractionOptimization(/* isRequires= */ true);
        mWatchdogPerfHandler.onDisplayStateChanged(/* isEnabled= */ false);
        int criticalSysSharedUid = Binder.getCallingUid();
        int nonCriticalVndrSharedUid = 10002564;
        int thirdPartySharedUid = 10002044;

        injectPackageInfos(Arrays.asList(
                constructPackageManagerPackageInfo(
                        "system_package.A", criticalSysSharedUid, "system_shared_package"),
                constructPackageManagerPackageInfo(
                        "system_package.B", criticalSysSharedUid, "system_shared_package"),
                constructPackageManagerPackageInfo("vendor_package.non_critical",
                        nonCriticalVndrSharedUid, "vendor_shared_package"),
                constructPackageManagerPackageInfo(
                        "third_party_package.A", thirdPartySharedUid, "third_party_shared_package"),
                constructPackageManagerPackageInfo(
                        "third_party_package.B", thirdPartySharedUid, "third_party_shared_package")
        ));

        IResourceOveruseListener mockSystemListener = createMockResourceOveruseListener();
        mWatchdogPerfHandler.addResourceOveruseListenerForSystem(
                FLAG_RESOURCE_OVERUSE_IO, mockSystemListener);

        IResourceOveruseListener mockListener = createMockResourceOveruseListener();
        mWatchdogPerfHandler.addResourceOveruseListener(
                FLAG_RESOURCE_OVERUSE_IO, mockListener);

        List<PackageIoOveruseStats> packageIoOveruseStats = Arrays.asList(
                /* Overuse occurred but cannot be killed/disabled. */
                constructPackageIoOveruseStats(criticalSysSharedUid, /* shouldNotify= */ true,
                        /* forgivenWriteBytes= */ constructPerStateBytes(100, 200, 300),
                        constructInternalIoOveruseStats(/* killableOnOveruse= */ false,
                                /* remainingWriteBytes= */ constructPerStateBytes(0, 0, 0),
                                /* writtenBytes= */ constructPerStateBytes(100, 200, 300),
                                /* totalOveruses= */ 3)),
                /* No overuse occurred but should be notified. */
                constructPackageIoOveruseStats(nonCriticalVndrSharedUid, /* shouldNotify= */ true,
                        /* forgivenWriteBytes= */ constructPerStateBytes(50, 100, 150),
                        constructInternalIoOveruseStats(/* killableOnOveruse= */ true,
                                /* remainingWriteBytes= */ constructPerStateBytes(200, 300, 400),
                                /* writtenBytes= */ constructPerStateBytes(100, 200, 300),
                                /* totalOveruses= */ 3)),
                /* Overuse occurred and can be killed/disabled. */
                constructPackageIoOveruseStats(thirdPartySharedUid, /* shouldNotify= */ true,
                        /* forgivenWriteBytes= */ constructPerStateBytes(100, 200, 300),
                        constructInternalIoOveruseStats(/* killableOnOveruse= */ true,
                                /* remainingWriteBytes= */ constructPerStateBytes(0, 0, 0),
                                /* writtenBytes= */ constructPerStateBytes(300, 600, 900),
                                /* totalOveruses= */ 3)));

        pushLatestIoOveruseStatsAndWait(packageIoOveruseStats);

        verifyDisabledPackages(
                /* userPackagesCsv= */ "100:third_party_package.A,100:third_party_package.B");

        List<ResourceOveruseStats> expectedStats = new ArrayList<>();

        expectedStats.add(constructResourceOveruseStats(criticalSysSharedUid,
                "shared:system_shared_package", packageIoOveruseStats.get(0).ioOveruseStats));

        verifyOnOveruseCalled(expectedStats, mockListener);

        expectedStats.add(constructResourceOveruseStats(nonCriticalVndrSharedUid,
                "shared:vendor_shared_package", packageIoOveruseStats.get(1).ioOveruseStats));

        /*
         * When the package receives overuse notification, the package is not yet killed so the
         * totalTimesKilled counter is not yet incremented.
         */
        expectedStats.add(constructResourceOveruseStats(thirdPartySharedUid,
                "shared:third_party_shared_package", packageIoOveruseStats.get(2).ioOveruseStats));

        verifyOnOveruseCalled(expectedStats, mockSystemListener);

        List<AtomsProto.CarWatchdogIoOveruseStatsReported> expectedReportedOveruseStats =
                new ArrayList<>();
        expectedReportedOveruseStats.add(constructIoOveruseStatsReported(criticalSysSharedUid,
                WatchdogPerfHandler.constructCarWatchdogPerStateBytes(10, 20, 30),
                WatchdogPerfHandler.constructCarWatchdogPerStateBytes(100, 200, 300)));
        expectedReportedOveruseStats.add(constructIoOveruseStatsReported(thirdPartySharedUid,
                WatchdogPerfHandler.constructCarWatchdogPerStateBytes(30, 60, 90),
                WatchdogPerfHandler.constructCarWatchdogPerStateBytes(300, 600, 900)));

        captureAndVerifyIoOveruseStatsReported(expectedReportedOveruseStats);

        List<AtomsProto.CarWatchdogKillStatsReported> expectedReportedKillStats =
                Collections.singletonList(constructIoOveruseKillStatsReported(thirdPartySharedUid,
                        CAR_WATCHDOG_KILL_STATS_REPORTED__SYSTEM_STATE__USER_NO_INTERACTION_MODE,
                        WatchdogPerfHandler.constructCarWatchdogPerStateBytes(30, 60, 90),
                        WatchdogPerfHandler.constructCarWatchdogPerStateBytes(300, 600, 900)));

        captureAndVerifyKillStatsReported(expectedReportedKillStats);
    }

    @Test
    public void testPersistStatsOnRestart() throws Exception {
        mockUmGetUserHandles(mMockUserManager, /* excludeDying= */ true, 100, 101, 102);
        injectPackageInfos(Arrays.asList(
                constructPackageManagerPackageInfo(
                        "third_party_package", 10103456, "vendor_shared_package.critical"),
                constructPackageManagerPackageInfo(
                        "vendor_package", 10103456, "vendor_shared_package.critical"),
                constructPackageManagerPackageInfo("third_party_package.A", 10001100, null),
                constructPackageManagerPackageInfo("third_party_package.A", 10201100, null)));

        SparseArray<PackageIoOveruseStats> packageIoOveruseStatsByUid =
                injectIoOveruseStatsForPackages(
                        mGenericPackageNameByUid,
                        /* killablePackages= */ new ArraySet<>(Collections.singletonList(
                                "third_party_package.A")),
                        /* shouldNotifyPackages= */ new ArraySet<>());

        mWatchdogPerfHandler.setKillablePackageAsUser(
                "third_party_package.A", UserHandle.of(102), /* isKillable= */ false);

        restartService(/* totalRestarts= */ 1, /* wantedDbWrites= */ 1);

        List<ResourceOveruseStats> actualStats = mWatchdogPerfHandler.getAllResourceOveruseStats(
                FLAG_RESOURCE_OVERUSE_IO, /* minimumStatsFlag= */ 0,
                CarWatchdogManager.STATS_PERIOD_CURRENT_DAY);

        List<ResourceOveruseStats> expectedStats = Arrays.asList(
                constructResourceOveruseStats(
                        /* uid= */ 10103456, "shared:vendor_shared_package.critical",
                        packageIoOveruseStatsByUid.get(10103456).ioOveruseStats),
                constructResourceOveruseStats(/* uid= */ 10001100, "third_party_package.A",
                        packageIoOveruseStatsByUid.get(10001100).ioOveruseStats),
                constructResourceOveruseStats(/* uid= */ 10201100, "third_party_package.A",
                        packageIoOveruseStatsByUid.get(10201100).ioOveruseStats));

        PackageKillableStateSubject.assertThat(
                        mWatchdogPerfHandler.getPackageKillableStatesAsUser(UserHandle.ALL))
                .containsExactly(
                        new PackageKillableState("third_party_package", 101,
                                PackageKillableState.KILLABLE_STATE_NEVER),
                        new PackageKillableState("vendor_package", 101,
                                PackageKillableState.KILLABLE_STATE_NEVER),
                        new PackageKillableState("third_party_package.A", 100,
                                PackageKillableState.KILLABLE_STATE_YES),
                        new PackageKillableState("third_party_package.A", 102,
                                PackageKillableState.KILLABLE_STATE_NO));

        // Changing and getting package killable states marks the database as dirty
        verify(mSpiedWatchdogStorage, times(5)).markDirty();

        ResourceOveruseStatsSubject.assertThat(actualStats)
                .containsExactlyElementsIn(expectedStats);

        verifyNoMoreInteractions(mSpiedWatchdogStorage);
    }

    @Test
    public void testWriteToDbOnDateChange() throws Exception {
        mockUmGetUserHandles(mMockUserManager, /* excludeDying= */ true, 100);
        injectPackageInfos(Arrays.asList(
                constructPackageManagerPackageInfo("system_package", 10011200, null),
                constructPackageManagerPackageInfo("third_party_package", 10001100, null)));

        mWatchdogPerfHandler.onDisplayStateChanged(/* isEnabled= */ false);
        mTimeSource.updateNow(/* numDaysAgo= */ 1);
        List<PackageIoOveruseStats> prevDayStats = Arrays.asList(
                constructPackageIoOveruseStats(10011200, /* shouldNotify= */ false,
                        /* forgivenWriteBytes= */ constructPerStateBytes(600, 700, 800),
                        constructInternalIoOveruseStats(/* killableOnOveruse= */ true,
                                /* remainingWriteBytes= */ constructPerStateBytes(0, 0, 0),
                                /* writtenBytes= */ constructPerStateBytes(600, 700, 800),
                                /* totalOveruses= */ 3)),
                constructPackageIoOveruseStats(10001100, /* shouldNotify= */ false,
                        /* forgivenWriteBytes= */ constructPerStateBytes(1050, 1100, 1200),
                        constructInternalIoOveruseStats(/* killableOnOveruse= */ true,
                                /* remainingWriteBytes= */ constructPerStateBytes(50, 60, 70),
                                /* writtenBytes= */ constructPerStateBytes(1100, 1200, 1300),
                                /* totalOveruses= */ 5)));
        pushLatestIoOveruseStatsAndWait(prevDayStats);

        List<WatchdogStorage.UserPackageSettingsEntry> expectedSavedUserPackageEntries =
                Arrays.asList(
                        new WatchdogStorage.UserPackageSettingsEntry(/* userId= */ 100,
                                "system_package",
                                /* killableState= */ PackageKillableState.KILLABLE_STATE_YES,
                                /* lastModifiedKillableStateEpoch= */ 123456789),
                        new WatchdogStorage.UserPackageSettingsEntry(/* userId= */ 100,
                                "third_party_package",
                                /* killableState= */ PackageKillableState.KILLABLE_STATE_YES,
                                /* lastModifiedKillableStateEpoch= */ 123456789));

        List<WatchdogStorage.IoUsageStatsEntry> expectedSavedIoUsageEntries = Arrays.asList(
                new WatchdogStorage.IoUsageStatsEntry(/* userId= */ 100, "system_package",
                        new WatchdogPerfHandler.PackageIoUsage(prevDayStats.get(0).ioOveruseStats,
                                /* forgivenWriteBytes= */ constructPerStateBytes(600, 700, 800),
                                /* forgivenOveruses= */ 3, /* totalTimesKilled= */ 1)),
                new WatchdogStorage.IoUsageStatsEntry(/* userId= */ 100, "third_party_package",
                        new WatchdogPerfHandler.PackageIoUsage(prevDayStats.get(1).ioOveruseStats,
                                /* forgivenWriteBytes= */ constructPerStateBytes(1050, 1100, 1200),
                                /* forgivenOveruses= */ 0, /* totalTimesKilled= */ 0)));

        mWatchdogPerfHandler.onDisplayStateChanged(/* isEnabled= */ true);
        mTimeSource.updateNow(/* numDaysAgo= */ 0);
        List<PackageIoOveruseStats> currentDayStats = Arrays.asList(
                constructPackageIoOveruseStats(10011200, /* shouldNotify= */ false,
                        /* forgivenWriteBytes= */ constructPerStateBytes(0, 0, 0),
                        constructInternalIoOveruseStats(/* killableOnOveruse= */ true,
                                /* remainingWriteBytes= */ constructPerStateBytes(500, 550, 600),
                                /* writtenBytes= */ constructPerStateBytes(100, 150, 200),
                                /* totalOveruses= */ 0)),
                constructPackageIoOveruseStats(10001100, /* shouldNotify= */ false,
                        /* forgivenWriteBytes= */ constructPerStateBytes(0, 0, 0),
                        constructInternalIoOveruseStats(/* killableOnOveruse= */ true,
                                /* remainingWriteBytes= */ constructPerStateBytes(250, 360, 470),
                                /* writtenBytes= */ constructPerStateBytes(900, 900, 900),
                                /* totalOveruses= */ 0)));
        pushLatestIoOveruseStatsAndWait(currentDayStats);

        assertWithMessage("Saved user package setting entries")
                .that(mUserPackageSettingsEntries)
                .containsExactlyElementsIn(expectedSavedUserPackageEntries);

        IoUsageStatsEntrySubject.assertThat(mIoUsageStatsEntries)
                .containsExactlyElementsIn(expectedSavedIoUsageEntries);

        List<ResourceOveruseStats> actualCurrentDayStats =
                mWatchdogPerfHandler.getAllResourceOveruseStats(
                        FLAG_RESOURCE_OVERUSE_IO, /* minimumStatsFlag= */ 0,
                        CarWatchdogManager.STATS_PERIOD_CURRENT_DAY);

        List<ResourceOveruseStats> expectedCurrentDayStats = Arrays.asList(
                constructResourceOveruseStats(/* uid= */ 10011200, "system_package",
                        currentDayStats.get(0).ioOveruseStats),
                constructResourceOveruseStats(/* uid= */ 10001100, "third_party_package",
                        currentDayStats.get(1).ioOveruseStats));

        ResourceOveruseStatsSubject.assertThat(actualCurrentDayStats)
                .containsExactlyElementsIn(expectedCurrentDayStats);
    }

    @Test
    public void testNoWriteToDbOnDateChangeWithNoStats() throws Exception {
        mTimeSource.updateNow(/* numDaysAgo= */ 1);

        // Since no I/O overuse stats where sent by watchdog daemon, no stats are written to
        // database.
        restartService(/* totalRestarts= */ 1, /* wantedDbWrites= */ 0);

        mTimeSource.updateNow(/* numDaysAgo= */ 0);
        pushLatestIoOveruseStatsAndWait(
                sampleIoOveruseStats(/* requireRecurrentOveruseStats= */ false));

        verify(mSpiedWatchdogStorage, never()).saveUserPackageSettings(any());
        verify(mSpiedWatchdogStorage, never()).saveIoUsageStats(any());
        verify(mSpiedWatchdogStorage, never()).forgiveHistoricalOveruses(any(), anyInt());
    }

    @Test
    public void testNoUserNotificationWithNoRecurrentOveruse() throws Exception {
        mockAmGetCurrentUser(CURRENT_USER_ID);
        mWatchdogPerfHandler.onDisplayStateChanged(/* isEnabled= */ true);
        setRequiresDistractionOptimization(false);

        setUpSampleUserAndPackages();

        pushLatestIoOveruseStatsAndWait(
                sampleIoOveruseStats(/* requireRecurrentOveruseStats= */ false));

        // Verify no notification is sent
        captureAndVerifyUserNotifications(Collections.emptyList());
    }

    @Test
    public void testNoUserNotificationOnRecurrentOveruseWithDistractionOptimization()
            throws Exception {
        mockAmGetCurrentUser(CURRENT_USER_ID);
        mWatchdogPerfHandler.onDisplayStateChanged(/* isEnabled= */ true);
        setRequiresDistractionOptimization(true);

        setUpSampleUserAndPackages();

        pushLatestIoOveruseStatsAndWait(
                sampleIoOveruseStats(/* requireRecurrentOveruseStats= */ true));

        // Verify no notification is sent
        captureAndVerifyUserNotifications(Collections.emptyList());
    }

    @Test
    public void testUserNotificationOnRecurrentOveruseAfterNoDistractionOptimization()
            throws Exception {
        mockAmGetCurrentUser(CURRENT_USER_ID);
        mWatchdogPerfHandler.onDisplayStateChanged(/* isEnabled= */ true);
        setRequiresDistractionOptimization(true);

        setUpSampleUserAndPackages();

        pushLatestIoOveruseStatsAndWait(
                sampleIoOveruseStats(/* requireRecurrentOveruseStats= */ true));

        setRequiresDistractionOptimization(false);

        captureAndVerifyUserNotifications(
                Collections.singletonList(
                        new UserNotificationReflectionCall(
                                UserHandle.of(100),
                                constructPackagesByNotificationId(/* idOffset= */ 0,
                                        "vendor_package.non_critical", "third_party_package.A",
                                        "third_party_package.B"), /* hasHeadsUpNotification= */
                                true)));
    }

    @Test
    public void testNoDuplicateUserNotificationOnRepeatedRecurrentOveruse()
            throws Exception {
        mockAmGetCurrentUser(CURRENT_USER_ID);
        mWatchdogPerfHandler.onDisplayStateChanged(/* isEnabled= */ true);
        setRequiresDistractionOptimization(false);

        setUpSampleUserAndPackages();

        List<PackageIoOveruseStats> ioOveruseStats =
                sampleIoOveruseStats(/* requireRecurrentOveruseStats= */ true);

        pushLatestIoOveruseStatsAndWait(ioOveruseStats);
        // Should not produce resource overuse notifications.
        pushLatestIoOveruseStatsAndWait(ioOveruseStats);

        captureAndVerifyUserNotifications(
                Collections.singletonList(
                        new UserNotificationReflectionCall(
                                UserHandle.of(100),
                                constructPackagesByNotificationId(/* idOffset= */ 0,
                                        "vendor_package.non_critical", "third_party_package.A",
                                        "third_party_package.B"), /* hasHeadsUpNotification= */
                                true)));
    }

    @Test
    public void testImmediateUserNotificationOnRecurrentOveruseWhenNoDistractionOptimization()
            throws Exception {
        mockAmGetCurrentUser(CURRENT_USER_ID);
        mWatchdogPerfHandler.onDisplayStateChanged(/* isEnabled= */ true);
        setRequiresDistractionOptimization(false);

        setUpSampleUserAndPackages();

        pushLatestIoOveruseStatsAndWait(
                sampleIoOveruseStats(/* requireRecurrentOveruseStats= */ true));

        pushLatestIoOveruseStatsAndWait(Collections.singletonList(
                constructPackageIoOveruseStats(/* uid= */ 10010002, /* shouldNotify= */ true,
                        /* forgivenWriteBytes= */ constructPerStateBytes(100, 200, 300),
                        constructInternalIoOveruseStats(/* killableOnOveruse= */ true,
                                /* remainingWriteBytes= */ constructPerStateBytes(0, 0, 0),
                                /* writtenBytes= */ constructPerStateBytes(300, 600, 900),
                                /* totalOveruses= */ 3))));

        List<UserNotificationReflectionCall>
                userNotificationReflectionCalls = Arrays.asList(
                new UserNotificationReflectionCall(UserHandle.of(100),
                        constructPackagesByNotificationId(/* idOffset= */ 0,
                                "vendor_package.non_critical", "third_party_package.A",
                                "third_party_package.B"), /* hasHeadsUpNotification= */ true),
                new UserNotificationReflectionCall(UserHandle.of(100),
                        constructPackagesByNotificationId(/* idOffset= */ 3,
                                "system_package.non_critical"),
                        /* hasHeadsUpNotification= */ false));

        captureAndVerifyUserNotifications(userNotificationReflectionCalls);
    }

    @Test
    public void testNoUserNotificationOnRecurrentOveruseByPrePrioritizedApp() throws Exception {
        mockAmGetCurrentUser(CURRENT_USER_ID);
        mWatchdogPerfHandler.onDisplayStateChanged(/* isEnabled= */ true);
        setRequiresDistractionOptimization(true);

        setUpSampleUserAndPackages();

        mWatchdogPerfHandler.setKillablePackageAsUser("third_party_package.A",
                UserHandle.of(100), /* isKillable= */ false);

        pushLatestIoOveruseStatsAndWait(Collections.singletonList(
                constructPackageIoOveruseStats(/* uid= */ 10010005, /* shouldNotify= */ true,
                        /* forgivenWriteBytes= */ constructPerStateBytes(100, 200, 300),
                        constructInternalIoOveruseStats(/* killableOnOveruse= */ true,
                                /* remainingWriteBytes= */ constructPerStateBytes(0, 0, 0),
                                /* writtenBytes= */ constructPerStateBytes(300, 600, 900),
                                /* totalOveruses= */ 3))));

        setRequiresDistractionOptimization(false);

        // Verify no notification is sent
        captureAndVerifyUserNotifications(Collections.emptyList());
    }

    @Test
    public void testNoUserNotificationOnRecurrentOveruseByPostPrioritizedApp() throws Exception {
        mockAmGetCurrentUser(CURRENT_USER_ID);
        mWatchdogPerfHandler.onDisplayStateChanged(/* isEnabled= */ true);
        setRequiresDistractionOptimization(true);

        setUpSampleUserAndPackages();

        pushLatestIoOveruseStatsAndWait(Collections.singletonList(
                constructPackageIoOveruseStats(/* uid= */ 10010005, /* shouldNotify= */ true,
                        /* forgivenWriteBytes= */ constructPerStateBytes(100, 200, 300),
                        constructInternalIoOveruseStats(/* killableOnOveruse= */ true,
                                /* remainingWriteBytes= */ constructPerStateBytes(0, 0, 0),
                                /* writtenBytes= */ constructPerStateBytes(300, 600, 900),
                                /* totalOveruses= */ 3))));

        mWatchdogPerfHandler.setKillablePackageAsUser("third_party_package.A",
                UserHandle.of(100), /* isKillable= */ false);

        setRequiresDistractionOptimization(false);

        // Verify no notification is sent
        captureAndVerifyUserNotifications(Collections.emptyList());
    }

    @Test
    public void testUserNotificationOnRecurrentOveruseByPriorityResettedApp() throws Exception {
        mockAmGetCurrentUser(CURRENT_USER_ID);
        mWatchdogPerfHandler.onDisplayStateChanged(/* isEnabled= */ true);
        setRequiresDistractionOptimization(true);

        setUpSampleUserAndPackages();

        mWatchdogPerfHandler.setKillablePackageAsUser("third_party_package.A", UserHandle.of(100),
                /* isKillable= */ false);

        pushLatestIoOveruseStatsAndWait(Collections.singletonList(
                constructPackageIoOveruseStats(/* uid= */ 10010005, /* shouldNotify= */ true,
                        /* forgivenWriteBytes= */ constructPerStateBytes(100, 200, 300),
                        constructInternalIoOveruseStats(/* killableOnOveruse= */ true,
                                /* remainingWriteBytes= */ constructPerStateBytes(0, 0, 0),
                                /* writtenBytes= */ constructPerStateBytes(300, 600, 900),
                                /* totalOveruses= */ 3))));

        mWatchdogPerfHandler.setKillablePackageAsUser("third_party_package.A", UserHandle.of(100),
                /* isKillable= */ true);

        setRequiresDistractionOptimization(false);

        captureAndVerifyUserNotifications(Collections.singletonList(
                new UserNotificationReflectionCall(UserHandle.of(100),
                        constructPackagesByNotificationId(/* idOffset= */ 0,
                                "third_party_package.A", "third_party_package.B"),
                        /* hasHeadsUpNotification= */ true)));
    }

    @Test
    public void testUserNotificationOnHistoricalRecurrentOveruse() throws Exception {
        doReturn(Arrays.asList(new WatchdogStorage.NotForgivenOverusesEntry(100,
                "system_package.non_critical", 2)))
                .when(mSpiedWatchdogStorage)
                .getNotForgivenHistoricalIoOveruses(RECURRING_OVERUSE_PERIOD_IN_DAYS);

        // Force CarWatchdogService to fetch historical not forgiven overuses.
        restartService(/* totalRestarts= */ 1, /* wantedDbWrites= */ 0);
        mockAmGetCurrentUser(CURRENT_USER_ID);
        mWatchdogPerfHandler.onDisplayStateChanged(/* isEnabled= */ true);
        setRequiresDistractionOptimization(false);

        setUpSampleUserAndPackages();

        pushLatestIoOveruseStatsAndWait(Collections.singletonList(
                constructPackageIoOveruseStats(/* uid= */ 10010002, /* shouldNotify= */ true,
                        /* forgivenWriteBytes= */ constructPerStateBytes(100, 200, 300),
                        constructInternalIoOveruseStats(/* killableOnOveruse= */ true,
                                /* remainingWriteBytes= */ constructPerStateBytes(0, 0, 0),
                                /* writtenBytes= */ constructPerStateBytes(300, 600, 900),
                                /* totalOveruses= */ 1))));

        captureAndVerifyUserNotifications(Collections.singletonList(
                new UserNotificationReflectionCall(UserHandle.of(100),
                        constructPackagesByNotificationId(/* idOffset= */ 0,
                                "system_package.non_critical"),
                        /* hasHeadsUpNotification= */ true)));
    }

    @Test
    public void testUserNotificationWithDisabledDisplay() throws Exception {
        mockAmGetCurrentUser(CURRENT_USER_ID);
        mWatchdogPerfHandler.onDisplayStateChanged(/* isEnabled= */ false);
        setRequiresDistractionOptimization(false);

        setUpSampleUserAndPackages();

        pushLatestIoOveruseStatsAndWait(Collections.singletonList(
                constructPackageIoOveruseStats(/* uid= */ 10010002, /* shouldNotify= */ true,
                        /* forgivenWriteBytes= */ constructPerStateBytes(100, 200, 300),
                        constructInternalIoOveruseStats(/* killableOnOveruse= */ true,
                                /* remainingWriteBytes= */ constructPerStateBytes(0, 0, 0),
                                /* writtenBytes= */ constructPerStateBytes(300, 600, 900),
                                /* totalOveruses= */ 3))));

        captureAndVerifyUserNotifications(Collections.singletonList(
                new UserNotificationReflectionCall(UserHandle.of(100),
                        constructPackagesByNotificationId(/* idOffset= */ 0,
                                "system_package.non_critical"),
                        /* hasHeadsUpNotification= */ false)));
    }

    @Test
    public void testNoDisableWithNoRecurrentOveruse() throws Exception {
        setUpSampleUserAndPackages();
        setRequiresDistractionOptimization(false);
        mWatchdogPerfHandler.onDisplayStateChanged(/* isEnabled= */ false);

        List<PackageIoOveruseStats> packageIoOveruseStats =
                sampleIoOveruseStats(/* requireRecurrentOveruseStats= */ false);

        pushLatestIoOveruseStatsAndWait(packageIoOveruseStats);

        captureAndVerifyIoOveruseStatsReported(sampleReportedOveruseStats());

        verify(() -> CarStatsLog.write(eq(CarStatsLog.CAR_WATCHDOG_KILL_STATS_REPORTED),
                anyInt(), anyInt(), anyInt(), anyInt(), any(), any()), never());

        verifyNoDisabledPackages();
    }

    @Test
    public void testNoDisableRecurrentlyOverusingAppWithDistractionOptimization() throws Exception {
        setUpSampleUserAndPackages();
        setRequiresDistractionOptimization(true);
        mWatchdogPerfHandler.onDisplayStateChanged(/* isEnabled= */ true);

        List<PackageIoOveruseStats> packageIoOveruseStats =
                sampleIoOveruseStats(/* requireRecurrentOveruseStats= */ true);

        pushLatestIoOveruseStatsAndWait(packageIoOveruseStats);

        captureAndVerifyIoOveruseStatsReported(sampleReportedOveruseStats());

        verify(() -> CarStatsLog.write(eq(CarStatsLog.CAR_WATCHDOG_KILL_STATS_REPORTED),
                anyInt(), anyInt(), anyInt(), anyInt(), any(), any()), never());

        verifyNoDisabledPackages();
    }

    @Test
    public void testPullUidIoUsageSummaryAtomsWithDateChange() throws Exception {
        injectPackageInfos(Arrays.asList(
                constructPackageManagerPackageInfo("system_package.critical", 10010001, null),
                constructPackageManagerPackageInfo("vendor_package.non_critical", 10110004, null),
                constructPackageManagerPackageInfo("third_party_package.A", 10110005,
                        "third_party_shared_package")));

        mTimeSource.updateNow(/* numDaysAgo= */ 7);

        List<StatsEvent> events = new ArrayList<>();
        assertWithMessage("Stats pull atom callback status")
                .that(mStatsPullAtomCallback.onPullAtom(CAR_WATCHDOG_UID_IO_USAGE_SUMMARY,
                        events)).isEqualTo(PULL_SUCCESS);

        List<AtomsProto.CarWatchdogUidIoUsageSummary> expectedSummaries =
                verifyAndGetUidIoUsageSummaries(
                        mTimeSource.getCurrentDate().minus(RETENTION_PERIOD),
                        /* expectUids= */ Arrays.asList(10010001, 10110004, 10110005));

        assertWithMessage("First pulled uid I/O usage summary atoms")
                .that(mPulledUidIoUsageSummaries).containsExactlyElementsIn(expectedSummaries);
        mPulledUidIoUsageSummaries.clear();

        mTimeSource.updateNow(/* numDaysAgo= */ 6);

        assertWithMessage("Status of stats pull atom callback within the same week")
                .that(mStatsPullAtomCallback.onPullAtom(CAR_WATCHDOG_UID_IO_USAGE_SUMMARY,
                        events)).isEqualTo(PULL_SUCCESS);

        assertWithMessage("Pulled uid I/O usage summary atoms within the same week")
                .that(mPulledUidIoUsageSummaries).isEmpty();

        mTimeSource.updateNow(/* numDaysAgo= */ 0);

        assertWithMessage("Status of stats pull atom callback after a week")
                .that(mStatsPullAtomCallback.onPullAtom(CAR_WATCHDOG_UID_IO_USAGE_SUMMARY,
                        events)).isEqualTo(PULL_SUCCESS);

        expectedSummaries = verifyAndGetUidIoUsageSummaries(
                mTimeSource.getCurrentDate().minus(1, ChronoUnit.WEEKS),
                /* expectUids= */ Arrays.asList(10010001, 10110004, 10110005));

        assertWithMessage("Pulled uid I/O usage summary atoms after a week")
                .that(mPulledUidIoUsageSummaries).containsExactlyElementsIn(expectedSummaries);

        verifyNoMoreInteractions(mSpiedWatchdogStorage);
    }

    @Test
    public void testPullSystemIoUsageSummaryAtomsWithRestart() throws Exception {
        List<StatsEvent> events = new ArrayList<>();
        assertWithMessage("Stats pull atom callback status")
                .that(mStatsPullAtomCallback.onPullAtom(CAR_WATCHDOG_SYSTEM_IO_USAGE_SUMMARY,
                        events)).isEqualTo(PULL_SUCCESS);

        List<AtomsProto.CarWatchdogSystemIoUsageSummary> expectedSummaries =
                verifyAndGetSystemIoUsageSummaries(
                        mTimeSource.getCurrentDate().minus(RETENTION_PERIOD));

        assertWithMessage("First pulled system I/O usage summary atoms")
                .that(mPulledSystemIoUsageSummaries).containsExactlyElementsIn(expectedSummaries);
        mPulledSystemIoUsageSummaries.clear();

        restartService(/* totalRestarts= */ 1, /* wantedDbWrites= */ 0);

        assertWithMessage("Status of stats pull atom callback after restart")
                .that(mStatsPullAtomCallback.onPullAtom(CAR_WATCHDOG_SYSTEM_IO_USAGE_SUMMARY,
                        events)).isEqualTo(PULL_SUCCESS);

        assertWithMessage("Pulled system I/O usage summary atoms after restart")
                .that(mPulledSystemIoUsageSummaries).isEmpty();

        verifyNoMoreInteractions(mSpiedWatchdogStorage);
    }

    @Test
    public void testPullSystemIoUsageSummaryAtomsWithDateChange() throws Exception {
        mTimeSource.updateNow(/* numDaysAgo= */ 7);

        List<StatsEvent> events = new ArrayList<>();
        assertWithMessage("Stats pull atom callback status")
                .that(mStatsPullAtomCallback.onPullAtom(CAR_WATCHDOG_SYSTEM_IO_USAGE_SUMMARY,
                        events)).isEqualTo(PULL_SUCCESS);

        List<AtomsProto.CarWatchdogSystemIoUsageSummary> expectedSummaries =
                verifyAndGetSystemIoUsageSummaries(
                        mTimeSource.getCurrentDate().minus(RETENTION_PERIOD));

        assertWithMessage("First pulled system I/O usage summary atoms")
                .that(mPulledSystemIoUsageSummaries).containsExactlyElementsIn(expectedSummaries);
        mPulledSystemIoUsageSummaries.clear();

        mTimeSource.updateNow(/* numDaysAgo= */ 6);

        assertWithMessage("Status of stats pull atom callback within the same week")
                .that(mStatsPullAtomCallback.onPullAtom(CAR_WATCHDOG_SYSTEM_IO_USAGE_SUMMARY,
                        events)).isEqualTo(PULL_SUCCESS);

        assertWithMessage("Pulled system I/O usage summary atoms within the same week")
                .that(mPulledSystemIoUsageSummaries).isEmpty();

        mTimeSource.updateNow(/* numDaysAgo= */ 0);

        assertWithMessage("Status of stats pull atom callback after a week")
                .that(mStatsPullAtomCallback.onPullAtom(CAR_WATCHDOG_SYSTEM_IO_USAGE_SUMMARY,
                        events)).isEqualTo(PULL_SUCCESS);

        expectedSummaries = verifyAndGetSystemIoUsageSummaries(
                mTimeSource.getCurrentDate().minus(1, ChronoUnit.WEEKS));

        assertWithMessage("Pulled system I/O usage summary atoms after a week")
                .that(mPulledSystemIoUsageSummaries).containsExactlyElementsIn(expectedSummaries);

        verifyNoMoreInteractions(mSpiedWatchdogStorage);
    }

    @Test
    public void testPullUidIoUsageSummaryAtomsForTopUids() throws Exception {
        injectPackageInfos(Arrays.asList(
                constructPackageManagerPackageInfo("system_package.critical.A", 10000345, null),
                constructPackageManagerPackageInfo("third_party_package.B", 10004675, null),
                constructPackageManagerPackageInfo("system_package.critical.B", 10010001, null),
                constructPackageManagerPackageInfo("vendor_package.non_critical", 10110004, null),
                constructPackageManagerPackageInfo("third_party_package.A", 10110005,
                        "third_party_shared_package")));

        List<StatsEvent> events = new ArrayList<>();
        assertWithMessage("Stats pull atom callback status")
                .that(mStatsPullAtomCallback.onPullAtom(CAR_WATCHDOG_UID_IO_USAGE_SUMMARY,
                        events)).isEqualTo(PULL_SUCCESS);

        List<AtomsProto.CarWatchdogUidIoUsageSummary> expectedSummaries =
                verifyAndGetUidIoUsageSummaries(
                        mTimeSource.getCurrentDate().minus(RETENTION_PERIOD),
                        /* expectUids= */ Arrays.asList(10010001, 10110004, 10110005));

        assertWithMessage(String.format("Pulled uid I/O usage summary atoms for top %d UIDs",
                UID_IO_USAGE_SUMMARY_TOP_COUNT)).that(mPulledUidIoUsageSummaries)
                .containsExactlyElementsIn(expectedSummaries);
    }

    @Test
    public void testPullUidIoUsageSummaryAtomsWithRestart() throws Exception {
        injectPackageInfos(Arrays.asList(
                constructPackageManagerPackageInfo("system_package.critical", 10010001, null),
                constructPackageManagerPackageInfo("vendor_package.non_critical", 10110004, null),
                constructPackageManagerPackageInfo("third_party_package.A", 10110005,
                        "third_party_shared_package")));

        List<StatsEvent> events = new ArrayList<>();
        assertWithMessage("Stats pull atom callback status")
                .that(mStatsPullAtomCallback.onPullAtom(CAR_WATCHDOG_UID_IO_USAGE_SUMMARY,
                        events)).isEqualTo(PULL_SUCCESS);

        List<AtomsProto.CarWatchdogUidIoUsageSummary> expectedSummaries =
                verifyAndGetUidIoUsageSummaries(
                        mTimeSource.getCurrentDate().minus(RETENTION_PERIOD),
                        /* expectUids= */ Arrays.asList(10010001, 10110004, 10110005));

        assertWithMessage("First pulled uid I/O usage summary atoms")
                .that(mPulledUidIoUsageSummaries).containsExactlyElementsIn(expectedSummaries);
        mPulledUidIoUsageSummaries.clear();

        restartService(/* totalRestarts= */ 1, /* wantedDbWrites= */ 0);

        assertWithMessage("Status of stats pull atom callback after restart")
                .that(mStatsPullAtomCallback.onPullAtom(CAR_WATCHDOG_UID_IO_USAGE_SUMMARY,
                        events)).isEqualTo(PULL_SUCCESS);

        assertWithMessage("Pulled uid I/O usage summary atoms after restart")
                .that(mPulledUidIoUsageSummaries).isEmpty();

        verifyNoMoreInteractions(mSpiedWatchdogStorage);
    }

    @Test
    public void testPullInvalidAtoms() throws Exception {
        List<StatsEvent> actualEvents = new ArrayList<>();
        assertWithMessage("Stats pull atom callback status").that(mStatsPullAtomCallback.onPullAtom(
                0, actualEvents)).isEqualTo(PULL_SKIP);
        assertWithMessage("Pulled stats events").that(actualEvents).isEmpty();
    }

    @Test
    public void testProcessPackageChangedIntentForEnabledPackage() throws Exception {
        String packageName = "system_package";
        int userId = 100;

        disableUserPackage("system_package", 100, 101);
        disableUserPackage("vendor_package", 100);
        disableUserPackage("third_party_package", 100);

        doReturn(COMPONENT_ENABLED_STATE_ENABLED).when(mSpiedPackageManager)
                .getApplicationEnabledSetting(or(eq("system_package"),
                        eq("irrelevant_random_package")), eq(100));

        mWatchdogPerfHandler.processPackageChangedIntent(new Intent(ACTION_PACKAGE_CHANGED)
                .putExtra(Intent.EXTRA_USER_HANDLE, userId)
                .setData(Uri.parse("package:" + packageName)));

        mWatchdogPerfHandler.processPackageChangedIntent(new Intent(ACTION_PACKAGE_CHANGED)
                .putExtra(Intent.EXTRA_USER_HANDLE, userId)
                .setData(Uri.parse("package:irrelevant_random_package")));

        verifyDisabledPackagesSettingsKey(
                /* message= */ " after enabling system_package for user 100",
                /* userPackagesCsv= */
                "100:vendor_package,100:third_party_package,101:system_package");
    }

    @Test
    public void testProcessPackageChangedIntentForDisabledPackage() throws Exception {
        String packageName = "system_package";
        int userId = 100;

        disableUserPackage("system_package", 100, 101);
        disableUserPackage("vendor_package", 100);

        doReturn(COMPONENT_ENABLED_STATE_DISABLED).when(mSpiedPackageManager)
                .getApplicationEnabledSetting("system_package", 100);

        mWatchdogPerfHandler.processPackageChangedIntent(new Intent(ACTION_PACKAGE_CHANGED)
                .putExtra(Intent.EXTRA_USER_HANDLE, userId)
                .setData(Uri.parse("package:" + packageName)));

        verifyDisabledPackagesSettingsKey(
                /* message= */ "",
                /* userPackagesCsv= */ "100:vendor_package,100:system_package,101:system_package");
    }

    private List<AtomsProto.CarWatchdogSystemIoUsageSummary> verifyAndGetSystemIoUsageSummaries(
            ZonedDateTime beginReportDate) {
        ZonedDateTime beginWeekStartDate = beginReportDate.with(ChronoField.DAY_OF_WEEK, 1);
        ZonedDateTime endWeekStartDate = mTimeSource.getCurrentDate()
                .with(ChronoField.DAY_OF_WEEK, 1);
        List<AtomsProto.CarWatchdogSystemIoUsageSummary> expectedSummaries = new ArrayList<>();
        while (!beginWeekStartDate.equals(endWeekStartDate)) {
            long startEpochSecond = beginWeekStartDate.toEpochSecond();
            verify(mSpiedWatchdogStorage).getDailySystemIoUsageSummaries(
                    IO_USAGE_SUMMARY_MIN_SYSTEM_TOTAL_WRITTEN_BYTES, startEpochSecond,
                    beginWeekStartDate.plusWeeks(1).toEpochSecond());
            expectedSummaries.add(AtomsProto.CarWatchdogSystemIoUsageSummary.newBuilder()
                    .setIoUsageSummary(constructCarWatchdogIoUsageSummary(
                            sampleDailyIoUsageSummariesForAWeek(startEpochSecond,
                                    SYSTEM_DAILY_IO_USAGE_SUMMARY_MULTIPLIER)))
                    .setStartTimeMillis(startEpochSecond * 1000)
                    .build());
            beginWeekStartDate = beginWeekStartDate.plusWeeks(1);
        }
        return expectedSummaries;
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

    private List<AtomsProto.CarWatchdogUidIoUsageSummary> verifyAndGetUidIoUsageSummaries(
            ZonedDateTime beginReportDate, List<Integer> expectUids) {
        ZonedDateTime beginWeekStartDate = beginReportDate.with(ChronoField.DAY_OF_WEEK, 1);
        ZonedDateTime endWeekStartDate = mTimeSource.getCurrentDate()
                .with(ChronoField.DAY_OF_WEEK, 1);
        List<AtomsProto.CarWatchdogUidIoUsageSummary> expectedSummaries = new ArrayList<>();
        while (!beginWeekStartDate.equals(endWeekStartDate)) {
            long startEpochSecond = beginWeekStartDate.toEpochSecond();
            verify(mSpiedWatchdogStorage).getTopUsersDailyIoUsageSummaries(
                    UID_IO_USAGE_SUMMARY_TOP_COUNT * 2,
                    IO_USAGE_SUMMARY_MIN_SYSTEM_TOTAL_WRITTEN_BYTES, startEpochSecond,
                    beginWeekStartDate.plusWeeks(1).toEpochSecond());
            for (Integer uid : expectUids) {
                expectedSummaries.add(AtomsProto.CarWatchdogUidIoUsageSummary.newBuilder()
                        .setUid(uid)
                        .setIoUsageSummary(constructCarWatchdogIoUsageSummary(
                                sampleDailyIoUsageSummariesForAWeek(startEpochSecond, uid)))
                        .setStartTimeMillis(startEpochSecond * 1000)
                        .build());
            }
            beginWeekStartDate = beginWeekStartDate.plusWeeks(1);
        }
        return expectedSummaries;
    }

    private static AtomsProto.CarWatchdogIoUsageSummary constructCarWatchdogIoUsageSummary(
            List<AtomsProto.CarWatchdogDailyIoUsageSummary> dailySummaries) {
        return AtomsProto.CarWatchdogIoUsageSummary.newBuilder()
                .setEventTimePeriod(AtomsProto.CarWatchdogEventTimePeriod.newBuilder()
                        .setPeriod(AtomsProto.CarWatchdogEventTimePeriod.Period.WEEKLY).build())
                .addAllDailyIoUsageSummary(dailySummaries)
                .build();
    }

    //TODO(b/296123438): Add test for remaining dump fields in dumpProto.
    @Test
    public void testDumpProto() throws Exception {
        mWatchdogPerfHandler.disablePackageForUser("testPackage", 101);
        // Set mLastSystemIoUsageSummaryReportedDate and mLastUidIoUsageSummaryReportedDate.
        assertWithMessage("Stats pull atom callback status")
                .that(mStatsPullAtomCallback.onPullAtom(CAR_WATCHDOG_SYSTEM_IO_USAGE_SUMMARY,
                        new ArrayList<>())).isEqualTo(PULL_SUCCESS);
        ProtoOutputStream proto = new ProtoOutputStream();
        mWatchdogPerfHandler.dumpProto(proto);

        CarWatchdogDumpProto carWatchdogDumpProto = CarWatchdogDumpProto.parseFrom(
                proto.getBytes());
        PerformanceDump performanceDump = carWatchdogDumpProto.getPerformanceDump();
        expectWithMessage("Current UX State")
                .that(performanceDump.getCurrentUxState())
                .isEqualTo(PerformanceDump.UxState.UX_STATE_USER_NOTIFICATION);

        expectWithMessage("Disabled Packages By User Id")
                .that(performanceDump.getDisabledUserPackagesCount()).isEqualTo(1);
        UserPackageInfo userPackageInfo =
                performanceDump.getDisabledUserPackages(0);
        expectWithMessage("UserPackageInfo.userId")
                .that(userPackageInfo.getUserId()).isEqualTo(101);
        expectWithMessage("UserPackageInfo.packageName")
                .that(userPackageInfo.getPackageName()).isEqualTo("testPackage");

        PerformanceDump.OveruseConfigurationCacheDump overuseConfigurationCacheDump =
                performanceDump.getOveruseConfigurationCacheDump();

        expectWithMessage("Safe To Kill System Packages")
                .that(overuseConfigurationCacheDump.getSafeToKillSystemPackagesCount())
                .isEqualTo(4);
        expectThat(overuseConfigurationCacheDump.getSafeToKillSystemPackages(0))
                .isEqualTo("system_package.non_critical.A");
        expectThat(overuseConfigurationCacheDump.getSafeToKillSystemPackages(1))
                .isEqualTo("system_pkg.non_critical.B");
        expectThat(overuseConfigurationCacheDump.getSafeToKillSystemPackages(2))
                .isEqualTo("shared:system_shared_package.non_critical.B");
        expectThat(overuseConfigurationCacheDump.getSafeToKillSystemPackages(3))
                .isEqualTo("some_pkg_as_system_pkg");

        expectWithMessage("Safe To Kill Vendor Packages")
                .that(overuseConfigurationCacheDump.getSafeToKillVendorPackagesCount())
                .isEqualTo(4);
        expectThat(overuseConfigurationCacheDump.getSafeToKillVendorPackages(0))
                .isEqualTo("vendor_package.non_critical.A");
        expectThat(overuseConfigurationCacheDump.getSafeToKillVendorPackages(1))
                .isEqualTo("shared:vendor_shared_package.non_critical.B");
        expectThat(overuseConfigurationCacheDump.getSafeToKillVendorPackages(2))
                .isEqualTo("vendor_pkg.non_critical.B");
        expectThat(overuseConfigurationCacheDump.getSafeToKillVendorPackages(3))
                .isEqualTo("some_pkg_as_vendor_pkg");

        expectWithMessage("Vendor Package Prefixes").that(
                overuseConfigurationCacheDump.getVendorPackagePrefixesCount()).isEqualTo(2);
        expectThat(overuseConfigurationCacheDump.getVendorPackagePrefixes(0))
                .isEqualTo("vendor_package");
        expectThat(overuseConfigurationCacheDump.getVendorPackagePrefixes(1))
                .isEqualTo("some_pkg_as_vendor_pkg");

        expectWithMessage("Packages by App Category")
                .that(overuseConfigurationCacheDump.getPackagesByAppCategoryCount()).isEqualTo(2);
        PerformanceDump.PackageByAppCategory packageByMapsAppCategory =
                overuseConfigurationCacheDump.getPackagesByAppCategory(0);
        expectThat(packageByMapsAppCategory.getApplicationCategory())
                .isEqualTo(PerformanceDump.ApplicationCategory.MAPS);
        expectThat(packageByMapsAppCategory.getPackageName(0)).isEqualTo("system_package.A");
        PerformanceDump.PackageByAppCategory packageByMediaAppCategory =
                overuseConfigurationCacheDump.getPackagesByAppCategory(1);
        expectThat(packageByMediaAppCategory.getApplicationCategory())
                .isEqualTo(PerformanceDump.ApplicationCategory.MEDIA);
        expectThat(packageByMediaAppCategory.getPackageName(0)).isEqualTo("vendor_package.MEDIA");

        expectWithMessage("Generic Io Thresholds by Component")
                .that(overuseConfigurationCacheDump.getGenericIoThresholdsByComponentCount())
                .isEqualTo(3);
        PerformanceDump.IoThresholdByComponent ioThresholdBySystemComponent =
                overuseConfigurationCacheDump.getGenericIoThresholdsByComponent(0);
        expectThat(ioThresholdBySystemComponent.getComponentType())
                .isEqualTo(PerformanceDump.ComponentType.SYSTEM);
        PerformanceDump.PerStateBytes perStateBytes =
                ioThresholdBySystemComponent.getThreshold();
        expectThat(perStateBytes.getForegroundBytes()).isEqualTo(10);
        expectThat(perStateBytes.getBackgroundBytes()).isEqualTo(20);
        expectThat(perStateBytes.getGaragemodeBytes()).isEqualTo(30);
        PerformanceDump.IoThresholdByComponent ioThresholdByVendorComponent =
                overuseConfigurationCacheDump.getGenericIoThresholdsByComponent(1);
        expectThat(ioThresholdByVendorComponent.getComponentType())
                .isEqualTo(PerformanceDump.ComponentType.VENDOR);
        perStateBytes = ioThresholdByVendorComponent.getThreshold();
        expectThat(perStateBytes.getForegroundBytes()).isEqualTo(20);
        expectThat(perStateBytes.getBackgroundBytes()).isEqualTo(40);
        expectThat(perStateBytes.getGaragemodeBytes()).isEqualTo(60);
        PerformanceDump.IoThresholdByComponent ioThresholdByThirdPartyComponent =
                overuseConfigurationCacheDump.getGenericIoThresholdsByComponent(2);
        expectThat(ioThresholdByThirdPartyComponent.getComponentType())
                .isEqualTo(PerformanceDump.ComponentType.THIRD_PARTY);
        perStateBytes = ioThresholdByThirdPartyComponent.getThreshold();
        expectThat(perStateBytes.getForegroundBytes()).isEqualTo(30);
        expectThat(perStateBytes.getBackgroundBytes()).isEqualTo(60);
        expectThat(perStateBytes.getGaragemodeBytes()).isEqualTo(90);

        expectWithMessage("Io Thresholds By Package").that(
                overuseConfigurationCacheDump.getIoThresholdsByPackageCount()).isEqualTo(2);
        PerformanceDump.IoThresholdByPackage ioThresholdBySystemPackage =
                overuseConfigurationCacheDump.getIoThresholdsByPackage(0);
        expectThat(ioThresholdBySystemPackage.getPackageType()).isEqualTo(
                PerformanceDump.ComponentType.SYSTEM);
        perStateBytes = ioThresholdBySystemPackage.getThreshold();
        expectThat(perStateBytes.getForegroundBytes()).isEqualTo(40);
        expectThat(perStateBytes.getBackgroundBytes()).isEqualTo(50);
        expectThat(perStateBytes.getGaragemodeBytes()).isEqualTo(60);
        expectThat(ioThresholdBySystemPackage.getPackageName()).isEqualTo("system_package.A");
        PerformanceDump.IoThresholdByPackage ioThresholdByVendorPackage =
                overuseConfigurationCacheDump.getIoThresholdsByPackage(1);
        expectThat(ioThresholdByVendorPackage.getPackageType()).isEqualTo(
                PerformanceDump.ComponentType.VENDOR);
        perStateBytes = ioThresholdByVendorPackage.getThreshold();
        expectThat(perStateBytes.getForegroundBytes()).isEqualTo(80);
        expectThat(perStateBytes.getBackgroundBytes()).isEqualTo(100);
        expectThat(perStateBytes.getGaragemodeBytes()).isEqualTo(120);
        expectThat(ioThresholdByVendorPackage.getPackageName()).isEqualTo("vendor_package.A");

        expectWithMessage("Thresholds by App Category")
                .that(overuseConfigurationCacheDump.getThresholdsByAppCategoryCount()).isEqualTo(2);
        PerformanceDump.IoThresholdByAppCategory ioThresholdByMapsAppCategory =
                overuseConfigurationCacheDump.getThresholdsByAppCategory(0);
        expectThat(ioThresholdByMapsAppCategory.getApplicationCategory())
                .isEqualTo(PerformanceDump.ApplicationCategory.MAPS);
        perStateBytes = ioThresholdByMapsAppCategory.getThreshold();
        expectThat(perStateBytes.getForegroundBytes()).isEqualTo(2200);
        expectThat(perStateBytes.getBackgroundBytes()).isEqualTo(4400);
        expectThat(perStateBytes.getGaragemodeBytes()).isEqualTo(6600);
        PerformanceDump.IoThresholdByAppCategory ioThresholdByMediaAppCategory =
                overuseConfigurationCacheDump.getThresholdsByAppCategory(1);
        expectThat(ioThresholdByMediaAppCategory.getApplicationCategory())
                .isEqualTo(PerformanceDump.ApplicationCategory.MEDIA);
        perStateBytes = ioThresholdByMediaAppCategory.getThreshold();
        expectThat(perStateBytes.getForegroundBytes()).isEqualTo(200);
        expectThat(perStateBytes.getBackgroundBytes()).isEqualTo(400);
        expectThat(perStateBytes.getGaragemodeBytes()).isEqualTo(600);
    }

    private static SparseArray<String> constructPackagesByNotificationId(int idOffset,
            String... packages) {
        SparseArray<String> packagesById = new SparseArray<>();
        idOffset = idOffset < 0 ? 0 : idOffset % RESOURCE_OVERUSE_NOTIFICATION_MAX_OFFSET;
        for (String packageName : packages) {
            packagesById.put(RESOURCE_OVERUSE_NOTIFICATION_BASE_ID + idOffset, packageName);
            idOffset = ++idOffset % RESOURCE_OVERUSE_NOTIFICATION_MAX_OFFSET;
        }
        return packagesById;
    }

    private void captureAndVerifyUserNotifications(
            List<UserNotificationReflectionCall> expectedNotifications) {
        // Recurring overuse notification handling task is posted on a separate handler thread and
        // this task sends the user notifications. Wait for this task to complete.
        CarServiceUtils.runEmptyRunnableOnLooperSync(CarWatchdogService.class.getSimpleName());

        verify(mMockNotificationHelper, times(expectedNotifications.size()))
                .showResourceOveruseNotificationsAsUser(mUserHandleCaptor.capture(),
                        mHeadsUpPackagesCaptor.capture(),
                        mNotificationCenterPackagesCaptor.capture());

        if (expectedNotifications.isEmpty()) {
            return;
        }

        assertWithMessage("Number of notification does not match").that(
                mUserHandleCaptor.getAllValues().size()).isEqualTo(expectedNotifications.size());
        for (int i = 0; i < expectedNotifications.size(); i++) {
            UserNotificationReflectionCall expectedNotification =
                    expectedNotifications.get(i);

            UserHandle userHandle = mUserHandleCaptor.getAllValues().get(i);
            SparseArray<String> actualHeadsUpNotificationPackagesById = mHeadsUpPackagesCaptor
                    .getAllValues().get(i);
            SparseArray<String> actualPackagesById = mNotificationCenterPackagesCaptor
                    .getAllValues().get(i);

            assertWithMessage("Current user id for resource overuse notifications")
                    .that(userHandle).isEqualTo(expectedNotification.userHandle);

            int expectedHeadsUpSize = expectedNotification.hasHeadsUpNotification ? 1 : 0;

            assertWithMessage("Resource overuse heads up packages size")
                    .that(actualHeadsUpNotificationPackagesById.size())
                    .isEqualTo(expectedHeadsUpSize);

            if (expectedNotification.hasHeadsUpNotification) {
                int headsUpNotificationId = actualHeadsUpNotificationPackagesById.keyAt(0);
                actualPackagesById.put(headsUpNotificationId,
                        actualHeadsUpNotificationPackagesById.valueAt(0));
            }

            int expectedSize = expectedNotification.packagesById.size();

            assertWithMessage("Resource overuse notification size")
                    .that(actualPackagesById.size()).isEqualTo(expectedSize);

            ArraySet<String> expectedPackages = new ArraySet<>(expectedSize);
            ArraySet<String> actualPackages = new ArraySet<>(expectedSize);
            for (int j = 0; j < expectedNotification.packagesById.size(); j++) {
                int expectedNotificationId = expectedNotification.packagesById.keyAt(j);

                assertWithMessage("Resource overuse notification id")
                        .that(actualPackagesById.get(expectedNotificationId)).isNotNull();

                expectedPackages.add(expectedNotification.packagesById.valueAt(j));
                actualPackages.add(actualPackagesById.valueAt(j));
            }

            assertWithMessage("Resource overuse notification package names")
                    .that(actualPackages).isEqualTo(expectedPackages);
        }
    }

    private static void verifyOnOveruseCalled(List<ResourceOveruseStats> expectedStats,
            IResourceOveruseListener mockListener) throws Exception {
        ArgumentCaptor<ResourceOveruseStats> resourceOveruseStatsCaptor =
                ArgumentCaptor.forClass(ResourceOveruseStats.class);

        verify(mockListener, times(expectedStats.size()))
                .onOveruse(resourceOveruseStatsCaptor.capture());

        ResourceOveruseStatsSubject.assertThat(resourceOveruseStatsCaptor.getAllValues())
                .containsExactlyElementsIn(expectedStats);
    }

    private void setupUsers() {
        when(mMockContext.getSystemService(UserManager.class)).thenReturn(mMockUserManager);
        mockUmGetAllUsers(mMockUserManager, new UserHandle[0]);
    }

    private void initService(int wantedInvocations) throws Exception {
        mWatchdogPerfHandler.setOveruseHandlingDelay(OVERUSE_HANDLING_DELAY_MILLS);
        mWatchdogPerfHandler.init();
        captureCarUxRestrictionsChangeListener(wantedInvocations);
        verifyDatabaseInit(wantedInvocations);
        captureStatsPullAtomCallback(wantedInvocations);
        mWatchdogPerfHandler.onDaemonConnectionChange(/* isConnected= */ true);

    }

    private void restartService(int totalRestarts, int wantedDbWrites) throws Exception {
        restartService(totalRestarts, wantedDbWrites, /* isWriteIoStats= */ true);
    }

    private void restartService(int totalRestarts, int wantedDbWrites, boolean isWriteIoStats)
            throws Exception {
        mWatchdogPerfHandler.writeMetadataFile();
        mWatchdogPerfHandler.writeToDatabase();
        mWatchdogPerfHandler.release();
        mSpiedWatchdogStorage.release();
        verify(mSpiedWatchdogStorage, times(totalRestarts)).startWrite();
        verify(mSpiedWatchdogStorage, times(isWriteIoStats ? wantedDbWrites : 0))
                .saveIoUsageStats(any());
        verify(mSpiedWatchdogStorage, times(wantedDbWrites)).saveUserPackageSettings(any());
        verify(mSpiedWatchdogStorage, times(wantedDbWrites)).markWriteSuccessful();
        verify(mSpiedWatchdogStorage, times(wantedDbWrites)).endWrite();
        verify(mSpiedWatchdogStorage, times(Math.max(totalRestarts, 1))).release();
        mWatchdogPerfHandler = new WatchdogPerfHandler(mMockContext,
                mMockBuiltinPackageContext, mMockCarWatchdogDaemonHelper,
                new PackageInfoHandler(mMockContext.getPackageManager()),
                mSpiedWatchdogStorage, mTimeSource);
        initService(/* wantedInvocations= */ totalRestarts + 1);
    }

    private void setRequiresDistractionOptimization(boolean isRequires) throws Exception {
        CarUxRestrictions.Builder builder = new CarUxRestrictions.Builder(
                isRequires, UX_RESTRICTIONS_BASELINE, /* time= */ 0);
        mCarUxRestrictionsChangeListener.onUxRestrictionsChanged(builder.build());
    }

    private List<PackageIoOveruseStats> sampleIoOveruseStats(boolean requireRecurrentOveruseStats)
            throws Exception {
        int[] users = new int[]{100, 101};
        int totalOveruses = requireRecurrentOveruseStats ? RECURRING_OVERUSE_TIMES + 1 : 1;
        List<PackageIoOveruseStats> packageIoOveruseStats = new ArrayList<>();
        android.automotive.watchdog.PerStateBytes zeroRemainingBytes =
                constructPerStateBytes(0, 0, 0);
        android.automotive.watchdog.PerStateBytes nonZeroRemainingBytes =
                constructPerStateBytes(20, 30, 40);
        android.automotive.watchdog.PerStateBytes writtenBytes =
                constructPerStateBytes(100, 200, 300);
        for (int i = 0; i < users.length; ++i) {
            // Overuse occurred but cannot be killed/disabled.
            packageIoOveruseStats.add(constructPackageIoOveruseStats(
                    UserHandle.getUid(users[i], 10001), /* shouldNotify= */ true,
                    /* forgivenWriteBytes= */ writtenBytes,
                    constructInternalIoOveruseStats(
                            /* killableOnOveruse= */ false, zeroRemainingBytes, writtenBytes,
                            totalOveruses)));
            // No overuse occurred but the package should be notified.
            packageIoOveruseStats.add(constructPackageIoOveruseStats(
                    UserHandle.getUid(users[i], 10002), /* shouldNotify= */ true,
                    /* forgivenWriteBytes= */ constructPerStateBytes(0, 0, 0),
                    constructInternalIoOveruseStats(
                            /* killableOnOveruse= */ true, nonZeroRemainingBytes, writtenBytes,
                            totalOveruses)));
            // Neither overuse occurred nor be notified.
            packageIoOveruseStats.add(constructPackageIoOveruseStats(
                    UserHandle.getUid(users[i], 10003), /* shouldNotify= */ false,
                    /* forgivenWriteBytes= */ constructPerStateBytes(0, 0, 0),
                    constructInternalIoOveruseStats(
                            /* killableOnOveruse= */ false, nonZeroRemainingBytes, writtenBytes,
                            totalOveruses)));
            // Overuse occurred and can be killed/disabled.
            packageIoOveruseStats.add(constructPackageIoOveruseStats(
                    UserHandle.getUid(users[i], 10004), /* shouldNotify= */ false,
                    /* forgivenWriteBytes= */ writtenBytes,
                    constructInternalIoOveruseStats(
                            /* killableOnOveruse= */ true, zeroRemainingBytes, writtenBytes,
                            totalOveruses)));
            // Overuse occurred and can be killed/disabled.
            packageIoOveruseStats.add(constructPackageIoOveruseStats(
                    UserHandle.getUid(users[i], 10005), /* shouldNotify= */ true,
                    /* forgivenWriteBytes= */ writtenBytes,
                    constructInternalIoOveruseStats(
                            /* killableOnOveruse= */ true, zeroRemainingBytes, writtenBytes,
                            totalOveruses)));
        }
        return packageIoOveruseStats;
    }

    private void setUpSampleUserAndPackages() {
        mockUmGetUserHandles(mMockUserManager, /* excludeDying= */ true, 100, 101);
        int[] users = new int[]{100, 101};
        List<android.content.pm.PackageInfo> packageInfos = new ArrayList<>();
        for (int i = 0; i < users.length; ++i) {
            packageInfos.add(constructPackageManagerPackageInfo(
                    "system_package.critical", UserHandle.getUid(users[i], 10001), null));
            packageInfos.add(constructPackageManagerPackageInfo(
                    "system_package.non_critical", UserHandle.getUid(users[i], 10002), null));
            packageInfos.add(constructPackageManagerPackageInfo(
                    "vendor_package.critical", UserHandle.getUid(users[i], 10003), null));
            packageInfos.add(constructPackageManagerPackageInfo(
                    "vendor_package.non_critical", UserHandle.getUid(users[i], 10004), null));
            packageInfos.add(constructPackageManagerPackageInfo(
                    "third_party_package.A", UserHandle.getUid(users[i], 10005),
                    "third_party_shared_package"));
            packageInfos.add(constructPackageManagerPackageInfo(
                    "third_party_package.B", UserHandle.getUid(users[i], 10005),
                    "third_party_shared_package"));
        }
        injectPackageInfos(packageInfos);
    }

    private static List<AtomsProto.CarWatchdogIoOveruseStatsReported> sampleReportedOveruseStats() {
        // The below thresholds are from {@link sampleInternalResourceOveruseConfiguration} and
        // UID/stat are from {@link sampleIoOveruseStats}.
        AtomsProto.CarWatchdogPerStateBytes systemThreshold =
                WatchdogPerfHandler.constructCarWatchdogPerStateBytes(10, 20, 30);
        AtomsProto.CarWatchdogPerStateBytes vendorThreshold =
                WatchdogPerfHandler.constructCarWatchdogPerStateBytes(20, 40, 60);
        AtomsProto.CarWatchdogPerStateBytes thirdPartyThreshold =
                WatchdogPerfHandler.constructCarWatchdogPerStateBytes(30, 60, 90);
        AtomsProto.CarWatchdogPerStateBytes writtenBytes =
                WatchdogPerfHandler.constructCarWatchdogPerStateBytes(100, 200, 300);
        List<AtomsProto.CarWatchdogIoOveruseStatsReported> reportedOveruseStats = new ArrayList<>();
        reportedOveruseStats.add(constructIoOveruseStatsReported(
                10010001, systemThreshold, writtenBytes));
        reportedOveruseStats.add(constructIoOveruseStatsReported(
                10110001, systemThreshold, writtenBytes));
        reportedOveruseStats.add(constructIoOveruseStatsReported(
                10010004, vendorThreshold, writtenBytes));
        reportedOveruseStats.add(constructIoOveruseStatsReported(
                10110004, vendorThreshold, writtenBytes));
        reportedOveruseStats.add(constructIoOveruseStatsReported(
                10010005, thirdPartyThreshold, writtenBytes));
        reportedOveruseStats.add(constructIoOveruseStatsReported(
                10110005, thirdPartyThreshold, writtenBytes));
        return reportedOveruseStats;
    }

    private static List<AtomsProto.CarWatchdogKillStatsReported> sampleReportedKillStats(
            int systemState, int[] killedUids) {
        // The below thresholds are from {@link sampleInternalResourceOveruseConfiguration} and
        // UID/stat are from {@link sampleIoOveruseStats}.
        AtomsProto.CarWatchdogPerStateBytes vendorThreshold =
                WatchdogPerfHandler.constructCarWatchdogPerStateBytes(20, 40, 60);
        AtomsProto.CarWatchdogPerStateBytes thirdPartyThreshold =
                WatchdogPerfHandler.constructCarWatchdogPerStateBytes(30, 60, 90);
        AtomsProto.CarWatchdogPerStateBytes writtenBytes =
                WatchdogPerfHandler.constructCarWatchdogPerStateBytes(100, 200, 300);
        List<AtomsProto.CarWatchdogKillStatsReported> reportedKillStats = new ArrayList<>();
        for (int uid : killedUids) {
            AtomsProto.CarWatchdogPerStateBytes threshold =
                    UserHandle.getAppId(uid) == 10004 ? vendorThreshold : thirdPartyThreshold;
            reportedKillStats.add(constructIoOveruseKillStatsReported(
                    uid, systemState, threshold, writtenBytes));
        }
        return reportedKillStats;
    }

    private static AtomsProto.CarWatchdogIoOveruseStatsReported
            constructIoOveruseStatsReported(int uid, AtomsProto.CarWatchdogPerStateBytes threshold,
            AtomsProto.CarWatchdogPerStateBytes writtenBytes) {
        return constructCarWatchdogIoOveruseStatsReported(
                uid, WatchdogPerfHandler.constructCarWatchdogIoOveruseStats(
                        AtomsProto.CarWatchdogIoOveruseStats.Period.DAILY, threshold, writtenBytes)
        );
    }

    private void captureAndVerifyIoOveruseStatsReported(
            List<AtomsProto.CarWatchdogIoOveruseStatsReported> expected) throws Exception {
        verify(() -> CarStatsLog.write(eq(CarStatsLog.CAR_WATCHDOG_IO_OVERUSE_STATS_REPORTED),
                mOverusingUidCaptor.capture(), mOveruseStatsCaptor.capture()),
                times(expected.size()));

        List<Integer> allUidValues = mOverusingUidCaptor.getAllValues();
        List<byte[]> allOveruseStatsValues = mOveruseStatsCaptor.getAllValues();
        List<AtomsProto.CarWatchdogIoOveruseStatsReported> actual = new ArrayList<>();
        for (int i = 0; i < expected.size(); ++i) {
            actual.add(constructCarWatchdogIoOveruseStatsReported(allUidValues.get(i),
                    AtomsProto.CarWatchdogIoOveruseStats.parseFrom(allOveruseStatsValues.get(i))));
        }
        assertWithMessage("I/O overuse stats reported to statsd").that(actual)
                .containsExactlyElementsIn(expected);
    }

    private void captureAndVerifyKillStatsReported(
            List<AtomsProto.CarWatchdogKillStatsReported> expected) throws Exception {
        // Overuse handling task is posted on the main thread and this task performs disabling and
        // uploading metrics. Wait for this task to complete.
        CarServiceUtils.runOnMainSync(() -> {});

        verify(() -> CarStatsLog.write(eq(CarStatsLog.CAR_WATCHDOG_KILL_STATS_REPORTED),
                mKilledUidCaptor.capture(), mUidStateCaptor.capture(),
                mSystemStateCaptor.capture(), mKillReasonCaptor.capture(), eq(null),
                mKilledStatsCaptor.capture()), times(expected.size()));

        List<Integer> allUidValues = mKilledUidCaptor.getAllValues();
        List<Integer> allUidStateValues = mUidStateCaptor.getAllValues();
        List<Integer> allSystemStateValues = mSystemStateCaptor.getAllValues();
        List<Integer> allKillReasonValues = mKillReasonCaptor.getAllValues();
        List<byte[]> allIoOveruseStatsValues = mKilledStatsCaptor.getAllValues();
        List<AtomsProto.CarWatchdogKillStatsReported> actual = new ArrayList<>();
        for (int i = 0; i < expected.size(); ++i) {
            actual.add(constructCarWatchdogKillStatsReported(allUidValues.get(i),
                    allUidStateValues.get(i), allSystemStateValues.get(i),
                    allKillReasonValues.get(i),
                    AtomsProto.CarWatchdogIoOveruseStats.parseFrom(
                            allIoOveruseStatsValues.get(i))));
        }
        assertWithMessage("I/O overuse kill stats reported to statsd").that(actual)
                .containsExactlyElementsIn(expected);
    }

    private static AtomsProto.CarWatchdogIoOveruseStatsReported
            constructCarWatchdogIoOveruseStatsReported(
            int uid, AtomsProto.CarWatchdogIoOveruseStats ioOveruseStats) {
        return AtomsProto.CarWatchdogIoOveruseStatsReported.newBuilder()
                .setUid(uid)
                .setIoOveruseStats(ioOveruseStats)
                .build();
    }

    private static AtomsProto.CarWatchdogKillStatsReported constructIoOveruseKillStatsReported(
            int uid, int systemState, AtomsProto.CarWatchdogPerStateBytes threshold,
            AtomsProto.CarWatchdogPerStateBytes writtenBytes) {
        return constructCarWatchdogKillStatsReported(uid,
                CAR_WATCHDOG_KILL_STATS_REPORTED__UID_STATE__UNKNOWN_UID_STATE, systemState,
                CAR_WATCHDOG_KILL_STATS_REPORTED__KILL_REASON__KILLED_ON_IO_OVERUSE,
                WatchdogPerfHandler.constructCarWatchdogIoOveruseStats(
                        AtomsProto.CarWatchdogIoOveruseStats.Period.DAILY, threshold, writtenBytes)
        );
    }

    private static AtomsProto.CarWatchdogKillStatsReported constructCarWatchdogKillStatsReported(
            int uid, int uidState, int systemState, int killReason,
            AtomsProto.CarWatchdogIoOveruseStats ioOveruseStats) {
        return AtomsProto.CarWatchdogKillStatsReported.newBuilder()
                .setUid(uid)
                .setUidState(AtomsProto.CarWatchdogKillStatsReported.UidState.forNumber(uidState))
                .setSystemState(AtomsProto.CarWatchdogKillStatsReported.SystemState.forNumber(
                        systemState))
                .setKillReason(AtomsProto.CarWatchdogKillStatsReported.KillReason.forNumber(
                        killReason))
                .setIoOveruseStats(ioOveruseStats)
                .build();
    }

    private void enableUserPackage(String packageName, int userId, boolean isKillable)
            throws Exception {
        UserHandle userHandle = UserHandle.of(userId);

        // Set package killable state to not killable, which enable the user package
        mWatchdogPerfHandler.setKillablePackageAsUser(packageName, userHandle,
                /* isKillable= */ false);

        if (isKillable) {
            mWatchdogPerfHandler.setKillablePackageAsUser(packageName, userHandle,
                    /* isKillable= */ true);
        }

        verify(mSpiedPackageManager, atLeastOnce())
                .getApplicationEnabledSetting(packageName, userId);

        verify(mSpiedPackageManager).setApplicationEnabledSetting(eq(packageName),
                eq(COMPONENT_ENABLED_STATE_ENABLED), eq(0),
                eq(userId), anyString());

        assertThat(mDisabledUserPackages).doesNotContain(userId + ":" + packageName);

        doReturn(COMPONENT_ENABLED_STATE_ENABLED).when(mSpiedPackageManager)
                .getApplicationEnabledSetting(eq(packageName), eq(userId));
    }

    private void disableUserPackage(String packageName, int... userIds) throws Exception {
        for (int i = 0; i < userIds.length; i++) {
            int userId = userIds[i];

            mWatchdogPerfHandler.disablePackageForUser(packageName, userId);

            verify(mSpiedPackageManager, atLeastOnce())
                    .getApplicationEnabledSetting(packageName, userId);

            verify(mSpiedPackageManager).setApplicationEnabledSetting(eq(packageName),
                    eq(COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED), eq(0),
                    eq(userId), anyString());

            assertThat(mDisabledUserPackages).contains(userId + ":" + packageName);

            doReturn(COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED).when(mSpiedPackageManager)
                    .getApplicationEnabledSetting(eq(packageName), eq(userId));
        }
    }

    static android.content.pm.PackageInfo constructPackageManagerPackageInfo(
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

    private void verifyDisabledPackages(String userPackagesCsv) {
        verifyDisabledPackages(/* message= */ "", userPackagesCsv);
    }

    private void verifyDisabledPackages(String message, String userPackagesCsv) {
        assertWithMessage("Disabled user packages %s", message).that(mDisabledUserPackages)
                .containsExactlyElementsIn(userPackagesCsv.split(","));

        verifyDisabledPackagesSettingsKey(message, userPackagesCsv);
    }

    private void verifyDisabledPackagesSettingsKey(String message, String userPackagesCsv) {
        List<String> userPackagesFromSettingsString = new ArrayList<>();
        for (int i = 0; i < mDisabledPackagesSettingsStringByUserid.size(); ++i) {
            int userId = mDisabledPackagesSettingsStringByUserid.keyAt(i);
            String value = mDisabledPackagesSettingsStringByUserid.valueAt(i);
            List<String> packages = TextUtils.isEmpty(value) ? new ArrayList<>()
                    : new ArrayList<>(Arrays.asList(value.split(
                            PACKAGES_DISABLED_ON_RESOURCE_OVERUSE_SEPARATOR)));
            packages.forEach(element ->
                    userPackagesFromSettingsString.add(userId + USER_PACKAGE_SEPARATOR + element));
        }

        assertWithMessage(
                "KEY_PACKAGES_DISABLED_ON_RESOURCE_OVERUSE settings string user packages %s",
                message).that(userPackagesFromSettingsString)
                .containsExactlyElementsIn(userPackagesCsv.split(","));
    }

    private void verifyNoDisabledPackages() {
        verifyNoDisabledPackages(/* message= */ "");
    }

    private void verifyNoDisabledPackages(String message) {
        assertWithMessage("Disabled user packages %s", message).that(mDisabledUserPackages)
                .isEmpty();
        assertWithMessage(
                "KEY_PACKAGES_DISABLED_ON_RESOURCE_OVERUSE settings string user packages %s",
                message).that(mDisabledPackagesSettingsStringByUserid.size()).isEqualTo(0);
    }

    private void mockSettingsStringCalls() {
        doAnswer(args -> {
            when(mMockContentResolver.getUserId()).thenReturn(args.getArgument(1));
            return mMockContentResolver;
        }).when(() -> CarServiceUtils.getContentResolverForUser(any(), anyInt()));

        when(Settings.Secure.getString(any(ContentResolver.class),
                eq(KEY_PACKAGES_DISABLED_ON_RESOURCE_OVERUSE))).thenAnswer(
                    args -> {
                        ContentResolver contentResolver = args.getArgument(0);
                        int userId = contentResolver.getUserId();
                        return mDisabledPackagesSettingsStringByUserid.get(userId);
                    });

        // Use any() instead of anyString() to consider when string arg is null.
        when(Settings.Secure.putString(any(ContentResolver.class),
                eq(KEY_PACKAGES_DISABLED_ON_RESOURCE_OVERUSE), any())).thenAnswer(args -> {
                    ContentResolver contentResolver = args.getArgument(0);
                    int userId = contentResolver.getUserId();
                    String packageSettings = args.getArgument(2);
                    if (packageSettings == null) {
                        mDisabledPackagesSettingsStringByUserid.remove(userId);
                    } else {
                        mDisabledPackagesSettingsStringByUserid.put(userId, args.getArgument(2));
                    }
                    return null;
                });
    }

    // TODO(b/262301082): Move to PackageInfoHandlerUnitTest.
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

    private List<AtomsProto.CarWatchdogDailyIoUsageSummary> sampleDailyIoUsageSummariesForAWeek(
            long startEpochSeconds, long sysOrUidMultiplier) {
        List<AtomsProto.CarWatchdogDailyIoUsageSummary> summaries = new ArrayList<>();
        long weekMultiplier = ChronoUnit.WEEKS.between(
                ZonedDateTime.ofInstant(Instant.ofEpochSecond(startEpochSeconds), ZONE_OFFSET),
                mTimeSource.getCurrentDate());
        for (int i = 1; i < 8; ++i) {
            summaries.add(CarWatchdogServiceUnitTest.constructCarWatchdogDailyIoUsageSummary(
                    /* fgWrBytes= */ 100 * i * weekMultiplier * sysOrUidMultiplier,
                    /* bgWrBytes= */ 200 * i * weekMultiplier * sysOrUidMultiplier,
                    /* gmWrBytes= */ 300 * i * weekMultiplier * sysOrUidMultiplier,
                    /* overuseCount= */ 2 * i));
        }
        return summaries;
    }

    private static ResourceOveruseStats constructResourceOveruseStats(int uid, String packageName,
            android.automotive.watchdog.IoOveruseStats internalIoOveruseStats) {
        IoOveruseStats ioOveruseStats = WatchdogPerfHandler.toIoOveruseStatsBuilder(
                internalIoOveruseStats, /* totalTimesKilled= */ 0,
                internalIoOveruseStats.killableOnOveruse).build();

        return new ResourceOveruseStats.Builder(packageName, UserHandle.getUserHandleForUid(uid))
                .setIoOveruseStats(ioOveruseStats).build();
    }

    static List<android.automotive.watchdog.internal.ResourceOveruseConfiguration>
            sampleInternalResourceOveruseConfigurations() {
        return Arrays.asList(
                sampleInternalResourceOveruseConfiguration(ComponentType.SYSTEM,
                        sampleInternalIoOveruseConfiguration(ComponentType.SYSTEM)),
                sampleInternalResourceOveruseConfiguration(ComponentType.VENDOR,
                        sampleInternalIoOveruseConfiguration(ComponentType.VENDOR)),
                sampleInternalResourceOveruseConfiguration(ComponentType.THIRD_PARTY,
                        sampleInternalIoOveruseConfiguration(ComponentType.THIRD_PARTY)));
    }

    private static android.automotive.watchdog.internal.ResourceOveruseConfiguration
            sampleInternalResourceOveruseConfiguration(@ComponentType int componentType,
            android.automotive.watchdog.internal.IoOveruseConfiguration ioOveruseConfig) {
        String prefix = WatchdogPerfHandler.toComponentTypeStr(componentType).toLowerCase();
        android.automotive.watchdog.internal.ResourceOveruseConfiguration config =
                new android.automotive.watchdog.internal.ResourceOveruseConfiguration();
        config.componentType = componentType;
        config.safeToKillPackages = Arrays.asList(prefix + "_package.non_critical.A",
                prefix + "_pkg.non_critical.B",
                "shared:" + prefix + "_shared_package.non_critical.B",
                "some_pkg_as_" + prefix + "_pkg");
        config.vendorPackagePrefixes = Arrays.asList(
                prefix + "_package", "some_pkg_as_" + prefix + "_pkg");
        config.packageMetadata = Arrays.asList(
                constructPackageMetadata("system_package.MEDIA", ApplicationCategoryType.MEDIA),
                constructPackageMetadata("system_package.A", ApplicationCategoryType.MAPS),
                constructPackageMetadata("vendor_package.MEDIA", ApplicationCategoryType.MEDIA),
                constructPackageMetadata("vendor_package.A", ApplicationCategoryType.MAPS),
                constructPackageMetadata("third_party_package.MAPS", ApplicationCategoryType.MAPS));

        ResourceSpecificConfiguration resourceSpecificConfig = new ResourceSpecificConfiguration();
        resourceSpecificConfig.setIoOveruseConfiguration(ioOveruseConfig);
        config.resourceSpecificConfigurations = Collections.singletonList(resourceSpecificConfig);

        return config;
    }

    private static PackageMetadata constructPackageMetadata(
            String packageName, @ApplicationCategoryType int appCategoryType) {
        PackageMetadata metadata = new PackageMetadata();
        metadata.packageName = packageName;
        metadata.appCategoryType = appCategoryType;
        return metadata;
    }

    private static UserPackageIoUsageStats constructUserPackageIoUsageStats(
            int userId, String packageName, android.automotive.watchdog.PerStateBytes writtenBytes,
            android.automotive.watchdog.PerStateBytes forgivenWriteBytes, int totalOveruses) {
        UserPackageIoUsageStats stats = new UserPackageIoUsageStats();
        stats.userId = userId;
        stats.packageName = packageName;
        stats.ioUsageStats = new IoUsageStats();
        stats.ioUsageStats.writtenBytes = writtenBytes;
        stats.ioUsageStats.forgivenWriteBytes = forgivenWriteBytes;
        stats.ioUsageStats.totalOveruses = totalOveruses;
        return stats;
    }

    private static android.automotive.watchdog.internal.IoOveruseConfiguration
            sampleInternalIoOveruseConfiguration(@ComponentType int componentType) {
        String prefix = WatchdogPerfHandler.toComponentTypeStr(componentType).toLowerCase();
        android.automotive.watchdog.internal.IoOveruseConfiguration config =
                new android.automotive.watchdog.internal.IoOveruseConfiguration();
        config.componentLevelThresholds = constructPerStateIoOveruseThreshold(
                WatchdogPerfHandler.toComponentTypeStr(componentType),
                /* fgBytes= */ componentType * 10L, /* bgBytes= */ componentType *  20L,
                /*gmBytes= */ componentType * 30L);
        config.packageSpecificThresholds = Collections.singletonList(
                constructPerStateIoOveruseThreshold(prefix + "_package.A",
                        /* fgBytes= */ componentType * 40L, /* bgBytes= */ componentType * 50L,
                        /* gmBytes= */ componentType * 60L));
        config.categorySpecificThresholds = Arrays.asList(
                constructPerStateIoOveruseThreshold(
                        WatchdogPerfHandler.INTERNAL_APPLICATION_CATEGORY_TYPE_MEDIA,
                        /* fgBytes= */ componentType * 100L, /* bgBytes= */ componentType * 200L,
                        /* gmBytes= */ componentType * 300L),
                constructPerStateIoOveruseThreshold(
                        WatchdogPerfHandler.INTERNAL_APPLICATION_CATEGORY_TYPE_MAPS,
                        /* fgBytes= */ componentType * 1100L, /* bgBytes= */ componentType * 2200L,
                        /* gmBytes= */ componentType * 3300L));
        config.systemWideThresholds = Collections.singletonList(
                constructInternalIoOveruseAlertThreshold(
                        /* duration= */ componentType * 10L, /* writeBPS= */ componentType * 200L));
        return config;
    }

    private static android.automotive.watchdog.internal.IoOveruseAlertThreshold
            constructInternalIoOveruseAlertThreshold(long duration, long writeBPS) {
        android.automotive.watchdog.internal.IoOveruseAlertThreshold threshold =
                new android.automotive.watchdog.internal.IoOveruseAlertThreshold();
        threshold.durationInSeconds = duration;
        threshold.writtenBytesPerSecond = writeBPS;
        return threshold;
    }

    private static PerStateIoOveruseThreshold constructPerStateIoOveruseThreshold(String name,
            long fgBytes, long bgBytes, long gmBytes) {
        PerStateIoOveruseThreshold threshold = new PerStateIoOveruseThreshold();
        threshold.name = name;
        threshold.perStateWriteBytes = new android.automotive.watchdog.PerStateBytes();
        threshold.perStateWriteBytes.foregroundBytes = fgBytes;
        threshold.perStateWriteBytes.backgroundBytes = bgBytes;
        threshold.perStateWriteBytes.garageModeBytes = gmBytes;
        return threshold;
    }

    private List<android.automotive.watchdog.internal.ResourceOveruseConfiguration>
            captureOnSetResourceOveruseConfigurations(int wantedInvocations) throws Exception {
        verify(mMockCarWatchdogDaemonHelper, times(wantedInvocations))
                .updateResourceOveruseConfigurations(
                        mResourceOveruseConfigurationsCaptor.capture());
        return mResourceOveruseConfigurationsCaptor.getValue();
    }

    static List<ResourceOveruseConfiguration> sampleResourceOveruseConfigurations() {
        return Arrays.asList(
                sampleResourceOveruseConfigurationBuilder(ComponentType.SYSTEM,
                        sampleIoOveruseConfigurationBuilder(ComponentType.SYSTEM).build()).build(),
                sampleResourceOveruseConfigurationBuilder(ComponentType.VENDOR,
                        sampleIoOveruseConfigurationBuilder(ComponentType.VENDOR).build()).build(),
                sampleResourceOveruseConfigurationBuilder(ComponentType.THIRD_PARTY,
                        sampleIoOveruseConfigurationBuilder(ComponentType.THIRD_PARTY).build())
                        .build());
    }

    private static IoOveruseConfiguration.Builder sampleIoOveruseConfigurationBuilder(
            @ComponentType int componentType) {
        String prefix = WatchdogPerfHandler.toComponentTypeStr(componentType).toLowerCase();
        PerStateBytes componentLevelThresholds = new PerStateBytes(
                /* foregroundModeBytes= */ componentType * 10L,
                /* backgroundModeBytes= */ componentType * 20L,
                /* garageModeBytes= */ componentType * 30L);
        Map<String, PerStateBytes> packageSpecificThresholds = new ArrayMap<>();
        packageSpecificThresholds.put(prefix + "_package.A", new PerStateBytes(
                /* foregroundModeBytes= */ componentType * 40L,
                /* backgroundModeBytes= */ componentType * 50L,
                /* garageModeBytes= */ componentType * 60L));

        Map<String, PerStateBytes> appCategorySpecificThresholds = new ArrayMap<>();
        appCategorySpecificThresholds.put(
                ResourceOveruseConfiguration.APPLICATION_CATEGORY_TYPE_MEDIA,
                new PerStateBytes(/* foregroundModeBytes= */ componentType * 100L,
                        /* backgroundModeBytes= */ componentType * 200L,
                        /* garageModeBytes= */ componentType * 300L));
        appCategorySpecificThresholds.put(
                ResourceOveruseConfiguration.APPLICATION_CATEGORY_TYPE_MAPS,
                new PerStateBytes(/* foregroundModeBytes= */ componentType * 1100L,
                        /* backgroundModeBytes= */ componentType * 2200L,
                        /* garageModeBytes= */ componentType * 3300L));

        List<IoOveruseAlertThreshold> systemWideThresholds = Collections.singletonList(
                new IoOveruseAlertThreshold(/* durationInSeconds= */ componentType * 10L,
                        /* writtenBytesPerSecond= */ componentType * 200L));

        return new IoOveruseConfiguration.Builder(componentLevelThresholds,
                packageSpecificThresholds, appCategorySpecificThresholds, systemWideThresholds);
    }

    private static ResourceOveruseConfiguration.Builder sampleResourceOveruseConfigurationBuilder(
            @ComponentType int componentType, IoOveruseConfiguration ioOveruseConfig) {
        String prefix = WatchdogPerfHandler.toComponentTypeStr(componentType).toLowerCase();
        List<String> safeToKill = Arrays.asList(prefix + "_package.non_critical.A",
                prefix + "_pkg.non_critical.B",
                "shared:" + prefix + "_shared_package.non_critical.B",
                "some_pkg_as_" + prefix + "_pkg");
        List<String> vendorPrefixes = Arrays.asList(
                prefix + "_package", "some_pkg_as_" + prefix + "_pkg");
        Map<String, String> pkgToAppCategory = new ArrayMap<>();
        pkgToAppCategory.put("system_package.MEDIA", "android.car.watchdog.app.category.MEDIA");
        pkgToAppCategory.put("system_package.A", "android.car.watchdog.app.category.MAPS");
        pkgToAppCategory.put("vendor_package.MEDIA", "android.car.watchdog.app.category.MEDIA");
        pkgToAppCategory.put("vendor_package.A", "android.car.watchdog.app.category.MAPS");
        pkgToAppCategory.put("third_party_package.MAPS", "android.car.watchdog.app.category.MAPS");
        ResourceOveruseConfiguration.Builder configBuilder =
                new ResourceOveruseConfiguration.Builder(componentType, safeToKill,
                        vendorPrefixes, pkgToAppCategory);
        configBuilder.setIoOveruseConfiguration(ioOveruseConfig);
        return configBuilder;
    }

    private void captureCarUxRestrictionsChangeListener(int wantedInvocations) {
        verify(mMockCarUxRestrictionsManagerService, times(wantedInvocations))
                .getCurrentUxRestrictions();
        verify(mMockCarUxRestrictionsManagerService, times(wantedInvocations))
                .registerUxRestrictionsChangeListener(
                        mICarUxRestrictionsChangeListenerCaptor.capture(),
                        eq(Display.DEFAULT_DISPLAY));
        mCarUxRestrictionsChangeListener = mICarUxRestrictionsChangeListenerCaptor.getValue();
        assertWithMessage("UX restrictions change listener").that(mCarUxRestrictionsChangeListener)
                .isNotNull();
    }

    private void captureStatsPullAtomCallback(int wantedInvocations) {
        verify(mMockStatsManager, times(wantedInvocations)).setPullAtomCallback(
                eq(CAR_WATCHDOG_SYSTEM_IO_USAGE_SUMMARY), any(StatsManager.PullAtomMetadata.class),
                any(Executor.class), mStatsPullAtomCallbackCaptor.capture());
        verify(mMockStatsManager, times(wantedInvocations)).setPullAtomCallback(
                eq(CAR_WATCHDOG_UID_IO_USAGE_SUMMARY), any(StatsManager.PullAtomMetadata.class),
                any(Executor.class), mStatsPullAtomCallbackCaptor.capture());

        // The same callback is set in the above calls, so fetch the latest captured callback.
        mStatsPullAtomCallback = mStatsPullAtomCallbackCaptor.getValue();
        assertWithMessage("Stats pull atom callback").that(mStatsPullAtomCallback).isNotNull();
    }

    private void verifyDatabaseInit(int wantedInvocations) throws Exception {
        /*
         * Database read is posted on a separate handler thread. Wait until the handler thread has
         * processed the database read request before verifying.
         */
        CarServiceUtils.runEmptyRunnableOnLooperSync(CAR_WATCHDOG_SERVICE_NAME);
        verify(mSpiedWatchdogStorage, times(wantedInvocations)).syncUsers(any());
        verify(mSpiedWatchdogStorage, times(wantedInvocations)).getUserPackageSettings();
        verify(mSpiedWatchdogStorage, times(wantedInvocations)).getTodayIoUsageStats();
        verify(mSpiedWatchdogStorage, times(wantedInvocations)).getNotForgivenHistoricalIoOveruses(
                RECURRING_OVERUSE_PERIOD_IN_DAYS);
    }

    static IResourceOveruseListener createMockResourceOveruseListener() {
        IResourceOveruseListener listener = mock(IResourceOveruseListener.Stub.class);
        when(listener.asBinder()).thenCallRealMethod();
        return listener;
    }

    private SparseArray<PackageIoOveruseStats> injectIoOveruseStatsForPackages(
            SparseArray<String> genericPackageNameByUid, Set<String> killablePackages,
            Set<String> shouldNotifyPackages) throws Exception {
        SparseArray<PackageIoOveruseStats> packageIoOveruseStatsByUid = new SparseArray<>();
        List<PackageIoOveruseStats> packageIoOveruseStats = new ArrayList<>();
        for (int i = 0; i < genericPackageNameByUid.size(); ++i) {
            String name = genericPackageNameByUid.valueAt(i);
            int uid = genericPackageNameByUid.keyAt(i);
            PackageIoOveruseStats stats = constructPackageIoOveruseStats(uid,
                    shouldNotifyPackages.contains(name),
                    constructPerStateBytes(80, 147, 213),
                    constructInternalIoOveruseStats(killablePackages.contains(name),
                            /* remainingWriteBytes= */
                            constructPerStateBytes(/* fgBytes= */ 20, /* bgBytes= */
                                    20, /* gmBytes= */ 20),
                            /* writtenBytes= */
                            constructPerStateBytes(/* fgBytes= */ 100, /* bgBytes= */
                                    200, /* gmBytes= */ 300),
                            /* totalOveruses= */ 3));
            packageIoOveruseStatsByUid.put(uid, stats);
            packageIoOveruseStats.add(stats);
        }
        pushLatestIoOveruseStatsAndWait(packageIoOveruseStats);
        return packageIoOveruseStatsByUid;
    }

    static PackageIoOveruseStats constructPackageIoOveruseStats(int uid,
            boolean shouldNotify, android.automotive.watchdog.PerStateBytes forgivenWriteBytes,
            android.automotive.watchdog.IoOveruseStats ioOveruseStats) {
        PackageIoOveruseStats stats = new PackageIoOveruseStats();
        stats.uid = uid;
        stats.shouldNotify = shouldNotify;
        stats.forgivenWriteBytes = forgivenWriteBytes;
        stats.ioOveruseStats = ioOveruseStats;
        return stats;
    }

    private android.automotive.watchdog.IoOveruseStats constructInternalIoOveruseStats(
            boolean killableOnOveruse,
            android.automotive.watchdog.PerStateBytes remainingWriteBytes,
            android.automotive.watchdog.PerStateBytes writtenBytes, int totalOveruses) {
        return constructInternalIoOveruseStats(killableOnOveruse, STATS_DURATION_SECONDS,
                remainingWriteBytes, writtenBytes, totalOveruses);
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

    private void pushLatestIoOveruseStatsAndWait(List<PackageIoOveruseStats> packageIoOveruseStats)
            throws Exception {
        mWatchdogPerfHandler.latestIoOveruseStats(packageIoOveruseStats);

        // Handling latest I/O overuse stats is done on the CarWatchdogService service handler
        // thread. Wait until the below message is processed before returning, so the
        // latestIoOveruseStats call is completed.
        CarServiceUtils.runEmptyRunnableOnLooperSync(CAR_WATCHDOG_SERVICE_NAME);

        // Resource overuse handling is done on the main thread by posting a new message with
        // OVERUSE_HANDLING_DELAY_MILLS delay. Wait until the below message is processed before
        // returning, so the resource overuse handling is completed.
        CarServiceUtils.runOnMainSyncDelayed(() -> {
        }, OVERUSE_HANDLING_DELAY_MILLS * 2);

        if (mGenericPackageNameByUid.size() > 0) {
            verify(mSpiedWatchdogStorage, atLeastOnce()).markDirty();
        }
    }

    private static final class UserNotificationReflectionCall {
        public final UserHandle userHandle;
        public final SparseArray<String> packagesById;
        public final boolean hasHeadsUpNotification;

        UserNotificationReflectionCall(UserHandle userHandle, SparseArray<String> packagesById,
                boolean hasHeadsUpNotification) {
            this.userHandle = userHandle;
            this.packagesById = packagesById;
            this.hasHeadsUpNotification = hasHeadsUpNotification;
        }
    }
}
