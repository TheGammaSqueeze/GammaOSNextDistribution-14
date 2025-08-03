/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import androidx.preference.PreferenceManager
import com.android.permissioncontroller.DumpableLog
import com.android.permissioncontroller.permission.data.PermissionEvent
import com.android.permissioncontroller.permission.utils.SystemTimeSource
import com.android.permissioncontroller.permission.utils.TimeSource
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * [BroadcastReceiver] to update the persisted timestamps when the date changes. Receives broadcasts
 * for [Intent.ACTION_TIME_CHANGED] to know when the system time has changed. However, this action
 * does not include metadata that allows for us to know how much the time has changed. To keep track
 * of the delta we take a snapshot of the current time and time since boot after the
 * [Intent.ACTION_BOOT_COMPLETED] action.
 */
class PermissionStorageTimeChangeReceiver(
    private val storages: List<PermissionEventStorage<out PermissionEvent>> =
        PermissionEventStorageImpls.getInstance(),
    private val timeSource: TimeSource = SystemTimeSource()
) : BroadcastReceiver() {

    companion object {
        private const val LOG_TAG = "PermissionStorageTimeChangeReceiver"

        /**
         * Key for the last known system time from the system. First initialized after boot
         * complete.
         */
        @VisibleForTesting const val PREF_KEY_SYSTEM_TIME_SNAPSHOT = "system_time_snapshot"

        /**
         * Key for the last known elapsed time since boot. First initialized after boot complete.
         */
        @VisibleForTesting
        const val PREF_KEY_ELAPSED_REALTIME_SNAPSHOT = "elapsed_realtime_snapshot"

        @VisibleForTesting const val SNAPSHOT_UNINITIALIZED = -1L

        /** The millisecond threshold for a time delta to be considered a time change. */
        private const val TIME_CHANGE_THRESHOLD_MILLIS = 60 * 1000L
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (storages.isEmpty()) {
            return
        }
        when (val action = intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                persistTimeSnapshots(
                    context,
                    timeSource.currentTimeMillis(),
                    timeSource.elapsedRealtime()
                )
            }
            Intent.ACTION_TIME_CHANGED -> {
                checkForTimeChanged(context)
            }
            else -> {
                DumpableLog.e(LOG_TAG, "Unexpected action $action")
            }
        }
    }

    private fun checkForTimeChanged(context: Context) {
        val systemTimeSnapshot = getSystemTimeSnapshot(context)
        val realtimeSnapshot = getElapsedRealtimeSnapshot(context)
        if (
            realtimeSnapshot == SNAPSHOT_UNINITIALIZED ||
                systemTimeSnapshot == SNAPSHOT_UNINITIALIZED
        ) {
            DumpableLog.e(LOG_TAG, "Snapshots not initialized")
            return
        }
        val actualSystemTime = timeSource.currentTimeMillis()
        val actualRealtime = timeSource.elapsedRealtime()
        val expectedSystemTime = (actualRealtime - realtimeSnapshot) + systemTimeSnapshot
        val diffSystemTime = actualSystemTime - expectedSystemTime
        if (abs(diffSystemTime) > TIME_CHANGE_THRESHOLD_MILLIS) {
            DumpableLog.d(LOG_TAG, "Time changed by ${diffSystemTime / 1000} seconds")
            onTimeChanged(diffSystemTime)
            persistTimeSnapshots(context, actualSystemTime, actualRealtime)
        }
    }

    @VisibleForTesting
    fun onTimeChanged(diffSystemTime: Long) {
        GlobalScope.launch(Dispatchers.IO) {
            for (storage in storages) {
                storage.updateEventsBySystemTimeDelta(diffSystemTime)
            }
        }
    }

    private fun persistTimeSnapshots(
        context: Context,
        systemTimeSnapshot: Long,
        realtimeSnapshot: Long
    ) {
        DumpableLog.d(LOG_TAG, "systemTimeSnapshot set to $systemTimeSnapshot")
        DumpableLog.d(LOG_TAG, "realtimeSnapshot set to $realtimeSnapshot")
        context.sharedPreferences.edit().apply {
            putLong(PREF_KEY_SYSTEM_TIME_SNAPSHOT, systemTimeSnapshot)
            putLong(PREF_KEY_ELAPSED_REALTIME_SNAPSHOT, realtimeSnapshot)
            apply()
        }
    }

    private fun getSystemTimeSnapshot(context: Context): Long {
        return context.sharedPreferences.getLong(
            PREF_KEY_SYSTEM_TIME_SNAPSHOT,
            SNAPSHOT_UNINITIALIZED
        )
    }

    private fun getElapsedRealtimeSnapshot(context: Context): Long {
        return context.sharedPreferences.getLong(
            PREF_KEY_ELAPSED_REALTIME_SNAPSHOT,
            SNAPSHOT_UNINITIALIZED
        )
    }

    val Context.sharedPreferences: SharedPreferences
        get() {
            return PreferenceManager.getDefaultSharedPreferences(this)
        }
}
