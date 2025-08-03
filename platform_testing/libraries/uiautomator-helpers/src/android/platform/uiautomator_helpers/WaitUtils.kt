/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package android.platform.uiautomator_helpers

import android.os.SystemClock.sleep
import android.os.SystemClock.uptimeMillis
import android.os.Trace
import android.platform.uiautomator_helpers.TracingUtils.trace
import android.platform.uiautomator_helpers.WaitUtils.LoggerImpl.Companion.withEventualLogging
import android.util.Log
import androidx.test.uiautomator.StaleObjectException
import java.io.Closeable
import java.time.Duration
import java.time.Instant.now

/**
 * Collection of utilities to ensure a certain conditions is met.
 *
 * Those are meant to make tests more understandable from perfetto traces, and less flaky.
 */
object WaitUtils {
    private val DEFAULT_DEADLINE = Duration.ofSeconds(10)
    private val POLLING_WAIT = Duration.ofMillis(100)
    private val DEFAULT_SETTLE_TIME = Duration.ofSeconds(3)
    private const val TAG = "WaitUtils"
    private const val VERBOSE = true

    /**
     * Ensures that [condition] succeeds within [timeout], or fails with [errorProvider] message.
     *
     * This also logs with atrace each iteration, and its entire execution. Those traces are then
     * visible in perfetto. Note that logs are output only after the end of the method, all
     * together.
     *
     * Example of usage:
     * ```
     * ensureThat("screen is on") { uiDevice.isScreenOn }
     * ```
     */
    @JvmStatic
    @JvmOverloads
    fun ensureThat(
        description: String? = null,
        timeout: Duration = DEFAULT_DEADLINE,
        errorProvider: (() -> String)? = null,
        ignoreFailure: Boolean = false,
        ignoreException: Boolean = false,
        condition: () -> Boolean,
    ) {
        val traceName =
            if (description != null) {
                "Ensuring $description"
            } else {
                "ensure"
            }
        val errorProvider =
            errorProvider
                ?: { "Error ensuring that \"$description\" within ${timeout.toMillis()}ms" }
        trace(traceName) {
            val startTime = uptimeMillis()
            val timeoutMs = timeout.toMillis()
            Log.d(TAG, "Starting $traceName")
            withEventualLogging(logTimeDelta = true) {
                log(traceName)
                var i = 1
                while (uptimeMillis() < startTime + timeoutMs) {
                    trace("iteration $i") {
                        try {
                            if (condition()) {
                                log("[#$i] Condition true")
                                return
                            }
                        } catch (t: Throwable) {
                            log("[#$i] Condition failing with exception")
                            if (!ignoreException) {
                                throw RuntimeException("[#$i] iteration failed.", t)
                            }
                        }

                        log("[#$i] Condition false, might retry.")
                        sleep(POLLING_WAIT.toMillis())
                        i++
                    }
                }
                log("[#$i] Condition has always been false. Failing.")
                if (ignoreFailure) {
                    Log.w(TAG, "Ignoring ensureThat failure: ${errorProvider()}")
                } else {
                    throw FailedEnsureException(errorProvider())
                }
            }
        }
    }

    /**
     * Same as [waitForNullableValueToSettle], but assumes that [supplier] return value is non-null.
     */
    @JvmStatic
    @JvmOverloads
    fun <T> waitForValueToSettle(
        description: String? = null,
        minimumSettleTime: Duration = DEFAULT_SETTLE_TIME,
        timeout: Duration = DEFAULT_DEADLINE,
        errorProvider: () -> String =
            defaultWaitForSettleError(minimumSettleTime, description, timeout),
        supplier: () -> T,
    ): T {
        return waitForNullableValueToSettle(
            description,
            minimumSettleTime,
            timeout,
            errorProvider,
            supplier
        )
            ?: error(errorProvider())
    }

    /**
     * Waits for [supplier] to return the same value for at least [minimumSettleTime].
     *
     * If the value changes, the timer gets restarted. Fails when reaching [timeoutMs]. The minimum
     * running time of this method is [minimumSettleTime], in case the value is stable since the
     * beginning.
     *
     * Fails if [supplier] throws an exception.
     *
     * Outputs atraces visible with perfetto.
     *
     * Example of usage:
     * ```
     * val screenOn = waitForValueToSettle("Screen on") { uiDevice.isScreenOn }
     * ```
     *
     * Note: Prefer using [waitForValueToSettle] when [supplier] doesn't return a null value.
     *
     * @return the settled value. Throws if it doesn't settle.
     */
    @JvmStatic
    @JvmOverloads
    fun <T> waitForNullableValueToSettle(
        description: String? = null,
        minimumSettleTime: Duration = DEFAULT_SETTLE_TIME,
        timeout: Duration = DEFAULT_DEADLINE,
        errorProvider: () -> String =
            defaultWaitForSettleError(minimumSettleTime, description, timeout),
        supplier: () -> T?,
    ): T? {
        val prefix =
            if (description != null) {
                "Waiting for \"$description\" to settle"
            } else {
                "waitForValueToSettle"
            }
        val traceName =
            prefix +
                " (settleTime=${minimumSettleTime.toMillis()}ms, deadline=${timeout.toMillis()}ms)"
        trace(traceName) {
            Log.d(TAG, "Starting $traceName")
            withEventualLogging(logTimeDelta = true) {
                log(traceName)

                val startTime = now()
                var settledSince = startTime
                var previousValue: T? = null
                var previousValueSet = false
                while (now().isBefore(startTime + timeout)) {
                    val newValue =
                        try {
                            supplier()
                        } catch (t: Throwable) {
                            if (previousValueSet) {
                                Trace.endSection()
                            }
                            log("Supplier has thrown an exception")
                            throw RuntimeException(t)
                        }
                    val currentTime = now()
                    if (previousValue != newValue || !previousValueSet) {
                        log("value changed to $newValue")
                        settledSince = currentTime
                        if (previousValueSet) {
                            Trace.endSection()
                        }
                        TracingUtils.beginSectionSafe("New value: $newValue")
                        previousValue = newValue
                        previousValueSet = true
                    } else if (now().isAfter(settledSince + minimumSettleTime)) {
                        log("Got settled value. Returning \"$previousValue\"")
                        Trace.endSection() // previousValue is guaranteed to be non-null.
                        return previousValue
                    }
                    sleep(POLLING_WAIT.toMillis())
                }
                if (previousValueSet) {
                    Trace.endSection()
                }
                error(errorProvider())
            }
        }
    }

