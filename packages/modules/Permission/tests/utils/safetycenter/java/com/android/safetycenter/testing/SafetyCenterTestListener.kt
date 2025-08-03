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

package com.android.safetycenter.testing

import android.os.Build.VERSION_CODES.TIRAMISU
import android.safetycenter.SafetyCenterData
import android.safetycenter.SafetyCenterEntry
import android.safetycenter.SafetyCenterErrorDetails
import android.safetycenter.SafetyCenterManager.OnSafetyCenterDataChangedListener
import android.safetycenter.SafetyCenterStaticEntry
import android.safetycenter.SafetyCenterStatus
import android.text.TextUtils
import androidx.annotation.RequiresApi
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import com.android.safetycenter.testing.Coroutines.TIMEOUT_LONG
import com.android.safetycenter.testing.Coroutines.runBlockingWithTimeout
import java.time.Duration
import kotlinx.coroutines.channels.Channel

/**
 * An [OnSafetyCenterDataChangedListener] that facilitates receiving updates from SafetyCenter in
 * tests.
 */
@RequiresApi(TIRAMISU)
class SafetyCenterTestListener : OnSafetyCenterDataChangedListener {
    private val dataChannel = Channel<SafetyCenterData>(Channel.UNLIMITED)
    private val errorChannel = Channel<SafetyCenterErrorDetails>(Channel.UNLIMITED)

    override fun onSafetyCenterDataChanged(data: SafetyCenterData) {
        runBlockingWithTimeout { dataChannel.send(data) }
    }

    override fun onError(errorDetails: SafetyCenterErrorDetails) {
        // This call to super is needed for code coverage purposes, see b/272351657 for more
        // details. The default impl of the interface is a no-op so the call to super is a no-op.
        super.onError(errorDetails)
        runBlockingWithTimeout { errorChannel.send(errorDetails) }
    }

    /**
     * Waits for a [SafetyCenterData] update from SafetyCenter within the given [timeout].
     *
     * Optionally, a predicate can be used to wait for the [SafetyCenterData] to be [matching].
     */
    fun receiveSafetyCenterData(
        timeout: Duration = TIMEOUT_LONG,
        matching: (SafetyCenterData) -> Boolean = { true }
    ): SafetyCenterData =
        runBlockingWithTimeout(timeout) {
            var safetyCenterData = dataChannel.receive()
            while (!matching(safetyCenterData)) {
                safetyCenterData = dataChannel.receive()
            }
            safetyCenterData
        }

    /**
     * Waits for a full Safety Center refresh to complete, where each change to the underlying
     * [SafetyCenterData] must happen within the given [timeout].
     *
     * @param withErrorEntry optionally check whether we should expect the [SafetyCenterData] to
     *   have or not have at least one an error entry after the refresh completes
     * @return the [SafetyCenterData] after the refresh completes
     */
    fun waitForSafetyCenterRefresh(
        timeout: Duration = TIMEOUT_LONG,
        withErrorEntry: Boolean? = null
    ): SafetyCenterData {
        receiveSafetyCenterData(timeout) {
            it.status.refreshStatus == SafetyCenterStatus.REFRESH_STATUS_DATA_FETCH_IN_PROGRESS ||
                it.status.refreshStatus == SafetyCenterStatus.REFRESH_STATUS_FULL_RESCAN_IN_PROGRESS
        }
        val afterRefresh =
            receiveSafetyCenterData(timeout) {
                it.status.refreshStatus == SafetyCenterStatus.REFRESH_STATUS_NONE
            }
        if (withErrorEntry == null) {
            return afterRefresh
        }
        val errorMessage =
            SafetyCenterTestData(getApplicationContext())
                .getRefreshErrorString(numberOfErrorEntries = 1)
        val containsErrorEntry = afterRefresh.containsAnyEntryWithSummary(errorMessage)
        if (withErrorEntry && !containsErrorEntry) {
            throw AssertionError(
                "No error entry with message: \"$errorMessage\" found in SafetyCenterData" +
                    " after refresh: $afterRefresh"
            )
        } else if (!withErrorEntry && containsErrorEntry) {
            throw AssertionError(
                "Found an error entry with message: \"$errorMessage\" in SafetyCenterData" +
                    " after refresh: $afterRefresh"
            )
        }
        return afterRefresh
    }

    /**
     * Waits for a [SafetyCenterErrorDetails] update from SafetyCenter within the given [timeout].
     */
    fun receiveSafetyCenterErrorDetails(timeout: Duration = TIMEOUT_LONG) =
        runBlockingWithTimeout(timeout) { errorChannel.receive() }

    /** Cancels any pending update on this [SafetyCenterTestListener]. */
    fun cancel() {
        dataChannel.cancel()
        errorChannel.cancel()
    }

    private companion object {
        fun SafetyCenterData.containsAnyEntryWithSummary(summary: CharSequence): Boolean =
            entries().any { TextUtils.equals(it.summary, summary) } ||
                staticEntries().any { TextUtils.equals(it.summary, summary) }

        fun SafetyCenterData.entries(): List<SafetyCenterEntry> =
            entriesOrGroups.flatMap {
                val entry = it.entry
                if (entry != null) {
                    listOf(entry)
                } else {
                    it.entryGroup!!.entries
                }
            }

        fun SafetyCenterData.staticEntries(): List<SafetyCenterStaticEntry> =
            staticEntryGroups.flatMap { it.staticEntries }
    }
}
