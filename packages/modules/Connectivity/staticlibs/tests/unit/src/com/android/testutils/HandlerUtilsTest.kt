/*
 * Copyright (C) 2019 The Android Open Source Project
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

import android.os.Handler
import android.os.HandlerThread
import com.android.testutils.FunctionalUtils.ThrowingSupplier
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

private const val ATTEMPTS = 50 // Causes testWaitForIdle to take about 150ms on aosp_crosshatch-eng
private const val TIMEOUT_MS = 1000

@RunWith(JUnit4::class)
class HandlerUtilsTest {
    @Test
    fun testWaitForIdle() {
        val handlerThread = HandlerThread("testHandler").apply { start() }

        // Tests that waitForIdle can be called many times without ill impact if the service is
        // already idle.
        repeat(ATTEMPTS) {
            handlerThread.waitForIdle(TIMEOUT_MS)
        }

        // Tests that calling waitForIdle waits for messages to be processed. Use both an
        // inline runnable that's instantiated at each loop run and a runnable that's instantiated
        // once for all.
        val tempRunnable = object : Runnable {
            // Use StringBuilder preferentially to StringBuffer because StringBuilder is NOT
            // thread-safe. It's part of the point that both runnables run on the same thread
            // so if anything is wrong in that space it's better to opportunistically use a class
            // where things might go wrong, even if there is no guarantee of failure.
            var memory = StringBuilder()
            override fun run() {
                memory.append("b")
            }
        }
        repeat(ATTEMPTS) { i ->
            handlerThread.threadHandler.post { tempRunnable.memory.append("a"); }
            handlerThread.threadHandler.post(tempRunnable)
            handlerThread.waitForIdle(TIMEOUT_MS)
            assertEquals(tempRunnable.memory.toString(), "ab".repeat(i + 1))
        }
    }

    // Statistical test : even if visibleOnHandlerThread doesn't work this is likely to succeed,
    // but it will be at least flaky.
    @Test
    fun testVisibleOnHandlerThread() {
        val handlerThread = HandlerThread("testHandler").apply { start() }
        val handler = Handler(handlerThread.looper)

        repeat(ATTEMPTS) { attempt ->
            var x = -10
            var y = -11
            y = visibleOnHandlerThread(handler, ThrowingSupplier<Int> { x = attempt; attempt })
            assertEquals(attempt, x)
            assertEquals(attempt, y)
            handler.post { assertEquals(attempt, x) }
        }

        assertFailsWith<IllegalArgumentException> {
            visibleOnHandlerThread(handler) { throw IllegalArgumentException() }
        }

        // Null values may be returned by the supplier
        assertNull(visibleOnHandlerThread(handler, ThrowingSupplier<Nothing?> { null }))
    }
}
