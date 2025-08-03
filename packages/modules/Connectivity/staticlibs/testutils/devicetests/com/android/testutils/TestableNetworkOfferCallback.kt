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

package com.android.testutils

import android.net.NetworkCapabilities
import android.net.NetworkProvider
import android.net.NetworkRequest
import android.util.Log
import com.android.net.module.util.ArrayTrackRecord
import kotlin.test.fail

class TestableNetworkOfferCallback(val timeoutMs: Long, private val noCallbackTimeoutMs: Long)
            : NetworkProvider.NetworkOfferCallback {
    private val TAG = this::class.simpleName
    val history = ArrayTrackRecord<CallbackEntry>().newReadHead()

    sealed class CallbackEntry {
        data class OnNetworkNeeded(val request: NetworkRequest) : CallbackEntry()
        data class OnNetworkUnneeded(val request: NetworkRequest) : CallbackEntry()
    }

    /**
     * Called by the system when a network for this offer is needed to satisfy some
     * networking request.
     */
    override fun onNetworkNeeded(request: NetworkRequest) {
        Log.d(TAG, "onNetworkNeeded $request")
        history.add(CallbackEntry.OnNetworkNeeded(request))
    }

    /**
     * Called by the system when this offer is no longer valuable for this request.
     */
    override fun onNetworkUnneeded(request: NetworkRequest) {
        Log.d(TAG, "onNetworkUnneeded $request")
        history.add(CallbackEntry.OnNetworkUnneeded(request))
    }

    inline fun <reified T : CallbackEntry> expectCallbackThat(
        crossinline predicate: (T) -> Boolean
    ) {
        val event = history.poll(timeoutMs)
                ?: fail("Did not receive callback after ${timeoutMs}ms")
        if (event !is T || !predicate(event)) fail("Received unexpected callback $event")
    }

    fun expectOnNetworkNeeded(capabilities: NetworkCapabilities) =
            expectCallbackThat<CallbackEntry.OnNetworkNeeded> {
                it.request.canBeSatisfiedBy(capabilities)
            }

    fun expectOnNetworkUnneeded(capabilities: NetworkCapabilities) =
            expectCallbackThat<CallbackEntry.OnNetworkUnneeded> {
                it.request.canBeSatisfiedBy(capabilities)
            }

    fun assertNoCallback() {
        val cb = history.poll(noCallbackTimeoutMs)
        if (null != cb) fail("Expected no callback but got $cb")
    }
}