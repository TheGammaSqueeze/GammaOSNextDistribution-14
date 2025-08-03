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

package com.android.server.healthconnect.permission;

import static com.android.server.healthconnect.TestUtils.getInternalBackgroundExecutorTaskCount;
import static com.android.server.healthconnect.TestUtils.waitForAllScheduledTasksToComplete;
import static com.android.server.healthconnect.permission.FirstGrantTimeDatastore.DATA_TYPE_CURRENT;
import static com.android.server.healthconnect.permission.FirstGrantTimeDatastore.DATA_TYPE_STAGED;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.UiAutomation;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.health.connect.HealthConnectException;
import android.health.connect.HealthConnectManager;
import android.health.connect.ReadRecordsRequest;
import android.health.connect.ReadRecordsRequestUsingFilters;
import android.health.connect.ReadRecordsResponse;
import android.health.connect.TimeInstantRangeFilter;
import android.health.connect.datatypes.Record;
import android.health.connect.datatypes.StepsRecord;
import android.os.OutcomeReceiver;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Pair;

import androidx.test.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

// TODO(b/261432978): add test for sharedUser backup
public class FirstGrantTimeUnitTest {

    private static final String SELF_PACKAGE_NAME = "com.android.healthconnect.unittests";
    private static final UserHandle CURRENT_USER = Process.myUserHandle();

    private static final int DEFAULT_VERSION = 1;

    @Mock private HealthPermissionIntentAppsTracker mTracker;
    @Mock private PackageManager mPackageManager;
    @Mock private UserManager mUserManager;
    @Mock private Context mContext;
    @Mock private FirstGrantTimeDatastore mDatastore;
    @Mock private PackageInfoUtils mPackageInfoUtils;

    private FirstGrantTimeManager mGrantTimeManager;

    private final UiAutomation mUiAutomation =
            InstrumentationRegistry.getInstrumentation().getUiAutomation();

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getContext();
        MockitoAnnotations.initMocks(this);
        when(mDatastore.readForUser(CURRENT_USER, DATA_TYPE_CURRENT))
                .thenReturn(new UserGrantTimeState(DEFAULT_VERSION));
        when(mDatastore.readForUser(CURRENT_USER, DATA_TYPE_STAGED))
                .thenReturn(new UserGrantTimeState(DEFAULT_VERSION));
        when(mTracker.supportsPermissionUsageIntent(SELF_PACKAGE_NAME, CURRENT_USER))
                .thenReturn(true);
        when(mContext.createContextAsUser(any(), anyInt())).thenReturn(context);
        when(mContext.getApplicationContext()).thenReturn(context);
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        when(mContext.getSystemService(UserManager.class)).thenReturn(mUserManager);
        when(mUserManager.isUserUnlocked()).thenReturn(true);
        PackageInfoUtils.setInstanceForTest(/* instance= */ null);

