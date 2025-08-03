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

package healthconnect.logging;

import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_STORAGE_STATS;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_USAGE_STATS;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.database.DatabaseUtils;
import android.health.HealthFitnessStatsLog;
import android.health.connect.HealthConnectManager;
import android.os.Process;
import android.os.UserHandle;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.server.healthconnect.logging.DailyLoggingService;
import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.storage.datatypehelpers.AccessLogsHelper;
import com.android.server.healthconnect.storage.datatypehelpers.BloodPressureRecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.ChangeLogsHelper;
import com.android.server.healthconnect.storage.datatypehelpers.HeartRateRecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.HeightRecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.PreferenceHelper;
import com.android.server.healthconnect.storage.datatypehelpers.SpeedRecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.StepsRecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.TotalCaloriesBurnedRecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.Vo2MaxRecordHelper;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

public class DailyLoggingServiceTest {

    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule =
            new ExtendedMockitoRule.Builder(this)
                    .mockStatic(HealthFitnessStatsLog.class)
                    .mockStatic(DatabaseUtils.class)
                    .mockStatic(TransactionManager.class)
                    .mockStatic(HealthConnectManager.class)
                    .mockStatic(AccessLogsHelper.class)
                    .mockStatic(PreferenceHelper.class)
                    .setStrictness(Strictness.LENIENT)
                    .build();

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Context mContext;

    @Mock private PackageInfo mPackageInfoConnectedApp;
    @Mock private PackageInfo mPackageInfoNotHoldingPermission;
    @Mock private PackageInfo mPackageInfoNotConnectedApp;
    @Mock private AccessLogsHelper mAccessLogsHelper;
    @Mock private PreferenceHelper mPreferenceHelper;
    private final UserHandle mCurrentUser = Process.myUserHandle();
    private static final String HEALTH_PERMISSION = "HEALTH_PERMISSION";
    private static final String NOT_HEALTH_PERMISSION = "NOT_HEALTH_PERMISSION";
    private static final String USER_MOST_RECENT_ACCESS_LOG_TIME =
            "USER_MOST_RECENT_ACCESS_LOG_TIME";

    @Before
    public void mockStatsLog() {
        ExtendedMockito.doReturn(true)
                .when(() -> HealthConnectManager.isHealthPermission(mContext, HEALTH_PERMISSION));
        ExtendedMockito.doReturn(false)
                .when(
                        () ->
                                HealthConnectManager.isHealthPermission(
                                        mContext, NOT_HEALTH_PERMISSION));
        mPackageInfoConnectedApp.requestedPermissions = new String[] {HEALTH_PERMISSION};
        mPackageInfoConnectedApp.requestedPermissionsFlags =
                new int[] {PackageInfo.REQUESTED_PERMISSION_GRANTED};

        mPackageInfoNotHoldingPermission.requestedPermissions =
                new String[] {NOT_HEALTH_PERMISSION};
        mPackageInfoNotHoldingPermission.requestedPermissionsFlags =
                new int[] {PackageInfo.REQUESTED_PERMISSION_GRANTED};

        mPackageInfoNotConnectedApp.requestedPermissions = new String[] {HEALTH_PERMISSION};
        mPackageInfoNotConnectedApp.requestedPermissionsFlags =
                new int[] {PackageInfo.REQUESTED_PERMISSION_NEVER_FOR_LOCATION};

        when(AccessLogsHelper.getInstance()).thenReturn(mAccessLogsHelper);
        when(PreferenceHelper.getInstance()).thenReturn(mPreferenceHelper);
    }

    @Test
    public void testDatabaseLogsStats() {

        TransactionManager transactionManager = mock(TransactionManager.class);

        ExtendedMockito.doReturn(transactionManager)
                .when(() -> TransactionManager.getInitialisedInstance());

        when(transactionManager.getDatabaseSize(mContext)).thenReturn(1L);

        when(transactionManager.getNumberOfEntriesInTheTable(any())).thenReturn(0L);

        for (String tableName :
                new String[] {
                    ChangeLogsHelper.TABLE_NAME,
                    BloodPressureRecordHelper.BLOOD_PRESSURE_RECORD_TABLE_NAME,
                    HeightRecordHelper.HEIGHT_RECORD_TABLE_NAME,
                    Vo2MaxRecordHelper.VO2_MAX_RECORD_TABLE_NAME,
                    StepsRecordHelper.STEPS_TABLE_NAME,
                    TotalCaloriesBurnedRecordHelper.TOTAL_CALORIES_BURNED_RECORD_TABLE_NAME,
                    SpeedRecordHelper.TABLE_NAME,
                    HeartRateRecordHelper.TABLE_NAME
                }) {
            doReturn(2L).when(transactionManager).getNumberOfEntriesInTheTable(tableName);
        }

        DailyLoggingService.logDailyMetrics(mContext, mCurrentUser);

        ExtendedMockito.verify(
                () ->
                        HealthFitnessStatsLog.write(
                                eq(HEALTH_CONNECT_STORAGE_STATS),
                                eq(1L), // Database size
                                eq(6L), // Instant Records i.e. 2 for each BloodPressure,
                                // Height, Vo2Max
                                eq(4L), // Interval Records i.e. 2 for each Steps, Total
                                // Calories Burned
                                eq(4L), // Series Records i.e. 2 for each Speed, Heart Rate
                                eq(2L)), // 2 Changelog records
                times(1));
    }

