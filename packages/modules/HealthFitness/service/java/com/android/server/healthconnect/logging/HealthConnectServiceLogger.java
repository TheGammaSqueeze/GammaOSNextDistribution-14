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

package com.android.server.healthconnect.logging;

import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_CALLED;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_CALLED__API_METHOD__API_METHOD_UNKNOWN;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_CALLED__API_METHOD__DELETE_DATA;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_CALLED__API_METHOD__GET_CHANGES;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_CALLED__API_METHOD__GET_CHANGES_TOKEN;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_CALLED__API_METHOD__GET_GRANTED_PERMISSIONS;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_CALLED__API_METHOD__INSERT_DATA;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_CALLED__API_METHOD__READ_AGGREGATED_DATA;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_CALLED__API_METHOD__READ_DATA;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_CALLED__API_METHOD__REVOKE_ALL_PERMISSIONS;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_CALLED__API_METHOD__UPDATE_DATA;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_CALLED__API_STATUS__ERROR;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_CALLED__API_STATUS__STATUS_UNKNOWN;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_CALLED__API_STATUS__SUCCESS;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_CALLED__CALLER_FOREGROUND_STATE__BACKGROUND;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_CALLED__CALLER_FOREGROUND_STATE__FOREGROUND;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_CALLED__CALLER_FOREGROUND_STATE__UNSPECIFIED;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_CALLED__RATE_LIMIT__NOT_DEFINED;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_CALLED__RATE_LIMIT__NOT_USED;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_CALLED__RATE_LIMIT__RATE_LIMIT_BACKGROUND_15_MIN_ABOVE_3000;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_CALLED__RATE_LIMIT__RATE_LIMIT_BACKGROUND_15_MIN_BW_1000_TO_2000;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_CALLED__RATE_LIMIT__RATE_LIMIT_BACKGROUND_15_MIN_BW_2000_TO_3000;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_CALLED__RATE_LIMIT__RATE_LIMIT_BACKGROUND_15_MIN_BW_500_TO_1000;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_CALLED__RATE_LIMIT__RATE_LIMIT_BACKGROUND_15_MIN_UNDER_500;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_CALLED__RATE_LIMIT__RATE_LIMIT_BACKGROUND_24_HRS_ABOVE_5000;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_CALLED__RATE_LIMIT__RATE_LIMIT_BACKGROUND_24_HRS_BW_1000_TO_2000;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_CALLED__RATE_LIMIT__RATE_LIMIT_BACKGROUND_24_HRS_BW_2000_TO_3000;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_CALLED__RATE_LIMIT__RATE_LIMIT_BACKGROUND_24_HRS_BW_3000_TO_4000;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_CALLED__RATE_LIMIT__RATE_LIMIT_BACKGROUND_24_HRS_BW_4000_TO_5000;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_CALLED__RATE_LIMIT__RATE_LIMIT_BACKGROUND_24_HRS_UNDER_1000;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_CALLED__RATE_LIMIT__RATE_LIMIT_FOREGROUND_15_MIN_ABOVE_4000;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_CALLED__RATE_LIMIT__RATE_LIMIT_FOREGROUND_15_MIN_BW_1000_TO_2000;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_CALLED__RATE_LIMIT__RATE_LIMIT_FOREGROUND_15_MIN_BW_2000_TO_3000;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_CALLED__RATE_LIMIT__RATE_LIMIT_FOREGROUND_15_MIN_BW_3000_TO_4000;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_CALLED__RATE_LIMIT__RATE_LIMIT_FOREGROUND_15_MIN_UNDER_1000;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_CALLED__RATE_LIMIT__RATE_LIMIT_FOREGROUND_24_HRS_ABOVE_6000;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_CALLED__RATE_LIMIT__RATE_LIMIT_FOREGROUND_24_HRS_BW_2000_TO_3000;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_CALLED__RATE_LIMIT__RATE_LIMIT_FOREGROUND_24_HRS_BW_3000_TO_4000;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_CALLED__RATE_LIMIT__RATE_LIMIT_FOREGROUND_24_HRS_BW_4000_TO_5000;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_CALLED__RATE_LIMIT__RATE_LIMIT_FOREGROUND_24_HRS_BW_5000_TO_6000;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_CALLED__RATE_LIMIT__RATE_LIMIT_FOREGROUND_24_HRS_UNDER_2000;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_INVOKED;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_INVOKED__DATA_TYPE_ONE__ACTIVE_CALORIES_BURNED;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_INVOKED__DATA_TYPE_ONE__BASAL_BODY_TEMPERATURE;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_INVOKED__DATA_TYPE_ONE__BASAL_METABOLIC_RATE;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_INVOKED__DATA_TYPE_ONE__BLOOD_GLUCOSE;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_INVOKED__DATA_TYPE_ONE__BLOOD_PRESSURE;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_INVOKED__DATA_TYPE_ONE__BODY_FAT;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_INVOKED__DATA_TYPE_ONE__BODY_TEMPERATURE;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_INVOKED__DATA_TYPE_ONE__BONE_MASS;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_INVOKED__DATA_TYPE_ONE__CERVICAL_MUCUS;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_INVOKED__DATA_TYPE_ONE__CYCLING_PEDALING_CADENCE;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_INVOKED__DATA_TYPE_ONE__DATA_TYPE_NOT_ASSIGNED;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_INVOKED__DATA_TYPE_ONE__DATA_TYPE_UNKNOWN;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_INVOKED__DATA_TYPE_ONE__DISTANCE;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_INVOKED__DATA_TYPE_ONE__ELEVATION_GAINED;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_INVOKED__DATA_TYPE_ONE__EXERCISE_SESSION;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_INVOKED__DATA_TYPE_ONE__FLOORS_CLIMBED;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_INVOKED__DATA_TYPE_ONE__HEART_RATE;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_INVOKED__DATA_TYPE_ONE__HEIGHT;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_INVOKED__DATA_TYPE_ONE__HYDRATION;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_INVOKED__DATA_TYPE_ONE__LEAN_BODY_MASS;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_INVOKED__DATA_TYPE_ONE__MENSTRUATION_FLOW;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_INVOKED__DATA_TYPE_ONE__NUTRITION;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_INVOKED__DATA_TYPE_ONE__OVULATION_TEST;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_INVOKED__DATA_TYPE_ONE__OXYGEN_SATURATION;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_INVOKED__DATA_TYPE_ONE__POWER;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_INVOKED__DATA_TYPE_ONE__RESPIRATORY_RATE;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_INVOKED__DATA_TYPE_ONE__RESTING_HEART_RATE;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_INVOKED__DATA_TYPE_ONE__SEXUAL_ACTIVITY;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_INVOKED__DATA_TYPE_ONE__SKIN_TEMPERATURE;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_INVOKED__DATA_TYPE_ONE__SPEED;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_INVOKED__DATA_TYPE_ONE__STEPS;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_INVOKED__DATA_TYPE_ONE__STEPS_CADENCE;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_INVOKED__DATA_TYPE_ONE__TOTAL_CALORIES_BURNED;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_INVOKED__DATA_TYPE_ONE__VO2_MAX;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_INVOKED__DATA_TYPE_ONE__WEIGHT;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_API_INVOKED__DATA_TYPE_ONE__WHEELCHAIR_PUSHES;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_ACTIVE_CALORIES_BURNED;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_BASAL_BODY_TEMPERATURE;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_BASAL_METABOLIC_RATE;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_BLOOD_GLUCOSE;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_BLOOD_PRESSURE;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_BODY_FAT;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_BODY_TEMPERATURE;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_BONE_MASS;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_CERVICAL_MUCUS;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_CYCLING_PEDALING_CADENCE;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_DISTANCE;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_ELEVATION_GAINED;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_EXERCISE_SESSION;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_FLOORS_CLIMBED;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_HEART_RATE;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_HEIGHT;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_HYDRATION;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_LEAN_BODY_MASS;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_MENSTRUATION_FLOW;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_NUTRITION;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_OVULATION_TEST;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_OXYGEN_SATURATION;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_POWER;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_RESPIRATORY_RATE;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_RESTING_HEART_RATE;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_SEXUAL_ACTIVITY;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_SKIN_TEMPERATURE;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_SPEED;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_STEPS;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_STEPS_CADENCE;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_TOTAL_CALORIES_BURNED;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_UNKNOWN;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_VO2_MAX;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_WEIGHT;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_WHEELCHAIR_PUSHES;
import static android.health.connect.ratelimiter.RateLimiter.QuotaBucket.QUOTA_BUCKET_READS_PER_15M_BACKGROUND;
import static android.health.connect.ratelimiter.RateLimiter.QuotaBucket.QUOTA_BUCKET_READS_PER_15M_FOREGROUND;
import static android.health.connect.ratelimiter.RateLimiter.QuotaBucket.QUOTA_BUCKET_READS_PER_24H_BACKGROUND;
import static android.health.connect.ratelimiter.RateLimiter.QuotaBucket.QUOTA_BUCKET_READS_PER_24H_FOREGROUND;
import static android.health.connect.ratelimiter.RateLimiter.QuotaBucket.QUOTA_BUCKET_WRITES_PER_15M_BACKGROUND;
import static android.health.connect.ratelimiter.RateLimiter.QuotaBucket.QUOTA_BUCKET_WRITES_PER_15M_FOREGROUND;
import static android.health.connect.ratelimiter.RateLimiter.QuotaBucket.QUOTA_BUCKET_WRITES_PER_24H_BACKGROUND;
import static android.health.connect.ratelimiter.RateLimiter.QuotaBucket.QUOTA_BUCKET_WRITES_PER_24H_FOREGROUND;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.health.HealthFitnessStatsLog;
import android.health.connect.internal.datatypes.RecordInternal;
import android.health.connect.ratelimiter.RateLimiter;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

