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

package com.android.server.net

import android.net.DataUsageRequest
import android.net.netstats.IUsageCallback
import android.os.IBinder
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.fail

private const val DEFAULT_TIMEOUT_MS = 200L

// TODO: Move the class to static libs once all downstream have IUsageCallback definition.
class TestableUsageCallback(private val binder: IBinder) : IUsageCallback.Stub() {
    sealed class CallbackType(val request: DataUsageRequest) {
        class OnThresholdReached(request: DataUsageRequest) : CallbackType(request)
        class OnCallbackReleased(request: DataUsageRequest) : CallbackType(request)
    }

    // TODO: Change to use ArrayTrackRecord once moved into to the module.
    private val history = LinkedBlockingQueue<CallbackType>()

    override fun onThresholdReached(request: DataUsageRequest) {
        history.add(CallbackType.OnThresholdReached(request))
    }

    override fun onCallbackReleased(request: DataUsageRequest) {
        history.add(CallbackType.OnCallbackReleased(request))
    }

    fun expectOnThresholdReached(request: DataUsageRequest) {
        expectCallback<CallbackType.OnThresholdReached>(request, DEFAULT_TIMEOUT_MS)
    }

    fun expectOnCallbackReleased(request: DataUsageRequest) {
        expectCallback<CallbackType.OnCallbackReleased>(request, DEFAULT_TIMEOUT_MS)
    }

    @JvmOverloads
    fun assertNoCallback(timeout: Long = DEFAULT_TIMEOUT_MS) {
        val cb = history.poll(timeout, TimeUnit.MILLISECONDS)
        cb?.let { fail("Expected no callback but got $cb") }
    }

    // Expects a callback of the specified request on the specified network within the timeout.
    // If no callback arrives, or a different callback arrives, fail.
    private inline fun <reified T : CallbackType> expectCallback(
        expectedRequest: DataUsageRequest,
        timeoutMs: Long
    ) {
        history.poll(timeoutMs, TimeUnit.MILLISECONDS).let {
            if (it !is T || it.request != expectedRequest) {
                fail("Unexpected callback : $it," +
                        " expected ${T::class} with Request[$expectedRequest]")
            } else {
                it
            }
        }
    }

    override fun asBinder(): IBinder {
        return binder
    }
}