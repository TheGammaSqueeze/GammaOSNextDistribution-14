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

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import java.time.Duration
import kotlinx.coroutines.DEBUG_PROPERTY_NAME
import kotlinx.coroutines.DEBUG_PROPERTY_VALUE_AUTO
import kotlinx.coroutines.DEBUG_PROPERTY_VALUE_ON
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

/** A class that facilitates interacting with coroutines. */
object Coroutines {

    /**
     * The timeout of a test case, typically varies depending on whether the test is running
     * locally, on pre-submit or post-submit.
     */
    val TEST_TIMEOUT: Duration
        get() =
            Duration.ofMillis(
                InstrumentationRegistry.getArguments().getString("timeout_msec", "60000").toLong()
            )

    /** A long timeout, to be used for actions that are expected to complete. */
    val TIMEOUT_LONG: Duration
        get() = TEST_TIMEOUT.dividedBy(2)

    /** A short timeout, to be used for actions that are expected not to complete. */
    val TIMEOUT_SHORT: Duration = Duration.ofSeconds(1)

    /** Shorthand for [runBlocking] combined with [withTimeout]. */
    fun <T> runBlockingWithTimeout(timeout: Duration = TIMEOUT_LONG, block: suspend () -> T): T =
        runBlocking {
            withTimeout(timeout.toMillis()) { block() }
        }

    /** Shorthand for [runBlocking] combined with [withTimeoutOrNull] */
    fun <T> runBlockingWithTimeoutOrNull(
        timeout: Duration = TIMEOUT_LONG,
        block: suspend () -> T
    ): T? = runBlocking { withTimeoutOrNull(timeout.toMillis()) { block() } }

    /** Check a condition using coroutines with a timeout. */
    fun waitForWithTimeout(
        timeout: Duration = TIMEOUT_LONG,
        checkPeriod: Duration = CHECK_PERIOD,
        condition: () -> Boolean
    ) {
        runBlockingWithTimeout(timeout) { waitFor(checkPeriod, condition) }
    }

    /** Retries a [fallibleAction] until no errors are thrown or a timeout occurs. */
    fun waitForSuccessWithTimeout(
        timeout: Duration = TIMEOUT_LONG,
        checkPeriod: Duration = CHECK_PERIOD,
        fallibleAction: () -> Unit
    ) {
        waitForWithTimeout(timeout, checkPeriod) {
            try {
                fallibleAction()
                true
            } catch (ex: Throwable) {
                Log.w(TAG, "Encountered failure, retrying until timeout: $ex")
                false
            }
        }
    }

    /**
     * Enables debug mode for coroutines, in particular this enables stack traces in case of
     * failures.
     */
    fun enableDebugging() {
        System.setProperty(DEBUG_PROPERTY_NAME, DEBUG_PROPERTY_VALUE_ON)
    }

    /** Resets the debug mode to its original state. */
    fun resetDebugging() {
        System.setProperty(DEBUG_PROPERTY_NAME, debugMode)
    }

    /** Check a condition using coroutines. */
    private suspend fun waitFor(checkPeriod: Duration = CHECK_PERIOD, condition: () -> Boolean) {
        while (!condition()) {
            delay(checkPeriod.toMillis())
        }
    }

    private const val TAG: String = "Coroutines"

    /** A medium period, to be used for conditions that are expected to change. */
    private val CHECK_PERIOD: Duration = Duration.ofMillis(250)

    private val debugMode: String? =
        System.getProperty(DEBUG_PROPERTY_NAME, DEBUG_PROPERTY_VALUE_AUTO)
}