/**
 * Class to log metrics from HealthConnectService
 *
 * @hide
 */
public class HealthConnectServiceLogger {

    private final int mHealthDataServiceApiMethod;
    private final int mHealthDataServiceApiStatus;
    private final int mErrorCode;
    private final long mDuration;
    private final boolean mHoldsDataManagementPermission;
    private final int mRateLimit;
    private final int mNumberOfRecords;
    private final int[] mRecordTypes;
    private final String mPackageName;
    private final int mCallerForegroundState;
    private static final int MAX_NUMBER_OF_LOGGED_DATA_TYPES = 6;
    private static final int RECORD_TYPE_NOT_ASSIGNED_DEFAULT_VALUE = -1;

    /**
     * HealthConnectService ApiMethods supported by logging.
     *
     * @hide
     */
    public static final class ApiMethods {

        public static final int API_METHOD_UNKNOWN =
                HEALTH_CONNECT_API_CALLED__API_METHOD__API_METHOD_UNKNOWN;
        public static final int DELETE_DATA = HEALTH_CONNECT_API_CALLED__API_METHOD__DELETE_DATA;
        public static final int GET_CHANGES = HEALTH_CONNECT_API_CALLED__API_METHOD__GET_CHANGES;
        public static final int GET_CHANGES_TOKEN =
                HEALTH_CONNECT_API_CALLED__API_METHOD__GET_CHANGES_TOKEN;
        public static final int GET_GRANTED_PERMISSIONS =
                HEALTH_CONNECT_API_CALLED__API_METHOD__GET_GRANTED_PERMISSIONS;
        public static final int INSERT_DATA = HEALTH_CONNECT_API_CALLED__API_METHOD__INSERT_DATA;
        public static final int READ_AGGREGATED_DATA =
                HEALTH_CONNECT_API_CALLED__API_METHOD__READ_AGGREGATED_DATA;
        public static final int READ_DATA = HEALTH_CONNECT_API_CALLED__API_METHOD__READ_DATA;
        public static final int REVOKE_ALL_PERMISSIONS =
                HEALTH_CONNECT_API_CALLED__API_METHOD__REVOKE_ALL_PERMISSIONS;
        public static final int UPDATE_DATA = HEALTH_CONNECT_API_CALLED__API_METHOD__UPDATE_DATA;