    private fun defaultWaitForSettleError(
        minimumSettleTime: Duration,
        description: String?,
        timeout: Duration
    ): () -> String {
        return {
            "Error getting settled (${minimumSettleTime.toMillis()}) " +
                "value for \"$description\" within ${timeout.toMillis()}."
        }
    }

    /**
     * Waits for [supplier] to return a non-null value within [timeout].
     *
     * Returns null after the timeout finished.
     */
    fun <T> waitForNullable(
        description: String,
        timeout: Duration = DEFAULT_DEADLINE,
        checker: (T?) -> Boolean = { it != null },
        supplier: () -> T?,
    ): T? {
        var result: T? = null

        ensureThat("Waiting for \"$description\"", timeout, ignoreFailure = true) {
            result = supplier()
            checker(result)
        }
        return result
    }

    /** Wraps [waitForNullable] using the default checker, and allowing kotlin supplier syntax. */
    fun <T> waitForNullable(
        description: String,
        timeout: Duration = DEFAULT_DEADLINE,
        supplier: () -> T?,
    ): T? = waitForNullable(description, timeout, checker = { it != null }, supplier)

    /**
     * Waits for [supplier] to return a not null and not empty list within [timeout].
     *
     * Returns the not-empty list as soon as it's received, or an empty list once reached the
     * timeout.
     */
    fun <T> waitForPossibleEmpty(
        description: String,
        timeout: Duration = DEFAULT_DEADLINE,
        supplier: () -> List<T>?
    ): List<T> =
        waitForNullable(description, timeout, { !it.isNullOrEmpty() }, supplier) ?: emptyList()

    /**
     * Waits for [supplier] to return a non-null value within [timeout].
     *
     * Throws an exception with [errorProvider] provided message if [supplier] failed to produce a
     * non-null value within [timeout].
     */
    fun <T> waitFor(
        description: String,
        timeout: Duration = DEFAULT_DEADLINE,
        errorProvider: () -> String = {
            "Didn't get a non-null value for \"$description\" within ${timeout.toMillis()}ms"
        },
        supplier: () -> T?
    ): T = waitForNullable(description, timeout, supplier) ?: error(errorProvider())

    /**
     * Retry a block of code [times] times, if it throws a StaleObjectException.
     *
     * This can be used to reduce flakiness in cases where waitForObj throws although the object
     * does seem to be present.
     */
    fun <T> retryIfStale(description: String, times: Int, block: () -> T): T {
        return trace("retryIfStale: $description") outerTrace@{
            repeat(times) {
                trace("attempt #$it") {
                    try {
                        return@outerTrace block()
                    } catch (e: StaleObjectException) {
                        Log.w(TAG, "Caught a StaleObjectException ($e). Retrying.")
                    }
                }
            }
            // Run the block once without catching
            trace("final attempt") { block() }
        }
    }

    /** Generic logging interface. */
    private interface Logger {
        fun log(s: String)
    }

    /** Logs all messages when closed. */
    private class LoggerImpl private constructor(private val logTimeDelta: Boolean) :
        Closeable, Logger {
        private val logs = mutableListOf<String>()
        private val startTime = uptimeMillis()

        companion object {
            /** Executes [block] and prints all logs at the end. */
            inline fun <T> withEventualLogging(
                logTimeDelta: Boolean = false,
                block: Logger.() -> T
            ): T = LoggerImpl(logTimeDelta).use { it.block() }
        }

        override fun log(s: String) {
            logs += if (logTimeDelta) "+${uptimeMillis() - startTime}ms $s" else s
        }

        override fun close() {
            if (VERBOSE) {
                Log.d(TAG, logs.joinToString("\n"))
            }
        }
    }
}

/** Exception thrown when [WaitUtils.ensureThat] fails. */
class FailedEnsureException(message: String? = null) : IllegalStateException(message)