        mUiAutomation.adoptShellPermissionIdentity(
                "android.permission.OBSERVE_GRANT_REVOKE_PERMISSIONS");
        mGrantTimeManager = new FirstGrantTimeManager(mContext, mTracker, mDatastore);
    }

    @After
    public void tearDown() throws Exception {
        waitForAllScheduledTasksToComplete();
    }

    @Test
    public void testSetFirstGrantTimeForAnApp_expectOtherAppsGrantTimesRemained() {
        Instant instant1 = Instant.parse("2023-02-11T10:00:00Z");
        Instant instant2 = Instant.parse("2023-02-12T10:00:00Z");
        Instant instant3 = Instant.parse("2023-02-13T10:00:00Z");
        String anotherPackage = "another.package";
        // mock PackageInfoUtils
        List<Pair<String, Integer>> packageNameAndUidPairs =
                Arrays.asList(new Pair<>(SELF_PACKAGE_NAME, 0), new Pair<>(anotherPackage, 1));
        PackageInfoUtils.setInstanceForTest(mPackageInfoUtils);
        List<PackageInfo> packageInfos = new ArrayList<>();
        for (Pair<String, Integer> pair : packageNameAndUidPairs) {
            String packageName = pair.first;
            int uid = pair.second;
            PackageInfo packageInfo = new PackageInfo();
            packageInfo.packageName = packageName;
            packageInfos.add(packageInfo);
            when(mPackageInfoUtils.getPackageUid(
                            eq(packageName), any(UserHandle.class), any(Context.class)))
                    .thenReturn(uid);
            when(mPackageInfoUtils.getPackageNameFromUid(eq(uid))).thenReturn(packageName);
        }
        when(mPackageInfoUtils.getPackagesHoldingHealthPermissions(
                        any(UserHandle.class), any(Context.class)))
                .thenReturn(packageInfos);
        // mock initial storage
        mGrantTimeManager = new FirstGrantTimeManager(mContext, mTracker, mDatastore);
        UserGrantTimeState currentGrantTimeState = new UserGrantTimeState(DEFAULT_VERSION);
        currentGrantTimeState.setPackageGrantTime(SELF_PACKAGE_NAME, instant1);
        currentGrantTimeState.setPackageGrantTime(anotherPackage, instant2);
        when(mDatastore.readForUser(CURRENT_USER, DATA_TYPE_CURRENT))
                .thenReturn(currentGrantTimeState);
        // mock permission intent tracker
        when(mTracker.supportsPermissionUsageIntent(anyString(), ArgumentMatchers.any()))
                .thenReturn(true);
        ArgumentCaptor<UserGrantTimeState> captor =
                ArgumentCaptor.forClass(UserGrantTimeState.class);

        assertThat(mGrantTimeManager.getFirstGrantTime(SELF_PACKAGE_NAME, CURRENT_USER))
                .isEqualTo(instant1);
        assertThat(mGrantTimeManager.getFirstGrantTime(anotherPackage, CURRENT_USER))
                .isEqualTo(instant2);

        mGrantTimeManager.setFirstGrantTime(SELF_PACKAGE_NAME, instant3, CURRENT_USER);
        verify(mDatastore).writeForUser(captor.capture(), eq(CURRENT_USER), anyInt());

        UserGrantTimeState newUserGrantTimeState = captor.getValue();
        assertThat(newUserGrantTimeState.getPackageGrantTimes().keySet()).hasSize(2);
        assertThat(newUserGrantTimeState.getPackageGrantTimes().get(SELF_PACKAGE_NAME))
                .isEqualTo(instant3);
        assertThat(newUserGrantTimeState.getPackageGrantTimes().get(anotherPackage))
                .isEqualTo(instant2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testUnknownPackage_throwsException() {
        mGrantTimeManager.getFirstGrantTime("android.unknown_package", CURRENT_USER);
    }

    @Test
    public void testCurrentPackage_intentNotSupported_grantTimeIsNull() {
        when(mTracker.supportsPermissionUsageIntent(SELF_PACKAGE_NAME, CURRENT_USER))
                .thenReturn(false);
        assertThat(mGrantTimeManager.getFirstGrantTime(SELF_PACKAGE_NAME, CURRENT_USER)).isNull();
    }

    @Test
    public void testOnPermissionsChangedCalledWhileDeviceIsLocked_getGrantTimeNotNullAfterUnlock()
            throws TimeoutException {
        // before device is unlocked
        when(mUserManager.isUserUnlocked()).thenReturn(false);
        when(mDatastore.readForUser(CURRENT_USER, DATA_TYPE_CURRENT)).thenReturn(null);
        when(mDatastore.readForUser(CURRENT_USER, DATA_TYPE_STAGED)).thenReturn(null);
        int uid = 123;
        String[] packageNames = {"package.name"};
        when(mPackageManager.getPackagesForUid(uid)).thenReturn(packageNames);
        when(mTracker.supportsPermissionUsageIntent(eq(packageNames[0]), ArgumentMatchers.any()))
                .thenReturn(true);
        mGrantTimeManager.onPermissionsChanged(uid);
        waitForAllScheduledTasksToComplete();
        // after device is unlocked
        when(mUserManager.isUserUnlocked()).thenReturn(true);
        UserGrantTimeState currentGrantTimeState = new UserGrantTimeState(DEFAULT_VERSION);
        Instant now = Instant.parse("2023-02-14T10:00:00Z");
        currentGrantTimeState.setPackageGrantTime(SELF_PACKAGE_NAME, now);
        when(mDatastore.readForUser(CURRENT_USER, DATA_TYPE_CURRENT))
                .thenReturn(currentGrantTimeState);

        assertThat(mGrantTimeManager.getFirstGrantTime(SELF_PACKAGE_NAME, CURRENT_USER))
                .isEqualTo(now);
    }

    @Test
    public void testOnPermissionsChangedCalled_withHealthPermissionsUid_expectBackgroundTaskAdded()
            throws TimeoutException {
        long currentTaskCount = getInternalBackgroundExecutorTaskCount();
        waitForAllScheduledTasksToComplete();
        int uid = 123;
        String[] packageNames = {"package.name"};
        when(mPackageManager.getPackagesForUid(uid)).thenReturn(packageNames);
        when(mTracker.supportsPermissionUsageIntent(eq(packageNames[0]), ArgumentMatchers.any()))
                .thenReturn(true);

        mGrantTimeManager.onPermissionsChanged(uid);
        waitForAllScheduledTasksToComplete();

        assertThat(getInternalBackgroundExecutorTaskCount()).isEqualTo(currentTaskCount + 1);
    }

    @Test
    public void
            testOnPermissionsChangedCalled_withNoHealthPermissionsUid_expectNoBackgroundTaskAdded()
                    throws TimeoutException {
        long currentTaskCount = getInternalBackgroundExecutorTaskCount();
        waitForAllScheduledTasksToComplete();
        int uid = 123;
        String[] packageNames = {"package.name"};
        when(mPackageManager.getPackagesForUid(uid)).thenReturn(packageNames);
        when(mTracker.supportsPermissionUsageIntent(eq(packageNames[0]), ArgumentMatchers.any()))
                .thenReturn(false);

        mGrantTimeManager.onPermissionsChanged(uid);
        waitForAllScheduledTasksToComplete();

        assertThat(getInternalBackgroundExecutorTaskCount()).isEqualTo(currentTaskCount);
    }

    @Test
    @Ignore("b/312712918 this test is flaky")
    public void testCurrentPackage_intentSupported_grantTimeIsNotNull() {
        assertThat(mGrantTimeManager.getFirstGrantTime(SELF_PACKAGE_NAME, CURRENT_USER))
                .isNotNull();
        assertThat(mGrantTimeManager.getFirstGrantTime(SELF_PACKAGE_NAME, CURRENT_USER))
                .isGreaterThan(Instant.now().minusSeconds((long) 1e3));
        assertThat(mGrantTimeManager.getFirstGrantTime(SELF_PACKAGE_NAME, CURRENT_USER))
                .isLessThan(Instant.now().plusSeconds((long) 1e3));
        verify(mDatastore)
                .writeForUser(
                        ArgumentMatchers.any(),
                        ArgumentMatchers.eq(CURRENT_USER),
                        ArgumentMatchers.eq(DATA_TYPE_CURRENT));
        verify(mDatastore)
                .readForUser(
                        ArgumentMatchers.eq(CURRENT_USER), ArgumentMatchers.eq(DATA_TYPE_CURRENT));
    }

    @Test
    public void testCurrentPackage_noGrantTimeBackupBecameAvailable_grantTimeEqualToStaged() {
        assertThat(mGrantTimeManager.getFirstGrantTime(SELF_PACKAGE_NAME, CURRENT_USER))
                .isNotNull();
        Instant backupTime = Instant.now().minusSeconds((long) 1e5);
        UserGrantTimeState stagedState = setupGrantTimeState(null, backupTime);
        mGrantTimeManager.applyAndStageBackupDataForUser(CURRENT_USER, stagedState);
        assertThat(mGrantTimeManager.getFirstGrantTime(SELF_PACKAGE_NAME, CURRENT_USER))
                .isEqualTo(backupTime);
    }

    @Test
    @Ignore("b/312712918 this test is flaky")
    public void testCurrentPackage_noBackup_useRecordedTime() {
        Instant stateTime = Instant.now().minusSeconds((long) 1e5);
        UserGrantTimeState stagedState = setupGrantTimeState(stateTime, null);

        assertThat(mGrantTimeManager.getFirstGrantTime(SELF_PACKAGE_NAME, CURRENT_USER))
                .isEqualTo(stateTime);
        mGrantTimeManager.applyAndStageBackupDataForUser(CURRENT_USER, stagedState);
        assertThat(mGrantTimeManager.getFirstGrantTime(SELF_PACKAGE_NAME, CURRENT_USER))
                .isEqualTo(stateTime);
    }

    @Test
    @Ignore("b/312712918 this test is flaky")
    public void testCurrentPackage_noBackup_grantTimeEqualToStaged() {
        Instant backupTime = Instant.now().minusSeconds((long) 1e5);
        Instant stateTime = backupTime.plusSeconds(10);
        UserGrantTimeState stagedState = setupGrantTimeState(stateTime, backupTime);

        mGrantTimeManager.applyAndStageBackupDataForUser(CURRENT_USER, stagedState);
        assertThat(mGrantTimeManager.getFirstGrantTime(SELF_PACKAGE_NAME, CURRENT_USER))
                .isEqualTo(backupTime);
    }

    @Test
    public void testCurrentPackage_backupDataLater_stagedDataSkipped() {
        Instant stateTime = Instant.now().minusSeconds((long) 1e5);
        UserGrantTimeState stagedState = setupGrantTimeState(stateTime, stateTime.plusSeconds(1));

        mGrantTimeManager.applyAndStageBackupDataForUser(CURRENT_USER, stagedState);
        assertThat(mGrantTimeManager.getFirstGrantTime(SELF_PACKAGE_NAME, CURRENT_USER))
                .isEqualTo(stateTime);
    }

    @Test
    public void testWriteStagedData_getStagedStateForCurrentPackage_returnsCorrectState() {
        Instant stateTime = Instant.now().minusSeconds((long) 1e5);
        setupGrantTimeState(stateTime, null);

        UserGrantTimeState state = mGrantTimeManager.createBackupState(CURRENT_USER);
        assertThat(state.getSharedUserGrantTimes()).isEmpty();
        assertThat(state.getPackageGrantTimes().containsKey(SELF_PACKAGE_NAME)).isTrue();
        assertThat(state.getPackageGrantTimes().get(SELF_PACKAGE_NAME)).isEqualTo(stateTime);
    }

    @Test(expected = HealthConnectException.class)
    public <T extends Record> void testReadRecords_withNoIntent_throwsException()
            throws InterruptedException {
        TimeInstantRangeFilter filter =
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.now())
                        .setEndTime(Instant.now().plusMillis(3000))
                        .build();
        ReadRecordsRequestUsingFilters<StepsRecord> request =
                new ReadRecordsRequestUsingFilters.Builder<>(StepsRecord.class)
                        .setTimeRangeFilter(filter)
                        .build();
        readRecords(request);
    }

    private UserGrantTimeState setupGrantTimeState(Instant currentTime, Instant stagedTime) {
        if (currentTime != null) {
            UserGrantTimeState state = new UserGrantTimeState(DEFAULT_VERSION);
            state.setPackageGrantTime(SELF_PACKAGE_NAME, currentTime);
            when(mDatastore.readForUser(CURRENT_USER, DATA_TYPE_CURRENT)).thenReturn(state);
        }

        UserGrantTimeState backupState = new UserGrantTimeState(DEFAULT_VERSION);
        if (stagedTime != null) {
            backupState.setPackageGrantTime(SELF_PACKAGE_NAME, stagedTime);
        }
        when(mDatastore.readForUser(CURRENT_USER, DATA_TYPE_STAGED)).thenReturn(backupState);
        return backupState;
    }

    private static <T extends Record> List<T> readRecords(ReadRecordsRequest<T> request)
            throws InterruptedException {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        HealthConnectManager service = context.getSystemService(HealthConnectManager.class);
        CountDownLatch latch = new CountDownLatch(1);
        assertThat(service).isNotNull();
        assertThat(request.getRecordType()).isNotNull();
        AtomicReference<List<T>> response = new AtomicReference<>();
        AtomicReference<HealthConnectException> healthConnectExceptionAtomicReference =
                new AtomicReference<>();
        service.readRecords(
                request,
                Executors.newSingleThreadExecutor(),
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(ReadRecordsResponse<T> result) {
                        response.set(result.getRecords());
                        latch.countDown();
                    }

                    @Override
                    public void onError(HealthConnectException exception) {
                        healthConnectExceptionAtomicReference.set(exception);
                        latch.countDown();
                    }
                });
        assertThat(latch.await(3, TimeUnit.SECONDS)).isEqualTo(true);
        if (healthConnectExceptionAtomicReference.get() != null) {
            throw healthConnectExceptionAtomicReference.get();
        }
        return response.get();
    }
}