        @IntDef({
            API_METHOD_UNKNOWN,
            DELETE_DATA,
            GET_CHANGES,
            GET_CHANGES_TOKEN,
            GET_GRANTED_PERMISSIONS,
            INSERT_DATA,
            READ_AGGREGATED_DATA,
            READ_DATA,
            REVOKE_ALL_PERMISSIONS,
            UPDATE_DATA,
        })
        @Retention(RetentionPolicy.SOURCE)
        public @interface ApiMethod {}
    }

    /**
     * Rate limiting ranges differentiated by Foreground/Background.
     *
     * @hide
     */
    public static final class RateLimitingRanges {

        public static final int NOT_USED = HEALTH_CONNECT_API_CALLED__RATE_LIMIT__NOT_USED;
        public static final int NOT_DEFINED = HEALTH_CONNECT_API_CALLED__RATE_LIMIT__NOT_DEFINED;
        public static final int FOREGROUND_15_MIN_UNDER_1000 =
                HEALTH_CONNECT_API_CALLED__RATE_LIMIT__RATE_LIMIT_FOREGROUND_15_MIN_UNDER_1000;
        public static final int FOREGROUND_15_MIN_BW_1000_TO_2000 =
                HEALTH_CONNECT_API_CALLED__RATE_LIMIT__RATE_LIMIT_FOREGROUND_15_MIN_BW_1000_TO_2000;
        public static final int FOREGROUND_15_MIN_BW_2000_TO_3000 =
                HEALTH_CONNECT_API_CALLED__RATE_LIMIT__RATE_LIMIT_FOREGROUND_15_MIN_BW_2000_TO_3000;
        public static final int FOREGROUND_15_MIN_BW_3000_TO_4000 =
                HEALTH_CONNECT_API_CALLED__RATE_LIMIT__RATE_LIMIT_FOREGROUND_15_MIN_BW_3000_TO_4000;
        public static final int FOREGROUND_15_MIN_ABOVE_4000 =
                HEALTH_CONNECT_API_CALLED__RATE_LIMIT__RATE_LIMIT_FOREGROUND_15_MIN_ABOVE_4000;
        public static final int BACKGROUND_15_MIN_UNDER_500 =
                HEALTH_CONNECT_API_CALLED__RATE_LIMIT__RATE_LIMIT_BACKGROUND_15_MIN_UNDER_500;
        public static final int BACKGROUND_15_MIN_BW_500_TO_1000 =
                HEALTH_CONNECT_API_CALLED__RATE_LIMIT__RATE_LIMIT_BACKGROUND_15_MIN_BW_500_TO_1000;
        public static final int BACKGROUND_15_MIN_BW_1000_TO_2000 =
                HEALTH_CONNECT_API_CALLED__RATE_LIMIT__RATE_LIMIT_BACKGROUND_15_MIN_BW_1000_TO_2000;
        public static final int BACKGROUND_15_MIN_BW_2000_TO_3000 =
                HEALTH_CONNECT_API_CALLED__RATE_LIMIT__RATE_LIMIT_BACKGROUND_15_MIN_BW_2000_TO_3000;
        public static final int BACKGROUND_15_MIN_ABOVE_3000 =
                HEALTH_CONNECT_API_CALLED__RATE_LIMIT__RATE_LIMIT_BACKGROUND_15_MIN_ABOVE_3000;
        public static final int FOREGROUND_24_HRS_UNDER_2000 =
                HEALTH_CONNECT_API_CALLED__RATE_LIMIT__RATE_LIMIT_FOREGROUND_24_HRS_UNDER_2000;
        public static final int FOREGROUND_24_HRS_BW_2000_TO_3000 =
                HEALTH_CONNECT_API_CALLED__RATE_LIMIT__RATE_LIMIT_FOREGROUND_24_HRS_BW_2000_TO_3000;
        public static final int FOREGROUND_24_HRS_BW_3000_TO_4000 =
                HEALTH_CONNECT_API_CALLED__RATE_LIMIT__RATE_LIMIT_FOREGROUND_24_HRS_BW_3000_TO_4000;
        public static final int FOREGROUND_24_HRS_BW_4000_TO_5000 =
                HEALTH_CONNECT_API_CALLED__RATE_LIMIT__RATE_LIMIT_FOREGROUND_24_HRS_BW_4000_TO_5000;
        public static final int FOREGROUND_24_HRS_BW_5000_TO_6000 =
                HEALTH_CONNECT_API_CALLED__RATE_LIMIT__RATE_LIMIT_FOREGROUND_24_HRS_BW_5000_TO_6000;
        public static final int FOREGROUND_24_HRS_ABOVE_6000 =
                HEALTH_CONNECT_API_CALLED__RATE_LIMIT__RATE_LIMIT_FOREGROUND_24_HRS_ABOVE_6000;
        public static final int BACKGROUND_24_HRS_UNDER_1000 =
                HEALTH_CONNECT_API_CALLED__RATE_LIMIT__RATE_LIMIT_BACKGROUND_24_HRS_UNDER_1000;
        public static final int BACKGROUND_24_HRS_BW_1000_TO_2000 =
                HEALTH_CONNECT_API_CALLED__RATE_LIMIT__RATE_LIMIT_BACKGROUND_24_HRS_BW_1000_TO_2000;
        public static final int BACKGROUND_24_HRS_BW_2000_TO_3000 =
                HEALTH_CONNECT_API_CALLED__RATE_LIMIT__RATE_LIMIT_BACKGROUND_24_HRS_BW_2000_TO_3000;
        public static final int BACKGROUND_24_HRS_BW_3000_TO_4000 =
                HEALTH_CONNECT_API_CALLED__RATE_LIMIT__RATE_LIMIT_BACKGROUND_24_HRS_BW_3000_TO_4000;
        public static final int BACKGROUND_24_HRS_BW_4000_TO_5000 =
                HEALTH_CONNECT_API_CALLED__RATE_LIMIT__RATE_LIMIT_BACKGROUND_24_HRS_BW_4000_TO_5000;
        public static final int BACKGROUND_24_HRS_ABOVE_5000 =
                HEALTH_CONNECT_API_CALLED__RATE_LIMIT__RATE_LIMIT_BACKGROUND_24_HRS_ABOVE_5000;

