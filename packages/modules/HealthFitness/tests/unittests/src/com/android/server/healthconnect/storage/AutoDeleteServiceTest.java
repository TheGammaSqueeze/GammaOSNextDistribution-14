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

package healthconnect.storage;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.health.connect.datatypes.RecordTypeIdentifier;
import android.util.ArrayMap;

import androidx.test.runner.AndroidJUnit4;

import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.server.healthconnect.storage.AutoDeleteService;
import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.storage.datatypehelpers.AccessLogsHelper;
import com.android.server.healthconnect.storage.datatypehelpers.ActiveCaloriesBurnedRecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.ActivityDateHelper;
import com.android.server.healthconnect.storage.datatypehelpers.AppInfoHelper;
import com.android.server.healthconnect.storage.datatypehelpers.BasalBodyTemperatureRecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.BasalMetabolicRateRecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.BloodGlucoseRecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.BloodPressureRecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.BodyFatRecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.BodyTemperatureRecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.BodyWaterMassRecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.BoneMassRecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.CervicalMucusRecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.ChangeLogsHelper;
import com.android.server.healthconnect.storage.datatypehelpers.ChangeLogsRequestHelper;
import com.android.server.healthconnect.storage.datatypehelpers.CyclingPedalingCadenceRecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.DistanceRecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.ElevationGainedRecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.ExerciseSessionRecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.FloorsClimbedRecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.HealthDataCategoryPriorityHelper;
import com.android.server.healthconnect.storage.datatypehelpers.HeartRateRecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.HeartRateVariabilityRmssdHelper;
import com.android.server.healthconnect.storage.datatypehelpers.HeightRecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.HydrationRecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.IntermenstrualBleedingRecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.LeanBodyMassRecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.MenstruationFlowRecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.MenstruationPeriodRecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.NutritionRecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.OvulationTestRecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.OxygenSaturationRecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.PowerRecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.PreferenceHelper;
import com.android.server.healthconnect.storage.datatypehelpers.RecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.RespiratoryRateRecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.RestingHeartRateRecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.SexualActivityRecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.SkinTemperatureRecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.SleepSessionRecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.SpeedRecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.StepsCadenceRecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.StepsRecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.TotalCaloriesBurnedRecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.Vo2MaxRecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.WeightRecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.WheelchairPushesRecordHelper;
import com.android.server.healthconnect.storage.request.DeleteTableRequest;
import com.android.server.healthconnect.storage.utils.RecordHelperProvider;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
public class AutoDeleteServiceTest {
    private static final String AUTO_DELETE_DURATION_RECORDS_KEY =
            "auto_delete_duration_records_key";

    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule =
            new ExtendedMockitoRule.Builder(this)
                    .mockStatic(PreferenceHelper.class)
                    .mockStatic(TransactionManager.class)
                    .mockStatic(RecordHelperProvider.class)
                    .mockStatic(AppInfoHelper.class)
                    .mockStatic(ActivityDateHelper.class)
                    .mockStatic(HealthDataCategoryPriorityHelper.class)
                    .build();

    @Mock private PreferenceHelper mPreferenceHelper;
    @Mock private TransactionManager mTransactionManager;
    @Mock private RecordHelperProvider mRecordHelperProvider;

    @Mock private AppInfoHelper mAppInfoHelper;
    @Mock private ActivityDateHelper mActivityDateHelper;
    @Mock private HealthDataCategoryPriorityHelper mHealthDataCategoryPriorityHelper;
    @Mock Context mContext;

    @Test
    public void testSetRecordRetentionPeriodInDays() {
        when(PreferenceHelper.getInstance()).thenReturn(mPreferenceHelper);
        AutoDeleteService.setRecordRetentionPeriodInDays(30);

        verify(mPreferenceHelper)
                .insertOrReplacePreference(
                        Mockito.eq(AUTO_DELETE_DURATION_RECORDS_KEY),
                        Mockito.eq(String.valueOf(30)));
    }

