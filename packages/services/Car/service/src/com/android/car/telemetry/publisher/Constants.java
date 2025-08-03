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

package com.android.car.telemetry.publisher;

/**
 * Container class for all constants that are related to transfer of data within
 * {@link com.android.car.telemetry.CarTelemetryService} and between {@link
 * com.android.car.telemetry.CarTelemetryService} and
 * {@link com.android.car.scriptexecutor.ScriptExecutor}
 * by using {@link android.os.PersistableBundle} objects.
 *
 * Each publisher is responsible for populating {@link android.os.PersistableBundle} instances with
 * data. This file keeps names of keys used in the bundles by various publishers all in one place.
 * This measure should allow clients of data to understand structure of {@link
 * android.os.PersistableBundle} objects without having to look for places where the objects are
 * populated.
 */
public final class Constants {
    // Session Annotations
    public static final String ANNOTATION_BUNDLE_KEY_SESSION_ID = "session.sessionId";
    public static final String ANNOTATION_BUNDLE_KEY_SESSION_STATE = "session.sessionState";
    public static final String ANNOTATION_BUNDLE_KEY_CREATED_AT_SINCE_BOOT_MILLIS =
            "session.createdAtSinceBootMillis";
    public static final String ANNOTATION_BUNDLE_KEY_CREATED_AT_MILLIS = "session.createdAtMillis";
    public static final String ANNOTATION_BUNDLE_KEY_BOOT_REASON = "session.bootReason";
    public static final String ANNOTATION_BUNDLE_KEY_BOOT_COUNT = "session.bootCount";

    // StatsPublisher
    public static final String STATS_BUNDLE_KEY_PREFIX = "stats.";
    public static final String STATS_BUNDLE_KEY_ELAPSED_TIMESTAMP = "stats.elapsed_timestamp_nanos";

    // CarTelemetrydPublisher
    public static final String CAR_TELEMETRYD_BUNDLE_KEY_ID = "ct.id";
    public static final String CAR_TELEMETRYD_BUNDLE_KEY_CONTENT = "ct.content";

    // MemoryPublisher
    public static final String MEMORY_BUNDLE_KEY_PREFIX = "mem.";
    public static final String MEMORY_BUNDLE_KEY_MEMINFO = "mem.meminfo";
    public static final String MEMORY_BUNDLE_KEY_TIMESTAMP = "mem.timestamp_millis";
    public static final String MEMORY_BUNDLE_KEY_TOTAL_SWAPPABLE_PSS = "mem.total_swappable_pss";
    public static final String MEMORY_BUNDLE_KEY_TOTAL_PRIVATE_DIRTY = "mem.total_private_dirty";
    public static final String MEMORY_BUNDLE_KEY_TOTAL_SHARED_DIRTY = "mem.total_shared_dirty";
    public static final String MEMORY_BUNDLE_KEY_TOTAL_PRIVATE_CLEAN = "mem.total_private_clean";
    public static final String MEMORY_BUNDLE_KEY_TOTAL_SHARED_CLEAN = "mem.total_shared_clean";

    // ConnectivityPublisher
    public static final String CONNECTIVITY_BUNDLE_KEY_START_MILLIS = "conn.startMillis";
    public static final String CONNECTIVITY_BUNDLE_KEY_END_MILLIS = "conn.endMillis";
    public static final String CONNECTIVITY_BUNDLE_KEY_SIZE = "conn.size";
    public static final String CONNECTIVITY_BUNDLE_KEY_UID = "conn.uid";
    public static final String CONNECTIVITY_BUNDLE_KEY_PACKAGES = "conn.packages";
    public static final String CONNECTIVITY_BUNDLE_KEY_TAG = "conn.tag";
    public static final String CONNECTIVITY_BUNDLE_KEY_RX_BYTES = "conn.rxBytes";
    public static final String CONNECTIVITY_BUNDLE_KEY_TX_BYTES = "conn.txBytes";

    // VehiclePropertyPublisher
    public static final String VEHICLE_PROPERTY_BUNDLE_KEY_TIMESTAMP = "vp.timestamp";
    public static final String VEHICLE_PROPERTY_BUNDLE_KEY_PROP_ID = "vp.propertyId";
    public static final String VEHICLE_PROPERTY_BUNDLE_KEY_AREA_ID = "vp.areaId";
    public static final String VEHICLE_PROPERTY_BUNDLE_KEY_STATUS = "vp.status";
    public static final String VEHICLE_PROPERTY_BUNDLE_KEY_STRING = "vp.stringVal";
    public static final String VEHICLE_PROPERTY_BUNDLE_KEY_BOOLEAN = "vp.boolVal";
    public static final String VEHICLE_PROPERTY_BUNDLE_KEY_INT = "vp.intVal";
    public static final String VEHICLE_PROPERTY_BUNDLE_KEY_INT_ARRAY = "vp.intArrayVal";
    public static final String VEHICLE_PROPERTY_BUNDLE_KEY_LONG = "vp.longVal";
    public static final String VEHICLE_PROPERTY_BUNDLE_KEY_LONG_ARRAY = "vp.longArrayVal";
    public static final String VEHICLE_PROPERTY_BUNDLE_KEY_FLOAT = "vp.floatVal";
    public static final String VEHICLE_PROPERTY_BUNDLE_KEY_FLOAT_ARRAY = "vp.floatArrayVal";
    public static final String VEHICLE_PROPERTY_BUNDLE_KEY_BYTE_ARRAY = "vp.byteArrayVal";

    private Constants() {
    }
}