        private static final Map<Integer, Integer> sForeground15min =
                Map.of(
                        0,
                        FOREGROUND_15_MIN_UNDER_1000,
                        1,
                        FOREGROUND_15_MIN_BW_1000_TO_2000,
                        2,
                        BACKGROUND_15_MIN_BW_2000_TO_3000,
                        3,
                        FOREGROUND_15_MIN_BW_3000_TO_4000);

        private static final Map<Integer, Integer> sForeground24hour =
                Map.of(
                        0,
                        FOREGROUND_24_HRS_UNDER_2000,
                        1,
                        FOREGROUND_24_HRS_UNDER_2000,
                        2,
                        FOREGROUND_24_HRS_BW_2000_TO_3000,
                        3,
                        FOREGROUND_24_HRS_BW_3000_TO_4000,
                        4,
                        FOREGROUND_24_HRS_BW_4000_TO_5000,
                        5,
                        FOREGROUND_24_HRS_BW_5000_TO_6000);

        private static final Map<Integer, Integer> sBackground15Min =
                Map.of(
                        0,
                        BACKGROUND_15_MIN_BW_500_TO_1000,
                        1,
                        BACKGROUND_15_MIN_BW_1000_TO_2000,
                        2,
                        BACKGROUND_15_MIN_BW_2000_TO_3000);

