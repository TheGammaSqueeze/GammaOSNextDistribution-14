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

package com.android.ondevicepersonalization.services;

import com.android.ondevicepersonalization.services.data.user.UserDataCollectionJobService;
import com.android.ondevicepersonalization.services.download.OnDevicePersonalizationDownloadProcessingJobService;
import com.android.ondevicepersonalization.services.maintenance.OnDevicePersonalizationMaintenanceJobService;

import java.util.Map;

/** Hard-coded configs for OnDevicePersonalization */
public class OnDevicePersonalizationConfig {
    private OnDevicePersonalizationConfig() {}

    /**
     * Job ID for Mdd Maintenance Task ({@link
     * com.android.ondevicepersonalization.services.download.mdd.MddJobService})
     */
    public static final int MDD_MAINTENANCE_PERIODIC_TASK_JOB_ID = 1000;
    public static final String MDD_MAINTENANCE_PERIODIC_TASK_JOB_NAME =
            "MDD_MAINTENANCE_PERIODIC_TASK";

    /**
     * Job ID for Mdd Charging Periodic Task ({@link
     * com.android.ondevicepersonalization.services.download.mdd.MddJobService})
     */
    public static final int MDD_CHARGING_PERIODIC_TASK_JOB_ID = 1001;
    public static final String MDD_CHARGING_PERIODIC_TASK_JOB_NAME =
            "MDD_CHARGING_PERIODIC_TASK_JOB";

    /**
     * Job ID for Mdd Cellular Charging Task ({@link
     * com.android.ondevicepersonalization.services.download.mdd.MddJobService})
     */
    public static final int MDD_CELLULAR_CHARGING_PERIODIC_TASK_JOB_ID = 1002;
    public static final String MDD_CELLULAR_CHARGING_PERIODIC_TASK_JOB_NAME =
            "MDD_CELLULAR_CHARGING_PERIODIC_TASK_JOB";

    /**
     * Job ID for Mdd Wifi Charging Task ({@link
     * com.android.ondevicepersonalization.services.download.mdd.MddJobService})
     */
    public static final int MDD_WIFI_CHARGING_PERIODIC_TASK_JOB_ID = 1003;
    public static final String MDD_WIFI_CHARGING_PERIODIC_TASK_JOB_NAME =
            "MDD_WIFI_CHARGING_PERIODIC_TASK_JOB";

    /**
     * Job ID for Download Processing Task ({@link
     * OnDevicePersonalizationDownloadProcessingJobService})
     */
    public static final int DOWNLOAD_PROCESSING_TASK_JOB_ID = 1004;
    public static final String DOWNLOAD_PROCESSING_TASK_JOB_NAME =
            "DOWNLOAD_PROCESSING_TASK_JOB";

    /** Job ID for Maintenance Task ({@link OnDevicePersonalizationMaintenanceJobService}) */
    public static final int MAINTENANCE_TASK_JOB_ID = 1005;
    public static final String MAINTENANCE_TASK_JOB_NAME =
            "MAINTENANCE_TASK_JOB";

    /** Job ID for User Data Collection Task ({@link UserDataCollectionJobService}) */
    public static final int USER_DATA_COLLECTION_ID = 1006;
    public static final String USER_DATA_COLLECTION_JOB_NAME =
            "USER_DATA_COLLECTION_JOB";

    public static final Map<Integer, String> JOB_ID_TO_NAME_MAP = Map.of(
            MDD_MAINTENANCE_PERIODIC_TASK_JOB_ID,
            MDD_MAINTENANCE_PERIODIC_TASK_JOB_NAME,
            MDD_CHARGING_PERIODIC_TASK_JOB_ID,
            MDD_CHARGING_PERIODIC_TASK_JOB_NAME,
            MDD_CELLULAR_CHARGING_PERIODIC_TASK_JOB_ID,
            MDD_CELLULAR_CHARGING_PERIODIC_TASK_JOB_NAME,
            MDD_WIFI_CHARGING_PERIODIC_TASK_JOB_ID,
            MDD_WIFI_CHARGING_PERIODIC_TASK_JOB_NAME,
            DOWNLOAD_PROCESSING_TASK_JOB_ID,
            DOWNLOAD_PROCESSING_TASK_JOB_NAME,
            MAINTENANCE_TASK_JOB_ID,
            MAINTENANCE_TASK_JOB_NAME,
            USER_DATA_COLLECTION_ID,
            USER_DATA_COLLECTION_JOB_NAME
    );
}