    @Test
    public void testDatabaseLogsStats_userDoesNotUseHealthConnect() {

        TransactionManager transactionManager = mock(TransactionManager.class);

        ExtendedMockito.doReturn(transactionManager)
                .when(() -> TransactionManager.getInitialisedInstance());

        when(transactionManager.getDatabaseSize(mContext)).thenReturn(1L);

        when(transactionManager.getNumberOfEntriesInTheTable(any())).thenReturn(0L);

        for (String tableName :
                new String[] {
                    ChangeLogsHelper.TABLE_NAME,
                    BloodPressureRecordHelper.BLOOD_PRESSURE_RECORD_TABLE_NAME,
                    HeightRecordHelper.HEIGHT_RECORD_TABLE_NAME,
                    Vo2MaxRecordHelper.VO2_MAX_RECORD_TABLE_NAME,
                    StepsRecordHelper.STEPS_TABLE_NAME,
                    TotalCaloriesBurnedRecordHelper.TOTAL_CALORIES_BURNED_RECORD_TABLE_NAME,
                    SpeedRecordHelper.TABLE_NAME,
                    HeartRateRecordHelper.TABLE_NAME
                }) {
            doReturn(0L).when(transactionManager).getNumberOfEntriesInTheTable(tableName);
        }

        DailyLoggingService.logDailyMetrics(mContext, mCurrentUser);

        ExtendedMockito.verify(
                () ->
                        HealthFitnessStatsLog.write(
                                eq(HEALTH_CONNECT_STORAGE_STATS),
                                anyLong(),
                                anyLong(),
                                anyLong(),
                                anyLong(),
                                anyLong()),
                never());
    }

    @Test
    public void testDailyUsageStatsLogs_oneConnected_oneAvailable_oneNotAvailableApp() {

        when(mContext.createContextAsUser(mCurrentUser, 0)
                        .getPackageManager()
                        .getInstalledPackages(any()))
                .thenReturn(List.of(mPackageInfoConnectedApp, mPackageInfoNotHoldingPermission));
        when(mAccessLogsHelper.getLatestAccessLogTimeStamp())
                .thenReturn(subtractDaysFromInstantNow(/* numberOfDays= */ 0));
        when(mPreferenceHelper.getPreference(USER_MOST_RECENT_ACCESS_LOG_TIME))
                .thenReturn(String.valueOf(subtractDaysFromInstantNow(/* numberOfDays= */ 0)));

        DailyLoggingService.logDailyMetrics(mContext, mCurrentUser);

        // Makes sure we do not have count any app that does not have Health Connect permission
        // declared in the manifest as a connected or an available app.
        ExtendedMockito.verify(
                () ->
                        HealthFitnessStatsLog.write(
                                eq(HEALTH_CONNECT_USAGE_STATS), eq(1), eq(1), eq(true)),
                times(1));
    }

    @Test
    public void testDailyUsageStatsLogs_oneConnected_oneAvailableApp() {

        when(mContext.createContextAsUser(mCurrentUser, 0)
                        .getPackageManager()
                        .getInstalledPackages(any()))
                .thenReturn(List.of(mPackageInfoConnectedApp));
        when(mAccessLogsHelper.getLatestAccessLogTimeStamp())
                .thenReturn(subtractDaysFromInstantNow(/* numberOfDays= */ 0));
        when(mPreferenceHelper.getPreference(USER_MOST_RECENT_ACCESS_LOG_TIME))
                .thenReturn(String.valueOf(subtractDaysFromInstantNow(/* numberOfDays= */ 0)));

        DailyLoggingService.logDailyMetrics(mContext, mCurrentUser);

        ExtendedMockito.verify(
                () ->
                        HealthFitnessStatsLog.write(
                                eq(HEALTH_CONNECT_USAGE_STATS), eq(1), eq(1), eq(true)),
                times(1));
    }

