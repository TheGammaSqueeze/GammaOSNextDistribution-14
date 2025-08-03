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

package android.tools.traces

/**
 * The utility class to wait a condition with customized options. The default retry policy is 5
 * times with interval 1 second.
 *
 * @param <T> The type of the object to validate.
 *
 * <p>Sample:</p> <pre> // Simple case. if (Condition.waitFor("true value", () -> true)) {
 *
 * ```
 *     println("Success");
 * ```
 *
 * } // Wait for customized result with customized validation. String result =
 * WaitForCondition.Builder(supplier = () -> "Result string")
 *
 * ```
 *         .withCondition(str -> str.equals("Expected string"))
 *         .withRetryIntervalMs(500)
 *         .withRetryLimit(3)
 *         .onFailure(str -> println("Failed on " + str)))
 *         .build()
 *         .waitFor()
 * ```
 *
 * </pre>
 *
 * @param condition If it returns true, that means the condition is satisfied.
 */
class WaitCondition<T>
private constructor(
    private val supplier: () -> T,
    private val condition: Condition<T>,
    private val retryLimit: Int,
    private val onLog: ((String, Boolean) -> Unit)?,
    private val onFailure: ((T) -> Any)?,
    private val onRetry: ((T) -> Any)?,
    private val onSuccess: ((T) -> Any)?,
    private val onStart: ((String) -> Any)?,
    private val onEnd: (() -> Any)?
) {
    /** @return `false` if the condition does not satisfy within the time limit. */
    fun waitFor(): Boolean {
        onStart?.invoke("waitFor")
        try {
            return doWaitFor()
        } finally {
            onEnd?.invoke()
        }
    }

    private fun doWaitFor(): Boolean {
        onLog?.invoke("***Waiting for $condition", false)
        var currState: T? = null
        var success = false
        for (i in 0..retryLimit) {
            val result = doWaitForRetry(i)
            success = result.first
            currState = result.second
            if (success) {
                break
            } else if (i < retryLimit) {
                onRetry?.invoke(currState)
            }
        }

        return if (success) {
            true
        } else {
            doNotifyFailure(currState)
            false
        }
    }

    private fun doWaitForRetry(retryNr: Int): Pair<Boolean, T> {
        onStart?.invoke("doWaitForRetry")
        try {
            val currState = supplier.invoke()
            return if (condition.isSatisfied(currState)) {
                onLog?.invoke("***Waiting for $condition ... Success!", false)
                onSuccess?.invoke(currState)
                Pair(true, currState)
            } else {
                val detailedMessage = condition.getMessage(currState)
                onLog?.invoke("***Waiting for $detailedMessage... retry=${retryNr + 1}", true)
                Pair(false, currState)
            }
        } finally {
            onEnd?.invoke()
        }
    }

    private fun doNotifyFailure(currState: T?) {
        val detailedMessage =
            if (currState != null) {
                condition.getMessage(currState)
            } else {
                condition.toString()
            }
        onLog?.invoke("***Waiting for $detailedMessage ... Failed!", true)
        if (onFailure != null) {
            require(currState != null) { "Missing last result for failure notification" }
            onFailure.invoke(currState)
        }
    }

    class Builder<T>(private val supplier: () -> T, private var retryLimit: Int) {
        private val conditions = mutableListOf<Condition<T>>()
        private var onStart: ((String) -> Any)? = null
        private var onEnd: (() -> Any)? = null
        private var onFailure: ((T) -> Any)? = null
        private var onRetry: ((T) -> Any)? = null
        private var onSuccess: ((T) -> Any)? = null
        private var onLog: ((String, Boolean) -> Unit)? = null

        fun withCondition(condition: Condition<T>) = apply { conditions.add(condition) }

        fun withCondition(message: String, condition: (T) -> Boolean) = apply {
            withCondition(Condition(message, condition))
        }

        private fun spreadConditionList(): List<Condition<T>> =
            conditions.flatMap {
                if (it is ConditionList<T>) {
                    it.conditions
                } else {
                    listOf(it)
                }
            }

        /**
         * Executes the action when the condition does not satisfy within the time limit. The passed
         * object to the consumer will be the last result from the supplier.
         */
        fun onFailure(onFailure: (T) -> Any): Builder<T> = apply { this.onFailure = onFailure }

        fun onLog(onLog: (String, Boolean) -> Unit): Builder<T> = apply { this.onLog = onLog }

        fun onRetry(onRetry: ((T) -> Any)? = null): Builder<T> = apply { this.onRetry = onRetry }

        fun onStart(onStart: ((String) -> Any)? = null): Builder<T> = apply {
            this.onStart = onStart
        }

        fun onEnd(onEnd: (() -> Any)? = null): Builder<T> = apply { this.onEnd = onEnd }

        fun onSuccess(onRetry: ((T) -> Any)? = null): Builder<T> = apply {
            this.onSuccess = onRetry
        }

        fun build(): WaitCondition<T> =
            WaitCondition(
                supplier,
                ConditionList(spreadConditionList()),
                retryLimit,
                onLog,
                onFailure,
                onRetry,
                onSuccess,
                onStart,
                onEnd
            )
    }
}
