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

package android.companion.cts.common

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

interface InvocationTracker<T> {
    val invocations: List<T>

    /**
     * Await invocations of this callback by the given [actions].
     */
    fun assertInvokedByActions(
        timeout: Duration = 1.seconds,
        minOccurrences: Int = 1,
        actions: () -> Unit
    ) {
        require(minOccurrences > 0) {
            "Must expect at least one callback occurrence. (Given $minOccurrences)"
        }
        val expectedInvocationCount = invocations.size + minOccurrences
        actions()
        if (!waitFor(timeout, interval = 100.milliseconds) {
                invocations.size >= expectedInvocationCount
        }) {
            throw AssertionError(
                "Callback was invoked ${invocations.size} times after $timeout ms! " +
                        "Expected at least $minOccurrences times."
            )
        }
    }

    fun clearRecordedInvocations()

    fun recordInvocation(invocation: T)
}

internal class InvocationContainer<T> : InvocationTracker<T> {
    private val _invocations: MutableList<T> = mutableListOf()
    override val invocations: List<T>
        @Synchronized
        get() = _invocations

    @Synchronized
    override fun clearRecordedInvocations() = _invocations.clear()

    @Synchronized
    override fun recordInvocation(invocation: T) {
        _invocations.add(invocation)
    }
}