    @Test
    public void testStartAutoDelete_getPreferenceReturnNull() {
        when(PreferenceHelper.getInstance()).thenReturn(mPreferenceHelper);
        when(TransactionManager.getInitialisedInstance()).thenReturn(mTransactionManager);
        when(AppInfoHelper.getInstance()).thenReturn(mAppInfoHelper);
        when(ActivityDateHelper.getInstance()).thenReturn(mActivityDateHelper);
        when(mPreferenceHelper.getPreference(AUTO_DELETE_DURATION_RECORDS_KEY)).thenReturn(null);
        when(HealthDataCategoryPriorityHelper.getInstance())
                .thenReturn(mHealthDataCategoryPriorityHelper);

        AutoDeleteService.startAutoDelete(mContext);

        verify(mRecordHelperProvider, never()).getRecordHelpers();
        verify(mTransactionManager, Mockito.times(2))
                .deleteWithoutChangeLogs(
                        Mockito.argThat(
                                (List<DeleteTableRequest> deleteTableRequestsList) ->
                                        checkTableNames_getPreferenceReturnNull(
                                                deleteTableRequestsList)));
        verify(mAppInfoHelper).syncAppInfoRecordTypesUsed();
        verify(mActivityDateHelper).reSyncForAllRecords();
        verify(mHealthDataCategoryPriorityHelper).reSyncHealthDataPriorityTable(mContext);
    }

    @Test
    public void testStartAutoDelete_getPreferenceReturnNonNull() {
        when(PreferenceHelper.getInstance()).thenReturn(mPreferenceHelper);
        when(TransactionManager.getInitialisedInstance()).thenReturn(mTransactionManager);
        when(RecordHelperProvider.getInstance()).thenReturn(mRecordHelperProvider);
        when(AppInfoHelper.getInstance()).thenReturn(mAppInfoHelper);
        when(ActivityDateHelper.getInstance()).thenReturn(mActivityDateHelper);
        when(HealthDataCategoryPriorityHelper.getInstance())
                .thenReturn(mHealthDataCategoryPriorityHelper);

        when(mPreferenceHelper.getPreference(AUTO_DELETE_DURATION_RECORDS_KEY))
                .thenReturn(String.valueOf(30));
        when(mRecordHelperProvider.getRecordHelpers()).thenReturn(getRecordHelpers());

        AutoDeleteService.startAutoDelete(mContext);

        verify(mTransactionManager, Mockito.times(3))
                .deleteWithoutChangeLogs(
                        Mockito.argThat(
                                (List<DeleteTableRequest> deleteTableRequestsList) ->
                                        checkTableNames_getPreferenceReturnNonNull(
                                                deleteTableRequestsList)));
        verify(mAppInfoHelper).syncAppInfoRecordTypesUsed();
        verify(mActivityDateHelper).reSyncForAllRecords();
        verify(mHealthDataCategoryPriorityHelper).reSyncHealthDataPriorityTable(mContext);
    }

    private boolean checkTableNames_getPreferenceReturnNull(List<DeleteTableRequest> list) {
        Set<String> tableNames = new HashSet<>();
        for (DeleteTableRequest request : list) {
            tableNames.add(request.getTableName());
        }
        return (tableNames.equals(getTableNamesForDeletingStaleChangeLogEntries())
                || tableNames.equals(getTableNamesForDeletingStaleAccessLogsEntries()));
    }

    private boolean checkTableNames_getPreferenceReturnNonNull(List<DeleteTableRequest> list) {
        Set<String> tableNames = new HashSet<>();
        for (DeleteTableRequest request : list) {
            tableNames.add(request.getTableName());
        }
        return (tableNames.equals(getTableNamesForDeletingStaleChangeLogEntries())
                || tableNames.equals(getTableNamesForDeletingStaleAccessLogsEntries())
                || tableNames.equals(getTableNamesForDeletingStaleRecordEntries()));
    }

