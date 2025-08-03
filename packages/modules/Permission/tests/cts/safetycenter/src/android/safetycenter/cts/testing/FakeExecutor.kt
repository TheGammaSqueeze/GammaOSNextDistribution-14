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

package android.safetycenter.cts.testing

import com.android.safetycenter.testing.Coroutines.TIMEOUT_LONG
import com.android.safetycenter.testing.Coroutines.runBlockingWithTimeout
import java.time.Duration
import java.util.concurrent.Executor
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED

/**
 * An [Executor] that can control when the supplied [Runnable] is actually executed.
 *
 * This is useful to test concurrency issues, as it allows sequencing the [Executor] in a
 * deterministic order.
 */
class FakeExecutor : Executor {
    private val tasks = Channel<Runnable>(UNLIMITED)

    override fun execute(task: Runnable) {
        runBlockingWithTimeout { tasks.send(task) }
    }

    /**
     * Returns the next submitted task to this executor.
     *
     * If a task was already submitted, this call returns it immediately.
     *
     * If no task was submitted, this call returns the next task submitted within the given
     * [timeout] and throws a [TimeoutCancellationException] exception otherwise.
     *
     * Note: the returned task is not run when returned. Use [Runnable.run] to actually run it.
     */
    fun getNextTask(timeout: Duration = TIMEOUT_LONG) =
        runBlockingWithTimeout(timeout) { tasks.receive() }
}
