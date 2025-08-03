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
package com.android.safetycenter.testing

import android.util.Log
import androidx.annotation.GuardedBy
import com.android.compatibility.common.util.SystemUtil
import com.android.safetycenter.testing.Coroutines.TIMEOUT_LONG
import com.android.safetycenter.testing.Coroutines.runBlockingWithTimeout
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A class to help waiting on broadcasts to be processed by the system. */
object WaitForBroadcasts {

    private const val TAG: String = "WaitForBroadcasts"

    private val mutex = Mutex()
    @GuardedBy("mutex") private var currentJob: Job? = null

    /**
     * Waits for broadcasts for at most [TIMEOUT_LONG] and prints a warning if that operation timed
     * out.
     *
     * The [SystemUtil.waitForBroadcasts] operation will keep on running even after the timeout as
     * it is not interruptible. Further calls to [WaitForBroadcasts.waitForBroadcasts] will re-use
     * the currently running [SystemUtil.waitForBroadcasts] call if it hasn't completed.
     */
    fun waitForBroadcasts() {
        try {
            runBlockingWithTimeout {
                mutex
                    .withLock {
                        val newJob = currentJob.maybeStartNewWaitForBroadcasts()
                        currentJob = newJob
                        newJob
                    }
                    .join()
            }
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Waiting for broadcasts timed out, proceeding anyway", e)
        }
    }

    // We're using a GlobalScope here as there doesn't seem to be a straightforward way to timeout
    // and interrupt the waitForBroadcasts() call. Given it's uninterruptible, we'd rather just have
    // at most one globally-bound waitForBroadcasts() call running at any given time.
    @OptIn(DelicateCoroutinesApi::class)
    private fun Job?.maybeStartNewWaitForBroadcasts(): Job =
        if (this != null && isActive) {
            this
        } else {
            GlobalScope.launch(Dispatchers.IO) { SystemUtil.waitForBroadcasts() }
        }
}