        private static final Map<Integer, Integer> sBackground24Hour =
                Map.of(
                        0,
                        BACKGROUND_24_HRS_UNDER_1000,
                        1,
                        BACKGROUND_24_HRS_BW_1000_TO_2000,
                        2,
                        BACKGROUND_24_HRS_BW_2000_TO_3000,
                        3,
                        BACKGROUND_24_HRS_BW_3000_TO_4000,
                        4,
                        BACKGROUND_24_HRS_BW_4000_TO_5000);

        @IntDef({
            NOT_USED,
            FOREGROUND_15_MIN_UNDER_1000,
            FOREGROUND_15_MIN_BW_1000_TO_2000,
            FOREGROUND_15_MIN_BW_2000_TO_3000,
            FOREGROUND_15_MIN_BW_3000_TO_4000,
            FOREGROUND_15_MIN_ABOVE_4000,
            BACKGROUND_15_MIN_UNDER_500,
            BACKGROUND_15_MIN_BW_500_TO_1000,
            BACKGROUND_15_MIN_BW_1000_TO_2000,
            BACKGROUND_15_MIN_BW_2000_TO_3000,
            BACKGROUND_15_MIN_ABOVE_3000,
            FOREGROUND_24_HRS_UNDER_2000,
            FOREGROUND_24_HRS_BW_2000_TO_3000,
            FOREGROUND_24_HRS_BW_3000_TO_4000,
            FOREGROUND_24_HRS_BW_4000_TO_5000,
            FOREGROUND_24_HRS_BW_5000_TO_6000,
            FOREGROUND_24_HRS_ABOVE_6000,
            BACKGROUND_24_HRS_UNDER_1000,
            BACKGROUND_24_HRS_BW_1000_TO_2000,
            BACKGROUND_24_HRS_BW_2000_TO_3000,
            BACKGROUND_24_HRS_BW_3000_TO_4000,
            BACKGROUND_24_HRS_BW_4000_TO_5000,
            BACKGROUND_24_HRS_ABOVE_5000
        })
        @Retention(RetentionPolicy.SOURCE)
        public @interface RateLimit {}
    }

    /**
     * Builder for HealthConnectServiceLogger
     *
     * @hide
     */
    public static class Builder {

        private final long mStartTime;
        private final int mHealthDataServiceApiMethod;
        private int mHealthDataServiceApiStatus;
        private int mErrorCode;
        private long mDuration;
        private int mRateLimit;
        private int mNumberOfRecords;
        private final boolean mHoldsDataManagementPermission;
        private int[] mRecordTypes;
        private String mPackageName;
        private int mCallerForegroundState;

        public Builder(boolean holdsDataManagementPermission, @ApiMethods.ApiMethod int apiMethod) {
            mStartTime = System.currentTimeMillis();
            mHealthDataServiceApiMethod = apiMethod;
            mHealthDataServiceApiStatus = HEALTH_CONNECT_API_CALLED__API_STATUS__STATUS_UNKNOWN;
            mErrorCode = 0; // Means no error
            mHoldsDataManagementPermission = holdsDataManagementPermission;
            mRateLimit = RateLimitingRanges.NOT_USED;
            mNumberOfRecords = 0;
            mRecordTypes = new int[MAX_NUMBER_OF_LOGGED_DATA_TYPES];
            Arrays.fill(mRecordTypes, RECORD_TYPE_NOT_ASSIGNED_DEFAULT_VALUE);
            mPackageName = "UNKNOWN";
            mCallerForegroundState =
                    HEALTH_CONNECT_API_CALLED__CALLER_FOREGROUND_STATE__UNSPECIFIED;
        }

        /** Set the API was called successfully. */
        public Builder setHealthDataServiceApiStatusSuccess() {
            this.mHealthDataServiceApiStatus = HEALTH_CONNECT_API_CALLED__API_STATUS__SUCCESS;
            return this;
        }

        /**
         * Set the API threw error.
         *
         * @param errorCode Error code thrown by the API.
         */
        public Builder setHealthDataServiceApiStatusError(int errorCode) {
            this.mErrorCode = errorCode;
            this.mHealthDataServiceApiStatus = HEALTH_CONNECT_API_CALLED__API_STATUS__ERROR;
            return this;
        }

