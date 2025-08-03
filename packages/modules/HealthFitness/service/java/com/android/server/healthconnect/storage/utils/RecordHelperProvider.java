/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.server.healthconnect.storage.utils;

import static java.util.Objects.requireNonNull;

import android.annotation.NonNull;
import android.health.connect.datatypes.RecordTypeIdentifier;
import android.util.ArrayMap;

import com.android.server.healthconnect.storage.datatypehelpers.ActiveCaloriesBurnedRecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.BasalBodyTemperatureRecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.BasalMetabolicRateRecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.BloodGlucoseRecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.BloodPressureRecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.BodyFatRecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.BodyTemperatureRecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.BodyWaterMassRecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.BoneMassRecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.CervicalMucusRecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.CyclingPedalingCadenceRecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.DistanceRecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.ElevationGainedRecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.ExerciseSessionRecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.FloorsClimbedRecordHelper;
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

import java.util.Collections;
import java.util.Map;

/**
 * Store for all the record helpers
 *
 * @hide
 */
public final class RecordHelperProvider {
    @SuppressWarnings("NullAway.Init") // TODO(b/317029272): fix this suppression
    private static volatile RecordHelperProvider sRecordHelperProvider;

    private final Map<Integer, RecordHelper<?>> mRecordIDToHelperMap;

    private RecordHelperProvider() {
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
                RecordTypeIdentifier.RECORD_TYPE_SKIN_TEMPERATURE,
                new SkinTemperatureRecordHelper());
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

        mRecordIDToHelperMap = Collections.unmodifiableMap(recordIDToHelperMap);
    }

    @NonNull
    public static synchronized RecordHelperProvider getInstance() {
        if (sRecordHelperProvider == null) {
            sRecordHelperProvider = new RecordHelperProvider();
        }

        return sRecordHelperProvider;
    }

    @NonNull
    public Map<Integer, RecordHelper<?>> getRecordHelpers() {
        return mRecordIDToHelperMap;
    }

    @NonNull
    public RecordHelper<?> getRecordHelper(int recordType) {
        return requireNonNull(mRecordIDToHelperMap.get(recordType));
    }
}