    @Test
    public void testDailyUsageStatsLogs_zeroConnected_twoAvailableApps() {

        when(mContext.createContextAsUser(mCurrentUser, 0)
                        .getPackageManager()
                        .getInstalledPackages(any()))
                .thenReturn(List.of(mPackageInfoNotConnectedApp, mPackageInfoNotConnectedApp));
        when(mAccessLogsHelper.getLatestAccessLogTimeStamp())
                .thenReturn(subtractDaysFromInstantNow(/* numberOfDays= */ 31));
        when(mPreferenceHelper.getPreference(USER_MOST_RECENT_ACCESS_LOG_TIME))
                .thenReturn(String.valueOf(subtractDaysFromInstantNow(/* numberOfDays= */ 31)));

        DailyLoggingService.logDailyMetrics(mContext, mCurrentUser);

        ExtendedMockito.verify(
                () ->
                        HealthFitnessStatsLog.write(
                                eq(HEALTH_CONNECT_USAGE_STATS), eq(0), eq(2), eq(false)),
                times(1));
    }

    @Test
    public void testDailyUsageStatsLogs_zeroConnected_zeroAvailableApps() {

        when(mContext.createContextAsUser(mCurrentUser, 0)
                        .getPackageManager()
                        .getInstalledPackages(any()))
                .thenReturn(List.of(mPackageInfoNotHoldingPermission));
        when(mAccessLogsHelper.getLatestAccessLogTimeStamp())
                .thenReturn(subtractDaysFromInstantNow(/* numberOfDays= */ 1));
        when(mPreferenceHelper.getPreference(USER_MOST_RECENT_ACCESS_LOG_TIME))
                .thenReturn(String.valueOf(subtractDaysFromInstantNow(/* numberOfDays= */ 1)));

        DailyLoggingService.logDailyMetrics(mContext, mCurrentUser);

        ExtendedMockito.verify(
                () ->
                        HealthFitnessStatsLog.write(
                                eq(HEALTH_CONNECT_USAGE_STATS), eq(0), eq(0), eq(true)),
                times(1));
    }

    @Test
    public void testDailyUsageStatsLogs_healthConnectAccessedPreviousDay_userMonthlyActive() {
        when(mAccessLogsHelper.getLatestAccessLogTimeStamp())
                .thenReturn(subtractDaysFromInstantNow(/* numberOfDays= */ 1));
        when(mPreferenceHelper.getPreference(USER_MOST_RECENT_ACCESS_LOG_TIME))
                .thenReturn(String.valueOf(subtractDaysFromInstantNow(/* numberOfDays= */ 1)));
        DailyLoggingService.logDailyMetrics(mContext, mCurrentUser);

        ExtendedMockito.verify(
                () ->
                        HealthFitnessStatsLog.write(
                                eq(HEALTH_CONNECT_USAGE_STATS), eq(0), eq(0), eq(true)),
                times(1));
    }

    @Test
    public void testDailyUsageStatsLogs_healthConnectAccessed31DaysAgo_userNotMonthlyActive() {
        when(mContext.createContextAsUser(mCurrentUser, 0)
                        .getPackageManager()
                        .getInstalledPackages(any()))
                .thenReturn(List.of(mPackageInfoNotConnectedApp, mPackageInfoNotConnectedApp));
        when(mAccessLogsHelper.getLatestAccessLogTimeStamp())
                .thenReturn(subtractDaysFromInstantNow(/* numberOfDays= */ 31));
        when(mPreferenceHelper.getPreference(USER_MOST_RECENT_ACCESS_LOG_TIME))
                .thenReturn(String.valueOf(subtractDaysFromInstantNow(/* numberOfDays= */ 31)));
        DailyLoggingService.logDailyMetrics(mContext, mCurrentUser);

        ExtendedMockito.verify(
                () ->
                        HealthFitnessStatsLog.write(
                                eq(HEALTH_CONNECT_USAGE_STATS), eq(0), eq(2), eq(false)),
                times(1));
    }

    @Test
    public void testDailyUsageStatsLogs_userDoesNotUseHealthConnect() {
        when(mAccessLogsHelper.getLatestAccessLogTimeStamp())
                .thenReturn(subtractDaysFromInstantNow(/* numberOfDays= */ 31));
        when(mPreferenceHelper.getPreference(USER_MOST_RECENT_ACCESS_LOG_TIME))
                .thenReturn(String.valueOf(subtractDaysFromInstantNow(/* numberOfDays= */ 31)));
        DailyLoggingService.logDailyMetrics(mContext, mCurrentUser);

        ExtendedMockito.verify(
                () ->
                        HealthFitnessStatsLog.write(
                                eq(HEALTH_CONNECT_USAGE_STATS), anyInt(), anyInt(), anyBoolean()),
                never());
    }

    private long subtractDaysFromInstantNow(int numberOfDays) {
        return Instant.now().minus(numberOfDays, ChronoUnit.DAYS).toEpochMilli();
    }
}