        /**
         * Set the rate limiting range if used.
         *
         * @param quotaBucket Quota bucket.
         * @param quotaLimit Bucket limit.
         */
        public Builder setRateLimit(
                @RateLimiter.QuotaBucket.Type int quotaBucket, float quotaLimit) {
            this.mRateLimit = calculateRateLimitEnum(quotaBucket, quotaLimit);
            return this;
        }

        /**
         * Set the number of records involved in the API call.
         *
         * @param numberOfRecords Number of records.
         */
        public Builder setNumberOfRecords(int numberOfRecords) {
            this.mNumberOfRecords = numberOfRecords;
            return this;
        }

        /**
         * Set the types of records.
         *
         * @param recordInternals List of records.
         */
        public Builder setDataTypesFromRecordInternals(
                @NonNull List<RecordInternal<?>> recordInternals) {
            Objects.requireNonNull(recordInternals);
            Map<Integer, Integer> recordTypeToNumberOfRecords = new HashMap<>();
            for (RecordInternal<?> recordInternal : recordInternals) {
                int recordType = getDataTypeEnumFromRecordType(recordInternal.getRecordType());
                int numberOfRecords = recordTypeToNumberOfRecords.getOrDefault(recordType, 0);
                numberOfRecords++;
                recordTypeToNumberOfRecords.put(recordType, numberOfRecords);
            }
            List<Entry<Integer, Integer>> recordTypeSortedByNumberOfRecords =
                    new ArrayList<>(recordTypeToNumberOfRecords.entrySet());
            recordTypeSortedByNumberOfRecords.sort(Entry.comparingByValue());
            for (int i = 0;
                    i
                            < Math.min(
                                    recordTypeSortedByNumberOfRecords.size(),
                                    MAX_NUMBER_OF_LOGGED_DATA_TYPES);
                    i++) {
                mRecordTypes[i] = recordTypeSortedByNumberOfRecords.get(i).getKey();
            }
            return this;
        }

        /**
         * Set the types of records.
         *
         * @param recordTypesList List of record types.
         */
        public Builder setDataTypesFromRecordTypes(@NonNull List<Integer> recordTypesList) {
            if (recordTypesList == null || recordTypesList.size() == 0) {
                return this;
            }
            HashSet<Integer> recordTypes = new HashSet<>();
            for (Integer recordType : recordTypesList) {
                recordTypes.add(getDataTypeEnumFromRecordType(recordType));
            }

            int index = 0;
            for (int recordType : recordTypes) {
                mRecordTypes[index++] = recordType;
                if (index == MAX_NUMBER_OF_LOGGED_DATA_TYPES) {
                    break;
                }
            }
            return this;
        }

        /**
         * Set the types of records.
         *
         * @param packageName Package name of the caller.
         */
        public Builder setPackageName(@NonNull String packageName) {
            if (packageName == null || packageName.isBlank()) {
                return this;
            }
            mPackageName = packageName;
            return this;
        }

        /**
         * Sets the caller's foreground state.
         *
         * @param isCallerInForeground whether the caller is in foreground or background.
         */
        @NonNull
        public Builder setCallerForegroundState(boolean isCallerInForeground) {
            mCallerForegroundState =
                    isCallerInForeground
                            ? HEALTH_CONNECT_API_CALLED__CALLER_FOREGROUND_STATE__FOREGROUND
                            : HEALTH_CONNECT_API_CALLED__CALLER_FOREGROUND_STATE__BACKGROUND;
            return this;
        }

        /** Returns an object of {@link HealthConnectServiceLogger}. */
        public HealthConnectServiceLogger build() {
            mDuration = System.currentTimeMillis() - mStartTime;
            return new HealthConnectServiceLogger(this);
        }

        private int calculateRateLimitEnum(
                @RateLimiter.QuotaBucket.Type int quotaBucket, float quotaLimit) {
            int quotient = (int) (quotaLimit / 1000);
            switch (quotaBucket) {
                case QUOTA_BUCKET_READS_PER_15M_FOREGROUND:
                case QUOTA_BUCKET_WRITES_PER_15M_FOREGROUND:
                    return RateLimitingRanges.sForeground15min.getOrDefault(
                            quotient, RateLimitingRanges.FOREGROUND_15_MIN_ABOVE_4000);
                case QUOTA_BUCKET_READS_PER_24H_FOREGROUND:
                case QUOTA_BUCKET_WRITES_PER_24H_FOREGROUND:
                    return RateLimitingRanges.sForeground24hour.getOrDefault(
                            quotient, RateLimitingRanges.FOREGROUND_24_HRS_ABOVE_6000);
                case QUOTA_BUCKET_READS_PER_15M_BACKGROUND:
                case QUOTA_BUCKET_WRITES_PER_15M_BACKGROUND:
                    if (quotaLimit < 500) {
                        return RateLimitingRanges.BACKGROUND_15_MIN_UNDER_500;
                    }
                    return RateLimitingRanges.sBackground15Min.getOrDefault(
                            quotient, RateLimitingRanges.BACKGROUND_15_MIN_ABOVE_3000);
                case QUOTA_BUCKET_READS_PER_24H_BACKGROUND:
                case QUOTA_BUCKET_WRITES_PER_24H_BACKGROUND:
                    return RateLimitingRanges.sBackground24Hour.getOrDefault(
                            quotient, RateLimitingRanges.BACKGROUND_24_HRS_ABOVE_5000);
            }
            return RateLimitingRanges.NOT_DEFINED;
        }

