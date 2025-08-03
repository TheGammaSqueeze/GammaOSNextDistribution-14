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

package com.android.net.module.util

import android.os.HandlerThread
import com.android.testutils.DevSdkIgnoreRunner
import com.android.testutils.DevSdkIgnoreRunner.MonitorThreadLeak
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

const val THREAD_BLOCK_TIMEOUT_MS = 1000L
const val TEST_REPEAT_COUNT = 100

@MonitorThreadLeak
@RunWith(DevSdkIgnoreRunner::class)
class HandlerUtilsTest {
    val handlerThread = HandlerThread("HandlerUtilsTestHandlerThread").also {
        it.start()
    }
    val handler = handlerThread.threadHandler

    @Test
    fun testRunWithScissors() {
        // Repeat the test a fair amount of times to ensure that it does not pass by chance.
        repeat(TEST_REPEAT_COUNT) {
            var result = false
            HandlerUtils.runWithScissorsForDump(handler, {
                assertEquals(Thread.currentThread(), handlerThread)
                result = true
            }, THREAD_BLOCK_TIMEOUT_MS)
            // Assert that the result is modified on the handler thread, but can also be seen from
            // the current thread. The assertion should pass if the runWithScissors provides
            // the guarantee where the assignment happens-before the assertion.
            assertTrue(result)
        }
    }

    @After
    fun tearDown() {
        handlerThread.quitSafely()
        handlerThread.join()
    }
}
