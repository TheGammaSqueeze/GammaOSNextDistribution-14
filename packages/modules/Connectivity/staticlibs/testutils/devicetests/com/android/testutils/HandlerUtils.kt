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

@file:JvmName("HandlerUtils")

package com.android.testutils

import android.os.ConditionVariable
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.android.testutils.FunctionalUtils.ThrowingRunnable
import com.android.testutils.FunctionalUtils.ThrowingSupplier
import java.lang.Exception
import java.util.concurrent.Executor
import kotlin.test.fail

private const val TAG = "HandlerUtils"

/**
 * Block until the specified Handler or HandlerThread becomes idle, or until timeoutMs has passed.
 */
fun HandlerThread.waitForIdle(timeoutMs: Int) = threadHandler.waitForIdle(timeoutMs.toLong())
fun HandlerThread.waitForIdle(timeoutMs: Long) = threadHandler.waitForIdle(timeoutMs)
fun Handler.waitForIdle(timeoutMs: Int) = waitForIdle(timeoutMs.toLong())
fun Handler.waitForIdle(timeoutMs: Long) {
    val cv = ConditionVariable(false)
    post(cv::open)
    if (!cv.block(timeoutMs)) {
        fail("Handler did not become idle after ${timeoutMs}ms")
    }
}

/**
 * Block until the given Serial Executor becomes idle, or until timeoutMs has passed.
 */
fun waitForIdleSerialExecutor(executor: Executor, timeoutMs: Long) {
    val cv = ConditionVariable()
    executor.execute(cv::open)
    if (!cv.block(timeoutMs)) {
        fail("Executor did not become idle after ${timeoutMs}ms")
    }
}

/**
 * Executes a block of code that returns a value, making its side effects visible on the caller and
 * the handler thread.
 *
 * After this function returns, the side effects of the passed block of code are guaranteed to be
 * observed both on the thread running the handler and on the thread running this method.
 * To achieve this, this method runs the passed block on the handler and blocks this thread
 * until it's executed, so keep in mind this method will block, (including, if the handler isn't
 * running, blocking forever).
 */
fun <T> visibleOnHandlerThread(handler: Handler, supplier: ThrowingSupplier<T>): T {
    val cv = ConditionVariable()
    var rv: Result<T> = Result.failure(RuntimeException("Not run"))
    handler.post {
        try {
            rv = Result.success(supplier.get())
        } catch (exception: Exception) {
            Log.e(TAG, "visibleOnHandlerThread caught exception", exception)
            rv = Result.failure(exception)
        }
        cv.open()
    }
    // After block() returns, the handler thread has seen the change (since it ran it)
    // and this thread also has seen the change (since cv.open() happens-before cv.block()
    // returns).
    cv.block()
    return rv.getOrThrow()
}

/** Overload of visibleOnHandlerThread but executes a block of code that does not return a value. */
inline fun visibleOnHandlerThread(handler: Handler, r: ThrowingRunnable){
    visibleOnHandlerThread(handler, ThrowingSupplier<Unit> { r.run() })
}
