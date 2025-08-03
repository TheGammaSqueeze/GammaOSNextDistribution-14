/*
 * Copyright (C) 2020 The Android Open Source Project
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

import android.net.netstats.provider.NetworkStatsProvider
import android.util.Log
import com.android.net.module.util.ArrayTrackRecord
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

private const val DEFAULT_TIMEOUT_MS = 200L
const val TOKEN_ANY = -1

open class TestableNetworkStatsProvider(
    val defaultTimeoutMs: Long = DEFAULT_TIMEOUT_MS
) : NetworkStatsProvider() {
    sealed class CallbackType {
        data class OnRequestStatsUpdate(val token: Int) : CallbackType()
        data class OnSetWarningAndLimit(
            val iface: String,
            val warningBytes: Long,
            val limitBytes: Long
        ) : CallbackType()
        data class OnSetLimit(val iface: String, val limitBytes: Long) : CallbackType() {
            // Add getter for backward compatibility since old tests do not recognize limitBytes.
            val quotaBytes: Long
                get() = limitBytes
        }
        data class OnSetAlert(val quotaBytes: Long) : CallbackType()
    }

    private val TAG = this::class.simpleName
    val history = ArrayTrackRecord<CallbackType>().newReadHead()
    // See ReadHead#mark
    val mark get() = history.mark

    override fun onRequestStatsUpdate(token: Int) {
        Log.d(TAG, "onRequestStatsUpdate $token")
        history.add(CallbackType.OnRequestStatsUpdate(token))
    }

    override fun onSetWarningAndLimit(iface: String, warningBytes: Long, limitBytes: Long) {
        Log.d(TAG, "onSetWarningAndLimit $iface $warningBytes $limitBytes")
        history.add(CallbackType.OnSetWarningAndLimit(iface, warningBytes, limitBytes))
    }

    override fun onSetLimit(iface: String, quotaBytes: Long) {
        Log.d(TAG, "onSetLimit $iface $quotaBytes")
        history.add(CallbackType.OnSetLimit(iface, quotaBytes))
    }

    override fun onSetAlert(quotaBytes: Long) {
        Log.d(TAG, "onSetAlert $quotaBytes")
        history.add(CallbackType.OnSetAlert(quotaBytes))
    }

    fun expectOnRequestStatsUpdate(token: Int, timeout: Long = defaultTimeoutMs): Int {
        val event = history.poll(timeout)
        assertTrue(event is CallbackType.OnRequestStatsUpdate)
        if (token != TOKEN_ANY) {
            assertEquals(token, event.token)
        }
        return event.token
    }

    fun expectOnSetLimit(iface: String, quotaBytes: Long, timeout: Long = defaultTimeoutMs) {
        assertEquals(CallbackType.OnSetLimit(iface, quotaBytes), history.poll(timeout))
    }

    fun expectOnSetAlert(quotaBytes: Long, timeout: Long = defaultTimeoutMs) {
        assertEquals(CallbackType.OnSetAlert(quotaBytes), history.poll(timeout))
    }

    fun pollForNextCallback(timeout: Long = defaultTimeoutMs) =
        history.poll(timeout) ?: fail("Did not receive callback after ${timeout}ms")

    inline fun <reified T : CallbackType> expectCallback(
        timeout: Long = defaultTimeoutMs,
        predicate: (T) -> Boolean = { true }
    ): T {
        return pollForNextCallback(timeout).also { assertTrue(it is T && predicate(it)) } as T
    }

    // Expects a callback of the specified type matching the predicate within the timeout.
    // Any callback that doesn't match the predicate will be skipped. Fails only if
    // no matching callback is received within the timeout.
    // TODO : factorize the code for this with the identical call in TestableNetworkCallback.
    // There should be a common superclass doing this generically.
    // TODO : have a better error message to have this fail. Right now the failure when no
    // matching callback arrives comes from the casting to a non-nullable T.
    // TODO : in fact, completely removing this method and have clients use
    // history.poll(timeout, index, predicate) directly might be simpler.
    inline fun <reified T : CallbackType> eventuallyExpect(
        timeoutMs: Long = defaultTimeoutMs,
        from: Int = mark,
        crossinline predicate: (T) -> Boolean = { true }
    ) = history.poll(timeoutMs, from) { it is T && predicate(it) } as T

    fun drainCallbacks() {
        history.mark = history.size
    }

    @JvmOverloads
    fun assertNoCallback(timeout: Long = defaultTimeoutMs) {
        val cb = history.poll(timeout)
        cb?.let { fail("Expected no callback but got $cb") }
    }
}