        private static int getDataTypeEnumFromRecordType(int recordType) {
            switch (recordType) {
                case RECORD_TYPE_STEPS:
                    return HEALTH_CONNECT_API_INVOKED__DATA_TYPE_ONE__STEPS;
                case RECORD_TYPE_HEART_RATE:
                    return HEALTH_CONNECT_API_INVOKED__DATA_TYPE_ONE__HEART_RATE;
                case RECORD_TYPE_BASAL_METABOLIC_RATE:
                    return HEALTH_CONNECT_API_INVOKED__DATA_TYPE_ONE__BASAL_METABOLIC_RATE;
                case RECORD_TYPE_CYCLING_PEDALING_CADENCE:
                    return HEALTH_CONNECT_API_INVOKED__DATA_TYPE_ONE__CYCLING_PEDALING_CADENCE;
                case RECORD_TYPE_POWER:
                    return HEALTH_CONNECT_API_INVOKED__DATA_TYPE_ONE__POWER;
                case RECORD_TYPE_SPEED:
                    return HEALTH_CONNECT_API_INVOKED__DATA_TYPE_ONE__SPEED;
                case RECORD_TYPE_STEPS_CADENCE:
                    return HEALTH_CONNECT_API_INVOKED__DATA_TYPE_ONE__STEPS_CADENCE;
                case RECORD_TYPE_DISTANCE:
                    return HEALTH_CONNECT_API_INVOKED__DATA_TYPE_ONE__DISTANCE;
                case RECORD_TYPE_WHEELCHAIR_PUSHES:
                    return HEALTH_CONNECT_API_INVOKED__DATA_TYPE_ONE__WHEELCHAIR_PUSHES;
                case RECORD_TYPE_TOTAL_CALORIES_BURNED:
                    return HEALTH_CONNECT_API_INVOKED__DATA_TYPE_ONE__TOTAL_CALORIES_BURNED;
                case RECORD_TYPE_FLOORS_CLIMBED:
                    return HEALTH_CONNECT_API_INVOKED__DATA_TYPE_ONE__FLOORS_CLIMBED;
                case RECORD_TYPE_ELEVATION_GAINED:
                    return HEALTH_CONNECT_API_INVOKED__DATA_TYPE_ONE__ELEVATION_GAINED;
                case RECORD_TYPE_ACTIVE_CALORIES_BURNED:
                    return HEALTH_CONNECT_API_INVOKED__DATA_TYPE_ONE__ACTIVE_CALORIES_BURNED;
                case RECORD_TYPE_HYDRATION:
                    return HEALTH_CONNECT_API_INVOKED__DATA_TYPE_ONE__HYDRATION;
                case RECORD_TYPE_NUTRITION:
                    return HEALTH_CONNECT_API_INVOKED__DATA_TYPE_ONE__NUTRITION;
                case RECORD_TYPE_RESPIRATORY_RATE:
                    return HEALTH_CONNECT_API_INVOKED__DATA_TYPE_ONE__RESPIRATORY_RATE;
                case RECORD_TYPE_BONE_MASS:
                    return HEALTH_CONNECT_API_INVOKED__DATA_TYPE_ONE__BONE_MASS;
                case RECORD_TYPE_RESTING_HEART_RATE:
                    return HEALTH_CONNECT_API_INVOKED__DATA_TYPE_ONE__RESTING_HEART_RATE;
                case RECORD_TYPE_BODY_FAT:
                    return HEALTH_CONNECT_API_INVOKED__DATA_TYPE_ONE__BODY_FAT;
                case RECORD_TYPE_VO2_MAX:
                    return HEALTH_CONNECT_API_INVOKED__DATA_TYPE_ONE__VO2_MAX;
                case RECORD_TYPE_CERVICAL_MUCUS:
                    return HEALTH_CONNECT_API_INVOKED__DATA_TYPE_ONE__CERVICAL_MUCUS;
                case RECORD_TYPE_BASAL_BODY_TEMPERATURE:
                    return HEALTH_CONNECT_API_INVOKED__DATA_TYPE_ONE__BASAL_BODY_TEMPERATURE;
                case RECORD_TYPE_MENSTRUATION_FLOW:
                    return HEALTH_CONNECT_API_INVOKED__DATA_TYPE_ONE__MENSTRUATION_FLOW;
                case RECORD_TYPE_OXYGEN_SATURATION:
                    return HEALTH_CONNECT_API_INVOKED__DATA_TYPE_ONE__OXYGEN_SATURATION;
                case RECORD_TYPE_BLOOD_PRESSURE:
                    return HEALTH_CONNECT_API_INVOKED__DATA_TYPE_ONE__BLOOD_PRESSURE;
                case RECORD_TYPE_HEIGHT:
                    return HEALTH_CONNECT_API_INVOKED__DATA_TYPE_ONE__HEIGHT;
                case RECORD_TYPE_BLOOD_GLUCOSE:
                    return HEALTH_CONNECT_API_INVOKED__DATA_TYPE_ONE__BLOOD_GLUCOSE;
                case RECORD_TYPE_WEIGHT:
                    return HEALTH_CONNECT_API_INVOKED__DATA_TYPE_ONE__WEIGHT;
                case RECORD_TYPE_LEAN_BODY_MASS:
                    return HEALTH_CONNECT_API_INVOKED__DATA_TYPE_ONE__LEAN_BODY_MASS;
                case RECORD_TYPE_SEXUAL_ACTIVITY:
                    return HEALTH_CONNECT_API_INVOKED__DATA_TYPE_ONE__SEXUAL_ACTIVITY;
                case RECORD_TYPE_BODY_TEMPERATURE:
                    return HEALTH_CONNECT_API_INVOKED__DATA_TYPE_ONE__BODY_TEMPERATURE;
                case RECORD_TYPE_OVULATION_TEST:
                    return HEALTH_CONNECT_API_INVOKED__DATA_TYPE_ONE__OVULATION_TEST;
                case RECORD_TYPE_EXERCISE_SESSION:
                    return HEALTH_CONNECT_API_INVOKED__DATA_TYPE_ONE__EXERCISE_SESSION;
                case RECORD_TYPE_SKIN_TEMPERATURE:
                    return HEALTH_CONNECT_API_INVOKED__DATA_TYPE_ONE__SKIN_TEMPERATURE;
                case RECORD_TYPE_UNKNOWN:
                default:
                    return HEALTH_CONNECT_API_INVOKED__DATA_TYPE_ONE__DATA_TYPE_UNKNOWN;
            }
        }
    }

