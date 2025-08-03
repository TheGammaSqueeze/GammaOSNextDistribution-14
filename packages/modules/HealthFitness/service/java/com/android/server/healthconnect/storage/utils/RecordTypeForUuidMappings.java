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

package com.android.server.healthconnect.storage.utils;

import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_ACTIVE_CALORIES_BURNED;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_BASAL_BODY_TEMPERATURE;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_BASAL_METABOLIC_RATE;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_BLOOD_GLUCOSE;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_BLOOD_PRESSURE;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_BODY_FAT;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_BODY_TEMPERATURE;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_BODY_WATER_MASS;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_BONE_MASS;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_CERVICAL_MUCUS;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_CYCLING_PEDALING_CADENCE;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_DISTANCE;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_ELEVATION_GAINED;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_EXERCISE_SESSION;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_FLOORS_CLIMBED;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_HEART_RATE;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_HEART_RATE_VARIABILITY_RMSSD;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_HEIGHT;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_HYDRATION;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_INTERMENSTRUAL_BLEEDING;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_LEAN_BODY_MASS;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_MENSTRUATION_FLOW;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_MENSTRUATION_PERIOD;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_NUTRITION;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_OVULATION_TEST;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_OXYGEN_SATURATION;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_POWER;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_RESPIRATORY_RATE;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_RESTING_HEART_RATE;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_SEXUAL_ACTIVITY;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_SKIN_TEMPERATURE;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_SLEEP_SESSION;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_SPEED;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_STEPS;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_STEPS_CADENCE;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_TOTAL_CALORIES_BURNED;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_UNKNOWN;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_VO2_MAX;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_WEIGHT;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_WHEELCHAIR_PUSHES;
import static android.health.connect.datatypes.RecordTypeIdentifier.RecordType;

import static java.util.Objects.requireNonNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Contains mappings from the internal record type to a special record type for UUIDs.
 *
 * @hide
 */
public final class RecordTypeForUuidMappings {

    private static final Map<Integer, Integer> sInternalTypeToSpecialTypeMap = new HashMap<>();

    static {
        sInternalTypeToSpecialTypeMap.put(RECORD_TYPE_UNKNOWN, 0);
        sInternalTypeToSpecialTypeMap.put(RECORD_TYPE_EXERCISE_SESSION, 4);
        sInternalTypeToSpecialTypeMap.put(RECORD_TYPE_DISTANCE, 6);
        sInternalTypeToSpecialTypeMap.put(RECORD_TYPE_ELEVATION_GAINED, 7);
        sInternalTypeToSpecialTypeMap.put(RECORD_TYPE_FLOORS_CLIMBED, 8);
        sInternalTypeToSpecialTypeMap.put(RECORD_TYPE_HYDRATION, 9);
        sInternalTypeToSpecialTypeMap.put(RECORD_TYPE_NUTRITION, 10);
        sInternalTypeToSpecialTypeMap.put(RECORD_TYPE_SLEEP_SESSION, 12);
        sInternalTypeToSpecialTypeMap.put(RECORD_TYPE_STEPS, 13);
        sInternalTypeToSpecialTypeMap.put(RECORD_TYPE_BASAL_METABOLIC_RATE, 16);
        sInternalTypeToSpecialTypeMap.put(RECORD_TYPE_BLOOD_GLUCOSE, 17);
        sInternalTypeToSpecialTypeMap.put(RECORD_TYPE_BLOOD_PRESSURE, 18);
        sInternalTypeToSpecialTypeMap.put(RECORD_TYPE_BODY_FAT, 19);
        sInternalTypeToSpecialTypeMap.put(RECORD_TYPE_BODY_TEMPERATURE, 20);
        sInternalTypeToSpecialTypeMap.put(RECORD_TYPE_BONE_MASS, 21);
        sInternalTypeToSpecialTypeMap.put(RECORD_TYPE_CERVICAL_MUCUS, 22);
        sInternalTypeToSpecialTypeMap.put(RECORD_TYPE_HEIGHT, 28);
        sInternalTypeToSpecialTypeMap.put(RECORD_TYPE_HEART_RATE_VARIABILITY_RMSSD, 31);
        sInternalTypeToSpecialTypeMap.put(RECORD_TYPE_LEAN_BODY_MASS, 39);
        sInternalTypeToSpecialTypeMap.put(RECORD_TYPE_MENSTRUATION_FLOW, 41);
        sInternalTypeToSpecialTypeMap.put(RECORD_TYPE_OVULATION_TEST, 42);
        sInternalTypeToSpecialTypeMap.put(RECORD_TYPE_OXYGEN_SATURATION, 43);
        sInternalTypeToSpecialTypeMap.put(RECORD_TYPE_RESPIRATORY_RATE, 46);
        sInternalTypeToSpecialTypeMap.put(RECORD_TYPE_RESTING_HEART_RATE, 47);
        sInternalTypeToSpecialTypeMap.put(RECORD_TYPE_SEXUAL_ACTIVITY, 48);
        sInternalTypeToSpecialTypeMap.put(RECORD_TYPE_VO2_MAX, 51);
        sInternalTypeToSpecialTypeMap.put(RECORD_TYPE_WEIGHT, 53);
        sInternalTypeToSpecialTypeMap.put(RECORD_TYPE_HEART_RATE, 56);
        sInternalTypeToSpecialTypeMap.put(RECORD_TYPE_CYCLING_PEDALING_CADENCE, 58);
        sInternalTypeToSpecialTypeMap.put(RECORD_TYPE_POWER, 60);
        sInternalTypeToSpecialTypeMap.put(RECORD_TYPE_SPEED, 61);
        sInternalTypeToSpecialTypeMap.put(RECORD_TYPE_STEPS_CADENCE, 62);
        sInternalTypeToSpecialTypeMap.put(RECORD_TYPE_WHEELCHAIR_PUSHES, 63);
        sInternalTypeToSpecialTypeMap.put(RECORD_TYPE_BODY_WATER_MASS, 64);
        sInternalTypeToSpecialTypeMap.put(RECORD_TYPE_BASAL_BODY_TEMPERATURE, 65);
        sInternalTypeToSpecialTypeMap.put(RECORD_TYPE_TOTAL_CALORIES_BURNED, 66);
        sInternalTypeToSpecialTypeMap.put(RECORD_TYPE_ACTIVE_CALORIES_BURNED, 67);
        sInternalTypeToSpecialTypeMap.put(RECORD_TYPE_MENSTRUATION_PERIOD, 69);
        sInternalTypeToSpecialTypeMap.put(RECORD_TYPE_INTERMENSTRUAL_BLEEDING, 70);
        sInternalTypeToSpecialTypeMap.put(RECORD_TYPE_SKIN_TEMPERATURE, 71);
    }

    private RecordTypeForUuidMappings() {}

    /** Maps the internal record type to a special record type for UUIDs. */
    public static int getRecordTypeIdForUuid(@RecordType int recordTypeId) {
        return requireNonNull(
                sInternalTypeToSpecialTypeMap.get(recordTypeId),
                () -> "No mapping for " + recordTypeId);
    }
}
