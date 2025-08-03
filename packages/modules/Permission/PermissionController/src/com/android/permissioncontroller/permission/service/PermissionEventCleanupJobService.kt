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

package com.android.permissioncontroller.permission.service

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.provider.DeviceConfig
import com.android.permissioncontroller.Constants
import com.android.permissioncontroller.DumpableLog
import com.android.permissioncontroller.permission.utils.Utils
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** A job to clean up old permission events. */
class PermissionEventCleanupJobService : JobService() {

    companion object {
        const val LOG_TAG = "PermissionEventCleanupJobService"
        val DEFAULT_CLEAR_OLD_EVENTS_CHECK_FREQUENCY = TimeUnit.DAYS.toMillis(1)

        fun scheduleOldDataCleanupIfNecessary(context: Context, jobScheduler: JobScheduler) {
            if (isNewJobScheduleRequired(jobScheduler)) {
                val jobInfo =
                    JobInfo.Builder(
                            Constants.OLD_PERMISSION_EVENT_CLEANUP_JOB_ID,
                            ComponentName(context, PermissionEventCleanupJobService::class.java)
                        )
                        .setPeriodic(getClearOldEventsCheckFrequencyMs())
                        // persist this job across boots
                        .setPersisted(true)
                        .build()
                val status = jobScheduler.schedule(jobInfo)
                if (status != JobScheduler.RESULT_SUCCESS) {
                    DumpableLog.e(LOG_TAG, "Could not schedule job: $status")
                }
            }
        }

        /**
         * Returns whether a new job needs to be scheduled. A persisted job is used to keep the
         * schedule across boots, but that job needs to be scheduled a first time and whenever the
         * check frequency changes.
         */
        private fun isNewJobScheduleRequired(jobScheduler: JobScheduler): Boolean {
            var scheduleNewJob = false
            val existingJob: JobInfo? =
                jobScheduler.getPendingJob(Constants.OLD_PERMISSION_EVENT_CLEANUP_JOB_ID)
            when {
                existingJob == null -> {
                    DumpableLog.i(LOG_TAG, "No existing job, scheduling a new one")
                    scheduleNewJob = true
                }
                existingJob.intervalMillis != getClearOldEventsCheckFrequencyMs() -> {
                    DumpableLog.i(LOG_TAG, "Interval frequency has changed, updating job")
                    scheduleNewJob = true
                }
                else -> {
                    DumpableLog.i(LOG_TAG, "Job already scheduled.")
                }
            }
            return scheduleNewJob
        }

        private fun getClearOldEventsCheckFrequencyMs() =
            DeviceConfig.getLong(
                DeviceConfig.NAMESPACE_PERMISSIONS,
                Utils.PROPERTY_PERMISSION_EVENTS_CHECK_OLD_FREQUENCY_MILLIS,
                DEFAULT_CLEAR_OLD_EVENTS_CHECK_FREQUENCY
            )
    }

    var job: Job? = null
    var jobStartTime: Long = -1L

    override fun onStartJob(params: JobParameters?): Boolean {
        DumpableLog.i(LOG_TAG, "onStartJob")
        val storages = PermissionEventStorageImpls.getInstance()
        if (storages.isEmpty()) {
            return false
        }
        jobStartTime = System.currentTimeMillis()
        job =
            GlobalScope.launch(Dispatchers.IO) {
                for (storage in storages) {
                    val success = storage.removeOldData()
                    if (!success) {
                        DumpableLog.e(LOG_TAG, "Failed to remove old data for $storage")
                    }
                }
                jobFinished(params, false)
            }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        DumpableLog.w(LOG_TAG, "onStopJob after ${System.currentTimeMillis() - jobStartTime}ms")
        job?.cancel()
        return true
    }
}