    private HealthConnectServiceLogger(@NonNull HealthConnectServiceLogger.Builder builder) {
        Objects.requireNonNull(builder);

        mHealthDataServiceApiMethod = builder.mHealthDataServiceApiMethod;
        mHealthDataServiceApiStatus = builder.mHealthDataServiceApiStatus;
        mErrorCode = builder.mErrorCode;
        mDuration = builder.mDuration;
        mHoldsDataManagementPermission = builder.mHoldsDataManagementPermission;
        mRateLimit = builder.mRateLimit;
        mNumberOfRecords = builder.mNumberOfRecords;
        mRecordTypes = builder.mRecordTypes;
        mPackageName = builder.mPackageName;
        mCallerForegroundState = builder.mCallerForegroundState;
    }

    /** Log to statsd. */
    public void log() {

        // Do not log API calls made from the controller
        if (mHoldsDataManagementPermission) {
            return;
        }
        HealthFitnessStatsLog.write(
                HEALTH_CONNECT_API_CALLED,
                mHealthDataServiceApiMethod,
                mHealthDataServiceApiStatus,
                mErrorCode,
                mDuration,
                mNumberOfRecords,
                mRateLimit,
                mCallerForegroundState);

        // For private logging, max 6 data types per request are being logged
        // rest will be ignored
        HealthFitnessStatsLog.write(
                HEALTH_CONNECT_API_INVOKED,
                mHealthDataServiceApiMethod,
                mHealthDataServiceApiStatus,
                mErrorCode,
                mDuration,
                mPackageName,
                getRecordTypeEnumToLog(mRecordTypes, 0),
                getRecordTypeEnumToLog(mRecordTypes, 1),
                getRecordTypeEnumToLog(mRecordTypes, 2),
                getRecordTypeEnumToLog(mRecordTypes, 3),
                getRecordTypeEnumToLog(mRecordTypes, 4),
                getRecordTypeEnumToLog(mRecordTypes, 5));
    }

    private int getRecordTypeEnumToLog(int[] recordTypes, int index) {
        if (recordTypes[index] == RECORD_TYPE_NOT_ASSIGNED_DEFAULT_VALUE) {
            return HEALTH_CONNECT_API_INVOKED__DATA_TYPE_ONE__DATA_TYPE_NOT_ASSIGNED;
        }
        return recordTypes[index];
    }
}
