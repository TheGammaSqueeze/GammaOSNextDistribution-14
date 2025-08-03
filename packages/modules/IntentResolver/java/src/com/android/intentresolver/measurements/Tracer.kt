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

package com.android.intentresolver.measurements

import android.os.SystemClock
import android.os.Trace
import android.os.UserHandle
import android.util.SparseArray
import androidx.annotation.GuardedBy
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

private const val SECTION_LAUNCH_TO_SHORTCUT = "launch-to-shortcut"
private const val SECTION_APP_PREDICTOR_PREFIX = "app-predictor-"
private const val SECTION_APP_TARGET_PREFIX = "app-target-"

object Tracer {
    private val launchToFirstShortcut = AtomicLong(-1L)
    private val nextId = AtomicInteger(0)
    @GuardedBy("self") private val profileRecords = SparseArray<ProfileRecord>()

    fun markLaunched() {
        if (launchToFirstShortcut.compareAndSet(-1, elapsedTimeNow())) {
            Trace.beginAsyncSection(SECTION_LAUNCH_TO_SHORTCUT, 1)
        }
    }

    fun endLaunchToShortcutTrace(): Long {
        val time = elapsedTimeNow()
        val startTime = launchToFirstShortcut.get()
        return if (startTime >= 0 && launchToFirstShortcut.compareAndSet(startTime, -1L)) {
            Trace.endAsyncSection(SECTION_LAUNCH_TO_SHORTCUT, 1)
            time - startTime
        } else {
            -1L
        }
    }

    /**
     * Begin shortcuts request tracing. The logic is based on an assumption that each request for
     * shortcuts update is followed by at least one response. Note, that it is not always measure
     * the request duration correctly as in the case of a two overlapping requests when the second
     * requests starts and ends while the first is running, the end of the second request will be
     * attributed to the first. This is tolerable as this still represents the visible to the user
     * app's behavior and expected to be quite rare.
     */
    fun beginAppPredictorQueryTrace(userHandle: UserHandle) {
        val queue = getUserShortcutRequestQueue(userHandle, createIfMissing = true) ?: return
        val startTime = elapsedTimeNow()
        val id = nextId.getAndIncrement()
        val sectionName = userHandle.toAppPredictorSectionName()
        synchronized(queue) {
            Trace.beginAsyncSection(sectionName, id)
            queue.addFirst(longArrayOf(startTime, id.toLong()))
        }
    }

    /**
     * End shortcut request tracing, see [beginAppPredictorQueryTrace].
     *
     * @return request duration is milliseconds.
     */
    fun endAppPredictorQueryTrace(userHandle: UserHandle): Long {
        val queue = getUserShortcutRequestQueue(userHandle, createIfMissing = false) ?: return -1L
        val endTime = elapsedTimeNow()
        val sectionName = userHandle.toAppPredictorSectionName()
        return synchronized(queue) { queue.removeLastOrNull() }
            ?.let { record ->
                Trace.endAsyncSection(sectionName, record[1].toInt())
                endTime - record[0]
            }
            ?: -1L
    }

    /**
     * Trace app target loading section per profile. If there's already an active section, it will
     * be ended an a new section started.
     */
    fun beginAppTargetLoadingSection(userHandle: UserHandle) {
        val profile = getProfileRecord(userHandle, createIfMissing = true) ?: return
        val sectionName = userHandle.toAppTargetSectionName()
        val time = elapsedTimeNow()
        synchronized(profile) {
            if (profile.appTargetLoading >= 0) {
                Trace.endAsyncSection(sectionName, 0)
            }
            profile.appTargetLoading = time
            Trace.beginAsyncSection(sectionName, 0)
        }
    }

    fun endAppTargetLoadingSection(userHandle: UserHandle): Long {
        val profile = getProfileRecord(userHandle, createIfMissing = false) ?: return -1L
        val time = elapsedTimeNow()
        val sectionName = userHandle.toAppTargetSectionName()
        return synchronized(profile) {
            if (profile.appTargetLoading >= 0) {
                Trace.endAsyncSection(sectionName, 0)
                (time - profile.appTargetLoading).also { profile.appTargetLoading = -1L }
            } else {
                -1L
            }
        }
    }

    private fun getUserShortcutRequestQueue(
        userHandle: UserHandle,
        createIfMissing: Boolean
    ): ArrayDeque<LongArray>? = getProfileRecord(userHandle, createIfMissing)?.appPredictorRequests

    private fun getProfileRecord(userHandle: UserHandle, createIfMissing: Boolean): ProfileRecord? =
        synchronized(profileRecords) {
            val idx = profileRecords.indexOfKey(userHandle.identifier)
            when {
                idx >= 0 -> profileRecords.valueAt(idx)
                createIfMissing ->
                    ProfileRecord().also { profileRecords.put(userHandle.identifier, it) }
                else -> null
            }
        }

    private fun elapsedTimeNow() = SystemClock.elapsedRealtime()
}

private class ProfileRecord {
    val appPredictorRequests = ArrayDeque<LongArray>()
    @GuardedBy("this") var appTargetLoading = -1L
}

private fun UserHandle.toAppPredictorSectionName() = SECTION_APP_PREDICTOR_PREFIX + identifier

private fun UserHandle.toAppTargetSectionName() = SECTION_APP_TARGET_PREFIX + identifier

inline fun <R> runTracing(name: String, block: () -> R): R {
    Trace.beginSection(name)
    try {
        return block()
    } finally {
        Trace.endSection()
    }
}