    private Map<Integer, RecordHelper<?>> getRecordHelpers() {
        Map<Integer, RecordHelper<?>> recordIDToHelperMap = new ArrayMap<>();
        recordIDToHelperMap.put(RecordTypeIdentifier.RECORD_TYPE_STEPS, new StepsRecordHelper());
        recordIDToHelperMap.put(
                RecordTypeIdentifier.RECORD_TYPE_DISTANCE, new DistanceRecordHelper());
        recordIDToHelperMap.put(
                RecordTypeIdentifier.RECORD_TYPE_ELEVATION_GAINED,
                new ElevationGainedRecordHelper());
        recordIDToHelperMap.put(
                RecordTypeIdentifier.RECORD_TYPE_ACTIVE_CALORIES_BURNED,
                new ActiveCaloriesBurnedRecordHelper());
        recordIDToHelperMap.put(
                RecordTypeIdentifier.RECORD_TYPE_FLOORS_CLIMBED, new FloorsClimbedRecordHelper());
        recordIDToHelperMap.put(
                RecordTypeIdentifier.RECORD_TYPE_HYDRATION, new HydrationRecordHelper());
        recordIDToHelperMap.put(
                RecordTypeIdentifier.RECORD_TYPE_NUTRITION, new NutritionRecordHelper());
        recordIDToHelperMap.put(
                RecordTypeIdentifier.RECORD_TYPE_WHEELCHAIR_PUSHES,
                new WheelchairPushesRecordHelper());
        recordIDToHelperMap.put(
                RecordTypeIdentifier.RECORD_TYPE_TOTAL_CALORIES_BURNED,
                new TotalCaloriesBurnedRecordHelper());
        recordIDToHelperMap.put(
                RecordTypeIdentifier.RECORD_TYPE_HEART_RATE, new HeartRateRecordHelper());
        recordIDToHelperMap.put(
                RecordTypeIdentifier.RECORD_TYPE_BASAL_METABOLIC_RATE,
                new BasalMetabolicRateRecordHelper());
        recordIDToHelperMap.put(
                RecordTypeIdentifier.RECORD_TYPE_CYCLING_PEDALING_CADENCE,
                new CyclingPedalingCadenceRecordHelper());
        recordIDToHelperMap.put(RecordTypeIdentifier.RECORD_TYPE_POWER, new PowerRecordHelper());
        recordIDToHelperMap.put(RecordTypeIdentifier.RECORD_TYPE_SPEED, new SpeedRecordHelper());
        recordIDToHelperMap.put(
                RecordTypeIdentifier.RECORD_TYPE_STEPS_CADENCE, new StepsCadenceRecordHelper());
        recordIDToHelperMap.put(
                RecordTypeIdentifier.RECORD_TYPE_MENSTRUATION_FLOW,
                new MenstruationFlowRecordHelper());
        recordIDToHelperMap.put(
                RecordTypeIdentifier.RECORD_TYPE_LEAN_BODY_MASS, new LeanBodyMassRecordHelper());
        recordIDToHelperMap.put(RecordTypeIdentifier.RECORD_TYPE_HEIGHT, new HeightRecordHelper());

        recordIDToHelperMap.put(
                RecordTypeIdentifier.RECORD_TYPE_OVULATION_TEST, new OvulationTestRecordHelper());
        recordIDToHelperMap.put(
                RecordTypeIdentifier.RECORD_TYPE_CERVICAL_MUCUS, new CervicalMucusRecordHelper());
        recordIDToHelperMap.put(
                RecordTypeIdentifier.RECORD_TYPE_BODY_TEMPERATURE,
                new BodyTemperatureRecordHelper());
        recordIDToHelperMap.put(
                RecordTypeIdentifier.RECORD_TYPE_BONE_MASS, new BoneMassRecordHelper());
        recordIDToHelperMap.put(
                RecordTypeIdentifier.RECORD_TYPE_BLOOD_PRESSURE, new BloodPressureRecordHelper());
        recordIDToHelperMap.put(
                RecordTypeIdentifier.RECORD_TYPE_BODY_FAT, new BodyFatRecordHelper());
        recordIDToHelperMap.put(
                RecordTypeIdentifier.RECORD_TYPE_BLOOD_GLUCOSE, new BloodGlucoseRecordHelper());
        recordIDToHelperMap.put(
                RecordTypeIdentifier.RECORD_TYPE_BASAL_BODY_TEMPERATURE,
                new BasalBodyTemperatureRecordHelper());
        recordIDToHelperMap.put(RecordTypeIdentifier.RECORD_TYPE_VO2_MAX, new Vo2MaxRecordHelper());
        recordIDToHelperMap.put(
                RecordTypeIdentifier.RECORD_TYPE_SEXUAL_ACTIVITY, new SexualActivityRecordHelper());
        recordIDToHelperMap.put(
                RecordTypeIdentifier.RECORD_TYPE_RESPIRATORY_RATE,
                new RespiratoryRateRecordHelper());
        recordIDToHelperMap.put(
                RecordTypeIdentifier.RECORD_TYPE_RESTING_HEART_RATE,
                new RestingHeartRateRecordHelper());
        recordIDToHelperMap.put(
                RecordTypeIdentifier.RECORD_TYPE_OXYGEN_SATURATION,
                new OxygenSaturationRecordHelper());
        recordIDToHelperMap.put(RecordTypeIdentifier.RECORD_TYPE_WEIGHT, new WeightRecordHelper());
        recordIDToHelperMap.put(
                RecordTypeIdentifier.RECORD_TYPE_BODY_WATER_MASS, new BodyWaterMassRecordHelper());
        recordIDToHelperMap.put(
                RecordTypeIdentifier.RECORD_TYPE_HEART_RATE_VARIABILITY_RMSSD,
                new HeartRateVariabilityRmssdHelper());
        recordIDToHelperMap.put(
                RecordTypeIdentifier.RECORD_TYPE_INTERMENSTRUAL_BLEEDING,
                new IntermenstrualBleedingRecordHelper());
        recordIDToHelperMap.put(
                RecordTypeIdentifier.RECORD_TYPE_MENSTRUATION_PERIOD,
                new MenstruationPeriodRecordHelper());
        recordIDToHelperMap.put(
                RecordTypeIdentifier.RECORD_TYPE_EXERCISE_SESSION,
                new ExerciseSessionRecordHelper());
        recordIDToHelperMap.put(
                RecordTypeIdentifier.RECORD_TYPE_SLEEP_SESSION, new SleepSessionRecordHelper());
        recordIDToHelperMap.put(
                RecordTypeIdentifier.RECORD_TYPE_SKIN_TEMPERATURE,
                new SkinTemperatureRecordHelper());

        recordIDToHelperMap = Collections.unmodifiableMap(recordIDToHelperMap);
        return recordIDToHelperMap;
    }

