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

package android.ext.services.common;

import android.annotation.TargetApi;
import android.provider.DeviceConfig;


/**
 * Adservices Flags Implementation that delegates to DeviceConfig.
 */
public class AdservicesPhFlags {
    /**
     * Gets the value of periodic interval to run the AdServices check deletion job from
     * device config flags
     *
     * @return value periodic interval in millis
     **/
    @TargetApi(33)
    public Long getAppsearchDeletePeriodicIntervalMillis() {
        return DeviceConfig.getLong(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* name= */ "ext_appsearch_delete_periodic_job_interval_ms",
                /* defaultValue= */ 7 * 24 * 60 * 60 * 1000L); // 1 week in millis
    }

    /**
     * Gets the value of periodic flex to run the adservices check deletion job from
     * device config flags
     *
     * @return value periodic flex in millis
     **/
    @TargetApi(33)
    public Long getAppsearchDeleteJobFlexMillis() {
        return DeviceConfig.getLong(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* name= */ "ext_appsearch_delete_scheduler_job_flex_ms",
                /* defaultValue= */ 15 * 60 * 1000L); // 15 Minutes in millis
    }

    /**
     * Check if AdServices flags are set to enable AdServices, this includes checking if the
     * global_kill_switch is disabled and the adservice_enabled is true.
     *
     * @return {@code true} AdService is enabled; else {@code false}.
     **/
    @TargetApi(33)
    public boolean isAdServicesEnabled() {
        return !DeviceConfig.getBoolean(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ "global_kill_switch",
                /* defaultValue */ true)
                && DeviceConfig.getBoolean(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ "adservice_enabled",
                /* defaultValue */ false);
    }

    /**
     * Check if Appsearch deletion job is enabled.
     *
     * @return {@code true} Appsearch deletion job is enabled; else {@code false}.
     **/
    @TargetApi(33)
    public boolean isAppsearchDeleteJobEnabled() {
        return DeviceConfig.getBoolean(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ "ext_enable_appsearch_delete_job",
                /* defaultValue */ true);
    }

    /**
     * Gets the value of min minutes after which to check AdServices status
     *
     * @return value of min minutes
     **/
    @TargetApi(33)
    public Long getMinMinutesFromOtaToCheckAdServicesStatus() {
        return DeviceConfig.getLong(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* name= */ "ext_min_minutes_from_ota_check_adservices_status",
                /* defaultValue= */ 7 * 24 * 60L); // 1 Week in minutes
    }

    /**
     * Gets the value of min minutes to delete Appsearch data from date of OTA
     *
     * @return value of min minutes
     **/
    @TargetApi(33)
    public Long getMinMinutesFromOtaToDeleteAppsearchData() {
        return DeviceConfig.getLong(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* name= */ "ext_min_minutes_from_ota_delete_appsearch_data",
                /* defaultValue= */ 365 * 24 * 60L); // 1 Year in minutes
    }

    /**
     * Gets the value of min minutes to delete Appsearch data from date of first time AdServices
     * was found to be enabled
     *
     * @return value of min minutes
     **/
    @TargetApi(33)
    public Long getMinMinutesToDeleteFromAdServicesEnabled() {
        return DeviceConfig.getLong(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* name= */ "ext_min_minutes_to_delete_from_adservices_enabled",
                /* defaultValue= */ 2 * 7 * 24 * 60L); // 2 Weeks in minutes
    }

    /**
     * max delete attempts on appsearch dbs before cancelling the job
     *
     * @return value of min delete attempts
     **/
    @TargetApi(33)
    public int getMaxAppsearchAdServicesDeleteAttempts() {
        return DeviceConfig.getInt(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* name= */ "ext_max_appsearch_adservices_delete_attempts",
                /* defaultValue= */ 10);
    }

    /**
     * returns if the job should do nothing in case the job needs to be updated
     * and job cannot be scheduled again, so keeping the periodic job running
     *
     * @return true if job should do nothing
     **/
    @TargetApi(33)
    public boolean shouldDoNothingAdServicesAppsearchDeleteJob() {
        return DeviceConfig.getBoolean(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* name= */ "ext_do_nothing_adservices_appsearch_delete_job",
                /* defaultValue= */ false);
    }

}
