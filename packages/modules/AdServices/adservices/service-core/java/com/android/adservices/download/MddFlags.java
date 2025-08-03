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

package com.android.adservices.download;

import android.annotation.NonNull;
import android.provider.DeviceConfig;

import com.google.android.libraries.mobiledatadownload.Flags;

/**
 * Flags for MDD
 *
 * <p>We only enable overriding for some flags that we may want to change the values. Other flags
 * will get the default values.
 */
public class MddFlags implements Flags {

    /*
     * Keys for ALL MDD flags stored in DeviceConfig.
     */
    static final String KEY_MDD_MAINTENANCE_GCM_TASK_PERIOD_SECONDS =
            "mdd_maintenance_gcm_task_period_seconds";
    static final String KEY_MDD_CHARGING_GCM_TASK_PERIOD_SECONDS =
            "mdd_charging_gcm_task_period_seconds";
    static final String KEY_MDD_CELLULAR_CHARGING_GCM_TASK_PERIOD_SECONDS =
            "mdd_cellular_charging_gcm_task_period_seconds";
    static final String KEY_MDD_WIFI_CHARGING_GCM_TASK_PERIOD_SECONDS =
            "mdd_wifi_charging_gcm_task_period_seconds";
    static final String KEY_MDD_DEFAULT_SAMPLE_INTERVAL = "mdd_default_sample_interval";
    static final String KEY_MDD_DOWNLOAD_EVENTS_SAMPLE_INTERVAL =
            "mdd_download_events_sample_interval";
    static final String KEY_MDD_GROUP_STATS_LOGGING_SAMPLE_INTERVAL =
            "mdd_group_stats_logging_sample_interval";
    static final String KEY_MDD_API_LOGGING_SAMPLE_INTERVAL = "mdd_api_logging_sample_interval";
    static final String KEY_MDD_STORAGE_STATS_LOGGING_SAMPLE_INTERVAL =
            "mdd_storage_stats_logging_sample_interval";
    static final String KEY_MDD_NETWORK_STATS_LOGGING_SAMPLE_INTERVAL =
            "mdd_network_stats_logging_sample_interval";
    static final String KEY_MDD_MOBSTORE_FILE_SERVICE_STATS_SAMPLE_INTERVAL =
            "mdd_mobstore_file_service_stats_sample_interval";
    static final String KEY_MDD_ANDROID_SHARING_SAMPLE_INTERVAL =
            "mdd_android_sharing_sample_interval";

    private static final MddFlags sSingleton = new MddFlags();

    /** Returns the singleton instance of the MddFlags. */
    @NonNull
    public static MddFlags getInstance() {
        return sSingleton;
    }

    // PeriodTaskFlags
    @Override
    @SuppressWarnings("InlinedApi")
    public long maintenanceGcmTaskPeriod() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getLong(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_MDD_MAINTENANCE_GCM_TASK_PERIOD_SECONDS,
                /* defaultValue */ Flags.super.maintenanceGcmTaskPeriod());
    }

    @Override
    @SuppressWarnings("InlinedApi")
    public long chargingGcmTaskPeriod() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getLong(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_MDD_CHARGING_GCM_TASK_PERIOD_SECONDS,
                /* defaultValue */ Flags.super.chargingGcmTaskPeriod());
    }

    @Override
    @SuppressWarnings("InlinedApi")
    public long cellularChargingGcmTaskPeriod() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getLong(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_MDD_CELLULAR_CHARGING_GCM_TASK_PERIOD_SECONDS,
                /* defaultValue */ Flags.super.cellularChargingGcmTaskPeriod());
    }

    @Override
    @SuppressWarnings("InlinedApi")
    public long wifiChargingGcmTaskPeriod() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getLong(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_MDD_WIFI_CHARGING_GCM_TASK_PERIOD_SECONDS,
                /* defaultValue */ Flags.super.wifiChargingGcmTaskPeriod());
    }

    // MddSampleIntervals
    @Override
    @SuppressWarnings("InlinedApi")
    public int mddDefaultSampleInterval() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getInt(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_MDD_DEFAULT_SAMPLE_INTERVAL,
                /* defaultValue */ Flags.super.mddDefaultSampleInterval());
    }

    @Override
    @SuppressWarnings("InlinedApi")
    public int mddDownloadEventsSampleInterval() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getInt(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_MDD_DOWNLOAD_EVENTS_SAMPLE_INTERVAL,
                /* defaultValue */ Flags.super.mddDownloadEventsSampleInterval());
    }

    @Override
    @SuppressWarnings("InlinedApi")
    public int groupStatsLoggingSampleInterval() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getInt(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_MDD_GROUP_STATS_LOGGING_SAMPLE_INTERVAL,
                /* defaultValue */ Flags.super.groupStatsLoggingSampleInterval());
    }

    @Override
    @SuppressWarnings("InlinedApi")
    public int apiLoggingSampleInterval() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getInt(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_MDD_API_LOGGING_SAMPLE_INTERVAL,
                /* defaultValue */ Flags.super.apiLoggingSampleInterval());
    }

    @Override
    @SuppressWarnings("InlinedApi")
    public int storageStatsLoggingSampleInterval() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getInt(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_MDD_STORAGE_STATS_LOGGING_SAMPLE_INTERVAL,
                /* defaultValue */ Flags.super.storageStatsLoggingSampleInterval());
    }

    @Override
    @SuppressWarnings("InlinedApi")
    public int networkStatsLoggingSampleInterval() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getInt(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_MDD_NETWORK_STATS_LOGGING_SAMPLE_INTERVAL,
                /* defaultValue */ Flags.super.networkStatsLoggingSampleInterval());
    }

    @Override
    @SuppressWarnings("InlinedApi")
    public int mobstoreFileServiceStatsSampleInterval() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getInt(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_MDD_MOBSTORE_FILE_SERVICE_STATS_SAMPLE_INTERVAL,
                /* defaultValue */ Flags.super.mobstoreFileServiceStatsSampleInterval());
    }

    @Override
    @SuppressWarnings("InlinedApi")
    public int mddAndroidSharingSampleInterval() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getInt(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_MDD_ANDROID_SHARING_SAMPLE_INTERVAL,
                /* defaultValue */ Flags.super.mddAndroidSharingSampleInterval());
    }
}