    List<DeleteTableRequest> getDeleteTableRequests(int recordAutoDeletePeriod) {
        List<DeleteTableRequest> deleteTableRequests = new ArrayList<>();

        Map<Integer, RecordHelper<?>> recordIdToHelperMap = getRecordHelpers();
        recordIdToHelperMap
                .values()
                .forEach(
                        (recordHelper) -> {
                            DeleteTableRequest request =
                                    recordHelper.getDeleteRequestForAutoDelete(
                                            recordAutoDeletePeriod);
                            deleteTableRequests.add(request);
                        });

        return deleteTableRequests;
    }

    Set<String> getTableNamesForDeletingStaleRecordEntries() {
        Set<String> tableNames = new HashSet<>();

        for (DeleteTableRequest deleteTableRequest : getDeleteTableRequests(30)) {
            tableNames.add(deleteTableRequest.getTableName());
        }

        return tableNames;
    }

    Set<String> getTableNamesForDeletingStaleChangeLogEntries() {
        Set<String> tableNames = new HashSet<>();

        tableNames.add(
                ChangeLogsHelper.getInstance().getDeleteRequestForAutoDelete().getTableName());
        tableNames.add(
                ChangeLogsRequestHelper.getInstance()
                        .getDeleteRequestForAutoDelete()
                        .getTableName());

        return tableNames;
    }

    Set<String> getTableNamesForDeletingStaleAccessLogsEntries() {
        Set<String> tableNames = new HashSet<>();

        tableNames.add(
                AccessLogsHelper.getInstance().getDeleteRequestForAutoDelete().getTableName());

        return tableNames;
    }
}
