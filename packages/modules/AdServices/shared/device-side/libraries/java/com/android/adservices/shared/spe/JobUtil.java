/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.adservices.shared.spe;

import android.app.job.JobInfo;

import com.android.adservices.shared.proto.JobPolicy;
import com.android.modules.utils.build.SdkLevel;

/** A class for utility methods. */
public final class JobUtil {
    private JobUtil() {
        throw new AssertionError(
                "The class only contains static method and should be noninstantiability.");
    }

    /**
     * Prints frequently used, and supported by {@link JobPolicy}, constraints in {@link JobInfo}.
     *
     * @param jobInfo the given {@link JobInfo}.
     * @return a well printed {@link String} formatted {@link JobInfo}.
     */
    public static String jobInfoToString(JobInfo jobInfo) {
        StringBuilder builder = new StringBuilder();

        builder.append("JobInfo:{");
        builder.append("JobId=")
                .append(jobInfo.getId())
                .append(", Network=")
                .append(jobInfo.getNetworkType())
                .append(", RequiresCharging=")
                .append(jobInfo.isRequireCharging())
                .append(", RequiresBatteryNotLow=")
                .append(jobInfo.isRequireBatteryNotLow())
                .append(", RequiresDeviceIdle=")
                .append(jobInfo.isRequireDeviceIdle())
                .append(", RequiresStorageNotLow=")
                .append(jobInfo.isRequireStorageNotLow());

        appendTriggerContentUriInfo(jobInfo, builder);
        builder.append(", TriggerContentMaxDelayMs=")
                .append(jobInfo.getTriggerContentMaxDelay())
                .append(", TriggerContentUpdateDelayMs=")
                .append(jobInfo.getTriggerContentUpdateDelay());

        builder.append(", PeriodicIntervalMs=")
                .append(jobInfo.getIntervalMillis())
                .append(", FlexIntervalMs=")
                .append(jobInfo.getFlexMillis())
                .append(", MinimumLatencyMs=")
                .append(jobInfo.getMinLatencyMillis())
                .append(", OverrideDeadlineMs=")
                .append(jobInfo.getMaxExecutionDelayMillis())
                .append(", Extras=")
                .append(jobInfo.getExtras().toString())
                .append(", IsPersisted=")
                .append(jobInfo.isPersisted());

        // Expedited is only available in S+.
        if (SdkLevel.isAtLeastS()) {
            builder.append(", IsExpedited=").append(jobInfo.isExpedited());
        }

        // Priority is only available in T+.
        if (SdkLevel.isAtLeastT()) {
            builder.append(", Priority=").append(jobInfo.getPriority());
        }

        builder.append("}");

        return builder.toString();
    }

    private static void appendTriggerContentUriInfo(JobInfo jobInfo, StringBuilder builder) {
        JobInfo.TriggerContentUri[] uris = jobInfo.getTriggerContentUris();
        if (uris == null) {
            return;
        }

        builder.append(" , TriggerUri=[");
        for (JobInfo.TriggerContentUri uri : uris) {
            builder.append("(uriString=").append(uri.getUri().toString());
            builder.append(",uriFlag=").append(uri.getFlags()).append("),");
        }
        builder.append("]");
    }
}
